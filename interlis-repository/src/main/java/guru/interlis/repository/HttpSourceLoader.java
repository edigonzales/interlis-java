package guru.interlis.repository;

import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/** Explicit HTTP loader with timeout, size and redirect limits. */
public final class HttpSourceLoader implements SourceLoader {
    private static final int MAX_REDIRECTS = 5;
    private final HttpClient client;
    private final ContentStore store;
    private final DirectoryMetadataStore metadata;
    private final Consumer<RepositoryCacheEvent> observer;

    /** Explicit uncached loader; existing callers retain their host policy. */
    public HttpSourceLoader() { this(new InMemoryContentStore(), null, event -> { }); }

    HttpSourceLoader(ContentStore store, DirectoryMetadataStore metadata, Consumer<RepositoryCacheEvent> observer) {
        this.client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        this.store = store; this.metadata = metadata; this.observer = observer;
    }

    @Override
    public LoadedSource loadCached(SourceLocator locator, FetchPolicy policy, CancellationToken token) {
        Objects.requireNonNull(policy);
        if(!policy.offline())throw new IllegalArgumentException("cached loading requires offline policy");
        return load(locator,policy,token);
    }

    @Override
    public LoadedSource load(SourceLocator locator, FetchPolicy policy, CancellationToken token) {
        Objects.requireNonNull(locator, "locator"); Objects.requireNonNull(policy, "policy"); Objects.requireNonNull(token, "token");
        token.throwIfCancellationRequested();
        var id = new SourceId(locator.location());
        var cached = metadata == null ? Optional.<RepositoryCacheMetadata>empty() : metadata.lookup(id);
        if (policy.offline()) {
            var content = cached.flatMap(m -> store.lookup(m.digest()));
            if (content.isEmpty()) {
                observer.accept(RepositoryCacheEvent.OFFLINE_MISS);
                throw new SourceLoadException(SourceLoadException.Reason.OFFLINE, "verified content unavailable offline", null);
            }
            var loaded = decode(id,content.orElseThrow().bytes(),policy,"verified-cache");
            observer.accept(RepositoryCacheEvent.OFFLINE_HIT);
            return loaded;
        }
        URI uri = URI.create(locator.location());
        try {
            // Missing byte identity proof or a vanished/corrupt 304 body gets one unconditional retry.
            for (int attempt=0;attempt<2;attempt++) {
                URI current = uri;
                HttpResponse<byte[]> response = null;
                boolean reusable = true;
                String requestedEtag = null;
                for (int redirects=0;redirects<=MAX_REDIRECTS;redirects++) {
                    token.throwIfCancellationRequested();
                    var request = HttpRequest.newBuilder(current).timeout(policy.timeout()).GET();
                    String requestLocation=current.toString();
                    requestedEtag = attempt==0 ? cached.filter(m -> m.responseSource().value().equals(requestLocation))
                            .flatMap(RepositoryCacheMetadata::etag).filter(HttpSourceLoader::isStrongEtag).orElse(null) : null;
                    if(requestedEtag!=null) request.header("If-None-Match",requestedEtag);
                    response=receive(request.build(),policy,token);
                    reusable &= reusable(response);
                    if(response.statusCode()==304 || response.statusCode()/100!=3)break;
                    String location=response.headers().firstValue("Location").orElse(null);
                    if(location==null)throw new SourceLoadException(SourceLoadException.Reason.IO_FAILURE,"redirect response without Location",null);
                    if(redirects==MAX_REDIRECTS)throw new SourceLoadException(SourceLoadException.Reason.REDIRECT_LIMIT,"redirect limit exceeded",null);
                    URI next=current.resolve(location);
                    if(!"http".equalsIgnoreCase(next.getScheme()) && !"https".equalsIgnoreCase(next.getScheme()))
                        throw new SourceLoadException(SourceLoadException.Reason.IO_FAILURE,"redirect to unsupported scheme",null);
                    current=next;
                }
                token.throwIfCancellationRequested();
                if(response.statusCode()==304) {
                    if(requestedEtag!=null && requestedEtag.equals(entityTag(response).orElse(null)) && cached.isPresent()) {
                        var content=store.lookup(cached.orElseThrow().digest());
                        if(content.isPresent()) {
                            var loaded=decode(id,content.orElseThrow().bytes(),policy,reusable?"verified-cache":"http-no-store");
                            if(metadata!=null) {
                                if(!reusable)metadata.remove(id);
                                else {
                                    var old=cached.orElseThrow();
                                    metadata.put(new RepositoryCacheMetadata(id,old.responseSource(),old.digest(),
                                            entityTag(response).or(old::etag),validator(response,"Last-Modified").or(old::lastModified)));
                                }
                            }
                            observer.accept(RepositoryCacheEvent.NOT_MODIFIED);
                            return loaded;
                        }
                    }
                    observer.accept(RepositoryCacheEvent.CACHE_MISS);
                    continue;
                }
                if(response.statusCode()==404) {
                    if(metadata!=null)metadata.remove(id);
                    throw new SourceLoadException(SourceLoadException.Reason.NOT_FOUND,"source not found",null);
                }
                if(response.statusCode()==403 || response.statusCode()==401)
                    throw new SourceLoadException(SourceLoadException.Reason.ACCESS_DENIED,"source access denied",null);
                if(response.statusCode()!=200)
                    throw new SourceLoadException(SourceLoadException.Reason.IO_FAILURE,"HTTP status "+response.statusCode(),null);
                byte[] bytes=response.body();
                var loaded=decode(id,bytes,policy,reusable?"http":"http-no-store");
                if(metadata!=null) {
                    if(reusable) {
                        var digest=new ContentDigest("sha256",loaded.byteSha256());
                        store.put(new VerifiedContent(id,digest,bytes));
                        // Do not publish metadata pointing at a failed write.
                        if(store.lookup(digest).isPresent()) metadata.put(new RepositoryCacheMetadata(id,new SourceId(current.toString()),digest,
                                entityTag(response),validator(response,"Last-Modified")));
                    } else metadata.remove(id);
                }
                observer.accept(RepositoryCacheEvent.DOWNLOAD);
                return loaded;
            }
            throw new SourceLoadException(SourceLoadException.Reason.IO_FAILURE,"unexpected conditional response without content",null);
        } catch (java.net.http.HttpTimeoutException e) {
            throw new SourceLoadException(SourceLoadException.Reason.TIMEOUT,"source request timed out",e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SourceLoadException(SourceLoadException.Reason.IO_FAILURE,"source request interrupted",e);
        } catch (IOException e) {
            throw new SourceLoadException(SourceLoadException.Reason.IO_FAILURE,"source request failed",e);
        }
    }

    /** Only a strong entity tag can certify the original bytes used by pins and closure checks. */
    private static boolean isStrongEtag(String value) {
        if(value.length()<2 || value.charAt(0)!='"' || value.charAt(value.length()-1)!='"')return false;
        return value.substring(1,value.length()-1).chars().allMatch(c -> c>=33 && c!=34 && c!=127 && c<=255);
    }

    private static Optional<String> entityTag(HttpResponse<?> response) {
        return response.headers().allValues("ETag").size()==1?validator(response,"ETag"):Optional.empty();
    }

    private static Optional<String> validator(HttpResponse<?> response, String name) {
        return response.headers().firstValue(name).filter(v -> v.length()<=8192 && v.chars().allMatch(c -> c>=32 && c!=127 && c<=255));
    }

    private static boolean reusable(HttpResponse<?> response) {
        // The loader sends a single fixed representation. Other Vary profiles are not cached.
        boolean noStore=response.headers().allValues("Cache-Control").stream().flatMap(v -> Arrays.stream(v.split(",")))
                .anyMatch(v -> v.trim().equalsIgnoreCase("no-store"));
        return !noStore && response.headers().allValues("Vary").stream().allMatch(String::isBlank);
    }

    private static LoadedSource decode(SourceId id, byte[] bytes, FetchPolicy policy, String origin) {
        if(bytes.length>policy.maxBytes())throw new SourceLoadException(SourceLoadException.Reason.TOO_LARGE,"source exceeds configured byte limit",null);
        try {
            String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            return new LoadedSource(id,text,ContentDigest.sha256(bytes).value(),origin);
        } catch(java.nio.charset.CharacterCodingException invalid) {
            throw new SourceLoadException(SourceLoadException.Reason.INVALID_ENCODING,"source is not valid UTF-8",invalid);
        }
    }
    private HttpResponse<byte[]> receive(HttpRequest request, FetchPolicy policy,
                                         CancellationToken token) throws IOException, InterruptedException {
        var future = client.sendAsync(request, info -> new BoundedBody(policy.maxBytes()));
        long started = System.nanoTime();
        try {
            while (true) {
                token.throwIfCancellationRequested();
                long remaining = policy.timeout().toNanos() - (System.nanoTime() - started);
                if (remaining <= 0) throw new java.net.http.HttpTimeoutException("body timed out");
                try {
                    return future.get(Math.min(remaining, 50_000_000L),
                            java.util.concurrent.TimeUnit.NANOSECONDS);
                } catch (java.util.concurrent.TimeoutException waiting) {
                    // Polling bounds cancellation latency even when no body bytes arrive.
                } catch (java.util.concurrent.ExecutionException failed) {
                    Throwable cause = failed.getCause();
                    if (cause instanceof SourceLoadException loading) throw loading;
                    if (cause instanceof IOException io) throw io;
                    throw new IOException("HTTP body failed", cause);
                }
            }
        } finally {
            if (!future.isDone()) future.cancel(true);
        }
    }

    /** Bounds received bytes before forwarding them to the accumulating subscriber. */
    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private final long maximum;
        private long received;
        private java.util.concurrent.Flow.Subscription subscription;
        private boolean failed;

        BoundedBody(long maximum) { this.maximum = maximum; }

        @Override
        public java.util.concurrent.CompletionStage<byte[]> getBody() { return delegate.getBody(); }

        @Override
        public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
            this.subscription = subscription;
            delegate.onSubscribe(subscription);
        }

        @Override
        public void onNext(java.util.List<ByteBuffer> buffers) {
            if (failed) return;
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > maximum - received) {
                    failed = true;
                    subscription.cancel();
                    delegate.onError(new SourceLoadException(SourceLoadException.Reason.TOO_LARGE,
                            "source exceeds configured byte limit", null));
                    return;
                }
                received += buffer.remaining();
            }
            delegate.onNext(buffers);
        }

        @Override
        public void onError(Throwable error) { if (!failed) delegate.onError(error); }

        @Override
        public void onComplete() { if (!failed) delegate.onComplete(); }
    }

}

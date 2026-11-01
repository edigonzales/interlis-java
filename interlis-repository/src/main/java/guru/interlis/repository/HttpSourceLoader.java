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
    private final java.net.ProxySelector proxySelector;
    private final java.util.concurrent.ConcurrentMap<java.time.Duration, HttpClient> clients = new java.util.concurrent.ConcurrentHashMap<>();
    private final ContentStore store;
    private final DirectoryMetadataStore metadata;
    private final CacheObservations observer;
    private final java.time.Clock clock;

    /** Explicit uncached loader; existing callers retain their host policy. */
    public HttpSourceLoader() { this(new RepositoryProxyConfiguration.SystemDefault()); }

    /** Creates an uncached host loader and captures its non-null proxy choice without network IO. */
    public HttpSourceLoader(RepositoryProxyConfiguration proxy) {
        this(new InMemoryContentStore(), null, CacheObservations.detailed(event -> { }), proxy);
    }

    HttpSourceLoader(ContentStore store, DirectoryMetadataStore metadata, Consumer<RepositoryCacheEvent> observer) {
        this(store,metadata,CacheObservations.legacy(observer));
    }
    static HttpSourceLoader observed(ContentStore store, DirectoryMetadataStore metadata, Consumer<RepositoryCacheObservation> observer) {
        return observed(store,metadata,observer,new RepositoryProxyConfiguration.SystemDefault());
    }
    static HttpSourceLoader observed(ContentStore store, DirectoryMetadataStore metadata,
                                      Consumer<RepositoryCacheObservation> observer, RepositoryProxyConfiguration proxy) {
        return new HttpSourceLoader(store,metadata,CacheObservations.detailed(observer),proxy);
    }
    private HttpSourceLoader(ContentStore store, DirectoryMetadataStore metadata, CacheObservations observer) {
        this(store, metadata, observer, new RepositoryProxyConfiguration.SystemDefault());
    }
    private HttpSourceLoader(ContentStore store, DirectoryMetadataStore metadata, CacheObservations observer,
                              RepositoryProxyConfiguration proxy) {
        this(store, metadata, observer, proxy, java.time.Clock.systemUTC());
    }
    static HttpSourceLoader observed(ContentStore store, DirectoryMetadataStore metadata,
            Consumer<RepositoryCacheObservation> observer, RepositoryProxyConfiguration proxy, java.time.Clock clock) {
        return new HttpSourceLoader(store, metadata, CacheObservations.detailed(observer), proxy, clock);
    }
    private HttpSourceLoader(ContentStore store, DirectoryMetadataStore metadata, CacheObservations observer,
                             RepositoryProxyConfiguration proxy, java.time.Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.proxySelector = Objects.requireNonNull(proxy, "proxy").selector();
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
        return load(locator, policy, RepositoryCacheRequest.unconditional(), token);
    }

    @Override
    public LoadedSource load(SourceLocator locator, FetchPolicy policy, RepositoryCacheRequest request, CancellationToken token) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(locator, "locator"); Objects.requireNonNull(policy, "policy"); Objects.requireNonNull(token, "token");
        token.throwIfCancellationRequested();
        var id = new SourceId(locator.location());
        var cached = metadata == null ? Optional.<RepositoryCacheMetadata>empty() : metadata.lookup(id, policy.offline());
        if (policy.offline()) {
            var content = cached.flatMap(m -> store.lookup(m.digest()));
            if (content.isEmpty()) {
                observer.publish(RepositoryCacheEvent.OFFLINE_MISS,id);
                throw new SourceLoadException(SourceLoadException.Reason.OFFLINE, "verified content unavailable offline", null);
            }
            var loaded = decode(id,content.orElseThrow().bytes(),policy,"verified-cache");
            observer.publish(RepositoryCacheEvent.OFFLINE_HIT,id);
            return loaded;
        }
        if (cached.filter(m -> fresh(m, request)).isPresent()) {
            var content = store.lookup(cached.orElseThrow().digest());
            if (content.isPresent()) {
                byte[] bytes = content.orElseThrow().bytes();
                if (bytes.length > policy.maxBytes()) throw new SourceLoadException(SourceLoadException.Reason.TOO_LARGE, "cached source exceeds byte limit", null);
                if (request.expectedMd5().filter(md5 -> !md5.equalsIgnoreCase(md5(bytes))).isEmpty()) {
                    var loaded = decode(id, bytes, policy, "verified-cache");
                    token.throwIfCancellationRequested();
                    observer.publish(RepositoryCacheEvent.FRESH_HIT, id);
                    return loaded;
                }
            }
            observer.publish(RepositoryCacheEvent.CACHE_MISS, id);
        }
        URI current;
        try { current = URI.create(locator.location()); }
        catch (IllegalArgumentException invalid) {
            throw new SourceLoadException(SourceLoadException.Reason.IO_FAILURE, "invalid HTTP source address", invalid);
        }
        try {
            HttpResponse<byte[]> response = null;
            boolean reusable = true;
            for (int redirects=0;redirects<=MAX_REDIRECTS;redirects++) {
                token.throwIfCancellationRequested();
                HttpRequest.Builder builder;
                try { builder = HttpRequest.newBuilder(current).header("Accept-Encoding", "identity").GET(); }
                catch (IllegalArgumentException invalid) {
                    throw new SourceLoadException(SourceLoadException.Reason.IO_FAILURE, "invalid HTTP source address", invalid);
                }
                policy.requestTimeout().ifPresent(builder::timeout);
                response = receive(builder.build(), policy, token);
                reusable &= reusable(response);
                if (response.statusCode()==304 || response.statusCode()/100!=3) break;
                String location = response.headers().firstValue("Location").orElse(null);
                if (location==null) throw new SourceLoadException(SourceLoadException.Reason.IO_FAILURE,"redirect response without Location",null);
                if (redirects==MAX_REDIRECTS) throw new SourceLoadException(SourceLoadException.Reason.REDIRECT_LIMIT,"redirect limit exceeded",null);
                URI next;
                try { next = current.resolve(location); }
                catch (IllegalArgumentException invalid) {
                    throw new SourceLoadException(SourceLoadException.Reason.IO_FAILURE,"invalid HTTP redirect address",invalid);
                }
                if (!"http".equalsIgnoreCase(next.getScheme()) && !"https".equalsIgnoreCase(next.getScheme()))
                    throw new SourceLoadException(SourceLoadException.Reason.IO_FAILURE,"redirect to unsupported scheme",null);
                current=next;
            }
            token.throwIfCancellationRequested();
            if (response.statusCode()==404) {
                if (metadata!=null) metadata.remove(id);
                throw new SourceLoadException(SourceLoadException.Reason.NOT_FOUND,"source not found",null);
            }
            if (response.statusCode()==403 || response.statusCode()==401)
                throw new SourceLoadException(SourceLoadException.Reason.ACCESS_DENIED,"source access denied",null);
            if (response.statusCode()!=200)
                throw new SourceLoadException(SourceLoadException.Reason.IO_FAILURE,"HTTP status "+response.statusCode(),null);
            requireIdentity(response);
            byte[] bytes=response.body();
            var loaded=decode(id,bytes,policy,reusable?"http":"http-no-store");
            if (metadata!=null) {
                if (reusable) {
                    var digest=new ContentDigest("sha256",loaded.byteSha256());
                    store.put(new VerifiedContent(id,digest,bytes));
                    if (store.lookup(digest).isPresent()) metadata.put(new RepositoryCacheMetadata(id,new SourceId(current.toString()),digest,clock.instant()));
                } else metadata.remove(id);
            }
            observer.publish(RepositoryCacheEvent.DOWNLOAD,id);
            return loaded;
        } catch (java.net.http.HttpTimeoutException e) {
            throw new SourceLoadException(SourceLoadException.Reason.TIMEOUT,"source request timed out",e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SourceLoadException(SourceLoadException.Reason.IO_FAILURE,"source request interrupted",e);
        } catch (IOException e) {
            throw new SourceLoadException(SourceLoadException.Reason.IO_FAILURE,"source request failed",e);
        }
    }

    private boolean fresh(RepositoryCacheMetadata entry, RepositoryCacheRequest request) {
        var now=clock.instant();
        return !request.maxAge().isZero() && !entry.downloadedAt().equals(java.time.Instant.EPOCH)
                && !entry.downloadedAt().isAfter(now)
                && java.time.Duration.between(entry.downloadedAt(),now).compareTo(request.maxAge())<=0;
    }

    private static String md5(byte[] bytes) {
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("MD5").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static boolean reusable(HttpResponse<?> response) {
        return response.headers().allValues("Cache-Control").stream().flatMap(v -> Arrays.stream(v.split(",")))
                .noneMatch(v -> v.trim().equalsIgnoreCase("no-store"));
    }

    /** Reject unsolicited compression before decoding or publishing any accepted representation. */
    private static void requireIdentity(HttpResponse<?> response) {
        boolean identity = response.headers().allValues("Content-Encoding").stream()
                .flatMap(v -> Arrays.stream(v.split(",", -1)))
                .allMatch(v -> v.trim().equalsIgnoreCase("identity"));
        if (!identity) throw new SourceLoadException(SourceLoadException.Reason.IO_FAILURE,
                "unsupported HTTP content coding", null);
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
        var client = clients.computeIfAbsent(policy.connectTimeout(), timeout -> HttpClient.newBuilder()
                .connectTimeout(timeout).proxy(proxySelector).followRedirects(HttpClient.Redirect.NEVER).build());
        long started = System.nanoTime();
        var lastActivity = new java.util.concurrent.atomic.AtomicLong(started);
        var body = new java.util.concurrent.atomic.AtomicReference<BoundedBody>();
        var future = client.sendAsync(request, info -> {
            lastActivity.set(System.nanoTime());
            var subscriber = new BoundedBody(policy.maxBytes(), lastActivity);
            body.set(subscriber);
            return subscriber;
        });
        try {
            while (true) {
                token.throwIfCancellationRequested();
                long now = System.nanoTime();
                long remaining = policy.readTimeout().toNanos() - (now - lastActivity.get());
                if (policy.requestTimeout().isPresent()) remaining = Math.min(remaining,
                        policy.requestTimeout().orElseThrow().toNanos() - (now - started));
                if (remaining <= 0) throw new java.net.http.HttpTimeoutException("HTTP exchange timed out");
                try {
                    return future.get(Math.min(remaining, 50_000_000L), java.util.concurrent.TimeUnit.NANOSECONDS);
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
            if (!future.isDone()) {
                future.cancel(true);
                var subscriber = body.get();
                if (subscriber != null) subscriber.cancel();
            }
        }
    }

    /** Bounds received bytes before forwarding them to the accumulating subscriber. */
    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private final long maximum;
        private long received;
        private volatile java.util.concurrent.Flow.Subscription subscription;
        private boolean failed;
        private final java.util.concurrent.atomic.AtomicLong lastActivity;

        BoundedBody(long maximum, java.util.concurrent.atomic.AtomicLong lastActivity) {
            this.maximum = maximum; this.lastActivity = lastActivity;
        }

        void cancel() {
            var active = subscription;
            if (active != null) active.cancel();
        }

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
                if (buffer.hasRemaining()) lastActivity.set(System.nanoTime());
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

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
import java.util.Objects;

/** Explicit HTTP loader with timeout, size and redirect limits. */
public final class HttpSourceLoader implements SourceLoader {
    private static final int MAX_REDIRECTS = 5;
    private final HttpClient client;

    /** Creates a loader with a default HTTP client and explicit redirect handling. */
    public HttpSourceLoader() {
        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public LoadedSource load(SourceLocator locator, FetchPolicy policy, CancellationToken token) {
        Objects.requireNonNull(locator, "locator");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(token, "token");
        if (policy.offline()) {
            throw new SourceLoadException(SourceLoadException.Reason.OFFLINE,
                    "network loading disabled by offline policy", null);
        }
        token.throwIfCancellationRequested();
        URI uri = URI.create(locator.location());
        try {
            URI current = uri;
            HttpResponse<byte[]> response = null;
            for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
                token.throwIfCancellationRequested();
                HttpRequest request = HttpRequest.newBuilder(current)
                        .timeout(policy.timeout())
                        .GET()
                        .build();
                response = receive(request, policy, token);
                if (response.statusCode() / 100 != 3) {
                    break;
                }
                String location = response.headers().firstValue("Location").orElse(null);
                if (location == null) {
                    throw new SourceLoadException(SourceLoadException.Reason.IO_FAILURE,
                            "redirect response without Location", null);
                }
                if (redirects == MAX_REDIRECTS) {
                    throw new SourceLoadException(SourceLoadException.Reason.REDIRECT_LIMIT,
                            "redirect limit exceeded", null);
                }
                URI next = current.resolve(location);
                if (!"http".equalsIgnoreCase(next.getScheme())
                        && !"https".equalsIgnoreCase(next.getScheme())) {
                    throw new SourceLoadException(SourceLoadException.Reason.IO_FAILURE,
                            "redirect to unsupported scheme", null);
                }
                current = next;
            }
            if (response.statusCode() == 404) {
                throw new SourceLoadException(SourceLoadException.Reason.NOT_FOUND,
                        "source not found", null);
            }
            if (response.statusCode() == 403 || response.statusCode() == 401) {
                throw new SourceLoadException(SourceLoadException.Reason.ACCESS_DENIED,
                        "source access denied", null);
            }
            if (response.statusCode() / 100 != 2) {
                throw new SourceLoadException(SourceLoadException.Reason.IO_FAILURE,
                        "HTTP status " + response.statusCode(), null);
            }
            byte[] bytes = response.body();
            if (bytes.length > policy.maxBytes()) {
                throw new SourceLoadException(SourceLoadException.Reason.TOO_LARGE,
                        "source exceeds configured byte limit", null);
            }
            var digest = guru.interlis.source.Sha256.digest(bytes);
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            return new LoadedSource(new SourceId(uri.toString()), text,
                    guru.interlis.source.Hex.encode(digest), "http");
        } catch (java.net.http.HttpTimeoutException e) {
            throw new SourceLoadException(SourceLoadException.Reason.TIMEOUT,
                    "source request timed out", e);
        } catch (java.nio.charset.CharacterCodingException e) {
            throw new SourceLoadException(SourceLoadException.Reason.INVALID_ENCODING,
                    "source is not valid UTF-8", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SourceLoadException(SourceLoadException.Reason.IO_FAILURE,
                    "source request interrupted", e);
        } catch (IOException e) {
            throw new SourceLoadException(SourceLoadException.Reason.IO_FAILURE,
                    "source request failed", e);
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

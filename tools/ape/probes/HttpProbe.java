import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.List;

/** Minimal uncaught-exception HTTP control; no INTERLIS or application classes. */
public class HttpProbe {
    public static void main(String[] args) throws Exception {
        var proxy = new ProxySelector() {
            public List<Proxy> select(URI uri) { return List.of(Proxy.NO_PROXY); }
            public void connectFailed(URI uri, SocketAddress address, java.io.IOException error) {}
        };
        try (var client = HttpClient.newBuilder().proxy(proxy).connectTimeout(Duration.ofSeconds(2)).build()) {
            var result = client.send(HttpRequest.newBuilder(URI.create(args[0])).timeout(Duration.ofSeconds(3)).GET().build(), HttpResponse.BodyHandlers.ofString());
            System.out.println("HTTP_PROBE " + result.statusCode() + " " + result.body());
        }
    }
}

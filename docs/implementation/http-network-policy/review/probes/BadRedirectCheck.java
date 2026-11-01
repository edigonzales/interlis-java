import guru.interlis.execution.CancellationToken;
import guru.interlis.repository.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

public class BadRedirectCheck {
    public static void main(String[] args) throws Exception {
        try (ServerSocket server = new ServerSocket(0, 10, InetAddress.getLoopbackAddress())) {
            Thread.startVirtualThread(() -> {
                try (Socket socket = server.accept()) {
                    var input = socket.getInputStream();
                    var bytes = new ByteArrayOutputStream();
                    int next;
                    while ((next = input.read()) >= 0) {
                        bytes.write(next);
                        if (bytes.toString(StandardCharsets.ISO_8859_1).endsWith("\r\n\r\n")) break;
                    }
                    socket.getOutputStream().write(("HTTP/1.1 302 Found\r\nLocation: http://[\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
                    socket.getOutputStream().flush();
                } catch (Exception ignored) { }
            });
            var loader = new HttpSourceLoader(new RepositoryProxyConfiguration.Direct());
            var policy = new FetchPolicy(false, 1024, Duration.ofSeconds(2), Duration.ofSeconds(2), Optional.empty());
            try {
                loader.load(new SourceLocator("http://127.0.0.1:" + server.getLocalPort() + "/bad"), policy, CancellationToken.none());
                System.out.println("UNEXPECTED_SUCCESS");
            } catch (Throwable failure) {
                System.out.println("OBSERVED " + failure.getClass().getName() + ": " + failure.getMessage());
            }
        }
    }
}

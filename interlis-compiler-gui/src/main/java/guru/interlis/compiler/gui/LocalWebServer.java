package guru.interlis.compiler.gui;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/** Serves only bundled resources on IPv4 loopback. No filesystem or remote fallback. */
final class LocalWebServer implements AutoCloseable {
    private final HttpServer server;
    private final java.util.concurrent.ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    LocalWebServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            try (exchange) {
                String path = exchange.getRequestURI().getPath();
                if (!exchange.getRequestMethod().equals("GET") || path.contains("..") || path.contains("\\")) {
                    exchange.sendResponseHeaders(400, -1); return;
                }
                if (path.equals("/")) path = "/index.html";
                try (var input = LocalWebServer.class.getResourceAsStream("web" + path)) {
                    if (input == null) { exchange.sendResponseHeaders(404, -1); return; }
                    String type = path.endsWith(".js") ? "text/javascript" : path.endsWith(".css") ? "text/css"
                            : path.endsWith(".json") ? "application/json" : path.endsWith(".wasm") ? "application/wasm"
                            : path.endsWith(".html") ? "text/html" : "text/plain";
                    byte[] bytes = input.readAllBytes();
                    exchange.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
                    exchange.getResponseHeaders().set("Cache-Control", "no-store");
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            }
        });
        server.start();
    }
    String url() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/"; }
    @Override public void close() { server.stop(0); executor.close(); }
}

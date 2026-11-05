package guru.interlis.gui.controller;

import com.sun.net.httpserver.HttpServer;
import guru.interlis.execution.CancellationToken;
import guru.interlis.gui.application.ConfiguredRepository;
import guru.interlis.gui.application.RepositoryCatalogService;
import guru.interlis.gui.application.SessionOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class GuiShutdownTest {
    @TempDir
    Path directory;

    @Test
    void closingDuringDelayedHttpStopsCatalogWorkerAndRejectsItsCallback() throws Exception {
        var request = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var publications = new AtomicInteger();
        var ref = new AtomicReference<RepositoryBrowserController>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            request.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            var service = new RepositoryCatalogService();
            var repo = service.repository("http://127.0.0.1:" + server.getAddress().getPort(), directory, CancellationToken.none());
            SwingUtilities.invokeAndWait(() -> {
                var browser = new RepositoryBrowserController(directory, service, ignored -> publications.incrementAndGet());
                ref.set(browser);
                browser.configure(List.of(new ConfiguredRepository(repo, true)), SessionOptions.defaults());
                browser.load(List.of(repo));
            });
            assertTrue(request.await(5, TimeUnit.SECONDS));
            var workers = Thread.getAllStackTraces().keySet().stream()
                    .filter(thread -> thread.getName().equals("interlis-catalog")).toList();
            assertFalse(workers.isEmpty());
            int before = publications.get();
            SwingUtilities.invokeAndWait(ref.get()::close);
            for (Thread worker : workers) {
                worker.join(5000);
                assertFalse(worker.isAlive(), "catalog worker did not stop while the server was delayed");
            }
            SwingUtilities.invokeAndWait(() -> {});
            assertEquals(before, publications.get(), "closed view received a late result");
        } finally {
            if (ref.get() != null) {
                SwingUtilities.invokeAndWait(ref.get()::close);
            }
            release.countDown();
            server.stop(0);
        }
    }
}

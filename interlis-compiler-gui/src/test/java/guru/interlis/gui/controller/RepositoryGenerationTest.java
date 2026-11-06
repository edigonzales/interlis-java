package guru.interlis.gui.controller;

import guru.interlis.gui.application.ConfiguredRepository;
import guru.interlis.gui.application.RepositoryCatalogService;
import guru.interlis.gui.application.SessionOptions;
import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryGenerationTest {
    @TempDir
    Path directory;

    @Test
    void changingSelectionRejectsTheLatePreviewEvenIfItsWorkerReturnsNormally() throws Exception {
        Files.writeString(directory.resolve("ilimodels.xml"), """
                <TRANSFER>
                <ModelMetadata><Name>A</Name><SchemaLanguage>ili2_4</SchemaLanguage><File>A.ili</File><Version>1</Version></ModelMetadata>
                <ModelMetadata><Name>B</Name><SchemaLanguage>ili2_4</SchemaLanguage><File>B.ili</File><Version>1</Version></ModelMetadata>
                </TRANSFER>
                """);
        Files.writeString(directory.resolve("A.ili"), "old preview A");
        Files.writeString(directory.resolve("B.ili"), "current preview B");
        var service = new RepositoryCatalogService();
        var repository = service.repository(directory.toString(), directory, CancellationToken.none());
        var firstStarted = new CountDownLatch(1);
        var letFirstReturn = new CountDownLatch(1);
        var views = new LinkedBlockingQueue<RepositoryBrowserController.View>();
        var ref = new AtomicReference<RepositoryBrowserController>();
        SwingUtilities.invokeAndWait(() -> ref.set(new RepositoryBrowserController(directory, service,
                (entry, options, root, revision, token) -> {
                    if (entry.metadata().name().equals("A")) {
                        var old = service.preview(entry, options, root, revision, CancellationToken.none());
                        firstStarted.countDown();
                        boolean finished = false;
                        while (!finished) {
                            try {
                                finished = letFirstReturn.await(5, TimeUnit.SECONDS);
                                if (!finished) {
                                    throw new IllegalStateException("Timed out waiting to release the old preview");
                                }
                            } catch (InterruptedException ignored) {
                                // Model an IO operation that finishes after its cancellation request.
                            }
                        }
                        assertTrue(token.isCancellationRequested());
                        return old;
                    }
                    return service.preview(entry, options, root, revision, token);
                }, views::add)));
        var browser = ref.get();
        try {
            SwingUtilities.invokeAndWait(() -> {
                browser.configure(List.of(new ConfiguredRepository(repository, true)), SessionOptions.defaults());
                browser.load(List.of(repository));
            });
            var loaded = next(views, view -> view.catalogs().containsKey(repository.base()));
            var entries = loaded.catalogs().get(repository.base()).entries();
            SwingUtilities.invokeAndWait(() -> {
                browser.select(Optional.of(entries.getFirst().id()));
                browser.preview(entries.getFirst());
            });
            assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(() -> {
                browser.select(Optional.of(entries.getLast().id()));
                browser.preview(entries.getLast());
            });
            letFirstReturn.countDown();
            var finished = next(views, view -> view.preview().isPresent());
            assertEquals("current preview B", finished.preview().orElseThrow().source().text());
            assertTrue(views.stream().noneMatch(view -> view.preview()
                    .map(preview -> preview.source().text().equals("old preview A")).orElse(false)));
        } finally {
            letFirstReturn.countDown();
            SwingUtilities.invokeAndWait(browser::close);
        }
    }

    private static RepositoryBrowserController.View next(LinkedBlockingQueue<RepositoryBrowserController.View> views,
                                                         Predicate<RepositoryBrowserController.View> match) throws InterruptedException {
        for (;;) {
            var view = views.poll(5, TimeUnit.SECONDS);
            assertNotNull(view, "No browser transition");
            if (match.test(view)) {
                return view;
            }
        }
    }
}

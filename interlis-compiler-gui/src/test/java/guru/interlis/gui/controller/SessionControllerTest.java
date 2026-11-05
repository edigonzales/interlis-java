package guru.interlis.gui.controller;

import guru.interlis.compiler.WarningPolicy;
import guru.interlis.execution.CancellationReason;
import guru.interlis.execution.RunContextFactory;
import guru.interlis.gui.application.CompileResult;
import guru.interlis.gui.application.CompileService;
import guru.interlis.gui.application.SessionOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class SessionControllerTest {
    @TempDir
    Path directory;

    @Test
    void cancellingKeepsTheWorkerSlotUntilTerminationAndDiscardsItsLateSuccess() throws Exception {
        Path source = write("A");
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var runs = new AtomicInteger();
        var cancellation = new AtomicReference<CancellationReason>();
        var backend = new CompileService();
        var views = new LinkedBlockingQueue<SessionController.View>();
        var owner = new AtomicReference<SessionController>();
        SwingUtilities.invokeAndWait(() -> owner.set(new SessionController(Executors.newSingleThreadExecutor(),
                (request, token) -> {
                    assertFalse(SwingUtilities.isEventDispatchThread());
                    var successful = backend.compile(request, guru.interlis.execution.CancellationToken.none());
                    if (runs.incrementAndGet() == 1) {
                        started.countDown();
                        try {
                            if (!release.await(5, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("Timed out waiting for release");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        }
                        if (token.isCancellationRequested()) {
                            cancellation.set(token.reason());
                        }
                    }
                    return successful;
                }, backend::inspect, RunContextFactory.defaults(), directory, SessionOptions.defaults(), views::add)));
        var session = owner.get();
        try {
            SwingUtilities.invokeAndWait(() -> session.addFiles(List.of(source, directory.resolve("./A.ili"))));
            var added = next(views, v -> v.job() == SessionController.Job.IDLE);
            assertEquals(1, added.files().size());
            SwingUtilities.invokeAndWait(session::compile);
            assertTrue(started.await(5, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(() -> {
                session.compile();
                session.removeFiles(List.of(source));
                session.cancel();
                assertEquals(SessionController.Job.CANCELLING, session.view().job());
                assertFalse(session.view().canCompile());
                assertEquals(1, session.view().files().size());
            });
            release.countDown();
            var cancelled = next(views, v -> v.last().isPresent() && v.job() == SessionController.Job.IDLE);
            assertEquals(CancellationReason.USER_REQUESTED, cancellation.get());
            var last = cancelled.last().orElseThrow();
            assertEquals(new CompileResult.Cancelled(CancellationReason.USER_REQUESTED), last.outcome());
            assertTrue(last.sources().isEmpty());
            assertEquals(1, runs.get());

            SwingUtilities.invokeAndWait(session::compile);
            var newRun = next(views, v -> v.job() == SessionController.Job.IDLE
                    && v.last().map(result -> result != last).orElse(false));
            assertEquals(2, runs.get());
            assertInstanceOf(CompileResult.Compiled.class, newRun.last().orElseThrow().outcome());
        } finally {
            release.countDown();
            SwingUtilities.invokeAndWait(session::close);
        }
    }

    @Test
    void rootAndOptionEditsKeepTheOldResultAndMarkItOutdated() throws Exception {
        Path source = write("A");
        Path folder = Files.createDirectory(directory.resolve("folder.ili"));
        var backend = new CompileService();
        var views = new LinkedBlockingQueue<SessionController.View>();
        var owner = new AtomicReference<SessionController>();
        var options = SessionOptions.defaults();
        SwingUtilities.invokeAndWait(() -> owner.set(new SessionController(Executors.newSingleThreadExecutor(),
                backend::compile, backend::inspect, RunContextFactory.defaults(), directory, options, views::add)));
        var session = owner.get();
        try {
            SwingUtilities.invokeAndWait(() -> session.addFiles(List.of(source, folder)));
            var added = next(views, v -> v.job() == SessionController.Job.IDLE);
            assertEquals(1, added.files().size());
            assertTrue(added.notice().contains("directories"));
            var model = added.files().getFirst().models().getFirst();
            SwingUtilities.invokeAndWait(session::compile);
            var compiled = next(views, v -> v.last().isPresent());
            var first = compiled.last().orElseThrow();
            assertFalse(compiled.outdated());
            SwingUtilities.invokeAndWait(() -> {
                session.setLocalRoots(List.of(model));
                assertTrue(session.view().outdated());
                assertSame(first, session.view().last().orElseThrow());
                session.updateOptions(options.withWarnings(WarningPolicy.REJECT_WARNINGS));
                session.removeFiles(List.of(source));
                assertTrue(session.view().roots().isEmpty());
                assertFalse(session.view().canCompile());
                assertSame(first, session.view().last().orElseThrow());
            });
        } finally {
            SwingUtilities.invokeAndWait(session::close);
        }
    }

    private static SessionController.View next(LinkedBlockingQueue<SessionController.View> views,
                                               Predicate<SessionController.View> predicate) throws InterruptedException {
        for (;;) {
            var view = views.poll(5, TimeUnit.SECONDS);
            assertNotNull(view, "No matching session transition");
            if (predicate.test(view)) {
                return view;
            }
        }
    }

    private Path write(String name) throws Exception {
        return Files.writeString(directory.resolve(name + ".ili"),
                "INTERLIS 2.4; MODEL " + name + " AT \"https://example.invalid\" VERSION \"1\" = END " + name + ".");
    }
}

package guru.interlis.gui.controller;

import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import guru.interlis.gui.application.CompileResult;
import guru.interlis.gui.application.ConfiguredRepository;
import guru.interlis.gui.application.RepositoryIndexSnapshot;
import guru.interlis.gui.application.RepositoryCatalogService;
import guru.interlis.gui.application.SessionOptions;
import guru.interlis.repository.RepositoryBase;
import guru.interlis.repository.RepositorySource;
import guru.interlis.repository.SourceLoadException;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Owns the browser's independent catalog and preview generations on the EDT. */
public final class RepositoryBrowserController implements AutoCloseable {
    public enum Activity { IDLE, VALIDATING, LOADING, CANCELLING }

    public record View(Map<RepositoryBase, RepositoryIndexSnapshot> catalogs, Activity activity,
                       Optional<CompileResult.SourceSnapshot> preview, boolean previewLoading, String notice) {
        public View {
            catalogs = Map.copyOf(catalogs);
        }
    }

    @FunctionalInterface
    public interface PreviewWork {
        CompileResult.SourceSnapshot preview(RepositoryIndexSnapshot.Entry entry, SessionOptions options, Path directory,
                                             long revision, CancellationToken token);
    }

    private final Path directory;
    private final RepositoryCatalogService service;
    private final PreviewWork previewWork;
    private final Consumer<View> render;
    private final ExecutorService catalogsWorker = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("interlis-catalog").factory());
    private final ExecutorService previewsWorker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1), Thread.ofPlatform().name("interlis-preview").factory(),
            new ThreadPoolExecutor.DiscardOldestPolicy());
    private final Map<RepositoryBase, RepositoryIndexSnapshot> catalogs = new LinkedHashMap<>();
    private List<ConfiguredRepository> configured = List.of();
    private SessionOptions options = SessionOptions.defaults();
    private Optional<RepositoryIndexSnapshot.EntryId> selected = Optional.empty();
    private Optional<CompileResult.SourceSnapshot> preview = Optional.empty();
    private Activity activity = Activity.IDLE;
    private Job catalogJob;
    private Job previewJob;
    private Future<?> previewFuture;
    private long sequence;
    private boolean closed;
    private String notice = "";

    public RepositoryBrowserController(Path directory, RepositoryCatalogService service, Consumer<View> render) {
        this(directory, service, service::preview, render);
    }

    public RepositoryBrowserController(Path directory, RepositoryCatalogService service, PreviewWork previewWork, Consumer<View> render) {
        this.directory = Objects.requireNonNull(directory);
        this.service = Objects.requireNonNull(service);
        this.previewWork = Objects.requireNonNull(previewWork);
        this.render = Objects.requireNonNull(render);
    }

    public View view() {
        requireEdt();
        return new View(catalogs, activity, preview, previewJob != null, notice);
    }

    public void configure(List<ConfiguredRepository> repositories, SessionOptions updated) {
        requireEdt();
        if (closed) {
            return;
        }
        boolean networkChanged = !options.repositories().fetchPolicy().equals(updated.repositories().fetchPolicy())
                || !options.cache().equals(updated.cache()) || !options.proxy().equals(updated.proxy());
        options = updated;
        if (networkChanged || !configured.equals(repositories)) {
            configured = List.copyOf(repositories);
            catalogs.keySet().removeIf(base -> configured.stream().noneMatch(row -> row.source().base().equals(base)));
            if (networkChanged) {
                catalogs.clear();
            }
            invalidateCatalog();
            invalidatePreview();
            selected = Optional.empty();
            notice = networkChanged ? "Network settings changed. Reload the indexes to apply them." : "";
            publish();
        }
    }

    public void validateLocation(String location, Consumer<RepositorySource> accept) {
        requireEdt();
        if (!canLoad()) {
            return;
        }
        var job = startCatalog(Activity.VALIDATING);
        catalogsWorker.execute(() -> {
            try {
                var source = service.repository(location, directory, job);
                SwingUtilities.invokeLater(() -> {
                    if (!finishCatalog(job, "")) {
                        return;
                    }
                    accept.accept(source);
                });
            } catch (OperationCancelledException cancelled) {
                SwingUtilities.invokeLater(() -> finishCatalog(job, "Repository validation cancelled."));
            } catch (IllegalArgumentException invalid) {
                SwingUtilities.invokeLater(() -> finishCatalog(job, invalid.getMessage()));
            } catch (RuntimeException unexpected) {
                SwingUtilities.invokeLater(() -> finishCatalog(job, "Repository validation failed."));
            }
        });
    }

    public void load(List<RepositorySource> sources) {
        requireEdt();
        if (!canLoad() || sources.isEmpty()) {
            return;
        }
        invalidatePreview();
        selected = Optional.empty();
        var ordered = List.copyOf(sources);
        var requestOptions = options;
        var job = startCatalog(Activity.LOADING);
        catalogsWorker.execute(() -> {
            try {
                var loaded = service.load(ordered, requestOptions, directory, job.revision, job);
                SwingUtilities.invokeLater(() -> {
                    if (catalogJob != job || closed) {
                        return;
                    }
                    if (!job.isCancellationRequested()) {
                        loaded.forEach(catalog -> catalogs.put(catalog.repository().base(), catalog));
                    }
                    finishCatalog(job, "Indexes loaded.");
                });
            } catch (OperationCancelledException cancelled) {
                SwingUtilities.invokeLater(() -> finishCatalog(job, "Index loading cancelled."));
            } catch (RuntimeException unexpected) {
                SwingUtilities.invokeLater(() -> finishCatalog(job, "Index loading failed."));
            }
        });
    }

    public void cancelLoad() {
        requireEdt();
        if (catalogJob != null) {
            catalogJob.cancel();
            activity = Activity.CANCELLING;
            publish();
        }
    }

    public void select(Optional<RepositoryIndexSnapshot.EntryId> id) {
        requireEdt();
        if (!selected.equals(id)) {
            invalidatePreview();
            selected = id;
            notice = "";
            publish();
        }
    }

    public void preview(RepositoryIndexSnapshot.Entry entry) {
        requireEdt();
        if (closed || selected.filter(entry.id()::equals).isEmpty() || !current(entry.id())) {
            return;
        }
        invalidatePreview();
        var job = new Job(++sequence);
        previewJob = job;
        var requestOptions = options;
        notice = "";
        publish();
        previewFuture = previewsWorker.submit(() -> {
            try {
                var source = previewWork.preview(entry, requestOptions, directory, job.revision, job);
                SwingUtilities.invokeLater(() -> finishPreview(job, entry.id(), Optional.of(source), ""));
            } catch (OperationCancelledException cancelled) {
                SwingUtilities.invokeLater(() -> finishPreview(job, entry.id(), Optional.empty(), "Preview cancelled."));
            } catch (SourceLoadException unavailable) {
                SwingUtilities.invokeLater(() -> finishPreview(job, entry.id(), Optional.empty(), unavailable.getMessage()));
            } catch (RuntimeException unexpected) {
                SwingUtilities.invokeLater(() -> finishPreview(job, entry.id(), Optional.empty(), "Source preview failed."));
            }
        });
    }

    public void cancelPreview() {
        requireEdt();
        invalidatePreview();
        notice = "Preview cancelled.";
        publish();
    }

    private void finishPreview(Job job, RepositoryIndexSnapshot.EntryId id, Optional<CompileResult.SourceSnapshot> source, String message) {
        if (closed || previewJob != job || job.isCancellationRequested() || selected.filter(id::equals).isEmpty() || !current(id)) {
            return;
        }
        previewJob = null;
        previewFuture = null;
        preview = source;
        notice = message;
        publish();
    }

    private boolean current(RepositoryIndexSnapshot.EntryId id) {
        var catalog = catalogs.get(id.repository());
        return catalog != null && id.occurrence() >= 0 && id.occurrence() < catalog.entries().size()
                && catalog.entries().get(id.occurrence()).id().equals(id);
    }

    private boolean finishCatalog(Job job, String message) {
        if (closed || catalogJob != job) {
            return false;
        }
        catalogJob = null;
        activity = Activity.IDLE;
        notice = job.isCancellationRequested() ? "Repository operation cancelled." : message;
        publish();
        return !job.isCancellationRequested();
    }

    private Job startCatalog(Activity next) {
        var job = new Job(++sequence);
        catalogJob = job;
        activity = next;
        notice = "";
        publish();
        return job;
    }

    private boolean canLoad() {
        return !closed && catalogJob == null;
    }

    private void invalidateCatalog() {
        if (catalogJob != null) {
            catalogJob.cancel();
            activity = Activity.CANCELLING;
        }
    }

    private void invalidatePreview() {
        if (previewJob != null) {
            previewJob.cancel();
            if (previewFuture != null) {
                previewFuture.cancel(true);
            }
        }
        previewJob = null;
        previewFuture = null;
        preview = Optional.empty();
    }

    private void publish() {
        render.accept(view());
    }

    @Override
    public void close() {
        requireEdt();
        closed = true;
        invalidateCatalog();
        invalidatePreview();
        catalogsWorker.shutdownNow();
        previewsWorker.shutdownNow();
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("The repository browser belongs to the event dispatch thread");
        }
    }

    private static final class Job implements CancellationToken {
        private final long revision;
        private final AtomicBoolean cancelled = new AtomicBoolean();

        private Job(long revision) {
            this.revision = revision;
        }

        private void cancel() {
            cancelled.set(true);
        }

        @Override
        public boolean isCancellationRequested() {
            return cancelled.get();
        }
    }
}

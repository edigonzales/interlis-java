package guru.interlis.gui.controller;

import guru.interlis.diagnostics.DiagnosticLanguage;
import guru.interlis.execution.CancellationReason;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.RunContextFactory;
import guru.interlis.gui.application.CompileRequest;
import guru.interlis.gui.application.CompileResult;
import guru.interlis.gui.application.LocalInput;
import guru.interlis.gui.application.RootChoice;
import guru.interlis.gui.application.SessionInputs;
import guru.interlis.gui.application.SessionOptions;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** The EDT owns all session mutations. Workers only receive frozen values and their own token. */
public final class SessionController implements AutoCloseable {
    private final ExecutorService worker;
    private final CompileWork compiler;
    private final InputDiscovery discovery;
    private final RunContextFactory contexts;
    private final Path directory;
    private final Consumer<View> render;
    private final Map<Path, LocalInput> inputs = new LinkedHashMap<>();
    private List<RootChoice> roots = List.of();
    private SessionOptions options;
    private CompileResult last;
    private WorkToken active;
    private Job job = Job.IDLE;
    private long generation;
    private boolean closed;
    private String notice = "";

    @FunctionalInterface
    public interface CompileWork {
        CompileResult compile(CompileRequest request, CancellationToken token);
    }

    @FunctionalInterface
    public interface InputDiscovery {
        Optional<LocalInput> inspect(Path path, Path directory, CancellationToken token);
    }

    public enum Job { IDLE, ADDING, COMPILING, CANCELLING }

    /** An immutable, ordered view of the current EDT state. */
    public record View(List<LocalInput> files, List<RootChoice> roots, SessionOptions options, Job job,
                       Optional<CompileResult> last, boolean outdated, String notice) {
        public View {
            files = List.copyOf(files);
            roots = List.copyOf(roots);
        }

        public boolean canCompile() {
            return job == Job.IDLE && (!files.isEmpty() || !roots.isEmpty());
        }
    }

    public SessionController(ExecutorService worker, CompileWork compiler, InputDiscovery discovery,
                             RunContextFactory contexts, Path directory, SessionOptions options, Consumer<View> render) {
        requireEdt();
        this.worker = Objects.requireNonNull(worker);
        this.compiler = Objects.requireNonNull(compiler);
        this.discovery = Objects.requireNonNull(discovery);
        this.contexts = Objects.requireNonNull(contexts);
        this.directory = directory.toAbsolutePath().normalize();
        this.options = Objects.requireNonNull(options);
        this.render = Objects.requireNonNull(render);
    }

    public View view() {
        requireEdt();
        return new View(List.copyOf(inputs.values()), roots, options, job, Optional.ofNullable(last),
                last != null && last.request().generation() != generation, notice);
    }

    public void addFiles(List<Path> files) {
        requireEdt();
        if (!idle()) {
            return;
        }
        var pending = new LinkedHashSet<Path>();
        boolean invalid = false;
        for (Path path : files) {
            Path normalized = directory.resolve(path).normalize();
            if (normalized.getFileName() == null
                    || !normalized.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ili")) {
                invalid = true;
            } else if (!inputs.containsKey(normalized)) {
                pending.add(normalized);
            }
        }
        notice = invalid ? "Choose .ili files. Directories cannot be added." : "";
        if (pending.isEmpty()) {
            publish();
            return;
        }
        var token = begin(Job.ADDING);
        List<Path> paths = List.copyOf(pending);
        worker.execute(() -> {
            var discovered = new ArrayList<LocalInput>();
            int rejected = 0;
            try {
                for (Path path : paths) {
                    Optional<LocalInput> found = discovery.inspect(path, directory, token);
                    if (found.isPresent()) {
                        discovered.add(found.orElseThrow());
                    } else {
                        rejected++;
                    }
                }
            } catch (RuntimeException problem) {
                rejected += paths.size() - discovered.size() - rejected;
            }
            int skipped = rejected;
            SwingUtilities.invokeLater(() -> finishAdding(token, discovered, skipped));
        });
    }

    private void finishAdding(WorkToken token, List<LocalInput> discovered, int skipped) {
        if (closed || active != token) {
            return;
        }
        if (skipped != 0) {
            notice = "Some inputs could not be added. Choose readable .ili files, without directories.";
        }
        var known = new LinkedHashSet<>(inputs.values().stream().flatMap(input -> input.source().stream()).toList());
        boolean changed = false;
        for (LocalInput input : discovered) {
            if (input.source().isEmpty() || known.add(input.source().orElseThrow())) {
                inputs.put(input.path(), input);
                changed = true;
            }
        }
        if (changed) {
            generation++;
        }
        finish();
    }

    public void removeFiles(List<Path> paths) {
        requireEdt();
        if (!idle()) {
            return;
        }
        var removed = paths.stream().map(inputs::remove).filter(Objects::nonNull).toList();
        if (!removed.isEmpty()) {
            var sources = removed.stream().flatMap(input -> input.source().stream()).toList();
            roots = roots.stream().filter(root -> !(root instanceof RootChoice.Local local
                    && sources.contains(local.source()))).toList();
            change();
        }
    }

    public void setLocalRoots(List<RootChoice.Local> choices) {
        requireEdt();
        if (!idle()) {
            return;
        }
        var available = inputs.values().stream().flatMap(input -> input.models().stream()).toList();
        if (!available.containsAll(choices)) {
            throw new IllegalArgumentException("Root choice does not belong to an input file");
        }
        var selected = new LinkedHashSet<RootChoice>();
        roots.stream().filter(RootChoice.Repository.class::isInstance).forEach(selected::add);
        selected.addAll(choices);
        setRoots(List.copyOf(selected));
    }

    public void useAllInputs() {
        requireEdt();
        if (idle()) {
            setRoots(List.of());
        }
    }

    private void setRoots(List<RootChoice> selected) {
        if (!roots.equals(selected)) {
            roots = selected;
            change();
        }
    }

    public void updateOptions(SessionOptions updated) {
        requireEdt();
        if (idle() && !options.equals(updated)) {
            boolean compileOptionsChanged = !options.sameCompileOptions(Objects.requireNonNull(updated));
            options = updated;
            if (compileOptionsChanged) {
                change();
            } else {
                publish();
            }
        }
    }

    public void setDiagnosticLanguage(DiagnosticLanguage language) {
        requireEdt();
        if (!closed && options.diagnosticLanguage() != language) {
            options = options.withDiagnosticLanguage(language);
            publish();
        }
    }

    public void compile() {
        requireEdt();
        if (!idle() || !view().canCompile()) {
            return;
        }
        var request = new CompileRequest(contexts.createRoot(), generation,
                new SessionInputs(List.copyOf(inputs.keySet()), roots), options, directory);
        notice = "";
        var token = begin(Job.COMPILING);
        worker.execute(() -> {
            CompileResult completed;
            long start = System.nanoTime();
            try {
                completed = compiler.compile(request, token);
            } catch (RuntimeException failure) {
                completed = terminal(request, new CompileResult.Failed(), start);
            }
            CompileResult result = completed;
            SwingUtilities.invokeLater(() -> finishCompile(token, request, result, start));
        });
    }

    public void cancel() {
        requireEdt();
        if (job == Job.COMPILING) {
            active.request(CancellationReason.USER_REQUESTED);
            job = Job.CANCELLING;
            publish();
        }
    }

    private void finishCompile(WorkToken token, CompileRequest request, CompileResult result, long start) {
        if (closed || token != active || generation != request.generation()) {
            return;
        }
        if (token.isCancellationRequested()) {
            last = terminal(request, new CompileResult.Cancelled(token.reason()), start);
        } else if (result.request().equals(request)) {
            last = result;
        } else {
            last = terminal(request, new CompileResult.Failed(), start);
        }
        finish();
    }

    private static CompileResult terminal(CompileRequest request, CompileResult.Outcome outcome, long start) {
        return new CompileResult(request, Instant.now(), Duration.ofNanos(System.nanoTime() - start), outcome,
                Map.of(), Optional.empty(), Optional.empty(), List.of(), List.of());
    }

    @Override
    public void close() {
        requireEdt();
        if (!closed) {
            closed = true;
            if (active != null) {
                active.request(CancellationReason.HOST_SHUTDOWN);
            }
            worker.shutdownNow();
        }
    }

    private WorkToken begin(Job next) {
        var token = new WorkToken();
        active = token;
        job = next;
        publish();
        return token;
    }

    private void finish() {
        active = null;
        job = Job.IDLE;
        publish();
    }

    private boolean idle() {
        return !closed && job == Job.IDLE;
    }

    private void change() {
        generation++;
        notice = "";
        publish();
    }

    private void publish() {
        render.accept(view());
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("The session belongs to the event dispatch thread");
        }
    }

    private static final class WorkToken implements CancellationToken {
        private final AtomicReference<CancellationReason> reason = new AtomicReference<>();

        private void request(CancellationReason requested) {
            reason.compareAndSet(null, requested);
        }

        @Override
        public boolean isCancellationRequested() {
            return reason.get() != null;
        }

        @Override
        public CancellationReason reason() {
            return Objects.requireNonNullElse(reason.get(), CancellationReason.USER_REQUESTED);
        }
    }
}

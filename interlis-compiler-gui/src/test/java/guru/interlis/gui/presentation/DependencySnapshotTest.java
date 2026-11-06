package guru.interlis.gui.presentation;

import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.RunContextFactory;
import guru.interlis.gui.application.CompileRequest;
import guru.interlis.gui.application.CompileResult;
import guru.interlis.gui.application.CompileService;
import guru.interlis.gui.application.RootChoice;
import guru.interlis.gui.application.SessionInputs;
import guru.interlis.gui.application.SessionOptions;
import guru.interlis.syntax.ParseMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DependencySnapshotTest {
    @TempDir
    Path directory;
    private final CompileService service = new CompileService();

    @Test
    void diamondShowsBothOccurrencesAndDirectIncomingEdgesFromTheStrictRun() throws Exception {
        Path root = model("Root", "1", "IMPORTS Left, Right, INTERLIS;");
        Path left = model("Left", "raw-left", "IMPORTS Shared;");
        Path right = model("Right", "raw-right", "IMPORTS Shared;");
        Path shared = model("Shared", "2026-11-raw", "");
        var result = compile(List.of(root, left, right, shared), List.of(choice(root)));
        var capture = result.capture().orElseThrow();
        assertTrue(capture.reuse().previousSyntax().values().stream().allMatch(syntax -> syntax.mode() == ParseMode.STRICT));
        assertEquals(1, capture.resolution().rootLocations().size());

        var graph = DependencySnapshot.from(result);
        assertEquals(DependencySnapshot.Completeness.COMPLETE, graph.completeness());
        var projection = new DependencyTree(graph, "", false);
        var rootOccurrence = projection.roots().getFirst();
        var imports = projection.children(rootOccurrence);
        assertEquals(List.of("Left", "Right", "INTERLIS"), imports.stream().map(DependencyTree.Occurrence::name).toList());
        assertEquals(DependencySnapshot.Status.BUILTIN, imports.getLast().status());
        var leftShared = projection.children(imports.getFirst()).getFirst();
        var rightShared = projection.children(imports.get(1)).getFirst();
        assertEquals(leftShared.target(), rightShared.target());
        assertNotEquals(leftShared, rightShared);
        assertFalse(leftShared.cycle());
        var sharedId = leftShared.target().orElseThrow();
        assertEquals("2026-11-raw", sharedId.modelVersion().orElseThrow());
        assertEquals(List.of("Left", "Right"), graph.usedBy(sharedId).stream()
                .map(edge -> edge.importer().modelName()).toList());
        assertTrue(graph.directImports(sharedId).isEmpty());
    }

    @Test
    void cycleTerminatesAtTheAncestorAndMissingAndAmbiguousAreExplicit() throws Exception {
        Path root = model("CycleA", "1", "IMPORTS CycleB;");
        Path other = model("CycleB", "1", "IMPORTS CycleA, Missing, Clash;");
        Path first = write("first", "Clash", "1", "");
        Path second = write("second", "Clash", "2", "");
        var result = compile(List.of(root, other, first, second), List.of(choice(root)));
        assertInstanceOf(CompilationOutcome.Rejected.class, assertInstanceOf(CompileResult.Compiled.class, result.outcome()).result());
        var graph = DependencySnapshot.from(result);
        assertEquals(DependencySnapshot.Completeness.PARTIAL, graph.completeness());
        var all = new DependencyTree(graph, "", false);
        var nested = all.children(all.children(all.roots().getFirst()).getFirst());
        assertTrue(nested.getFirst().cycle());
        assertTrue(all.children(nested.getFirst()).isEmpty());
        assertEquals(DependencySnapshot.Status.MISSING, nested.get(1).status());
        assertEquals(DependencySnapshot.Status.AMBIGUOUS, nested.getLast().status());
        assertInstanceOf(guru.interlis.compiler.ImportResolution.Ambiguous.class,
                nested.getLast().edge().orElseThrow().resolution().orElseThrow());
        var filtered = new DependencyTree(graph, "missing", true);
        var path = filtered.children(filtered.roots().getFirst()).getFirst();
        assertTrue(filtered.children(path).stream().anyMatch(item -> item.name().equals("Missing")));
        assertFalse(filtered.children(path).stream().anyMatch(item -> item.name().equals("Clash")));
    }

    @Test
    void sameNameInDifferentSourcesKeepsTwoIdentitiesAndTheRequestedOrder() throws Exception {
        Path first = write("left", "Twin", "v1", "");
        Path second = write("right", "Twin", "v2", "");
        var graph = DependencySnapshot.from(compile(List.of(first, second), List.of(choice(second), choice(first))));
        assertEquals(2, graph.roots().size());
        var targets = graph.roots().stream().map(root -> root.target().orElseThrow()).toList();
        assertNotEquals(targets.getFirst().source(), targets.getLast().source());
        assertEquals(List.of("v2", "v1"), targets.stream().map(target -> target.modelVersion().orElseThrow()).toList());
    }

    @Test
    void expansionRetainsAnExplicitDepthLimitForDeepGraphs() throws Exception {
        var files = new java.util.ArrayList<Path>();
        int size = DependencyTree.MAX_DEPTH + 4;
        for (int index = 0; index < size; index++) {
            files.add(model("M" + index, "1", index == size - 1 ? "" : "IMPORTS M" + (index + 1) + ";"));
        }
        var graph = DependencySnapshot.from(compile(files, List.of(choice(files.getFirst()))));
        assertEquals(size, graph.models().size());
        var tree = new DependencyTree(graph, "", false);
        var item = tree.roots().getFirst();
        for (int depth = 0; depth < DependencyTree.MAX_DEPTH; depth++) {
            var next = tree.children(item).getFirst();
            assertFalse(next.limit());
            item = next;
        }
        var limit = tree.children(item).getFirst();
        assertTrue(limit.limit());
        assertTrue(tree.children(limit).isEmpty());
    }

    @Test
    void failedPreparationIsNotAnEmptySuccessfulGraph() {
        Path missing = directory.resolve("missing.ili");
        var graph = DependencySnapshot.from(compile(List.of(missing), List.of()));
        assertEquals(DependencySnapshot.Completeness.UNAVAILABLE, graph.completeness());
        assertTrue(graph.roots().isEmpty());
    }

    private CompileResult compile(List<Path> files, List<RootChoice> roots) {
        var request = new CompileRequest(RunContextFactory.defaults().createRoot(), 1, new SessionInputs(files, roots),
                SessionOptions.defaults(), directory);
        return service.compile(request, CancellationToken.none());
    }

    private RootChoice.Local choice(Path path) {
        return service.inspect(path, directory, CancellationToken.none()).orElseThrow().models().getFirst();
    }

    private Path model(String name, String version, String body) throws Exception {
        return write(name, name, version, body);
    }

    private Path write(String file, String name, String version, String body) throws Exception {
        return Files.writeString(directory.resolve(file + ".ili"),
                "INTERLIS 2.4; MODEL " + name + " AT \"https://example.invalid\" VERSION \"" + version
                        + "\" = " + body + " END " + name + ".");
    }
}

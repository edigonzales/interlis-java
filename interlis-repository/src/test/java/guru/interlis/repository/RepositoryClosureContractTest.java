package guru.interlis.repository;

import com.sun.net.httpserver.HttpServer;
import guru.interlis.compiler.*;
import guru.interlis.execution.*;
import guru.interlis.model.*;
import guru.interlis.source.*;
import guru.interlis.workspace.*;
import guru.interlis.workspace.resolution.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class RepositoryClosureContractTest {
    @TempDir Path directory;
    static final CancellationToken TOKEN = CancellationToken.none();
    static String model(String name, String version, String body) {
        return "INTERLIS 2.4; MODEL " + name + " AT \"https://example.org\" VERSION \"" + version + "\" = " + body + " END " + name + ".";
    }
    static SourceId id(String value) { return new SourceId(value); }
    static SourceUpdate add(String id, String text) { return new SourceUpdate(id(id), new SourceExpectation.Absent(), new SourceChange.Replace(text)); }
    static Workspace workspace(String root) {
        var w = Workspaces.create(CompilerEngines.create(), WorkspaceConfiguration.defaults());
        w.update(add("memory:/root.ili", root)); return w;
    }
    static AnalysisRequest request(Workspace w) {
        return new AnalysisRequest(w.currentRevision(), List.of(new ModelSelector.BySource(id("memory:/root.ili"))));
    }
    static RepositorySource source(String base) {
        return new RepositorySource(new RepositoryBase(URI.create(base)), new SourceLocator(base + "ilimodels.xml"));
    }
    static RepositoryConfig config(String base) { return new RepositoryConfig(List.of(source(base)), FetchPolicy.defaults()); }
    static String entry(String name, String version, String file) {
        return "<IliRepository20.RepositoryIndex.ModelMetadata><Name>" + name + "</Name><SchemaLanguage>ili2_4</SchemaLanguage><Version>"
                + version + "</Version><File>" + file + "</File></IliRepository20.RepositoryIndex.ModelMetadata>";
    }
    static String index(String... entries) { return "<TRANSFER><DATASECTION><IliRepository20.RepositoryIndex>" + String.join("", entries) + "</IliRepository20.RepositoryIndex></DATASECTION></TRANSFER>"; }
    static LoadedSource loaded(String uri, String text) {
        return new LoadedSource(id(uri), text, ContentDigest.sha256(text.getBytes(StandardCharsets.UTF_8)).value(), "test");
    }
    static SourceLoader loader(Map<String, String> contents) {
        return (locator, policy, token) -> {
            token.throwIfCancellationRequested();
            var text = contents.get(locator.location());
            if (text == null) throw new SourceLoadException(SourceLoadException.Reason.NOT_FOUND, "missing fixture", null);
            return loaded(locator.location(), text);
        };
    }
    static PreparedRepositoryUpdate prepared(RepositoryResolutionOutcome outcome) {
        return assertInstanceOf(RepositoryResolutionOutcome.Prepared.class, outcome, outcome.toString()).update();
    }
    static RepositoryProblem problem(RepositoryResolutionOutcome outcome) {
        return assertInstanceOf(RepositoryResolutionOutcome.Rejected.class, outcome).problems().getFirst();
    }
    static CompilationOutcome.Succeeded compile(Workspace w, String root) {
        var outcome = w.compile(new WorkspaceCompileRequest(w.currentRevision(), List.of(new ModelSelector.ByName(root, Optional.empty(), Optional.empty()))), TOKEN);
        return assertInstanceOf(CompilationOutcome.Succeeded.class, outcome, () -> outcome instanceof CompilationOutcome.Rejected r ? r.analysis().diagnostics().toString() : outcome.toString());
    }
    static Symbol named(SemanticGraph graph, String name) {
        return graph.symbol(graph.qualifiedNames().get(new QualifiedName(List.of(name.split("\\.")))).getFirst()).orElseThrow();
    }
    static Map<String, String> closure(String base) {
        return new HashMap<>(Map.of(base + "ilimodels.xml", index(entry("Middle", "1", "middle.ili"), entry("Leaf", "1", "leaf.ili")),
                base + "middle.ili", model("Middle", "1", "IMPORTS Leaf; DOMAIN D EXTENDS Leaf.D=TEXT*7;"),
                base + "leaf.ili", model("Leaf", "1", "DOMAIN D=TEXT*7;")));
    }

    @Test void preparesTransitiveLocalAndFileUriSourcesAndInstallsOneAtomicBatch() throws Exception {
        String base = directory.toUri().toString();
        for (var e : closure(base).entrySet()) Files.writeString(Path.of(URI.create(e.getKey())), e.getValue());
        var file = new FileSourceLoader(directory);
        assertEquals(file.load(new SourceLocator("leaf.ili"), FetchPolicy.defaults(), TOKEN).text(),
                file.load(new SourceLocator(base + "leaf.ili"), FetchPolicy.defaults(), TOKEN).text());
        try (var w = workspace(model("Root", "1", "IMPORTS Middle; DOMAIN D EXTENDS Middle.D=TEXT*7;"))) {
            var before = w.currentRevision();
            var update = prepared(new RepositoryClosureService(new RoutingSourceLoader(directory), new InMemoryContentStore())
                    .resolve(w, request(w), RepositoryResolveOptions.defaults(config(base)), TOKEN));
            assertEquals(before, w.currentRevision()); assertEquals(1, w.state().sourceRevisions().size());
            assertEquals(2, update.sources().size()); assertEquals(Set.of("Middle", "Leaf"), update.lock().models().keySet());
            var receipt = update.apply(w);
            assertEquals(before.value() + 1, receipt.workspaceRevision().value());
            var g = compile(w, "Root").schema().semanticGraph();
            var middle = (ModelSymbol) named(g, "Middle");
            assertEquals(named(g, "Leaf").id(), middle.imports().getFirst().targetModel().orElseThrow());
            assertEquals("Leaf", named(g, "Leaf").name().value());
            assertInstanceOf(DomainSymbol.class, named(g, "Root.D"));
            assertThrows(UnsupportedOperationException.class, () -> update.lock().models().clear());
        }
    }

    @Test void resolvesViaRealHttpAndReplaysLockOfflineWithoutCallingAnyProvider() throws Exception {
        var contents = new HashMap<String, String>();
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
        contents.putAll(closure(base));
        server.createContext("/", exchange -> {
            calls.incrementAndGet(); var text = contents.get(base + exchange.getRequestURI().getPath().substring(1));
            byte[] bytes = (text == null ? "missing" : text).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(text == null ? 404 : 200, bytes.length);
            try (var body = exchange.getResponseBody()) { body.write(bytes); }
        });
        server.start();
        var cache = new InMemoryContentStore(); ResolutionLock lock;
        try (var w = workspace(model("Root", "1", "IMPORTS Middle; DOMAIN D EXTENDS Middle.D=TEXT*7;"))) {
            var update = prepared(new RepositoryClosureService(new RoutingSourceLoader(directory), cache)
                    .resolve(w, request(w), RepositoryResolveOptions.defaults(config(base)), TOKEN));
            lock = update.lock(); update.apply(w); compile(w, "Root"); assertEquals(6, calls.get());
        } finally { server.stop(0); }
        var offline = new RepositoryConfig(List.of(source(base)), new FetchPolicy(true, 100_000, Duration.ofSeconds(1)));
        try (var w = workspace(model("Root", "1", "IMPORTS Middle; DOMAIN D EXTENDS Middle.D=TEXT*7;"))) {
            var update = prepared(new RepositoryClosureService((l,p,t) -> { fail("offline provider called"); return null; }, cache)
                    .resolve(w, request(w), new RepositoryResolveOptions(offline, Map.of(), Map.of(), Optional.of(lock)), TOKEN));
            assertEquals(lock, update.lock()); update.apply(w); compile(w, "Root");
        }
        try (var w = workspace(model("Root", "1", "IMPORTS Middle;"))) {
            var rejected = problem(new RepositoryClosureService((l,p,t) -> { fail("offline provider called"); return null; }, new InMemoryContentStore())
                    .resolve(w, request(w), new RepositoryResolveOptions(offline, Map.of(), Map.of(), Optional.of(lock)), TOKEN));
            assertEquals(SourceLoadException.Reason.OFFLINE, rejected.loadReason().orElseThrow());
            assertEquals(1, w.state().sourceRevisions().size());
        }
    }

    @Test void versionsNeverUseLatestAndExactPinsBindTheRealSelectedDefinition() {
        String base = "https://repo.example/";
        var contents = new HashMap<>(Map.of(base + "ilimodels.xml", index(entry("Dep", "1", "one.ili"), entry("Dep", "99", "other.ili")),
                base + "one.ili", model("Dep", "1", "DOMAIN D=TEXT*7;"), base + "other.ili", model("Dep", "99", "DOMAIN D=TEXT*99;")));
        var service = new RepositoryClosureService(loader(contents), new InMemoryContentStore());
        try (var w = workspace(model("Root", "1", "IMPORTS Dep; DOMAIN D EXTENDS Dep.D=TEXT*7;"))) {
            assertEquals(RepositoryProblem.Reason.AMBIGUOUS, problem(service.resolve(w, request(w), RepositoryResolveOptions.defaults(config(base)), TOKEN)).reason());
            var text = contents.get(base + "one.ili"); var pin = new ModelPin("Dep", "1", id(base + "one.ili"), ContentDigest.sha256(text.getBytes(StandardCharsets.UTF_8)));
            var update = prepared(service.resolve(w, request(w), new RepositoryResolveOptions(config(base), Map.of("Dep", pin), Map.of(), Optional.empty()), TOKEN));
            assertEquals(List.of(pin), update.lock().models().get("Dep")); update.apply(w);
            assertEquals("1", ((ModelSymbol) named(compile(w, "Root").schema().semanticGraph(), "Dep")).modelVersion());
        }
        try (var w = workspace(model("Root", "1", "IMPORTS Dep;"))) {
            var pin = new ModelPin("Dep", "absent", id(base + "one.ili"), ContentDigest.sha256(new byte[0]));
            assertEquals(RepositoryProblem.Reason.VERSION_MISMATCH, problem(service.resolve(w, request(w), new RepositoryResolveOptions(config(base), Map.of("Dep", pin), Map.of(), Optional.empty()), TOKEN)).reason());
        }
    }

    @Test void repositoryPriorityWinsWithoutHidingAmbiguityInsideTheWinningGroup() {
        String a="https://first.example/", b="https://second.example/";
        var contents = new HashMap<>(Map.of(a+"ilimodels.xml",index(entry("Dep","1","one.ili")), a+"one.ili",model("Dep","1","DOMAIN D=TEXT;"),
                b+"ilimodels.xml",index(entry("Dep","99","other.ili")), b+"other.ili",model("Dep","99","DOMAIN D=TEXT;")));
        var service=new RepositoryClosureService(loader(contents),new InMemoryContentStore());
        var config=new RepositoryConfig(List.of(source(a),source(b)),FetchPolicy.defaults());
        try(var w=workspace(model("Root","1","IMPORTS Dep;"))) {
            var update=prepared(service.resolve(w,request(w),RepositoryResolveOptions.defaults(config),TOKEN));
            assertEquals("1",update.lock().models().get("Dep").getFirst().modelVersion());
        }
        contents.put(a+"ilimodels.xml",index(entry("Dep","1","one.ili"),entry("Dep","2","two.ili")));
        try(var w=workspace(model("Root","1","IMPORTS Dep;"))) {
            assertEquals(RepositoryProblem.Reason.AMBIGUOUS,problem(service.resolve(w,request(w),RepositoryResolveOptions.defaults(config),TOKEN)).reason());
        }
    }

    @Test void wrongDeclaredHeaderDigestAndPublishedMd5AreRejectedWithoutFallback() {
        String base="https://repo.example/";
        var contents=new HashMap<>(Map.of(base+"ilimodels.xml",index(entry("Dep","1","dep.ili")),base+"dep.ili",model("Wrong","1","")));
        var service=new RepositoryClosureService(loader(contents),new InMemoryContentStore());
        try(var w=workspace(model("Root","1","IMPORTS Dep;"))) {
            assertEquals(RepositoryProblem.Reason.INVALID_SOURCE,problem(service.resolve(w,request(w),RepositoryResolveOptions.defaults(config(base)),TOKEN)).reason());
            assertEquals(1,w.state().sourceRevisions().size());
            contents.put(base+"dep.ili",model("Dep","2",""));
            assertEquals(RepositoryProblem.Reason.VERSION_MISMATCH,problem(service.resolve(w,request(w),RepositoryResolveOptions.defaults(config(base)),TOKEN)).reason());
            contents.put(base+"dep.ili",model("Dep","1",""));
            var pin=new ModelPin("Dep","1",id(base+"dep.ili"),ContentDigest.sha256(new byte[0]));
            assertEquals(RepositoryProblem.Reason.PIN_MISMATCH,problem(service.resolve(w,request(w),new RepositoryResolveOptions(config(base),Map.of("Dep",pin),Map.of(),Optional.empty()),TOKEN)).reason());
            contents.put(base+"ilimodels.xml",index(entry("Dep","1","dep.ili").replace("</File>","</File><md5>00000000000000000000000000000000</md5>")));
            assertEquals(RepositoryProblem.Reason.PIN_MISMATCH,problem(service.resolve(w,request(w),RepositoryResolveOptions.defaults(config(base)),TOKEN)).reason());
        }
    }

    @Test void sourceChangesDuringResolutionAndStaleBatchesNeverOverwriteEditors() {
        String base="https://repo.example/"; var contents=closure(base); var reads=new AtomicInteger();
        var unstable=(SourceLoader)(l,p,t)-> {
            var text=contents.get(l.location());
            if(l.location().endsWith("middle.ili")&&reads.incrementAndGet()>1) text=text.replace("VERSION \"1\"","VERSION \"2\"");
            return loaded(l.location(),text);
        };
        try(var w=workspace(model("Root","1","IMPORTS Middle;"))) {
            assertEquals(RepositoryProblem.Reason.CHANGED_SOURCE,problem(new RepositoryClosureService(unstable,new InMemoryContentStore())
                    .resolve(w,request(w),RepositoryResolveOptions.defaults(config(base)),TOKEN)).reason());
            assertEquals(1,w.state().sourceRevisions().size());
            var service=new RepositoryClosureService(loader(contents),new InMemoryContentStore());
            var update=prepared(service.resolve(w,request(w),RepositoryResolveOptions.defaults(config(base)),TOKEN));
            var before=w.configuration(); var source=w.source(id("memory:/root.ili")).orElseThrow();
            w.update(new SourceUpdate(source.id(),new SourceExpectation.At(source.revision()),new SourceChange.Replace(model("Root","2",""))));
            assertThrows(UpdateRejectedException.class,()->update.apply(w));
            assertEquals(before,w.configuration()); assertEquals(1,w.state().sourceRevisions().size());
            assertTrue(w.source(source.id()).orElseThrow().text().contains("VERSION \"2\""));
            try(var foreign=workspace(model("Root","1",""))) { assertThrows(IllegalArgumentException.class,()->update.apply(foreign)); }
        }
    }

    @Test void editingDuringFetchReturnsStaleAndCancellationReturnsNoBatch() {
        String base="https://repo.example/";var contents=closure(base);
        try(var w=workspace(model("Root","1","IMPORTS Middle;"))) {
            var mutate=(SourceLoader)(l,p,t)->{
                var loaded=loader(contents).load(l,p,t);
                if(l.location().endsWith("middle.ili")) {
                    var text=w.source(id("memory:/root.ili")).orElseThrow();
                    w.update(new SourceUpdate(text.id(),new SourceExpectation.At(text.revision()),new SourceChange.Replace(model("Root","2",""))));
                }
                return loaded;
            };
            assertInstanceOf(RepositoryResolutionOutcome.Stale.class,new RepositoryClosureService(mutate,new InMemoryContentStore())
                    .resolve(w,request(w),RepositoryResolveOptions.defaults(config(base)),TOKEN));
            assertEquals(1,w.state().sourceRevisions().size());
            assertEquals(CancellationReason.USER_REQUESTED,assertInstanceOf(RepositoryResolutionOutcome.Cancelled.class,
                    new RepositoryClosureService(loader(contents),new InMemoryContentStore()).resolve(w,request(w),
                            RepositoryResolveOptions.defaults(config(base)),CancellationToken.requested(CancellationReason.USER_REQUESTED))).reason());
        }
    }

    @Test void lockRejectsChangedIndexesAndUnlockedNewDependencies() {
        String base="https://repo.example/";var contents=closure(base);var service=new RepositoryClosureService(loader(contents),new InMemoryContentStore());ResolutionLock lock;
        try(var w=workspace(model("Root","1","IMPORTS Middle;"))) { lock=prepared(service.resolve(w,request(w),RepositoryResolveOptions.defaults(config(base)),TOKEN)).lock(); }
        contents.put(base+"ilimodels.xml",contents.get(base+"ilimodels.xml")+" ");
        try(var w=workspace(model("Root","1","IMPORTS Middle;"))) {
            assertEquals(RepositoryProblem.Reason.LOCK_MISMATCH,problem(service.resolve(w,request(w),new RepositoryResolveOptions(config(base),Map.of(),Map.of(),Optional.of(lock)),TOKEN)).reason());
        }
        contents.putAll(closure(base));
        var partial=new ResolutionLock(Map.of("Middle",lock.models().get("Middle")),lock.indexes());
        try(var w=workspace(model("Root","1","IMPORTS Middle;"))) {
            assertEquals(RepositoryProblem.Reason.LOCK_MISMATCH,problem(service.resolve(w,request(w),new RepositoryResolveOptions(config(base),Map.of(),Map.of(),Optional.of(partial)),TOKEN)).reason());
        }
    }

    @Test void workspaceOverridesKeepIdentityAndAreNotReplacedByDownloads() {
        String base="https://repo.example/";var contents=closure(base);var calls=new AtomicInteger();
        try(var w=workspace(model("Root","1","IMPORTS Middle;DOMAIN D EXTENDS Middle.D=TEXT*7;"))) {
            w.update(add(base+"middle.ili",model("Middle","local","DOMAIN D=TEXT*12;")));
            var update=prepared(new RepositoryClosureService((l,p,t)->{calls.incrementAndGet();return loader(contents).load(l,p,t);},new InMemoryContentStore())
                    .resolve(w,request(w),RepositoryResolveOptions.defaults(config(base)),TOKEN));
            assertTrue(update.sources().isEmpty());assertEquals(0,calls.get());update.apply(w);
            assertEquals("local",((ModelSymbol)named(compile(w,"Root").schema().semanticGraph(),"Middle")).modelVersion());
        }
    }

    @Test void completeFrozenClosureNeedsNoIndexOrProviderEvenOfflineAndRetainsEarlierRemoteLock() {
        String base="https://repo.example/";var service=new RepositoryClosureService(loader(closure(base)),new InMemoryContentStore());
        try(var w=workspace(model("Root","1","IMPORTS Middle;"))) {
            var first=prepared(service.resolve(w,request(w),RepositoryResolveOptions.defaults(config(base)),TOKEN));first.apply(w);
            var offline=new RepositoryConfig(config(base).sources(),new FetchPolicy(true,10000,Duration.ofSeconds(1)));
            var noIO=new RepositoryClosureService((l,p,t)->{fail("complete frozen closure must not perform IO");return null;},new InMemoryContentStore());
            var again=prepared(noIO.resolve(w,request(w),RepositoryResolveOptions.defaults(offline),TOKEN));
            assertTrue(again.sources().isEmpty());assertEquals(first.lock(),again.lock());again.apply(w);compile(w,"Root");
        }
    }

    @Test void cacheOnlyConfigurationEditsAlsoPreventStaleBatchApplication() {
        String base="https://repo.example/";
        try(var w=workspace(model("Root","1","IMPORTS Middle;"))) {
            var update=prepared(new RepositoryClosureService(loader(closure(base)),new InMemoryContentStore())
                    .resolve(w,request(w),RepositoryResolveOptions.defaults(config(base)),TOKEN));
            var before=w.currentRevision();var config=w.configuration();
            var changed=new WorkspaceConfiguration(config.compilerOptions(),config.resolutionPolicy(),config.resolverCatalog(),new CacheLimits(2,1,2,1024));
            w.configure(changed,w.currentRevision());assertEquals(before,w.currentRevision());
            assertThrows(UpdateRejectedException.class,()->update.apply(w));
            assertEquals(changed,w.configuration());assertEquals(1,w.state().sourceRevisions().size());
        }
    }

    @Test void externalOverlayOriginalAndItsImportsAreDownloadedByTheSameHostLoop() {
        String base="https://repo.example/";
        var contents=new HashMap<>(Map.of(base+"ilimodels.xml",index(entry("Original","1","original.ili"),entry("Leaf","1","leaf.ili")),
                base+"original.ili",model("Original","1","IMPORTS Leaf;DOMAIN D=TEXT*7;"),base+"leaf.ili",model("Leaf","1","DOMAIN P=TEXT;")));
        try(var w=workspace(model("Root","1","IMPORTS Leaf;DOMAIN D=TEXT*7;"))) {
            var config=w.configuration();var options=config.compilerOptions();
            var origin=new SemanticOverlays.OverlaySource("project",4);
            var overlay=new SemanticOverlays.SemanticOverlay.TranslationOverlay(origin,SemanticOverlays.Target.named("Root"),SemanticOverlays.Target.named("Original"));
            options=new CompilerOptions(options.semanticProfile(),options.allowedLanguages(),options.warningPolicy(),options.budget(),new SemanticOverlays(List.of(overlay)));
            w.configure(new WorkspaceConfiguration(options,config.resolutionPolicy(),config.resolverCatalog(),config.cacheLimits()),w.currentRevision());
            var update=prepared(new RepositoryClosureService(loader(contents),new InMemoryContentStore()).resolve(w,request(w),RepositoryResolveOptions.defaults(config(base)),TOKEN));
            assertEquals(Set.of("Leaf","Original"),update.lock().models().keySet());update.apply(w);
            var graph=compile(w,"Root").schema().semanticGraph();var root=(ModelSymbol)named(graph,"Root");
            assertEquals(named(graph,"Original").id(),root.translation().orElseThrow().sourceTarget().orElseThrow());
            assertEquals(origin.origin(),root.translation().orElseThrow().origin().orElseThrow());
            assertEquals(List.of("Leaf"),root.imports().stream().map(ModelImport::importedModel).toList());
        }
    }

    @Test void twoExplicitOriginalVersionsRetainTheirIdentitiesAndBothReplayOffline() {
        for(String language:List.of("2.3","2.4")) {
            String base="https://repo.example/";
            String roots=model("F1","1","DOMAIN D=TEXT;").replace("VERSION \"1\" =","VERSION \"1\" TRANSLATION OF Original[\"1\"] =")
                    +model("F2","1","DOMAIN D=TEXT*7;").replace("INTERLIS 2.4;", "").replace("VERSION \"1\" =","VERSION \"1\" TRANSLATION OF Original[\"2\"] =");
            roots=roots.replace("INTERLIS 2.4;","INTERLIS "+language+";");
            var contents=new HashMap<>(Map.of(base+"ilimodels.xml",index(entry("Original","1","one.ili"),entry("Original","2","two.ili")).replace("ili2_4",language.equals("2.3")?"ili2_3":"ili2_4"),
                    base+"one.ili",model("Original","1","DOMAIN D=TEXT;").replace("INTERLIS 2.4;","INTERLIS "+language+";"),
                    base+"two.ili",model("Original","2","DOMAIN D=TEXT*7;").replace("INTERLIS 2.4;","INTERLIS "+language+";")));
            var cache=new InMemoryContentStore();ResolutionLock lock;
            try(var w=workspace(roots)) {
                var update=prepared(new RepositoryClosureService(loader(contents),cache).resolve(w,request(w),RepositoryResolveOptions.defaults(config(base)),TOKEN));
                assertEquals(List.of("1","2"),update.lock().models().get("Original").stream().map(ModelPin::modelVersion).toList());
                lock=new ResolutionLockCodec().decode(new ResolutionLockCodec().encode(update.lock()));assertEquals(update.lock(),lock);
                update.apply(w);assertBothOriginals(w,base,List.of("1","2"));
            }
            var offline=new RepositoryConfig(config(base).sources(),new FetchPolicy(true,10000,Duration.ofSeconds(1)));
            try(var w=workspace(roots)) {
                var update=prepared(new RepositoryClosureService((l,p,t)->{fail("offline provider called");return null;},cache).resolve(w,request(w),
                        new RepositoryResolveOptions(offline,Map.of(),Map.of(),Optional.of(lock)),TOKEN));
                assertEquals(lock,update.lock());update.apply(w);assertBothOriginals(w,base,List.of("1","2"));
            }
        }
    }

    private static void assertBothOriginals(Workspace w,String base,List<String> versions) {
        for(int i=0;i<2;i++) {
            var graph=compile(w,"F"+(i+1)).schema().semanticGraph();
            var translated=(ModelSymbol)named(graph,"F"+(i+1));
            var original=(ModelSymbol)graph.symbol(translated.translation().orElseThrow().sourceTarget().orElseThrow()).orElseThrow();
            assertEquals(versions.get(i),original.modelVersion());
            assertEquals(id(base+(i==0?"one.ili":"two.ili")),original.header().nameRange().orElseThrow().source());
        }
        // Procurement retains both exact inputs; language validity is still the compiler's decision.
        // The existing MODEL_NAME_UNIQUE rule deliberately rejects their combined closure.
        var combined=w.compile(new WorkspaceCompileRequest(w.currentRevision(),List.of(new ModelSelector.BySource(id("memory:/root.ili")))),TOKEN);
        var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,combined);
        assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),rejected.rejection().reasons());
        assertTrue(rejected.analysis().diagnostics().stream().anyMatch(d->d.code().value().equals("ILI-SEM-0010")
                &&d.arguments().toString().contains("MODEL_NAME_UNIQUE")));
    }

    @Test void twoExplicitOriginalSourcesWithOneVersionRemainSeparatePerTranslation() {
        String base="https://repo.example/";
        var contents=Map.of(base+"ilimodels.xml",index(entry("Original","1","one.ili"),entry("Original","1","two.ili")),
                base+"one.ili",model("Original","1","DOMAIN D=TEXT;"),base+"two.ili",model("Original","1","DOMAIN D=TEXT*7;"));
        String roots=model("F1","1","DOMAIN D=TEXT;")+model("F2","1","DOMAIN D=TEXT*7;").replace("INTERLIS 2.4;", "");
        try(var w=workspace(roots)) {
            var config=w.configuration();var d=config.compilerOptions();
            var overlays=new ArrayList<SemanticOverlays.SemanticOverlay>();
            for(int i=0;i<2;i++)overlays.add(new SemanticOverlays.SemanticOverlay.TranslationOverlay(new SemanticOverlays.OverlaySource("original-"+i,1),
                    SemanticOverlays.Target.named("F"+(i+1)),new SemanticOverlays.Target(SemanticOverlays.Target.named("Original").name(),Optional.of("1"),Optional.of(id(base+(i==0?"one.ili":"two.ili"))))));
            var options=new CompilerOptions(d.semanticProfile(),d.allowedLanguages(),d.warningPolicy(),d.budget(),new SemanticOverlays(overlays));
            w.configure(new WorkspaceConfiguration(options,config.resolutionPolicy(),config.resolverCatalog(),config.cacheLimits()),w.currentRevision());
            var update=prepared(new RepositoryClosureService(loader(contents),new InMemoryContentStore()).resolve(w,request(w),RepositoryResolveOptions.defaults(config(base)),TOKEN));
            assertEquals(2,update.lock().models().get("Original").size());
            assertEquals(update.lock(),new ResolutionLockCodec().decode(new ResolutionLockCodec().encode(update.lock())));
            update.apply(w);assertBothOriginals(w,base,List.of("1","1"));
        }
    }

    @Test void namedMissingRootIsDiscoveredAndCyclicImportsTerminateWithoutPretendingSemanticValidity() {
        String base="https://repo.example/";var contents=new HashMap<>(Map.of(base+"ilimodels.xml",index(entry("A","1","a.ili"),entry("B","1","b.ili")),
                base+"a.ili",model("A","1","IMPORTS B;"),base+"b.ili",model("B","1","IMPORTS A;")));
        try(var w=Workspaces.create(CompilerEngines.create(),WorkspaceConfiguration.defaults())) {
            var roots=List.<ModelSelector>of(new ModelSelector.ByName("A",Optional.empty(),Optional.empty()));
            var update=prepared(new RepositoryClosureService(loader(contents),new InMemoryContentStore()).resolve(w,
                    new AnalysisRequest(w.currentRevision(),roots),RepositoryResolveOptions.defaults(config(base)),TOKEN));
            assertEquals(Set.of("A","B"),update.lock().models().keySet());update.apply(w);
            var outcome=w.compile(new WorkspaceCompileRequest(w.currentRevision(),roots),TOKEN);
            var g=assertInstanceOf(CompilationOutcome.Succeeded.class,outcome).schema().semanticGraph();
            assertEquals(named(g,"B").id(),((ModelSymbol)named(g,"A")).imports().getFirst().targetModel().orElseThrow());
        }
    }
}

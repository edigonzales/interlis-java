package guru.interlis.repository;

import guru.interlis.compiler.*;
import guru.interlis.execution.*;
import guru.interlis.model.*;
import guru.interlis.source.*;
import guru.interlis.workspace.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.repository.RepositoryClosureContractTest.*;

class RepositoryParityIntegrationTest {
    static String fixture(String name,String sha) throws Exception {
        try(var in=Objects.requireNonNull(RepositoryParityIntegrationTest.class.getResourceAsStream("/repository-parity/"+name))) {
            var bytes=name.endsWith(".gz")?new GZIPInputStream(in).readAllBytes():in.readAllBytes();
            assertEquals(sha,ContentDigest.sha256(bytes).value());return new String(bytes,StandardCharsets.UTF_8);
        }
    }
    @Test void officialMotherAndDirectRepositoriesChooseTheSameCurrentGeometryAndReplayOffline() throws Exception {
        String mother="https://models.interlis.ch/", admin="https://models.geo.admin.ch/";
        String geometry=fixture("geometry.ili","f6979b3e349ddb30601a01a6d0e03c5285add8cc63c6f3d99b72101e16a82fbd");
        String adminIndex=fixture("admin-index.xml.gz","c2ce3bfce1cc574b42791304caf5d52b64ef18a28c26c52bb8e6e3b75c740af5");
        var material=new HashMap<>(Map.of(mother+"ilimodels.xml",fixture("interlis-index.xml.gz","2defccf0152978dd2a5b46bc4fa95ca818bb3a66813f5fb99d89c1fb54507853"),
                mother+"ilisite.xml",fixture("interlis-site.xml","27845ecacfbb72c0c584ecbd99295def618f0af22cf939a6a5fbb5b2800b81ac"),
                admin+"ilimodels.xml",adminIndex,"http://models.geo.admin.ch/ilimodels.xml",adminIndex,
                admin+"CH/CHBase_Part1_GEOMETRY_V1.ili",geometry,"http://models.geo.admin.ch/CH/CHBase_Part1_GEOMETRY_V1.ili",geometry));
        material.put(admin+"ilisite.xml",fixture("admin-site.xml","6a4b66bb3408179224c2ae3b4f2ccbbcb0643c295cc5d9e597fc1d55c2f7625e"));
        material.put(mother+"refhb23/Units-20120220.ili",fixture("Units.ili","45a233d3f2a681f2573965fb9ee6a98d279bdadbca94107874a22aa0c733a92d"));
        material.put(mother+"refhb23/CoordSys.ili",fixture("CoordSys.ili","7a7ce05144b6cc49d9f71aeca4c171cff79eaa24de8eee2de9ad5a75ddae8d7e"));
        for(var e:new ArrayList<>(material.entrySet())) if(e.getKey().startsWith("https:"))material.put(e.getKey().replace("https:","http:"),e.getValue());
        String root="INTERLIS 2.3; MODEL Root AT \"x\" VERSION \"1\" = IMPORTS GeometryCHLV95_V1; STRUCTURE P=point:GeometryCHLV95_V1.Coord2; END P; END Root.";
        for(String start:List.of(mother,admin)) {
            var cache=new InMemoryContentStore();ResolutionLock lock;
            try(var w=workspace(root)) {
                var update=prepared(new RepositoryClosureService(loader(material),cache).resolve(w,request(w),RepositoryResolveOptions.defaults(config(start)),TOKEN));
                var pin=update.lock().models().get("GeometryCHLV95_V1").getFirst();
                assertEquals("2017-12-04",pin.modelVersion());assertTrue(pin.source().value().endsWith("/CH/CHBase_Part1_GEOMETRY_V1.ili"));
                lock=new ResolutionLockCodec().decode(new ResolutionLockCodec().encode(update.lock()));assertEquals(2,lock.formatVersion());
                update.apply(w);compile(w,"Root");
            }
            var offline=new RepositoryConfig(config(start).sources(),new FetchPolicy(true, 2_000_000, Duration.ofSeconds(1), Duration.ofSeconds(1), java.util.Optional.of(Duration.ofSeconds(1))));
            try(var w=workspace(root)) {
                var update=prepared(new RepositoryClosureService((l,p,t)->{fail("offline IO");return null;},cache)
                        .resolve(w,request(w),new RepositoryResolveOptions(offline,Map.of(),Map.of(),Optional.of(lock)),TOKEN));
                assertEquals(lock,update.lock());update.apply(w);compile(w,"Root");
            }
        }
    }
    @Test void adjustmentsReachTheCompilerGateAndLockReplayRetainsWarnings() {
        String base="https://repo.example/";var contents=Map.of(base+"ilimodels.xml",index(entry("Dep","1","one.ili"),entry("Dep","2","two.ili")),base+"one.ili",model("Dep","1","DOMAIN D=TEXT;"));
        var cache=new InMemoryContentStore();ResolutionLock lock;
        try(var w=workspace(model("Root","1","IMPORTS Dep;"))) {
            var c=w.configuration();var o=c.compilerOptions();
            w.configure(new WorkspaceConfiguration(new CompilerOptions(o.semanticProfile(),o.allowedLanguages(),WarningPolicy.REJECT_WARNINGS,o.budget(),o.overlays()),c.resolutionPolicy(),c.resolverCatalog(),c.cacheLimits()),w.currentRevision());
            var update=prepared(new RepositoryClosureService(loader(contents),cache).resolve(w,request(w),RepositoryResolveOptions.defaults(config(base)),TOKEN));
            lock=update.lock();update.apply(w);
            var out=assertInstanceOf(CompilationOutcome.Rejected.class,w.compile(new WorkspaceCompileRequest(w.currentRevision(),request(w).roots()),TOKEN));
            assertEquals(Set.of(RejectionReason.WARNINGS_REJECTED),out.rejection().reasons());
            assertTrue(out.analysis().diagnostics().stream().anyMatch(d->d.code().value().equals("ILI-RES-0008")));
        }
        try(var w=workspace(model("Root","1","IMPORTS Dep;"))) {
            var offline=new RepositoryConfig(config(base).sources(),new FetchPolicy(true, 10000, Duration.ofSeconds(1), Duration.ofSeconds(1), java.util.Optional.of(Duration.ofSeconds(1))));
            var update=prepared(new RepositoryClosureService((l,p,t)->{fail("offline IO");return null;},cache).resolve(w,request(w),new RepositoryResolveOptions(offline,Map.of(),Map.of(),Optional.of(lock)),TOKEN));
            assertEquals(lock,update.lock());update.apply(w);assertEquals(1,compile(w,"Root").analysis().diagnostics().size());
        }
    }
    @Test void legacyLockKeepsStrictVersionAmbiguityAndNoImplicitUpgrade() {
        String base="https://repo.example/";String text=model("Dep","1","DOMAIN D=TEXT;");
        var material=Map.of(base+"ilimodels.xml",index(entry("Dep","1","one.ili")),base+"one.ili",text);
        var old=new ResolutionLock(Map.of("Dep",List.of(new ModelPin("Dep","1",id(base+"one.ili"),ContentDigest.sha256(text.getBytes(StandardCharsets.UTF_8))))),Map.of(id(base+"ilimodels.xml"),ContentDigest.sha256(material.get(base+"ilimodels.xml").getBytes(StandardCharsets.UTF_8))));
        try(var w=workspace(model("Root","1","IMPORTS Dep;"))) {
            var update=prepared(new RepositoryClosureService(loader(material),new InMemoryContentStore()).resolve(w,request(w),new RepositoryResolveOptions(config(base),Map.of(),Map.of(),Optional.of(old)),TOKEN));
            assertEquals(old,update.lock());assertTrue(new ResolutionLockCodec().encode(update.lock()).contains("version=\"1\""));
        }
    }
    @Test void rejectedImportUsesTheOriginalWorkspaceRevisionAfterAnEdit() {
        String text=model("Root","1","IMPORTS Missing;");
        try(var w=workspace(text)) {
            var source=id("memory:/root.ili");
            w.update(new SourceUpdate(source,new SourceExpectation.At(new SourceRevision(1)),new SourceChange.Replace("!! edited\n"+text)));
            var out=assertInstanceOf(RepositoryResolutionOutcome.Rejected.class,new RepositoryClosureService(loader(Map.of("https://repo.example/ilimodels.xml",index(entry("Other","1","other.ili")))),new InMemoryContentStore())
                    .resolve(w,request(w),RepositoryResolveOptions.defaults(config("https://repo.example/")),TOKEN));
            assertEquals(w.source(source).orElseThrow().revision(),out.diagnostics().getLast().primaryRange().orElseThrow().revision());
            assertEquals(w.source(source).orElseThrow().text(),out.sources().get(source).text());
        }
    }

    @Test void aLockForAnExtendedAlreadyLoadedClosureReplaysInAFreshWorkspace() {
        String a="https://a.example/",b="https://b.example/";
        var contents=Map.of(a+"ilimodels.xml",index(entry("Dep","1","dep.ili")),a+"dep.ili",model("Dep","1",""),
                b+"ilimodels.xml",index(entry("NewDep","1","new.ili")),b+"new.ili",model("NewDep","1",""));
        var config=new RepositoryConfig(List.of(source(a),source(b)),FetchPolicy.defaults());
        var cache=new InMemoryContentStore();ResolutionLock lock;
        String edited=model("Root","1","IMPORTS Dep, NewDep;");
        try(var w=workspace(model("Root","1","IMPORTS Dep;"))) {
            var service=new RepositoryClosureService(loader(contents),cache);
            prepared(service.resolve(w,request(w),RepositoryResolveOptions.defaults(config),TOKEN)).apply(w);
            var id=id("memory:/root.ili");w.update(new SourceUpdate(id,new SourceExpectation.At(w.source(id).orElseThrow().revision()),new SourceChange.Replace(edited)));
            var next=prepared(service.resolve(w,request(w),RepositoryResolveOptions.defaults(config),TOKEN));lock=next.lock();next.apply(w);compile(w,"Root");
            assertEquals(2,lock.indexes().size());assertEquals(Set.of("Dep","NewDep"),lock.models().keySet());
        }
        try(var w=workspace(edited)) {
            var offline=new RepositoryConfig(config.sources(),new FetchPolicy(true, 10000, Duration.ofSeconds(1), Duration.ofSeconds(1), java.util.Optional.of(Duration.ofSeconds(1))));
            var update=prepared(new RepositoryClosureService((l,p,t)->{fail("offline IO");return null;},cache).resolve(w,request(w),new RepositoryResolveOptions(offline,Map.of(),Map.of(),Optional.of(lock)),TOKEN));
            assertEquals(lock,update.lock());update.apply(w);compile(w,"Root");
        }
    }

}

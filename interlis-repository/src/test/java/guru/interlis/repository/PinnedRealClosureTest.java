package guru.interlis.repository;

import guru.interlis.compiler.*;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.*;
import guru.interlis.workspace.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import static guru.interlis.repository.RepositoryClosureContractTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exact official bytes, original indices and public schema assertions, without live server dependencies. */
class PinnedRealClosureTest {
    static final String MODELS="https://models.interlis.ch/";
    static final String ADMIN="https://models.geo.admin.ch/";
    static byte[] bytes(String file, String sha) throws Exception {
        try(var input=Objects.requireNonNull(PinnedRealClosureTest.class.getResourceAsStream("/closures/"+file))) {
            byte[] bytes=file.endsWith(".gz")?new GZIPInputStream(input).readAllBytes():input.readAllBytes();
            assertEquals(sha,ContentDigest.sha256(bytes).value(),file);return bytes;
        }
    }
    static ModelPin pin(String name,String version,String uri,byte[] bytes) {
        return new ModelPin(name,version,id(uri),ContentDigest.sha256(bytes));
    }
    static Map<String,String> texts(Map<String,byte[]> bytes) {
        var texts=new HashMap<String,String>();bytes.forEach((uri,content)->texts.put(uri,new String(content,StandardCharsets.UTF_8)));return texts;
    }
    static CompiledSchema resolveAndCompile(String root,RepositoryConfig repositories,Map<String,byte[]> material,
                                            Map<String,ModelPin> pins) {
        try(var w=Workspaces.create(CompilerEngines.create(),WorkspaceConfiguration.defaults())) {
            var roots=List.<ModelSelector>of(new ModelSelector.ByName(root,Optional.empty(),Optional.empty()));
            var indexes=new HashMap<SourceId,ContentDigest>();repositories.sources().forEach(source->
                    indexes.put(id(source.indexLocator().location()),ContentDigest.sha256(material.get(source.indexLocator().location()))));
            var update=prepared(new RepositoryClosureService(loader(texts(material)),new InMemoryContentStore()).resolve(w,
                    new AnalysisRequest(w.currentRevision(),roots),new RepositoryResolveOptions(repositories,pins,indexes,Optional.empty()),TOKEN));
            assertEquals(pins,update.lock().models().entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,e->e.getValue().getFirst())));assertEquals(indexes,update.lock().indexes());update.apply(w);
            var capture=w.capture(new AnalysisRequest(w.currentRevision(),roots));assertTrue(capture.resolution().complete());
            var out=compile(w,root);var fresh=CompilerEngines.create().compile(capture.request(),TOKEN);
            var freshOk=assertInstanceOf(CompilationOutcome.Succeeded.class,fresh);
            assertEquals(out.schema().fingerprint(),freshOk.schema().fingerprint());
            assertEquals(out.schema().semanticGraph().qualifiedNames().keySet(),freshOk.schema().semanticGraph().qualifiedNames().keySet());
            var reused=CompilerEngines.create().compile(capture.request(),new AnalysisReuse(Optional.of(out.analysis()),out.analysis().syntaxSnapshots()),TOKEN);
            assertEquals(out.schema().fingerprint(),assertInstanceOf(CompilationOutcome.Succeeded.class,reused).schema().fingerprint());
            return out.schema();
        }
    }

    @Test void officialIli23RoadsClosureRetainsImportedBaseEffectiveMembersAndEnumOrder() throws Exception {
        var index=bytes("ilimodels.xml","2defccf0152978dd2a5b46bc4fa95ca818bb3a66813f5fb99d89c1fb54507853");
        var base=bytes("refhb23/RoadsExdm2ben-20050616.ili","c12ec56358fb50b1d0579c812bfbc67da07dd4c9d16ab250145a5cd77b484c97");
        var root=bytes("refhb23/RoadsExdm2ien-20170405.ili","6a18f8a3eff8ef71a064a85353c49ca08dc5a67768cf0d4728da27f802c32cdc");
        String baseUri=MODELS+"refhb23/RoadsExdm2ben-20050616.ili",rootUri=MODELS+"refhb23/RoadsExdm2ien-20170405.ili";
        var schema=resolveAndCompile("RoadsExdm2ien",config(MODELS),Map.of(MODELS+"ilimodels.xml",index,baseUri,base,rootUri,root),
                Map.of("RoadsExdm2ben",pin("RoadsExdm2ben","2005-06-16",baseUri,base),"RoadsExdm2ien",pin("RoadsExdm2ien","2017-04-05",rootUri,root)));
        var g=schema.semanticGraph();
        var rootModel=(ModelSymbol)named(g,"RoadsExdm2ien");
        assertEquals(named(g,"RoadsExdm2ben").id(),rootModel.imports().getFirst().targetModel().orElseThrow());
        var axis=(ClassSymbol)named(g,"RoadsExdm2ien.RoadsExtended.StreetAxis");
        assertEquals(named(g,"RoadsExdm2ben.Roads.StreetAxis").id(),axis.baseType().orElseThrow());
        assertEquals(List.of("Geometry","Precision"),g.effectiveMembers(axis.id()).stream().map(m->g.symbol(m.declaredSymbol()).orElseThrow().name().value()).toList());
        var precision=(AttributeSymbol)named(g,"RoadsExdm2ien.RoadsExtended.StreetAxis.Precision");
        assertTrue(precision.mandatory());
        var enumeration=assertInstanceOf(EnumerationType.class,g.type(precision.declaredType()).orElseThrow());
        assertEquals(List.of("precise","unprecise"),enumeration.values().stream().map(EnumerationValue::path).toList());
        assertEquals(id(rootUri),precision.header().nameRange().orElseThrow().source());
        assertEquals(Set.of(LanguageVersion.ILI23),g.languageVersions());
    }

    @Test void officialIli24MultiModelThreeLevelClosureRetainsStructuresConstraintsAndTargets() throws Exception {
        var index=bytes("geo-admin-index.xml.gz","c2ce3bfce1cc574b42791304caf5d52b64ef18a28c26c52bb8e6e3b75c740af5");
        var content=bytes("CHBase_Part2_LOCALISATION_V2.ili","1b592fce43751c4df221a0f85df3d0c8b669612b8bbc167d9c78bb8f7d7e0857");
        String uri=ADMIN+"CH/CHBase_Part2_LOCALISATION_V2.ili";
        var pins=new HashMap<String,ModelPin>();for(String name:List.of("LocalisationCH_V2","Localisation_V2","InternationalCodes_V2"))pins.put(name,pin(name,"2021-01-06",uri,content));
        var schema=resolveAndCompile("LocalisationCH_V2",config(ADMIN),Map.of(ADMIN+"ilimodels.xml",index,uri,content),pins);
        var g=schema.semanticGraph();
        var local=(ModelSymbol)named(g,"Localisation_V2");var ch=(ModelSymbol)named(g,"LocalisationCH_V2");
        assertEquals(named(g,"InternationalCodes_V2").id(),local.imports().getFirst().targetModel().orElseThrow());
        assertEquals(local.id(),ch.imports().getFirst().targetModel().orElseThrow());
        var original=(StructureSymbol)named(g,"Localisation_V2.LocalisedText");
        var translated=(StructureSymbol)named(g,"LocalisationCH_V2.LocalisedText");
        assertEquals(original.id(),translated.baseType().orElseThrow());assertEquals(1,translated.constraints().size());
        assertEquals(List.of("Language","Text"),g.effectiveMembers(translated.id()).stream().map(m->g.symbol(m.declaredSymbol()).orElseThrow().name().value()).toList());
        var text=(AttributeSymbol)named(g,"Localisation_V2.LocalisedText.Text");assertTrue(text.mandatory());
        assertEquals(TextKind.TEXT,assertInstanceOf(TextType.class,g.type(text.declaredType()).orElseThrow()).textKind());
        var codes=(DomainSymbol)named(g,"InternationalCodes_V2.LanguageCode_ISO639_1");
        var enumeration=assertInstanceOf(EnumerationType.class,g.type(codes.typeId()).orElseThrow());
        assertEquals(List.of("de","fr","it","rm","en"),enumeration.values().subList(0,5).stream().map(EnumerationValue::path).toList());
        assertEquals(139,enumeration.values().size());assertEquals(Set.of(LanguageVersion.ILI24),g.languageVersions());
        assertEquals(id(uri),translated.header().nameRange().orElseThrow().source());
        // The file also contains unrelated Dictionaries models. They are not certified by this selected closure.
        assertFalse(g.qualifiedNames().containsKey(new QualifiedName(List.of("DictionariesCH_V2"))));
    }
}

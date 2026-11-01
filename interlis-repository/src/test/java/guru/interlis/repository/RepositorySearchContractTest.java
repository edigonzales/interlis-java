package guru.interlis.repository;

import guru.interlis.compiler.ModelSelector;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.LanguageVersion;
import java.net.URI;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.repository.RepositoryClosureContractTest.*;

class RepositorySearchContractTest {
    static final String A="https://a.example/", B="https://b.example/", P="https://p.example/", C="https://c.example/";
    static String site(List<String> parents,List<String> children) {
        return "<TRANSFER><IliSite09.SiteMetadata.Site>"+links("parentSite",parents)+links("subsidiarySite",children)+"</IliSite09.SiteMetadata.Site></TRANSFER>";
    }
    static String links(String name,List<String> values) {
        return "<"+name+">"+values.stream().map(v->"<IliSite09.RepositoryLocation_><value>"+v+"</value></IliSite09.RepositoryLocation_>").reduce("",String::concat)+"</"+name+">";
    }
    static RepositorySearch search(Map<String,String> contents,List<String> bases,List<String> requests) {
        SourceLoader delegate=loader(contents);
        return new RepositorySearch((l,p,t)->{requests.add(l.location()); return delegate.load(l,p,t);},
                new RepositoryConfig(bases.stream().map(RepositoryClosureContractTest::source).toList(),FetchPolicy.defaults()),TOKEN,10000);
    }
    static ModelSelector.ByName query() { return new ModelSelector.ByName("Dep",Optional.empty(),Optional.empty()); }
    @Test void followsConfiguredThenParentThenSubsidiaryOrderAndStopsBeforeReadingUnneededSites() {
        var data=new HashMap<String,String>();
        data.put(A+"ilimodels.xml",index(entry("Other","1","other.ili")));
        data.put(B+"ilimodels.xml",index(entry("Other","1","other.ili")));
        data.put(P+"ilimodels.xml",index(entry("Dep","1","dep.ili")));
        data.put(A+"ilisite.xml",site(List.of(P),List.of(C)));
        data.put(B+"ilisite.xml",site(List.of(),List.of()));
        var calls=new ArrayList<String>(); var session=search(data,List.of(A,B),calls);
        assertEquals(P+"dep.ili",session.find(query(),Optional.of(LanguageVersion.ILI24)).orElseThrow().metadata().location().toString());
        assertEquals(List.of(A+"ilimodels.xml",B+"ilimodels.xml",A+"ilisite.xml",B+"ilisite.xml",P+"ilimodels.xml"),calls);
    }
    @Test void visitsSubsidiaryParentsBeforeNextSiblingAndTerminatesCycles() {
        var data=new HashMap<String,String>();
        for(String base:List.of(A,B,C,P))data.put(base+"ilimodels.xml",index(entry("Other","1","other.ili")));
        data.put(A+"ilisite.xml",site(List.of(),List.of(B,C)));
        data.put(B+"ilisite.xml",site(List.of(P),List.of(A)));
        data.put(P+"ilisite.xml",site(List.of(A),List.of()));
        data.put(C+"ilisite.xml",site(List.of(),List.of(B)));
        var calls=new ArrayList<String>();var session=search(data,List.of(A),calls);
        assertTrue(session.find(query(),Optional.of(LanguageVersion.ILI24)).isEmpty());
        assertEquals(List.of(A,B,P,C),session.steps().stream().filter(s->s.kind()==RepositorySearchStep.Kind.LOOKUP).map(s->s.repository().toString()).toList());
        assertEquals(calls.size(),new HashSet<>(calls).size());
    }
    @Test void selectsChainNotLexicalLatestAndHonoursExplicitOldVersionAndLanguage() {
        var data=Map.of(A+"ilimodels.xml",index(entry("Dep","z","old.ili"),
                entry("Dep","a","new.ili").replace("</File>","</File><precursorVersion>z</precursorVersion>"),
                entry("Dep","999","other.ili").replace("ili2_4","ili2_3")));
        var session=search(data,List.of(A),new ArrayList<>());
        assertEquals("a",session.find(query(),Optional.of(LanguageVersion.ILI24)).orElseThrow().metadata().version());
        assertEquals("z",session.find(new ModelSelector.ByName("Dep",Optional.of("z"),Optional.empty()),Optional.of(LanguageVersion.ILI24)).orElseThrow().metadata().version());
    }
    @Test void malformedChainsFollowIndexOrderAndExplainIgnoredEntries() {
        var data=Map.of(A+"ilimodels.xml",index(entry("Dep","1","a.ili"),entry("Dep","other","b.ili"),
                entry("Dep","2","c.ili").replace("</File>","</File><precursorVersion>1</precursorVersion>"),
                entry("Dep","3","d.ili").replace("</File>","</File><precursorVersion>1</precursorVersion>"),
                entry("Dep","4","e.ili").replace("</File>","</File><precursorVersion>missing</precursorVersion>")));
        var session=search(data,List.of(A),new ArrayList<>());
        assertEquals("2",session.find(query(),Optional.of(LanguageVersion.ILI24)).orElseThrow().metadata().version());
        assertEquals(List.of(RepositorySearchStep.Kind.DUPLICATE_ROOT,RepositorySearchStep.Kind.DUPLICATE_SUCCESSOR,RepositorySearchStep.Kind.BROKEN_CHAIN),session.steps().stream().filter(RepositorySearchStep::warning).map(RepositorySearchStep::kind).toList());
    }
    @Test void excludesBrowseOnlyAndDoesNotChooseARootlessCycle() {
        var data=Map.of(A+"ilimodels.xml",index(entry("Dep","1","a.ili").replace("</File>","</File><browseOnly>true</browseOnly>"),entry("Dep","2","b.ili").replace("</File>","</File><precursorVersion>2</precursorVersion>")));
        var session=search(data,List.of(A),new ArrayList<>());
        assertTrue(session.find(query(),Optional.of(LanguageVersion.ILI24)).isEmpty());
        assertTrue(session.steps().stream().anyMatch(s->s.kind()==RepositorySearchStep.Kind.NO_ROOT));
    }
    @Test void siteReaderPreservesOrderAndRejectsEntitiesAndUnsafeSchemes() {
        var reader=new RepositorySiteReader();var base=new RepositoryBase(URI.create(A));
        assertEquals(List.of(URI.create(B),URI.create(P)),reader.read(new IndexPayload(site(List.of(B,P),List.of(C))),base).parents());
        assertThrows(RepositoryIndexException.class,()->reader.read(new IndexPayload("<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]><x>&e;</x>"),base));
        assertThrows(RepositoryIndexException.class,()->reader.read(new IndexPayload(site(List.of("javascript:evil"),List.of())),base));
    }
    @Test void missingRepositoryDoesNotPreventLaterConfiguredHitAndLimitsCancelWithoutAPartialResult() {
        var data=Map.of(B+"ilimodels.xml",index(entry("Dep","1","dep.ili")));
        var session=search(data,List.of(A,B),new ArrayList<>());
        assertEquals(B+"dep.ili",session.find(query(),Optional.of(LanguageVersion.ILI24)).orElseThrow().metadata().location().toString());
        assertTrue(session.steps().stream().anyMatch(s -> s.kind()==RepositorySearchStep.Kind.REPOSITORY_UNAVAILABLE));
        var limited=new RepositorySearch(loader(data),config(B),TOKEN,1);
        assertEquals(guru.interlis.execution.CancellationReason.RESOURCE_LIMIT,
                assertThrows(guru.interlis.execution.OperationCancelledException.class,()->limited.find(query(),Optional.empty())).reason());
        var warnings=new RepositorySearch(loader(Map.of()),config(A),TOKEN,100,0);
        assertThrows(guru.interlis.execution.OperationCancelledException.class,()->warnings.find(query(),Optional.empty()));
    }

    @Test void anEmptyStandardIndexCanActAsAMotherWithoutAnInvalidIndexWarning() {
        var data=Map.of(A+"ilimodels.xml","<TRANSFER><DATASECTION><IliRepository20.RepositoryIndex BID=\"b\"/></DATASECTION></TRANSFER>",
                A+"ilisite.xml",site(List.of(),List.of(B)),B+"ilimodels.xml",index(entry("Dep","1","dep.ili")));
        var session=search(data,List.of(A),new ArrayList<>());
        assertTrue(session.find(query(),Optional.of(LanguageVersion.ILI24)).isPresent());
        assertFalse(session.steps().stream().anyMatch(RepositorySearchStep::warning));
    }

}

package guru.interlis.cli.evidence;

import com.google.gson.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static guru.interlis.cli.evidence.EvidenceFiles.*;
import static org.junit.jupiter.api.Assertions.*;

/** Checks the manually reconciled existing worklist links, never accepts semantics by counting. */
class CompletionLinksTest {
    private static final String LINKS="testdata/g2/completion-links.json";
    private static void sameIds(List<JsonObject> actual,List<JsonObject> expected) {
        require(ids(actual,"id").equals(ids(expected,"id")),"Missing/foreign/duplicate worklist id");
    }
    private static void links(JsonObject document,List<JsonObject> rows,boolean required) {
        var bindings=document.getAsJsonObject("bindings");
        for(var row:rows) {
            var links=strings(row.get("links"));require(!required||!links.isEmpty(),"Missing executable links: "+s(row,"id"));
            require(new HashSet<>(links).size()==links.size()&&links.stream().allMatch(bindings::has),"Foreign/duplicate binding");
        }
    }
    static void validate(JsonObject document,boolean reports) throws Exception {
        require(s(document,"status").equals("ASSERTION_LINKS_NOT_AUTOMATIC_ACCEPTANCE"),"Invented acceptance claim");pins(document.getAsJsonObject("sourcePins"));
        var norm=new ArrayList<JsonObject>();for(var language:rows(data("norm-conditions"),"documents"))for(var condition:rows(language,"conditions"))if(s(condition,"kind").equals("COMPILER_RULE"))norm.add(condition);
        sameIds(rows(document,"conditions"),norm);
        for(var row:rows(document,"conditions")) {
            var original=norm.stream().filter(c->s(c,"id").equals(s(row,"id"))).findFirst().orElseThrow();require(s(row,"quoteSha256").equals(s(original,"quoteSha256")),"Changed norm quote");
            require(!row.has("pendingDetail")&&!s(row,"scope").isBlank(),"Unresolved compiler scope");
            if(s(row,"disposition").equals("ASSERTIONS_LINKED"))require(!strings(row.get("links")).isEmpty(),"Unlinked compiler clause");
            else require(s(row,"disposition").equals("OUTSIDE_G2_COMPILER"),"Unrecognised scope disposition");
        }
        sameIds(rows(document,"requirements"),rows(data("test-candidate-index"),"requirements"));
        var grammar=data("grammar-inventory");sameIds(rows(document,"grammar"),rows(grammar,"productions"));
        for(var row:rows(document,"grammar"))require(new LinkedHashSet<>(strings(row.get("occurrenceIds"))).equals(rows(grammar,"occurrences").stream().filter(o->s(o,"production").equals(s(row,"production"))).map(o->s(o,"id")).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))),"Missing grammar branch");
        sameIds(rows(document,"builtins"),rows(data("builtin-inventory"),"functions"));
        var catalogue=json("testdata/g2/predefined-catalogue.json");sameIds(rows(document,"predefined"),rows(catalogue,"declarations"));sameIds(rows(document,"special"),rows(catalogue,"specialValuesAndTypes"));
        var groups=document.getAsJsonObject("grammarGroups");
        for(var production:rows(document,"grammar"))require(groups.has(s(production,"group")),"Missing grammar family");
        links(document,groups.entrySet().stream().map(e->e.getValue().getAsJsonObject()).toList(),true);
        links(document,rows(document,"conditions"),false);for(String section:List.of("builtins","predefined","special"))links(document,rows(document,section),true);
        for(var row:rows(document,"requirements")) {
            require(!row.has("pendingDetail")&&(!strings(row.get("links")).isEmpty()||row.has("processEvidence")),"Unresolved requirement");
            if(row.has("processEvidence"))for(var item:rows(row,"processEvidence"))pin(s(item,"source"),s(item,"sha256"));
        }
        links(document,rows(document,"requirements"),false);
        var xmls=new HashMap<String,Set<AssertionEvidence.Binding>>();
        for(var entry:document.getAsJsonObject("bindings").entrySet()) {
            var binding=entry.getValue().getAsJsonObject();String full=text(s(binding,"source"));require(s(binding,"source").contains("/src/test/java/")&&s(binding,"source").endsWith("/"+s(binding,"suite").substring(s(binding,"suite").lastIndexOf('.')+1)+".java"),"Wrong test source");
            pin(s(binding,"source"),s(binding,"sourceSha256"));String body=slice(full,n(binding,"methodStart"),n(binding,"methodEnd"));
            require(hash(body).equals(s(binding,"methodSha256"))&&body.matches("(?s)@(Test|ParameterizedTest)\\b.*\\bvoid\\s+"+java.util.regex.Pattern.quote(s(binding,"method"))+"\\s*\\(.*"),"Wrong/missing test method");
            require(!strings(binding.get("observations")).isEmpty(),"Missing public observation");
            if(reports) {
                String suite=s(binding,"suite");String module=s(binding,"source").split("/")[0];
                if(!xmls.containsKey(suite)){var xml=ROOT.resolve(module+"/build/test-results/test/TEST-"+suite+".xml");xmls.put(suite,AssertionEvidence.xml(xml,hash(xml),Instant.EPOCH));}
                long matches=xmls.get(suite).stream().filter(b->b.suite().equals(suite)&&b.method().startsWith(s(binding,"method")+"(")).count();
                require(matches==(body.startsWith("@ParameterizedTest")?2:1),"Missing/foreign JUnit invocation: "+entry.getKey());
            }
        }
    }
    @Test void allExistingIdsHaveConcreteCurrentSourceAndExecutedPublicAssertions() throws Exception {validate(json(LINKS),true);}
    @ParameterizedTest @ValueSource(strings={"condition","branch","builtin","predefined","method","sourceHash"})
    void inventedOrLostLinksCannotAppearComplete(String kind) throws Exception {
        var changed=json(LINKS);
        switch(kind) {
            case "condition"->changed.getAsJsonArray("conditions").remove(0);
            case "branch"->rows(changed,"grammar").getFirst().getAsJsonArray("occurrenceIds").remove(0);
            case "builtin"->changed.getAsJsonArray("builtins").remove(0);
            case "predefined"->changed.getAsJsonArray("predefined").remove(0);
            case "method","sourceHash"->{var binding=changed.getAsJsonObject("bindings").entrySet().iterator().next().getValue().getAsJsonObject();binding.addProperty(kind.equals("method")?"method":"sourceSha256","invented");}
        }
        assertThrows(Exception.class,()->validate(changed,false));
    }
}

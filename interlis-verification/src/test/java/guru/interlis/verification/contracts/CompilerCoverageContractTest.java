package guru.interlis.verification.contracts;

import com.google.gson.*;
import guru.interlis.verification.VerificationLayout;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static guru.interlis.verification.contracts.ContractFiles.*;
import static org.junit.jupiter.api.Assertions.*;

/** Checks the manually reconciled existing worklist links, never accepts semantics by counting. */
class CompilerCoverageContractTest {
    private static final String LINKS="testdata/g2/completion-links.json";
    static JsonObject current() throws Exception {
        return guru.interlis.verification.CurrentContractSelection.read(root());
    }
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
    static void validateCoverage(JsonObject document) throws Exception {
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
    }
    static void validateSourceBindings(JsonObject document) throws Exception {
        for(var entry:document.getAsJsonObject("bindings").entrySet()) {
            var binding=entry.getValue().getAsJsonObject(); String source=s(binding,"source"),full=text(source);
            require(source.contains("/src/test/java/") && source.endsWith("/"+s(binding,"suite").replace('.','/')+".java"),"Wrong test source");
            pin(source,s(binding,"sourceSha256"));
            String body=slice(full,n(binding,"methodStart"),n(binding,"methodEnd"));
            require(hash(body).equals(s(binding,"methodSha256")) && body.matches("(?s)@(Test|ParameterizedTest)\\b.*\\bvoid\\s+"+java.util.regex.Pattern.quote(s(binding,"method"))+"\\s*\\(.*"),"Wrong/missing test method");
            require(!strings(binding.get("observations")).isEmpty(),"Missing public observation");
            var expected=strings(binding.get("invocations"));
            require(!expected.isEmpty() && new HashSet<>(expected).size()==expected.size(),"Empty/duplicate invocations");
            if(body.startsWith("@ParameterizedTest")) require(expected.size()>1,"Missing parameter invocation");
            else require(expected.equals(List.of(s(binding,"method")+"()")),"Foreign invocation");
        }
    }
    static void validateExecutedAssertions(JsonObject document) throws Exception {
        var modules=new TreeMap<String,Set<JUnitReportAssertions.Binding>>();
        for(var entry:document.getAsJsonObject("bindings").entrySet()) {
            var b=entry.getValue().getAsJsonObject();String module=s(b,"source").split("/")[0];
            var expected=modules.computeIfAbsent(module,k->new LinkedHashSet<>());
            for(String name:strings(b.get("invocations")))require(expected.add(new JUnitReportAssertions.Binding(s(b,"suite"),name)),"Duplicate invocation binding");
        }
        String candidate=git("rev-parse","HEAD"),tree=git("rev-parse","HEAD^{tree}");
        for(var module:modules.entrySet()) {
            var layout=VerificationLayout.forInvocation(root());
            var manifest=JsonParser.parseString(Files.readString(layout.contractAssertionManifest(module.getKey()))).getAsJsonObject();
            validateRunIdentity(manifest,candidate,tree,System.getProperty("contract.runId"));
            JUnitReportAssertions.fresh(module.getValue(),layout.junitDirectory(module.getKey(),"contractAssertionsTest"),
                    Instant.parse(s(manifest,"started")),manifest.getAsJsonObject("hashes"));
        }
    }
    static void validateRunIdentity(JsonObject manifest,String candidate,String tree,String runId) {
        require(s(manifest,"gitCommit").equals(candidate) && s(manifest,"treeSha").equals(tree),"Foreign assertion candidate");
        require(runId != null && s(manifest,"runId").equals(runId),"Stale assertion run");
    }
    @Test void duplicateForeignAndLostParameterBindingsAreRejected() throws Exception {
        validateCoverage(current());validateSourceBindings(current());
        for(String key:List.of("duplicate","foreign","parameter","path")) {
            var changed=current();
            if(key.equals("duplicate"))changed.getAsJsonArray("requirements").add(rows(changed,"requirements").getFirst().deepCopy());
            else if(key.equals("foreign"))rows(changed,"requirements").getFirst().addProperty("id","INVENTED-999");
            else if(key.equals("path"))changed.getAsJsonObject("bindings").entrySet().iterator().next().getValue().getAsJsonObject().addProperty("source","../outside");
            else changed.getAsJsonObject("bindings").getAsJsonObject("NestedBlockCommentContractTest.balancedCommentDelimitersPreserveTriviaAndFollowingDeclarations").getAsJsonArray("invocations").remove(1);
            assertThrows(IllegalArgumentException.class,()->{validateCoverage(changed);validateSourceBindings(changed);});
        }
        var manifest=new JsonObject();manifest.addProperty("gitCommit","candidate");manifest.addProperty("treeSha","tree");manifest.addProperty("runId","run");
        validateRunIdentity(manifest,"candidate","tree","run");
        for(String key:List.of("gitCommit","treeSha","runId")) {
            var changed=manifest.deepCopy();changed.addProperty(key,"foreign");
            assertThrows(IllegalArgumentException.class,()->validateRunIdentity(changed,"candidate","tree","run"));
        }
    }
    private static String git(String... args) throws Exception {
        var command=new ArrayList<String>();command.add("git");command.addAll(List.of(args));
        var process=new ProcessBuilder(command).directory(root().toFile()).start();
        String output=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).trim();
        require(process.waitFor()==0,"Cannot inspect candidate");return output;
    }
    @org.junit.jupiter.api.Tag("compiler-contract")
    @Test void currentRequirementsHaveExecutableBindings() throws Exception {validateCoverage(current());}
    @org.junit.jupiter.api.Tag("compiler-contract")
    @Test void boundTestSourcesExistAndMatch() throws Exception {validateSourceBindings(current());}
    @org.junit.jupiter.api.Tag("compiler-contract")
    @Test void boundAssertionsWereExecutedSuccessfully() throws Exception {validateExecutedAssertions(current());}
    @ParameterizedTest @ValueSource(strings={"condition","branch","builtin","predefined","method","sourceHash"})
    void inventedOrLostLinksCannotAppearComplete(String kind) throws Exception {
        validateCoverage(current()); validateSourceBindings(current());
        var changed=current();
        switch(kind) {
            case "condition"->changed.getAsJsonArray("conditions").remove(0);
            case "branch"->rows(changed,"grammar").getFirst().getAsJsonArray("occurrenceIds").remove(0);
            case "builtin"->changed.getAsJsonArray("builtins").remove(0);
            case "predefined"->changed.getAsJsonArray("predefined").remove(0);
            case "method","sourceHash"->{var binding=changed.getAsJsonObject("bindings").entrySet().iterator().next().getValue().getAsJsonObject();binding.addProperty(kind.equals("method")?"method":"sourceSha256","invented");}
        }
        assertThrows(Exception.class,()->{validateCoverage(changed);validateSourceBindings(changed);});
    }
}

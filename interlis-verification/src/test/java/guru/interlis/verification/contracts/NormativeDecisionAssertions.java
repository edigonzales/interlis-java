package guru.interlis.verification.contracts;
import com.google.gson.*;
import java.io.*;
import java.util.*;
import static guru.interlis.verification.contracts.ContractFiles.*;
/** Current immutable expectation objects, explicit authorizations and cited norm text. */
final class NormativeDecisionAssertions {
    static void decisions(JsonObject packet, JsonObject inventory) throws IOException {
        var expected = json("testdata/conformance/normative/expectations.json");
        String decisionDigests=BASE+"decision-expectation-digests.json";
        pin(decisionDigests,s(packet,"sourceSha256"));
        decisionSources(packet,expected,json(decisionDigests));
        Map<String, JsonObject> paragraphs = new HashMap<>();
        rows(inventory, "documents").forEach(d -> rows(d, "entries").forEach(r -> paragraphs.put(s(r, "id"), r)));
        for (var c : rows(packet, "cases")) {
            var original = rows(expected, "cases").stream().filter(r -> r.get("testId").equals(c.get("testId"))).findFirst().orElseThrow();
            boolean approved = original.has("compatibilityDecision");
            require(c.get("ownExpectation").equals(original.get("ownExpectation")) && c.get("referenceExpectation").equals(original.get("referenceExpectation"))
                    && (!approved || c.get("compatibilityDecision").equals(original.get("compatibilityDecision")))
                    && rows(c, "normEvidence").stream().map(r -> s(r,"document")).collect(java.util.stream.Collectors.toSet()).equals(Set.of("2.3", "2.4")), "Changed normative expectation/decision");
            for (var norm : rows(c, "normEvidence")) {
                // Empty locator-only entries are existing, unaccepted gaps. They do
                // not certify semantics; every paragraph actually cited stays exact.
                for (var p : rows(norm, "paragraphs")) {
                    var ref = paragraphs.get(s(p, "id")); require(ref != null && ref.get("document").equals(norm.get("document")) && ref.get("section").equals(norm.get("section"))
                            && ref.get("page").equals(p.get("page")) && ref.get("quote").equals(p.get("quote")) && hash(s(p, "quote")).equals(s(p, "quoteSha256")), "Changed decision norm quote/hash");
                }
            }
        }
    }

    static void decisionSources(JsonObject packet,JsonObject expected,JsonObject historical) {
        var digests=historical.getAsJsonObject("caseDigests");
        require(digests.size()==17&&rows(expected,"cases").size()==19
                &&ids(rows(packet,"cases"),"testId").equals(digests.keySet()),"Decision packet coverage differs");
        for(String key:List.of("schemaVersion","suiteCommit","suiteSha256"))require(expected.get(key).equals(historical.get(key)),"Suite identity changed");
        for(var entry:digests.entrySet()) {
            var matches=rows(expected,"cases").stream().filter(c->s(c,"testId").equals(entry.getKey())).toList();
            require(matches.size()==1&&hash(matches.getFirst().toString()).equals(entry.getValue().getAsString()),"Approved assessment changed");
        }
        var oldIds=digests.keySet();
        var additions=rows(expected,"cases").stream().filter(c->!oldIds.contains(s(c,"testId"))).toList();
        require(additions.size()==2&&ids(additions,"testId").equals(Set.of("ili23.Constraint23Test.setCstrt",
                "ili23.InterlisConversion23Test.iliconversion_ConversionOfSETCONSTRAINT")),"Unapproved additional assessment");
        for(var added:additions) {
            var authorization=added.getAsJsonObject("authorizationBasis");
            require(authorization!=null&&s(authorization,"kind").equals("APPROVED_SET_OBJECT_CONTEXT_PROFILE")
                    &&s(added,"languageVersion").equals("2.3")&&s(added,"ownExpectation").equals("rejected")
                    &&s(added.getAsJsonObject("referenceExpectation"),"outcome").equals("accept")
                    &&s(added.getAsJsonObject("norm"),"sections").equals("2.12"),"SET profile authorization differs");
        }
    }
}

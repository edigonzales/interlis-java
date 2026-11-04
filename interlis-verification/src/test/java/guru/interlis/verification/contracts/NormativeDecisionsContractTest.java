package guru.interlis.verification.contracts;
import com.google.gson.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.verification.contracts.ContractFiles.*;
class NormativeDecisionsContractTest {
    @Tag("compiler-contract")
    @Test void currentExpectationsAndCitedOriginalNormsStayBound() throws Exception {
        var matrix=data("rule-matrix"); var packet=data("normative-decision-packet-2026-10-28");
        var packetPin=matrix.getAsJsonObject("normativeDecisionPacket");pin(BASE+s(packetPin,"path"),s(packetPin,"sha256"));
        var sourcePin=matrix.getAsJsonObject("sourceInventory");pin(BASE+s(sourcePin,"path"),s(sourcePin,"sha256"));
        var inventory=data("norm-inventory");
        pin(BASE+"g2-norm-inventory.json",s(matrix.getAsJsonObject("normInventory"),"sha256"));
        NormativeDecisionAssertions.decisions(packet,inventory);
        for(var recorded:rows(data("source-inventory"),"normDocuments")) {
            String version=s(recorded,"document");
            var pages=NormativeQuoteAssertions.pages(version,matrix.getAsJsonObject("documents").getAsJsonObject(version),recorded);
            var paragraphs=rows(inventory,"documents").stream().filter(d->s(d,"document").equals(version)).findFirst().orElseThrow();
            for(var c:rows(packet,"cases")) for(var norm:rows(c,"normEvidence")) if(s(norm,"document").equals(version))
                for(var p:rows(norm,"paragraphs")) {
                    var ref=rows(paragraphs,"entries").stream().filter(r->r.get("id").equals(p.get("id"))).findFirst().orElseThrow();
                    String page=pages.get(n(ref,"page")-1);
                    require(slice(page,n(ref,"start"),n(ref,"end")).equals(s(p,"quote")) && hash(page).equals(s(ref,"pageSha256")),"Changed cited norm text");
                }
        }
    }
    @Test void setProfileAdditionsCannotChangeHistoricalCasesOrInventApproval() throws Exception {
        var packet=data("normative-decision-packet-2026-10-28");
        var expected=json("docs/evidence/conformance/normative-expectations.json");
        var historical=json("docs/evidence/conformance/normative-expectations-before-set-context.json");
        NormativeDecisionAssertions.decisionSources(packet,expected,historical);
        for(String change:List.of("old-case","missing","new-id","authorization")) {
            var altered=expected.deepCopy();var cases=altered.getAsJsonArray("cases");
            switch(change) {
                case "old-case" -> cases.get(0).getAsJsonObject().addProperty("ownExpectation","changed");
                case "missing" -> cases.remove(cases.size()-1);
                case "new-id" -> cases.get(cases.size()-1).getAsJsonObject().addProperty("testId","unapproved");
                case "authorization" -> cases.get(cases.size()-1).getAsJsonObject().getAsJsonObject("authorizationBasis").addProperty("kind","PENDING");
            }
            assertThrows(Exception.class,()->NormativeDecisionAssertions.decisionSources(packet,altered,historical));
        }
    }

    @Test void expectationAndQuoteMutationsAreRejected() throws Exception {
        var inventory=data("norm-inventory");
        for(String key:List.of("ownExpectation","quote","quoteSha256","missing")) {
            var changed=data("normative-decision-packet-2026-10-28");var row=rows(changed,"cases").getFirst();
            if(key.equals("missing"))changed.getAsJsonArray("cases").remove(0);
            else if(key.startsWith("quote"))rows(rows(row,"normEvidence").getFirst(),"paragraphs").getFirst().addProperty(key,"broken");
            else row.addProperty(key,"broken");
            assertThrows(IllegalArgumentException.class,()->NormativeDecisionAssertions.decisions(changed,inventory));
        }
    }
}

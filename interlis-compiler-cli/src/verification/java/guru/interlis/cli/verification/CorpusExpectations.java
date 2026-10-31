package guru.interlis.cli.verification;

import com.google.gson.*;
import java.nio.file.Path;
import java.util.*;
import static guru.interlis.cli.verification.HostChecks.*;

/** Two pinned negative originals required by the user's strict outer-name decision. */
final class CorpusExpectations {
    private CorpusExpectations() { }
    static List<JsonObject> load(Path root,JsonObject manifest) throws Exception {
        var profile=read(root.resolve("testdata/g2/corpus-expectations.json"));
        require(s(profile,"id").equals("STRICT_OUTER_TYPE_NAMES") && s(profile,"userStatement").equals("Auch äussere Typnamen strikt sperren"),"Unapproved corpus profile");
        var cases=rows(profile,"cases");require(cases.size()==2 && cases.stream().map(c->s(c,"model")).collect(java.util.stream.Collectors.toSet()).equals(Set.of("SO_AGI_Metadata_20230304","SO_AWJF_Gesuchsteller_20210202")),"Changed negative corpus selection");
        for(var expected:cases) {
        require(s(expected,"outcome").equals("rejected") && expected.get("exitCode").getAsInt()==1,"Not a source-rejection profile");
        var original=rows(manifest,"models").stream().filter(m->s(m,"file").equals(s(expected,"file"))).toList();
        require(original.size()==1 && s(original.getFirst(),"model").equals(s(expected,"model")) && s(original.getFirst(),"sha256").equals(s(expected,"sha256")),"Changed negative corpus original");
        var control=expected.getAsJsonObject("control");pin(root.resolve(s(control,"source")),s(control,"sha256"));
        }
        return List.copyOf(cases);
    }
    static boolean applies(JsonObject expected,JsonObject model) {return s(expected,"file").equals(s(model,"file"));}
    static void check(Result result,JsonObject expected,Path source) {
        checkPayload(result,true);
        var diagnostics=JsonParser.parseString(result.stdout()).getAsJsonObject().getAsJsonArray("diagnostics").deepCopy();
        for(var value:diagnostics) {
            var diagnostic=value.getAsJsonObject();diagnostic.remove("id");var range=diagnostic.getAsJsonObject("range");
            require(s(range,"source").equals(source.toUri().toString()) && s(range,"revision").equals("1"),"Wrong corpus diagnostic source/revision");
            range.remove("source");range.remove("revision");
        }
        require(diagnostics.equals(expected.getAsJsonArray("diagnostics")),"Wrong corpus diagnostic fields or multiplicity: "+diagnostics);
    }
}

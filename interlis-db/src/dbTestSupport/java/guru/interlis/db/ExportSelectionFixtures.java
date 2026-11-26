package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import org.w3c.dom.Element;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static guru.interlis.db.DbTestSupport.catalogKeys;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.referenceDatasetFixture;
import static guru.interlis.db.ModelFixtures.headerModels;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ExportSelectionFixtures {
    final String model;
    final Path source;
    private final Path work;
    private final int version;
    private final ProfileFixtures transfers;

    ExportSelectionFixtures(Path work, int version) throws IOException {
        this.work = work;
        this.version = version;
        model = "ExportSelection" + version;
        transfers = new ProfileFixtures(work, version);
        source = transfers.source(model, """
                STRUCTURE Note = Label : TEXT*30; END Note;
                CLASS Asset = Label : TEXT*30; Detail : Note; END Asset;
                CLASS Place = END Place;
                STRUCTURE Pointer = Destination : REFERENCE TO (EXTERNAL) Place; END Pointer;
                CLASS Survey = PointerValue : Pointer; END Survey;
                """);
    }

    String asset(String tid, String label, boolean detail) {
        String note = version == 23 ? model + ".Data.Note" : "Note";
        return transfers.object(model, "Asset", tid, "<Label>" + label + "</Label>"
                + (detail ? "<Detail><" + note + "><Label>note</Label></" + note + "></Detail>" : ""));
    }

    String place(String tid) {
        return transfers.object(model, "Place", tid, "");
    }

    String survey(String tid, String target, String targetBid) {
        String pointer = version == 23 ? model + ".Data.Pointer" : "Pointer";
        String reference = version == 23 ? "<Destination REF=\"" + target + "\" BID=\"" + targetBid + "\"/>"
                : transfers.reference("Destination", target);
        return transfers.object(model, "Survey", tid, "<PointerValue><" + pointer + ">" + reference + "</" + pointer + "></PointerValue>");
    }

    String basket(String topic, String bid, String objects) {
        String tag = version == 23 ? model + "." + topic : topic;
        String attribute = version == 23 ? "BID" : "i:bid";
        return "<" + tag + " " + attribute + "=\"" + bid + "\">" + objects + "</" + tag + ">";
    }

    Path delivery(String file, String... baskets) throws IOException {
        String envelope = referenceDatasetFixture(model, version, "placeholder", "");
        return Files.writeString(work.resolve(file + ".xtf"),
                envelope.replace(basket("Data", "placeholder", ""), String.join("", baskets)));
    }

    List<Element> baskets(Path output) throws Exception {
        var data = readDataSection(output, version);
        assertEquals(List.of(model), headerModels(data, version));
        return children(data);
    }

    List<String> exportedAssets(Path output) throws Exception {
        var found = new ArrayList<String>();
        for (var basket : baskets(output)) {
            String topic = basket.getLocalName();
            if (version == 23) topic = topic.substring(model.length() + 1);
            for (var object : children(basket)) {
                found.add(topic + ":" + id(basket, version, "bid") + ":" + id(object, version, "tid")
                        + ":" + child(object, "Label").getTextContent());
            }
        }
        return found;
    }

    void assertImportedAssets(Target db, Set<String> expected) throws Exception {
        try (var sql = db.connect()) {
            assertEquals(expected, catalogKeys(sql, "SELECT b.topic || ':' || b.t_ili_tid || ':' || a.t_ili_tid || ':' || a.label FROM "
                    + db.table("asset") + " a JOIN " + db.table("t_ili2db_basket") + " b ON b.t_id=a.t_basket"));
        }
    }
}

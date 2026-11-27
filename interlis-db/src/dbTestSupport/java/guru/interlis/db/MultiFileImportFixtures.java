package guru.interlis.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static guru.interlis.db.DbTestSupport.referenceDatasetFixture;

final class MultiFileImportFixtures {
    final String model;
    final Path source;
    private final Path work;
    private final int version;
    private final ProfileFixtures transfers;

    MultiFileImportFixtures(Path work, int version) throws IOException {
        this.work = work;
        this.version = version;
        model = "MultiFileImport" + version;
        transfers = new ProfileFixtures(work, version);
        source = transfers.source(model, """
                CLASS Place = Label : TEXT*25; END Place;
                STRUCTURE Pointer = Destination : REFERENCE TO (EXTERNAL) Place; END Pointer;
                STRUCTURE LocalPointer = Destination : REFERENCE TO Place; END LocalPointer;
                CLASS Survey = PointerValue : Pointer; LocalValue : LocalPointer; END Survey;
                """);
    }

    String place(String tid, String label) {
        return transfers.object(model, "Place", tid, "<Label>" + label + "</Label>");
    }

    String survey(String tid) {
        return transfers.object(model, "Survey", tid, "");
    }

    String externalSurvey(String tid, String target, String targetBid) {
        String reference = transfers.reference("Destination", target);
        if (version == 23) reference = reference.replace("/>", " BID=\"" + targetBid + "\"/>");
        return surveyWithPointer(tid, "PointerValue", "Pointer", reference);
    }

    String localSurvey(String tid, String target) {
        return surveyWithPointer(tid, "LocalValue", "LocalPointer", transfers.reference("Destination", target));
    }

    private String surveyWithPointer(String tid, String field, String structure, String reference) {
        String tag = version == 23 ? model + ".Data." + structure : structure;
        return transfers.object(model, "Survey", tid,
                "<" + field + "><" + tag + ">" + reference + "</" + tag + "></" + field + ">");
    }

    String basket(String bid, String... objects) {
        String tag = version == 23 ? model + ".Data" : "Data";
        String attribute = version == 23 ? "BID" : "i:bid";
        return "<" + tag + " " + attribute + "=\"" + bid + "\">" + String.join("", objects) + "</" + tag + ">";
    }

    Path delivery(String file, String... baskets) throws IOException {
        String envelope = referenceDatasetFixture(model, version, "placeholder", "");
        return Files.writeString(work.resolve(file + ".xtf"),
                envelope.replace(basket("placeholder"), String.join("", baskets)));
    }
}

package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import org.w3c.dom.Element;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static guru.interlis.db.DbTestSupport.catalogKeys;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.referenceDatasetFixture;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ProfileFixtures {
    static final Set<String> LINKED_KEYS = Set.of("asset:t_basket:t_ili2db_basket", "asset:refasset:target",
            "target:t_basket:t_ili2db_basket", "note:t_basket:t_ili2db_basket", "note:asset_notes:asset",
            "t_ili2db_basket:dataset:t_ili2db_dataset");
    static final Set<String> LINKED_INDEXES = Set.of("asset:t_basket", "asset:refasset", "target:t_basket",
            "note:t_basket", "note:asset_notes", "t_ili2db_basket:dataset");
    static final Set<String> DATASET_INDEXES = Set.of("asset:t_datasetname", "target:t_datasetname", "note:t_datasetname");
    static final List<String> DATASET_TABLES = List.of("asset", "target", "note");
    static final List<String> AUDIT_TABLES = List.of("asset", "asset_checkpoint", "note", "target");

    private final Path work;
    private final int version;

    ProfileFixtures(Path work, int version) {
        this.work = work;
        this.version = version;
    }

    Path source(String model, String declarations) throws IOException {
        return Files.writeString(work.resolve(model + ".ili"), """
                INTERLIS 2.%d;
                MODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  TOPIC Data =
                %s
                  END Data;
                END %s.
                """.formatted(version - 20, model, declarations, model));
    }

    Path linkedModel(String model) throws IOException {
        return source(model, """
                STRUCTURE Note = Message : TEXT*24; END Note;
                CLASS Target = Label : TEXT*24; END Target;
                CLASS Asset = Label : TEXT*24; Notes : LIST OF Note; END Asset;
                ASSOCIATION Assignment =
                  RefAsset -- {0..1} Target;
                  Sources -- {0..*} Asset;
                END Assignment;
                """);
    }

    Path linkedData(String model, String prefix) throws IOException {
        String note = version == 23 ? model + ".Data.Note" : "Note";
        return data(model, prefix, object(model, "Asset", prefix + "-asset",
                "<Label>linked</Label><Notes><" + note + "><Message>present</Message></" + note + "></Notes>"
                        + reference("RefAsset", prefix + "-target"))
                + object(model, "Target", prefix + "-target", "<Label>target</Label>"));
    }

    Path requiredRoleModel(String model) throws IOException {
        return source(model, """
                CLASS Site = Label : MANDATORY TEXT*30; END Site;
                CLASS Asset = SerialNo : MANDATORY 0..100; END Asset;
                CLASS Other = END Other;
                ASSOCIATION Assignment =
                  AssignedSite -- {1} Site;
                  Assets -- {0..*} Asset;
                END Assignment;
                ASSOCIATION Review =
                  ReviewSite -- {0..1} Site;
                  ReviewedAssets -- {0..*} Asset;
                END Review;
                CLASS PairLeft = Label : TEXT*30; END PairLeft;
                CLASS PairRight = END PairRight;
                ASSOCIATION ForwardPair =
                  RequiredLeft -- {1} PairLeft;
                  OwnerRight -- {0..1} PairRight;
                END ForwardPair;
                ASSOCIATION ReversePair =
                  RequiredRight -- {1} PairRight;
                  OwnerLeft -- {0..1} PairLeft;
                END ReversePair;
                ASSOCIATION OptionalPair =
                  MaybeRight -- {0..1} PairRight;
                  MaybeLeft -- {0..1} PairLeft;
                END OptionalPair;
                """);
    }

    String assignedAsset(String model, String prefix, int serialNo) {
        return object(model, "Asset", prefix + "-asset", "<SerialNo>" + serialNo + "</SerialNo>"
                + reference("AssignedSite", prefix + "-site"));
    }

    String assignedSite(String model, String prefix) {
        return object(model, "Site", prefix + "-site", "<Label>site</Label>");
    }

    String pairedLeft(String model, String prefix, String label) {
        return object(model, "PairLeft", prefix + "-left", "<Label>" + label + "</Label>"
                + reference("RequiredRight", prefix + "-right"));
    }

    String pairedRight(String model, String prefix) {
        return object(model, "PairRight", prefix + "-right", reference("RequiredLeft", prefix + "-left"));
    }

    Path requiredReferenceModel(String model) throws IOException {
        String direct = version == 24 ? "DirectSite : MANDATORY REFERENCE TO Site;" : "";
        return source(model, """
                CLASS Site = Label : MANDATORY TEXT*30; END Site;
                STRUCTURE SiteDetail = AssignedSite : MANDATORY REFERENCE TO Site; END SiteDetail;
                CLASS Asset = Detail : SiteDetail; %s END Asset;
                """.formatted(direct));
    }

    String referringAsset(String model, String tid, String targetTid) {
        String structure = version == 23 ? model + ".Data.SiteDetail" : "SiteDetail";
        String fields = targetTid == null ? "" : reference("AssignedSite", targetTid);
        String direct = version == 24 && targetTid != null ? reference("DirectSite", targetTid) : "";
        return object(model, "Asset", tid, "<Detail><" + structure + ">" + fields + "</" + structure + "></Detail>" + direct);
    }

    Path nestedStructureModel(String model) throws IOException {
        return source(model, """
                STRUCTURE MemoPart = Label : MANDATORY TEXT*30; END MemoPart;
                STRUCTURE Envelope =
                  Heading : MANDATORY TEXT*30;
                  MainPart : MANDATORY MemoPart;
                  Parts : LIST OF MemoPart;
                  Copies : BAG OF MemoPart;
                END Envelope;
                CLASS Asset = Payload : Envelope; END Asset;
                """);
    }

    String nestedStructureObject(String model, String tid, boolean missingLastLabel) {
        String first = structure(model, "MemoPart", "<Label>first</Label>");
        String last = structure(model, "MemoPart", missingLastLabel ? "" : "<Label>last</Label>");
        String fields = "<Heading>set</Heading><MainPart>" + first + "</MainPart><Parts>" + first + last
                + "</Parts><Copies>" + first + first + "</Copies>";
        return object(model, "Asset", tid, "<Payload>" + structure(model, "Envelope", fields) + "</Payload>");
    }

    private String structure(String model, String type, String fields) {
        String tag = version == 23 ? model + ".Data." + type : type;
        return "<" + tag + ">" + fields + "</" + tag + ">";
    }

    Path requiredGeometryModel(String model) throws IOException {
        String multi = version == 24
                ? "CLASS Stops = Locations : MANDATORY MULTICOORD 0.000..100.000, 0.000..100.000; END Stops;"
                : "";
        return source(model, """
                DOMAIN
                  XY = COORD 0.000..100.000, 0.000..100.000;
                  XYZ = COORD 0.000..100.000, 0.000..100.000, -100.000..100.000;
                CLASS Marker = Center : MANDATORY XYZ; END Marker;
                CLASS Trail = Trace : MANDATORY POLYLINE WITH (STRAIGHTS) VERTEX XY; END Trail;
                CLASS Plot = Outline : MANDATORY SURFACE WITH (STRAIGHTS) VERTEX XY WITHOUT OVERLAPS > 0.001; END Plot;
                CLASS Survey = OptionalPoint : XY; END Survey;
                %s
                """.formatted(multi));
    }

    String requiredGeometryObjects(String model, String prefix) {
        String line = geometry("POLYLINE", "polyline", point("10", "10") + point("20", "30"));
        String ring = geometry("POLYLINE", "polyline",
                point("10", "10") + point("20", "10") + point("20", "20") + point("10", "20") + point("10", "10"));
        String surface = geometry("SURFACE", "surface", geometry("BOUNDARY", "exterior", ring));
        String multi = version == 24 ? object(model, "Stops", prefix + "-stops", "<Locations>"
                + geometry("MULTICOORD", "multicoord", point("30", "40") + point("50", "60")) + "</Locations>") : "";
        return object(model, "Marker", prefix + "-marker", "<Center>" + point("12.125", "13.375", "-4.875") + "</Center>")
                + object(model, "Trail", prefix + "-trail", "<Trace>" + line + "</Trace>")
                + object(model, "Plot", prefix + "-plot", "<Outline>" + surface + "</Outline>")
                + object(model, "Survey", prefix + "-survey", "") + multi;
    }

    Path auditModel(String model) throws IOException {
        Path ili = linkedModel(model);
        return Files.writeString(ili, Files.readString(ili).replace("CLASS Asset = Label", """
                DOMAIN Position = COORD 0.000 .. 100.000, 0.000 .. 100.000;
                CLASS Asset = MainPoint : Position; CheckPoint : Position; Label"""));
    }

    Path auditData(String model, String prefix) throws IOException {
        String note = version == 23 ? model + ".Data.Note" : "Note";
        String fields = "<MainPoint>" + point("12.000", "13.000") + "</MainPoint><CheckPoint>" + point("14.000", "15.000")
                + "</CheckPoint><Label>linked</Label><Notes><" + note + "><Message>present</Message></" + note
                + "></Notes>" + reference("RefAsset", prefix + "-target");
        return data(model, prefix, object(model, "Asset", prefix + "-asset", fields)
                + object(model, "Target", prefix + "-target", "<Label>target</Label>"));
    }

    static void assertDatasetColumns(Target db, Connection sql, List<String> tables) throws SQLException {
        for (String table : tables) {
            assertEquals(Set.of("legacy:legacy", "native:native"), catalogKeys(sql,
                    "SELECT a.t_datasetname || ':' || d.datasetname FROM " + db.table(table) + " a JOIN "
                            + db.table("t_ili2db_basket") + " b ON b.t_id = a.t_basket JOIN "
                            + db.table("t_ili2db_dataset") + " d ON d.t_id = b.dataset"), table);
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table(table)), table);
        }
    }

    static void assertInitialAuditColumns(Target db, Connection sql) throws SQLException {
        String user = db.postgis() ? sql.getMetaData().getUserName() : System.getProperty("user.name");
        for (String table : AUDIT_TABLES) {
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table(table)
                    + " WHERE t_createdate = t_lastchange AND t_user = '" + user.replace("'", "''") + "'"
                    + (db.postgis() ? "" : " AND typeof(t_createdate) = 'integer'")), table);
        }
    }

    Path data(String model, String prefix, String objects) throws IOException {
        return Files.writeString(work.resolve(prefix + ".xtf"), referenceDatasetFixture(model, version, prefix + "-basket", objects));
    }

    String object(String model, String type, String tid, String fields) {
        String tag = version == 23 ? model + ".Data." + type : type;
        String attribute = version == 23 ? "TID" : "i:tid";
        return "<" + tag + " " + attribute + "=\"" + tid + "\">" + fields + "</" + tag + ">";
    }

    String reference(String field, String tid) {
        return "<" + field + (version == 23 ? " REF=\"" : " i:ref=\"") + tid + "\"/>";
    }

    String point(String x, String y) {
        return point(x, y, null);
    }

    private String point(String x, String y, String z) {
        String prefix = version == 23 ? "C" : "g:c";
        String axes = "<" + prefix + "1>" + x + "</" + prefix + "1><" + prefix + "2>" + y + "</" + prefix + "2>";
        if (z != null) axes += "<" + prefix + "3>" + z + "</" + prefix + "3>";
        return geometry("COORD", "coord", axes);
    }

    private String geometry(String xtf23, String xtf24, String contents) {
        return version == 23 ? "<" + xtf23 + ">" + contents + "</" + xtf23 + ">"
                : "<g:" + xtf24 + " xmlns:g=\"http://www.interlis.ch/geometry/1.0\">" + contents + "</g:" + xtf24 + ">";
    }

    List<Element> rows(Path path, String prefix, int count) throws Exception {
        return basketRows(path, prefix + "-basket", count);
    }

    List<Element> basketRows(Path path, String bid, int count) throws Exception {
        var baskets = children(readDataSection(path, version));
        assertEquals(1, baskets.size());
        assertEquals(bid, id(baskets.getFirst(), version, "bid"));
        var rows = children(baskets.getFirst()).stream().sorted(Comparator.comparing(row -> id(row, version, "tid"))).toList();
        assertEquals(count, rows.size());
        return rows;
    }

    void assertLink(Element row, String prefix) {
        assertEquals(prefix + "-asset", id(row, version, "tid"));
        var role = child(row, "RefAsset");
        assertEquals(prefix + "-target", id(role, version, "ref"));
        assertEquals("present", child(children(child(row, "Notes")).getFirst(), "Message").getTextContent());
    }
}

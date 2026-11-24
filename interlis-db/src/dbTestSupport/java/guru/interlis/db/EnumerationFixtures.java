package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.referenceDatasetFixture;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class EnumerationFixtures {
    static final String BASE = """
            DOMAIN Grade = (Zulu, Family (Zed, Alpha), Last_Code) ORDERED;
            CLASS Asset =
              GradeValue : Grade;
              DirectValue : (No_Sign, Present);
              Active : BOOLEAN;
            END Asset;
            """;

    private final Path work;
    private final int version;

    EnumerationFixtures(Path work, int version) {
        this.work = work;
        this.version = version;
    }

    Path textModel(String model) throws Exception {
        return source(model, BASE.replace("Zed, Alpha", """
                Zed,
                  !!@ili2db.dispName="Alpine label"
                  Alpha"""));
    }

    Path extensionModel(String model) throws Exception {
        return source(model, BASE.replace("CLASS Asset", """
                MoreGrade EXTENDS Grade = (Family (Extra), Final_Code);
                OrderedGrade EXTENDS Grade = (Other) ORDERED;
                CLASS Asset""").replace("GradeValue : Grade;", """
                GradeValue : Grade;
                  DerivedValue : MoreGrade;"""));
    }

    Path arrayModel(String model) throws Exception {
        return source(model, """
                DOMAIN Grade = (Zulu, Family (Zed, Alpha), Last_Code) ORDERED;
                %s
                CLASS Asset =
                  !!@ili2db.mapping=ARRAY
                  Grades : LIST OF %s;
                END Asset;
                """.formatted(version == 23 ? "STRUCTURE Member = Value : Grade; END Member;" : "",
                version == 23 ? "Member" : "Grade"));
    }

    AppendModels appendModels(String model) throws Exception {
        int domainEnd = BASE.indexOf("CLASS Asset");
        Path base = source(model, BASE.substring(0, domainEnd), BASE.substring(domainEnd));
        String added = "EnumAppend" + version;
        Path addition = source(added, """
                IMPORTS %s;
                DOMAIN MoreGrade EXTENDS %s.Grade = (Family (Extra), Final_Code);
                       Status = (Open_Code, Closed_Code) ORDERED;
                """.formatted(model, model), """
                CLASS Detail =
                  Comparison : %s.Grade;
                  Choice : MoreGrade;
                  StatusValue : Status;
                END Detail;
                """.formatted(model));
        String independent = "EnumIndependent" + version;
        Path standalone = source(independent, """
                DOMAIN Stage = (Start_Code, End_Code);
                CLASS IndependentAsset = StageValue : Stage; END IndependentAsset;
                """);
        String broken = "EnumBrokenAppend" + version;
        Path conflict = source(broken, """
                IMPORTS %s;
                DOMAIN BrokenGrade EXTENDS %s.Grade = (Unstorable);
                """.formatted(model, model), "CLASS AbortAsset = Value : BrokenGrade; END AbortAsset;");
        return new AppendModels(base, added, addition, independent, standalone, broken, conflict);
    }

    record AppendModels(Path base, String added, Path addition, String independent, Path standalone,
                        String broken, Path conflict) { }

    Path source(String model, String declarations) throws Exception {
        return source(model, "", declarations);
    }

    Path source(String model, String header, String declarations) throws Exception {
        return Files.writeString(work.resolve(model + ".ili"), """
                INTERLIS 2.%d;
                MODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                %s
                  TOPIC Data =
                %s
                  END Data;
                END %s.
                """.formatted(version - 20, model, header, declarations, model));
    }

    Path dataset(String model, String prefix, String objects) throws Exception {
        return Files.writeString(work.resolve(prefix + ".xtf"),
                referenceDatasetFixture(model, version, prefix + "-basket", objects));
    }

    String object(String model, String type, String tid, String fields) {
        String name = version == 23 ? model + ".Data." + type : type;
        return "<" + name + (version == 23 ? " TID=\"" : " i:tid=\"") + tid + "\">" + fields + "</" + name + ">";
    }

    Path scalars(String model, String prefix) throws Exception {
        return dataset(model, prefix, object(model, "Asset", prefix + "-value", """
                <GradeValue>Family.Alpha</GradeValue><DirectValue>No_Sign</DirectValue><Active>true</Active>
                """) + object(model, "Asset", prefix + "-missing", ""));
    }

    void assertScalars(Path output, String prefix) throws Exception {
        var baskets = children(readDataSection(output, version));
        assertEquals(1, baskets.size());
        assertEquals(prefix + "-basket", id(baskets.getFirst(), version, "bid"));
        var objects = children(baskets.getFirst()).stream()
                .collect(Collectors.toMap(object -> id(object, version, "tid"), object -> object));
        assertEquals(2, objects.size());
        var value = objects.get(prefix + "-value");
        assertEquals("Family.Alpha", child(value, "GradeValue").getTextContent());
        assertEquals("No_Sign", child(value, "DirectValue").getTextContent());
        assertEquals("true", child(value, "Active").getTextContent());
        assertNull(child(objects.get(prefix + "-missing"), "GradeValue"));
    }

    Path numericDataset(String model, String prefix, List<String> values) throws Exception {
        return dataset(model, prefix, object(model, "Asset", prefix + "-value", numericFields(model, values))
                + object(model, "Asset", prefix + "-missing", ""));
    }

    String numericFields(String model, List<String> values) {
        if (version == 24) {
            return values.stream().map(value -> "<Grades>" + value + "</Grades>").collect(Collectors.joining());
        }
        String wrapper = model + ".Data.Member";
        return "<Grades>" + values.stream().map(value -> "<" + wrapper + "><Value>" + value + "</Value></" + wrapper + ">")
                .collect(Collectors.joining()) + "</Grades>";
    }

    void assertNumericArray(Path output, String prefix, List<String> values) throws Exception {
        var basket = children(readDataSection(output, version)).getFirst();
        assertEquals(prefix + "-basket", id(basket, version, "bid"));
        var rows = children(basket).stream().collect(Collectors.toMap(row -> id(row, version, "tid"), row -> row));
        assertNull(child(rows.get(prefix + "-missing"), "Grades"));
        var row = rows.get(prefix + "-value");
        var paths = version == 23
                ? children(child(row, "Grades")).stream().map(item -> child(item, "Value").getTextContent()).toList()
                : children(row).stream().filter(item -> item.getLocalName().equals("Grades")).map(item -> item.getTextContent()).toList();
        assertEquals(values, paths);
    }

    static List<List<String>> rows(Connection sql, String query) throws Exception {
        var result = new ArrayList<List<String>>();
        try (var statement = sql.createStatement(); var rows = statement.executeQuery(query)) {
            int width = rows.getMetaData().getColumnCount();
            while (rows.next()) {
                var row = new ArrayList<String>(width);
                for (int i = 1; i <= width; i++) row.add(rows.getString(i));
                result.add(row);
            }
        }
        return result;
    }

    static String mappedTable(Target db, Connection sql, String path) throws Exception {
        return scalar(sql, "SELECT sqlname FROM " + db.table("t_ili2db_classname") + " WHERE iliname = '" + path + "'");
    }

    static void assertItfRows(Target db, Connection sql, String prefix) throws Exception {
        String value = " FROM " + db.table("asset") + " WHERE t_ili_tid = '" + prefix;
        assertEquals("2", scalar(sql, "SELECT gradevalue" + value + "-value'"));
        assertEquals("0", scalar(sql, "SELECT directvalue" + value + "-value'"));
        assertNull(scalar(sql, "SELECT gradevalue" + value + "-missing'"));
    }

    Path extensionDataset(String model, String prefix) throws Exception {
        return dataset(model, prefix, object(model, "Asset", prefix + "-pair",
                "<GradeValue>Family.Alpha</GradeValue><DerivedValue>Family.Alpha</DerivedValue><DirectValue>No_Sign</DirectValue>")
                + object(model, "Asset", prefix + "-extra", "<DerivedValue>Family.Extra</DerivedValue>")
                + object(model, "Asset", prefix + "-missing", ""));
    }

    void assertExtension(Path output, String prefix) throws Exception {
        var basket = children(readDataSection(output, version)).getFirst();
        assertEquals(prefix + "-basket", id(basket, version, "bid"));
        var objects = children(basket).stream().collect(Collectors.toMap(row -> id(row, version, "tid"), row -> row));
        assertEquals("Family.Alpha", child(objects.get(prefix + "-pair"), "GradeValue").getTextContent());
        assertEquals("Family.Alpha", child(objects.get(prefix + "-pair"), "DerivedValue").getTextContent());
        assertEquals("Family.Extra", child(objects.get(prefix + "-extra"), "DerivedValue").getTextContent());
        assertEquals("No_Sign", child(objects.get(prefix + "-pair"), "DirectValue").getTextContent());
        assertNull(child(objects.get(prefix + "-missing"), "GradeValue"));
    }

    void assertAppended(Path output, String prefix) throws Exception {
        var basket = children(readDataSection(output, version)).getFirst();
        assertEquals(prefix + "-basket", id(basket, version, "bid"));
        var row = children(basket).getFirst();
        assertEquals(prefix + "-value", id(row, version, "tid"));
        assertEquals("Family.Alpha", child(row, "Comparison").getTextContent());
        assertEquals("Family.Extra", child(row, "Choice").getTextContent());
        assertEquals("Closed_Code", child(row, "StatusValue").getTextContent());
    }

}

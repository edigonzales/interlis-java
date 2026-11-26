package guru.interlis.validator.cli;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ValidatorCommandTest {
    @TempDir Path directory;

    @Test
    void validatesXtf23UsingANeighbouringModelAndExplicitModelSelection() throws Exception {
        model(directory, "2.3", "");
        write("input.data", legacy("<name>valid</name>"));
        var result = run("--model", "Checks", "input.data", "--offline");
        assertEquals(0, result.exit(), result.error());
        assertTrue(result.error().contains("Objects checked: 1"), result.error());
        assertEquals("", result.output());
    }

    @Test
    void validatesXtf23FullWithAnyOidOnAConcreteTopic() throws Exception {
        write("Checks.ili", """
                INTERLIS 2.3;
                CONTRACTED MODEL Checks AT "https://example.org/checks" VERSION "1" =
                  TOPIC Data =
                    OID AS INTERLIS.ANYOID;
                    CLASS Item = name : MANDATORY TEXT*8; END Item;
                  END Data;
                END Checks.
                """);
        var input = legacy("<name>valid</name>").replace("TID=\"one\"", "TID=\"123\"");
        write("input.xtf", input);
        var result = run("--json", "--offline", "input.xtf");
        assertEquals(0, result.exit(), result.output());
        var report = result.json();
        assertTrue(report.get("completed").getAsBoolean());
        assertEquals(1, report.get("objects").getAsInt());
        assertEquals(0, report.get("violations").getAsInt());
        assertTrue(report.getAsJsonArray("unchecked").isEmpty());

        write("duplicate.xtf", input.replace("</Checks.Data>", """
                <Checks.Data.Item TID="123"><name>valid</name></Checks.Data.Item>
                </Checks.Data>
                """));
        var duplicate = run("--json", "--offline", "duplicate.xtf");
        assertFailed(duplicate, 1, "transfer");
        assertTrue(duplicate.output().contains("DUPLICATE_ID"), duplicate.output());
    }

    @Test
    void checksSurfaceAndAreaLineAttributes() throws Exception {
        lineAttributeModel("MANDATORY CONSTRAINT label != \"reserved\";");
        write("valid.xtf", legacy(attributedBoundary("shape", "<label>valid</label>")
                + attributedBoundary("parcel", "<label>valid</label>")));
        assertLineAttributeResult("valid.xtf", 0, "passed", 0, 0);

        write("missing.xtf", legacy(attributedBoundary("shape", "")));
        var missing = assertLineAttributeResult("missing.xtf", 1, "rejected", 1, 0);
        assertLineDiagnostic(missing, "missing.xtf", "REQUIRED_VALUE_MISSING", "shape",
                "/label", "label : MANDATORY TEXT*20;");

        write("long.xtf", legacy(attributedBoundary("parcel", "<label>abcdefghijklmnopqrstu</label>")));
        var longText = assertLineAttributeResult("long.xtf", 1, "rejected", 1, 0);
        assertLineDiagnostic(longText, "long.xtf", "TEXT_TOO_LONG", "parcel",
                "/label", "label : MANDATORY TEXT*20;");

        write("nested.xtf", legacy(attributedBoundary("shape", """
                <label>valid</label><details>
                  <Checks.Data.Details><note>ok</note></Checks.Data.Details>
                  <Checks.Data.Details><note>too long</note></Checks.Data.Details>
                </details>
                """)));
        var nested = assertLineAttributeResult("nested.xtf", 1, "rejected", 1, 0);
        assertLineDiagnostic(nested, "nested.xtf", "TEXT_TOO_LONG", "shape",
                "/details[2]/note", "note : TEXT*4;");

        write("constraint.xtf", legacy(attributedBoundary("parcel", "<label>reserved</label>")));
        var constraint = assertLineAttributeResult("constraint.xtf", 1, "rejected", 1, 0);
        assertLineDiagnostic(constraint, "constraint.xtf", "MANDATORY_CONSTRAINT_FALSE", "parcel",
                "", "MANDATORY CONSTRAINT label != \"reserved\";");
    }

    @Test
    void reportsUnsupportedLineConstraintsAlongsideViolations() throws Exception {
        lineAttributeModel("MANDATORY CONSTRAINT Checks.accepts(label);");
        write("unchecked-line.xtf", legacy(attributedBoundary("shape", "<label>valid</label>")));
        var unchecked = assertLineAttributeResult("unchecked-line.xtf", 5, "incomplete", 0, 1);
        assertTrue(unchecked.getAsJsonArray("unchecked").get(0).getAsString().contains("UNSUPPORTED_EXPRESSION"));
        write("rejected-line.xtf", legacy(attributedBoundary("shape", "<label>abcdefghijklmnopqrstu</label>")));
        assertLineAttributeResult("rejected-line.xtf", 1, "rejected", 1, 1);
    }

    @Test
    void reportsDatasetRulesOnLineStructuresOutsideTheCheckedScope() throws Exception {
        lineAttributeModel("EXISTENCE CONSTRAINT label REQUIRED IN Target:name;");
        write("missing-target.xtf", legacy(attributedBoundary("shape", "<label>valid</label>")));
        var result = assertLineAttributeResult("missing-target.xtf", 5, "incomplete", 0, 1);
        assertTrue(result.getAsJsonArray("unchecked").get(0).getAsString().startsWith("OUT_OF_SCOPE_GLOBAL "));
    }

    @Test
    void checksLocalLineConstraintsOnDeferredGeometry() throws Exception {
        write("Checks.ili", """
                INTERLIS 2.3;
                MODEL Checks AT "https://example.org/checks" VERSION "1" =
                  DOMAIN Point = COORD 0..100, 0..100;
                  TOPIC Data =
                    STRUCTURE Marks =
                      label : MANDATORY TEXT*20;
                      MANDATORY CONSTRAINT label != "reserved";
                    END Marks;
                    CLASS Target =
                      shape : SURFACE WITH (STRAIGHTS) VERTEX Point WITHOUT OVERLAPS > 0.01 LINE ATTRIBUTES Marks;
                    END Target;
                    STRUCTURE Pointer =
                      link : REFERENCE TO Target;
                      shape : SURFACE WITH (STRAIGHTS) VERTEX Point WITHOUT OVERLAPS > 0.01 LINE ATTRIBUTES Marks := link->shape;
                    END Pointer;
                    CLASS Item = pointer : Pointer; END Item;
                  END Data;
                END Checks.
                """);
        var input = legacy("<pointer><Checks.Data.Pointer><link REF=\"target\"/></Checks.Data.Pointer></pointer>")
                .replace("</Checks.Data.Item>", "</Checks.Data.Item><Checks.Data.Target TID=\"target\">"
                        + attributedBoundary("shape", "<label>valid</label>") + "</Checks.Data.Target>");
        write("derived-line.xtf", input);
        var result = run("--json", "--offline", "derived-line.xtf");
        assertEquals(0, result.exit(), result.output());
        assertEquals(2, result.json().get("objects").getAsInt());
        assertTrue(result.json().get("completed").getAsBoolean());
        assertEquals(0, result.json().get("violations").getAsInt());
        assertTrue(result.json().getAsJsonArray("unchecked").isEmpty());

        write("invalid-derived-line.xtf", input.replace("<label>valid</label>", "<label>reserved</label>"));
        var rejected = run("--json", "--offline", "invalid-derived-line.xtf");
        assertEquals(1, rejected.exit(), rejected.output());
        var json = rejected.json();
        assertEquals("rejected", json.get("status").getAsString());
        assertTrue(json.get("completed").getAsBoolean());
        assertEquals(2, json.get("violations").getAsInt());
        var diagnostics = json.getAsJsonArray("diagnostics");
        assertEquals(2, diagnostics.size());
        var byTid = diagnostics.asList().stream().map(element -> element.getAsJsonObject())
                .collect(Collectors.groupingBy(diagnostic -> diagnostic.getAsJsonObject("location").get("tid").getAsString()));
        assertEquals(Set.of("one", "target"), byTid.keySet());
        assertDeferredLineDiagnostic(byTid.get("one").getFirst(), "Item", "/pointer/shape/RING[1]/LINE[2]");
        assertDeferredLineDiagnostic(byTid.get("target").getFirst(), "Target", "/shape/RING[1]/LINE[2]");
    }

    @Test
    void reportsUncheckedRuleOnGeometryDerivedFromReferenceData() throws Exception {
        write("Checks.ili", """
                INTERLIS 2.3;
                MODEL Checks AT "https://example.org/checks" VERSION "1" =
                  DOMAIN Id = OID TEXT*20;
                         Point = COORD 0..100, 0..100;
                  TOPIC Data =
                    STRUCTURE Marks =
                      label : MANDATORY TEXT*20;
                      MANDATORY CONSTRAINT label != "reserved";
                      EXISTENCE CONSTRAINT label REQUIRED IN Target:name;
                    END Marks;
                    STRUCTURE Spare =
                      note : MANDATORY TEXT*20;
                      EXISTENCE CONSTRAINT note REQUIRED IN Target:name;
                    END Spare;
                    CLASS Target =
                      OID AS Id;
                      name : MANDATORY TEXT*20;
                      shape : SURFACE WITH (STRAIGHTS) VERTEX Point WITHOUT OVERLAPS > 0.01 LINE ATTRIBUTES Marks;
                    END Target;
                    STRUCTURE Pointer =
                      link : REFERENCE TO (EXTERNAL) Target;
                      shape : SURFACE WITH (STRAIGHTS) VERTEX Point WITHOUT OVERLAPS > 0.01 LINE ATTRIBUTES Marks := link->shape;
                    END Pointer;
                    CLASS Item =
                      pointer : Pointer;
                      spare : Spare;
                    END Item;
                  END Data;
                END Checks.
                """);
        String oidSpace = "</MODELS><OIDSPACES><OIDSPACE NAME=\"ids\" OIDDOMAIN=\"Checks.Id\"/></OIDSPACES>";
        write("primary.xtf", legacy("""
                <pointer><Checks.Data.Pointer><link REF="ids:target" BID="reference"/></Checks.Data.Pointer></pointer>
                """).replace("</MODELS>", oidSpace));
        String reference = legacy("<name>witness</name>" + attributedBoundary("shape", "<label>valid</label>"))
                .replace("</MODELS>", oidSpace).replace("BID=\"basket\"", "BID=\"reference\"")
                .replace("Checks.Data.Item", "Checks.Data.Target").replace("TID=\"one\"", "TID=\"ids:target\"");
        write("reference.xtf", reference);
        write("invalid-reference.xtf", reference.replace("<label>valid</label>", "<label>reserved</label>"));
        write("unused.xtf", legacy(""));

        // Reading Target directly identifies the Marks rule independently of the derivation.
        var direct = assertLineAttributeResult("reference.xtf", 5, "incomplete", 0, 1);
        var unchecked = direct.getAsJsonArray("unchecked");
        assertTrue(unchecked.get(0).getAsString().startsWith("OUT_OF_SCOPE_GLOBAL "));

        var unused = run("--offline", "--json", "unused.xtf", "--refdata", "reference.xtf");
        assertEquals(0, unused.exit(), unused.output());
        assertEquals(1, unused.json().get("objects").getAsInt());
        assertTrue(unused.json().getAsJsonArray("unchecked").isEmpty());

        var rejected = run("--offline", "--json", "primary.xtf", "--refdata", "invalid-reference.xtf");
        assertEquals(1, rejected.exit(), rejected.output());
        var invalid = rejected.json();
        assertEquals("rejected", invalid.get("status").getAsString());
        assertTrue(invalid.get("completed").getAsBoolean());
        assertEquals(1, invalid.get("objects").getAsInt());
        assertEquals(1, invalid.get("violations").getAsInt());
        var diagnostics = invalid.getAsJsonArray("diagnostics");
        assertEquals(1, diagnostics.size());
        var diagnostic = diagnostics.get(0).getAsJsonObject();
        assertEquals("one", diagnostic.getAsJsonObject("location").get("tid").getAsString());
        assertDeferredLineDiagnostic(diagnostic, "Item", "/pointer/shape/RING[1]/LINE[2]");

        var incomplete = run("--offline", "--json", "primary.xtf", "--refdata", "reference.xtf");
        assertEquals(5, incomplete.exit(), incomplete.output());
        var valid = incomplete.json();
        assertEquals("incomplete", valid.get("status").getAsString());
        assertTrue(valid.get("completed").getAsBoolean());
        assertEquals(1, valid.get("objects").getAsInt());
        assertEquals(0, valid.get("violations").getAsInt());
        assertEquals(unchecked, valid.getAsJsonArray("unchecked"));
        assertEquals(unchecked, invalid.getAsJsonArray("unchecked"));
    }

    private void assertDeferredLineDiagnostic(JsonObject diagnostic, String type, String path) throws IOException {
        assertEquals("MANDATORY_CONSTRAINT_FALSE", diagnostic.get("code").getAsString());
        var location = diagnostic.getAsJsonObject("location");
        assertEquals(type, location.get("objectType").getAsString());
        assertEquals(path, location.get("path").getAsString());
        assertLineRule(diagnostic, "MANDATORY CONSTRAINT label != \"reserved\";");
    }

    private void lineAttributeModel(String constraint) throws Exception {
        write("Checks.ili", """
                INTERLIS 2.3;
                CONTRACTED MODEL Checks AT "https://example.org/checks" VERSION "1" =
                  FUNCTION accepts(value: TEXT): BOOLEAN;
                  DOMAIN Point = COORD 0.00..100.00, 0.00..100.00;
                  TOPIC Data =
                    CLASS Target = name : TEXT*20; END Target;
                    STRUCTURE Details =
                      note : TEXT*4;
                    END Details;
                    STRUCTURE Marks =
                      label : MANDATORY TEXT*20;
                      details : BAG {0..*} OF Details;
                      %s
                    END Marks;
                    CLASS Item =
                      shape : SURFACE WITH (STRAIGHTS) VERTEX Point WITHOUT OVERLAPS > 0.01 LINE ATTRIBUTES Marks;
                      parcel : AREA WITH (STRAIGHTS) VERTEX Point WITHOUT OVERLAPS > 0.01 LINE ATTRIBUTES Marks;
                    END Item;
                  END Data;
                END Checks.
                """.formatted(constraint));
    }

    private static String attributedBoundary(String field, String attributes) {
        return """
                <%1$s><SURFACE><BOUNDARY>
                  <POLYLINE><LINEATTR><Checks.Data.Marks><label>first</label></Checks.Data.Marks></LINEATTR>
                    <COORD><C1>0.00</C1><C2>0.00</C2></COORD>
                    <COORD><C1>10.00</C1><C2>0.00</C2></COORD>
                    <COORD><C1>10.00</C1><C2>10.00</C2></COORD>
                  </POLYLINE>
                  <POLYLINE><LINEATTR><Checks.Data.Marks>%2$s</Checks.Data.Marks></LINEATTR>
                    <COORD><C1>10.00</C1><C2>10.00</C2></COORD>
                    <COORD><C1>0.00</C1><C2>10.00</C2></COORD>
                    <COORD><C1>0.00</C1><C2>0.00</C2></COORD>
                  </POLYLINE>
                </BOUNDARY></SURFACE></%1$s>
                """.formatted(field, attributes);
    }

    private JsonObject assertLineAttributeResult(String file, int exit, String status, int violations, int unchecked) {
        var result = run("--json", "--offline", file);
        assertEquals(exit, result.exit(), result.output() + result.error());
        var json = result.json();
        assertEquals(status, json.get("status").getAsString());
        assertTrue(json.get("completed").getAsBoolean());
        assertEquals(1, json.get("objects").getAsInt());
        assertEquals(violations, json.get("violations").getAsInt());
        assertEquals(unchecked, json.getAsJsonArray("unchecked").size(), result.output());
        return json;
    }

    private void assertLineDiagnostic(JsonObject json, String file, String code, String geometry,
                                             String suffix, String declaration) throws IOException {
        var diagnostics = json.getAsJsonArray("diagnostics");
        assertEquals(1, diagnostics.size());
        var diagnostic = diagnostics.get(0).getAsJsonObject();
        assertEquals(code, diagnostic.get("code").getAsString());
        var location = diagnostic.getAsJsonObject("location");
        assertTrue(location.get("source").getAsString().endsWith(file));
        assertEquals("basket", location.get("bid").getAsString());
        assertEquals("one", location.get("tid").getAsString());
        assertEquals("Item", location.get("objectType").getAsString());
        assertEquals("/" + geometry + "/RING[1]/LINE[2]" + suffix,
                location.get("path").getAsString());
        assertLineRule(diagnostic, declaration);
    }

    private void assertLineRule(JsonObject diagnostic, String declaration) throws IOException {
        var rule = diagnostic.getAsJsonObject("ruleLocation");
        assertTrue(rule.get("source").getAsString().endsWith("Checks.ili"));
        int start = rule.get("startOffset").getAsInt();
        int end = rule.get("endOffset").getAsInt();
        assertEquals(declaration, Files.readString(directory.resolve("Checks.ili")).substring(start, end));
        assertEquals(declaration.startsWith("MANDATORY") ? "constraint@" + start : declaration.split(" ")[0],
                rule.get("declaration").getAsString());
    }

    @Test
    void validatesPrefixedXtf24WithABomInAnExplicitLocalRepository() throws Exception {
        var repository = Files.createDirectory(directory.resolve("local models"));
        model(repository, "2.4", "");
        Files.writeString(directory.resolve("data"), "\uFEFF" + """
                <?xml version="1.0" encoding="UTF-8"?>
                <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS"
                  xmlns:m="http://www.interlis.ch/xtf/2.4/Checks">
                  <!-- prefix and encoding are independent of the filename -->
                  <i:headersection><i:models><i:model>Checks</i:model></i:models><i:sender>test</i:sender></i:headersection>
                  <i:datasection><m:Data i:bid="basket"><m:Item i:tid="one">
                    <m:name>valid</m:name>
                  </m:Item></m:Data></i:datasection>
                </i:transfer>
                """, StandardCharsets.UTF_8);
        var result = run("--offline", "--repository", "local models", "data");
        assertEquals(0, result.exit(), result.error());
        assertTrue(result.error().contains("Objects checked: 1"), result.error());
    }

    @Test
    void usesRepositoryOrderForLooseAndIndexedModels() throws Exception {
        var wide = Files.createDirectory(directory.resolve("wide"));
        var narrow = Files.createDirectory(directory.resolve("narrow"));
        model(wide, "2.3", "");
        model(narrow, "2.3", "");
        var file = narrow.resolve("Checks.ili");
        Files.writeString(file, Files.readString(file).replace("TEXT*8", "TEXT*4"));
        write("input.xtf", legacy("<name>abcde</name>"));
        assertRepositoryOrder();
        Files.writeString(narrow.resolve("ilimodels.xml"), """
                <RepositoryIndex><ModelMetadata>
                  <Name>Checks</Name><SchemaLanguage>ili2_3</SchemaLanguage>
                  <File>Checks.ili</File><Version>1</Version>
                </ModelMetadata></RepositoryIndex>
                """);
        assertRepositoryOrder();
    }

    private void assertRepositoryOrder() {
        var firstWide = run("--offline", "--repository", "wide", "--repository", "narrow", "input.xtf");
        assertEquals(0, firstWide.exit(), firstWide.error());
        var firstNarrow = run("--offline", "--repository", "narrow", "--repository", "wide", "input.xtf");
        assertEquals(1, firstNarrow.exit(), firstNarrow.error());
        assertTrue(firstNarrow.error().contains("TEXT_TOO_LONG"), firstNarrow.error());
    }

    @Test
    void reportsADataViolationAtTheObjectAndItsModelRule() throws Exception {
        model(directory, "2.3", "");
        write("bad.xtf", legacy("<name>too many characters</name>"));
        var result = run("--offline", "bad.xtf");
        assertEquals(1, result.exit(), result.error());
        assertTrue(result.error().contains("TEXT_TOO_LONG") && result.error().contains("TID=one")
                && result.error().contains("/name") && result.error().contains("model rule name"), result.error());
    }

    @Test
    void readsToPhysicalEofAfterAValidHeaderAndDataset() throws Exception {
        model(directory, "2.3", "");
        write("early.xtf", legacy("<name>valid</wrong>"));
        write("trail.xtf", legacy("<name>valid</name>") + "<trailing/>");
        for (var file : List.of("early.xtf", "trail.xtf")) {
            var result = run("--offline", file);
            assertEquals(1, result.exit(), result.error());
            assertTrue(result.error().contains("transfer: MALFORMED_XML"), result.error());
            assertFalse(result.error().contains("No violations"));
        }
    }

    @Test
    void separatesUnresolvedModelsAndFileFailuresFromMalformedHeaders() throws Exception {
        write("unknown.xtf", legacy("<name>valid</name>"));
        write("header.xtf", legacy("").replace(" SENDER=\"test\"", ""));
        var missingModel = run("--offline", "unknown.xtf");
        assertEquals(3, missingModel.exit(), missingModel.error());
        assertTrue(missingModel.error().contains("model resolution") && missingModel.error().contains("Checks"),
                missingModel.error());
        assertEquals(3, run("--offline", "missing.xtf").exit());
        var invalid = run("--offline", "header.xtf");
        assertEquals(1, invalid.exit(), invalid.error());
        assertTrue(invalid.error().contains("SENDER"), invalid.error());
    }

    @Test
    void doesNotProcessDtdsOrExternalEntities() throws Exception {
        write("unsafe.xtf", legacy("").replace("<TRANSFER", """
                <!DOCTYPE TRANSFER [<!ENTITY external SYSTEM "file:///no-such-file">]>
                <TRANSFER"""));
        var result = run("--offline", "unsafe.xtf");
        assertEquals(1, result.exit(), result.error());
        assertTrue(result.error().startsWith("header:"), result.error());
    }

    @Test
    void usesJtsForTopology() throws Exception {
        projectedModel("2.3", """
                shape : SURFACE WITH (STRAIGHTS) VERTEX Point WITHOUT OVERLAPS > 0.001;
                """);
        var ring = polygon23(0, 0, 2, 2, 2, 0, 0, 2, 0, 0);
        write("crossed.xtf", legacy("<name>valid</name>" + ring));
        var result = run("--offline", "crossed.xtf");
        assertEquals(1, result.exit(), result.error());
        String path = "/shape/RING[1]/LINE[1]/PART[1]/SEGMENT[";
        assertTrue(result.error().lines().anyMatch(line -> line.contains("GEOMETRY_SELF_INTERSECTION")
                && line.contains("TID=one") && line.contains(path + "1]") && line.contains(path + "3]")
                && line.contains("intersection XY=(2600001") && line.contains("1200001")), result.error());

        var json = run("--offline", "--json", "crossed.xtf");
        assertEquals(1, json.exit(), json.output());
        assertTrue(json.json().get("completed").getAsBoolean());
        var finding = json.json().getAsJsonArray("diagnostics").get(0).getAsJsonObject();
        var evidence = assertGeometryEvidence(finding, "GEOMETRY_SELF_INTERSECTION", "intersection");
        assertWitness(evidence, "2600001", "1200001");
        var primary = evidence.getAsJsonObject("location");
        var related = evidence.getAsJsonArray("relatedLocations").get(0).getAsJsonObject();
        assertEquals("one", primary.get("tid").getAsString());
        assertEquals("one", related.get("tid").getAsString());
        assertEquals(Set.of(path + "1]", path + "3]"),
                Set.of(primary.get("path").getAsString(), related.get("path").getAsString()));

        write("late-error.xtf", legacy("<name>ok</name>").replace("BID=\"basket\"", "BID=\"late\"") + "<trailing/>");
        var interrupted = run("--offline", "--json", "--max-diagnostics", "2", "crossed.xtf", "late-error.xtf");
        assertFailed(interrupted, 1, "transfer");
        assertEquals(1, interrupted.json().get("violations").getAsLong());
        assertEquals(finding, interrupted.json().getAsJsonArray("diagnostics").get(1).getAsJsonObject());
        var capped = run("--offline", "--json", "--max-diagnostics", "1", "crossed.xtf", "late-error.xtf");
        assertFailed(capped, 1, "transfer");
        assertEquals(1, capped.json().get("violations").getAsLong());
        assertTrue(capped.json().get("diagnosticsTruncated").getAsBoolean());
        assertEquals(1, capped.json().getAsJsonArray("diagnostics").size());
    }

    @Test
    void locatesAreaConflictsAcrossFilesEvenWithTheSameTransferId() throws Exception {
        projectedModel("2.3", "shape : AREA WITH (STRAIGHTS) VERTEX Point WITHOUT OVERLAPS > 0.001;");
        write("west.xtf", legacy("<name>western</name>" + polygon23(0, 0, 10, 0, 10, 10, 0, 10, 0, 0))
                .replace("BID=\"basket\"", "BID=\"west\""));
        write("east.xtf", legacy("<name>eastern</name>" + polygon23(8, 4, 12, 4, 12, 8, 8, 8, 8, 4))
                .replace("BID=\"basket\"", "BID=\"east\""));
        write("inside.xtf", legacy("<name>inside</name>" + polygon23(2, 2, 4, 2, 4, 4, 2, 4, 2, 2))
                .replace("BID=\"basket\"", "BID=\"inside\""));
        var crossing = run("--offline", "--json", "west.xtf", "east.xtf");
        assertEquals(1, crossing.exit(), crossing.output());
        assertTrue(crossing.json().get("completed").getAsBoolean());
        assertEquals(2, crossing.json().get("objects").getAsInt());
        var witnesses = new HashSet<BigDecimal>();
        for (var finding : crossing.json().getAsJsonArray("diagnostics")) {
            var evidence = assertGeometryEvidence(finding.getAsJsonObject(), "GEOMETRY_AREA_OVERLAP", "intersection");
            assertPair(evidence, "west", "east");
            assertEquals(0, ordinate(evidence, "x").compareTo(new BigDecimal("2600010")));
            var y = ordinate(evidence, "y").stripTrailingZeros();
            witnesses.add(y);
            String path = "/shape/RING[1]/LINE[1]/PART[1]/SEGMENT[";
            var first = evidence.getAsJsonObject("location");
            var second = evidence.getAsJsonArray("relatedLocations").get(0).getAsJsonObject();
            String eastSegment = y.equals(new BigDecimal("1200004")) ? "1]" : "3]";
            assertEquals(Set.of(path + "2]", path + eastSegment),
                    Set.of(first.get("path").getAsString(), second.get("path").getAsString()));
        }
        assertEquals(Set.of(new BigDecimal("1200004"), new BigDecimal("1200008")), witnesses);
        var crossingText = run("--offline", "west.xtf", "east.xtf");
        assertEquals(1, crossingText.exit(), crossingText.error());
        assertTrue(crossingText.error().lines().anyMatch(line -> line.contains("GEOMETRY_AREA_OVERLAP")
                && line.contains("west.xtf BID=west TID=one") && line.contains("east.xtf BID=east TID=one")
                && line.contains("intersection XY=(2600010") && line.contains("/shape/RING[1]")), crossingText.error());

        var contained = run("--offline", "--json", "west.xtf", "inside.xtf");
        assertEquals(1, contained.exit(), contained.output());
        assertTrue(contained.json().get("completed").getAsBoolean());
        var evidence = assertGeometryEvidence(contained.json().getAsJsonArray("diagnostics").get(0).getAsJsonObject(),
                "GEOMETRY_AREA_OVERLAP", "contained-boundary");
        assertPair(evidence, "west", "inside");
        var x = ordinate(evidence, "x");
        var y = ordinate(evidence, "y");
        assertTrue(onInterval(x, "2600002", "2600004") && onInterval(y, "1200002", "1200004"));
        assertTrue(onExtreme(x, "2600002", "2600004") || onExtreme(y, "1200002", "1200004"));

        var text = run("--offline", "west.xtf", "inside.xtf");
        assertEquals(1, text.exit(), text.error());
        assertTrue(text.error().contains("contained-boundary XY=(") && text.error().contains("west.xtf")
                && text.error().contains("inside.xtf"), text.error());
    }

    @Test
    void locatesGapEvidenceWithinItsFailedAreaConstraint() throws Exception {
        projectedModel("2.4", """
                shape : SURFACE WITH (STRAIGHTS) VERTEX Point;
                SET CONSTRAINT INTERLIS.areAreasWithoutGaps(ALL, UNDEFINED, >>shape);
                """);
        write("gap.xtf", surface24(ring24("exterior", 0, 0, 8, 0, 8, 8, 0, 8, 0, 0)
                + ring24("interior", 2, 2, 4, 2, 4, 4, 2, 4, 2, 2)));
        var result = run("--offline", "--json", "gap.xtf");
        assertEquals(1, result.exit(), result.output());
        assertTrue(result.json().get("completed").getAsBoolean());
        assertEquals(1, result.json().get("violations").getAsInt());
        var finding = result.json().getAsJsonArray("diagnostics").get(0).getAsJsonObject();
        assertEquals("SET_CONSTRAINT_FALSE", finding.get("code").getAsString());
        var evidence = assertGeometryEvidence(finding, "GEOMETRY_GAP", "gap-boundary");
        var location = evidence.getAsJsonObject("location");
        assertEquals("ring", location.get("tid").getAsString());
        assertTrue(location.get("path").getAsString().startsWith("/shape/RING[2]/"), evidence.toString());
        var x = ordinate(evidence, "x");
        var y = ordinate(evidence, "y");
        assertTrue(onInterval(x, "2600002", "2600004") && onInterval(y, "1200002", "1200004"));
        assertTrue(onExtreme(x, "2600002", "2600004") || onExtreme(y, "1200002", "1200004"));

        var text = run("--offline", "gap.xtf");
        assertEquals(1, text.exit(), text.error());
        assertTrue(text.error().lines().anyMatch(line -> line.contains("SET_CONSTRAINT_FALSE")
                && line.contains("GEOMETRY_GAP gap-boundary XY=(") && line.contains("gap.xtf BID=parcel TID=ring")
                && line.contains("/shape/RING[2]/")), text.error());
    }

    @Test
    void includesALocallyInvalidSurfaceInItsFailedAreaConstraint() throws Exception {
        projectedModel("2.4", """
                shape : SURFACE WITH (STRAIGHTS) VERTEX Point;
                SET CONSTRAINT INTERLIS.areAreas(ALL, UNDEFINED, >>shape);
                """);
        write("crossed.xtf", surface24(ring24("exterior", 0, 0, 2, 2, 2, 0, 0, 2, 0, 0)));
        var result = run("--offline", "--json", "crossed.xtf");
        assertEquals(1, result.exit(), result.output());
        assertTrue(result.json().get("completed").getAsBoolean());
        assertEquals(2, result.json().get("violations").getAsInt());
        var diagnostics = result.json().getAsJsonArray("diagnostics").asList().stream()
                .map(item -> item.getAsJsonObject()).toList();
        var direct = diagnostics.stream().filter(finding -> finding.get("code").getAsString()
                .equals("GEOMETRY_SELF_INTERSECTION")).findFirst().orElseThrow();
        var constraint = diagnostics.stream().filter(finding -> finding.get("code").getAsString()
                .equals("SET_CONSTRAINT_FALSE")).findFirst().orElseThrow();
        var evidence = assertGeometryEvidence(constraint, "GEOMETRY_SELF_INTERSECTION", "intersection");
        assertEquals(direct.getAsJsonObject("geometryEvidence"), evidence);
        assertWitness(evidence, "2600001", "1200001");
        var primary = evidence.getAsJsonObject("location");
        var related = evidence.getAsJsonArray("relatedLocations").get(0).getAsJsonObject();
        assertEquals("ring", primary.get("tid").getAsString());
        assertEquals("ring", related.get("tid").getAsString());
        String path = "/shape/RING[1]/LINE[1]/PART[1]/SEGMENT[";
        assertEquals(Set.of(path + "1]", path + "3]"),
                Set.of(primary.get("path").getAsString(), related.get("path").getAsString()));
    }

    @Test
    void reportsAnUncheckedConstraintWithoutReportingSuccess() throws Exception {
        write("Checks.ili", """
                INTERLIS 2.3;
                CONTRACTED MODEL Checks AT "https://example.org/checks" VERSION "1" =
                  FUNCTION ok(value: TEXT): BOOLEAN;
                  TOPIC Data =
                    CLASS Item =
                      name : MANDATORY TEXT*8;
                      MANDATORY CONSTRAINT Checks.ok(name);
                    END Item;
                  END Data;
                END Checks.
                """);
        write("unchecked.xtf", legacy("<name>valid</name>"));
        var result = run("--offline", "unchecked.xtf");
        assertEquals(5, result.exit(), result.error());
        assertTrue(result.error().contains("UNSUPPORTED_EXPRESSION"), result.error());
        assertFalse(result.error().contains("No violations"));
        write("rejected.xtf", legacy("<name>too many characters</name>"));
        var rejected = run("--json", "--offline", "rejected.xtf");
        assertEquals(1, rejected.exit(), rejected.output());
        assertTrue(rejected.json().get("completed").getAsBoolean());
        assertEquals(1, rejected.json().get("violations").getAsLong());
        assertTrue(rejected.json().getAsJsonArray("unchecked").size() > 0);
    }

    @Test
    void parsesArgumentsBeforeIoAndRecognisesCancellation() {
        assertEquals(0, run("--help").exit());
        assertEquals(0, run("--version").exit());
        for (var arguments : List.of(List.of("--unknown"), List.of("--offline", "--offline", "file"),
                List.of("--repository"), List.of("--refdata", "second"), List.of("--model", "--offline", "file"),
                List.of("--temp-dir", ".", "file"), List.of("--max-diagnostics", "0", "file"),
                List.of("--storage", "memory", "--storage", "mvstore", "file"))) {
            assertEquals(2, run(arguments.toArray(String[]::new)).exit(), arguments.toString());
        }
        assertEquals(List.of(Path.of("-input.xtf")), ValidatorOptions.parse(List.of("--", "-input.xtf")).primary());
        assertEquals(4, ValidatorCommand.run(List.of("missing.xtf"), directory,
                new PrintWriter(new StringWriter()), new PrintWriter(new StringWriter()), () -> true));
    }

    @Test
    void globalUniqueProducesTheSameBoundedJsonWithBothStores() throws Exception {
        model(directory, "2.3", "UNIQUE name;");
        write("first file.xtf", legacy("<name>alpha</name>").replace("BID=\"basket\"", "BID=\"first\""));
        write("second.xtf", legacy("<name>alpha</name>").replace("BID=\"basket\"", "BID=\"second\""));
        write("third.xtf", legacy("<name>beta</name>").replace("BID=\"basket\"", "BID=\"third\""));
        write("fourth.xtf", legacy("<name>beta</name>").replace("BID=\"basket\"", "BID=\"fourth\""));
        var passed = run("--offline", "--json", "--verbose", "first file.xtf");
        assertEquals(0, passed.exit(), passed.error());
        assertEquals("passed", passed.json().get("status").getAsString());
        assertTrue(passed.error().contains("Preparing"));
        assertEquals(1, passed.json().get("objects").getAsLong());
        var args = List.of("--offline", "--json", "--max-diagnostics", "1", "first file.xtf", "second.xtf", "third.xtf", "fourth.xtf");
        var memory = run(args.toArray(String[]::new));
        var diskArgs = new java.util.ArrayList<>(args);
        diskArgs.addAll(List.of("--storage", "mvstore", "--temp-dir", "."));
        var disk = run(diskArgs.toArray(String[]::new));
        assertEquals(1, memory.exit(), memory.error() + memory.output());
        assertEquals(memory.exit(), disk.exit(), disk.error() + disk.output());
        assertEquals(memory.json(), disk.json());
        var json = memory.json();
        assertEquals("rejected", json.get("status").getAsString());
        assertTrue(json.get("completed").getAsBoolean());
        assertEquals(4, json.get("objects").getAsLong());
        assertEquals(2, json.get("violations").getAsLong());
        assertTrue(json.get("diagnosticsTruncated").getAsBoolean());
        var diagnostics = json.getAsJsonArray("diagnostics");
        assertEquals(1, diagnostics.size());
        var diagnostic = diagnostics.get(0).getAsJsonObject();
        assertEquals("DATASET_UNIQUE_DUPLICATE", diagnostic.get("code").getAsString());
        assertFalse(diagnostic.has("geometryEvidence"));
        assertTrue(diagnostic.getAsJsonObject("location").get("source").getAsString().endsWith(".xtf"));
        assertTrue(diagnostic.getAsJsonObject("ruleLocation").get("source").getAsString().endsWith(".ili"));
        assertEquals(1, diagnostic.getAsJsonArray("relatedLocations").size());
        assertNoRunDirectories();
    }

    @Test
    void resolvesStableReferenceAcrossPrimaryAndReferenceFiles() throws Exception {
        write("Checks.ili", """
                INTERLIS 2.3;
                MODEL Catalogue AT "https://example.org" VERSION "1" =
                  DOMAIN Id = OID TEXT*20;
                  TOPIC Lists =
                    CLASS Target =
                      OID AS Id;
                      name : MANDATORY TEXT*2;
                      SET CONSTRAINT INTERLIS.objectCount(ALL) > 0;
                    END Target;
                  END Lists;
                END Catalogue.
                MODEL Checks AT "https://example.org" VERSION "1" =
                  IMPORTS Catalogue;
                  TOPIC Data =
                    DEPENDS ON Catalogue.Lists;
                    STRUCTURE Pointer = peer: REFERENCE TO (EXTERNAL) Catalogue.Lists.Target; END Pointer;
                    CLASS Item = link: Pointer; END Item;
                  END Data;
                END Checks.
                """);
        var primary = legacy("<link><Checks.Data.Pointer><peer REF=\"ids:target\" BID=\"target-basket\"/></Checks.Data.Pointer></link>")
                .replace("</MODELS>", "</MODELS><OIDSPACES><OIDSPACE NAME=\"ids\" OIDDOMAIN=\"Catalogue.Id\"/></OIDSPACES>");
        var target = legacy("<name>ok</name>").replace("NAME=\"Checks\"", "NAME=\"Catalogue\"")
                .replace("Checks.Data.Item", "Catalogue.Lists.Target").replace("Checks.Data", "Catalogue.Lists")
                .replace("TID=\"one\"", "TID=\"ids:target\"").replace("BID=\"basket\"", "BID=\"target-basket\"")
                .replace("</MODELS>", "</MODELS><OIDSPACES><OIDSPACE NAME=\"ids\" OIDDOMAIN=\"Catalogue.Id\"/></OIDSPACES>");
        write("source.xtf", primary);
        write("target.xtf", target);
        write("witness.xtf", target.replace("<name>ok</name>", "<name>invalid for primary</name>"));
        var missing = run("--json", "--offline", "source.xtf");
        assertEquals(1, missing.exit(), missing.output());
        assertEquals("REFERENCE_TARGET_MISSING", missing.json().getAsJsonArray("diagnostics").get(0)
                .getAsJsonObject().get("code").getAsString());
        var partial = run("--json", "--offline", "--partial", "source.xtf");
        assertEquals(5, partial.exit(), partial.output());
        assertEquals("partial-dataset", partial.json().get("scope").getAsString());
        assertTrue(partial.json().get("completed").getAsBoolean());
        assertEquals(0, partial.json().get("violations").getAsLong());
        assertTrue(partial.json().getAsJsonArray("unchecked").size() > 0);
        var both = run("--json", "--offline", "source.xtf", "target.xtf");
        assertEquals(0, both.exit(), both.output());
        var witness = run("--json", "--offline", "source.xtf", "--refdata", "witness.xtf");
        assertEquals(0, witness.exit(), witness.output());
        assertEquals("reference", witness.json().getAsJsonArray("inputs").get(1).getAsJsonObject().get("role").getAsString());
        var duplicated = run("--json", "--offline", "source.xtf", "target.xtf", "--refdata", "witness.xtf");
        assertEquals(1, duplicated.exit(), duplicated.output());
        assertTrue(duplicated.json().getAsJsonArray("diagnostics").asList().stream()
                .map(d -> d.getAsJsonObject())
                .anyMatch(d -> "AMBIGUOUS_OBJECT_IDENTITY".equals(d.get("code").getAsString())
                        && !d.getAsJsonArray("relatedLocations").isEmpty()), duplicated.output());
    }

    @Test
    void anExtensionUsedOnlyAsReferenceCreatesNoEmptySetPopulation() throws Exception {
        model(directory, "2.3", "");
        write("Extra.ili", """
                INTERLIS 2.3;
                MODEL Extra AT "https://example.org/checks" VERSION "1" =
                  IMPORTS Checks;
                  TOPIC Lists EXTENDS Checks.Data =
                    CLASS Witness =
                      SET CONSTRAINT INTERLIS.objectCount(ALL) > 0;
                    END Witness;
                  END Lists;
                END Extra.
                """);
        write("primary.xtf", legacy("<name>ok</name>"));
        var extended = legacy("").replace("NAME=\"Checks\"", "NAME=\"Extra\"")
                .replace("Checks.Data.Item", "Extra.Lists.Witness").replace("Checks.Data", "Extra.Lists");
        write("reference.xtf", extended);
        var referenced = run("--offline", "--json", "primary.xtf", "--refdata", "reference.xtf");
        assertEquals(0, referenced.exit(), referenced.output());
        assertEquals(1, referenced.json().get("objects").getAsLong());

        write("extended-primary.xtf", extended.replaceFirst("(?s)<Extra.Lists.Witness.*?</Extra.Lists.Witness>", ""));
        var delivered = run("--offline", "--json", "--model", "Checks",
                "primary.xtf", "extended-primary.xtf", "--refdata", "reference.xtf");
        assertEquals(1, delivered.exit(), delivered.output());
        assertTrue(delivered.output().contains("SET_CONSTRAINT_FALSE"), delivered.output());
    }

    @Test
    void anExplicitPrimaryModelCannotPassAnUnrelatedTransferLoadedForReference() throws Exception {
        model(directory, "2.3", "");
        write("Other.ili", Files.readString(directory.resolve("Checks.ili")).replace("MODEL Checks", "MODEL Other")
                .replace("END Checks.", "END Other."));
        var foreign = legacy("<name>too many characters</name>").replace("NAME=\"Checks\"", "NAME=\"Other\"")
                .replace("Checks.Data", "Other.Data");
        write("foreign-primary.xtf", foreign);
        write("reference.xtf", foreign);
        var mismatched = run("--json", "--offline", "--model", "Checks", "foreign-primary.xtf",
                "--refdata", "reference.xtf");
        assertFailed(mismatched, 1, "transfer");
        assertTrue(mismatched.output().contains("PRIMARY_MODEL_MISMATCH"), mismatched.output());

        write("checks.xtf", legacy("<name>ok</name>"));
        var matched = run("--json", "--offline", "--model", "Checks", "checks.xtf", "--refdata", "reference.xtf");
        assertEquals(0, matched.exit(), matched.output());
        assertEquals(1, matched.json().get("objects").getAsLong());
    }

    @Test
    void reportsEarlyAndOperationalFailuresWithoutACompletedResult() throws Exception {
        model(directory, "2.3", "");
        write("bad.xtf", legacy("<name>too many characters</name>"));
        write("wrong.xtf", legacy("<name>ok</name>").replace("VERSION=\"1\"", "VERSION=\"2\""));
        write("trailing.xtf", legacy("<name>ok</name>") + "<trailing/>");
        write("limit.xtf", legacy("<name>" + "x".repeat(1_048_577) + "</name>"));
        assertFailed(run("--json", "--offline", "bad.xtf", "wrong.xtf"), 1, "header");
        var malformed = run("--json", "--offline", "bad.xtf", "trailing.xtf",
                "--storage", "mvstore", "--temp-dir", ".");
        assertFailed(malformed, 1, "transfer");
        assertTrue(malformed.output().contains("MALFORMED_XML"), malformed.output());
        assertTrue(malformed.output().contains("TEXT_TOO_LONG"), malformed.output());
        var resourceFailure = run("--json", "--offline", "bad.xtf", "limit.xtf",
                "--storage", "mvstore", "--temp-dir", ".");
        assertFailed(resourceFailure, 3, "transfer");
        assertTrue(resourceFailure.output().contains("LIMIT_EXCEEDED"), resourceFailure.output());
        assertTrue(resourceFailure.output().contains("TEXT_TOO_LONG"), resourceFailure.output());
        assertEquals(1, resourceFailure.json().get("violations").getAsLong());
        assertFailed(run("--json", "--offline", "bad.xtf", "--refdata", "missing.xtf"), 3, "input");
        assertFailed(run("--json", "--offline", "bad.xtf", "--storage", "mvstore",
                "--temp-dir", "missing-directory"), 3, "storage");
        assertFailed(run("--json", "--refdata", "bad.xtf"), 2, "options");
        assertFailed(run("--json", "--offline", "wrong.xtf"), 3, "model resolution");
        assertNoRunDirectories();
        var version = run("--version", "--json");
        assertEquals(0, version.exit());
        assertEquals("interlis-validator", version.json().get("tool").getAsString());
    }

    @Test
    void aLaterUnsupportedBasketDoesNotEraseProvenViolations() throws Exception {
        model(directory, "2.3", "");
        write("bad.xtf", legacy("<name>too many characters</name>"));
        write("incremental.xtf", legacy("<name>ok</name>").replace("BID=\"basket\"", "BID=\"next\" KIND=\"INITIAL\""));
        var unsupported = run("--offline", "--json", "incremental.xtf");
        assertFailed(unsupported, 5, "transfer");
        assertEquals(0, unsupported.json().get("violations").getAsLong());
        assertTrue(unsupported.output().contains("UNSUPPORTED"), unsupported.output());

        var rejected = run("--offline", "--json", "bad.xtf", "incremental.xtf");
        assertFailed(rejected, 1, "transfer");
        assertEquals("rejected", rejected.json().get("status").getAsString());
        assertEquals(1, rejected.json().get("violations").getAsLong());
        assertTrue(rejected.output().contains("UNSUPPORTED") && rejected.output().contains("TEXT_TOO_LONG"),
                rejected.output());

        var capped = run("--offline", "--json", "--max-diagnostics", "1", "bad.xtf", "incremental.xtf");
        assertFailed(capped, 1, "transfer");
        assertEquals(1, capped.json().get("violations").getAsLong());
        assertEquals(1, capped.json().getAsJsonArray("diagnostics").size());
        assertTrue(capped.json().get("diagnosticsTruncated").getAsBoolean());
    }

    @Test
    void reportsAnUndeterminedSetRuleEvenWithNoCandidates() throws Exception {
        model(directory, "2.3", "SET CONSTRAINT INTERLIS.objectCount(ALL) > 0;");
        write("empty.xtf", legacy("").replaceFirst("(?s)<DATASECTION>.*?</DATASECTION>", "<DATASECTION/>"));
        var supplied = run("--json", "--offline", "empty.xtf");
        assertEquals(1, supplied.exit(), supplied.output());
        assertTrue(supplied.output().contains("SET_CONSTRAINT_FALSE"), supplied.output());
        var partial = run("--json", "--offline", "--partial", "empty.xtf");
        assertEquals(5, partial.exit(), partial.output());
        assertTrue(partial.json().get("completed").getAsBoolean());
        assertEquals(0, partial.json().get("violations").getAsLong());
        assertTrue(partial.output().contains("UNDETERMINED"), partial.output());
    }

    @Test
    void cleansUpTheStoreOnCancellationDuringTransfer() throws Exception {
        model(directory, "2.3", "");
        write("ok.xtf", legacy("<name>ok</name>"));
        var output = new StringWriter();
        var errors = new StringWriter();
        CancellationToken afterStorageOpened = () -> {
            try (var files = Files.list(directory)) {
                return files.anyMatch(path -> path.getFileName().toString().startsWith("interlis-validation-"));
            } catch (IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
        };
        int code = ValidatorCommand.run(List.of("--json", "--offline", "--storage", "mvstore",
                "--temp-dir", ".", "ok.xtf"), directory, new PrintWriter(output), new PrintWriter(errors), afterStorageOpened);
        assertFailed(new Result(code, output.toString(), errors.toString()), 4, "transfer");
        assertNoRunDirectories();
    }

    @Test
    void treatsBrokenJsonOutputAsAnOperationalFailure() throws Exception {
        model(directory, "2.3", "");
        write("ok.xtf", legacy("<name>ok</name>"));
        var err = new StringWriter();
        var broken = new Writer() {
            @Override public void write(char[] chars, int offset, int length) throws IOException {
                throw new IOException("broken");
            }
            @Override public void flush() throws IOException { throw new IOException("broken"); }
            @Override public void close() { }
        };
        assertEquals(3, ValidatorCommand.run(List.of("--json", "--offline", "ok.xtf"),
                directory, new PrintWriter(broken), new PrintWriter(err), CancellationToken.none()));
        assertTrue(err.toString().contains("Output failure"));
    }

    private static void assertFailed(Result result, int code, String phase) {
        assertEquals(code, result.exit(), result.output() + result.error());
        assertEquals(code, result.json().get("exitCode").getAsInt());
        assertEquals(phase, result.json().get("phase").getAsString());
        assertFalse(result.json().get("completed").getAsBoolean());
    }

    private void assertNoRunDirectories() throws IOException {
        try (var files = Files.list(directory)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith("interlis-validation-")));
        }
    }

    private Result run(String... arguments) {
        var output = new StringWriter();
        var error = new StringWriter();
        int exit = ValidatorCommand.run(List.of(arguments), directory, new PrintWriter(output),
                new PrintWriter(error), CancellationToken.none());
        return new Result(exit, output.toString(), error.toString());
    }

    private void write(String name, String content) throws Exception {
        Files.writeString(directory.resolve(name), content);
    }

    private void projectedModel(String version, String attributes) throws Exception {
        write("Checks.ili", """
                INTERLIS %s;
                MODEL Checks AT "https://example.org/checks" VERSION "1" =
                  DOMAIN Point = COORD 2600000.000 .. 2600015.000, 1200000.000 .. 1200015.000;
                  TOPIC Data =
                    CLASS Item =
                      name : MANDATORY TEXT*8;
                      %s
                    END Item;
                  END Data;
                END Checks.
                """.formatted(version, attributes));
    }

    private static String polygon23(int... offsets) {
        return "<shape><SURFACE><BOUNDARY><POLYLINE>" + positions("COORD", "C1", "C2", offsets)
                + "</POLYLINE></BOUNDARY></SURFACE></shape>";
    }

    private static String surface24(String rings) {
        return """
                <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS"
                    xmlns:m="http://www.interlis.ch/xtf/2.4/Checks" xmlns:g="http://www.interlis.ch/geometry/1.0">
                  <i:headersection><i:models><i:model>Checks</i:model></i:models><i:sender>test</i:sender></i:headersection>
                  <i:datasection><m:Data i:bid="parcel"><m:Item i:tid="ring">
                    <m:name>ring</m:name><m:shape><g:surface>%s</g:surface></m:shape>
                  </m:Item></m:Data></i:datasection>
                </i:transfer>
                """.formatted(rings);
    }

    private static String ring24(String boundary, int... offsets) {
        return "<g:%s><g:polyline>%s</g:polyline></g:%s>"
                .formatted(boundary, positions("g:coord", "g:c1", "g:c2", offsets), boundary);
    }

    private static String positions(String coord, String x, String y, int... offsets) {
        var coordinates = new StringBuilder();
        for (int index = 0; index < offsets.length; index += 2) {
            coordinates.append("<%s><%s>%d</%s><%s>%d</%s></%s>".formatted(coord, x, 2_600_000 + offsets[index], x,
                    y, 1_200_000 + offsets[index + 1], y, coord));
        }
        return coordinates.toString();
    }

    private static JsonObject assertGeometryEvidence(JsonObject diagnostic, String code, String kind) {
        var evidence = diagnostic.getAsJsonObject("geometryEvidence");
        assertNotNull(evidence, diagnostic.toString());
        assertEquals(code, evidence.get("code").getAsString(), evidence.toString());
        assertFalse(evidence.get("detail").getAsString().isBlank());
        assertEquals(kind, evidence.getAsJsonObject("witness").get("kind").getAsString(), evidence.toString());
        return evidence;
    }

    private static void assertWitness(JsonObject evidence, String x, String y) {
        assertEquals(0, ordinate(evidence, "x").compareTo(new BigDecimal(x)), evidence.toString());
        assertEquals(0, ordinate(evidence, "y").compareTo(new BigDecimal(y)), evidence.toString());
    }

    private static BigDecimal ordinate(JsonObject evidence, String axis) {
        var value = evidence.getAsJsonObject("witness").getAsJsonPrimitive(axis);
        assertTrue(value.isString(), evidence.toString());
        return new BigDecimal(value.getAsString());
    }

    private static boolean onInterval(BigDecimal value, String lower, String upper) {
        return value.compareTo(new BigDecimal(lower)) >= 0 && value.compareTo(new BigDecimal(upper)) <= 0;
    }

    private static boolean onExtreme(BigDecimal value, String lower, String upper) {
        return value.compareTo(new BigDecimal(lower)) == 0 || value.compareTo(new BigDecimal(upper)) == 0;
    }

    private static void assertPair(JsonObject evidence, String firstBasket, String secondBasket) {
        var primary = evidence.getAsJsonObject("location");
        assertEquals(1, evidence.getAsJsonArray("relatedLocations").size(), evidence.toString());
        var related = evidence.getAsJsonArray("relatedLocations").get(0).getAsJsonObject();
        assertEquals(Set.of(firstBasket, secondBasket), Set.of(primary.get("bid").getAsString(), related.get("bid").getAsString()));
        for (var location : List.of(primary, related)) {
            assertEquals("one", location.get("tid").getAsString());
            assertTrue(location.get("source").getAsString().endsWith("/" + location.get("bid").getAsString() + ".xtf"),
                    location.toString());
            assertTrue(location.get("path").getAsString().startsWith("/shape/RING[1]"), location.toString());
        }
    }

    private static void model(Path path, String version, String attributes) throws Exception {
        Files.writeString(path.resolve("Checks.ili"), """
                INTERLIS %s;
                MODEL Checks AT "https://example.org/checks" VERSION "1" =
                  DOMAIN Point = COORD 0.000 .. 10.000, 0.000 .. 10.000;
                  TOPIC Data =
                    CLASS Item =
                      name : MANDATORY TEXT*8;
                      %s
                    END Item;
                  END Data;
                END Checks.
                """.formatted(version, attributes));
    }

    private static String legacy(String fields) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <TRANSFER xmlns="http://www.interlis.ch/INTERLIS2.3">
                  <HEADERSECTION VERSION="2.3" SENDER="test"><MODELS>
                    <MODEL NAME="Checks" VERSION="1" URI="https://example.org/checks"/>
                  </MODELS></HEADERSECTION>
                  <DATASECTION><Checks.Data BID="basket"><Checks.Data.Item TID="one">
                    %s
                  </Checks.Data.Item></Checks.Data></DATASECTION>
                </TRANSFER>
                """.formatted(fields);
    }

    private record Result(int exit, String output, String error) {
        JsonObject json() { return JsonParser.parseString(output).getAsJsonObject(); }
    }
}

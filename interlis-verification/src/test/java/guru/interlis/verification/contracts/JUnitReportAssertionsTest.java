package guru.interlis.verification.contracts;

import com.google.gson.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.verification.contracts.ContractFiles.*;

class JUnitReportAssertionsTest {
    @TempDir Path temporary;
    static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    static final String XML = "<testsuite name=\"test.Example\" tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\" timestamp=\"2026-01-01T00:00:01Z\"><testcase classname=\"test.Example\" name=\"sample()\"/></testsuite>";
    @Test void exactXmlIdentityAndHashesPass() throws Exception {
        Path path = Files.writeString(temporary.resolve("TEST-example.xml"), XML);
        assertEquals(Set.of(new JUnitReportAssertions.Binding("test.Example", "sample()")), JUnitReportAssertions.xml(path, hash(path), START));
    }
    static List<String> badXml() {
        return List.of(XML.replace("classname=\"test.Example\"", "classname=\"wrong.Example\""),
                XML.replace("name=\"test.Example\"", "name=\"wrong.Example\""), XML.replace("sample()", "other()"),
                XML.replace("failures=\"0\"", "failures=\"1\""), XML.replace("errors=\"0\"", "errors=\"1\""),
                XML.replace("skipped=\"0\"", "skipped=\"1\""), XML.replace("tests=\"1\"", "tests=\"0\""),
                XML.replace("<testcase classname=\"test.Example\" name=\"sample()\"/>", ""),
                XML.replace("/>", "><failure/></testcase>"), XML.replace("/>", "><error/></testcase>"), XML.replace("/>", "><skipped/></testcase>"),
                XML.replace("2026-01-01T00:00:01Z", "2000-01-01T00:00:00Z"));
    }
    @ParameterizedTest @MethodSource("badXml") void wrongClassMethodSuiteCountersDetailsAndTimestampFail(String xml) throws Exception {
        Path path = Files.writeString(temporary.resolve("TEST-example.xml"), xml); JsonObject hashes = new JsonObject(); hashes.addProperty(path.getFileName().toString(), hash(path));
        assertThrows(Exception.class, () -> JUnitReportAssertions.fresh(Set.of(new JUnitReportAssertions.Binding("test.Example", "sample()")), temporary, START, hashes));
    }
    @Test void missingStaleOrChangedXmlNeverPasses() throws Exception {
        Path path = temporary.resolve("TEST-example.xml");
        assertThrows(Exception.class, () -> JUnitReportAssertions.xml(path, "0".repeat(64), START));
        Files.writeString(path, XML);
        assertThrows(Exception.class, () -> JUnitReportAssertions.xml(path, "0".repeat(64), START));
        Files.setLastModifiedTime(path, FileTime.from(Instant.parse("2000-01-01T00:00:00Z")));
        assertThrows(Exception.class, () -> JUnitReportAssertions.xml(path, hash(path), START));
    }
    @Test void obsoleteOrUnhashedSuiteFails() throws Exception {
        Path path = Files.writeString(temporary.resolve("TEST-extra.xml"), XML);
        assertThrows(Exception.class, () -> JUnitReportAssertions.fresh(Set.of(new JUnitReportAssertions.Binding("test.Example", "sample()")), temporary, START, new JsonObject()));
        JsonObject hashes = new JsonObject(); hashes.addProperty(path.getFileName().toString(), hash(path)); hashes.addProperty("TEST-missing.xml", "0".repeat(64));
        assertThrows(Exception.class, () -> JUnitReportAssertions.fresh(Set.of(new JUnitReportAssertions.Binding("test.Example", "sample()")), temporary, START, hashes));
    }
}

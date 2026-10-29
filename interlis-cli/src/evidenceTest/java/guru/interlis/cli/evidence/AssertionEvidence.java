package guru.interlis.cli.evidence;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import static guru.interlis.cli.evidence.EvidenceFiles.*;

final class AssertionEvidence {
    record Binding(String suite, String method) { }
    private AssertionEvidence() { }
    static Binding recorded(JsonObject evidence) throws IOException {
        String source = s(evidence, "source");
        require(source.startsWith("interlis-compiler/src/test/java/"), "Assertion outside compiler tests");
        String[] candidate = s(evidence, "candidateTest").split("\\.");
        require(candidate.length == 2, "Missing assertion method");
        JsonObject method = evidence.deepCopy();
        method.addProperty("method", candidate[1]);
        String body = method(method, "jvmSuite", "method");
        var fragments = strings(evidence.get("assertionFragments"));
        require(!fragments.isEmpty() && fragments.stream().allMatch(body::contains), "Invented assertion fragment");
        require(s(evidence, "jvmSuite").endsWith("." + candidate[0]), "Wrong assertion suite");
        var run = json(BASE + s(evidence, "runEvidence")).getAsJsonObject("freshSelectedRun");
        String xml = s(evidence, "jvmXml");
        require(xml.startsWith("build/") && !List.of(xml.split("/")).contains(".."), "Unsafe raw XML path");
        long count = rows(run, "reports").stream().filter(r -> s(r, "file").equals(xml)
                && s(r, "sha256").equals(s(evidence, "jvmXmlSha256")) && s(r, "suite").equals(s(evidence, "jvmSuite"))
                && strings(r.get("tests")).contains(candidate[1] + "()")
                && s(r, "failures").equals("0") && s(r, "errors").equals("0") && s(r, "skipped").equals("0")).count();
        require(n(run, "processExitCode") == 0 && count == 1 && s(evidence, "extent").equals("PARTIAL")
                && !s(evidence, "remainingGap").isBlank(), "Invalid recorded partial run");
        return new Binding(s(evidence, "jvmSuite"), candidate[1] + "()");
    }
    /** Checks the actual bytes and all three JUnit identities. Expected hashes come from the same fresh run. */
    static Set<Binding> xml(Path path, String sha, Instant started) throws Exception {
        require(Files.isRegularFile(path), "Missing JVM report: " + path);
        require(!Files.getLastModifiedTime(path).toInstant().isBefore(started), "Stale JVM report");
        byte[] bytes = Files.readAllBytes(path);
        require(hash(bytes).equals(sha), "Changed JVM report hash");
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
        Element suite = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes)).getDocumentElement();
        require(suite.getTagName().equals("testsuite") && !suite.getAttribute("name").isBlank(), "Missing JUnit suite");
        for (String counter : List.of("failures", "errors", "skipped")) require(suite.getAttribute(counter).equals("0"), "Unsuccessful JVM report");
        require(!Instant.parse(suite.getAttribute("timestamp") + (suite.getAttribute("timestamp").endsWith("Z") ? "" : "Z")).isBefore(started), "Stale JVM timestamp");
        NodeList cases = suite.getElementsByTagName("testcase");
        require(cases.getLength() > 0 && Integer.parseInt(suite.getAttribute("tests")) == cases.getLength(), "Incomplete JVM testcase details");
        Set<Binding> bindings = new LinkedHashSet<>();
        for (int i = 0; i < cases.getLength(); i++) {
            Element test = (Element) cases.item(i);
            require(test.getParentNode() == suite && suite.getAttribute("name").equals(test.getAttribute("classname"))
                    && !test.getAttribute("name").isBlank(), "Wrong JUnit testcase class");
            for (String kind : List.of("failure", "error", "skipped")) require(test.getElementsByTagName(kind).getLength() == 0, "Unsuccessful JVM testcase");
            require(bindings.add(new Binding(suite.getAttribute("name"), test.getAttribute("name"))), "Duplicate JVM testcase");
        }
        require(hash(path).equals(sha), "JVM report changed during validation");
        return bindings;
    }
    static void fresh(Set<Binding> expected, Path directory, Instant started, JsonObject hashes) throws Exception {
        require(!expected.isEmpty(), "No assertion bindings");
        Set<Binding> actual = new LinkedHashSet<>();
        Set<String> files = new HashSet<>();
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(p -> p.getFileName().toString().endsWith(".xml")).toList()) {
                String file = path.getFileName().toString(); files.add(file);
                require(hashes.has(file), "Unbound JVM report");
                for (Binding binding : xml(path, hashes.get(file).getAsString(), started)) require(actual.add(binding), "Duplicate JVM suite/test");
            }
        }
        require(files.equals(hashes.keySet()), "Unexpected or missing hashed JVM report");
        require(actual.equals(expected), "Unexpected or missing fresh JVM suites/methods: " + actual);
    }
}

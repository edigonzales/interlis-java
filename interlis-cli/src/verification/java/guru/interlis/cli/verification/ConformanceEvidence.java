package guru.interlis.cli.verification;

import com.google.gson.*;
import java.nio.file.*;
import static guru.interlis.cli.verification.HostChecks.*;

/** Current installed-compiler binding for the unchanged conformance runner/assessment. */
public final class ConformanceEvidence {
    private ConformanceEvidence() { }
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]).toAbsolutePath(),output=root.resolve("build/reports/conformance/candidate.json");Files.deleteIfExists(output);
        Path summaryFile=root.resolve("build/reports/conformance/conformance-summary.json");var summary=read(summaryFile);var counts=summary.getAsJsonObject("summary");
        require(s(summary,"overallResult").equals("PASS")&&counts.get("PASS").getAsInt()==577&&counts.get("XFAIL").getAsInt()==16&&counts.get("TOTAL").getAsInt()==593,"Unexpected conformance profile");
        for(String field:java.util.List.of("FAIL","XPASS","ERROR"))require(counts.get(field).getAsInt()==0,"Unsuccessful conformance outcome");
        require(summary.getAsJsonArray("normativeAssessments").size()==21,"Missing explicit normative controls");
        var report=new JsonObject();report.addProperty("status","passed");report.addProperty("gitCommit",ProfileFingerprint.commit(root));report.addProperty("dirty",ProfileFingerprint.dirty(root));report.addProperty("sourceFingerprint",ProfileFingerprint.current(root));report.addProperty("summarySha256",hash(summaryFile));report.addProperty("rawReportSha256",hash(root.resolve("build/conformance/report.json")));report.add("installedJars",JSON.toJsonTree(artifacts(root)));report.add("summary",summary);
        write(output,report);System.out.println("CONFORMANCE_CANDIDATE_PASS 577/16/0 controls=21");
    }
}

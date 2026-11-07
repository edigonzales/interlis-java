package guru.interlis.verification;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class VerificationLayoutTest {
    @TempDir Path temporary;
    @Test void explicitRootAndLaunchersPreserveUnicodeAndSpaces() throws Exception {
        var root = Files.createDirectories(temporary.resolve("project 😀 space"));
        var layout = new VerificationLayout(root.resolve("x/.."));
        assertEquals(root, layout.repositoryRoot());
        assertEquals(root.resolve("target/jenesis-jvm/delivery/compiler/bin/interlis-compiler.bat"), layout.compilerLauncher(true));
        assertEquals(root.resolve("target/jenesis-jvm/reports/junit/interlis-verification/test"), layout.verificationUnitReports());
        assertEquals(root.resolve("target/jenesis-jvm/reports/junit/interlis-verification/contractTest"), layout.verificationContractReports());
        assertEquals(root.resolve("target/jenesis-jvm/reports/sdk/result.json"), layout.rootReport("sdk/result.json"));
        for (String bad : new String[]{"..", "/tmp", "a/b", "C:\\x", ""}) {
            assertThrows(IllegalArgumentException.class, () -> layout.junitDirectory(bad, "test"));
            assertThrows(IllegalArgumentException.class, () -> layout.junitDirectory("module", bad));
        }
        for (String bad : new String[]{"../x", "/tmp/x", "C:\\x", ""})
            assertThrows(IllegalArgumentException.class, () -> layout.rootReport(bad));
        var reports = Files.createDirectories(root.resolve("target/jenesis-jvm/reports"));
        Files.createSymbolicLink(reports.resolve("escape"), temporary);
        assertThrows(IllegalArgumentException.class, () -> layout.rootReport("escape/result.json"));
    }
    @Test void allEntrypointsAndChildMainsResolveOnTheNewRuntimeClasspath() throws Exception {
        for (String name : new String[]{"HostChecks", "ProfileFingerprint", "CompilerCliProcesses", "SdkProcesses",
                "SdkArchiveCheck", "PlatformProcesses", "ConformanceEvidence", "CorpusExpectations", "GeoCorpus",
                "FullCorpus", "FullCorpusRun", "FullCorpusCold", "JvmBenchmark", "EditorPressure", "ObservationPressure",
                "ReferenceProcesses", "RepositoryReferenceCheck", "SemanticComparison", "PythonFreeCheck"}) {
            var type = Class.forName("guru.interlis.verification." + name);
            if (!java.util.Set.of("HostChecks", "ProfileFingerprint", "CorpusExpectations").contains(name))
                assertEquals(void.class, type.getMethod("main", String[].class).getReturnType());
        }
        var child = HostChecks.run(temporary, HostChecks.javaCommand("guru.interlis.verification.FullCorpusCold"), java.time.Duration.ofSeconds(10));
        assertNotEquals(0, child.exitCode());
        assertFalse(child.stderr().contains("ClassNotFoundException"), child.stderr());
        assertFalse(child.stderr().contains("NoClassDefFoundError"), child.stderr());
    }
    @Test void explicitJenesisLayoutHasNoGradleFallbackAndRejectsEscapes() throws Exception {
        Path root=Files.createDirectory(temporary.resolve("root"));
        Path output=Files.createDirectories(root.resolve("target/jenesis-jvm"));
        Path file=output.resolve("layout.properties");
        String valid="schemaVersion=1\nbuilder=jenesis\nroot="+root.toString().replace("\\","\\\\")+"\nreports=target/jenesis-jvm/reports\ncompiler=target/jenesis-jvm/delivery/compiler\n";
        Files.writeString(file,valid);var layout=VerificationLayout.read(root,file);
        assertEquals(output.resolve("delivery/compiler/bin/interlis-compiler"),layout.compilerLauncher(false));
        assertThrows(IllegalArgumentException.class,()->layout.junitDirectory("interlis-compiler","test"));
        assertEquals(output.resolve("reports/probe.json"),layout.rootReport("probe.json"));
        for(String bad:new String[]{"../../outside","build/install/compiler","/tmp/compiler"}) {
            Files.writeString(file,valid.replace("target/jenesis-jvm/delivery/compiler",bad));
            assertThrows(IllegalArgumentException.class,()->VerificationLayout.read(root,file));
        }
        Files.writeString(file,valid);Files.createSymbolicLink(output.resolve("delivery"),temporary);
        assertThrows(IllegalArgumentException.class,()->VerificationLayout.read(root,file));
    }
    @Test void portableWriterFormatResolvesWindowsNativeRelativePaths() throws Exception {
        Path root=Files.createDirectories(temporary.resolve("windows-paths"));
        Path file=Files.createDirectories(root.resolve("target/jenesis-jvm")).resolve("layout.properties");
        var properties=new java.util.Properties();properties.setProperty("schemaVersion","1");properties.setProperty("builder","jenesis");properties.setProperty("root",root.toString());
        String nativeReports="target\\jenesis-jvm\\host\\run\\reports";
        properties.setProperty("junit.interlis-xtf23.test","target/jenesis-jvm/host/run/reports/xtf23");
        properties.setProperty("manifest.interlis-xtf23","target/jenesis-jvm/host/run/reports/xtf23.json");
        properties.setProperty("reports",nativeReports.replace('\\','/'));
        try(var writer=Files.newBufferedWriter(file)){properties.store(writer,"portable layout format");}
        var layout = VerificationLayout.read(root,file);
        assertEquals(root.resolve("target/jenesis-jvm/host/run/reports/probe.json"),layout.rootReport("probe.json"));
        assertEquals(root.resolve("target/jenesis-jvm/host/run/reports/xtf23"),layout.junitDirectory("interlis-xtf23","test"));
        assertEquals(root.resolve("target/jenesis-jvm/host/run/reports/xtf23.json"),layout.contractAssertionManifest("interlis-xtf23"));
        properties.setProperty("reports",nativeReports);
        try(var writer=Files.newBufferedWriter(file)){properties.store(writer,"nonportable input");}
        assertThrows(IllegalArgumentException.class,()->VerificationLayout.read(root,file));
    }
}

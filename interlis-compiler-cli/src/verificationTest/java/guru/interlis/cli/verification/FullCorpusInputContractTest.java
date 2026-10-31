package guru.interlis.cli.verification;

import com.google.gson.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.cli.verification.HostChecks.*;

class FullCorpusInputContractTest {
    @TempDir Path temporary;
    private Path copy() throws Exception {
        Path directory=Path.of(System.getProperty("repo.root")).resolve("docs/evidence/p3/full-corpus");
        Files.copy(directory.resolve("manifest.json"),temporary.resolve("manifest.json"));
        Files.copy(directory.resolve("sources.zip"),temporary.resolve("sources.zip"));return temporary;
    }
    @Test void completeIndexAndExplicitLegacyDecodingReproducePinnedTexts() throws Exception {
        var result=FullCorpus.prepare(copy());assertEquals(347,result.getAsJsonArray("roots").size());
        var model=rows(result,"roots").stream().filter(r->s(r,"model").equals("SO_Nutzungsplanung_20171118")).findFirst().orElseThrow();
        var source=rows(model,"sources").stream().filter(s->s(s,"file").equals("ARP/SO_Nutzungsplanung_20171118.ili")).findFirst().orElseThrow();
        assertEquals("windows-1252",s(source,"encoding"));assertTrue(s(source,"text").contains("\u201c"));
        assertFalse(s(source,"text").contains("\u0093"));
    }
    @Test void omittedRootCannotTurnIntoAPartialSuccessfulCorpus() throws Exception {
        var directory=copy();var manifest=read(directory.resolve("manifest.json"));manifest.getAsJsonArray("roots").remove(0);write(directory.resolve("manifest.json"),manifest);
        assertThrows(IllegalArgumentException.class,()->FullCorpus.prepare(directory));
    }
    @Test void legacyDecoderMismatchAndUnapprovedEncodingsFailClosed() throws Exception {
        var directory=copy();var manifest=read(directory.resolve("manifest.json"));var file=rows(manifest,"files").stream().filter(f->s(f,"encoding").equals("windows-1252")).findFirst().orElseThrow();
        file.addProperty("encoding","ISO-8859-1");write(directory.resolve("manifest.json"),manifest);assertThrows(IllegalArgumentException.class,()->FullCorpus.prepare(directory));
        file.addProperty("encoding","windows-1252");file.addProperty("textSha256","0".repeat(64));write(directory.resolve("manifest.json"),manifest);assertThrows(IllegalArgumentException.class,()->FullCorpus.prepare(directory));
    }
    @Test void changedOriginalArchiveIsNeverRepairedOrDownloaded() throws Exception {
        var directory=copy();Path archive=directory.resolve("sources.zip");byte[] changed=Files.readAllBytes(archive);changed[200]^=1;Files.write(archive,changed);
        assertThrows(IllegalArgumentException.class,()->FullCorpus.prepare(directory));assertArrayEquals(changed,Files.readAllBytes(archive));
    }
}

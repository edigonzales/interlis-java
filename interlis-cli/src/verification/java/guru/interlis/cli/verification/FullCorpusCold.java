package guru.interlis.cli.verification;

import guru.interlis.bridge.BridgeDispatcher;
import java.nio.file.Path;
import static guru.interlis.cli.verification.HostChecks.*;

/** Fresh JVM process: pinned input verification, session opening and first real closure compile. */
public final class FullCorpusCold {
    private FullCorpusCold() { }
    public static void main(String[] args) throws Exception {
        var prepared=FullCorpus.prepare(Path.of(args[0]).resolve("docs/evidence/p3/full-corpus"));
        var row=FullCorpusRun.capture(new BridgeDispatcher(),rows(prepared,"roots").getFirst(),0);
        require(s(row,"outcome").equals("succeeded"),"Cold representative closure failed");
        System.out.println("FULL_CORPUS_COLD_PASS "+s(row,"model"));
    }
}

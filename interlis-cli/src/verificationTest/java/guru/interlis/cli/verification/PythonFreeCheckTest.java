package guru.interlis.cli.verification;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PythonFreeCheckTest {
    @Test void sourceManifestAndGradleShellNodeOrJavaCallsFail() {
        for(String path:new String[]{"tool.py","docs/archived.py","pyproject.toml","requirements.txt"}) assertThrows(Exception.class,()->PythonFreeCheck.validate(path,""));
        for(String path:new String[]{"build.gradle","tools/tool.sh","tools/tool.cjs","tools/Host.java",".github/workflows/build.yml"}) {
            String interpreter="py"+"thon3";
            assertThrows(Exception.class,()->PythonFreeCheck.validate(path,"commandLine '"+interpreter+"', 'script'"));
            assertThrows(Exception.class,()->PythonFreeCheck.validate(path,"'"+"pi"+"p3 install dependency'"));
        }
        assertThrows(Exception.class,()->PythonFreeCheck.validate("tools/a.sh","#!/usr/bin/env "+"py"+"thon3\n"));
        PythonFreeCheck.validate("docs/evidence/old.md","Historical "+"py"+"thon3 command");
        PythonFreeCheck.validate("tools/ok.cjs","spawn('node', ['contract.cjs'])");
    }
    @Test void absoluteAndEnvWrappedShellCommandsFail() {
        String interpreter="py"+"thon3";
        for(String command:new String[]{
                "exec /usr/bin/env "+interpreter+" \"$@\"",
                "/usr/bin/env "+interpreter+" tool",
                "command /usr/bin/"+interpreter+" tool",
                "/usr/bin/"+interpreter+" tool",
                "env -u UNUSED PROFILE=test "+interpreter+" tool",
                "env -u UNUSED "+interpreter+" tool",
                "env --unset=UNUSED "+interpreter+" tool",
                "echo input | xargs "+interpreter+" tool",
                "xargs -n 1 /usr/bin/env "+interpreter+" tool",
                "xargs -I {} /usr/bin/"+interpreter+" {}",
                "result=$(/usr/bin/"+interpreter+" --version)",
                "result=`env "+interpreter+" --version`",
                "run: /usr/bin/env "+interpreter+" tool",
                "exec /usr/bin/"+"pi"+"p3 install dependency"}) {
            assertThrows(Exception.class,()->PythonFreeCheck.validate("tools/a.sh",command),command);
        }
        PythonFreeCheck.validate("tools/a.sh","exec /usr/bin/env node contract.cjs");
    }
}

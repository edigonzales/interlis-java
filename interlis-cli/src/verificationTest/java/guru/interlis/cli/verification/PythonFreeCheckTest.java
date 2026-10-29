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
}

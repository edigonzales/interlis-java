package guru.interlis.compiler;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Consumers parse with ANTLR runtime while the generator stays a build dependency. */
class ParserRuntimeDependencyContractTest {
    @Test void runtimeIsPresentAndGeneratorToolsAreAbsent() throws Exception {
        assertNotNull(Class.forName("org.antlr.v4.runtime.RuntimeMetaData"));
        for (String tool : java.util.List.of("org.antlr.v4.Tool", "org.stringtemplate.v4.ST",
                "org.antlr.runtime.Token", "com.ibm.icu.text.UnicodeSet"))
            assertThrows(ClassNotFoundException.class, () -> Class.forName(tool), tool);
    }
}

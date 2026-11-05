package guru.interlis.compiler;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ModuleIdentityTest {
    @Test void productAndPatchedTestsAreInTheExplicitCompilerModule() {
        assertTrue(CompilerEngine.class.getModule().isNamed());
        assertEquals("guru.interlis.compiler",CompilerEngine.class.getModule().getName());
        assertSame(CompilerEngine.class.getModule(),getClass().getModule());
        assertFalse(getClass().getModule().isExported("guru.interlis.compiler.internal"));
    }
}

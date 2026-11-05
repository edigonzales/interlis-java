package guru.interlis.cli;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ModuleIdentityTest {
    @Test void productAndPatchedTestsUseTheExplicitModule() {
        assertTrue(CliApplication.class.getModule().isNamed());
        assertEquals("guru.interlis.compiler.cli",CliApplication.class.getModule().getName());
        assertSame(CliApplication.class.getModule(),getClass().getModule());
    }
}

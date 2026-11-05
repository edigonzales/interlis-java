package guru.interlis.lsp;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ModuleIdentityTest {
    @Test void productAndPatchedTestsUseTheExplicitModule() {
        assertTrue(InterlisLanguageServer.class.getModule().isNamed());
        assertEquals("guru.interlis.lsp",InterlisLanguageServer.class.getModule().getName());
        assertSame(InterlisLanguageServer.class.getModule(),getClass().getModule());
    }
}

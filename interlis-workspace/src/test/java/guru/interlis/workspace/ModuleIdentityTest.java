package guru.interlis.workspace;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ModuleIdentityTest {
    @Test void productAndPatchedTestsUseTheExplicitModule() {
        assertTrue(Workspace.class.getModule().isNamed());
        assertEquals("guru.interlis.workspace",Workspace.class.getModule().getName());
        assertSame(Workspace.class.getModule(),getClass().getModule());
    }
}

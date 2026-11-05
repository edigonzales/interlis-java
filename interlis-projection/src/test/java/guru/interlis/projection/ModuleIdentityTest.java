package guru.interlis.projection;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ModuleIdentityTest {
    @Test void productAndPatchedTestsUseTheExplicitModule() {
        assertTrue(DiagramProjector.class.getModule().isNamed());
        assertEquals("guru.interlis.projection",DiagramProjector.class.getModule().getName());
        assertSame(DiagramProjector.class.getModule(),getClass().getModule());
    }
}

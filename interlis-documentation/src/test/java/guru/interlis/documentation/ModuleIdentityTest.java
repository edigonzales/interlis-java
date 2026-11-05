package guru.interlis.documentation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ModuleIdentityTest {
    @Test void productAndPatchedTestsUseTheExplicitModule() {
        assertTrue(DocumentationProjector.class.getModule().isNamed());
        assertEquals("guru.interlis.documentation",DocumentationProjector.class.getModule().getName());
        assertSame(DocumentationProjector.class.getModule(),getClass().getModule());
    }
}

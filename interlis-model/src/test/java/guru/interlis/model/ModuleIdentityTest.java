package guru.interlis.model;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ModuleIdentityTest {
    @Test void productAndPatchedTestsAreInTheExplicitModelModule() {
        assertTrue(sourceModule().isNamed());
        assertEquals("guru.interlis.model",sourceModule().getName());
        assertSame(sourceModule(),getClass().getModule());
    }
    private static Module sourceModule(){return guru.interlis.source.SourceId.class.getModule();}
}

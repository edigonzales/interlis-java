package guru.interlis.repository;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ModuleIdentityTest {
    @Test void productAndPatchedTestsUseTheExplicitModule() {
        assertTrue(RepositoryClient.class.getModule().isNamed());
        assertEquals("guru.interlis.repository",RepositoryClient.class.getModule().getName());
        assertSame(RepositoryClient.class.getModule(),getClass().getModule());
    }
}

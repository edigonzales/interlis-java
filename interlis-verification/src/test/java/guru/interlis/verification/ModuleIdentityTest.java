package guru.interlis.verification;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ModuleIdentityTest {
    @Test void productAndPatchedTestsUseTheExplicitModule() {
        assertTrue(VerificationLayout.class.getModule().isNamed());
        assertEquals("guru.interlis.verification",VerificationLayout.class.getModule().getName());
        assertSame(VerificationLayout.class.getModule(),getClass().getModule());
    }
}

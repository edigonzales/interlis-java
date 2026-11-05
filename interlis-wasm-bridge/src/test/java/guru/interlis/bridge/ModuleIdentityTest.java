package guru.interlis.bridge;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ModuleIdentityTest {
    @Test void productAndPatchedTestsUseTheExplicitModule() {
        assertTrue(BridgeDispatcher.class.getModule().isNamed());
        assertEquals("guru.interlis.wasm.bridge",BridgeDispatcher.class.getModule().getName());
        assertSame(BridgeDispatcher.class.getModule(),getClass().getModule());
    }
}

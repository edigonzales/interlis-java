package guru.interlis.wasm.teavm;

import guru.interlis.bridge.BridgeDispatcher;
import org.teavm.jso.JSExport;

/** TeaVM-specific entry; contains no INTERLIS semantics. */
public final class TeaVmEntryPoint {
    private static final BridgeDispatcher DISPATCHER = new BridgeDispatcher();

    private TeaVmEntryPoint() {
    }

    @JSExport
    /**
     * Provides {@code handle} contract.
     *
     * @param requestJson the {@code requestJson} argument
     *
     * @return the result
     */
    public static String handle(String requestJson) {
        return DISPATCHER.handle(requestJson);
    }

    /**
     * Provides {@code main} contract.
     *
     * @param args the {@code args} argument
     *
     * @return the result
     */
    public static void main(String[] args) {
        // The module is driven through the exported handle function.
    }
}

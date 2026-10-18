package guru.interlis.wasm.webimage;

import guru.interlis.bridge.BridgeDispatcher;
import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSString;

import java.util.function.Function;

/** Web Image bootstrap; the API annotation is compile-time only. */
public final class WebImageEntryPoint {
    private static final BridgeDispatcher DISPATCHER = new BridgeDispatcher();

    private WebImageEntryPoint() {
    }

    @JS(args = {"handler"}, value = "globalThis.interlisCompilerHandle = handler;")
    private static native void export(Function<JSString, JSString> handler);

    public static void main(String[] args) {
        export(request -> JSString.of(DISPATCHER.handle(request.asString())));
    }
}

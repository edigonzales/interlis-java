package guru.interlis.lsp;

import guru.interlis.compiler.CompilerInternalException;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.*;
import java.util.UUID;
import java.util.function.Consumer;

/** Protocol failures carry safe identifiers, never Throwable serialization or stack traces. */
final class LspFailures {
    private LspFailures() { }
    static ResponseErrorException error(ResponseErrorCode code,String message) {
        return new ResponseErrorException(new ResponseError(code,message,null));
    }
    static ResponseErrorException internal(Throwable failure,Consumer<String> operations) {
        String id=failure instanceof CompilerInternalException compiler ? compiler.failureId().value() : "lsp-"+UUID.randomUUID();
        try { operations.accept("Internal language service failure "+id); } catch(RuntimeException disconnected) { }
        return error(ResponseErrorCode.InternalError,"internal language service failure "+id);
    }
    static Throwable response(Throwable failure,Consumer<String> operations) {
        while((failure instanceof java.util.concurrent.CompletionException || failure instanceof java.util.concurrent.ExecutionException)
                && failure.getCause()!=null) failure=failure.getCause();
        if(failure instanceof ResponseErrorException || failure instanceof java.util.concurrent.CancellationException) return failure;
        return internal(failure,operations);
    }
}

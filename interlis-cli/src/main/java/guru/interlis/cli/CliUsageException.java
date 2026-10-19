package guru.interlis.cli;

/** Explicit command-line usage failure; never a compiler-internal error. */
final class CliUsageException extends RuntimeException {
    CliUsageException(String message) {
        super(message);
    }
}

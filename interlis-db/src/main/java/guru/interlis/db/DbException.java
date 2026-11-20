package guru.interlis.db;

/** An unsupported or inconsistent database mapping or an unencodable transfer value. */
final class DbException extends RuntimeException {
    DbException(String message) {
        super(message);
    }

    DbException(String message, Throwable cause) {
        super(message, cause);
    }
}

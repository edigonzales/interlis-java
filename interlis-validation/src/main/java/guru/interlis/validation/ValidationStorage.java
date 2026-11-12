package guru.interlis.validation;

/** Single-caller byte storage for one validation run. The host owns its lifetime. */
public interface ValidationStorage extends AutoCloseable {
    ValidationTable table(StorageArea area);
    void checkpoint();
    StorageUsage usage();
    @Override
    void close();
}

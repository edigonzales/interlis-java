package guru.interlis.lsp;
/** Immutable readonly text and its exact range/digest identity; never a path to a writable cache file. */
public record SourceContent(String uri,String sourceRevision,String text,boolean readonly,String origin,String digest,String digestAlgorithm) { }

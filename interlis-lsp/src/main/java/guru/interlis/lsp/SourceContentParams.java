package guru.interlis.lsp;
/** Server-issued handle plus exact decimal core source revision. No arbitrary URI loading. */
public record SourceContentParams(String handle,String expectedRevision) { }

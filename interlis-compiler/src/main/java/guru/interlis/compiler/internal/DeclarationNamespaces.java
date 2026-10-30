package guru.interlis.compiler.internal;

import guru.interlis.model.SymbolKind;
import java.util.Set;

/** Shared declaration categories for semantic validation and safe language edits. */
public final class DeclarationNamespaces {
    private DeclarationNamespaces() { }
    public static final Set<SymbolKind> TYPE_KINDS=Set.of(SymbolKind.TOPIC,SymbolKind.CLASS,SymbolKind.STRUCTURE,SymbolKind.ASSOCIATION,
            SymbolKind.VIEW,SymbolKind.GRAPHIC,SymbolKind.DOMAIN,SymbolKind.UNIT,SymbolKind.FUNCTION,
            SymbolKind.LINE_FORM,SymbolKind.METADATA_BASKET);
}

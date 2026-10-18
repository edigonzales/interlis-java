package guru.interlis.compiler.internal;

import guru.interlis.model.LanguageVersion;
import guru.interlis.model.Modifier;
import guru.interlis.model.Origin;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolKind;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRange;
import guru.interlis.syntax.SyntaxNode;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** One indexed declaration with stable snapshot-local identity. */
record DeclInfo(SymbolId id, SymbolKind kind, String name, Optional<SymbolId> owner,
                int order, Origin origin, Optional<SourceRange> nameRange,
                LanguageVersion language, Set<Modifier> modifiers, SyntaxNode syntax,
                SourceId sourceId, String qualifiedName, Optional<String> featureId) {
    DeclInfo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(name, "name");
        owner = Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(origin, "origin");
        nameRange = Objects.requireNonNull(nameRange, "nameRange");
        Objects.requireNonNull(language, "language");
        modifiers = Set.copyOf(Objects.requireNonNull(modifiers, "modifiers"));
        Objects.requireNonNull(syntax, "syntax");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(qualifiedName, "qualifiedName");
        featureId = Objects.requireNonNull(featureId, "featureId");
    }
}

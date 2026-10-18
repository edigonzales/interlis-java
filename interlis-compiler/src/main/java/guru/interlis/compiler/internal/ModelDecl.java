package guru.interlis.compiler.internal;

import guru.interlis.source.SourceText;
import guru.interlis.syntax.ModelSyntax;

import java.util.Objects;

/** One parsed model declaration in one source revision. */
record ModelDecl(ModelSyntax syntax, SourceText source) {
    ModelDecl {
        Objects.requireNonNull(syntax, "syntax");
        Objects.requireNonNull(source, "source");
    }

    String name() {
        return syntax.name();
    }

    String version() {
        return syntax.modelVersion();
    }

    int declarationOffset() {
        return syntax.range().startOffset();
    }

    guru.interlis.source.SourceId sourceId() {
        return source.id();
    }
}

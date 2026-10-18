package guru.interlis.diagnostics;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

/**
 * Typed diagnostic argument. Values are data; renderers escape and may
 * truncate for display but must not reinterpret them.
 */
public sealed interface DiagnosticArgument
        permits DiagnosticArgument.TextArgument, DiagnosticArgument.IdentifierArgument,
                DiagnosticArgument.TokenArgument, DiagnosticArgument.IntegerArgument,
                DiagnosticArgument.DecimalArgument, DiagnosticArgument.TypeNameArgument,
                DiagnosticArgument.IdentifierListArgument {

    /** Returns the stable argument name used by catalog and renderers. */
    String name();

    /**
     * Free text argument.
     *
     * @param name stable argument name
     * @param value text value
     */
    record TextArgument(String name, String value) implements DiagnosticArgument {
        /** Creates a validated instance. */
        public TextArgument {
            requireName(name);
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * Identifier argument.
     *
     * @param name stable argument name
     * @param value identifier value
     */
    record IdentifierArgument(String name, String value) implements DiagnosticArgument {
        /** Creates a validated instance. */
        public IdentifierArgument {
            requireName(name);
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * Source-token argument.
     *
     * @param name stable argument name
     * @param value token text
     */
    record TokenArgument(String name, String value) implements DiagnosticArgument {
        /** Creates a validated instance. */
        public TokenArgument {
            requireName(name);
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * Exact integer argument.
     *
     * @param name stable argument name
     * @param value exact integer
     */
    record IntegerArgument(String name, BigInteger value) implements DiagnosticArgument {
        /** Creates a validated instance. */
        public IntegerArgument {
            requireName(name);
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * Exact decimal argument with optional original lexical form.
     *
     * @param name stable argument name
     * @param value exact decimal value
     * @param raw original lexical text, if available
     */
    record DecimalArgument(String name, BigDecimal value, java.util.Optional<String> raw)
            implements DiagnosticArgument {
        /** Creates a validated instance. */
        public DecimalArgument {
            requireName(name);
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(raw, "raw");
        }
    }

    /**
     * Type-name argument.
     *
     * @param name stable argument name
     * @param value type name
     */
    record TypeNameArgument(String name, String value) implements DiagnosticArgument {
        /** Creates a validated instance. */
        public TypeNameArgument {
            requireName(name);
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * Ordered identifier-list argument.
     *
     * @param name stable argument name
     * @param values identifier values in display order
     */
    record IdentifierListArgument(String name, List<String> values) implements DiagnosticArgument {
        /** Creates a validated instance. */
        public IdentifierListArgument {
            requireName(name);
            values = List.copyOf(Objects.requireNonNull(values, "values"));
        }
    }

    private static void requireName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("argument name must not be blank");
        }
    }
}

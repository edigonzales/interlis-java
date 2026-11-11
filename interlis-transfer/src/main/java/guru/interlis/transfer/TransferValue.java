package guru.interlis.transfer;

import guru.interlis.model.CollectionKind;
import guru.interlis.model.QualifiedName;
import guru.interlis.model.SnapshotMismatchException;
import guru.interlis.model.SymbolRef;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable, owned transfer value. A containing object represents an absent value by
 * omitting its slot. Values and collection entries are nonnull. */
public sealed interface TransferValue permits TransferValue.Text, TransferValue.BooleanValue,
        TransferValue.Enumeration, TransferValue.NumberValue, TransferValue.Structured,
        TransferValue.Repeated, TransferValue.Reference, TransferValue.Binary, TransferValue.XmlFragment,
        TransferValue.Oid, TransferValue.ClassName, TransferValue.AttributePath,
        TransferValue.Undefined, TransferGeometry {

    /** Binary payload with content equality. The constructor and accessor copy the bytes. */
    record Binary(byte[] bytes) implements TransferValue {
        public Binary {
            bytes = bytes.clone();
        }

        @Override public byte[] bytes() { return bytes.clone(); }
        /** Returns the payload length in bytes without copying it. */
        public int size() { return bytes.length; }
        @Override public boolean equals(Object other) {
            return other instanceof Binary binary && Arrays.equals(bytes, binary.bytes);
        }
        @Override public int hashCode() { return Arrays.hashCode(bytes); }
    }

    /**
     * Serialized XML fragment, possibly empty, with the in-scope namespace
     * bindings of its containing value. Bindings also preserve the interpretation
     * of textual QNames outside child elements. Consumers must securely parse
     * the fragment before emitting it. The map is immutable, with unspecified
     * iteration order. An empty prefix denotes the default namespace.
     */
    record XmlFragment(String xml, Map<String, String> namespaceBindings) implements TransferValue {
        /** Creates a self-contained fragment without additional namespace bindings. */
        public XmlFragment(String xml) {
            this(xml, Map.of());
        }

        public XmlFragment {
            Objects.requireNonNull(xml, "xml");
            namespaceBindings = Map.copyOf(namespaceBindings);
        }
    }

    /** Exact identifier spelling of an attribute value, including any identifier space prefix. */
    record Oid(String lexeme) implements TransferValue {
        public Oid {
            if (Objects.requireNonNull(lexeme, "lexeme").isBlank()) {
                throw new IllegalArgumentException("An OID needs its lexical spelling");
            }
        }
    }

    /** Qualified declaration name in a CLASS or STRUCTURE slot.
     * The slot's type determines the declaration family and any restrictions. */
    record ClassName(QualifiedName name) implements TransferValue {
        public ClassName {
            Objects.requireNonNull(name, "name");
        }
    }

    /** Qualified owner name followed by its attribute name. */
    record AttributePath(QualifiedName name) implements TransferValue {
        public AttributePath {
            Objects.requireNonNull(name, "name");
        }
    }

    /** Exact text, including the empty string. */
    record Text(String value) implements TransferValue {
        public Text {
            Objects.requireNonNull(value, "value");
        }
    }

    /** Boolean value. */
    record BooleanValue(boolean value) implements TransferValue { }

    /** An enumeration value in root-to-leaf component order. */
    record Enumeration(List<String> path) implements TransferValue {
        public Enumeration {
            path = List.copyOf(path);
            if (path.isEmpty() || path.stream().anyMatch(String::isBlank)) {
                throw new IllegalArgumentException("An enumeration path needs nonblank components");
            }
        }
    }

    /** Exact decimal value and source spelling. The adapter supplies the interpretation
     * of its own numeric notation. Both components participate in value equality. */
    record NumberValue(String lexeme, BigDecimal exactValue) implements TransferValue {
        public NumberValue {
            if (Objects.requireNonNull(lexeme, "lexeme").isBlank()) {
                throw new IllegalArgumentException("A number needs its lexical spelling");
            }
            Objects.requireNonNull(exactValue, "exactValue");
        }
    }

    /** Fields of an object or a nested structure. The immutable index preserves the supplied
     * iteration order. The plan's slots define model order. The type and every slot key belong
     * to the same snapshot. Values are
     * already constructed and defensively captured, so nested values can share immutable
     * subtrees. TransferObject checks snapshot ownership across the complete object. */
    record Structured(SymbolRef type, Map<SymbolRef, TransferValue> values) implements TransferValue {
        public Structured {
            Objects.requireNonNull(type, "type");
            var copy = new LinkedHashMap<SymbolRef, TransferValue>();
            for (var field : values.entrySet()) {
                checkSlot(type, field.getKey());
                copy.put(field.getKey(), Objects.requireNonNull(field.getValue(), "value"));
            }
            values = Collections.unmodifiableMap(copy);
        }

        /** Returns a supplied value or empty for an absent slot.
         * @throws SnapshotMismatchException if the key belongs to a different snapshot
         */
        public Optional<TransferValue> value(SymbolRef slot) {
            checkSlot(type, slot);
            return Optional.ofNullable(values.get(slot));
        }

        private static void checkSlot(SymbolRef type, SymbolRef slot) {
            if (!type.snapshot().equals(slot.snapshot())) {
                throw new SnapshotMismatchException(type.snapshot(), slot.snapshot());
            }
        }
    }

    /** BAG or LIST of primitive or structured values. The supplied order and duplicates
     * are retained for both kinds. Only LIST gives that order model semantics. */
    record Repeated(CollectionKind kind, List<TransferValue> values) implements TransferValue {
        public Repeated {
            Objects.requireNonNull(kind, "kind");
            values = List.copyOf(values);
        }
    }

    /** Reference to an object identity. Basket and position are retained when supplied.
     * The external flag records the EXTERNAL reference semantics that permit a target in
     * another basket. The supplied target basket is independent of that flag. A position
     * is absolute in the ordered relation within the transfer basket and is one-based. */
    record Reference(TransferIdentity.ObjectKey target, Optional<TransferIdentity.BasketId> targetBasket,
                     boolean external, Optional<BigInteger> orderPosition) implements TransferValue {
        public Reference {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(targetBasket, "targetBasket");
            Objects.requireNonNull(orderPosition, "orderPosition").ifPresent(position -> {
                if (position.signum() <= 0) {
                    throw new IllegalArgumentException("A reference position must be positive");
                }
            });
        }
    }

    /** A supplied value explicitly marked undefined by the input. */
    enum Undefined implements TransferValue { INSTANCE }
}

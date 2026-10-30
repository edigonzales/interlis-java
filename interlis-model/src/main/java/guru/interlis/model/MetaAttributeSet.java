package guru.interlis.model;

import java.util.*;

/**
 * Immutable effective metadata, distinct from transfer data. Overlay occurrences
 * precede the unchanged source occurrences in explicit priority order; source
 * order and every provenance are retained. The map indexes the first occurrence.
 *
 * @param values null-free first-occurrence index
 * @param entries null-free occurrences in effective precedence order, then source order, including repetitions
 */
public record MetaAttributeSet(Map<String, Value> values, List<Entry> entries) {
    /** Copies both inputs and rejects an index inconsistent with the occurrences. */
    public MetaAttributeSet {
        Objects.requireNonNull(values,"values");
        entries=List.copyOf(Objects.requireNonNull(entries,"entries"));
        var first=new LinkedHashMap<String,Value>();
        for(var entry:entries)first.putIfAbsent(entry.key(),entry.value());
        if(!first.equals(values))throw new IllegalArgumentException("metadata index differs from first occurrences");
        values=Collections.unmodifiableMap(first);
    }

    /** Retains the map constructor: each map entry becomes one ordered occurrence. */
    public MetaAttributeSet(Map<String,Value> values) {
        this(values,Objects.requireNonNull(values,"values").entrySet().stream().map(e->new Entry(e.getKey(),e.getValue())).toList());
    }

    /** One immutable occurrence.
     * @param key non-null metadata key
     * @param value non-null value retaining its original provenance
     */
    public record Entry(String key,Value value) {
        public Entry {Objects.requireNonNull(key,"key");Objects.requireNonNull(value,"value");}
    }

    /** Immutable value with its original declared, derived or predefined provenance.
     * @param text non-null decoded text, without normalization
     * @param origin non-null original provenance, including source revision when declared
     */
    public record Value(String text,Origin origin) {
        public Value {Objects.requireNonNull(text,"text");Objects.requireNonNull(origin,"origin");}
    }

    /** All occurrences of a non-null key, in effective precedence then source order; never null, empty when absent. */
    public List<Value> values(String key) {
        Objects.requireNonNull(key,"key");return entries.stream().filter(e->e.key().equals(key)).map(Entry::value).toList();
    }

    /** Empty immutable metadata. */
    public static MetaAttributeSet empty() {return new MetaAttributeSet(Map.of());}
}

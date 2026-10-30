package guru.interlis.compiler;

import guru.interlis.model.Origin;
import guru.interlis.model.QualifiedName;
import guru.interlis.source.*;
import java.util.*;

/** Immutable semantic configuration, applied before checking the requested closure.
 * List order is retained in provenance and fingerprints. Higher priorities override
 * lower priorities; conflicting values at the same priority are diagnostics.
 * No entry loads sources or executes conversions. */
public record SemanticOverlays(List<SemanticOverlay> overlays) {
    public SemanticOverlays { overlays=List.copyOf(Objects.requireNonNull(overlays,"overlays")); }
    public static SemanticOverlays empty() {return new SemanticOverlays(List.of());}

    /** Stable external configuration identity, never a caller-supplied content hash.
     * The optional range belongs to the configuration document, not the model text. */
    public record OverlaySource(String id,int priority,Optional<SourceRange> range) {
        public OverlaySource {Objects.requireNonNull(id,"id");if(id.isBlank())throw new IllegalArgumentException("blank overlay id");Objects.requireNonNull(range,"range");}
        public OverlaySource(String id,int priority) {this(id,priority,Optional.empty());}
        public Origin.OverlayOrigin origin() {return new Origin.OverlayOrigin(id,priority,range);}
    }
    /** Canonical declaration name, optionally disambiguated by exact owning model
     * version and source. No namespace alias or latest-version fallback is used. */
    public record Target(QualifiedName name,Optional<String> modelVersion,Optional<SourceId> sourceHint) {
        public Target {Objects.requireNonNull(name,"name");Objects.requireNonNull(modelVersion,"modelVersion");Objects.requireNonNull(sourceHint,"sourceHint");}
        public static Target named(String name) {return new Target(new QualifiedName(List.of(name.split("\\.",-1))),Optional.empty(),Optional.empty());}
    }
    public sealed interface SemanticOverlay permits SemanticOverlay.TranslationOverlay,SemanticOverlay.CrsOverlay,SemanticOverlay.MetaAttributeOverlay {
        OverlaySource source();
        Target target();
        String kind();
        default String fingerprint() {return digest(List.of(this));}
        /** Adds a structural translation dependency; it does not import the original namespace.
         * Both targets must be models. Conflicting explicit TRANSLATION OF is rejected. */
        record TranslationOverlay(OverlaySource source,Target target,Target original) implements SemanticOverlay {
            public TranslationOverlay {Objects.requireNonNull(source,"source");Objects.requireNonNull(target,"target");Objects.requireNonNull(original,"original");}
            public String kind(){return "translation";}
        }
        /** Declares a coordinate reference identifier on a coordinate domain/attribute.
         * Coordinates are not transformed and the identifier is not resolved over IO. */
        record CrsOverlay(OverlaySource source,Target target,String crs) implements SemanticOverlay {
            public CrsOverlay {Objects.requireNonNull(source,"source");Objects.requireNonNull(target,"target");Objects.requireNonNull(crs,"crs");if(crs.isBlank())throw new IllegalArgumentException("blank CRS");}
            public String kind(){return "crs";}
        }
        /** Ordered values for each metadata key. Keys are copied in lexical order;
         * repeated values retain order. CRS uses the same coordinate-target check. */
        record MetaAttributeOverlay(OverlaySource source,Target target,Map<String,List<String>> attributes) implements SemanticOverlay {
            public MetaAttributeOverlay {
                Objects.requireNonNull(source,"source");Objects.requireNonNull(target,"target");Objects.requireNonNull(attributes,"attributes");
                var copy=new LinkedHashMap<String,List<String>>();
                var supplied=attributes;
                supplied.keySet().stream().sorted().forEach(key->{if(key.isBlank())throw new IllegalArgumentException("blank metadata key");var values=List.copyOf(supplied.get(key));if(values.isEmpty())throw new IllegalArgumentException("empty metadata values");copy.put(key,values);});
                attributes=Collections.unmodifiableMap(copy);
            }
            public String kind(){return "meta-attribute";}
        }
    }
    /** SHA-256 of a versioned, length-prefixed UTF-16 content encoding, including
     * all values, target qualifiers, order and external provenance. */
    public String fingerprint(){return digest(overlays);}
    private static String digest(List<SemanticOverlay> overlays) {
        var b=new StringBuilder("semantic-overlays/v2");field(b,Integer.toString(overlays.size()));
        for(var o:overlays) {
            field(b,o.kind());field(b,o.source().id());field(b,Integer.toString(o.source().priority()));
            field(b,Boolean.toString(o.source().range().isPresent()));
            o.source().range().ifPresent(r->{field(b,r.source().value());field(b,Long.toString(r.revision().value()));field(b,Integer.toString(r.startOffset()));field(b,Integer.toString(r.endOffset()));});
            target(b,o.target());
            switch(o) {
                case SemanticOverlay.TranslationOverlay t->target(b,t.original());
                case SemanticOverlay.CrsOverlay c->field(b,c.crs());
                case SemanticOverlay.MetaAttributeOverlay m->{field(b,Integer.toString(m.attributes().size()));m.attributes().forEach((k,v)->{field(b,k);field(b,Integer.toString(v.size()));v.forEach(x->field(b,x));});}
            }
        }
        return Hex.encode(Sha256.digestUtf16(b.toString()));
    }
    private static void target(StringBuilder b,Target t) {field(b,Integer.toString(t.name().segments().size()));t.name().segments().forEach(s->field(b,s));field(b,Boolean.toString(t.modelVersion().isPresent()));t.modelVersion().ifPresent(s->field(b,s));field(b,Boolean.toString(t.sourceHint().isPresent()));t.sourceHint().ifPresent(s->field(b,s.value()));}
    private static void field(StringBuilder b,String value){b.append(value.length()).append(':').append(value);}
}

package guru.interlis.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Bound view. Lists preserve language order; references belong to the containing snapshot. */
public record ViewSymbol(SymbolHeader header, Optional<SymbolId> baseView,
                         Optional<Formation> formation, List<SymbolId> bases,
                         List<BaseExtension> baseExtensions, List<ExpressionId> selections, List<SymbolId> members,
                         List<SymbolId> constraints) implements Symbol {
    public ViewSymbol {
        Objects.requireNonNull(header); Objects.requireNonNull(baseView); Objects.requireNonNull(formation);
        bases = List.copyOf(bases); baseExtensions = List.copyOf(baseExtensions); selections = List.copyOf(selections);
        members = List.copyOf(members); constraints = List.copyOf(constraints);
        if (header.kind() != SymbolKind.VIEW) throw new IllegalArgumentException("VIEW header required");
    }
    /** Closed formation variants. Base IDs refer to Base symbols, not inheritance. */
    public sealed interface Formation permits Projection, Join, Union, Aggregation, Inspection {}
    public record Projection(SymbolId base) implements Formation {
        public Projection { Objects.requireNonNull(base); }
    }
    public record Join(List<SymbolId> bases) implements Formation {
        public Join { bases = List.copyOf(bases); }
    }
    public record Union(List<SymbolId> bases) implements Formation {
        public Union { bases = List.copyOf(bases); }
    }
    public record Aggregation(SymbolId base, List<ExpressionId> equal) implements Formation {
        public Aggregation { Objects.requireNonNull(base); equal = List.copyOf(equal); }
    }
    public record Inspection(SymbolId base, ExpressionId path, SymbolId element, SymbolId parent, boolean area) implements Formation {
        public Inspection { Objects.requireNonNull(base); Objects.requireNonNull(path); Objects.requireNonNull(element); Objects.requireNonNull(parent); }
    }
    public record BaseExtension(SymbolId base, List<SymbolId> extensions) {
        public BaseExtension { Objects.requireNonNull(base); extensions = List.copyOf(extensions); }
    }
    /** Local basis name, with its own navigable identity and bound target. */
    public record Base(SymbolHeader header, SymbolId target, boolean orNull) implements Symbol {
        public Base { Objects.requireNonNull(header); Objects.requireNonNull(target);
            if (header.kind() != SymbolKind.VIEW_BASE) throw new IllegalArgumentException("VIEW_BASE header required"); }
    }
}

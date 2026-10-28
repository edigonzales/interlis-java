package guru.interlis.model;
import guru.interlis.source.SourceRange;
import java.util.*;
/** Ordered conditional parameter assignments; no drawing or value evaluation is performed.
 * Values and references belong to the same snapshot. Empty targets identify erroneous partial binding.
 * An override retains its base rule; consumers evaluate base assignments before local clauses.
 * signatureClass is the bound OF class. It is not a single computed responsible
 * class: selected objects and used parameter owners can differ across alternatives.
 * Their bound identities and original conditional/enum order remain in clauses. */
public record DrawingRuleSymbol(SymbolHeader header, Optional<SymbolId> signatureClass, Optional<SymbolId> overrides,
        List<Clause> clauses) implements Symbol {
    public DrawingRuleSymbol { Objects.requireNonNull(header); Objects.requireNonNull(signatureClass); Objects.requireNonNull(overrides); clauses=List.copyOf(clauses); }
    public record Clause(SourceRange range, Optional<ExpressionId> condition, List<Assignment> assignments) {
        public Clause { Objects.requireNonNull(range); Objects.requireNonNull(condition); assignments=List.copyOf(assignments); }
    }
    public record Assignment(SourceRange range, Optional<SymbolId> parameter, Value value) {
        public Assignment { Objects.requireNonNull(range); Objects.requireNonNull(parameter); Objects.requireNonNull(value); }
    }
    public sealed interface Value permits ExpressionValue, MetaObjectValue, AccordingValue {}
    public record ExpressionValue(ExpressionId expression) implements Value { public ExpressionValue { Objects.requireNonNull(expression); } }
    public record MetaObjectValue(Optional<SymbolId> object) implements Value { public MetaObjectValue { Objects.requireNonNull(object); } }
    public record AccordingValue(ExpressionId selector, List<EnumAssignment> cases) implements Value {
        public AccordingValue { Objects.requireNonNull(selector); cases=List.copyOf(cases); }
    }
    /** Enumerators are effective enum paths within the selector's resolved type; bounds retain their syntax ranges. */
    public record EnumAssignment(SourceRange range, String first, Optional<String> last, Value value) {
        public EnumAssignment { Objects.requireNonNull(range); Objects.requireNonNull(first); Objects.requireNonNull(last); Objects.requireNonNull(value); }
    }
}

package guru.interlis.validation;

import guru.interlis.model.AssignmentContext;
import guru.interlis.model.AttributePathType;
import guru.interlis.model.ClassValueType;
import guru.interlis.model.QualifiedName;
import guru.interlis.model.RelationDecision;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeRef;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.TransferValue;

import java.util.List;
import java.util.Optional;

final class ModelValueRules {
    private ModelValueRules() { }

    enum Result { CHECKED, INVALID, UNKNOWN }

    static Result check(ValidationPlan plan, TypeDescriptor type, TransferValue value) {
        if (type instanceof ClassValueType classType && value instanceof TransferValue.ClassName name) {
            var found = descriptor(plan, name.name());
            if (found.isEmpty()) {
                return Result.UNKNOWN;
            }
            var object = found.orElseThrow();
            var expected = classType.structureValue() ? SymbolKind.STRUCTURE : SymbolKind.CLASS;
            return object.kind() == expected && specializes(plan, object, classType.restrictions())
                    ? Result.CHECKED : Result.INVALID;
        }
        if (type instanceof AttributePathType attributeType && value instanceof TransferValue.AttributePath path) {
            return attribute(plan, attributeType, path.name());
        }
        return Result.UNKNOWN;
    }

    private static Result attribute(ValidationPlan plan, AttributePathType type, QualifiedName name) {
        var segments = name.segments();
        if (segments.size() < 2) {
            return Result.INVALID;
        }
        var owner = descriptor(plan, new QualifiedName(segments.subList(0, segments.size() - 1)));
        if (owner.isEmpty()) {
            return Result.UNKNOWN;
        }
        var object = owner.orElseThrow();
        if (type.owner().isPresent()) {
            if (!(type.owner().orElseThrow() instanceof AttributePathType.Viewable viewable)) {
                return Result.UNKNOWN;
            }
            if (!specializes(plan, object, List.of(viewable.target()))) {
                return Result.INVALID;
            }
        }
        String fieldName = segments.getLast();
        var slot = object.slots().stream().filter(AttributeSlot.class::isInstance).map(AttributeSlot.class::cast)
                .filter(attribute -> attribute.attribute().name().value().equals(fieldName)
                        || attribute.name().transfer().segments().getLast().equals(fieldName))
                .findFirst();
        if (slot.isEmpty()) {
            return Result.UNKNOWN;
        }
        if (type.restrictions().isEmpty()) {
            return Result.CHECKED;
        }
        boolean unresolved = false;
        for (var restriction : type.restrictions()) {
            var relation = plan.typeRelations().canAssign(slot.orElseThrow().effectiveType(),
                    new TypeRef(plan.snapshotId(), restriction), AssignmentContext.NORMAL).known();
            if (relation.isPresent() && relation.orElseThrow().decision() == RelationDecision.ALLOWED) {
                return Result.CHECKED;
            }
            unresolved |= relation.isEmpty();
        }
        return unresolved ? Result.UNKNOWN : Result.INVALID;
    }

    private static Optional<ObjectDescriptor> descriptor(ValidationPlan plan, QualifiedName name) {
        var symbols = plan.symbols(name);
        if (symbols.size() == 1) {
            return plan.transferPlan().object(plan.ref(symbols.getFirst()));
        }
        var matches = plan.transferPlan().objects().stream().filter(object -> object.name().transfer().equals(name)).toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    static boolean specializes(ValidationPlan plan, ObjectDescriptor type, List<SymbolId> roots) {
        if (roots.isEmpty()) {
            return true;
        }
        var current = type;
        for (;;) {
            if (roots.contains(current.symbol().symbol()) || roots.contains(current.name().canonical().symbol())) {
                return true;
            }
            if (current.baseType().isEmpty()) {
                return false;
            }
            current = plan.transferPlan().object(current.baseType().orElseThrow()).orElseThrow();
        }
    }
}

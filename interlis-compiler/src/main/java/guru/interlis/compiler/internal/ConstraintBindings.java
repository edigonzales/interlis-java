package guru.interlis.compiler.internal;

import guru.interlis.compiler.internal.ExpressionBindings.ExpressionResult;
import guru.interlis.model.AssociationSymbol;
import guru.interlis.model.ClassSymbol;
import guru.interlis.model.CoordinateType;
import guru.interlis.model.FormattedType;
import guru.interlis.model.GeometryType;
import guru.interlis.model.StructureValueType;
import guru.interlis.model.CollectionType;
import guru.interlis.model.ConstraintDefinition;
import guru.interlis.model.ConstraintSymbol;
import guru.interlis.model.DomainConstraint;
import guru.interlis.model.EnumerationType;
import guru.interlis.model.EnumerationValue;
import guru.interlis.model.ExpressionId;
import guru.interlis.model.MandatoryConstraint;
import guru.interlis.model.NumericRange;
import guru.interlis.model.NumericType;
import guru.interlis.model.PlausibilityConstraint;
import guru.interlis.model.ReferenceUsage;
import guru.interlis.model.StructureSymbol;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.TextType;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeId;
import guru.interlis.model.TypedExpression;
import guru.interlis.model.FunctionSymbol;
import guru.interlis.model.UniquenessConstraint;
import guru.interlis.model.ViewSymbol;
import guru.interlis.source.SourceRange;
import guru.interlis.syntax.ConstraintDeclarationSyntax;
import guru.interlis.syntax.ExternalConstraintSyntax;
import guru.interlis.syntax.ExpressionSyntax;
import guru.interlis.syntax.PathExpressionSyntax;
import guru.interlis.syntax.TopicSyntax;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import guru.interlis.compiler.internal.ExpressionBindings.ExpressionResult;

/** Binds typed constraint definitions in their actual object context. */
final class ConstraintBindings {
    private final GraphBuilder g;
    private final SemanticBuildState state;
    private final SemanticWork work;
    private final IndexedDeclarations index;
    private final SemanticDiagnostics diagnostics;
    private final Map<SymbolId, SymbolId> externalConstraintTargets = new LinkedHashMap<>();

    ConstraintBindings(GraphBuilder graph, SemanticBuildState state, SemanticWork work,
                   IndexedDeclarations index, SemanticDiagnostics diagnostics) {
        this.g = graph;
        this.state = state;
        this.work = work;
        this.index = index;
        this.diagnostics = diagnostics;
    }

    /** Named constraints share the declaring viewable's local namespace, including
     * same-topic CONSTRAINTS OF blocks. Inherited-topic contexts remain distinct. */
    void validateConstraintNames() {
        record ConstraintScope(SymbolId target, SymbolId topic) {}
        Map<ConstraintScope, Set<String>> names = new LinkedHashMap<>();
        var declarations = index.all().stream()
                .filter(d -> d.kind() == SymbolKind.CONSTRAINT && d.language() == guru.interlis.model.LanguageVersion.ILI24)
                .sorted(Comparator.comparing((DeclInfo d) -> d.sourceId().value())
                        .thenComparingInt(d -> d.syntax().range().startOffset())).toList();
        for (var declaration : declarations) {
            work.checkpoint();
            var syntax = declaration.syntax() instanceof ExternalConstraintSyntax external
                    ? external.constraint() : (ConstraintDeclarationSyntax) declaration.syntax();
            if (syntax.name().isEmpty()) continue;
            SymbolId target = externalConstraintTargets.getOrDefault(declaration.id(), declaration.owner().orElse(null));
            var symbol = state.symbol(target);
            if (!(symbol instanceof ClassSymbol || symbol instanceof StructureSymbol
                    || symbol instanceof AssociationSymbol || symbol instanceof ViewSymbol)) continue;
            var scope = new ConstraintScope(target, g.paths.scopeRoot(declaration.id()));
            if (!names.computeIfAbsent(scope, ignored -> new LinkedHashSet<>()).add(syntax.name().orElseThrow()))
                diagnostics.emitRule(declaration, "constraint names must be unique within their declaring class",
                        "CONSTRAINT_NAME", declaration.nameRange().orElse(syntax.range()));
        }
    }

    ConstraintSymbol buildConstraint(DeclInfo decl) {
        ConstraintDeclarationSyntax syntax;
        Optional<SymbolId> externalTarget = Optional.empty();
        if (decl.syntax() instanceof ExternalConstraintSyntax external) {
            syntax = external.constraint();
            externalTarget = g.names.tryResolve(decl, external.target(),
                    Set.of(SymbolKind.CLASS, SymbolKind.ASSOCIATION));
            if (externalTarget.isEmpty() && external.target().segments().size() == 1) {
                externalTarget = g.inheritance.baseTopicMember(decl.id(), external.target().text(),
                        Set.of(SymbolKind.CLASS, SymbolKind.ASSOCIATION));
            }
            if (externalTarget.isEmpty()) {
                diagnostics.unresolved(external.target());
            } else {
                SymbolId target = externalTarget.orElseThrow();
                g.references.recordReference(decl, external.target(), target, ReferenceUsage.EXPRESSION);
                boolean inherited = g.inheritance.baseTopicMember(decl.id(),
                        index.byId(target).orElseThrow().name(),
                        Set.of(SymbolKind.CLASS, SymbolKind.ASSOCIATION)).filter(target::equals).isPresent();
                if (!Objects.equals(g.paths.scopeRoot(decl.id()), g.paths.scopeRoot(target)) && !inherited) {
                    diagnostics.emitRule(decl, "external constraints must be declared in the target's topic",
                            "CONSTRAINT_TARGET_TOPIC");
                }
            }
        } else {
            syntax = (ConstraintDeclarationSyntax) decl.syntax();
        }
        externalTarget.ifPresent(target -> externalConstraintTargets.put(decl.id(), target));
        SymbolId context = g.paths.contextualObject(g.expressions.expressionContext(decl.id()), g.paths.scopeRoot(decl.id()));
        if (syntax.existence().isPresent()) {
            var existence = syntax.existence().orElseThrow();
            ExpressionResult source = constraintPath(existence.source(), decl, context, false);
            requireConstraintValue(decl, source, false);
            List<guru.interlis.model.ExistenceConstraint.ExistenceTarget> targets = new ArrayList<>();
            for (var target : existence.targets()) {
                work.checkpoint();
                var resolved = g.resolver.resolve(decl.id(), target.viewable(),
                        Set.of(SymbolKind.CLASS, SymbolKind.ASSOCIATION, SymbolKind.VIEW), false).target();
                if (resolved.isEmpty() && target.viewable().segments().size() == 1)
                    resolved = g.inheritance.baseTopicMember(decl.id(), target.viewable().text(),
                            Set.of(SymbolKind.CLASS, SymbolKind.ASSOCIATION, SymbolKind.VIEW));
                g.references.recordReference(decl, target.viewable(), resolved.orElse(null), ReferenceUsage.EXPRESSION);
                if (resolved.isEmpty()) { diagnostics.unresolved(target.viewable()); continue; }
                SymbolId targetScope = resolved.orElseThrow();
                SymbolId ownerTopic = g.paths.scopeRoot(decl.id());
                SymbolId targetTopic = g.paths.scopeRoot(targetScope);
                boolean depends = ownerTopic == null || targetTopic == null
                        || index.byId(ownerTopic).map(d -> d.kind() != SymbolKind.TOPIC).orElse(true)
                        || index.byId(targetTopic).map(d -> d.kind() != SymbolKind.TOPIC).orElse(true)
                        || g.paths.topicAvailable(ownerTopic, targetTopic)
                        || g.inheritance.baseChain(ownerTopic).stream().map(index::byId).flatMap(Optional::stream)
                        .filter(d -> d.syntax() instanceof TopicSyntax)
                        .anyMatch(d -> ((TopicSyntax) d.syntax()).dependencies().stream()
                                .anyMatch(n -> g.resolver.resolve(d.id(), n, Set.of(SymbolKind.TOPIC)).target()
                                        .filter(targetTopic::equals).isPresent()));
                if (!depends) diagnostics.emitRule(decl, "EXISTENCE target topic requires DEPENDS ON", "EXISTENCE_DEPENDS");
                ExpressionResult path = constraintPath(target.path(), decl, targetScope, false);
                requireConstraintValue(decl, path, false);
                if (!source.error() && !path.error() && !existenceCompatible(source.typeId(), path.typeId())) {
                    TypeDescriptor from = g.types.effectiveDescriptor(source.typeId()), to = g.types.effectiveDescriptor(path.typeId());
                    boolean familyPending = from.kind() == to.kind() && switch (from.kind()) {
                        case GENERIC, DEFERRED, PENDING -> true;
                        default -> false;
                    };
                    if (familyPending) diagnostics.emitUnsupported(decl.sourceId(), target.path().range(),
                            "EXISTENCE_TYPE_COMPATIBILITY", "EXISTENCE compatibility for " + from.kind());
                    else g.emitTypeMismatch(decl.sourceId(), target.path().range(), g.types.typeName(source.typeId()), g.types.typeName(path.typeId()));
                }
                targets.add(new guru.interlis.model.ExistenceConstraint.ExistenceTarget(targetScope, path.id()));
            }
            return new ConstraintSymbol(g.declarations.header(decl), new guru.interlis.model.ExistenceConstraint(
                    syntax.name(), source.id(), targets), externalTarget);
        }
        if (syntax.constraintKind() == guru.interlis.syntax.ConstraintKind.SET
                && decl.owner().flatMap(index::byId).filter(d -> d.kind() == SymbolKind.STRUCTURE).isPresent())
            diagnostics.emitRule(decl, "SET CONSTRAINT is not allowed in a structure", "SET_IN_STRUCTURE");
        if (syntax.constraintKind() == guru.interlis.syntax.ConstraintKind.UNIQUENESS) {
            Optional<ExpressionId> where = syntax.expression().map(e -> {
                ExpressionResult condition = g.expressions.buildExpression(e, decl.id());
                if (!condition.error() && !g.types.isBoolean(condition.typeId()))
                    g.emitTypeMismatch(decl.sourceId(), e.range(), g.types.typeName(condition.typeId()), "BOOLEAN");
                return condition.id();
            });
            List<ExpressionId> keys = new ArrayList<>();
            List<UniquenessConstraint.LocalUniqueness> localKeys = new ArrayList<>();
            if (syntax.localPrefix().isPresent()) {
                if (syntax.basket()) diagnostics.emitRule(decl, "UNIQUE cannot combine BASKET and LOCAL", "UNIQUE_KEY");
                ExpressionResult prefix = constraintPath(syntax.localPrefix().orElseThrow(), decl, context, true);
                SymbolId structure = g.structures.target(g.types.effectiveDescriptor(prefix.typeId()));
                if (!prefix.error() && structure == null)
                    diagnostics.emitRule(decl, "LOCAL prefix must select a structure attribute", "UNIQUE_LOCAL_PREFIX");
                for (var key : syntax.uniqueKeys()) {
                    ExpressionResult field = constraintPath(key, decl, structure, false);
                    requireConstraintValue(decl, field, true);
                    keys.add(field.id());
                }
                localKeys.add(new UniquenessConstraint.LocalUniqueness(prefix.id(), keys));
                keys = List.of();
            } else {
                if (context != null && index.byId(context).filter(d -> d.kind() == SymbolKind.STRUCTURE).isPresent())
                    diagnostics.emitRule(decl, "global UNIQUE requires identifiable objects", "UNIQUE_STRUCTURE");
                for (var key : syntax.uniqueKeys()) {
                    ExpressionResult path = constraintPath(key, decl, context, false);
                    requireConstraintValue(decl, path, true);
                    keys.add(path.id());
                }
            }
            return new ConstraintSymbol(g.declarations.header(decl), new UniquenessConstraint(syntax.name(),
                    syntax.basket(), where, keys, localKeys), externalTarget);
        }
        Optional<ExpressionId> setWhere=syntax.where().map(e->{
            var condition=g.expressions.buildExpression(e,decl.id());
            if(!condition.error() && !g.types.isBoolean(condition.typeId()))g.emitTypeMismatch(decl.sourceId(),e.range(),g.types.typeName(condition.typeId()),"BOOLEAN");
            return condition.id();
        });
        if(syntax.constraintKind()==guru.interlis.syntax.ConstraintKind.PLAUSIBILITY && syntax.plausibility().isEmpty()) {
            var error=missingConstraintExpression(decl,syntax.range());
            return new ConstraintSymbol(g.declarations.header(decl),new MandatoryConstraint(syntax.name(),error.id()),externalTarget);
        }
        SymbolId expressionOwner = decl.id();
        ExpressionResult expression = syntax.expression()
                .map(e -> g.expressions.buildExpression(e, expressionOwner))
                .orElseGet(() -> missingConstraintExpression(decl,syntax.range()));
        if (!expression.error() && !g.types.isBoolean(expression.typeId())) {
            g.emitTypeMismatch(decl.sourceId(), syntax.range(), g.types.typeName(expression.typeId()),
                    "BOOLEAN");
        }
        if (!expression.error() && g.types.isBoolean(expression.typeId()) && syntax.constraintKind()==guru.interlis.syntax.ConstraintKind.SET
                && decl.language()==guru.interlis.model.LanguageVersion.ILI24 && !hasObjectSetFunction(expression.id())) {
            var cause=g.emitTypeMismatch(decl.sourceId(),state.expression(expression.id()).range(),
                    "SET condition without OBJECTS function","SET condition with OBJECTS function");
            expression=g.expressions.failedFunctionArgument(state.expression(expression.id()).range(),List.of(cause));
        }
        ConstraintDefinition definition = switch (syntax.constraintKind()) {
            case MANDATORY -> new MandatoryConstraint(syntax.name(), expression.id());
            case PLAUSIBILITY -> new PlausibilityConstraint(syntax.name(),
                    syntax.plausibility().orElseThrow().percentage().exactValue(), syntax.plausibility().orElseThrow().comparison(),
                    expression.id());
            case DOMAIN -> new DomainConstraint(expression.id());
            case SET -> new guru.interlis.model.SetConstraint(syntax.name(),
                    syntax.basket()?guru.interlis.model.SetConstraint.SetConstraintScope.BASKET: guru.interlis.model.SetConstraint.SetConstraintScope.GLOBAL, setWhere,
                    expression.id());
            default -> new MandatoryConstraint(syntax.name(), expression.id());
        };
        return new ConstraintSymbol(g.declarations.header(decl), definition, externalTarget);
    }

    /** 2.4 requires an actual call with an OBJECTS formal somewhere in the set
     * condition; an ALL value or such a call only in WHERE does not suffice. */
    private boolean hasObjectSetFunction(ExpressionId root) {
        var pending=new java.util.ArrayDeque<ExpressionId>();var seen=new java.util.HashSet<ExpressionId>();pending.add(root);
        while(!pending.isEmpty()) {
            work.checkpoint();var id=pending.removeFirst();if(!seen.add(id))continue;var expression=state.expression(id);
            if(expression instanceof guru.interlis.model.FunctionCallExpression call
                    && call.targetFunction().map(state::symbol).filter(FunctionSymbol.class::isInstance).map(FunctionSymbol.class::cast)
                    .stream().flatMap(f->f.parameters().stream()).map(p->g.types.effectiveDescriptor(p.typeId()))
                    .anyMatch(t->t instanceof guru.interlis.model.ObjectArgumentType objects&&objects.objects()))return true;
            pending.addAll(expression.children());
        }
        return false;
    }

    private ExpressionResult constraintPath(ExpressionSyntax syntax, DeclInfo owner,
            SymbolId start, boolean localPrefix) {
        if (syntax instanceof PathExpressionSyntax path)
            return g.paths.pathExpression(state.nextExpressionId(), path, owner.id(), start, localPrefix);
        return g.expressions.buildExpression(syntax, owner.id()); // Unsupported operators keep their explicit diagnostic.
    }

    private void requireConstraintValue(DeclInfo owner, ExpressionResult result, boolean unique) {
        if (result.error()) return;
        TypedExpression expression = state.expression(result.id());
        TypeDescriptor type = g.types.effectiveDescriptor(result.typeId());
        boolean invalid = unique && type instanceof CollectionType || type instanceof guru.interlis.model.ObjectArgumentType o && o.objects();
        if (unique) invalid |= type instanceof StructureValueType;
        else invalid |= !(expression instanceof guru.interlis.model.PathExpression p
                && (p.path().steps().getLast() instanceof guru.interlis.model.PathStep.AttributeStep
                || p.path().steps().getLast() instanceof guru.interlis.model.PathStep.IndexStep));
        if (invalid) diagnostics.emitRule(owner, unique ? "UNIQUE key must be a singular non-structure value"
                : "EXISTENCE path must select one attribute value", unique ? "UNIQUE_KEY" : "EXISTENCE_PATH");
    }

    private boolean existenceCompatible(TypeId source, TypeId target) {
        TypeDescriptor actual = g.types.effectiveDescriptor(source);
        TypeDescriptor expected = g.types.effectiveDescriptor(target);
        if (actual instanceof CollectionType a && expected instanceof CollectionType b)
            return a.collectionKind() == b.collectionKind() && existenceCompatible(a.elementType(), b.elementType());
        if (actual instanceof CollectionType a && !g.paths.isMany(a.cardinality())) actual = g.types.effectiveDescriptor(a.elementType());
        if (expected instanceof CollectionType b && !g.paths.isMany(b.cardinality())) expected = g.types.effectiveDescriptor(b.elementType());
        if (actual == null || expected == null) return false;
        if (actual instanceof guru.interlis.model.OidType a && expected instanceof guru.interlis.model.OidType b)
            return a.oidKind() == guru.interlis.model.OidKind.ANY || b.oidKind() == guru.interlis.model.OidKind.ANY
                    || a.oidKind() == b.oidKind() && (a.valueType().isEmpty() && b.valueType().isEmpty()
                    || a.valueType().isPresent() && b.valueType().isPresent()
                    && existenceCompatible(a.valueType().orElseThrow(), b.valueType().orElseThrow()));
        if (actual.id().equals(expected.id())) return true;
        if (actual instanceof GeometryType geometry && expected instanceof CoordinateType) {
            actual = geometry.vertexDomain().map(state::declaredType).map(g.types::effectiveDescriptor).orElse(null);
        }
        if (actual instanceof NumericType a && expected instanceof NumericType b)
            return numericValueCompatible(a.range(), b.range());
        if (actual instanceof TextType && expected instanceof TextType) return true;
        if (actual instanceof CoordinateType a && expected instanceof CoordinateType b) {
            if (a.dimensions() != b.dimensions() || a.multiCoordinate() != b.multiCoordinate()
                    || !a.referenceSystem().equals(b.referenceSystem())) return false;
            for (int i = 0; i < a.dimensions(); i++)
                if (!numericValueCompatible(a.axes().get(i), b.axes().get(i))) return false;
            return true;
        }
        if (actual instanceof guru.interlis.model.AlignmentType a && expected instanceof guru.interlis.model.AlignmentType b)
            return a.alignment() == b.alignment();
        if (actual instanceof guru.interlis.model.BlackboxType a && expected instanceof guru.interlis.model.BlackboxType b)
            return a.blackboxKind() == b.blackboxKind();
        if (actual instanceof FormattedType a && expected instanceof FormattedType b) {
            if (a.predefined().isPresent() || b.predefined().isPresent()) return a.predefined().equals(b.predefined());
            return a.structure().equals(b.structure()) && a.parts().stream().map(p -> List.of(p.attribute(), p.width(), p.format(), p.literal())).toList()
                    .equals(b.parts().stream().map(p -> List.of(p.attribute(), p.width(), p.format(), p.literal())).toList());
        }
        if (actual instanceof EnumerationType a && expected instanceof EnumerationType b)
            return guru.interlis.model.internal.TypeCompatibility.relatedEnumerations(a, b, g.types::effectiveDescriptor) || a.ordered() == b.ordered() && a.circular() == b.circular()
                    && a.values().stream().map(EnumerationValue::path).toList()
                    .equals(b.values().stream().map(EnumerationValue::path).toList());
        return guru.interlis.model.internal.TypeCompatibility.assign(actual, expected,
                g.types::effectiveDescriptor, state::baseOf, domain -> g.types.effectiveDescriptor(state.declaredType(domain))).decision() == guru.interlis.model.RelationDecision.ALLOWED;
    }

    private boolean numericValueCompatible(NumericRange a, NumericRange b) {
        return a.unit().equals(b.unit()) && a.referenceSystem().equals(b.referenceSystem())
                && a.direction() == b.direction() && a.circular() == b.circular();
    }

    /** A missing parsed condition is a source recovery error, never an implementation gap. */
    private ExpressionResult missingConstraintExpression(DeclInfo owner,SourceRange range) {
        var causes=diagnostics.syntaxErrors().stream().filter(d->d.primaryRange().filter(r->r.source().equals(range.source())
                && r.revision().equals(range.revision()) && r.startOffset()<=range.endOffset() && r.endOffset()>=range.startOffset()).isPresent())
                .map(guru.interlis.diagnostics.Diagnostic::id).toList();
        if(causes.isEmpty())causes=List.of(diagnostics.emitRule(owner,"constraint syntax is incomplete","SOURCE_MISSING_CONSTRAINT",range));
        return g.expressions.failedValueExpression(state.nextExpressionId(),range,causes);
    }

    SymbolId externalConstraintTarget(SymbolId constraint, SymbolId fallback) {
        return externalConstraintTargets.getOrDefault(constraint, fallback);
    }
}

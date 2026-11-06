package guru.interlis.compiler.internal;

import guru.interlis.compiler.SourceSet;
import guru.interlis.compiler.internal.ExpressionBindings.ExpressionResult;
import guru.interlis.diagnostics.DiagnosticArgument;
import guru.interlis.diagnostics.DiagnosticCode;
import guru.interlis.diagnostics.DiagnosticDraft;
import guru.interlis.diagnostics.DiagnosticId;
import guru.interlis.diagnostics.Phase;
import guru.interlis.diagnostics.Severity;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.Cardinality;
import guru.interlis.model.ClassSymbol;
import guru.interlis.model.CollectionType;
import guru.interlis.model.CoordinateType;
import guru.interlis.model.ErrorType;
import guru.interlis.model.ExpressionId;
import guru.interlis.model.GeometryType;
import guru.interlis.model.NumericType;
import guru.interlis.model.Origin;
import guru.interlis.model.ReferenceType;
import guru.interlis.model.ReferenceUsage;
import guru.interlis.model.RoleSymbol;
import guru.interlis.model.StructureSymbol;
import guru.interlis.model.StructureValueType;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeId;
import guru.interlis.model.TypedExpression;
import guru.interlis.model.ViewSymbol;
import guru.interlis.source.SourceRange;
import guru.interlis.syntax.AssociationDeclarationSyntax;
import guru.interlis.syntax.ConstraintDeclarationSyntax;
import guru.interlis.syntax.ExpressionSyntax;
import guru.interlis.syntax.ExternalConstraintSyntax;
import guru.interlis.syntax.PathExpressionSyntax;
import guru.interlis.syntax.TopicSyntax;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/** Binds object paths with explicit value, inspection and function contexts. */
final class ObjectPathBindings {
    private final SemanticGraphRead state;
    private final SemanticWork work;
    private final IndexedDeclarations index;
    private final SemanticDiagnostics diagnostics;
    private final NameResolver resolver;
    private final SourceSet sourceSet;
    private final CancellationToken cancellation;
    private final Supplier<ExpressionBindings> expressions;
    private final Supplier<ViewBuilder> views;
    private final Supplier<TypeBindings> typeBindings;
    private final InheritanceBindings inheritance;
    private final ReferenceBindings references;
    private final StructureBindings structures;
    private final Function<SymbolId, SymbolId> graphicContext;
    private final Function<DeclInfo, Symbol> ensureSymbol;

    ObjectPathBindings(SemanticGraphRead state,
                       SemanticWork work,
                       IndexedDeclarations index,
                       SemanticDiagnostics diagnostics,
                       NameResolver resolver,
                       SourceSet sourceSet,
                       CancellationToken cancellation,
                       Supplier<ExpressionBindings> expressions,
                       Supplier<ViewBuilder> views,
                       Supplier<TypeBindings> typeBindings,
                       InheritanceBindings inheritance,
                       ReferenceBindings references,
                       StructureBindings structures,
                       Function<SymbolId, SymbolId> graphicContext,
                       Function<DeclInfo, Symbol> ensureSymbol) {
        this.state = state;
        this.work = work;
        this.index = index;
        this.diagnostics = diagnostics;
        this.resolver = resolver;
        this.sourceSet = sourceSet;
        this.cancellation = cancellation;
        this.expressions = expressions;
        this.views = views;
        this.typeBindings = typeBindings;
        this.inheritance = inheritance;
        this.references = references;
        this.structures = structures;
        this.graphicContext = graphicContext;
        this.ensureSymbol = ensureSymbol;
    }

    ExpressionResult pathExpression(ExpressionId id, PathExpressionSyntax syntax,
                                            SymbolId owner) {
        if (isSetCondition(owner, syntax.range())) {
            work.checkpoint();
            var name=syntax.segments().getFirst().name();
            var diagnostic=diagnostics.emit(DiagnosticDraft.of(new DiagnosticCode("ILI-SEM-0008"),Severity.ERROR,
                            Phase.TYPE_CHECKING,"diagnostic.semantics.path")
                    .at(name.range()).argument(new DiagnosticArgument.IdentifierArgument("segment",name.text()))
                    .argument(new DiagnosticArgument.TextArgument("reason","SET condition has no individual object context; object values belong in WHERE")));
            return expressions.get().failedValueExpression(id,syntax.range(),List.of(diagnostic));
        }
        return pathExpression(id, syntax, owner, expressions.get().expressionContext(owner), false);
    }

    /** The source-bound main expression distinguishes SET's set context from
     * its individual WHERE context, including nested calls and external blocks.
     * Explicit class/attribute constants and separately bound function string
     * paths do not enter this ordinary object-value path entry point. */
    private boolean isSetCondition(SymbolId owner,SourceRange range) {
        var declaration=index.byId(owner).orElseThrow().syntax();
        var constraint=declaration instanceof ExternalConstraintSyntax external?external.constraint()
                :declaration instanceof ConstraintDeclarationSyntax local?local:null;
        if(constraint==null||constraint.constraintKind()!=guru.interlis.syntax.ConstraintKind.SET)return false;
        return constraint.expression().map(ExpressionSyntax::range).filter(body->body.source().equals(range.source())
                &&body.revision().equals(range.revision())&&body.startOffset()<=range.startOffset()&&body.endOffset()>=range.endOffset()).isPresent();
    }

    ExpressionResult pathExpression(ExpressionId id, PathExpressionSyntax syntax,
            SymbolId owner, SymbolId start, boolean localPrefix) {
        return pathExpression(id, syntax, owner, start, localPrefix, PathContext.VALUE);
    }

    guru.interlis.syntax.FragmentSnapshot parsePathLiteral(SourceRange range, guru.interlis.model.LanguageVersion language) {
        var fragment = new guru.interlis.compiler.internal.antlr.AntlrSyntaxService().parsePathLiteral(
                sourceSet.sources().get(range.source()), range, language, cancellation);
        diagnostics.importAll(fragment.diagnostics());
        return fragment;
    }

    ExpressionResult valuePath(PathExpressionSyntax syntax, SymbolId owner, SymbolId start) {
        return pathExpression(expressions.get().nextExpressionId(), syntax, owner, start, false, PathContext.FUNCTION_PATH);
    }

    ExpressionResult inspectionPath(PathExpressionSyntax syntax, SymbolId owner, SymbolId start) {
        return pathExpression(expressions.get().nextExpressionId(), syntax, owner, start, false, PathContext.INSPECTION);
    }

    ExpressionResult pathExpression(ExpressionId id, PathExpressionSyntax syntax,
            SymbolId owner, SymbolId start, boolean localPrefix, PathContext pathContext) {
        boolean inspection=pathContext==PathContext.INSPECTION;
        List<guru.interlis.model.PathStep> steps = new ArrayList<>();
        SymbolId currentScope = start;
        SymbolId contextTopic = scopeRoot(graphicContext.apply(owner));
        currentScope = contextualObject(currentScope, contextTopic);
        TypeId currentType = null;
        boolean error = false;
        for (int i = 0; i < syntax.segments().size(); i++) {
            work.checkpoint();
            var segment = syntax.segments().get(i);
            guru.interlis.syntax.NameSyntax name = segment.name();
            String text = name.text();
            if (Set.of("PARENT", "THISAREA", "THATAREA").contains(text)) {
                var inspected = views.get().inspection(expressions.get().expressionContext(owner));
                boolean valid = inspected != null && (text.equals("PARENT") != inspected.area())
                        && segment.index().isEmpty() && !segment.associationAccess() && segment.associationQualifier().isEmpty()
                        && (i == 0 || java.util.Objects.equals(currentScope, inspected.element())
                            || java.util.Objects.equals(currentScope, expressions.get().expressionContext(owner)));
                if (!valid) {
                    var diagnostic = diagnostics.emitRule(index.byId(owner).orElseThrow(), "invalid inspection path context", "VIEW_INSPECTION_CONTEXT");
                    currentType = typeBindings.get().nextTypeId();
                    typeBindings.get().defineType(new ErrorType(currentType, new Origin.DeclaredOrigin(name.range()), List.of(diagnostic)));
                    steps.add(new guru.interlis.model.PathStep.UnresolvedStep(text, name.range()));
                    error = true; break;
                }
                currentScope = inspected.parent(); currentType = objectPathType(currentScope, name.range());
                steps.add(new guru.interlis.model.PathStep.InspectionStep(guru.interlis.model.PathStep.InspectionKind.valueOf(text),
                        expressions.get().expressionContext(owner), currentScope, name.range()));
                continue;
            }
            if ("THIS".equals(text) && segment.index().isEmpty() && !segment.associationAccess() && (currentScope != null || currentType != null
                    && typeBindings.get().effectiveDescriptor(currentType) instanceof guru.interlis.model.ObjectArgumentType)) {
                steps.add(new guru.interlis.model.PathStep.ThisStep(name.range()));
                currentType = i == 0 ? state.declaredType(currentScope) : currentType;
                if (currentType == null) {
                    currentType = objectPathType(currentScope, name.range());
                }
                TypeDescriptor descriptor = typeBindings.get().effectiveDescriptor(currentType);
                if (!(descriptor instanceof ReferenceType)
                        && !(descriptor instanceof StructureValueType)
                        && !(descriptor instanceof guru.interlis.model.ObjectArgumentType)) {
                    currentScope = null;
                }
                continue;
            }
            boolean incomingMany = currentType != null && typeBindings.get().effectiveDescriptor(currentType)
                    instanceof guru.interlis.model.ObjectArgumentType set && set.objects();
            Optional<SymbolId> member = currentScope == null || segment.associationAccess() || segment.associationQualifier().isPresent()
                    ? Optional.empty() : findMember(currentScope, text);
            // Projections retain role access to their source object, including
            // roles which ALL OF does not copy as transfer attributes.
            if (i == 0 && member.isEmpty()) {
                var projection = views.get().projectionBase(currentScope);
                if (projection != null) {
                    currentScope = projection.target();
                    steps.add(new guru.interlis.model.PathStep.ViewBaseStep(projection.id(), currentScope, name.range()));
                    if (!segment.associationAccess() && segment.associationQualifier().isEmpty())
                        member = findMember(currentScope, text).filter(candidate ->
                                index.byId(candidate).map(d -> d.kind() == SymbolKind.ROLE).orElse(false));
                }
            }
            List<SymbolId> objectScopes = currentScope == null ? List.of() : List.of(currentScope);
            if (currentType != null && typeBindings.get().effectiveDescriptor(currentType) instanceof guru.interlis.model.ObjectArgumentType objects) {
                if (!objects.restrictions().isEmpty()) objectScopes = objects.restrictions();
                else if (objects.base().isPresent()) objectScopes = List.of(objects.base().orElseThrow());
            }
            if (!localPrefix && segment.index().isEmpty() && member.isEmpty() && !objectScopes.isEmpty()) {
                boolean outgoing = segment.associationQualifier().isPresent();
                List<RoleAccess> accesses = associationAccesses(objectScopes, contextTopic, name, i == 0, outgoing);
                if (!outgoing && !segment.associationAccess() && accesses.isEmpty()) {
                    accesses = associationAccesses(objectScopes, contextTopic, name, i == 0, true);
                }
                if (segment.associationQualifier().isPresent()) {
                    var qualifier = segment.associationQualifier().orElseThrow();
                    var target = resolver.resolve(owner, qualifier, Set.of(SymbolKind.ASSOCIATION)).target();
                    if (target.isEmpty()) target = inheritance.baseTopicMember(owner, qualifier.text(), Set.of(SymbolKind.ASSOCIATION));
                    references.recordReference(index.byId(owner).orElseThrow(), qualifier, target.orElse(null), ReferenceUsage.EXPRESSION);
                    if (target.isEmpty()) diagnostics.unresolved(qualifier);
                    SymbolId selected = target.orElse(null);
                    accesses = accesses.stream().filter(a -> a.association().equals(selected)).toList();
                }
                if (accesses.size() == 1) {
                    RoleAccess access = accesses.getFirst();
                    boolean associationObject = segment.associationAccess() || access.target().isEmpty();
                    currentScope = associationObject ? access.association()
                            : commonRoleScope(access.target().orElseThrow(), contextTopic);
                    Cardinality cardinality = accessCardinality(access, associationObject);
                    boolean many = incomingMany || isMany(cardinality);
                    currentType = typeBindings.get().nextTypeId();
                    typeBindings.get().defineType(associationObject
                            ? new guru.interlis.model.ObjectArgumentType(currentType, new Origin.DeclaredOrigin(segment.range()),
                                    Optional.of(currentScope), List.of(), many)
                            : rolePathType(currentType, access.target().orElseThrow(), contextTopic, segment.range(), many));
                    if (access.target().isEmpty()) {
                        steps.add(new guru.interlis.model.PathStep.AssociationSourceStep(text, access.association(),
                                access.source().id(), cardinality, currentType, segment.range()));
                    } else {
                        steps.add(new guru.interlis.model.PathStep.AssociationAccessStep(text,
                                access.association(), access.source().id(), access.target().orElseThrow().id(),
                                associationObject, cardinality, currentType, segment.range()));
                    }
                    references.recordReference(index.byId(owner).orElseThrow(), name,
                            access.target().orElse(access.source()).id(), ReferenceUsage.EXPRESSION);
                    continue;
                } else if (accesses.size() > 1) {
                    var diagnostic = diagnostics.emit(DiagnosticDraft.of(new DiagnosticCode("ILI-SEM-0002"),
                                    Severity.ERROR, Phase.BINDING, "diagnostic.semantics.ambiguous")
                            .at(name.range()).argument(new DiagnosticArgument.IdentifierArgument("name", text))
                            .argument(new DiagnosticArgument.IdentifierListArgument("candidates",
                                    accesses.stream().map(a -> a.association().value() + ":" + a.source().id().value()).toList())));
                    currentType = typeBindings.get().nextTypeId();
                    typeBindings.get().defineType(new ErrorType(currentType, new Origin.DeclaredOrigin(segment.range()), List.of(diagnostic)));
                    steps.add(new guru.interlis.model.PathStep.UnresolvedStep(text, segment.range()));
                    error = true;
                    break;
                }
            }
            if (member.isEmpty()) {
                DiagnosticId diagnostic = diagnostics.unresolved(name);
                steps.add(new guru.interlis.model.PathStep.UnresolvedStep(text, name.range()));
                TypeId errorType = typeBindings.get().nextTypeId();
                typeBindings.get().defineType(new ErrorType(errorType,
                        new Origin.DeclaredOrigin(name.range()), List.of(diagnostic)));
                currentType = errorType;
                error = true;
                break;
            }
            var referenceTarget = state.symbol(member.orElseThrow());
            references.recordReference(index.byId(owner).orElseThrow(), name,
                    referenceTarget instanceof ViewSymbol.Base basis && basis.nameRange().isEmpty() ? basis.target() : member.orElseThrow(),
                    ReferenceUsage.EXPRESSION);
            SymbolKind kind = index.byId(member.get()).map(DeclInfo::kind).orElse(SymbolKind.ATTRIBUTE);
            if ((localPrefix || segment.index().isPresent()) && kind != SymbolKind.ATTRIBUTE) {
                DiagnosticId diagnostic = diagnostics.emitTypeMismatch(expressions.get().ownerSource(owner), name.range(), "role", "structure attribute");
                currentType = typeBindings.get().nextTypeId();
                typeBindings.get().defineType(new ErrorType(currentType, new Origin.DeclaredOrigin(name.range()), List.of(diagnostic)));
                error = true;
                break;
            }
            if (kind == SymbolKind.VIEW_BASE) {
                ViewSymbol.Base base = (ViewSymbol.Base) ensureSymbol.apply(index.byId(member.get()).orElseThrow());
                steps.add(new guru.interlis.model.PathStep.ViewBaseStep(base.id(), views.get().basisTarget(base), name.range()));
                currentScope = views.get().basisTarget(base); currentType = objectPathType(currentScope, name.range());
            } else if (kind == SymbolKind.ROLE) {
                steps.add(new guru.interlis.model.PathStep.RoleStep(text, member.get(),
                        name.range()));
                // A role declared on this association selects one participant of
                // the association instance. Its relationship cardinality does
                // not turn that participant into an object set.
                RoleSymbol role = (RoleSymbol) ensureSymbol.apply(index.byId(member.get()).orElseThrow());
                currentScope = commonRoleScope(role, contextTopic);
                currentType = typeBindings.get().nextTypeId();
                typeBindings.get().defineType(rolePathType(currentType, role, contextTopic, name.range(), incomingMany));
            } else {
                steps.add(new guru.interlis.model.PathStep.AttributeStep(text, member.get(),
                        name.range()));
                currentType = state.declaredType(member.get());
                if (currentType == null) currentType = typeBindings.get().attributeType(index.byId(member.get()).orElseThrow());
                TypeDescriptor descriptor = currentType == null ? null
                        : typeBindings.get().effectiveDescriptor(currentType);
                currentScope = null;
                if (segment.index().isPresent()) {
                    var selection = segment.index().orElseThrow();
                    TypeId selected = incomingMany ? null : indexedType(descriptor, selection, segment.range());
                    if (selected == null) {
                        DiagnosticId diagnostic = diagnostics.emitTypeMismatch(expressions.get().ownerSource(owner), segment.range(),
                                typeBindings.get().typeName(currentType), "valid LIST position or coordinate axis");
                        currentType = typeBindings.get().nextTypeId();
                        typeBindings.get().defineType(new ErrorType(currentType, new Origin.DeclaredOrigin(segment.range()), List.of(diagnostic)));
                        error = true;
                        break;
                    }
                    currentType = selected;
                    descriptor = typeBindings.get().effectiveDescriptor(currentType);
                    steps.add(new guru.interlis.model.PathStep.IndexStep(selection, currentType, segment.range()));
                }
                if (localPrefix) {
                    currentScope = structures.target(descriptor);
                    if (currentScope == null) {
                        DiagnosticId diagnostic = diagnostics.emitTypeMismatch(expressions.get().ownerSource(owner), name.range(),
                                typeBindings.get().typeName(currentType), "structure attribute");
                        currentType = typeBindings.get().nextTypeId();
                        typeBindings.get().defineType(new ErrorType(currentType, new Origin.DeclaredOrigin(name.range()), List.of(diagnostic)));
                        error = true;
                        break;
                    }
                } else if (descriptor instanceof CollectionType collection && !incomingMany
                        && (pathContext!=PathContext.VALUE || !isMany(collection.cardinality())) && i + 1 < syntax.segments().size()) {
                    currentScope = structures.target(collection);
                } else if (descriptor instanceof ReferenceType reference) {
                    List<SymbolId> roots = reference.targetRoots().stream().map(t -> contextualObject(t, contextTopic)).toList();
                    currentScope = commonObjectScope(roots);
                    currentType = typeBindings.get().nextTypeId();
                    if (incomingMany) {
                        typeBindings.get().defineType(new guru.interlis.model.ObjectArgumentType(currentType,
                                new Origin.DeclaredOrigin(name.range()), roots.size() == 1 ? Optional.of(roots.getFirst()) : Optional.empty(),
                                roots.size() <= 1 ? List.of() : roots, true));
                    } else {
                        typeBindings.get().defineType(new ReferenceType(currentType, new Origin.DeclaredOrigin(name.range()),
                                reference.target().map(t -> contextualObject(t, contextTopic)),
                                reference.restrictions().stream().map(t -> contextualObject(t, contextTopic)).toList(), reference.external()));
                    }
                } else if (inspection && descriptor instanceof GeometryType geometry && i + 1 < syntax.segments().size()) {
                    currentScope = views.get().geometryElement(index.byId(owner).orElseThrow(), geometry, false);
                } else if (!incomingMany && descriptor instanceof StructureValueType structure) {
                    currentScope = structures.target(structure);
                } else if (!incomingMany && descriptor instanceof guru.interlis.model.ObjectArgumentType object) {
                    // Derived view values can retain a participant object type.
                    // Continue at its actual roots just as for a bound role.
                    var roots = object.restrictions().isEmpty() ? object.base().stream().toList() : object.restrictions();
                    currentScope = commonObjectScope(roots.stream().map(t -> contextualObject(t, contextTopic)).toList());
                } else if (incomingMany) {
                    DiagnosticId diagnostic = diagnostics.emitTypeMismatch(expressions.get().ownerSource(owner), name.range(),
                            "multiple attribute values", "single attribute value");
                    currentType = typeBindings.get().nextTypeId();
                    typeBindings.get().defineType(new ErrorType(currentType, new Origin.DeclaredOrigin(name.range()), List.of(diagnostic)));
                    error = true;
                    break;
                }
            }
        }
        if (currentType == null) {
            currentType = typeBindings.get().errorType(expressions.get().ownerSource(owner), syntax.range(), "PATH");
            error = true;
        }
        guru.interlis.model.ObjectPath path = new guru.interlis.model.ObjectPath(steps.isEmpty()
                ? List.of(new guru.interlis.model.PathStep.ThisStep(syntax.range())) : steps);
        TypedExpression expression = new guru.interlis.model.PathExpression(id, currentType,
                error ? guru.interlis.model.ExpressionTypeState.ERROR
                        : guru.interlis.model.ExpressionTypeState.RESOLVED,
                syntax.range(), List.of(), path);
        expressions.get().defineExpression(expression);
        return new ExpressionResult(id, currentType, error);
    }

    private TypeId indexedType(TypeDescriptor type, guru.interlis.model.PathIndex index,
            guru.interlis.source.SourceRange range) {
        var position = index.position();
        if (position.filter(p -> p.signum() <= 0).isPresent()) return null;
        if (type instanceof CollectionType collection && collection.collectionKind() == guru.interlis.model.CollectionKind.LIST) {
            if (collection.cardinality().upper() instanceof guru.interlis.model.CardinalityUpper.Finite maximum
                    && (maximum.value().signum() == 0 || position.filter(p -> p.compareTo(maximum.value()) > 0).isPresent()))
                return null;
            return collection.elementType();
        }
        if (type instanceof CoordinateType coordinate && !coordinate.multiCoordinate() && position.isPresent()
                && position.orElseThrow().compareTo(java.math.BigInteger.valueOf(coordinate.dimensions())) <= 0) {
            TypeId result = typeBindings.get().nextTypeId();
            typeBindings.get().defineType(new NumericType(result, new Origin.DeclaredOrigin(range),
                    coordinate.axes().get(position.orElseThrow().intValueExact() - 1)));
            return result;
        }
        return null;
    }

    boolean isMany(Cardinality cardinality) {
        return !(cardinality.upper() instanceof guru.interlis.model.CardinalityUpper.Finite finite)
                || finite.value().compareTo(java.math.BigInteger.ONE) > 0;
    }

    private Cardinality accessCardinality(RoleAccess access, boolean associationObject) {
        var roles = inheritance.associationRoles(access.association());
        // A role bound only for one participant does not constrain the remaining
        // combinations in an n-ary relation. Never infer a singleton from it.
        if (roles.size() != 2) return Cardinality.unboundedFrom(0);
        RoleSymbol target = access.target().orElseGet(() -> roles.stream()
                .filter(r -> !r.id().equals(access.source().id())).findFirst().orElseThrow());
        Cardinality cardinality = target.cardinality();
        if (associationObject) {
            Cardinality links = Cardinality.exactly(1);
            for (SymbolId base : inheritance.baseChain(access.association())) {
                var syntax = (AssociationDeclarationSyntax) index.byId(base).orElseThrow().syntax();
                if (syntax.cardinality().isPresent()) { links = syntax.cardinality().orElseThrow(); break; }
            }
            cardinality = multiplyCardinality(cardinality, links);
        }
        return cardinality;
    }

    private Cardinality multiplyCardinality(Cardinality left, Cardinality right) {
        var upper = left.upper() instanceof guru.interlis.model.CardinalityUpper.Finite l
                && right.upper() instanceof guru.interlis.model.CardinalityUpper.Finite r
                ? new guru.interlis.model.CardinalityUpper.Finite(l.value().multiply(r.value()))
                : guru.interlis.model.CardinalityUpper.unbounded();
        return new Cardinality(left.lower().multiply(right.lower()), upper);
    }

    private List<RoleAccess> associationAccesses(List<SymbolId> fromTargets, SymbolId contextTopic,
            guru.interlis.syntax.NameSyntax name, boolean first, boolean outgoing) {
        List<RoleAccess> result = new ArrayList<>();
        for (DeclInfo association : index.all()) {
            work.checkpoint();
            if (association.kind() != SymbolKind.ASSOCIATION) continue;
            SymbolId topic = scopeRoot(association.id());
            // Traversing a role or projecting an object retains the role namespace
            // of that reached object, in addition to the declaring context.
            if (!topicAvailable(contextTopic, topic)
                    && !fromTargets.stream().allMatch(from -> topicAvailable(scopeRoot(from), topic))) continue;
            // A declaration appearing later cannot retroactively add an access to a constraint.
            if (association.sourceId().equals(name.range().source())
                    && association.syntax().range().startOffset() > name.range().startOffset()) continue;
            List<RoleSymbol> roles = inheritance.associationRoles(association.id());
            for (RoleSymbol target : roles) {
                work.checkpoint();
                if (!outgoing && !target.name().value().equals(name.text())) continue;
                for (RoleSymbol source : roles) {
                    work.checkpoint();
                    if (outgoing ? !source.id().equals(target.id()) : source.id().equals(target.id())) continue;
                    if (outgoing && !source.name().value().equals(name.text())) continue;
                    if (!fromTargets.stream().allMatch(from -> inheritance.roleAccepts(source, from))) continue;
                    if (first) {
                        if (source.external()) continue;
                        if (!fromTargets.stream().allMatch(from -> {
                            boolean originalAccess = source.targetRoots().stream()
                                    .anyMatch(t -> inheritance.targetSpecializes(from, t) && Objects.equals(scopeRoot(source.id()), scopeRoot(t)));
                            boolean extendedHere = Objects.equals(topic, scopeRoot(from))
                                    && index.byId(from).map(d -> d.modifiers().contains(guru.interlis.model.Modifier.EXTENDED)).orElse(false);
                            return originalAccess || extendedHere;
                        })) continue;
                    }
                    result.add(new RoleAccess(association.id(), source, outgoing ? Optional.empty() : Optional.of(target)));
                }
            }
        }
        List<RoleAccess> candidates = List.copyOf(result);
        result.removeIf(candidate -> candidates.stream().anyMatch(other -> {
            if (candidate == other) return false;
            boolean derived = inheritance.baseChain(other.association()).contains(candidate.association());
            boolean base = inheritance.baseChain(candidate.association()).contains(other.association());
            boolean localExtension = index.byId(other.association()).orElseThrow().modifiers()
                    .contains(guru.interlis.model.Modifier.EXTENDED);
            boolean candidateExtension = index.byId(candidate.association()).orElseThrow().modifiers()
                    .contains(guru.interlis.model.Modifier.EXTENDED);
            return derived && (other.source().hiding() || localExtension)
                    || base && !candidate.source().hiding() && !candidateExtension;
        }));
        return result;
    }

    boolean topicAvailable(SymbolId context, SymbolId candidate) {
        Set<SymbolId> seen = new HashSet<>();
        for (SymbolId current = context; current != null && seen.add(current);) {
            work.checkpoint();
            if (current.equals(candidate)) return true;
            DeclInfo declaration = index.byId(current).orElse(null);
            if (declaration == null || !(declaration.syntax() instanceof TopicSyntax topic)
                    || topic.baseTopic().isEmpty()) return false;
            current = resolver.resolve(current, topic.baseTopic().orElseThrow(), Set.of(SymbolKind.TOPIC))
                    .target().orElse(null);
        }
        return false;
    }

    SymbolId contextualObject(SymbolId target, SymbolId topic) {
        if (target == null) return null;
        Set<SymbolId> seen = new HashSet<>();
        for (SymbolId context = topic; context != null && seen.add(context); context = state.baseOf(context)) {
            for (DeclInfo candidate : index.children(context)) {
                work.checkpoint();
                if ((candidate.kind() == SymbolKind.CLASS || candidate.kind() == SymbolKind.ASSOCIATION)
                        && candidate.modifiers().contains(guru.interlis.model.Modifier.EXTENDED)
                        && guru.interlis.model.internal.TypeCompatibility.specializes(candidate.id(), target, state::baseOf)) {
                    return candidate.id();
                }
            }
        }
        return target;
    }

    private SymbolId commonRoleScope(RoleSymbol role, SymbolId topic) {
        List<SymbolId> roots = role.targetRoots().stream().map(t -> contextualObject(t, topic)).toList();
        return commonObjectScope(roots);
    }

    SymbolId commonObjectScope(List<SymbolId> roots) {
        if (roots.isEmpty()) return null;
        // Only a common hierarchy admits a subsequent statically bound member.
        for (SymbolId candidate : inheritance.baseChain(roots.getFirst())) {
            if (roots.stream().allMatch(t -> inheritance.targetSpecializes(t, candidate))) return candidate;
        }
        return null;
    }

    private guru.interlis.model.ObjectArgumentType rolePathType(TypeId id, RoleSymbol role,
            SymbolId topic, SourceRange range, boolean many) {
        List<SymbolId> roots = role.targetRoots().stream().map(t -> contextualObject(t, topic)).toList();
        boolean any = roots.stream().anyMatch(t -> inheritance.objectBase(t).isEmpty());
        return new guru.interlis.model.ObjectArgumentType(id, new Origin.DeclaredOrigin(range),
                !any && roots.size() == 1 ? Optional.of(roots.getFirst()) : Optional.empty(),
                any || roots.size() == 1 ? List.of() : roots, many);
    }

    /** Same participant-value type as an ordinary role path, for implicit 2.4
     * ALL OF values. One basis row denotes one link, not all its peer links. */
    TypeId roleValueType(RoleSymbol role,SymbolId owner,SourceRange range) {
        TypeId id=typeBindings.get().nextTypeId();typeBindings.get().defineType(rolePathType(id,role,scopeRoot(owner),range,false));return id;
    }

    TypeId objectPathType(SymbolId target, SourceRange range) {
        TypeId type = typeBindings.get().nextTypeId();
        if (inheritance.objectBase(target).isEmpty()) {
            typeBindings.get().defineType(new guru.interlis.model.ObjectArgumentType(type, new Origin.DeclaredOrigin(range),
                    Optional.empty(), List.of(), false));
        } else if (index.byId(target).map(DeclInfo::kind).orElse(null) == SymbolKind.STRUCTURE) {
            typeBindings.get().defineType(new StructureValueType(type, new Origin.DeclaredOrigin(range), Optional.of(target), List.of()));
        } else {
            typeBindings.get().defineType(new ReferenceType(type, new Origin.DeclaredOrigin(range), Optional.of(target), List.of(), false));
        }
        return type;
    }

    Optional<SymbolId> findMember(SymbolId owner, String name) {
        SymbolId current = owner;
        Set<SymbolId> seen = new HashSet<>();
        while (current != null && seen.add(current)) {
            work.checkpoint();
            for (DeclInfo child : index.children(current)) {
                work.checkpoint();
                if ((child.kind() == SymbolKind.ATTRIBUTE || child.kind() == SymbolKind.ROLE || child.kind() == SymbolKind.VIEW_BASE)
                        && child.name().equals(name)) {
                    return Optional.of(child.id());
                }
            }
            Symbol predefinedOwner = state.symbol(current);
            if (predefinedOwner != null && predefinedOwner.origin() instanceof Origin.PredefinedOrigin) {
                var members = predefinedOwner instanceof StructureSymbol s ? s.members() : predefinedOwner instanceof ClassSymbol c ? c.members() : List.<SymbolId>of();
                for (SymbolId member : members) if (state.symbol(member).kind() == SymbolKind.ATTRIBUTE && state.symbol(member).name().value().equals(name)) return Optional.of(member);
            }
            var viewMember = views.get().member(current, name);
            if (viewMember.isPresent()) return viewMember;
            current = inheritance.objectInheritanceBase(current).orElse(null);
        }
        return Optional.empty();
    }

    SymbolId scopeRoot(SymbolId symbol) {
        SymbolId current = symbol;
        Set<SymbolId> seen = new HashSet<>();
        while (current != null && seen.add(current)) {
            work.checkpoint();
            DeclInfo info = index.byId(current).orElse(null);
            if (info == null) {
                return null;
            }
            if (info.kind() == SymbolKind.TOPIC || info.kind() == SymbolKind.MODEL) {
                return current;
            }
            current = info.owner().orElse(null);
        }
        return null;
    }

    private record RoleAccess(SymbolId association, RoleSymbol source, Optional<RoleSymbol> target) { }

    private enum PathContext { VALUE, INSPECTION, FUNCTION_PATH }
}

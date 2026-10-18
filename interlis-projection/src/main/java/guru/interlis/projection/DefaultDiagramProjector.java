package guru.interlis.projection;

import guru.interlis.compiler.AnalysisSnapshot;
import guru.interlis.diagnostics.Severity;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.AssociationSymbol;
import guru.interlis.model.AttributeSymbol;
import guru.interlis.model.Cardinality;
import guru.interlis.model.CardinalityUpper;
import guru.interlis.model.ClassSymbol;
import guru.interlis.model.CompiledSchema;
import guru.interlis.model.ConstraintSymbol;
import guru.interlis.model.DomainReferenceType;
import guru.interlis.model.DomainSymbol;
import guru.interlis.model.EffectiveMember;
import guru.interlis.model.EffectiveMemberOrigin;
import guru.interlis.model.ModelSymbol;
import guru.interlis.model.RoleSymbol;
import guru.interlis.model.StructureSymbol;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TopicSymbol;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeId;
import guru.interlis.model.query.QueryResult;
import guru.interlis.model.query.SemanticQueries;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRange;
import guru.interlis.source.SourceRevision;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Default renderer-independent projection. */
final class DefaultDiagramProjector implements DiagramProjector {
    @Override
    public DiagramProjectionOutcome project(DiagramInput input, DiagramOptions options,
                                            CancellationToken token) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(token, "token");
        Context context = context(input);
        List<ProjectionNotice> notices = new ArrayList<>();
        for (SymbolRef ref : options.selection().symbols()) {
            if (!ref.snapshot().equals(context.snapshotId())) {
                return new DiagramProjectionOutcome.Rejected(List.of(new ProjectionNotice(
                        ProjectionNoticeCode.STALE_SELECTION, Severity.ERROR,
                        "projection.staleSelection", List.of(ref.snapshot().value()),
                        Optional.of(ref), Optional.empty())));
            }
            if (context.graph().symbol(ref.symbol()).isEmpty()) {
                return new DiagramProjectionOutcome.Rejected(List.of(new ProjectionNotice(
                        ProjectionNoticeCode.STALE_SELECTION, Severity.ERROR,
                        "projection.staleSelection", List.of(ref.symbol().value()),
                        Optional.of(ref), Optional.empty())));
            }
        }
        ProjectionState state = new ProjectionState(context, options, notices);
        for (SymbolRef ref : options.selection().symbols()) {
            token.throwIfCancellationRequested();
            state.include(ref.symbol());
        }
        state.finishEdges();
        ProjectionCompleteness completeness = state.completeness();
        DiagramDocument document = new DiagramDocument(DiagramDocument.SCHEMA_VERSION,
                context.snapshotId(), context.sourceRevisions(), context.inputKind(),
                completeness, options.selection().symbols(), options.fingerprint(),
                state.containers.values().stream().toList(),
                state.nodes.values().stream().toList(),
                state.edges, notices);
        return new DiagramProjectionOutcome.Produced(document, List.copyOf(notices));
    }

    private static Context context(DiagramInput input) {
        return switch (input) {
            case DiagramInput.FromAnalysis analysis -> new Context(analysis.snapshot().snapshotId(),
                    analysis.snapshot().semanticGraph(), analysis.snapshot().queries(),
                    analysis.snapshot().sourceRevisions(), InputKind.ANALYSIS);
            case DiagramInput.FromSchema schema -> new Context(schema.schema().snapshotId(),
                    schema.schema().semanticGraph(), schema.schema().queries(),
                    schema.schema().sourceRevisions(), InputKind.CHECKED_SCHEMA);
        };
    }

    private record Context(guru.interlis.model.SnapshotId snapshotId,
                           guru.interlis.model.SemanticGraph graph, SemanticQueries queries,
                           Map<SourceId, SourceRevision> sourceRevisions, InputKind inputKind) {
    }

    private static final class ProjectionState {
        private final Context context;
        private final DiagramOptions options;
        private final List<ProjectionNotice> notices;
        private final Map<String, DiagramContainer> containers = new LinkedHashMap<>();
        private final Map<String, DiagramNode> nodes = new LinkedHashMap<>();
        private final List<DiagramEdge> edges = new ArrayList<>();
        private final Set<String> edgeKeys = new LinkedHashSet<>();
        private boolean limited;

        private ProjectionState(Context context, DiagramOptions options,
                                List<ProjectionNotice> notices) {
            this.context = context;
            this.options = options;
            this.notices = notices;
        }

        void include(SymbolId id) {
            Symbol symbol = context.graph().symbol(id).orElse(null);
            if (symbol == null) {
                return;
            }
            switch (symbol.kind()) {
                case MODEL, TOPIC -> includeContainer(symbol);
                case CLASS, STRUCTURE, ASSOCIATION, DOMAIN, VIEW -> includeNode(symbol);
                default -> {
                }
            }
        }

        private void includeContainer(Symbol symbol) {
            String containerId = "container-" + symbol.id().value();
            if (containers.containsKey(containerId)) {
                return;
            }
            Optional<SymbolId> owner = symbol.owner();
            Optional<String> parent = owner.map(id -> "container-" + id.value());
            if (parent.isPresent() && !containers.containsKey(parent.get())
                    && owner.isPresent()) {
                Symbol ownerSymbol = context.graph().symbol(owner.get()).orElse(null);
                if (ownerSymbol != null && (ownerSymbol.kind() == SymbolKind.MODEL
                        || ownerSymbol.kind() == SymbolKind.TOPIC)) {
                    includeContainer(ownerSymbol);
                }
            }
            containers.put(containerId, new DiagramContainer(containerId,
                    symbol.kind() == SymbolKind.MODEL ? DiagramContainer.ContainerKind.MODEL
                            : DiagramContainer.ContainerKind.TOPIC,
                    parent.filter(containers::containsKey), symbol.name().value(),
                    qualifiedName(symbol), Optional.of(new SymbolRef(context.snapshotId(), symbol.id())),
                    anchor(symbol)));
            List<SymbolId> members = switch (symbol) {
                case ModelSymbol model -> model.members();
                case TopicSymbol topic -> topic.members();
                default -> List.of();
            };
            for (SymbolId member : members) {
                include(member);
                if (limited) {
                    return;
                }
            }
        }

        private void includeNode(Symbol symbol) {
            if (limited) {
                return;
            }
            String nodeId = "node-" + symbol.id().value();
            if (nodes.containsKey(nodeId)) {
                return;
            }
            Symbol owner = symbol.owner().flatMap(context.graph()::symbol).orElse(null);
            if (owner != null && (owner.kind() == SymbolKind.MODEL
                    || owner.kind() == SymbolKind.TOPIC)) {
                includeContainer(owner);
            } else if (owner != null) {
                includeNode(owner);
            }
            String containerId = owner == null ? "container-model" : "container-" + owner.id().value();
            if (!containers.containsKey(containerId)) {
                containers.put("container-model", new DiagramContainer("container-model",
                        DiagramContainer.ContainerKind.MODEL, Optional.empty(), "Model",
                        "Model", Optional.empty(), Optional.empty()));
                containerId = "container-model";
            }
            DiagramNodeKind kind = nodeKind(symbol);
            if (!options.visibleKinds().contains(kind)) {
                return;
            }
            if (nodes.size() >= options.budget().maxNodes()) {
                limited();
                return;
            }
            List<DiagramCompartment> compartments = compartments(symbol);
            nodes.put(nodeId, new DiagramNode(nodeId, kind, containerId, symbol.name().value(),
                    qualifiedName(symbol), DiagramNodeStatus.RESOLVED,
                    Optional.of(new SymbolRef(context.snapshotId(), symbol.id())),
                    compartments, anchor(symbol)));
        }

        private List<DiagramCompartment> compartments(Symbol symbol) {
            List<DiagramMember> attributes = new ArrayList<>();
            List<DiagramMember> roles = new ArrayList<>();
            List<DiagramMember> constraints = new ArrayList<>();
            switch (symbol) {
                case ClassSymbol clazz -> {
                    addEffectiveMembers(symbol.id(), attributes, roles);
                    addConstraints(clazz.constraints(), constraints);
                }
                case StructureSymbol structure -> {
                    addEffectiveMembers(symbol.id(), attributes, roles);
                    addConstraints(structure.constraints(), constraints);
                }
                case AssociationSymbol association -> {
                    for (SymbolId role : association.roles()) {
                        addRole(role, attributes, roles, false, false);
                    }
                    for (SymbolId attribute : association.attributes()) {
                        addAttribute(attribute, attributes, false, false);
                    }
                    addConstraints(association.constraints(), constraints);
                }
                case DomainSymbol domain -> {
                    // detail compartment carries the declared type label
                }
                default -> {
                }
            }
            if (attributes.size() > options.budget().maxMembersPerNode()
                    || roles.size() > options.budget().maxMembersPerNode()) {
                limited();
            }
            List<DiagramCompartment> result = new ArrayList<>();
            if (!attributes.isEmpty()) {
                result.add(new DiagramCompartment("comp-" + symbol.id().value() + "-attributes",
                        DiagramCompartment.CompartmentKind.ATTRIBUTES, attributes));
            }
            if (!roles.isEmpty()) {
                result.add(new DiagramCompartment("comp-" + symbol.id().value() + "-roles",
                        DiagramCompartment.CompartmentKind.ROLES, roles));
            }
            if (!constraints.isEmpty()) {
                result.add(new DiagramCompartment("comp-" + symbol.id().value() + "-constraints",
                        DiagramCompartment.CompartmentKind.CONSTRAINTS, constraints));
            }
            if (symbol instanceof DomainSymbol domain) {
                result.add(new DiagramCompartment("comp-" + symbol.id().value() + "-details",
                        DiagramCompartment.CompartmentKind.DETAILS,
                        List.of(new DiagramMember("member-" + symbol.id().value() + "-type",
                                DiagramMember.MemberKind.ATTRIBUTE, "declared type",
                                Optional.of(typeLabel(domain.typeId())), Optional.empty(),
                                domain.id(), domain.id(), false, false, anchor(domain)))));
            }
            return result;
        }

        private void addEffectiveMembers(SymbolId viewable, List<DiagramMember> attributes,
                                         List<DiagramMember> roles) {
            QueryResult<List<EffectiveMember>> result = context.queries()
                    .effectiveMembers(new SymbolRef(context.snapshotId(), viewable));
            if (!(result instanceof QueryResult.Known<List<EffectiveMember>> known)) {
                return;
            }
            for (EffectiveMember member : known.value()) {
                Symbol declared = context.graph().symbol(member.declaredSymbol()).orElse(null);
                if (declared == null) {
                    continue;
                }
                boolean inherited = member.origin() != EffectiveMemberOrigin.LOCAL;
                if (inherited && options.inheritedMembers() == InheritedMembers.HIDE) {
                    continue;
                }
                boolean overridden = member.overridden();
                if (declared.kind() == SymbolKind.ROLE) {
                    addRole(member.declaredSymbol(), attributes, roles, inherited, overridden);
                } else if (declared.kind() == SymbolKind.ATTRIBUTE) {
                    addAttribute(member.declaredSymbol(), attributes, inherited, overridden);
                }
            }
        }

        private void addAttribute(SymbolId id, List<DiagramMember> target,
                                  boolean inherited, boolean overridden) {
            if (!(context.graph().symbol(id).orElse(null) instanceof AttributeSymbol attribute)) {
                return;
            }
            if (target.size() >= options.budget().maxMembersPerNode()) {
                limited();
                return;
            }
            target.add(new DiagramMember("member-" + id.value(),
                    DiagramMember.MemberKind.ATTRIBUTE, attribute.name().value(),
                    options.showTypes() ? Optional.of(typeLabel(attribute.declaredType()))
                            : Optional.empty(),
                    Optional.empty(), id, attribute.owner().orElse(id), inherited, overridden,
                    anchor(attribute)));
        }

        private void addRole(SymbolId id, List<DiagramMember> attributes,
                             List<DiagramMember> roles, boolean inherited, boolean overridden) {
            if (!(context.graph().symbol(id).orElse(null) instanceof RoleSymbol role)) {
                return;
            }
            if (roles.size() >= options.budget().maxMembersPerNode()) {
                limited();
                return;
            }
            roles.add(new DiagramMember("member-" + id.value(), DiagramMember.MemberKind.ROLE,
                    role.name().value(), Optional.empty(),
                    options.showRoleCardinalities() ? Optional.of(format(role.cardinality()))
                            : Optional.empty(),
                    id, role.owner().orElse(id), inherited, overridden, anchor(role)));
        }

        private void addConstraints(List<SymbolId> ids, List<DiagramMember> target) {
            if (!options.showConstraints()) {
                return;
            }
            for (SymbolId id : ids) {
                Symbol symbol = context.graph().symbol(id).orElse(null);
                if (symbol instanceof ConstraintSymbol constraint) {
                    target.add(new DiagramMember("member-" + id.value(),
                            DiagramMember.MemberKind.CONSTRAINT, constraint.name().value(),
                            Optional.of(constraint.definition().getClass().getSimpleName()),
                            Optional.empty(), id, constraint.owner().orElse(id), false, false,
                            anchor(constraint)));
                }
            }
        }

        /**
         * Returns the {@code finishEdges} value.
         *
         * @return the value
         */
        void finishEdges() {
            List<DiagramNode> snapshot = List.copyOf(nodes.values());
            for (DiagramNode node : snapshot) {
                if (node.symbol().isEmpty()) {
                    continue;
                }
                Symbol symbol = context.graph().symbol(node.symbol().get().symbol()).orElse(null);
                if (symbol == null) {
                    continue;
                }
                if (symbol instanceof ClassSymbol clazz) {
                    clazz.baseType().ifPresent(base -> addInheritance(node.id(), base));
                } else if (symbol instanceof StructureSymbol structure) {
                    structure.baseType().ifPresent(base -> addInheritance(node.id(), base));
                } else if (symbol instanceof AssociationSymbol association) {
                    for (SymbolId roleId : association.roles()) {
                        Symbol roleSymbol = context.graph().symbol(roleId).orElse(null);
                        if (roleSymbol instanceof RoleSymbol role) {
                            addAssociationRole(node.id(), role);
                        }
                    }
                }
            }
        }

        private void addInheritance(String subtypeNode, SymbolId base) {
            String baseNode = ensureExternal(base, false);
            if (baseNode == null) {
                return;
            }
            addEdge(new DiagramEdge("edge-" + subtypeNode + "-inherits-" + baseNode,
                    DiagramEdge.EdgeKind.INHERITANCE, new DiagramEndpoint(subtypeNode,
                    Optional.empty()), new DiagramEndpoint(baseNode, Optional.empty()),
                    Optional.empty(), emptyEnd(), emptyEnd(), "extends", Optional.empty()));
        }

        private void addAssociationRole(String associationNode, RoleSymbol role) {
            String target = ensureExternal(role.target(), true);
            if (target == null) {
                return;
            }
            DiagramEdgeEnd end = new DiagramEdgeEnd(Optional.of(role.name().value()),
                    Optional.of(role.cardinality()), role.aggregation());
            addEdge(new DiagramEdge("edge-" + associationNode + "-" + role.id().value(),
                    DiagramEdge.EdgeKind.ASSOCIATION_ROLE,
                    new DiagramEndpoint(associationNode, Optional.empty()),
                    new DiagramEndpoint(target, Optional.empty()),
                    Optional.of(new SymbolRef(context.snapshotId(), role.id())),
                    emptyEnd(), end, role.name().value(), anchor(role)));
        }

        private String ensureExternal(SymbolId id, boolean association) {
            String nodeId = "node-" + id.value();
            if (nodes.containsKey(nodeId)) {
                return nodeId;
            }
            Symbol symbol = context.graph().symbol(id).orElse(null);
            if (symbol == null) {
                notices.add(new ProjectionNotice(ProjectionNoticeCode.UNRESOLVED_TARGET,
                        Severity.WARNING, "projection.unresolvedTarget",
                        List.of(id.value()), Optional.empty(), Optional.empty()));
                return null;
            }
            DiagramNodeKind kind = nodeKind(symbol);
            if (!options.visibleKinds().contains(kind)) {
                return null;
            }
            if (nodes.size() >= options.budget().maxNodes()) {
                limited();
                return null;
            }
            String containerId = "container-external";
            containers.putIfAbsent(containerId, new DiagramContainer(containerId,
                    DiagramContainer.ContainerKind.MODEL, Optional.empty(), "External",
                    "External", Optional.empty(), Optional.empty()));
            nodes.put(nodeId, new DiagramNode(nodeId, DiagramNodeKind.EXTERNAL_REFERENCE,
                    containerId, symbol.name().value(), qualifiedName(symbol),
                    DiagramNodeStatus.RESOLVED,
                    Optional.of(new SymbolRef(context.snapshotId(), symbol.id())),
                    List.of(), anchor(symbol)));
            return nodeId;
        }

        private void addEdge(DiagramEdge edge) {
            if (edges.size() >= options.budget().maxEdges()) {
                limited();
                return;
            }
            if (edgeKeys.add(edge.id())) {
                edges.add(edge);
            }
        }

        /**
         * Returns the {@code emptyEnd} value.
         *
         * @return the value
         */
        private static DiagramEdgeEnd emptyEnd() {
            return new DiagramEdgeEnd(Optional.empty(), Optional.empty(),
                    guru.interlis.model.RoleAggregation.NONE);
        }

        /**
         * Returns the {@code limited} value.
         *
         * @return the value
         */
        private void limited() {
            if (!limited) {
                limited = true;
                notices.add(new ProjectionNotice(ProjectionNoticeCode.PROJECTION_LIMIT_REACHED,
                        Severity.WARNING, "projection.limited",
                        List.of(options.budget().toString()), Optional.empty(), Optional.empty()));
            }
        }

        /**
         * Returns the {@code completeness} value.
         *
         * @return the value
         */
        private ProjectionCompleteness completeness() {
            if (limited) {
                return ProjectionCompleteness.LIMITED;
            }
            return notices.isEmpty() ? ProjectionCompleteness.COMPLETE
                    : ProjectionCompleteness.PARTIAL;
        }

        private Optional<SourceAnchor> anchor(Symbol symbol) {
            return symbol.nameRange().map(range -> new SourceAnchor(context.snapshotId(), range,
                    symbol.origin() instanceof guru.interlis.model.Origin.DeclaredOrigin declared
                            ? Optional.of(declared.range()) : Optional.empty()));
        }

        private String qualifiedName(Symbol symbol) {
            List<String> parts = new ArrayList<>();
            Symbol current = symbol;
            int guard = 0;
            while (current != null && guard++ < 64) {
                parts.addFirst(current.name().value());
                current = current.owner().flatMap(context.graph()::symbol).orElse(null);
            }
            return String.join(".", parts);
        }

        private String typeLabel(TypeId typeId) {
            TypeDescriptor descriptor = context.graph().type(typeId).orElse(null);
            if (descriptor == null) {
                return "UNKNOWN";
            }
            return switch (descriptor) {
                case guru.interlis.model.TextType text -> text.textKind()
                        + (text.maxLength().isPresent() ? "*" + text.maxLength().getAsInt() : "");
                case guru.interlis.model.NumericType numeric -> {
                    String min = numeric.range().minimum().map(d -> d.lexeme()).orElse("*");
                    String max = numeric.range().maximum().map(d -> d.lexeme()).orElse("*");
                    yield min + ".." + max;
                }
                case guru.interlis.model.BooleanType ignored -> "BOOLEAN";
                case DomainReferenceType reference -> context.graph().symbol(reference.targetDomain())
                        .map(Symbol::name).map(guru.interlis.model.DeclaredName::value)
                        .orElse("DOMAIN");
                case guru.interlis.model.CollectionType collection -> collection.collectionKind()
                        + " " + format(collection.cardinality()) + " OF "
                        + typeLabel(collection.elementType());
                default -> descriptor.kind().name();
            };
        }

        private static String format(Cardinality cardinality) {
            String upper = cardinality.upper() instanceof CardinalityUpper.Finite finite
                    ? finite.value().toString() : "*";
            return "{" + cardinality.lower() + ".." + upper + "}";
        }

        private static DiagramNodeKind nodeKind(Symbol symbol) {
            return switch (symbol.kind()) {
                case CLASS -> DiagramNodeKind.CLASS;
                case STRUCTURE -> DiagramNodeKind.STRUCTURE;
                case ASSOCIATION -> DiagramNodeKind.ASSOCIATION;
                case VIEW -> DiagramNodeKind.VIEW;
                case DOMAIN -> DiagramNodeKind.DOMAIN;
                default -> DiagramNodeKind.EXTERNAL_REFERENCE;
            };
        }
    }
}

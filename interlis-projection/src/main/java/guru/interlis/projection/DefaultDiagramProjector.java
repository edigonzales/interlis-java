package guru.interlis.projection;

import guru.interlis.compiler.AnalysisSnapshot;
import guru.interlis.diagnostics.Severity;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.AssociationSymbol;
import guru.interlis.model.ViewSymbol;
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
        if(options.scope()==DiagramScope.NEIGHBOURHOOD && options.neighbourhoodDepth().isEmpty()
                || options.labelPolicy()==LabelPolicy.TRANSLATED && options.translationModel().isEmpty())
            return invalidOptions("projection.optionSelectionRequired");
        if(options.translationModel().isPresent()) {
            var ref=options.translationModel().orElseThrow();
            if(!ref.snapshot().equals(context.snapshotId()))return invalidOptions("projection.staleTranslationSelection");
            var symbol=context.graph().symbol(ref.symbol()).orElse(null);
            if(!(symbol instanceof ModelSymbol model) || model.translation().isEmpty()
                    || model.translation().orElseThrow().sourceTarget().isEmpty()
                    || model.translation().orElseThrow().correspondences().isEmpty())return invalidOptions("projection.invalidTranslationSelection");
        }
        if(options.associationPresentation()==AssociationPresentation.COMPACT_BINARY)
            notices.add(new ProjectionNotice(ProjectionNoticeCode.UNSUPPORTED_PRESENTATION,Severity.WARNING,
                    "projection.entityFallback",List.of("COMPACT_BINARY"),Optional.empty(),Optional.empty()));
        ProjectionState state = new ProjectionState(context, options, notices, token);
        for (SymbolRef ref : options.selection().symbols()) {
            token.throwIfCancellationRequested();
            state.include(ref.symbol());
        }
        if(options.scope()==DiagramScope.NEIGHBOURHOOD)state.includeNeighbourhood();
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

    private static DiagramProjectionOutcome invalidOptions(String message) {
        return new DiagramProjectionOutcome.Rejected(List.of(new ProjectionNotice(ProjectionNoticeCode.UNSUPPORTED_PRESENTATION,
                Severity.ERROR,message,List.of(),Optional.empty(),Optional.empty())));
    }

    private static Context context(DiagramInput input) {
        return switch (input) {
            case DiagramInput.FromAnalysis analysis -> new Context(analysis.snapshot().snapshotId(),
                    analysis.snapshot().semanticGraph(), analysis.snapshot().queries(),
                    analysis.snapshot().sourceRevisions(), InputKind.ANALYSIS,
                    !analysis.snapshot().coverage().entries().isEmpty() && analysis.snapshot().coverage().incompleteEntries().isEmpty(), analysis.snapshot().diagnostics());
            case DiagramInput.FromSchema schema -> new Context(schema.schema().snapshotId(),
                    schema.schema().semanticGraph(), schema.schema().queries(),
                    schema.schema().sourceRevisions(), InputKind.CHECKED_SCHEMA, true, List.of());
        };
    }

    private record Context(guru.interlis.model.SnapshotId snapshotId,
                           guru.interlis.model.SemanticGraph graph, SemanticQueries queries,
                           Map<SourceId, SourceRevision> sourceRevisions, InputKind inputKind, boolean complete,
                           List<guru.interlis.diagnostics.Diagnostic> diagnostics) {
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
        private final Set<SymbolId> expandedContainers=new LinkedHashSet<>();
        private final Map<SymbolId,SymbolId> translatedLabels=new LinkedHashMap<>();
        private final CancellationToken token;

        private ProjectionState(Context context, DiagramOptions options,
                                List<ProjectionNotice> notices, CancellationToken token) {
            this.token = token;
            this.context = context;
            this.options = options;
            this.notices = notices;
            options.translationModel().ifPresent(ref -> collectLabels(ref.symbol()));
        }

        private void collectLabels(SymbolId selected) {
            var model=(ModelSymbol)context.graph().symbol(selected).orElseThrow();
            var translation=model.translation().orElseThrow();
            for(var link:translation.correspondences()) {token.throwIfCancellationRequested();translatedLabels.put(link.original(),link.translated());}
            var seen=new LinkedHashSet<SymbolId>();
            SymbolId source=translation.sourceTarget().orElse(null);
            while(source!=null && seen.add(source)) {
                token.throwIfCancellationRequested();
                var original=context.graph().symbol(source).orElse(null);
                if(!(original instanceof ModelSymbol ancestor)||ancestor.translation().isEmpty())break;
                for(var link:ancestor.translation().orElseThrow().correspondences()) {
                    token.throwIfCancellationRequested();
                    if(translatedLabels.containsKey(link.translated()))translatedLabels.put(link.original(),translatedLabels.get(link.translated()));
                }
                source=ancestor.translation().orElseThrow().sourceTarget().orElse(null);
            }
        }
        private String displayName(Symbol symbol) {
            var target=translatedLabels.get(symbol.id());
            return target==null?symbol.name().value():context.graph().symbol(target).orElseThrow().name().value();
        }

        void includeNeighbourhood() {
            Map<SymbolId,Set<SymbolId>> adjacency=new LinkedHashMap<>();
            for(var reference:context.graph().references()) {
                token.throwIfCancellationRequested();
                if(!(reference.resolution() instanceof guru.interlis.model.BindingResolution.BoundTarget bound))continue;
                var scope=context.graph().symbol(new SymbolId(reference.originalScope().value())).orElse(null);
                if(scope==null || reference.usage()==guru.interlis.model.ReferenceUsage.DECLARATION_END
                        || reference.usage()==guru.interlis.model.ReferenceUsage.EXPRESSION && scope.kind()!=SymbolKind.VIEW_BASE
                        || reference.usage()==guru.interlis.model.ReferenceUsage.IMPORT && !options.showImports())continue;
                SymbolId from=projectedOwner(scope),to=projectedTarget(bound.target());
                if(from==null||to==null||from.equals(to))continue;
                adjacency.computeIfAbsent(from,ignored->new LinkedHashSet<>()).add(to);
                adjacency.computeIfAbsent(to,ignored->new LinkedHashSet<>()).add(from);
            }
            Map<SymbolId,Integer> distance=new LinkedHashMap<>();
            for(var ref:options.selection().symbols())distance.put(ref.symbol(),0);
            for(var node:nodes.values())node.symbol().ifPresent(ref->distance.put(ref.symbol(),0));
            var queue=new java.util.ArrayDeque<SymbolId>(distance.keySet());
            while(!queue.isEmpty()&&!limited) {
                token.throwIfCancellationRequested();var current=queue.removeFirst();int depth=distance.get(current);
                if(depth>=options.neighbourhoodDepth().orElseThrow())continue;
                for(var next:adjacency.getOrDefault(current,Set.of())) {
                    token.throwIfCancellationRequested();if(distance.putIfAbsent(next,depth+1)!=null)continue;
                    include(next);queue.addLast(next);
                    // A selected container admits its subtree at the container's
                    // distance; membership itself is not a relationship hop.
                    for(var node:nodes.values()) {
                        token.throwIfCancellationRequested();
                        if(node.symbol().isPresent()&&distance.putIfAbsent(node.symbol().orElseThrow().symbol(),depth+1)==null)
                            queue.addLast(node.symbol().orElseThrow().symbol());
                    }
                    for(var container:expandedContainers) {
                        token.throwIfCancellationRequested();
                        if(distance.putIfAbsent(container,depth+1)==null)queue.addLast(container);
                    }
                }
            }
        }
        private SymbolId projectedTarget(SymbolId id) {
            var target=context.graph().symbol(id).orElse(null);
            return target!=null&&projectable(target.kind())?id:null;
        }
        private SymbolId projectedOwner(Symbol symbol) {
            var seen=new LinkedHashSet<SymbolId>();
            while(symbol!=null && seen.add(symbol.id())) {
                token.throwIfCancellationRequested();if(projectable(symbol.kind()))return symbol.id();
                if(!Set.of(SymbolKind.ATTRIBUTE,SymbolKind.ROLE,SymbolKind.PARAMETER,SymbolKind.VIEW_BASE).contains(symbol.kind()))return null;
                symbol=symbol.owner().flatMap(context.graph()::symbol).orElse(null);
            }return null;
        }
        private boolean projectable(SymbolKind kind) {
            return switch(kind) {case MODEL,TOPIC,CLASS,STRUCTURE,ASSOCIATION,DOMAIN,VIEW,LINE_FORM,METAOBJECT,DRAWING_RULE,GRAPHIC,METADATA_BASKET->true;default->false;};
        }

        void include(SymbolId id) {
            token.throwIfCancellationRequested();
            Symbol symbol = context.graph().symbol(id).orElse(null);
            if (symbol == null) {
                return;
            }
            switch (symbol.kind()) {
                case MODEL, TOPIC -> includeContainer(symbol);
                case CLASS, STRUCTURE, ASSOCIATION, DOMAIN, VIEW, LINE_FORM, METAOBJECT, DRAWING_RULE -> includeNode(symbol);
                case GRAPHIC -> { includeNode(symbol); ((guru.interlis.model.GraphicSymbol)symbol).rules().forEach(this::include); }
                case METADATA_BASKET -> { includeNode(symbol); ((guru.interlis.model.MetadataBasketSymbol)symbol).objects().forEach(this::include); }
                default -> {
                }
            }
        }

        private void includeContainer(Symbol symbol) {
            ensureContainer(symbol);
            if(!expandedContainers.add(symbol.id()))return;
            List<SymbolId> members=symbol instanceof ModelSymbol model?model.members():symbol instanceof TopicSymbol topic?topic.members():List.of();
            for(var member:members){token.throwIfCancellationRequested();include(member);if(limited)return;}
        }

        private void ensureContainer(Symbol symbol) {
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
                    ensureContainer(ownerSymbol);
                }
            }
            containers.put(containerId, new DiagramContainer(containerId,
                    symbol.kind() == SymbolKind.MODEL ? DiagramContainer.ContainerKind.MODEL
                            : DiagramContainer.ContainerKind.TOPIC,
                    parent.filter(containers::containsKey), displayName(symbol),
                    qualifiedName(symbol), Optional.of(new SymbolRef(context.snapshotId(), symbol.id())),
                    anchor(symbol)));
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
                ensureContainer(owner);
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
            nodes.put(nodeId, new DiagramNode(nodeId, kind, containerId, displayName(symbol),
                    qualifiedName(symbol), nodeStatus(symbol),
                    Optional.of(new SymbolRef(context.snapshotId(), symbol.id())),
                    compartments, anchor(symbol),symbol.header().modifiers()));
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
                    addEffectiveMembers(symbol.id(), attributes, roles);
                    addConstraints(association.constraints(), constraints);
                }
                case guru.interlis.model.ViewSymbol view -> {
                    addEffectiveMembers(symbol.id(), attributes, roles);
                    addConstraints(view.constraints(), constraints);
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
            List<SymbolId> declared = symbol instanceof ClassSymbol c ? c.members() : symbol instanceof StructureSymbol s ? s.members() : List.of();
            List<DiagramMember> parameters = new ArrayList<>();
            for (var id : declared) if (context.graph().symbol(id).orElse(null) instanceof guru.interlis.model.ParameterSymbol p) {
                if (parameters.size() >= options.budget().maxMembersPerNode()) { limited(); break; }
                parameters.add(new DiagramMember("member-" + id.value(), DiagramMember.MemberKind.PARAMETER, displayName(p),
                        options.showTypes() ? Optional.of(typeLabel(p.typeId())) : Optional.empty(), Optional.empty(), id, symbol.id(), false, p.overrides().isPresent(), anchor(p)));
            }
            if (!parameters.isEmpty()) result.add(new DiagramCompartment("comp-" + symbol.id().value() + "-parameters", DiagramCompartment.CompartmentKind.PARAMETERS, parameters));
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
                    addRole(member.declaredSymbol(),member.effectiveOwner(), attributes, roles, inherited, overridden);
                } else if (declared.kind() == SymbolKind.ATTRIBUTE) {
                    addAttribute(member.declaredSymbol(),member.effectiveOwner(), attributes, inherited, overridden);
                }
            }
        }

        private void addAttribute(SymbolId id,SymbolId effectiveOwner, List<DiagramMember> target,
                                  boolean inherited, boolean overridden) {
            if (!(context.graph().symbol(id).orElse(null) instanceof AttributeSymbol attribute)) {
                return;
            }
            if (target.size() >= options.budget().maxMembersPerNode()) {
                limited();
                return;
            }
            target.add(new DiagramMember(memberId(id,effectiveOwner,inherited),
                    DiagramMember.MemberKind.ATTRIBUTE, displayName(attribute),
                    options.showTypes() ? Optional.of(typeLabel(attribute.declaredType()))
                            : Optional.empty(),
                    Optional.empty(), id, effectiveOwner, inherited, overridden,
                    anchor(attribute)));
        }

        private void addRole(SymbolId id,SymbolId effectiveOwner, List<DiagramMember> attributes,
                             List<DiagramMember> roles, boolean inherited, boolean overridden) {
            if (!(context.graph().symbol(id).orElse(null) instanceof RoleSymbol role)) {
                return;
            }
            if (roles.size() >= options.budget().maxMembersPerNode()) {
                limited();
                return;
            }
            roles.add(new DiagramMember(memberId(id,effectiveOwner,inherited), DiagramMember.MemberKind.ROLE,
                    displayName(role), Optional.empty(),
                    options.showRoleCardinalities() ? Optional.of(format(role.cardinality()))
                            : Optional.empty(),
                    id, effectiveOwner, inherited, overridden, anchor(role)));
        }

        private static String memberId(SymbolId declared,SymbolId owner,boolean inherited) {
            return "member-"+declared.value()+(inherited?"-in-"+owner.value():"");
        }

        private void addConstraints(List<SymbolId> ids, List<DiagramMember> target) {
            if (!options.showConstraints()) {
                return;
            }
            for (SymbolId id : ids) {
                Symbol symbol = context.graph().symbol(id).orElse(null);
                if (symbol instanceof ConstraintSymbol constraint) {
                    target.add(new DiagramMember("member-" + id.value(),
                            DiagramMember.MemberKind.CONSTRAINT, displayName(constraint),
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
            addModelEdges();
            List<DiagramNode> snapshot = List.copyOf(nodes.values());
            for (DiagramNode node : snapshot) {
                token.throwIfCancellationRequested();
                if (node.symbol().isEmpty()) {
                    continue;
                }
                Symbol symbol = context.graph().symbol(node.symbol().get().symbol()).orElse(null);
                if (symbol == null) {
                    continue;
                }
                for (var member : context.graph().effectiveMembers(symbol.id())) {
                    var declaration=context.graph().symbol(member.declaredSymbol()).orElse(null);
                    if (declaration instanceof AttributeSymbol attribute)
                        addAttributeTypeEdges(node.id(), attribute, attribute.declaredType(), Optional.empty(), new LinkedHashSet<>());
                }
                if (symbol instanceof guru.interlis.model.DomainSymbol domain) {
                    domain.baseDomain().ifPresent(base -> addInheritance(node.id(), base));
                    addAttributeTypeEdges(node.id(),domain,domain.typeId(),Optional.empty(),new LinkedHashSet<>());
                } else if (symbol instanceof guru.interlis.model.GraphicSymbol graphic) {
                    graphic.baseGraphic().ifPresent(id->addInheritance(node.id(),id));
                    graphic.basis().ifPresent(id->addMetadataReference(node.id(),graphic,id,"basis"));
                } else if(symbol instanceof guru.interlis.model.DrawingRuleSymbol rule) {
                    rule.signatureClass().ifPresent(id->addMetadataReference(node.id(),rule,id,"signature"));
                    rule.owner().ifPresent(id->addMetadataReference(node.id(),rule,id,"graphic"));
                    rule.overrides().ifPresent(id->addInheritance(node.id(),id));
                } else if (symbol instanceof guru.interlis.model.MetadataBasketSymbol basket) {
                    basket.topic().ifPresent(target -> addMetadataReference(node.id(), basket, target, "topic"));
                    basket.baseBasket().ifPresent(base -> addInheritance(node.id(), base));
                } else if (symbol instanceof guru.interlis.model.MetaObjectSymbol object) {
                    object.objectClass().ifPresent(target -> addMetadataReference(node.id(), object, target, "class"));
                    object.owner().ifPresent(target -> addMetadataReference(node.id(), object, target, "basket"));
                } else if (symbol instanceof guru.interlis.model.LineFormSymbol form) {
                    form.segmentStructure().ifPresent(target -> {
                        String targetNode = ensureExternal(target, false);
                        if (targetNode != null) addEdge(new DiagramEdge("edge-" + node.id() + "-segment", DiagramEdge.EdgeKind.REFERENCE,
                                new DiagramEndpoint(node.id(), Optional.empty()), new DiagramEndpoint(targetNode, Optional.empty()),
                                Optional.of(new SymbolRef(context.snapshotId(), form.id())), emptyEnd(), emptyEnd(), "segment", anchor(form)));
                    });
                } else if (symbol instanceof ClassSymbol clazz) {
                    clazz.baseType().ifPresent(base -> addInheritance(node.id(), base));
                } else if (symbol instanceof StructureSymbol structure) {
                    structure.baseType().ifPresent(base -> addInheritance(node.id(), base));
                } else if (symbol instanceof ViewSymbol view) {
                    view.baseView().ifPresent(base -> addInheritance(node.id(), base));
                    for (SymbolId basis : view.bases()) addViewDerivation(node.id(), basis);
                } else if (symbol instanceof AssociationSymbol association) {
                    association.derivedFrom().ifPresent(base -> addViewDerivation(node.id(), base));
                    association.baseAssociation().ifPresent(base -> addInheritance(node.id(), base));
                    for (SymbolId roleId : context.graph().effectiveMembers(association.id()).stream()
                            .map(guru.interlis.model.EffectiveMember::declaredSymbol).toList()) {
                        Symbol roleSymbol = context.graph().symbol(roleId).orElse(null);
                        if (roleSymbol instanceof RoleSymbol role) {
                            addAssociationRole(node.id(), role);
                        }
                    }
                }
            }
        }


        /** Projects only already bound type identities; no name or inheritance binding here. */
        private void addAttributeTypeEdges(String node, Symbol origin, guru.interlis.model.TypeId typeId,
                Optional<Cardinality> cardinality, Set<guru.interlis.model.TypeId> visited) {
            token.throwIfCancellationRequested();
            if (!visited.add(typeId)) return;
            var type=context.graph().type(typeId).orElse(null);
            if (type instanceof guru.interlis.model.DomainReferenceType domain) {
                addTypeEdge(node,origin,domain.targetDomain(),DiagramEdge.EdgeKind.DOMAIN_USE,Optional.empty());
                var target=context.graph().symbol(domain.targetDomain()).orElse(null);
                if (target instanceof guru.interlis.model.DomainSymbol declaration)
                    addAttributeTypeEdges(node,origin,declaration.typeId(),cardinality,visited);
            } else if (type instanceof guru.interlis.model.ReferenceType reference) {
                for(var target:reference.targetRoots()) addTypeEdge(node,origin,target,DiagramEdge.EdgeKind.REFERENCE,cardinality);
            } else if (type instanceof guru.interlis.model.StructureValueType structure) {
                for(var target:structure.targetRoots()) addTypeEdge(node,origin,target,DiagramEdge.EdgeKind.STRUCTURE_COMPOSITION,cardinality);
            } else if (type instanceof guru.interlis.model.CollectionType collection) {
                addAttributeTypeEdges(node,origin,collection.elementType(),Optional.of(collection.cardinality()),visited);
            }
        }
        private void addTypeEdge(String node, Symbol origin, SymbolId target, DiagramEdge.EdgeKind kind,
                Optional<Cardinality> cardinality) {
            String other=ensureExternal(target,false);
            if(other!=null) addEdge(new DiagramEdge("edge-"+node+"-"+origin.id().value()+"-"+kind+"-"+target.value(),kind,
                    new DiagramEndpoint(node,Optional.empty()),new DiagramEndpoint(other,Optional.empty()),
                    Optional.of(new SymbolRef(context.snapshotId(),origin.id())),emptyEnd(),
                    new DiagramEdgeEnd(Optional.empty(),cardinality,guru.interlis.model.RoleAggregation.NONE),displayName(origin),anchor(origin)));
        }
        private void addModelEdges() {
            var processed=new LinkedHashSet<String>();
            while(true) {
                var pending=containers.values().stream().filter(c -> !processed.contains(c.id())).toList();
                if(pending.isEmpty())break;
                for(var container:pending) {
                    token.throwIfCancellationRequested();
                    processed.add(container.id());
                    var declaration=container.symbol().flatMap(ref -> context.graph().symbol(ref.symbol())).orElse(null);
                    if(declaration instanceof ModelSymbol model) {
                        if(options.showImports()) for(var imported:model.imports()) {
                            if(imported.targetModel().isEmpty()){addMissingImport(container,model,imported);continue;}
                            var target=imported.targetModel().orElseThrow();
                            String other=modelFrame(target);
                            if(other!=null)addEdge(new DiagramEdge("edge-import-"+model.id().value()+"-"+target.value(),DiagramEdge.EdgeKind.MODEL_IMPORT,
                                    new DiagramEndpoint(container.id(),Optional.empty()),new DiagramEndpoint(other,Optional.empty()),
                                    Optional.of(new SymbolRef(context.snapshotId(),model.id())),emptyEnd(),emptyEnd(),imported.importedModel(),
                                    Optional.of(new SourceAnchor(context.snapshotId(),imported.range(),Optional.empty()))));
                        }
                        model.translation().flatMap(guru.interlis.model.ModelTranslation::sourceTarget).ifPresent(target -> {
                            String other=modelFrame(target);
                            if(other!=null)addEdge(new DiagramEdge("edge-translation-"+model.id().value()+"-"+target.value(),DiagramEdge.EdgeKind.TRANSLATION,
                                    new DiagramEndpoint(container.id(),Optional.empty()),new DiagramEndpoint(other,Optional.empty()),
                                    Optional.of(new SymbolRef(context.snapshotId(),model.id())),emptyEnd(),emptyEnd(),"translation",anchor(model)));
                        });
                    } else if(declaration instanceof TopicSymbol topic) topic.baseTopic().ifPresent(target -> {
                        var base=context.graph().symbol(target).orElse(null);
                        if(base==null)return;
                        base.owner().ifPresent(this::modelFrame);
                        String other="container-"+target.value();
                        containers.putIfAbsent(other,new DiagramContainer(other,DiagramContainer.ContainerKind.TOPIC,
                                base.owner().map(id -> "container-"+id.value()),displayName(base),qualifiedName(base),
                                Optional.of(new SymbolRef(context.snapshotId(),target)),anchor(base)));
                        addEdge(new DiagramEdge("edge-topic-inherits-"+topic.id().value()+"-"+target.value(),DiagramEdge.EdgeKind.INHERITANCE,
                                new DiagramEndpoint(container.id(),Optional.empty()),new DiagramEndpoint(other,Optional.empty()),
                                Optional.of(new SymbolRef(context.snapshotId(),topic.id())),emptyEnd(),emptyEnd(),"extends",anchor(topic)));
                    });
                }
            }
        }

        private DiagramNodeStatus nodeStatus(Symbol symbol) {
            var type=context.graph().declaredType(symbol.id()).flatMap(context.graph()::type).orElse(null);
            if(type instanceof guru.interlis.model.ErrorType || type instanceof guru.interlis.model.PendingType)
                return DiagramNodeStatus.PARTIAL;
            if(symbol.origin() instanceof guru.interlis.model.Origin.DeclaredOrigin origin)
                for(var diagnostic:context.diagnostics()) {
                    token.throwIfCancellationRequested();
                    var range=diagnostic.primaryRange().orElse(null);var declaration=origin.range();
                    if(diagnostic.severity()==Severity.ERROR && range!=null && range.source().equals(declaration.source())
                            && range.revision().equals(declaration.revision()) && range.startOffset()<declaration.endOffset()
                            && range.endOffset()>declaration.startOffset()) return DiagramNodeStatus.PARTIAL;
                }
            return DiagramNodeStatus.RESOLVED;
        }
        private void addMissingImport(DiagramContainer source,ModelSymbol model,guru.interlis.model.ModelImport imported) {
            notices.add(new ProjectionNotice(ProjectionNoticeCode.UNRESOLVED_TARGET,Severity.WARNING,
                    "projection.unresolvedImport",List.of(imported.importedModel()),
                    Optional.of(new SymbolRef(context.snapshotId(),model.id())),Optional.of(imported.range())));
            if(nodes.size()>=options.budget().maxNodes()){limited();return;}
            String container="container-external",id="stub-import-"+model.id().value()+"-"+imported.importedModel();
            containers.putIfAbsent(container,new DiagramContainer(container,DiagramContainer.ContainerKind.MODEL,
                    Optional.empty(),"External","External",Optional.empty(),Optional.empty()));
            nodes.putIfAbsent(id,new DiagramNode(id,DiagramNodeKind.EXTERNAL_REFERENCE,container,
                    imported.importedModel(),imported.importedModel(),DiagramNodeStatus.UNRESOLVED,
                    Optional.empty(),List.of(),Optional.empty(),Set.of()));
            addEdge(new DiagramEdge("edge-"+id,DiagramEdge.EdgeKind.MODEL_IMPORT,
                    new DiagramEndpoint(source.id(),Optional.empty()),new DiagramEndpoint(id,Optional.empty()),
                    Optional.of(new SymbolRef(context.snapshotId(),model.id())),emptyEnd(),emptyEnd(),imported.importedModel(),
                    Optional.of(new SourceAnchor(context.snapshotId(),imported.range(),Optional.empty()))));
        }

        private String modelFrame(SymbolId target) {
            var declaration=context.graph().symbol(target).orElse(null);
            if(!(declaration instanceof ModelSymbol))return null;
            if(options.scope()==DiagramScope.WITH_DEPENDENCIES)include(target);
            String id="container-"+target.value();
            containers.putIfAbsent(id,new DiagramContainer(id,DiagramContainer.ContainerKind.MODEL,Optional.empty(),displayName(declaration),qualifiedName(declaration),
                    Optional.of(new SymbolRef(context.snapshotId(),target)),anchor(declaration)));
            return id;
        }

        private void addViewDerivation(String sourceNode, SymbolId basisId) {
            var basis = (ViewSymbol.Base)context.graph().symbol(basisId).orElseThrow();
            String targetNode = ensureExternal(basis.target(), false);
            if (targetNode == null) return;
            addEdge(new DiagramEdge("edge-" + sourceNode + "-derives-" + basisId.value(),
                    DiagramEdge.EdgeKind.VIEW_DERIVATION, new DiagramEndpoint(sourceNode, Optional.empty()),
                    new DiagramEndpoint(targetNode, Optional.empty()),
                    Optional.of(new SymbolRef(context.snapshotId(), basis.id())),
                    emptyEnd(), emptyEnd(), displayName(basis), anchor(basis)));
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
            for (SymbolId targetId : role.targetRoots()) {
                addAssociationRoleTarget(associationNode, role, targetId);
            }
        }

        private void addAssociationRoleTarget(String associationNode, RoleSymbol role, SymbolId targetId) {
            String target = ensureExternal(targetId, true);
            if (target == null) {
                return;
            }
            DiagramEdgeEnd end = new DiagramEdgeEnd(Optional.of(displayName(role)),
                    Optional.of(role.cardinality()), role.aggregation());
            addEdge(new DiagramEdge("edge-" + associationNode + "-" + role.id().value() + "-" + targetId.value(),
                    DiagramEdge.EdgeKind.ASSOCIATION_ROLE,
                    new DiagramEndpoint(associationNode, Optional.empty()),
                    new DiagramEndpoint(target, Optional.empty()),
                    Optional.of(new SymbolRef(context.snapshotId(), role.id())),
                    emptyEnd(), end, displayName(role), anchor(role)));
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
            if (!options.visibleKinds().contains(kind) && !(symbol instanceof TopicSymbol)) {
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
                    containerId, displayName(symbol), qualifiedName(symbol),
                    nodeStatus(symbol),
                    Optional.of(new SymbolRef(context.snapshotId(), symbol.id())),
                    List.of(), anchor(symbol),symbol.header().modifiers()));
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
            return context.complete() && notices.isEmpty() && nodes.values().stream().allMatch(n -> n.status()==DiagramNodeStatus.RESOLVED) ? ProjectionCompleteness.COMPLETE
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
                        .map(this::displayName)
                        .orElse("DOMAIN");
                case guru.interlis.model.StructureValueType structure -> structure.target()
                        .flatMap(context.graph()::symbol).map(this::displayName).orElse("ANYSTRUCTURE")
                        + (structure.restrictions().isEmpty()?"":" RESTRICTION("+structure.restrictions().stream()
                        .map(id -> displayName(context.graph().symbol(id).orElseThrow())).collect(java.util.stream.Collectors.joining(";"))+")");
                case guru.interlis.model.CollectionType collection -> collection.collectionKind()
                        + " " + format(collection.cardinality()) + " OF "
                        + typeLabel(collection.elementType());
                default -> descriptor.kind().name();
            };
        }

        private void addMetadataReference(String node, Symbol source, SymbolId target, String label) {
            String other = ensureExternal(target, false);
            if (other != null) addEdge(new DiagramEdge("edge-" + node + "-" + label, DiagramEdge.EdgeKind.REFERENCE,
                    new DiagramEndpoint(node, Optional.empty()), new DiagramEndpoint(other, Optional.empty()),
                    Optional.of(new SymbolRef(context.snapshotId(), source.id())), emptyEnd(), emptyEnd(), label, anchor(source)));
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
                case GRAPHIC -> DiagramNodeKind.GRAPHIC;
                case DRAWING_RULE -> DiagramNodeKind.DRAWING_RULE;
                case LINE_FORM -> DiagramNodeKind.LINE_FORM;
                case METADATA_BASKET -> DiagramNodeKind.METADATA_BASKET;
                case METAOBJECT -> DiagramNodeKind.METAOBJECT;
                default -> DiagramNodeKind.EXTERNAL_REFERENCE;
            };
        }
    }
}

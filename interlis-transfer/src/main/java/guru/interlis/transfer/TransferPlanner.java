package guru.interlis.transfer;

import guru.interlis.model.AssociationSymbol;
import guru.interlis.model.AttributeSymbol;
import guru.interlis.model.Cardinality;
import guru.interlis.model.ClassSymbol;
import guru.interlis.model.CollectionType;
import guru.interlis.model.CompiledSchema;
import guru.interlis.model.CoordinateType;
import guru.interlis.model.DomainReferenceType;
import guru.interlis.model.GeometryType;
import guru.interlis.model.GraphicSymbol;
import guru.interlis.model.ModelSymbol;
import guru.interlis.model.Modifier;
import guru.interlis.model.Origin;
import guru.interlis.model.QualifiedName;
import guru.interlis.model.RoleSymbol;
import guru.interlis.model.StructureSymbol;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TopicSymbol;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeId;
import guru.interlis.model.TypeRef;
import guru.interlis.model.UnsupportedSymbol;
import guru.interlis.model.ViewSymbol;
import guru.interlis.model.query.QueryResult;
import guru.interlis.model.query.SemanticQueries;
import guru.interlis.transfer.TransferPlanningResult.Feature;
import guru.interlis.transfer.TransferPlanningResult.Issue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Projects the checked closure of one schema into a format-neutral transfer vocabulary.
 * Association slots describe the relationship itself. Adapters select any format-specific
 * placement of those slots and encode the selected names. */
public final class TransferPlanner {
    private TransferPlanner() { }

    /** Name selection for the neutral vocabulary, independent of language version. */
    public enum Names {
        /** Use names of the canonical originals of translated declarations. */
        ORIGINAL,
        /** Use names as declared in each model. */
        DECLARED
    }

    /** Projects a nonnull checked schema using original names, without accessing host resources. */
    public static TransferPlanningResult plan(CompiledSchema schema) {
        return plan(schema, Names.ORIGINAL);
    }

    /** Projects a nonnull checked schema using the selected nonnull vocabulary.
     * Issues are reported in deterministic model and member order. A result with issues
     * carries no partial plan. Neither the schema nor its graph is modified. */
    public static TransferPlanningResult plan(CompiledSchema schema, Names names) {
        return new Projection(schema.queries(), Objects.requireNonNull(names, "names")).project(schema);
    }

    private static final class Projection {
        private final SemanticQueries queries;
        private final Names names;
        private final Map<SymbolId, SymbolId> originals = new HashMap<>();
        private final Map<SymbolId, QualifiedName> qualifiedNames = new HashMap<>();
        private final List<ModelDescriptor> models = new ArrayList<>();
        private final List<TopicDescriptor> topics = new ArrayList<>();
        private final List<ObjectDescriptor> objects = new ArrayList<>();
        private final LinkedHashSet<Issue> issues = new LinkedHashSet<>();

        Projection(SemanticQueries queries, Names names) {
            this.queries = queries;
            this.names = names;
            queries.graph().qualifiedNames().forEach((name, ids) ->
                    ids.forEach(id -> qualifiedNames.put(id, name)));
        }

        TransferPlanningResult project(CompiledSchema schema) {
            try {
                var declarations = schema.models().stream().map(ref -> {
                    var path = QualifiedName.of(ref.symbol().value());
                    if (known(queries.symbol(ref), path) instanceof ModelSymbol model) {
                        return model;
                    }
                    throw new MissingModelInformation(path);
                }).toList();
                declarations.forEach(this::indexTranslations);
                declarations.forEach(this::projectModel);
            } catch (MissingModelInformation missing) {
                issues.add(new Issue(missing.path, Feature.MISSING_MODEL_INFORMATION));
            }
            if (!issues.isEmpty()) {
                return new TransferPlanningResult.Unsupported(List.copyOf(issues));
            }
            return new TransferPlanningResult.Ready(new TransferPlan(schema, models, topics, objects));
        }

        private void indexTranslations(ModelSymbol model) {
            model.translation().ifPresent(translation -> {
                for (var link : translation.correspondences()) {
                    if (!link.original().equals(link.translated())) {
                        var previous = originals.putIfAbsent(link.translated(), link.original());
                        if (previous != null && !previous.equals(link.original())) {
                            throw new MissingModelInformation(qualifiedName(model));
                        }
                    }
                }
            });
        }

        private void projectModel(ModelSymbol model) {
            SymbolRef modelRef = ref(model.id());
            var modelTopics = new ArrayList<SymbolRef>();
            for (var member : model.members()) {
                Symbol declaration = symbol(member);
                if (declaration instanceof TopicSymbol topic) {
                    modelTopics.add(ref(topic.id()));
                    projectTopic(modelRef, topic);
                } else {
                    projectObject(modelRef, Optional.empty(), declaration);
                }
            }
            models.add(new ModelDescriptor(modelRef, name(model), model.languageVersion(),
                    model.modelVersion(), model.transferNamespace(),
                    model.options().incrementalTransferAllowed(), modelTopics));
        }

        private void projectTopic(SymbolRef model, TopicSymbol topic) {
            SymbolRef topicRef = ref(topic.id());
            topic.members().forEach(id -> projectObject(model, Optional.of(topicRef), symbol(id)));
            for (var binding : topic.genericBindings()) {
                for (var alternative : binding.alternatives()) {
                    checkType(typeOf(ref(alternative), qualifiedName(topic)), qualifiedName(topic));
                }
            }
            var objectTypes = known(queries.membersOf(topicRef), qualifiedName(topic)).stream()
                    .filter(member -> isBasketObject(symbol(member.symbol())))
                    .toList();
            topics.add(new TopicDescriptor(topicRef, name(topic), model, topic.baseTopic().map(this::ref),
                    topic.dependencies().stream().map(this::ref).toList(), objectTypes,
                    topic.header().oidRules(), topic.genericBindings(), topic.header().has(Modifier.ABSTRACT)));
        }

        private boolean isBasketObject(Symbol symbol) {
            return !symbol.header().has(Modifier.ABSTRACT)
                    && (symbol instanceof ClassSymbol || symbol instanceof AssociationSymbol association
                        && association.derivedFrom().isEmpty());
        }

        private void projectObject(SymbolRef model, Optional<SymbolRef> topic, Symbol declaration) {
            Optional<SymbolId> base;
            Optional<Cardinality> associationCardinality = Optional.empty();
            switch (declaration) {
                case ClassSymbol cls -> base = cls.baseType();
                case StructureSymbol structure -> base = structure.baseType();
                case AssociationSymbol association -> {
                    base = association.baseAssociation();
                    associationCardinality = Optional.of(association.cardinality());
                    if (association.derivedFrom().isPresent()) {
                        issues.add(new Issue(qualifiedName(declaration), Feature.DERIVED_ASSOCIATION));
                        return;
                    }
                }
                case ViewSymbol view -> {
                    if (!view.header().has(Modifier.TRANSIENT)
                            && topic.map(ref -> symbol(ref.symbol()).header().has(Modifier.VIEW)).orElse(false)) {
                        issues.add(new Issue(qualifiedName(view), Feature.VIEW));
                    }
                    return;
                }
                case GraphicSymbol graphic -> {
                    if (!graphic.header().has(Modifier.ABSTRACT)) {
                        issues.add(new Issue(qualifiedName(graphic), Feature.GRAPHIC));
                    }
                    return;
                }
                case UnsupportedSymbol unsupported -> {
                    issues.add(new Issue(qualifiedName(unsupported), Feature.MISSING_MODEL_INFORMATION));
                    return;
                }
                default -> { return; }
            }
            var path = qualifiedName(declaration);
            if (declaration.name().synthetic()) {
                issues.add(new Issue(path, Feature.ANONYMOUS_OBJECT));
                return;
            }
            if (topic.isEmpty() && isBasketObject(declaration)) {
                issues.add(new Issue(path, Feature.OBJECT_OUTSIDE_TOPIC));
                return;
            }
            SymbolRef owner = ref(declaration.id());
            var slots = new ArrayList<TransferSlot>();
            for (var member : known(queries.effectiveMembers(owner), path)) {
                Symbol slot = symbol(member.declaredSymbol());
                var memberPath = path.child(slot.name().value());
                switch (slot) {
                    case AttributeSymbol attribute -> {
                        var attributeRef = ref(attribute.id());
                        var type = typeOf(attributeRef, memberPath);
                        if (!attribute.transientAttribute()) {
                            checkType(type, memberPath);
                        }
                        var cardinality = type instanceof CollectionType collection ? collection.cardinality()
                                : Cardinality.of(attribute.mandatory() ? 1 : 0, 1);
                        slots.add(new AttributeSlot(attributeRef, name(attribute), member, attribute,
                                typeRef(type.id()), cardinality));
                    }
                    case RoleSymbol role -> slots.add(new RoleSlot(ref(role.id()), name(role), member, role));
                    default -> throw new MissingModelInformation(memberPath);
                }
            }
            objects.add(new ObjectDescriptor(owner, name(declaration), model, topic, declaration.kind(),
                    base.map(this::ref), slots, declaration.header().oidRules(), declaration.header().modifiers(),
                    associationCardinality));
        }

        private void checkType(TypeDescriptor type, QualifiedName path) {
            switch (type.kind()) {
                case TEXT, BOOLEAN, NUMERIC, ENUMERATION, ALIGNMENT, FORMATTED, OID,
                        BLACKBOX, CLASS_VALUE, ATTRIBUTE_PATH, REFERENCE, STRUCTURE_VALUE, GENERIC, DEFERRED -> { }
                case COLLECTION -> checkType(known(queries.type(typeRef(((CollectionType) type).elementType())), path), path);
                case DOMAIN_REFERENCE -> checkType(typeOf(ref(((DomainReferenceType) type).targetDomain()), path), path);
                case COORDINATE -> {
                    if (((CoordinateType) type).dimensions() < 2) {
                        issues.add(new Issue(path, Feature.COORDINATE_DIMENSION));
                    }
                }
                case GEOMETRY -> checkGeometry((GeometryType) type, path);
                case ERROR, PENDING -> issues.add(new Issue(path, Feature.MISSING_MODEL_INFORMATION));
                default -> issues.add(new Issue(path, Feature.VALUE_TYPE));
            }
        }

        private void checkGeometry(GeometryType geometry, QualifiedName path) {
            for (var form : geometry.lineForms()) {
                var symbol = symbol(form);
                if (!(symbol.origin() instanceof Origin.PredefinedOrigin predefined)
                        || !(predefined.key().equals("INTERLIS.STRAIGHTS") || predefined.key().equals("INTERLIS.ARCS"))) {
                    issues.add(new Issue(path, Feature.CUSTOM_LINE_FORM));
                }
            }
            geometry.lineAttributes().ifPresent(id -> {
                // The structure's effective fields are checked by projectObject, as for other structures.
                if (!(known(queries.symbol(ref(id)), path) instanceof StructureSymbol)) {
                    issues.add(new Issue(path, Feature.LINE_ATTRIBUTES));
                }
            });
            geometry.vertexDomain().ifPresent(vertex -> checkType(typeOf(ref(vertex), path), path));
        }

        private TypeDescriptor typeOf(SymbolRef symbol, QualifiedName path) {
            var effective = known(queries.effectiveType(symbol), path);
            return known(queries.type(typeRef(effective.effectiveType())), path);
        }

        private TransferName name(Symbol symbol) {
            SymbolId canonical = symbol.id();
            var visited = new HashSet<SymbolId>();
            while (originals.containsKey(canonical)) {
                if (!visited.add(canonical)) {
                    throw new MissingModelInformation(qualifiedName(symbol));
                }
                canonical = originals.get(canonical);
            }
            QualifiedName declared = qualifiedName(symbol);
            QualifiedName transfer = names == Names.ORIGINAL && !canonical.equals(symbol.id())
                    ? qualifiedName(symbol(canonical)) : declared;
            return new TransferName(declared, transfer, ref(canonical));
        }

        private QualifiedName qualifiedName(Symbol symbol) {
            var indexed = qualifiedNames.get(symbol.id());
            if (indexed != null) {
                return indexed;
            }
            // Synthetic declarations are absent from the qualified-name index.
            return symbol.owner().map(owner -> qualifiedName(symbol(owner)).child(symbol.name().value()))
                    .orElseGet(() -> QualifiedName.of(symbol.name().value()));
        }

        private Symbol symbol(SymbolId id) {
            return known(queries.symbol(ref(id)), QualifiedName.of(id.value()));
        }

        private SymbolRef ref(SymbolId id) {
            return new SymbolRef(queries.snapshotId(), id);
        }

        private TypeRef typeRef(TypeId id) {
            return new TypeRef(queries.snapshotId(), id);
        }

        private static <T> T known(QueryResult<T> result, QualifiedName path) {
            return result.known().orElseThrow(() -> new MissingModelInformation(path));
        }
    }

    private static final class MissingModelInformation extends RuntimeException {
        private final QualifiedName path;

        MissingModelInformation(QualifiedName path) {
            this.path = path;
        }
    }
}

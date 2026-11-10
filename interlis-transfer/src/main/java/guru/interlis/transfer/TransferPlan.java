package guru.interlis.transfer;

import guru.interlis.model.CompiledSchema;
import guru.interlis.model.ModelSymbol;
import guru.interlis.model.SchemaFingerprint;
import guru.interlis.model.SnapshotId;
import guru.interlis.model.SnapshotMismatchException;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeRef;
import guru.interlis.model.query.QueryResult;
import guru.interlis.model.query.SemanticQueries;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Immutable neutral transfer vocabulary derived from one checked schema snapshot.
 * Lists preserve plan order. Lookups reject handles from other snapshots.
 */
public final class TransferPlan {
    private final SnapshotId snapshotId;
    private final SchemaFingerprint schemaFingerprint;
    private final SemanticQueries queries;
    private final List<SymbolRef> rootModels;
    private final List<ModelDescriptor> models;
    private final List<TopicDescriptor> topics;
    private final List<ObjectDescriptor> objects;
    private final Map<SymbolRef, ModelDescriptor> modelsBySymbol;
    private final Map<SymbolRef, TopicDescriptor> topicsBySymbol;
    private final Map<SymbolRef, ObjectDescriptor> objectsBySymbol;

    TransferPlan(CompiledSchema schema, List<ModelDescriptor> models, List<TopicDescriptor> topics,
                 List<ObjectDescriptor> objects) {
        snapshotId = schema.snapshotId();
        schemaFingerprint = schema.fingerprint();
        queries = schema.queries();
        rootModels = List.copyOf(schema.rootModels());
        this.models = List.copyOf(models);
        this.topics = List.copyOf(topics);
        this.objects = List.copyOf(objects);
        modelsBySymbol = index(this.models, ModelDescriptor::symbol);
        topicsBySymbol = index(this.topics, TopicDescriptor::symbol);
        objectsBySymbol = index(this.objects, ObjectDescriptor::symbol);
    }

    /** Returns the snapshot to which the plan and its handles belong. */
    public SnapshotId snapshotId() {
        return snapshotId;
    }

    /** Returns the fingerprint of the checked schema used for projection. */
    public SchemaFingerprint schemaFingerprint() {
        return schemaFingerprint;
    }

    /** Returns the immutable root model references in schema order. */
    public List<SymbolRef> rootModels() {
        return rootModels;
    }

    /**
     * Returns the immutable set of the supplied plan models and their transitive
     * resolved imports present in this plan. Order is unspecified.
     * @throws SnapshotMismatchException if a root belongs to another snapshot
     * @throws IllegalArgumentException if a root is absent from this plan
     */
    public Set<SymbolRef> modelImportClosure(Set<SymbolRef> roots) {
        var covered = new HashSet<SymbolRef>();
        var remaining = new ArrayDeque<>(roots);
        while (!remaining.isEmpty()) {
            SymbolRef ref = remaining.removeFirst();
            if (model(ref).isEmpty()) {
                throw new IllegalArgumentException("Model is absent from the transfer plan");
            }
            if (covered.add(ref) && symbol(ref).known().orElseThrow() instanceof ModelSymbol model) {
                model.imports().forEach(imported -> imported.targetModel()
                        .map(id -> new SymbolRef(snapshotId, id))
                        .filter(modelsBySymbol::containsKey)
                        .ifPresent(remaining::addLast));
            }
        }
        return Set.copyOf(covered);
    }

    /** Returns the immutable model descriptors in plan order. */
    public List<ModelDescriptor> models() {
        return models;
    }

    /** Returns the immutable topic descriptors in plan order. */
    public List<TopicDescriptor> topics() {
        return topics;
    }

    /** Returns the immutable object type descriptors in plan order. */
    public List<ObjectDescriptor> objects() {
        return objects;
    }

    /**
     * Finds a model declaration in this plan.
     * @param ref model reference from this snapshot
     * @return descriptor, or empty for a reference absent from the model index
     * @throws SnapshotMismatchException if the reference belongs to a different snapshot
     */
    public Optional<ModelDescriptor> model(SymbolRef ref) {
        checkSnapshot(ref.snapshot());
        return Optional.ofNullable(modelsBySymbol.get(ref));
    }

    /**
     * Finds a topic declaration in this plan.
     * @param ref topic reference from this snapshot
     * @return descriptor, or empty for a reference absent from the topic index
     * @throws SnapshotMismatchException if the reference belongs to a different snapshot
     */
    public Optional<TopicDescriptor> topic(SymbolRef ref) {
        checkSnapshot(ref.snapshot());
        return Optional.ofNullable(topicsBySymbol.get(ref));
    }

    /**
     * Finds a class, structure or association declaration in this plan.
     * @param ref object type reference from this snapshot
     * @return descriptor, or empty for a reference absent from the object type index
     * @throws SnapshotMismatchException if the reference belongs to a different snapshot
     */
    public Optional<ObjectDescriptor> object(SymbolRef ref) {
        checkSnapshot(ref.snapshot());
        return Optional.ofNullable(objectsBySymbol.get(ref));
    }

    /**
     * Resolves a type in the plan's checked schema.
     * @param ref type reference from this snapshot
     * @return type or the schema query's typed rejection
     * @throws SnapshotMismatchException if the reference belongs to a different snapshot
     */
    public QueryResult<TypeDescriptor> type(TypeRef ref) {
        checkSnapshot(ref.snapshot());
        return queries.type(ref);
    }

    /**
     * Resolves a symbol in the plan's checked schema, including predefined symbols.
     * @param ref symbol reference from this snapshot
     * @return symbol or the schema query's typed rejection
     * @throws SnapshotMismatchException if the reference belongs to a different snapshot
     */
    public QueryResult<Symbol> symbol(SymbolRef ref) {
        checkSnapshot(ref.snapshot());
        return queries.symbol(ref);
    }

    private <T> Map<SymbolRef, T> index(List<T> descriptors, Function<T, SymbolRef> reference) {
        return descriptors.stream().collect(Collectors.toUnmodifiableMap(descriptor -> {
            SymbolRef ref = reference.apply(descriptor);
            checkSnapshot(ref.snapshot());
            return ref;
        }, Function.identity()));
    }

    private void checkSnapshot(SnapshotId actual) {
        if (!snapshotId.equals(actual)) {
            throw new SnapshotMismatchException(snapshotId, actual);
        }
    }
}

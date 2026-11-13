package guru.interlis.xtf24;

import guru.interlis.model.ModelSymbol;
import guru.interlis.model.QualifiedName;
import guru.interlis.model.Modifier;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TopicSymbol;
import guru.interlis.transfer.ModelDescriptor;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.TopicDescriptor;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferSlot;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static guru.interlis.xtf24.Xtf24Exception.Reason.INVALID_WIRE;
import static guru.interlis.xtf24.Xtf24Exception.Reason.UNSUPPORTED;

final class Xtf24Names {
    private record ObjectLocalName(SymbolRef model, String localName) {}

    private final TransferPlan plan;
    private final Map<SymbolRef, SymbolRef> originals;
    private final Map<SymbolRef, QName> objectNames;
    private final Map<QName, List<ObjectDescriptor>> objects;
    private final Map<QName, List<TopicDescriptor>> topics;

    Xtf24Names(TransferPlan plan) {
        this.plan = plan;
        var links = new HashMap<SymbolRef, SymbolRef>();
        for (var descriptor : plan.models()) {
            var model = (ModelSymbol) symbol(descriptor.symbol());
            model.translation().ifPresent(translation -> translation.correspondences().forEach(link -> {
                var translated = new SymbolRef(plan.snapshotId(), link.translated());
                var original = new SymbolRef(plan.snapshotId(), link.original());
                if (!translated.equals(original)) {
                    SymbolRef previous = links.putIfAbsent(translated, original);
                    if (previous != null && !previous.equals(original)) {
                        throw failure(UNSUPPORTED, "Conflicting checked translation correspondences");
                    }
                }
            }));
        }
        originals = Map.copyOf(links);
        Map<ObjectLocalName, Set<SymbolRef>> collisions = new HashMap<>();
        Map<QName, SymbolRef> declarations = new HashMap<>();
        for (var object : plan.objects()) {
            SymbolRef canonical = object.name().canonical();
            Symbol original = symbol(canonical);
            ModelSymbol model = declaringModel(canonical);
            var localName = new ObjectLocalName(ref(model, canonical), original.name().value());
            collisions.computeIfAbsent(localName, key -> new HashSet<>()).add(canonical);
        }

        Map<SymbolRef, QName> names = new HashMap<>();
        Map<QName, List<ObjectDescriptor>> objectIndex = new HashMap<>();
        for (var object : plan.objects()) {
            SymbolRef canonical = object.name().canonical();
            Symbol original = symbol(canonical);
            ModelSymbol model = declaringModel(canonical);
            String local = original.name().value();
            var key = new ObjectLocalName(ref(model, canonical), local);
            if (collisions.get(key).size() > 1) {
                Symbol owner = symbol(new SymbolRef(canonical.snapshot(), original.owner().orElseThrow()));
                if (owner instanceof TopicSymbol) {
                    local = owner.name().value() + "." + local;
                }
            }
            QName name = new QName(model.transferNamespace(), local);
            checkCollision(declarations, canonical, name);
            names.put(object.symbol(), name);
            objectIndex.computeIfAbsent(name, ignored -> new ArrayList<>()).add(object);
        }
        objectNames = Map.copyOf(names);
        objects = freeze(objectIndex);

        Map<QName, List<TopicDescriptor>> topicIndex = new HashMap<>();
        declarations.clear();
        for (var topic : plan.topics()) {
            QName name = topicName(topic);
            checkCollision(declarations, topic.name().canonical(), name);
            topicIndex.computeIfAbsent(name, ignored -> new ArrayList<>()).add(topic);
        }
        topics = freeze(topicIndex);
    }

    Set<SymbolRef> structureTypes(Set<SymbolRef> models) {
        var types = new HashSet<SymbolRef>();
        for (var type : plan.objects()) {
            if (type.kind() == SymbolKind.STRUCTURE && !type.modifiers().contains(Modifier.ABSTRACT)
                    && models.contains(type.model())) {
                types.add(type.symbol());
            }
        }
        return Set.copyOf(types);
    }

    QualifiedName originalName(SymbolRef ref) {
        var seen = new HashSet<SymbolRef>();
        while (originals.containsKey(ref)) {
            if (!seen.add(ref)) {
                throw failure(UNSUPPORTED, "Cyclic checked translation correspondence");
            }
            ref = originals.get(ref);
        }
        Symbol current = symbol(ref);
        var segments = new java.util.ArrayDeque<String>();
        segments.addFirst(current.name().value());
        while (current.owner().isPresent()) {
            current = symbol(new SymbolRef(ref.snapshot(), current.owner().orElseThrow()));
            segments.addFirst(current.name().value());
        }
        return new QualifiedName(List.copyOf(segments));
    }

    ModelDescriptor model(String name) {
        return model(name, false);
    }

    ModelDescriptor model(String name, boolean explicitOriginal) {
        var translation = translatedModel(name);
        if (translation.isPresent()) {
            return translation.orElseThrow();
        }
        var candidates = plan.models().stream().filter(model -> modelName(model).equals(name)).toList();
        var roots = candidates.stream().filter(model -> plan.rootModels().contains(model.symbol())).toList();
        if (!explicitOriginal && roots.size() == 1) {
            return roots.getFirst();
        }
        var originals = candidates.stream().filter(model -> model.symbol().equals(model.name().canonical())).toList();
        return unique(new QName("", name), originals);
    }

    java.util.Optional<ModelDescriptor> translatedModel(String name) {
        return plan.models().stream()
                .filter(model -> !model.symbol().equals(model.name().canonical()))
                .filter(model -> model.name().declared().last().equals(name)).findFirst();
    }

    String modelName(ModelDescriptor model) {
        return symbol(model.name().canonical()).name().value();
    }

    QName topicName(TopicDescriptor topic) {
        return declarationName(topic.name().canonical());
    }

    QName slotName(TransferSlot slot) {
        return declarationName(slot.name().canonical());
    }

    QName objectName(ObjectDescriptor object) {
        plan.object(object.symbol());
        QName name = objectNames.get(object.symbol());
        if (name == null) {
            throw failure(INVALID_WIRE, "Object type is outside the XTF 2.4 plan");
        }
        return name;
    }

    TopicDescriptor topic(QName name, Set<SymbolRef> availableModels) {
        var candidates = topics.getOrDefault(name, List.of()).stream()
                .filter(topic -> availableModels.contains(topic.model())).toList();
        if (candidates.size() > 1) {
            var original = candidates.stream().filter(topic -> topic.symbol().equals(topic.name().canonical())).findFirst();
            if (original.isPresent()) {
                return original.orElseThrow();
            }
        }
        return unique(name, candidates);
    }

    ObjectDescriptor object(QName name, Set<SymbolRef> availableTypes) {
        return unique(name, objects.getOrDefault(name, List.of()).stream()
                .filter(object -> availableTypes.contains(object.symbol())).toList());
    }

    private QName declarationName(SymbolRef canonical) {
        return new QName(declaringModel(canonical).transferNamespace(), symbol(canonical).name().value());
    }

    private ModelSymbol declaringModel(SymbolRef declaration) {
        Symbol symbol = symbol(declaration);
        while (!(symbol instanceof ModelSymbol)) {
            symbol = symbol(new SymbolRef(declaration.snapshot(), symbol.owner().orElseThrow()));
        }
        return (ModelSymbol) symbol;
    }

    private Symbol symbol(SymbolRef ref) {
        return plan.symbol(ref).known().orElseThrow(() -> failure(UNSUPPORTED, "Missing canonical XTF 2.4 declaration"));
    }

    private static SymbolRef ref(Symbol symbol, SymbolRef context) {
        return new SymbolRef(context.snapshot(), symbol.id());
    }

    private static <T> T unique(QName name, List<T> candidates) {
        if (candidates.size() != 1) {
            throw failure(INVALID_WIRE, "Expected one declaration for " + name + ", found " + candidates.size());
        }
        return candidates.getFirst();
    }

    private static <T> Map<QName, List<T>> freeze(Map<QName, List<T>> index) {
        index.replaceAll((name, values) -> List.copyOf(values));
        return Map.copyOf(index);
    }

    private static void checkCollision(Map<QName, SymbolRef> declarations, SymbolRef canonical, QName name) {
        SymbolRef existing = declarations.putIfAbsent(name, canonical);
        if (existing != null && !existing.equals(canonical)) {
            throw failure(UNSUPPORTED, "Multiple canonical declarations for XTF 2.4 name " + name);
        }
    }

    private static Xtf24Exception failure(Xtf24Exception.Reason reason, String message) {
        return new Xtf24Exception(reason, message, -1, -1, null);
    }
}

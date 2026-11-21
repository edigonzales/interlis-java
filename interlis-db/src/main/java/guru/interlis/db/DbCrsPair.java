package guru.interlis.db;

import guru.interlis.db.DbMapping.Column;
import guru.interlis.db.DbMapping.CrsDefaults;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.TopicDescriptor;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferValue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** An ili2db XTF 2.3 CRS-model assignment with one shared set of SQL relations. */
final class DbCrsPair {
    record Models(String original, String alternate) {
        static Models parse(String value, String selected) {
            String[] names = value.split("=", -1);
            if (names.length != 2 || !selected.equals(names[0])
                    || !names[1].matches("[A-Za-z][A-Za-z0-9_]*") || names[0].equals(names[1])) {
                throw new DbException("Expected an alternate CRS model assignment " + selected + "=Alternative");
            }
            return new Models(names[0], names[1]);
        }

        List<String> names() { return List.of(original, alternate); }

        String setting() { return original + "=" + alternate; }
    }

    private record ClassPair(ObjectDescriptor alternate, Map<SymbolRef, SymbolRef> alternateSlots) { }

    private final SymbolRef original;
    private final SymbolRef alternate;
    private final Map<SymbolRef, SymbolRef> topicOwners;
    private final Map<SymbolRef, ClassPair> classes;
    private final Map<SymbolRef, List<Column>> columns;
    private final Map<SymbolRef, Map<SymbolRef, Integer>> codesByTopic;

    DbCrsPair(TransferPlan plan, Models models, CrsDefaults originalCrs, CrsDefaults alternateCrs) {
        var main = plan.models().stream().filter(model -> model.name().declared().last().equals(models.original())).findFirst().orElseThrow();
        var other = plan.models().stream().filter(model -> model.name().declared().last().equals(models.alternate())).findFirst().orElseThrow();
        original = main.symbol();
        alternate = other.symbol();
        if (main.languageVersion() != LanguageVersion.ILI23 || other.languageVersion() != LanguageVersion.ILI23) {
            throw DbMapping.unsupported("alternate CRS models outside XTF 2.3");
        }
        var ownerTopics = new HashMap<SymbolRef, SymbolRef>();
        var objectPairs = new HashMap<SymbolRef, ClassPair>();
        var attributes = new HashMap<SymbolRef, List<Column>>();
        var codes = new HashMap<SymbolRef, Map<SymbolRef, Integer>>();
        if (main.topics().size() != other.topics().size()) throw incompatible(models.setting());
        for (int i = 0; i < main.topics().size(); i++) {
            var left = plan.topic(main.topics().get(i)).orElseThrow();
            var right = plan.topic(other.topics().get(i)).orElseThrow();
            if (!left.name().declared().last().equals(right.name().declared().last())
                    || !left.oidRules().isEmpty() || !right.oidRules().isEmpty()) {
                throw incompatible(left.name().declared().toString());
            }
            ownerTopics.put(right.symbol(), left.symbol());
            var leftCodes = new HashMap<SymbolRef, Integer>();
            var rightCodes = new HashMap<SymbolRef, Integer>();
            var a = objects(plan, left);
            var b = objects(plan, right);
            if (a.size() != b.size()) throw incompatible(left.name().declared().toString());
            for (int j = 0; j < a.size(); j++) {
                var src = a.get(j);
                var dest = b.get(j);
                if (!src.name().declared().last().equals(dest.name().declared().last())) {
                    throw incompatible(src.name().declared().toString());
                }
                var originalFields = DbMapping.attributes(src);
                var alternateFields = DbMapping.attributes(dest);
                if (originalFields.size() != alternateFields.size()) throw incompatible(src.name().declared().toString());
                var slots = new HashMap<SymbolRef, SymbolRef>();
                for (int field = 0; field < originalFields.size(); field++) {
                    var srcField = originalFields.get(field);
                    var destField = alternateFields.get(field);
                    String path = srcField.name().declared().toString();
                    var first = DbMapping.describeColumns(plan, src, srcField, originalCrs).getFirst();
                    var second = DbMapping.describeColumns(plan, dest, destField, alternateCrs).getFirst();
                    if (!srcField.name().declared().last().equals(destField.name().declared().last())
                            || !srcField.cardinality().equals(destField.cardinality()) || first.kind() != second.kind()
                            || first.dimensions() != second.dimensions() || first.target().isPresent()) {
                        throw incompatible(path);
                    }
                    slots.put(srcField.symbol(), destField.symbol());
                    if (first.kind().geometry()) {
                        leftCodes.put(srcField.symbol(), first.epsgCode());
                        rightCodes.put(srcField.symbol(), second.epsgCode());
                    }
                    attributes.put(srcField.symbol(), first.epsgCode() == second.epsgCode() ? List.of(first)
                            : List.of(first, first.withEpsg(second.epsgCode())));
                }
                objectPairs.put(src.symbol(), new ClassPair(dest, Map.copyOf(slots)));
            }
            codes.put(left.symbol(), Map.copyOf(leftCodes));
            codes.put(right.symbol(), Map.copyOf(rightCodes));
        }
        topicOwners = Map.copyOf(ownerTopics);
        classes = Map.copyOf(objectPairs);
        columns = Map.copyOf(attributes);
        codesByTopic = Map.copyOf(codes);
    }

    private static List<ObjectDescriptor> objects(TransferPlan plan, TopicDescriptor topic) {
        var classes = plan.objects().stream().filter(o -> o.topic().filter(topic.symbol()::equals).isPresent()).toList();
        for (var object : classes) {
            if (object.kind() != SymbolKind.CLASS || object.baseType().isPresent() || !object.transferable()
                    || !object.oidRules().isEmpty() || DbMapping.attributes(object).size() != object.slots().size()) {
                throw incompatible(object.name().declared().toString());
            }
        }
        return classes;
    }

    private static DbException incompatible(String path) {
        return DbMapping.unsupported("structurally incompatible alternate CRS model at " + path);
    }

    List<SymbolRef> models() { return List.of(original, alternate); }

    SymbolRef originalTopic(SymbolRef topic) { return topicOwners.getOrDefault(topic, topic); }

    Map<SymbolRef, Integer> epsgCodes(SymbolRef topic) { return codesByTopic.getOrDefault(topic, Map.of()); }

    List<Column> columns(SymbolRef field) { return columns.get(field); }

    Optional<ObjectDescriptor> alternateClass(SymbolRef topic, SymbolRef type) {
        return topicOwners.containsKey(topic) ? Optional.of(classes.get(type).alternate()) : Optional.empty();
    }

    Map<SymbolRef, TransferValue> alternateValues(SymbolRef type, Map<SymbolRef, TransferValue> values) {
        var slots = classes.get(type).alternateSlots();
        var mapped = new LinkedHashMap<SymbolRef, TransferValue>();
        values.forEach((slot, value) -> mapped.put(slots.get(slot), value));
        return mapped;
    }
}

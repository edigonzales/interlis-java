package guru.interlis.db;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.AttributeSymbol;
import guru.interlis.model.Cardinality;
import guru.interlis.model.CardinalityUpper;
import guru.interlis.model.CollectionType;
import guru.interlis.model.DomainReferenceType;
import guru.interlis.model.EnumElementSymbol;
import guru.interlis.model.ModelKind;
import guru.interlis.model.ModelSymbol;
import guru.interlis.model.OidRule;
import guru.interlis.model.RoleSymbol;
import guru.interlis.model.SemanticGraph;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TopicSymbol;
import guru.interlis.model.TypeRef;
import guru.interlis.transfer.TransferPlan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Declared and derived metadata in the ili2db model metadata table. */
final class DbMetaInfo {
    private static final String PREFIX = "ili2db.ili.";

    record Row(String element, String key, String value) { }

    private DbMetaInfo() { }

    static List<Row> plan(DbMapping mapping, CancellationToken token) {
        var plan = mapping.plan();
        var graph = plan.schema().semanticGraph();
        var rows = new ArrayList<Row>();
        for (var symbol : graph.declarationsInOrder()) {
            token.throwIfCancellationRequested();
            if (symbol instanceof EnumElementSymbol || !dataModel(graph, symbol)) continue;
            var attributes = new LinkedHashMap<String, String>();
            symbol.header().metaAttributes().values().forEach((key, value) -> attributes.put(key, value.text()));
            switch (symbol) {
                case ModelSymbol model -> {
                    model.options().language().ifPresent(value -> attributes.put(PREFIX + "lang", value.value()));
                    model.translation().ifPresent(value -> attributes.put(PREFIX + "translationOf", value.sourceModel()));
                }
                case TopicSymbol topic -> topicAttributes(mapping, topic, attributes);
                case AttributeSymbol attribute -> putCardinality(attributes, "attr", attributeCardinality(plan, attribute));
                case RoleSymbol role -> {
                    putCardinality(attributes, "assoc", role.cardinality());
                    attributes.put(PREFIX + "assocKind", switch (role.aggregation()) {
                        case NONE -> "ASSOCIATE";
                        case AGGREGATION -> "AGGREGATE";
                        case COMPOSITION -> "COMPOSITE";
                    });
                }
                default -> { }
            }
            if (!attributes.isEmpty()) {
                String element = SchemaPlan.qualifiedName(mapping, symbol);
                attributes.forEach((key, value) -> rows.add(new Row(element, key, value)));
            }
        }
        return List.copyOf(rows);
    }

    private static boolean dataModel(SemanticGraph graph, Symbol symbol) {
        while (!(symbol instanceof ModelSymbol)) {
            if (symbol.owner().isEmpty()) return false;
            symbol = graph.symbol(symbol.owner().orElseThrow()).orElseThrow();
        }
        return ((ModelSymbol) symbol).modelKind() == ModelKind.NORMAL && !symbol.name().value().equals("INTERLIS");
    }

    private static void topicAttributes(DbMapping mapping, TopicSymbol topic, Map<String, String> attributes) {
        var plan = mapping.plan();
        var ref = new SymbolRef(plan.snapshotId(), topic.id());
        plan.topic(ref).ifPresent(declaration -> {
            String classes = mapping.objectStorage().stream().filter(type -> type.object().transferable()
                            && !mapping.isEmbeddedLink(type.root())
                            && type.object().topic().filter(parent -> mapping.includesTopic(ref, parent)).isPresent())
                    .map(DbMapping.ObjectStorage::discriminator).distinct().sorted().collect(Collectors.joining(" "));
            attributes.put(PREFIX + "topicClasses", classes);
            declaration.oidRules().stream().filter(OidRule::basket).findFirst().flatMap(OidRule::domain)
                    .flatMap(plan.schema().semanticGraph()::symbol)
                    .ifPresent(domain -> attributes.put(PREFIX + "bidDomain", SchemaPlan.qualifiedName(mapping, domain)));
        });
    }

    private static Cardinality attributeCardinality(TransferPlan plan, AttributeSymbol attribute) {
        var type = plan.type(new TypeRef(plan.snapshotId(), attribute.declaredType())).known().orElseThrow();
        while (type instanceof DomainReferenceType alias) {
            var domain = DbDomains.domain(plan, alias.targetDomain());
            type = plan.type(new TypeRef(plan.snapshotId(), domain.typeId())).known().orElseThrow();
        }
        return type instanceof CollectionType collection ? collection.cardinality() : Cardinality.of(attribute.mandatory() ? 1 : 0, 1);
    }

    private static void putCardinality(Map<String, String> attributes, String kind, Cardinality cardinality) {
        attributes.put(PREFIX + kind + "CardinalityMin", cardinality.lower().toString());
        attributes.put(PREFIX + kind + "CardinalityMax", cardinality.upper() instanceof CardinalityUpper.Finite finite
                ? finite.value().toString() : "*");
    }
}

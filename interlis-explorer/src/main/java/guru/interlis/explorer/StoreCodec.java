package guru.interlis.explorer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import guru.interlis.model.CollectionKind;
import guru.interlis.model.DomainSymbol;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.QualifiedName;
import guru.interlis.model.SymbolRef;
import guru.interlis.source.SourceId;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferGeometry;
import guru.interlis.transfer.TransferGeometry.*;
import guru.interlis.transfer.TransferIdentity;
import guru.interlis.transfer.TransferIdentity.*;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferValue;
import guru.interlis.transfer.TransferValue.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** Maps owned transfer values to JSON, rebinding declaration names in the supplied plan. */
final class StoreCodec {
    private final TransferPlan plan;
    private final Map<SymbolRef, String> slotNames = new HashMap<>();

    StoreCodec(TransferPlan plan) {
        this.plan = plan;
        for (var object : plan.objects()) {
            for (var slot : object.slots()) {
                slotNames.put(slot.symbol(), slot.name().declared().toString());
            }
        }
    }

    JsonArray header(TransferEvent.StartTransfer event) {
        return array(event.source().map(SourceId::value).orElse(null), event.sender().orElse(null),
                event.comment().orElse(null), array(event.models(), model -> array(
                        plan.model(model.model()).orElseThrow().name().declared().toString(),
                        model.languageVersion().name(), model.modelVersion().orElse(null), model.issuer().orElse(null))));
    }

    TransferEvent.StartTransfer header(String json) {
        var data = parseArray(json);
        return new TransferEvent.StartTransfer(optional(data, 0).map(SourceId::new), optional(data, 1),
                optional(data, 2), list(data.get(3), declaration -> {
                    var model = declaration.getAsJsonArray();
                    var ref = resolve(text(model, 0), candidate -> plan.model(candidate).isPresent(), "model");
                    return new TransferEvent.ModelDeclaration(ref, LanguageVersion.valueOf(text(model, 1)),
                            optional(model, 2), optional(model, 3));
                }));
    }

    JsonArray basket(TransferEvent.StartBasket event) {
        return array(basketId(event.id()), topicName(event.topic()), array(event.additionalTopics(),
                ref -> new JsonPrimitive(topicName(ref))), event.consistency().name(), event.stableOid().orElse(null),
                array(event.domainBindings(), binding -> array(domainName(binding.generic()), domainName(binding.concrete()))));
    }

    TransferEvent.StartBasket basket(String json) {
        var data = parseArray(json);
        return new TransferEvent.StartBasket(basketId(data.get(0)), topic(text(data, 1)),
                list(data.get(2), name -> topic(name.getAsString())), TransferEvent.BasketKind.FULL,
                TransferEvent.Consistency.valueOf(text(data, 3)), Optional.empty(), Optional.empty(),
                optional(data, 4), list(data.get(5), binding -> {
                    var domains = binding.getAsJsonArray();
                    return new TransferEvent.DomainBinding(domain(text(domains, 0)), domain(text(domains, 1)));
                }));
    }

    private String topicName(SymbolRef ref) {
        return plan.topic(ref).orElseThrow().name().declared().toString();
    }

    private SymbolRef topic(String name) {
        return resolve(name, candidate -> plan.topic(candidate).isPresent(), "topic");
    }

    private String domainName(SymbolRef ref) {
        var domain = plan.symbol(ref).known().orElseThrow();
        if (!(domain instanceof DomainSymbol)) {
            throw new IllegalArgumentException("Expected a basket domain declaration");
        }
        var names = new ArrayDeque<String>();
        names.addFirst(domain.name().value());
        while (domain.owner().isPresent()) {
            domain = plan.symbol(new SymbolRef(plan.snapshotId(), domain.owner().orElseThrow())).known().orElseThrow();
            names.addFirst(domain.name().value());
        }
        return String.join(".", names);
    }

    private SymbolRef domain(String name) {
        return resolve(name, ref -> plan.symbol(ref).known().orElseThrow() instanceof DomainSymbol, "domain");
    }

    String objectName(SymbolRef type) {
        return plan.object(type).orElseThrow(() -> new IllegalArgumentException("Unknown transfer object type " + type))
                .name().declared().toString();
    }

    JsonArray identity(TransferIdentity id) {
        return array(basketId(id.currentBasket()), id.originalBasket().map(StoreCodec::basketId).orElse(null),
                id.tid().map(tid -> array(tid.value(), tid.origin().name())).orElse(null),
                id.oid().map(ObjectOid::value).orElse(null), id.source().map(SourceId::value).orElse(null));
    }

    TransferIdentity identity(String json) {
        var data = parseArray(json);
        return new TransferIdentity(basketId(data.get(0)),
                nonNull(data.get(1)).map(StoreCodec::basketId),
                nonNull(data.get(2)).map(tid -> new TransferId(text(tid.getAsJsonArray(), 0),
                        IdOrigin.valueOf(text(tid.getAsJsonArray(), 1)))),
                optional(data, 3).map(ObjectOid::new), optional(data, 4).map(SourceId::new));
    }

    JsonObject fields(Structured object, BiConsumer<String, Reference> references) {
        var fields = new JsonObject();
        for (var entry : object.values().entrySet()) {
            String slot = slotNames.get(entry.getKey());
            if (slot == null || !slot(slot).equals(entry.getKey())) {
                throw new IllegalArgumentException("Cannot bind transfer slot " + entry.getKey());
            }
            fields.add(slot, value(entry.getValue(), slot, references));
        }
        return fields;
    }

    Structured structure(String type, String json) {
        return structure(type, JsonParser.parseString(json).getAsJsonObject());
    }

    private Structured structure(String type, JsonObject fields) {
        var ref = resolve(type, candidate -> plan.object(candidate).isPresent(), "object type");
        var values = new LinkedHashMap<SymbolRef, TransferValue>();
        for (var entry : fields.entrySet()) {
            values.put(slot(entry.getKey()), value(entry.getValue()));
        }
        return new Structured(ref, values);
    }

    QualifiedName referenceSlot(String name) {
        slot(name);
        return qualifiedName(name);
    }

    private SymbolRef slot(String name) {
        // Embedded fields also use actual association role declarations from the plan.
        return resolve(name, slotNames::containsKey, "slot");
    }

    private SymbolRef resolve(String name, Predicate<SymbolRef> matches, String kind) {
        var candidates = plan.schema().queries().findQualifiedName(qualifiedName(name)).stream().filter(matches).toList();
        if (candidates.size() != 1) {
            throw new IllegalArgumentException("Cannot uniquely bind stored " + kind + " " + name);
        }
        return candidates.getFirst();
    }

    private JsonArray value(TransferValue value, String slot, BiConsumer<String, Reference> references) {
        return switch (value) {
            case Text text -> array("text", text.value());
            case BooleanValue bool -> array("boolean", bool.value());
            case Enumeration enumeration -> array("enumeration", array(enumeration.path(), JsonPrimitive::new));
            case NumberValue number -> array("number", number.lexeme(), number.exactValue().toString());
            case Structured structure -> array("structure", objectName(structure.type()), fields(structure, references));
            case Repeated repeated -> array("repeated", repeated.kind().name(),
                    array(repeated.values(), item -> value(item, slot, references)));
            case Reference reference -> {
                references.accept(slot, reference);
                yield reference(reference);
            }
            case Binary binary -> array("binary", Base64.getEncoder().encodeToString(binary.bytes()));
            case XmlFragment fragment -> {
                var namespaces = new JsonObject();
                fragment.namespaceBindings().forEach(namespaces::addProperty);
                yield array("xml", fragment.xml(), namespaces);
            }
            case Oid oid -> array("oid", oid.lexeme());
            case ClassName name -> array("class", name.name().toString());
            case AttributePath name -> array("path", name.name().toString());
            case Undefined ignored -> array("undefined");
            case TransferGeometry geometry -> geometry(geometry);
        };
    }

    private TransferValue value(JsonElement json) {
        var data = json.getAsJsonArray();
        return switch (text(data, 0)) {
            case "text" -> new Text(text(data, 1));
            case "boolean" -> new BooleanValue(data.get(1).getAsBoolean());
            case "enumeration" -> new Enumeration(list(data.get(1), JsonElement::getAsString));
            case "number" -> new NumberValue(text(data, 1), new BigDecimal(text(data, 2)));
            case "structure" -> structure(text(data, 1), data.get(2).getAsJsonObject());
            case "repeated" -> new Repeated(CollectionKind.valueOf(text(data, 1)), list(data.get(2), this::value));
            case "reference" -> reference(data);
            case "binary" -> new Binary(Base64.getDecoder().decode(text(data, 1)));
            case "xml" -> {
                var namespaces = new LinkedHashMap<String, String>();
                data.get(2).getAsJsonObject().entrySet().forEach(entry ->
                        namespaces.put(entry.getKey(), entry.getValue().getAsString()));
                yield new XmlFragment(text(data, 1), namespaces);
            }
            case "oid" -> new Oid(text(data, 1));
            case "class" -> new ClassName(qualifiedName(text(data, 1)));
            case "path" -> new AttributePath(qualifiedName(text(data, 1)));
            case "undefined" -> Undefined.INSTANCE;
            case "coordinate" -> coordinate(data.get(1));
            case "multicoordinate" -> new MultiCoordinate(list(data.get(1), StoreCodec::coordinate));
            case "polyline" -> polyline(data.get(1));
            case "multipolyline" -> new MultiPolyline(list(data.get(1), StoreCodec::polyline));
            case "surface" -> surface(data.get(1));
            case "multisurface" -> new MultiSurface(list(data.get(1), StoreCodec::surface));
            default -> throw new IllegalArgumentException("Unknown stored transfer value " + text(data, 0));
        };
    }

    static JsonArray reference(Reference reference) {
        String origin = reference.target() instanceof TransferId tid ? tid.origin().name() : null;
        return array("reference", reference.target().value(), origin,
                reference.targetBasket().map(StoreCodec::basketId).orElse(null), reference.external(),
                reference.orderPosition().map(BigInteger::toString).orElse(null));
    }

    static Reference reference(String json) {
        return reference(parseArray(json));
    }

    private static Reference reference(JsonArray data) {
        String value = text(data, 1);
        ObjectKey target = optional(data, 2).<ObjectKey>map(origin -> new TransferId(value, IdOrigin.valueOf(origin)))
                .orElseGet(() -> new ObjectOid(value));
        return new Reference(target, nonNull(data.get(3)).map(StoreCodec::basketId),
                data.get(4).getAsBoolean(), optional(data, 5).map(BigInteger::new));
    }

    private static JsonArray geometry(TransferGeometry geometry) {
        return switch (geometry) {
            case Coordinate coordinate -> array("coordinate", coordinate(coordinate));
            case MultiCoordinate multi -> array("multicoordinate", array(multi.coordinates(), StoreCodec::coordinate));
            case Polyline line -> array("polyline", polyline(line));
            case MultiPolyline multi -> array("multipolyline", array(multi.lines(), StoreCodec::polyline));
            case Surface surface -> array("surface", surface(surface));
            case MultiSurface multi -> array("multisurface", array(multi.surfaces(), StoreCodec::surface));
        };
    }

    private static JsonArray coordinate(Coordinate coordinate) {
        return array(coordinate.ordinates(), StoreCodec::number);
    }

    private static Coordinate coordinate(JsonElement json) {
        return new Coordinate(list(json, value -> number(value.getAsJsonArray())));
    }

    private static JsonArray number(NumberValue value) {
        return array(value.lexeme(), value.exactValue().toString());
    }

    private static NumberValue number(JsonArray value) {
        return new NumberValue(text(value, 0), new BigDecimal(text(value, 1)));
    }

    private static JsonArray polyline(Polyline line) {
        if (line.lineAttributes().isPresent()) {
            throw new IllegalArgumentException("The XTF 2.4 store profile does not support LINEATTR payloads");
        }
        return array(line.parts(), part -> array(coordinate(part.start()), array(part.segments(), StoreCodec::segment)));
    }

    private static Polyline polyline(JsonElement json) {
        return new Polyline(list(json, element -> {
            var part = element.getAsJsonArray();
            return new CurvePart(coordinate(part.get(0)), list(part.get(1), StoreCodec::segment));
        }));
    }

    private static JsonArray segment(Segment segment) {
        return switch (segment) {
            case Straight straight -> array(coordinate(straight.end()));
            case Arc arc -> array(coordinate(arc.end()), coordinate(arc.support()), arc.radius()
                    .map(radius -> array(number(radius.sourceValue()), radius.convention().name())).orElse(null));
        };
    }

    private static Segment segment(JsonElement json) {
        var data = json.getAsJsonArray();
        var end = coordinate(data.get(0));
        if (data.size() == 1) {
            return new Straight(end);
        }
        return new Arc(coordinate(data.get(1)), end, nonNull(data.get(2)).map(value -> {
            var radius = value.getAsJsonArray();
            return new ArcRadius(number(radius.get(0).getAsJsonArray()), RadiusConvention.valueOf(text(radius, 1)));
        }));
    }

    private static JsonArray surface(Surface surface) {
        return array(ring(surface.shell()), array(surface.holes(), StoreCodec::ring));
    }

    private static Surface surface(JsonElement json) {
        var data = json.getAsJsonArray();
        return new Surface(ring(data.get(0)), list(data.get(1), StoreCodec::ring));
    }

    private static JsonArray ring(Ring ring) {
        return array(ring.lines(), StoreCodec::polyline);
    }

    private static Ring ring(JsonElement json) {
        return new Ring(list(json, StoreCodec::polyline));
    }

    private static JsonArray basketId(BasketId id) {
        return array(id.value(), id.origin().name());
    }

    private static BasketId basketId(JsonElement json) {
        var data = json.getAsJsonArray();
        return new BasketId(text(data, 0), IdOrigin.valueOf(text(data, 1)));
    }

    static QualifiedName qualifiedName(String name) {
        return QualifiedName.of(name.split("\\.", -1));
    }

    private static JsonArray parseArray(String json) {
        return JsonParser.parseString(json).getAsJsonArray();
    }

    private static String text(JsonArray data, int index) {
        return data.get(index).getAsString();
    }

    private static Optional<String> optional(JsonArray data, int index) {
        return nonNull(data.get(index)).map(JsonElement::getAsString);
    }

    private static Optional<JsonElement> nonNull(JsonElement json) {
        return json.isJsonNull() ? Optional.empty() : Optional.of(json);
    }

    private static <T> List<T> list(JsonElement elements, Function<JsonElement, T> decode) {
        return elements.getAsJsonArray().asList().stream().map(decode).toList();
    }

    private static <T> JsonArray array(List<T> values, Function<T, JsonElement> encode) {
        var data = new JsonArray(values.size());
        values.forEach(value -> data.add(encode.apply(value)));
        return data;
    }

    private static JsonArray array(Object... values) {
        var data = new JsonArray(values.length);
        for (Object value : values) {
            data.add(switch (value) {
                case null -> JsonNull.INSTANCE;
                case JsonElement json -> json;
                case String text -> new JsonPrimitive(text);
                case Boolean bool -> new JsonPrimitive(bool);
                default -> throw new IllegalArgumentException("Unsupported JSON component " + value.getClass());
            });
        }
        return data;
    }
}

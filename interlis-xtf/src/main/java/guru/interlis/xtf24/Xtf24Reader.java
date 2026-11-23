package guru.interlis.xtf24;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TypeRef;
import guru.interlis.source.SourceId;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferEvent.*;
import guru.interlis.transfer.TransferIdentity;
import guru.interlis.transfer.TransferIdentity.*;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferReader;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static guru.interlis.xtf24.Xtf24Exception.Reason.*;
import static guru.interlis.xtf24.Xtf24Wire.ili;
import static javax.xml.stream.XMLStreamConstants.*;

/**
 * Pull reader for XTF 2.4 FULL transfers. Returned events are immutable. Use
 * from one caller and close the reader when done. The caller owns the input.
 * Limits bound buffering and the transfer-wide identity index. Cancellation
 * is observed during parsing and cannot interrupt a blocked host read.
 * Decoding does not validate model constraints.
 */
public final class Xtf24Reader extends TransferReader {
    private enum Position { BEFORE_TRANSFER, BETWEEN_BASKETS, IN_BASKET, DONE }

    private record BasketContext(StartBasket event, Set<SymbolRef> activeTypes,
                                 Map<SymbolRef, TypeRef> concreteTypes) {}

    private record HeaderModels(List<String> names, long remaining) {}

    private final TransferPlan plan;
    private final Optional<SourceId> source;
    private final Xtf24Limits limits;
    private final Xtf24Input input;
    private final Xtf24Layout layout;
    private final Xtf24ReadProfile profile;
    private final Xtf24Names names;
    private final Xtf24ValueReader values;
    private final Xtf24Domains domains;
    private final Xtf24IdentityScope identities;
    private Position position = Position.BEFORE_TRANSFER;
    private Set<SymbolRef> coveredModels = Set.of();
    private Set<SymbolRef> structureTypes = Set.of();
    private BasketContext basket;

    /** Creates a reader with default limits, without reading the nonnull input. */
    public Xtf24Reader(TransferPlan plan, InputStream input) {
        this(plan, input, Optional.empty());
    }

    /** Creates a reader with an optional source identifier and default limits. */
    public Xtf24Reader(TransferPlan plan, InputStream input, Optional<SourceId> source) {
        this(plan, input, source, Xtf24Limits.defaults());
    }

    /** Creates a reader with nonnull arguments. Construction performs no stream I/O. */
    public Xtf24Reader(TransferPlan plan, InputStream input, Optional<SourceId> source, Xtf24Limits limits) {
        this(plan, input, source, limits, Xtf24ReadProfile.STANDARD);
    }

    /** Creates a reader with an explicit input profile and nonnull arguments. Construction performs no stream I/O. */
    public Xtf24Reader(TransferPlan plan, InputStream input, Optional<SourceId> source, Xtf24Limits limits,
                       Xtf24ReadProfile profile) {
        super(plan, true);
        this.plan = plan;
        this.source = Objects.requireNonNull(source, "source");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.input = new Xtf24Input(input, limits);
        layout = new Xtf24Layout(plan);
        names = layout.names();
        values = new Xtf24ValueReader(plan, this.input, layout, limits, profile);
        domains = new Xtf24Domains(plan, names);
        identities = new Xtf24IdentityScope(limits);
    }

    @Override
    protected Optional<TransferEvent> readEvent(CancellationToken cancellation) {
        input.begin(cancellation);
        try {
            return switch (position) {
                case BEFORE_TRANSFER -> Optional.of(startTransfer());
                case BETWEEN_BASKETS -> Optional.of(basketOrEnd());
                case IN_BASKET -> Optional.of(objectOrEnd());
                case DONE -> Optional.empty();
            };
        } catch (XMLStreamException failure) {
            throw input.translate(failure);
        } catch (Xtf24Exception failure) {
            throw input.locate(failure);
        } finally {
            input.finish();
        }
    }

    /**
     * Reads the immutable list of declared model names in wire order, using
     * the same XML restrictions and default limits as an XTF reader.
     * The input and cancellation token must be nonnull. The input remains
     * open at an unspecified byte position and must be reopened for decoding.
     *
     * @throws Xtf24Exception if the model declarations cannot be read
     */
    public static List<String> readModelNames(InputStream stream, CancellationToken cancellation) {
        var limits = Xtf24Limits.defaults();
        try (var input = new Xtf24Input(stream, limits)) {
            input.begin(Objects.requireNonNull(cancellation, "cancellation"));
            try {
                return modelNames(input, limits).names();
            } catch (XMLStreamException failure) {
                throw input.translate(failure);
            } finally {
                input.finish();
            }
        }
    }

    private static HeaderModels modelNames(Xtf24Input input, Xtf24Limits limits) throws XMLStreamException {
        input.open();
        input.nextStart(ili("transfer"));
        input.requireAttributes(Set.of(new QName(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "schemaLocation")));
        input.nextStart(ili("headersection"));
        input.requireAttributes(Set.of());
        input.nextStart(ili("models"));
        input.requireAttributes(Set.of());
        Set<String> declaredNames = new java.util.LinkedHashSet<>();
        long remaining = limits.maxObjectCodeUnits();
        while (input.nextTag() == START_ELEMENT) {
            input.require(START_ELEMENT, ili("model"));
            if (declaredNames.size() == Xtf24Wire.MAX_MODELS) {
                throw input.error(LIMIT_EXCEEDED, "Too many header models");
            }
            input.requireAttributes(Set.of());
            String modelText = input.text();
            remaining = chargeHeader(input, remaining, modelText);
            String modelName = modelText.trim();
            if (modelName.length() > limits.maxIdentifierCodeUnits()) {
                throw input.error(LIMIT_EXCEEDED, "Header model name is too long");
            }
            if (modelName.isEmpty()) {
                throw input.error(INVALID_WIRE, "Empty header model name");
            }
            if (!declaredNames.add(modelName)) {
                throw input.error(INVALID_WIRE, "Repeated header model");
            }
        }
        input.require(END_ELEMENT, ili("models"));
        if (declaredNames.isEmpty()) {
            throw input.error(INVALID_WIRE, "XTF header needs at least one model");
        }
        return new HeaderModels(List.copyOf(declaredNames), remaining);
    }

    private StartTransfer startTransfer() throws XMLStreamException {
        var header = modelNames(input, limits);
        List<ModelDeclaration> models = new ArrayList<>();
        Set<SymbolRef> declared = new HashSet<>();
        for (var model : names.models(header.names())) {
            if (!declared.add(model.symbol())) {
                throw input.error(INVALID_WIRE, "Repeated header model");
            }
            models.add(new ModelDeclaration(model.symbol(), model.languageVersion(),
                    Optional.empty(), Optional.empty()));
        }

        input.nextTag();
        Optional<String> sender = optionalText(ili("sender"));
        Optional<String> comment = optionalText(ili("comment"));
        long remaining = header.remaining();
        if (sender.isPresent()) {
            remaining = chargeHeader(input, remaining, sender.orElseThrow());
        }
        if (comment.isPresent()) {
            chargeHeader(input, remaining, comment.orElseThrow());
        }
        if (input.at(START_ELEMENT, ili("extensions"))) {
            input.requireAttributes(Set.of());
            if (input.nextTag() != END_ELEMENT) {
                throw input.error(UNSUPPORTED, "Nonempty XTF header extensions");
            }
            input.nextTag();
        }
        input.require(END_ELEMENT, ili("headersection"));
        input.nextStart(ili("datasection"));
        input.requireAttributes(Set.of());
        coveredModels = plan.modelImportClosure(declared);
        structureTypes = names.structureTypes(coveredModels);
        position = Position.BETWEEN_BASKETS;
        return new StartTransfer(source, sender, comment, models);
    }

    private TransferEvent basketOrEnd() throws XMLStreamException {
        if (input.nextTag() == END_ELEMENT) {
            input.require(END_ELEMENT, ili("datasection"));
            input.nextTag();
            input.require(END_ELEMENT, ili("transfer"));
            if (input.nextTag() != END_DOCUMENT) {
                throw input.error(INVALID_WIRE, "Unexpected content after transfer");
            }
            position = Position.DONE;
            release();
            return new EndTransfer();
        }
        if (input.cursor().getEventType() != START_ELEMENT) {
            throw input.error(INVALID_WIRE, "Expected an XTF basket");
        }
        var topic = names.topic(input.cursor().getName(), coveredModels);
        if (topic.abstractTopic()) {
            throw input.error(INVALID_WIRE, "Basket topic is abstract");
        }
        String kind = attribute("kind").orElse("FULL");
        if (kind.equals("INITIAL") || kind.equals("UPDATE")
                || attribute("startstate").isPresent() || attribute("endstate").isPresent()) {
            throw input.error(UNSUPPORTED, "Incremental basket metadata");
        }
        if (!kind.equals("FULL")) {
            throw input.error(INVALID_WIRE, "Unrecognized transfer kind");
        }
        input.requireAttributes(Set.of(ili("bid"), ili("kind"), ili("consistency"), ili("domains")));
        String bid = requiredAttribute("bid");
        identities.add(bid);
        var consistency = switch (attribute("consistency").orElse("COMPLETE")) {
            case "COMPLETE" -> Consistency.COMPLETE;
            case "INCOMPLETE" -> Consistency.INCOMPLETE;
            default -> throw input.error(INVALID_WIRE, "Unrecognized basket consistency");
        };
        var bindings = domains.parse(topic, attribute("domains").orElse(""));
        var stableOid = topic.oidRules().stream().anyMatch(rule -> rule.basket())
                ? Optional.of(bid) : Optional.<String>empty();
        var start = new StartBasket(new BasketId(bid, IdOrigin.SOURCE), topic.symbol(), List.of(),
                BasketKind.FULL, consistency, Optional.empty(), Optional.empty(), stableOid, bindings);
        values.basketConsistency(consistency);
        basket = new BasketContext(start, Set.copyOf(topic.objectTypes()), domains.resolve(topic, bindings));
        position = Position.IN_BASKET;
        return start;
    }

    private TransferEvent objectOrEnd() throws XMLStreamException {
        if (input.nextTag() == END_ELEMENT) {
            var end = new EndBasket(basket.event().id());
            basket = null;
            values.basketConsistency(Consistency.COMPLETE);
            position = Position.BETWEEN_BASKETS;
            return end;
        }
        if (input.cursor().getEventType() != START_ELEMENT) {
            throw input.error(INVALID_WIRE, "Expected an object or the end of its basket");
        }
        if (input.at(START_ELEMENT, ili("delete")) || attribute("operation").isPresent()) {
            throw input.error(UNSUPPORTED, "Incremental object operation");
        }
        input.beginObject();
        var type = names.objectOccurrence(input.cursor().getName(), basket.activeTypes(), profile);
        if (layout.embedded(type).isPresent()) {
            throw input.error(INVALID_WIRE, "Association belongs in an embedded XTF field");
        }
        input.requireAttributes(Set.of(ili("tid")));
        Optional<String> value = attribute("tid");
        if (value.isPresent() != layout.hasTid(type)) {
            throw input.error(INVALID_WIRE, "Object TID does not match its wire type");
        }
        value.ifPresent(identities::add);
        var tid = value.map(id -> new TransferId(id, IdOrigin.SOURCE));
        var oid = layout.identified(type) ? value.map(ObjectOid::new) : Optional.<ObjectOid>empty();
        var data = values.read(type, basket.concreteTypes(), structureTypes, basket.activeTypes());
        input.endObject();
        var identity = new TransferIdentity(basket.event().id(), Optional.empty(), tid, oid, source);
        var object = new TransferObject(identity, data);
        return new ObjectEvent(object, Optional.empty(), Consistency.COMPLETE);
    }

    private static long chargeHeader(Xtf24Input input, long remaining, String text) {
        if (remaining < text.length()) {
            throw input.error(LIMIT_EXCEEDED, "XTF header exceeds its aggregate text limit");
        }
        return remaining - text.length();
    }

    private Optional<String> optionalText(QName name) throws XMLStreamException {
        if (!input.at(START_ELEMENT, name)) {
            return Optional.empty();
        }
        input.requireAttributes(Set.of());
        String value = input.text();
        input.nextTag();
        return Optional.of(value);
    }

    private Optional<String> attribute(String name) {
        return Optional.ofNullable(input.cursor().getAttributeValue(Xtf24Wire.NAMESPACE, name));
    }

    private String requiredAttribute(String name) {
        return attribute(name).orElseThrow(() -> input.error(INVALID_WIRE, "Missing attribute " + name));
    }

    @Override
    protected void onFailure() {
        release();
    }

    @Override
    protected void release() {
        coveredModels = Set.of();
        structureTypes = Set.of();
        basket = null;
        identities.clear();
        values.basketConsistency(Consistency.COMPLETE);
        input.close();
    }
}

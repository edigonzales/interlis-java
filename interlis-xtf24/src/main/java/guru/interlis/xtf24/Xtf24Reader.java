package guru.interlis.xtf24;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TypeRef;
import guru.interlis.source.SourceId;
import guru.interlis.transfer.ModelDescriptor;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferEvent.*;
import guru.interlis.transfer.TransferIdentity;
import guru.interlis.transfer.TransferIdentity.*;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferReader;
import guru.interlis.transfer.TransferValue;

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

    private final TransferPlan plan;
    private final Optional<SourceId> source;
    private final Xtf24Limits limits;
    private final Xtf24Input input;
    private final Xtf24Names names;
    private final Xtf24Domains domains;
    private final Xtf24IdentityScope identities;
    private Position position = Position.BEFORE_TRANSFER;
    private Set<SymbolRef> coveredModels = Set.of();
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
        super(plan, true);
        this.plan = plan;
        this.source = Objects.requireNonNull(source, "source");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.input = new Xtf24Input(input, limits);
        names = new Xtf24Names(plan);
        domains = new Xtf24Domains(plan);
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

    private StartTransfer startTransfer() throws XMLStreamException {
        input.open();
        nextStart(ili("transfer"));
        attributes(Set.of(new QName(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "schemaLocation")));
        nextStart(ili("headersection"));
        attributes(Set.of());
        nextStart(ili("models"));
        attributes(Set.of());
        List<ModelDeclaration> models = new ArrayList<>();
        Set<SymbolRef> declared = new HashSet<>();
        Set<String> declaredNames = new HashSet<>();
        long remaining = limits.maxObjectCodeUnits();
        while (input.nextTag() == START_ELEMENT) {
            require(START_ELEMENT, ili("model"));
            if (models.size() == Xtf24Wire.MAX_MODELS) {
                throw input.error(LIMIT_EXCEEDED, "Too many header models");
            }
            attributes(Set.of());
            String modelText = input.text();
            remaining = chargeHeader(remaining, modelText);
            String modelName = modelText.trim();
            if (modelName.length() > limits.maxIdentifierCodeUnits()) {
                throw input.error(LIMIT_EXCEEDED, "Header model name is too long");
            }
            var model = findModel(modelName);
            if (!declared.add(model.symbol()) || !declaredNames.add(modelName)) {
                throw input.error(INVALID_WIRE, "Repeated header model");
            }
            models.add(new ModelDeclaration(model.symbol(), model.languageVersion(), Optional.empty(), Optional.empty()));
        }
        require(END_ELEMENT, ili("models"));
        if (models.isEmpty()) {
            throw input.error(INVALID_WIRE, "XTF header needs at least one model");
        }
        input.nextTag();
        Optional<String> sender = optionalText(ili("sender"));
        Optional<String> comment = optionalText(ili("comment"));
        if (sender.isPresent()) {
            remaining = chargeHeader(remaining, sender.orElseThrow());
        }
        if (comment.isPresent()) {
            chargeHeader(remaining, comment.orElseThrow());
        }
        if (at(START_ELEMENT, ili("extensions"))) {
            attributes(Set.of());
            if (input.nextTag() != END_ELEMENT) {
                throw input.error(UNSUPPORTED, "Nonempty XTF header extensions");
            }
            input.nextTag();
        }
        require(END_ELEMENT, ili("headersection"));
        nextStart(ili("datasection"));
        attributes(Set.of());
        coveredModels = plan.modelImportClosure(declared);
        position = Position.BETWEEN_BASKETS;
        return new StartTransfer(source, sender, comment, models);
    }

    private TransferEvent basketOrEnd() throws XMLStreamException {
        if (input.nextTag() == END_ELEMENT) {
            require(END_ELEMENT, ili("datasection"));
            input.nextTag();
            require(END_ELEMENT, ili("transfer"));
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
        attributes(Set.of(ili("bid"), ili("kind"), ili("consistency"), ili("domains")));
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
        basket = new BasketContext(start, Set.copyOf(topic.objectTypes()), domains.resolve(topic, bindings));
        position = Position.IN_BASKET;
        return start;
    }

    private TransferEvent objectOrEnd() throws XMLStreamException {
        if (input.nextTag() == END_ELEMENT) {
            var end = new EndBasket(basket.event().id());
            basket = null;
            position = Position.BETWEEN_BASKETS;
            return end;
        }
        if (input.cursor().getEventType() != START_ELEMENT) {
            throw input.error(INVALID_WIRE, "Expected an object or the end of its basket");
        }
        if (at(START_ELEMENT, ili("delete")) || attribute("operation").isPresent()) {
            throw input.error(UNSUPPORTED, "Incremental object operation");
        }
        input.beginObject();
        var type = names.object(input.cursor().getName(), basket.activeTypes());
        attributes(Set.of(ili("tid")));
        Optional<String> value = attribute("tid");
        if (value.isPresent() != Xtf24Wire.hasTid(type)) {
            throw input.error(INVALID_WIRE, "Object TID does not match its wire type");
        }
        value.ifPresent(identities::add);
        var tid = value.map(id -> new TransferId(id, IdOrigin.SOURCE));
        var oid = Xtf24Wire.hasStableOid(type) ? value.map(ObjectOid::new) : Optional.<ObjectOid>empty();
        if (input.nextTag() != END_ELEMENT) {
            throw input.error(UNSUPPORTED, "Object payload is outside the current XTF 2.4 codec");
        }
        input.endObject();
        var identity = new TransferIdentity(basket.event().id(), Optional.empty(), tid, oid, source);
        var object = new TransferObject(identity, new TransferValue.Structured(type.symbol(), Map.of()));
        return new ObjectEvent(object, Optional.empty(), Consistency.COMPLETE);
    }

    private ModelDescriptor findModel(String name) {
        var candidates = plan.models().stream().filter(model -> names.modelName(model).equals(name)).toList();
        var roots = candidates.stream().filter(model -> plan.rootModels().contains(model.symbol())).toList();
        var active = roots.isEmpty() ? candidates : roots;
        if (active.size() != 1) {
            throw input.error(INVALID_WIRE, "Header model name is unknown or ambiguous");
        }
        return active.getFirst();
    }

    private long chargeHeader(long remaining, String text) {
        if (remaining < text.length()) {
            throw input.error(LIMIT_EXCEEDED, "XTF header exceeds its aggregate text limit");
        }
        return remaining - text.length();
    }

    private Optional<String> optionalText(QName name) throws XMLStreamException {
        if (!at(START_ELEMENT, name)) {
            return Optional.empty();
        }
        attributes(Set.of());
        String value = input.text();
        input.nextTag();
        return Optional.of(value);
    }

    private void nextStart(QName name) throws XMLStreamException {
        input.nextTag();
        require(START_ELEMENT, name);
    }

    private void require(int token, QName name) {
        if (!at(token, name)) {
            throw input.error(INVALID_WIRE, "Expected " + name);
        }
    }

    private boolean at(int token, QName name) {
        var cursor = input.cursor();
        return cursor.getEventType() == token && cursor.getName().equals(name);
    }

    private Optional<String> attribute(String name) {
        return Optional.ofNullable(input.cursor().getAttributeValue(Xtf24Wire.NAMESPACE, name));
    }

    private String requiredAttribute(String name) {
        return attribute(name).orElseThrow(() -> input.error(INVALID_WIRE, "Missing attribute " + name));
    }

    private void attributes(Set<QName> supported) {
        var cursor = input.cursor();
        for (int i = 0; i < cursor.getAttributeCount(); i++) {
            if (!supported.contains(cursor.getAttributeName(i))) {
                throw input.error(UNSUPPORTED, "Attribute is outside the XTF 2.4 wire profile");
            }
        }
    }

    @Override
    protected void onFailure() {
        release();
    }

    @Override
    protected void release() {
        coveredModels = Set.of();
        basket = null;
        identities.clear();
        input.close();
    }
}

package guru.interlis.xtf24;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TypeRef;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferEvent.*;
import guru.interlis.transfer.TransferIdentity.ObjectOid;
import guru.interlis.transfer.TransferIdentity.TransferId;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferWriter;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Objects;

import static guru.interlis.xtf24.Xtf24Exception.Reason.*;
import static guru.interlis.xtf24.Xtf24Wire.ili;

/**
 * Push writer for XTF 2.4 FULL transfers. Use from one caller. The caller owns
 * the destination. Only a successful EndTransfer completes and flushes the XML
 * document. Limits bound the writer's additional work and identity index.
 * Writing does not validate model constraints. Negative radii are rejected
 * because the neutral value does not distinguish invalid 2.4 data from the
 * signed-direction convention of XTF 2.3. Curve and boundary partitions and
 * LINEATTR metadata require an explicit external transformation.
 */
public final class Xtf24Writer extends TransferWriter {
    private final TransferPlan plan;
    private final Xtf24Limits limits;
    private final Xtf24Output output;
    private final Xtf24Layout layout;
    private final Xtf24Names names;
    private final Xtf24ValueWriter values;
    private final Xtf24Domains domains;
    private final Xtf24IdentityScope identities;
    private Set<SymbolRef> activeTypes = Set.of();
    private Set<SymbolRef> structureTypes = Set.of();
    private Map<SymbolRef, TypeRef> concreteTypes = Map.of();

    /** Creates a writer with default limits, without writing to the nonnull destination. */
    public Xtf24Writer(TransferPlan plan, OutputStream output) {
        this(plan, output, Xtf24Limits.defaults());
    }

    /** Creates a writer with nonnull arguments. Construction performs no stream I/O. */
    public Xtf24Writer(TransferPlan plan, OutputStream output, Xtf24Limits limits) {
        super(plan, true);
        this.plan = plan;
        this.limits = Objects.requireNonNull(limits, "limits");
        this.output = new Xtf24Output(output, limits);
        layout = new Xtf24Layout(plan);
        names = layout.names();
        values = new Xtf24ValueWriter(plan, this.output, layout, limits);
        domains = new Xtf24Domains(plan, names);
        identities = new Xtf24IdentityScope(limits);
    }

    @Override
    protected void writeEvent(TransferEvent event, CancellationToken cancellation) {
        output.begin(cancellation);
        try {
            switch (event) {
                case StartTransfer start -> startTransfer(start);
                case StartBasket start -> startBasket(start);
                case ObjectEvent object -> writeObject(object);
                case EndBasket ignored -> {
                    output.end();
                    concreteTypes = Map.of();
                    activeTypes = Set.of();
                    values.basketConsistency(Consistency.COMPLETE);
                }
                case EndTransfer ignored -> {
                    output.end();
                    output.end();
                    output.endDocument();
                    release();
                }
            }
            cancellation.throwIfCancellationRequested();
        } catch (XMLStreamException failure) {
            throw Xtf24Output.translate(failure);
        } finally {
            output.finish();
        }
    }

    private void startTransfer(StartTransfer start) throws XMLStreamException {
        if (start.models().isEmpty()) {
            throw Xtf24Wire.failure(INVALID_WIRE, "XTF header needs at least one model");
        }
        if (start.models().size() > Xtf24Wire.MAX_MODELS) {
            throw Xtf24Wire.failure(LIMIT_EXCEEDED, "Too many header models");
        }
        var modelNames = new ArrayList<String>();
        var unique = new HashSet<String>();
        long remaining = limits.maxObjectCodeUnits() - start.sender().orElse("").length()
                - start.comment().orElse("").length();
        var declared = start.models().stream().map(ModelDeclaration::model).collect(Collectors.toSet());
        for (var declaration : start.models()) {
            if (declaration.modelVersion().isPresent() || declaration.issuer().isPresent()) {
                throw Xtf24Wire.failure(UNSUPPORTED, "XTF 2.4 has no model version or issuer header field");
            }
            var model = plan.model(declaration.model()).orElseThrow();
            String canonical = names.modelName(model);
            boolean originalAlsoDeclared = declared.contains(model.name().canonical());
            String name = !model.symbol().equals(model.name().canonical())
                    && (originalAlsoDeclared || !names.model(canonical).symbol().equals(model.symbol()))
                    ? model.name().declared().last() : canonical;
            if (name.length() > limits.maxIdentifierCodeUnits()) {
                throw Xtf24Wire.failure(LIMIT_EXCEEDED, "Header model name is too long");
            }
            if (!unique.add(name)) {
                throw Xtf24Wire.failure(INVALID_WIRE, "Repeated header model");
            }
            modelNames.add(name);
            remaining -= name.length();
            if (remaining < 0) {
                throw Xtf24Wire.failure(LIMIT_EXCEEDED, "XTF header exceeds its aggregate text limit");
            }
        }
        structureTypes = names.structureTypes(plan.modelImportClosure(start.models().stream()
                .map(ModelDeclaration::model).collect(Collectors.toSet())));
        output.open();
        output.start(ili("transfer"));
        output.start(ili("headersection"));
        output.start(ili("models"));
        for (String name : modelNames) {
            writeText(ili("model"), name);
        }
        output.end();
        if (start.sender().isPresent()) {
            writeText(ili("sender"), start.sender().orElseThrow());
        }
        if (start.comment().isPresent()) {
            writeText(ili("comment"), start.comment().orElseThrow());
        }
        output.end();
        output.start(ili("datasection"));
    }

    private void startBasket(StartBasket start) throws XMLStreamException {
        if (start.kind() != BasketKind.FULL || start.startState().isPresent() || start.endState().isPresent()) {
            throw Xtf24Wire.failure(UNSUPPORTED, "Incremental basket metadata");
        }
        if (!start.additionalTopics().isEmpty()) {
            throw Xtf24Wire.failure(UNSUPPORTED, "XTF 2.4 has no additional topic field");
        }
        if (start.consistency() != Consistency.COMPLETE && start.consistency() != Consistency.INCOMPLETE) {
            throw Xtf24Wire.failure(UNSUPPORTED, "Basket consistency is outside the XTF 2.4 profile");
        }
        var topic = plan.topic(start.topic()).orElseThrow();
        boolean stable = topic.oidRules().stream().anyMatch(rule -> rule.basket());
        String bid = start.id().value();
        if (start.stableOid().filter(oid -> !stable || !oid.equals(bid)).isPresent()) {
            throw Xtf24Wire.failure(UNSUPPORTED, "Independent stable basket identity");
        }
        concreteTypes = domains.resolve(topic, start.domainBindings());
        activeTypes = Set.copyOf(topic.objectTypes());
        values.basketConsistency(start.consistency());
        identities.add(bid);
        output.start(names.topicName(topic));
        output.attribute(ili("bid"), bid);
        if (start.consistency() == Consistency.INCOMPLETE) {
            output.attribute(ili("consistency"), "INCOMPLETE");
        }
        if (!start.domainBindings().isEmpty()) {
            output.attribute(ili("domains"), domains.format(start.domainBindings()));
        }
    }

    private void writeObject(ObjectEvent occurrence) throws XMLStreamException {
        var object = occurrence.object();
        var type = plan.object(object.type()).orElseThrow();
        var identity = object.identity();
        if (layout.embedded(type).isPresent()) {
            throw Xtf24Wire.failure(INVALID_WIRE, "Association belongs in an embedded XTF field");
        }
        if (occurrence.operation().isPresent() || occurrence.consistency() != Consistency.COMPLETE) {
            throw Xtf24Wire.failure(UNSUPPORTED, "Object operation or consistency metadata");
        }
        var tid = identity.tid().map(TransferId::value);
        if (tid.isPresent() != layout.hasTid(type)) {
            throw Xtf24Wire.failure(INVALID_WIRE, "Object TID does not match its wire type");
        }
        if (identity.originalBasket().filter(original -> !original.value().equals(identity.currentBasket().value())).isPresent()
                || identity.oid().map(ObjectOid::value)
                .filter(oid -> !layout.identified(type) || tid.filter(oid::equals).isEmpty()).isPresent()) {
            throw Xtf24Wire.failure(UNSUPPORTED, "Independent original basket or stable object identity");
        }
        tid.ifPresent(identities::add);
        output.beginObject();
        output.start(names.objectName(type));
        if (tid.isPresent()) {
            output.attribute(ili("tid"), tid.orElseThrow());
        }
        values.write(type, object.data(), concreteTypes, structureTypes, activeTypes);
        output.end();
    }

    private void writeText(QName name, String value) throws XMLStreamException {
        output.start(name);
        output.text(value);
        output.end();
    }

    @Override
    protected void onFailure() {
        release();
    }

    @Override
    protected void release() {
        identities.clear();
        values.basketConsistency(Consistency.COMPLETE);
        activeTypes = Set.of();
        structureTypes = Set.of();
        concreteTypes = Map.of();
        output.close();
    }
}

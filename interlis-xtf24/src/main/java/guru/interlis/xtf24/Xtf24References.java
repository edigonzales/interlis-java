package guru.interlis.xtf24;

import guru.interlis.transfer.TransferIdentity;
import guru.interlis.transfer.TransferValue.Reference;

import javax.xml.stream.XMLStreamException;
import java.math.BigInteger;
import java.util.Optional;

import static guru.interlis.xtf24.Xtf24Exception.Reason.*;
import static guru.interlis.xtf24.Xtf24Wire.ili;

final class Xtf24References {
    record Raw(String id, Optional<BigInteger> position) {}

    private final Xtf24Limits limits;

    Xtf24References(Xtf24Limits limits) {
        this.limits = limits;
    }

    Raw read(Xtf24Input input) {
        var xml = input.cursor();
        for (int i = 0; i < xml.getAttributeCount(); i++) {
            var name = xml.getAttributeName(i);
            if (!name.equals(ili("ref")) && !name.equals(ili("order_pos"))) {
                throw input.error(UNSUPPORTED, "Unexpected reference attribute");
            }
        }
        String id = xml.getAttributeValue(Xtf24Wire.NAMESPACE, "ref");
        if (id == null) {
            throw input.error(INVALID_WIRE, "Missing reference identity");
        }
        Xtf24IdentityScope.requireIdentifier(id, limits.maxIdentifierCodeUnits());
        var position = Optional.ofNullable(xml.getAttributeValue(Xtf24Wire.NAMESPACE, "order_pos")).map(value -> {
            if (value.length() > limits.maxIdentifierCodeUnits()) {
                throw input.error(LIMIT_EXCEEDED, "Reference position is too long");
            }
            if (!value.matches("[+]?[0-9]+")) {
                throw input.error(INVALID_WIRE, "Expected a positive reference position");
            }
            var integer = new BigInteger(value);
            if (integer.signum() <= 0) {
                throw input.error(INVALID_WIRE, "Expected a positive reference position");
            }
            return integer;
        });
        return new Raw(id, position);
    }

    Reference interpret(Raw raw, boolean external, boolean ordered, boolean stable) {
        if (!ordered && raw.position().isPresent()) {
            throw Xtf24Wire.failure(INVALID_WIRE, "Reference position requires an ordered role");
        }
        TransferIdentity.ObjectKey key = stable ? new TransferIdentity.ObjectOid(raw.id())
                : new TransferIdentity.TransferId(raw.id(), TransferIdentity.IdOrigin.SOURCE);
        return new Reference(key, Optional.empty(), external, raw.position());
    }

    void write(Xtf24Output output, Reference reference, boolean external, boolean ordered, boolean stable)
            throws XMLStreamException {
        if (reference.targetBasket().isPresent()) {
            throw Xtf24Wire.failure(UNSUPPORTED, "XTF 2.4 has no target basket field");
        }
        if (reference.external() != external
                || reference.target() instanceof TransferIdentity.ObjectOid != stable) {
            throw Xtf24Wire.failure(UNSUPPORTED, "Reference metadata cannot be expressed by this XTF role");
        }
        Xtf24IdentityScope.requireIdentifier(reference.target().value(), limits.maxIdentifierCodeUnits());
        output.attribute(ili("ref"), reference.target().value());
        if (reference.orderPosition().isPresent()) {
            if (!ordered) {
                throw Xtf24Wire.failure(INVALID_WIRE, "Reference position requires an ordered role");
            }
            var position = reference.orderPosition().orElseThrow();
            if (position.bitLength() > 4L * limits.maxIdentifierCodeUnits()) {
                throw Xtf24Wire.failure(LIMIT_EXCEEDED, "Reference position is too long");
            }
            String text = position.toString();
            if (text.length() > limits.maxIdentifierCodeUnits()) {
                throw Xtf24Wire.failure(LIMIT_EXCEEDED, "Reference position is too long");
            }
            output.attribute(ili("order_pos"), text);
        }
    }
}

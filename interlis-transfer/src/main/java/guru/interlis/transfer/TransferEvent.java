package guru.interlis.transfer;

import guru.interlis.model.LanguageVersion;
import guru.interlis.model.SymbolRef;
import guru.interlis.source.SourceId;
import guru.interlis.transfer.TransferIdentity.BasketId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Owned, immutable event in one neutral transfer stream. Symbol references
 * are bound to the stream's plan snapshot. Event metadata is carried in source order.
 */
public sealed interface TransferEvent permits TransferEvent.StartTransfer, TransferEvent.StartBasket,
        TransferEvent.ObjectEvent, TransferEvent.EndBasket, TransferEvent.EndTransfer {

    /**
     * Transfer header.
     *
     * @param source opaque source identifier, if known
     * @param sender supplied sender, if present
     * @param comment supplied comment, if present
     * @param models immutable list of model declarations in source order, covering every
     *               basket topic and object model used in the stream
     */
    record StartTransfer(Optional<SourceId> source, Optional<String> sender, Optional<String> comment,
                         List<ModelDeclaration> models) implements TransferEvent {
        public StartTransfer {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(sender, "sender");
            Objects.requireNonNull(comment, "comment");
            models = List.copyOf(models);
        }
    }

    /**
     * One model declared by the source.
     *
     * @param model model declaration in the plan snapshot
     * @param languageVersion INTERLIS language version of the model
     * @param modelVersion supplied version string, if present, matching the plan's model version
     * @param issuer supplied model issuer, if present
     */
    record ModelDeclaration(SymbolRef model, LanguageVersion languageVersion, Optional<String> modelVersion,
                            Optional<String> issuer) {
        public ModelDeclaration {
            Objects.requireNonNull(model, "model");
            Objects.requireNonNull(languageVersion, "languageVersion");
            Objects.requireNonNull(modelVersion, "modelVersion");
            Objects.requireNonNull(issuer, "issuer");
        }
    }

    /**
     * Header for one basket. Incremental metadata is carried for later interpretation.
     *
     * @param id identity of this basket occurrence within the transfer
     * @param topic primary topic in the plan snapshot
     * @param additionalTopics effective extension topics in the supplied order
     * @param kind transfer kind
     * @param consistency supplied or defaulted consistency state
     * @param startState opaque initial state of an incremental basket, if supplied
     * @param endState opaque final state of an incremental basket, if supplied
     * @param stableOid independently supplied stable basket identity, if present
     * @param domainBindings immutable list of permitted runtime bindings for the active topics,
     *                       covering every generic domain used by those topics, in input order
     */
    record StartBasket(BasketId id, SymbolRef topic, List<SymbolRef> additionalTopics, BasketKind kind,
                       Consistency consistency, Optional<String> startState, Optional<String> endState,
                       Optional<String> stableOid, List<DomainBinding> domainBindings) implements TransferEvent {
        public StartBasket {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(topic, "topic");
            additionalTopics = List.copyOf(additionalTopics);
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(consistency, "consistency");
            Objects.requireNonNull(startState, "startState");
            Objects.requireNonNull(endState, "endState");
            Objects.requireNonNull(stableOid, "stableOid");
            domainBindings = List.copyOf(domainBindings);
        }
    }

    /** One supplied binding from a generic domain to a concrete domain in the plan snapshot. */
    record DomainBinding(SymbolRef generic, SymbolRef concrete) {
        public DomainBinding {
            Objects.requireNonNull(generic, "generic");
            Objects.requireNonNull(concrete, "concrete");
        }
    }

    /**
     * One object occurrence and its metadata. Values can be retained after advancing a reader.
     *
     * @param object immutable data and identity of the occurrence
     * @param operation supplied incremental operation, if present
     * @param consistency supplied or defaulted consistency state
     */
    record ObjectEvent(TransferObject object, Optional<Operation> operation, Consistency consistency)
            implements TransferEvent {
        public ObjectEvent {
            Objects.requireNonNull(object, "object");
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(consistency, "consistency");
        }
    }

    /** Ends the active basket, identified by the same transfer-scoped ID as its start. */
    record EndBasket(BasketId id) implements TransferEvent {
        public EndBasket {
            Objects.requireNonNull(id, "id");
        }
    }

    /** Ends a complete transfer after its last basket. */
    record EndTransfer() implements TransferEvent { }

    /** Full dataset, initial incremental state, or update of an incremental state. */
    enum BasketKind {
        FULL, INITIAL, UPDATE
    }

    /** Transfer consistency metadata shared by baskets and objects. */
    enum Consistency {
        COMPLETE, INCOMPLETE, INCONSISTENT, ADAPTED
    }

    /** Incremental operation supplied for an object occurrence. */
    enum Operation {
        INSERT, UPDATE, DELETE
    }
}

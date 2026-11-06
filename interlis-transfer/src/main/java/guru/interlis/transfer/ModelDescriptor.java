package guru.interlis.transfer;

import guru.interlis.model.LanguageVersion;
import guru.interlis.model.SymbolRef;

import java.util.List;
import java.util.Objects;

/**
 * One model in the checked transfer closure. References belong to the plan snapshot.
 *
 * @param symbol model declaration
 * @param name model names and original identity
 * @param languageVersion INTERLIS language version of the declaration
 * @param modelVersion version string declared by the model
 * @param xmlNamespace effective XML namespace published by the schema
 * @param incrementalTransferAllowed whether the model permits incremental transfers
 * @param topics immutable list of model topics in declaration order
 */
public record ModelDescriptor(SymbolRef symbol, TransferName name, LanguageVersion languageVersion,
                              String modelVersion, String xmlNamespace, boolean incrementalTransferAllowed,
                              List<SymbolRef> topics) {
    public ModelDescriptor {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(languageVersion, "languageVersion");
        Objects.requireNonNull(modelVersion, "modelVersion");
        Objects.requireNonNull(xmlNamespace, "xmlNamespace");
        topics = List.copyOf(topics);
    }
}

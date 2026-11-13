package guru.interlis.xtf24;

import guru.interlis.model.BlackboxType;
import guru.interlis.transfer.TransferValue;
import guru.interlis.transfer.TransferValue.Binary;
import guru.interlis.transfer.TransferValue.XmlFragment;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import java.util.Base64;

import static guru.interlis.xtf24.Xtf24Exception.Reason.*;

final class Xtf24Blackbox {
    private Xtf24Blackbox() {}

    static TransferValue read(Xtf24Input input, BlackboxType type) throws XMLStreamException {
        return switch (type.blackboxKind()) {
            case XML -> Xtf24XmlFragment.read(input);
            case BINARY -> {
                String text = input.text();
                var compact = new StringBuilder(text.length());
                for (int i = 0; i < text.length(); i++) {
                    char code = text.charAt(i);
                    if (code != ' ' && code != '\r' && code != '\n' && code != '\t') {
                        compact.append(code);
                    }
                }
                String base64 = compact.toString();
                byte[] bytes;
                try {
                    bytes = Base64.getDecoder().decode(base64);
                } catch (IllegalArgumentException failure) {
                    throw input.error(INVALID_WIRE, "Invalid XTF BINARY Base64");
                }
                if (!Base64.getEncoder().encodeToString(bytes).equals(base64)) {
                    throw input.error(INVALID_WIRE, "Invalid XTF BINARY padding or unused bits");
                }
                yield new Binary(bytes);
            }
        };
    }

    static void write(QName field, Xtf24Output output, BlackboxType type, TransferValue value)
            throws XMLStreamException {
        switch (type.blackboxKind()) {
            case XML -> Xtf24XmlFragment.write(field, Xtf24Scalars.require(value, XmlFragment.class), output);
            case BINARY -> {
                var binary = Xtf24Scalars.require(value, Binary.class);
                if (4L * ((binary.size() + 2L) / 3) > output.limits().maxScalarCodeUnits()) {
                    throw Xtf24Wire.failure(LIMIT_EXCEEDED, "XTF BINARY exceeds the encoded text limit");
                }
                output.start(field);
                output.text(Base64.getEncoder().encodeToString(binary.bytes()));
                output.end();
            }
        }
    }
}

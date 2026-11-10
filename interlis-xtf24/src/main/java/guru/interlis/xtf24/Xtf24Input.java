package guru.interlis.xtf24;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import java.util.Objects;

import static guru.interlis.xtf24.Xtf24Exception.Reason.CONFIGURATION;
import static guru.interlis.xtf24.Xtf24Exception.Reason.UNSAFE_XML;

final class Xtf24Input {
    private Xtf24Input() {}

    static XMLInputFactory secureFactory(Xtf24Limits limits) {
        var factory = XMLInputFactory.newDefaultFactory();
        try {
            requireProperty(factory, XMLInputFactory.IS_NAMESPACE_AWARE, true);
            requireProperty(factory, XMLInputFactory.IS_COALESCING, false);
            requireProperty(factory, XMLInputFactory.IS_VALIDATING, false);
            requireProperty(factory, XMLInputFactory.SUPPORT_DTD, false);
            requireProperty(factory, XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            requireProperty(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
            requireProperty(factory, "jdk.xml.maxElementDepth", limits.maxDepth());
            requireProperty(factory, "jdk.xml.maxXMLNameLimit", limits.maxIdentifierCodeUnits());
            requireProperty(factory, "jdk.xml.elementAttributeLimit", limits.maxIdentifierCodeUnits());
            factory.setXMLResolver((publicId, systemId, baseUri, namespace) -> {
                throw new Xtf24Exception(UNSAFE_XML, "External XML resolution is forbidden", -1, -1, null);
            });
        } catch (IllegalArgumentException e) {
            throw new Xtf24Exception(CONFIGURATION, "Cannot configure the JDK XML input factory", -1, -1, e);
        }
        return factory;
    }

    private static void requireProperty(XMLInputFactory factory, String property, Object value) {
        factory.setProperty(property, value);
        if (!value.toString().equals(Objects.toString(factory.getProperty(property), null))) {
            throw new IllegalArgumentException("JDK XML property did not take effect: " + property);
        }
    }
}

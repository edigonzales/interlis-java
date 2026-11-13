package guru.interlis.xtf24;

import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLInputFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Xtf24LimitsTest {
    @Test
    void rejectsNonpositiveLimits() {
        assertThrows(IllegalArgumentException.class, () -> new Xtf24Limits(0, 1, 1, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Xtf24Limits(1, -1, 1, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Xtf24Limits(1, 1, 0, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Xtf24Limits(1, 1, 1, 0, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Xtf24Limits(1, 1, 1, 1, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Xtf24Limits(1, 1, 1, 1, 1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new Xtf24Limits(1, 1, 1, 1, 1, 1, 0));
    }

    @Test
    void configuresNamespaceAwareParserWithoutExternalEntities() {
        var factory = Xtf24Input.secureFactory(Xtf24Limits.defaults());
        assertEquals(true, factory.getProperty(XMLInputFactory.IS_NAMESPACE_AWARE));
        assertEquals(false, factory.getProperty(XMLInputFactory.SUPPORT_DTD));
        assertEquals(false, factory.getProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES));
        var error = assertThrows(Xtf24Exception.class,
                () -> factory.getXMLResolver().resolveEntity(null, "file:/unavailable", null, null));
        assertEquals(Xtf24Exception.Reason.UNSAFE_XML, error.reason());
    }
}

package guru.interlis.verification.contracts;

import com.google.gson.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.verification.contracts.ContractFiles.*;

class NormativeQuoteAssertionsTest {
    @ParameterizedTest @ValueSource(strings={"document", "page", "excerptStart", "quote", "quoteSha256", "extractedPageSha256"})
    void movedWeakenedOrWrongVersionExcerptFails(String key) {
        String page = "😀 Exact norm. Footer."; String quote = "Exact norm.";
        JsonObject ref = new JsonObject(); ref.addProperty("document", "2.3"); ref.addProperty("page", 1);
        ref.addProperty("excerptStart", 2); ref.addProperty("excerptEnd", 13); ref.addProperty("quote", quote);
        ref.addProperty("quoteSha256", hash(quote)); ref.addProperty("extractedPageSha256", hash(page));
        NormativeQuoteAssertions.excerpt("2.3", List.of(page), ref);
        if (key.equals("page") || key.equals("excerptStart")) ref.addProperty(key, 2); else ref.addProperty(key, "broken");
        if (key.equals("excerptStart")) ref.addProperty(key, 3);
        assertThrows(Exception.class, () -> NormativeQuoteAssertions.excerpt("2.3", List.of(page), ref));
    }
    @Test void wrongPdfHashVersionAndExtractorAreRejected() {
        var document=JsonParser.parseString("{\"sha256\":\"pdf-hash\",\"url\":\"https://norm.invalid\"}").getAsJsonObject();
        var recorded=JsonParser.parseString("{\"document\":\"2.3\",\"sha256\":\"pdf-hash\",\"extractor\":\"pypdf 6.10.0\",\"url\":\"https://norm.invalid\",\"pageCount\":1}").getAsJsonObject();
        var data=JsonParser.parseString("{\"document\":\"2.3\",\"pdfSha256\":\"pdf-hash\",\"extractor\":\"pypdf 6.10.0\",\"offsetUnit\":\"Unicode code points\",\"pages\":[\"😀 norm\"]}").getAsJsonObject();
        var hashes=new JsonArray();hashes.add(hash("😀 norm"));recorded.add("extractedPageHashes",hashes);
        assertEquals(List.of("😀 norm"),NormativeQuoteAssertions.validatePages("2.3",document,recorded,data));
        for(String key:List.of("pdfSha256","extractor","document","offsetUnit")) {
            var changed=data.deepCopy();changed.addProperty(key,"wrong");
            assertThrows(IllegalArgumentException.class,()->NormativeQuoteAssertions.validatePages("2.3",document,recorded,changed));
        }
        assertThrows(IllegalArgumentException.class,()->NormativeQuoteAssertions.validatePages("2.4",document,recorded,data));
    }
}

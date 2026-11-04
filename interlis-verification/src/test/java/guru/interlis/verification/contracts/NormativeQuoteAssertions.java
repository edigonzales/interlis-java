package guru.interlis.verification.contracts;

import com.google.gson.*;
import guru.interlis.verification.VerificationLayout;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import static guru.interlis.verification.contracts.ContractFiles.*;

/** Validates preserved pypdf text coordinates; does not extract or interpret the norm. */
final class NormativeQuoteAssertions {
    private NormativeQuoteAssertions() { }
    static List<String> pages(String version, JsonObject document, JsonObject recorded) throws IOException {
        require(Set.of("2.3", "2.4").contains(version), "Wrong norm version");
        require(hash(VerificationLayout.forInvocation(root()).normativePdf(version)).equals(s(document,"sha256")),"Changed norm PDF");
        Path path = inside(root(), BASE + "norm-text/ili" + version.replace(".", "") + "-pypdf-6.10.0.json.gz");
        JsonObject data;
        try (var reader = new InputStreamReader(new GZIPInputStream(Files.newInputStream(path)), StandardCharsets.UTF_8)) {
            data = JsonParser.parseReader(reader).getAsJsonObject();
        }
        return validatePages(version, document, recorded, data);
    }
    static List<String> validatePages(String version, JsonObject document, JsonObject recorded, JsonObject data) {
        require(Set.of("2.3", "2.4").contains(version), "Wrong norm version");
        require(s(data, "document").equals(version) && s(recorded, "document").equals(version)
                && s(data, "pdfSha256").equals(s(document, "sha256")) && s(recorded, "sha256").equals(s(document, "sha256"))
                && s(data, "extractor").equals("pypdf 6.10.0") && s(recorded, "extractor").equals(s(data, "extractor"))
                && s(data, "offsetUnit").equals("Unicode code points") && recorded.get("url").equals(document.get("url")), "Wrong PDF/extractor binding");
        var pages = strings(data.get("pages"));
        require(pages.size() == n(recorded, "pageCount") && pages.stream().noneMatch(String::isBlank)
                && pages.stream().map(ContractFiles::hash).toList().equals(strings(recorded.get("extractedPageHashes"))), "Changed extracted pages");
        return pages;
    }
    static void excerpt(String version, List<String> pages, JsonObject ref) {
        require(s(ref, "document").equals(version) && n(ref, "page") > 0 && n(ref, "page") <= pages.size(), "Wrong norm excerpt document/page");
        String page = pages.get(n(ref, "page") - 1), quote = s(ref, "quote");
        require(n(ref, "excerptStart") < n(ref, "excerptEnd")
                && slice(page, n(ref, "excerptStart"), n(ref, "excerptEnd")).equals(quote)
                && hash(page).equals(s(ref, "extractedPageSha256")) && hash(quote).equals(s(ref, "quoteSha256")), "Changed norm excerpt");
    }
}

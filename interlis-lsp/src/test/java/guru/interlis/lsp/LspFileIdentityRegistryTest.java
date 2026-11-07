package guru.interlis.lsp;

import guru.interlis.execution.CancellationToken;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LspFileIdentityRegistryTest {
    @TempDir Path temporary;

    @Test void aliasPathLimitCountsBothPathsAndRejectsOverflowAtomically() {
        var registry = new LspFileIdentityRegistry();
        var primary = temporary.resolve("Primary.ili");
        String canonical = primary.toUri().toString();
        registry.rememberPhysicalIdentity(new ProjectSourceLoader.FileIdentity(primary, primary, Optional.empty()), canonical);
        // Leave room for exactly one additional path.
        for (int i = 0; i < 39_998; i++) {
            var alias = temporary.resolve("alias-" + i + ".ili");
            registry.rememberPhysicalIdentity(new ProjectSourceLoader.FileIdentity(alias, primary, Optional.empty()), canonical);
        }

        var unknownAlias = temporary.resolve("unregistered-alias.ili");
        var unknownReal = temporary.resolve("unregistered-real.ili");
        var twoNewPaths = new ProjectSourceLoader.FileIdentity(unknownAlias, unknownReal, Optional.empty());
        var failure = assertThrows(ResponseErrorException.class, () -> registry.rememberPhysicalIdentity(twoNewPaths, canonical));
        assertEquals(ResponseErrorCode.RequestFailed.getValue(), failure.getResponseError().getCode());
        assertEquals(unknownAlias.toUri().toString(), registry.canonicalUri(unknownAlias.toUri().toString()));
        assertEquals(unknownReal.toUri().toString(), registry.canonicalUri(unknownReal.toUri().toString()));

        var lastAlias = temporary.resolve("last-alias.ili");
        var last = new ProjectSourceLoader.FileIdentity(lastAlias, primary, Optional.empty());
        registry.rememberPhysicalIdentity(last, canonical);
        registry.rememberPhysicalIdentity(last, canonical);
        assertEquals(canonical, registry.canonicalUri(lastAlias.toUri().toString()));
        var oneNewPath = new ProjectSourceLoader.FileIdentity(unknownAlias, primary, Optional.empty());
        assertThrows(ResponseErrorException.class, () -> registry.rememberPhysicalIdentity(oneNewPath, canonical));
        assertEquals(canonical, registry.canonicalUri(lastAlias.toUri().toString()));
    }

    @Test void physicalIdentityWithoutFileKeysDistinguishesHardlinksFromEqualBytes() throws Exception {
        var primary = Files.writeString(temporary.resolve("Base.ili"),
                "INTERLIS 2.4; MODEL Base AT \"x\" VERSION \"1\" = END Base.");
        var hardlink = Files.createLink(temporary.resolve("Link.ili"), primary);
        var copy = Files.copy(primary, temporary.resolve("Copy.ili"));
        var loader = new ProjectSourceLoader();
        var token = CancellationToken.none();
        var loaded = loader.read(primary.toUri().toString(), List.of(temporary.toRealPath()), token).orElseThrow();
        var keyless = new ProjectSourceLoader.FileContent(loaded.path(), loaded.realPath(), Optional.empty(),
                loaded.loaded(), loaded.editable(), loaded.roots(), loaded.symbolicLink());

        assertEquals(-1, Files.mismatch(primary, copy));
        assertTrue(loader.sameFile(keyless, hardlink.toUri().toString(), token));
        assertFalse(loader.sameFile(keyless, copy.toUri().toString(), token));
    }
}

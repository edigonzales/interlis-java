package guru.interlis.repository;

import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;

/** Strict UTF-8 file loader with size and cancellation limits. */
public final class FileSourceLoader implements SourceLoader {
    private final Path workingDirectory;

    /**
     * Creates an instance.
     *
     * @param workingDirectory the {@code workingDirectory} argument
     */
    public FileSourceLoader(Path workingDirectory) {
        this.workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory")
                .toAbsolutePath().normalize();
    }

    @Override
    public LoadedSource load(SourceLocator locator, FetchPolicy policy, CancellationToken token) {
        Objects.requireNonNull(locator, "locator");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(token, "token");
        if (policy.offline()) {
            throw new SourceLoadException(SourceLoadException.Reason.OFFLINE,
                    "file loading disabled by offline policy", null);
        }
        token.throwIfCancellationRequested();
        Path path;
        try {
            path = Path.of(locator.location());
        } catch (InvalidPathException failure) {
            throw new SourceLoadException(SourceLoadException.Reason.IO_FAILURE,
                    "source location is not a valid path", failure);
        }
        if (!path.isAbsolute()) {
            path = workingDirectory.resolve(path);
        }
        path = path.normalize();
        try {
            if (!Files.exists(path)) {
                throw new SourceLoadException(SourceLoadException.Reason.NOT_FOUND,
                        "source not found", null);
            }
            long size = Files.size(path);
            if (size > policy.maxBytes()) {
                throw new SourceLoadException(SourceLoadException.Reason.TOO_LARGE,
                        "source exceeds configured byte limit", null);
            }
            byte[] bytes = Files.readAllBytes(path);
            token.throwIfCancellationRequested();
            var digest = guru.interlis.source.Sha256.digest(bytes);
            String text;
            try {
                text = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException e) {
                throw new SourceLoadException(SourceLoadException.Reason.INVALID_ENCODING,
                        "source is not valid UTF-8", e);
            }
            Path absolute = path.toAbsolutePath().normalize();
            return new LoadedSource(new SourceId(absolute.toUri().toString()), text,
                    guru.interlis.source.Hex.encode(digest), "file");
        } catch (NoSuchFileException e) {
            throw new SourceLoadException(SourceLoadException.Reason.NOT_FOUND,
                    "source not found", e);
        } catch (AccessDeniedException e) {
            throw new SourceLoadException(SourceLoadException.Reason.ACCESS_DENIED,
                    "source access denied", e);
        } catch (IOException e) {
            throw new SourceLoadException(SourceLoadException.Reason.IO_FAILURE,
                    "source could not be read", e);
        }
    }
}

package guru.interlis.lsp;

import guru.interlis.execution.*;
import guru.interlis.repository.*;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.*;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/** Bounded local project IO; model headers and imports remain compiler responsibilities. */
final class ProjectSourceLoader {
    private static final Set<String> EXCLUDED=Set.of(".git",".gradle","build","node_modules");
    record FileContent(Path path,Path realPath,Optional<Object> fileKey,LoadedSource loaded,boolean editable,List<Path> roots) {
        FileContent { roots=List.copyOf(roots); }
    }
    record Project(List<Path> roots,List<FileContent> files) {
        Project { roots=List.copyOf(roots); files=List.copyOf(files); }
    }

    Project scan(List<String> uris,CancellationToken token) {
        var roots=new ArrayList<Path>(); var paths=new LinkedHashSet<Path>();
        try {
            for(String uri:uris) {
                token.throwIfCancellationRequested();
                // Preserve the client's logical URI (e.g. /var versus /private/var).
                // Real paths are used only for editable-root containment checks.
                Path root=local(uri);
                if(!Files.isDirectory(root)) throw error(ResponseErrorCode.InvalidParams,"workspace root is not a directory");
                Path realRoot=root.toRealPath(); roots.add(realRoot);
                Files.walkFileTree(realRoot,new SimpleFileVisitor<>() {
                    @Override public FileVisitResult preVisitDirectory(Path directory,BasicFileAttributes attributes) {
                        token.throwIfCancellationRequested();
                        return !directory.equals(realRoot) && EXCLUDED.contains(directory.getFileName().toString())
                                ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attributes) {
                        token.throwIfCancellationRequested();
                        if(file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ili") && Files.isRegularFile(file)) {
                            paths.add(root.resolve(realRoot.relativize(file)).toAbsolutePath().normalize());
                            if(paths.size()>10_000) throw new OperationCancelledException(CancellationReason.RESOURCE_LIMIT);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            var files=new ArrayList<FileContent>(); long characters=0;
            for(Path path:paths.stream().sorted().toList()) {
                var file=read(path.toUri().toString(),roots,token).orElseThrow(() -> error(ResponseErrorCode.RequestFailed,"project source disappeared during discovery"));
                characters+=file.loaded().text().length();
                if(characters>32_000_000) throw new OperationCancelledException(CancellationReason.RESOURCE_LIMIT);
                files.add(file);
            }
            return new Project(roots,files);
        } catch(IOException unavailable) { throw error(ResponseErrorCode.RequestFailed,"project discovery failed: IO_FAILURE"); }
    }

    Optional<FileContent> read(String uri,List<Path> roots,CancellationToken token) {
        Path path=local(uri);
        try {
            var before=Files.readAttributes(path,BasicFileAttributes.class);
            var source=new FileSourceLoader(path.getParent()).load(new SourceLocator(path.toString()),FetchPolicy.defaults(),token);
            Path real=path.toRealPath();
            var after=Files.readAttributes(real,BasicFileAttributes.class);
            if(!Objects.equals(before.fileKey(),after.fileKey())) throw error(ResponseErrorCode.ContentModified,"source identity changed during load");
            boolean editable=roots.stream().anyMatch(real::startsWith) && Files.isWritable(real);
            return Optional.of(new FileContent(path,real,Optional.ofNullable(after.fileKey()),source,editable,roots));
        } catch(NoSuchFileException missing) { return Optional.empty(); }
        catch(SourceLoadException failure) {
            if(failure.reason()==SourceLoadException.Reason.NOT_FOUND) return Optional.empty();
            throw error(ResponseErrorCode.RequestFailed,"source load failed: "+failure.reason());
        } catch(IOException failure) { throw error(ResponseErrorCode.RequestFailed,"source load failed: IO_FAILURE"); }
    }

    void verify(List<FileContent> expected,CancellationToken token) {
        for(var original:expected) {
            token.throwIfCancellationRequested();
            var current=read(original.path().toUri().toString(),original.roots(),token);
            if(current.isEmpty() || !current.get().realPath().equals(original.realPath())
                    || !current.get().loaded().byteSha256().equals(original.loaded().byteSha256()))
                throw error(ResponseErrorCode.ContentModified,"CONTENT_MODIFIED: closed source changed on disk");
            if(!current.get().editable()) throw error(ResponseErrorCode.RequestFailed,"closed source is not editable");
        }
    }

    boolean sameFile(FileContent file,String uri,CancellationToken token) {
        token.throwIfCancellationRequested();
        try {
            Path candidate=local(uri);
            Object key=Files.readAttributes(candidate,BasicFileAttributes.class).fileKey();
            return file.fileKey().isPresent() && key!=null?file.fileKey().orElseThrow().equals(key):Files.isSameFile(file.realPath(),candidate);
        }
        catch(IOException | ResponseErrorException unavailable) { return false; }
    }

    private static Path local(String uri) {
        try {
            URI value=URI.create(uri);
            if(!"file".equalsIgnoreCase(value.getScheme())) throw error(ResponseErrorCode.InvalidParams,"only local file workspace roots are supported");
            return Path.of(value).toAbsolutePath().normalize();
        } catch(IllegalArgumentException invalid) { throw error(ResponseErrorCode.InvalidParams,"invalid workspace file URI"); }
    }
    private static ResponseErrorException error(ResponseErrorCode code,String message) {
        return new ResponseErrorException(new ResponseError(code,message,null));
    }
}

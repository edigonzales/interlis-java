package build.interlis.archive;

import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.Comparator;
import org.apache.commons.compress.archivers.zip.*;

/** Build-only ZIP writer: deterministic entries and real Unix permissions, never a product dependency. */
public final class ArchiveWriter {
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Expected prepared SDK directory and destination ZIP");
        Path root=Path.of(args[0]).toAbsolutePath().normalize(),archive=Path.of(args[1]).toAbsolutePath().normalize();
        if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS)||archive.startsWith(root))throw new IllegalArgumentException("Unsafe archive paths");
        Files.createDirectories(archive.getParent());
        try(var zip=new ZipArchiveOutputStream(archive.toFile());var paths=Files.walk(root)) {
            zip.setEncoding("UTF-8");zip.setUseLanguageEncodingFlag(true);zip.setCreateUnicodeExtraFields(ZipArchiveOutputStream.UnicodeExtraFieldPolicy.NEVER);
            for(Path file:paths.filter(p->!p.equals(root)).sorted(Comparator.comparing(p->root.relativize(p).toString().replace('\\','/'))).toList()) {
                if(Files.isSymbolicLink(file))throw new IllegalArgumentException("Symlink in SDK staging: "+file);
                boolean directory=Files.isDirectory(file);String name=root.relativize(file).toString().replace('\\','/')+(directory?"/":"");
                var entry=new ZipArchiveEntry(name);entry.setTimeLocal(LocalDateTime.of(1980,2,1,0,0));
                boolean launcher=name.equals("cli/bin/interlis-compiler")||name.equals("cli/bin/interlis-compiler.bat");
                entry.setUnixMode((directory?040000:0100000)|(directory||launcher?0755:0644));
                zip.putArchiveEntry(entry);if(!directory)Files.copy(file,zip);zip.closeArchiveEntry();
            }
        }
    }
}

package build.interlis;

import java.nio.channels.*;
import java.nio.file.*;

/** Protects host outputs and their JVM inputs from simultaneous host runs or clean. */
public final class JvmBuildLock implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;
    private JvmBuildLock(FileChannel channel,FileLock lock){this.channel=channel;this.lock=lock;}
    public static JvmBuildLock acquire(Path root)throws Exception {
        Path file=JvmBuildInputs.requireOutputRoot(root,"target/jenesis-jvm/project.lock");Files.createDirectories(file.getParent());
        var channel=FileChannel.open(file,StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        try {
            var lock=channel.tryLock();if(lock==null)throw new IllegalStateException("Another project build is active");
            return new JvmBuildLock(channel,lock);
        }catch(Exception error){channel.close();throw error;}
    }
    @Override public void close()throws java.io.IOException {
        try {lock.release();}finally{channel.close();}
    }
}

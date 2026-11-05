package build.interlis;

import java.nio.channels.*;
import java.nio.file.*;
import java.util.concurrent.Callable;

/** Invocation-owned JVM producer lease. Immutable consumers suspend it explicitly. */
public final class JvmBuildLock implements AutoCloseable {
    private final FileChannel channel;
    private FileLock lock;
    private JvmBuildLock(FileChannel channel,FileLock lock){this.channel=channel;this.lock=lock;}
    public static JvmBuildLock acquire(Path root)throws Exception {
        Path file=JvmBuildInputs.requireOutputRoot(root,"target/jenesis-jvm/project.lock");Files.createDirectories(file.getParent());
        var channel=FileChannel.open(file,StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        try {
            var lock=channel.tryLock();if(lock==null)throw new IllegalStateException("Another project build is active");
            return new JvmBuildLock(channel,lock);
        }catch(Exception error){channel.close();throw error;}
    }
    /** Runs an immutable consumer without the JVM lease, then reacquires it before returning. */
    public <T> T withoutLock(Callable<T> consumer)throws Exception {
        if(lock==null||!lock.isValid())throw new IllegalStateException("No active JVM producer lease");
        lock.release();lock=null;
        try {return consumer.call();}
        finally {lock=channel.lock();}
    }
    @Override public void close()throws java.io.IOException {
        try {if(lock!=null&&lock.isValid())lock.release();}finally{channel.close();}
    }
}

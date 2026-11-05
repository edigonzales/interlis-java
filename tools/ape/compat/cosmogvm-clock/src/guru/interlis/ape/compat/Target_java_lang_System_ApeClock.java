/* Derived from CosmoGVM's GPL-2.0-with-Classpath-exception CosmoSubstitutions.
 * Local change: currentTimeMillis uses CLOCK_REALTIME, including its error text.
 * All native calls, conversion and interruption constraints match the pinned vendor.
 */
package guru.interlis.ape.compat;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.posix.cosmo.CosmoLibCSupplier;
import com.oracle.svm.core.posix.cosmo.CosmoUtils;
import com.oracle.svm.core.posix.cosmo.headers.Time;
import com.oracle.svm.shared.Uninterruptible;
import org.graalvm.nativeimage.StackValue;

/** Build-only replacement selected exclusively for Cosmopolitan libc targets. */
@TargetClass(value = System.class, onlyWith = CosmoLibCSupplier.class)
final class Target_java_lang_System_ApeClock {
    @Substitute
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long currentTimeMillis() {
        Time.timespec ts = StackValue.get(Time.timespec.class);
        int status = CosmoUtils.clock_gettime(Time.CLOCK_REALTIME(), ts);
        CosmoUtils.checkStatusIs0(status, "System.currentTimeMillis(): clock_gettime(CLOCK_REALTIME) failed.");
        return ts.tv_sec() * 1000L + ts.tv_nsec() / 1_000_000L;
    }
}

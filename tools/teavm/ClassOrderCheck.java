import java.lang.reflect.Constructor;
import java.util.*;
import org.teavm.model.*;

/** Build-tool regression: filtered immutable membership with stable traversal. */
public final class ClassOrderCheck {
    public static void main(String[] args) throws Exception {
        var source = new MutableClassHolderSource();
        var expected = new ArrayList<String>();
        for (int i = 0; i < 40; i++) {
            String name = "example.Type" + i;
            source.putClassHolder(new ClassHolder(name));
            expected.add(name);
        }
        Collections.sort(expected);
        Class<?> adapter = Class.forName("org.teavm.vm.TeaVM$ListableClassReaderSourceAdapter");
        Constructor<?> constructor = adapter.getDeclaredConstructor(ClassReaderSource.class, Set.class);
        constructor.setAccessible(true);
        var shuffled = new ArrayList<>(expected);
        for (int trial = 0; trial < 80; trial++) {
            Collections.shuffle(shuffled, new Random(trial));
            var input = new LinkedHashSet<>(shuffled);
            input.add("example.Missing");
            var result = (ListableClassReaderSource) constructor.newInstance(source, input);
            if (!new ArrayList<>(result.getClassNames()).equals(expected)) {
                throw new AssertionError("TeaVM class traversal depends on unordered set iteration");
            }
            if (result.get("example.Missing") != null || result.getClassNames().contains("example.Missing")) {
                throw new AssertionError("Unavailable classes must stay filtered");
            }
            for (String name : expected) if (result.get(name) != source.get(name)) {
                throw new AssertionError("Class source identity changed");
            }
            try {
                result.getClassNames().add("example.Missing");
                throw new AssertionError("Class names must stay immutable");
            } catch (UnsupportedOperationException expectedFailure) { }
        }
        System.out.println("ORDERED_TEAVM_CONTRACT_PASS permutations=80 classes=40 membership/identity/filtering/immutability");
    }
}

package guru.interlis.compiler;

import guru.interlis.model.CompiledSchema;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** ARCH-001/API-001: public compiler/model API must not leak host or ANTLR types. */
class ArchitectureBoundaryTest {
    private static final List<String> FORBIDDEN = List.of(
            "org.antlr.",
            "org.eclipse.lsp4j.",
            "java.net.http.",
            "java.sql.",
            "javax.swing.",
            "java.awt.",
            "guru.interlis.compiler.internal.",
            "guru.interlis.cli.",
            "guru.interlis.lsp.");

    @Test
    void publicModelAndCompilerApiDoNotLeakForbiddenTypes() throws Exception {
        List<String> violations = new ArrayList<>();
        for (Path root : List.of(classesOf(CompiledSchema.class),
                classesOf(CompilerEngine.class))) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".class"))
                        .filter(path -> !path.getFileName().toString().contains("$"))
                        .toList()) {
                    String relative = root.relativize(file).toString()
                            .replace('/', '.').replace('\\', '.');
                    String name = relative.substring(0, relative.length() - ".class".length());
                    if (name.contains(".internal.") || name.endsWith(".internal")) {
                        continue;
                    }
                    Class<?> type;
                    try {
                        type = Class.forName(name, false,
                                ArchitectureBoundaryTest.class.getClassLoader());
                    } catch (ClassNotFoundException | LinkageError ignored) {
                        continue;
                    }
                    if (!java.lang.reflect.Modifier.isPublic(type.getModifiers())) {
                        continue;
                    }
                    inspect(type, violations);
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
    }

    private static void inspect(Class<?> type, List<String> violations) {
        check(type.getGenericSuperclass(), type, violations);
        for (var iface : type.getGenericInterfaces()) {
            check(iface, type, violations);
        }
        for (Field field : type.getFields()) {
            check(field.getGenericType(), type, violations);
        }
        for (Constructor<?> constructor : type.getConstructors()) {
            for (var parameter : constructor.getGenericParameterTypes()) {
                check(parameter, type, violations);
            }
        }
        for (Method method : type.getMethods()) {
            if (method.getDeclaringClass() == Object.class || method.isSynthetic()) {
                continue;
            }
            check(method.getGenericReturnType(), type, violations);
            for (var parameter : method.getGenericParameterTypes()) {
                check(parameter, type, violations);
            }
        }
    }

    private static void check(java.lang.reflect.Type type, Class<?> owner,
                              List<String> violations) {
        if (type == null) {
            return;
        }
        String text = type.getTypeName();
        for (String forbidden : FORBIDDEN) {
            if (text.contains(forbidden)) {
                violations.add(owner.getName() + " exposes " + forbidden + " in " + text);
            }
        }
    }

    private static Path classesOf(Class<?> type) throws Exception {
        URL location = type.getProtectionDomain().getCodeSource().getLocation();
        return Path.of(location.toURI());
    }
}

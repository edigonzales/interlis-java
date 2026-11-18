package guru.interlis.compiler;

import guru.interlis.model.CompiledSchema;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

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
        for (Module module : List.of(CompiledSchema.class.getModule(), CompilerEngine.class.getModule())) {
            var reference = module.getLayer().configuration().findModule(module.getName()).orElseThrow().reference();
            var exports = module.getDescriptor().exports().stream()
                    .filter(export -> !export.isQualified())
                    .map(java.lang.module.ModuleDescriptor.Exports::source)
                    .collect(java.util.stream.Collectors.toSet());
            try (var reader = reference.open(); var entries = reader.list()) {
                var apiClasses = entries.filter(entry -> entry.endsWith(".class") && entry.contains("/"))
                        .map(entry -> entry.substring(0, entry.length() - ".class".length()).replace('/', '.'))
                        .filter(name -> exports.contains(name.substring(0, name.lastIndexOf('.'))))
                        .toList();
                assertTrue(!apiClasses.isEmpty(), () -> "No exported classes in " + module.getName());
                for (String name : apiClasses) {
                    Class<?> type = Class.forName(module, name);
                    if (java.lang.reflect.Modifier.isPublic(type.getModifiers())) {
                        inspect(type, violations);
                    }
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

}

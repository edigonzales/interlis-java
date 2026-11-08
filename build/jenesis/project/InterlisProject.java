package build.jenesis.project;

import build.jenesis.Environment;
import build.jenesis.step.Bind;
import build.jenesis.step.Inventory;
import build.jenesis.step.Jar;
import build.jenesis.step.ProcessHandler;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The INTERLIS-specific additions to Jenesis' Maven assembly. */
public final class InterlisProject {
    private InterlisProject() {}

    public static MultiProjectAssembler<ProjectModuleDescriptor> assemble(
            Environment environment, InferredMultiProjectAssembler stock) {
        Path root = environment.root();
        Path revision;
        try {
            revision = revision(root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        boolean tests = !environment.flag("test.skip", Environment.Setting.PROJECT);
        return (descriptor, repositories, resolvers) -> {
            String module = descriptor.location().getFileName().toString();
            var assembler = stock;
            boolean product = descriptor.name().equals("module-" + module);
            boolean compiler = product && module.equals("interlis-compiler");
            if (compiler) {
                assembler = assembler.resources(new LinkedHashMap<>(Map.of(
                        Path.of("META-INF/licenses/UNICODE-LICENSE.txt"),
                        root.resolve("testdata/reference/unicode/17.0.0/UNICODE-LICENSE.txt"))));
            }
            if (product && module.equals("interlis-compiler-cli") && revision != null) {
                assembler = assembler.resources(new LinkedHashMap<>(Map.of(
                        Path.of("guru/interlis/cli/build-revision.txt"), revision)));
            }
            if (descriptor.test() && Files.isRegularFile(descriptor.location().resolve("src/main/java/module-info.java"))) {
                assembler = assembler.observe(observation -> observation.test(nativeTests -> {
                    try {
                        return InterlisJpmsTests.observe(nativeTests, root, module, environment.keys(), tests);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }));
            }
            var assembly = assembler.apply(descriptor, repositories, resolvers);
            if (!compiler) return assembly;
            return assembly.nest("source", "", ignored -> (executor, inherited) -> {
                var names = new ArrayList<String>();
                descriptor.resources().forEach(name -> names.add(localIdentity(name)));
                descriptor.manifests().forEach(name -> names.add(localIdentity(name)));
                names.addAll(List.of("prepare", "binary", "include"));
                var inputs = new ArrayList<String>();
                for (String key : inherited.sequencedKeySet()) {
                    String name = localIdentity(key);
                    if (name.startsWith("build/")) name = name.substring(6);
                    String candidate = name;
                    if (names.stream().anyMatch(input -> candidate.equals(input) || candidate.startsWith(input + "/"))) {
                        inputs.add(key);
                    }
                }
                executor.addSource("maintained-java", Bind.asSources().extensions(Set.of(".java")),
                        descriptor.location().resolve("src/main/java"));
                executor.addSource("grammars", Bind.asResources(), descriptor.location().resolve("src/main/antlr"));
                inputs.addAll(List.of("maintained-java", "grammars"));
                executor.addStep("archive", Jar.ofEnvironment(environment,
                        ProcessHandler.Factory.ofEnvironment(environment), Jar.Sort.SOURCES), inputs.stream());
                inputs.add("archive");
                executor.addStep("inventory", new Inventory().supplement(true), inputs.stream());
            });
        };
    }

    private static String localIdentity(String value) {
        while (value.startsWith("../")) value = value.substring(3);
        return value;
    }

    private static Path revision(Path root) throws IOException {
        Process git;
        try {
            git = new ProcessBuilder("git", "rev-parse", "--verify", "HEAD").directory(root.toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException unavailable) {
            return null;
        }
        String revision = new String(git.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).strip();
        if (!revision.matches("[0-9a-f]{40}|[0-9a-f]{64}")) return null;
        Path file = root.resolve("target/jenesis-jvm/build-revisions").resolve(revision + ".txt");
        Files.createDirectories(file.getParent());
        if (!Files.exists(file)) Files.writeString(file, revision);
        return file;
    }
}

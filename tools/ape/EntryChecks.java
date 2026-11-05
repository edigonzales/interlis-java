package build;

/** Parser-only checks preserve existing JVM profiles without starting their test suites. */
public class EntryChecks {
    public static void main(String[] args) throws Exception {
        var defaults = InterlisJvmBuild.parse(new String[]{});
        if (!defaults.profile().equals("build") || defaults.module()!=null || defaults.tests()!=null)
            throw new AssertionError("Default JVM profile changed");
        var selected = InterlisJvmBuild.parse(new String[]{"build","--module","interlis-model","--tests","ValueTest#valid"});
        if (!selected.profile().equals("build") || !selected.module().equals("interlis-model") || !selected.tests().equals("ValueTest#valid"))
            throw new AssertionError("Explicit JVM selection changed");
        for(String profile : java.util.List.of("contracts","sdk-check"))
            if(!InterlisJvmBuild.parse(new String[]{profile}).profile().equals(profile))throw new AssertionError(profile);
        var directory = java.nio.file.Files.createTempDirectory("ape-parser-");
        try {
            var report = build.interlis.ape.CompilerApe.parse(new String[]{"report", "--evidence", directory.toString()});
            if (report.operation()!=build.interlis.ape.CompilerApe.Operation.REPORT) throw new AssertionError();
            for (String[] invalid : new String[][]{{"report"},{"report","--evidence","relative"},{"report","--other",directory.toString()},{"inspect","--evidence",directory.toString()}}) {
                try { build.interlis.ape.CompilerApe.parse(invalid); throw new AssertionError("Accepted invalid APE arguments"); }
                catch (IllegalArgumentException expected) { }
            }
        } finally { java.nio.file.Files.delete(directory); }
        System.out.println("EXISTING_ENTRY_PASS");
    }
}

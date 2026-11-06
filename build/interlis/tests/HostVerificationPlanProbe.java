package build.interlis.tests;

import build.interlis.HostVerificationProfiles;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Checks the real host plan without executing benchmarks or starting browsers. */
final class HostVerificationPlanProbe {
    public static void main(String[] args) {
        var editor=HostVerificationProfiles.plan(List.of("full-corpus-editor-wasm"));
        equal(editor,List.of("full-corpus","full-corpus-editor-wasm"));
        equal(backendInvocations(editor),List.of("wasm-prepare"));
        var wasm=HostVerificationProfiles.plan(List.of("full-corpus-wasm"));
        equal(wasm,List.of("full-corpus","full-corpus-jvm","full-corpus-wasm"));
        equal(backendInvocations(wasm),List.of("wasm-prepare"));
        var browser=HostVerificationProfiles.plan(List.of("full-corpus-browsers"));
        equal(browser,List.of("full-corpus","full-corpus-jvm","full-corpus-browsers"));
        equal(backendInvocations(browser),List.of("browser-prepare"));

        var performance=HostVerificationProfiles.plan(List.of("performance","performance"));
        equal(performance,List.of("full-corpus","full-corpus-jvm","full-corpus-wasm","full-corpus-browsers",
                "full-corpus-safari","full-corpus-artifacts","full-corpus-editor-wasm","editor-pressure",
                "observation-pressure","performance-checker-test","performance"));
        equal(backendInvocations(performance),List.of("wasm-prepare","browser-prepare"));

        var release=HostVerificationProfiles.releasePlan();
        equal(release,List.of("python-free","sdk","platform","conformance","conformance-evidence","jvm",
                "portable","browser","reproducibility","release-evidence-checker-test","full-corpus",
                "full-corpus-jvm","full-corpus-wasm","full-corpus-browsers","full-corpus-safari",
                "full-corpus-artifacts","full-corpus-editor-wasm","editor-pressure","observation-pressure",
                "performance-checker-test","performance"));
        equal(backendInvocations(release),List.of("teavm-contracts","webimage-contracts","browser-contracts"));
        for(String profile:HostVerificationProfiles.PROFILES) {
            var plan=HostVerificationProfiles.plan(List.of(profile));
            equal(plan.getLast(),profile);
            equal(plan.size(),Set.copyOf(plan).size());
        }
        try {
            HostVerificationProfiles.plan(List.of("missing-profile"));
            throw new AssertionError("Unknown profile accepted");
        } catch(IllegalArgumentException expected) {
            equal(expected.getMessage(),"Unknown host profile: missing-profile");
        }
        System.out.println("HOST_VERIFICATION_PLAN_PASS editor="+editor+" release="+release);
    }

    private static List<String> backendInvocations(List<String> profiles) {
        var invocations=new ArrayList<String>();
        var covered=new HashSet<String>();
        for(String profile:profiles) for(String backend:HostVerificationProfiles.backends(profile)) {
            if(covered.contains(backend))continue;
            invocations.add(backend);
            covered.addAll(HostVerificationProfiles.backendCoverage(backend));
        }
        return invocations;
    }

    private static void equal(Object actual,Object expected) {
        if(!actual.equals(expected))throw new AssertionError("Expected "+expected+", got "+actual);
    }
}

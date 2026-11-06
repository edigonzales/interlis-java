package guru.interlis.verification;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;

/**
 * Reads executed JUnit reports. Identities come from native test events, never from an expected selection. Values and mappings are immutable.
 * Reports containing failures remain inspectable; requireSuccessful is the gate.
 */
public final class TestReportInventory {
    public record TestIdentity(String module, String className, String signature, String invocation) {
        public TestIdentity { Objects.requireNonNull(module); Objects.requireNonNull(className); Objects.requireNonNull(signature); Objects.requireNonNull(invocation); }
    }
    public record ExecutionReceipt(String module, String builder, String runId, Instant started,
            int exitCode, String execution, Map<String,String> reportHashes,
            Map<String,String> requiredProperties) {
        public ExecutionReceipt {
            Objects.requireNonNull(module); Objects.requireNonNull(builder); Objects.requireNonNull(runId); Objects.requireNonNull(started);
            reportHashes=Map.copyOf(reportHashes); requiredProperties=Map.copyOf(requiredProperties);
            if(!Set.of("EXECUTED","REUSED").contains(execution)||runId.isBlank())throw new IllegalArgumentException("Missing execution provenance");
        }

    }
    public record TestCase(TestIdentity identity, String displayName, String outcome, Path rawReport, String rawSha256, String nativeId) {}
    public record TestInventory(ExecutionReceipt receipt, List<TestCase> cases, Map<Path,String> rawBindings) {
        public TestInventory { cases=List.copyOf(cases); rawBindings=Map.copyOf(rawBindings); }
    }
    public record TestSelection(Set<TestIdentity> identities) {
        public TestSelection { identities=Set.copyOf(identities); if(identities.isEmpty())throw new IllegalArgumentException("Empty expected selection"); }
    }
    public record CanonicalReportSet(TestInventory original, Path directory, Map<Path,String> projectedHashes, String adapterHash) {
        public CanonicalReportSet { projectedHashes=Map.copyOf(projectedHashes); }
    }
    private static final Pattern METHOD=Pattern.compile("^([\\w$]+\\([^)]*\\))(?:\\s*\\[(\\d+)\\].*)?$");
    private static final Pattern NATIVE_METHOD=Pattern.compile("\\[(?:method|test-template):([^]]+)]");
    private static final Pattern NATIVE_INVOCATION=Pattern.compile("\\[test-template-invocation:#(\\d+)]");

    public TestInventory readJenesis(Path directory, ExecutionReceipt receipt) throws Exception {
        if(!receipt.builder().equals("jenesis"))throw new IllegalArgumentException("Foreign builder receipt");
        Map<Path,String> bindings=new TreeMap<>(); List<TestCase> cases=new ArrayList<>(); Set<TestIdentity> identities=new HashSet<>();
        Set<String> files=new TreeSet<>();
        try(var stream=Files.list(directory)) {
            for(Path raw:stream.filter(p->p.toString().endsWith(".xml")).sorted().toList()) {
                if(!Files.isRegularFile(raw,LinkOption.NOFOLLOW_LINKS))throw new IllegalArgumentException("Nonregular native report");
                String name=raw.getFileName().toString(),hash=HostChecks.hash(raw); files.add(name);
                if(!hash.equals(receipt.reportHashes().get(name)))throw new IllegalArgumentException("Unbound/changed native report: "+raw);
                bindings.put(raw.toAbsolutePath().normalize(),hash);
                Element suite=xml(raw).getDocumentElement();
                if(!name.startsWith("TEST-"))continue;
                Map<String,String> properties=new HashMap<>();var propertyNodes=suite.getElementsByTagName("property");
                for(int i=0;i<propertyNodes.getLength();i++) {
                    Element property=(Element)propertyNodes.item(i);
                    if(properties.put(property.getAttribute("name"),property.getAttribute("value"))!=null)throw new IllegalArgumentException("Duplicate reported property");
                }
                for(var expected:receipt.requiredProperties().entrySet())
                    if(!expected.getValue().equals(properties.get(expected.getKey())))throw new IllegalArgumentException("Foreign run/input JVM property: "+expected.getKey());
                if(!suite.getTagName().equals("testsuite")||suite.getAttribute("name").isBlank())throw new IllegalArgumentException("Missing test suite");
                var nodes=suite.getElementsByTagName("testcase"); int failed=0,errors=0,skipped=0;
                for(int i=0;i<nodes.getLength();i++) {
                    Element test=(Element)nodes.item(i);
                    if(test.getParentNode()!=suite)throw new IllegalArgumentException("Nested/malformed testcase");
                    String clazz=test.getAttribute("classname"),display=test.getAttribute("name"),nativeId="";
                    if(clazz.isBlank()||display.isBlank())throw new IllegalArgumentException("Missing/wrong testcase class");
                    String output="";for(int j=0;j<test.getElementsByTagName("system-out").getLength();j++)output+=test.getElementsByTagName("system-out").item(j).getTextContent();
                    var ids=output.lines().filter(l->l.startsWith("unique-id: ")).toList();
                    var names=output.lines().filter(l->l.startsWith("display-name: ")).toList();
                    if(ids.size()!=1||names.size()!=1)throw new IllegalArgumentException("Missing/ambiguous native test identity");
                    nativeId=ids.getFirst().substring(11); display=names.getFirst().substring(14);
                    if(!nativeId.contains("[class:"+clazz+"]"))throw new IllegalArgumentException("Foreign native class");
                    var method=NATIVE_METHOD.matcher(nativeId);if(!method.find())throw new IllegalArgumentException("Missing native method");
                    String signature=simpleSignature(method.group(1));var invocation=NATIVE_INVOCATION.matcher(nativeId);
                    String index=invocation.find()?invocation.group(1):"";
                    var declared=METHOD.matcher(test.getAttribute("name"));
                    if(!declared.matches()||!simpleSignature(declared.group(1)).equals(signature)||!Objects.toString(declared.group(2),"").equals(index))throw new IllegalArgumentException("Conflicting native identities");
                    var id=new TestIdentity(receipt.module(),clazz,signature,index);
                    if(!identities.add(id))throw new IllegalArgumentException("Duplicate test invocation: "+id);
                    int f=test.getElementsByTagName("failure").getLength(),e=test.getElementsByTagName("error").getLength(),s=test.getElementsByTagName("skipped").getLength();
                    if(f+e+s>1)throw new IllegalArgumentException("Conflicting testcase outcomes");
                    failed+=f;errors+=e;skipped+=s;
                    cases.add(new TestCase(id,display,f>0?"FAILED":e>0?"ERROR":s>0?"SKIPPED":"PASSED",raw.toAbsolutePath().normalize(),hash,nativeId));
                }
                for(var count:Map.of("tests",nodes.getLength(),"failures",failed,"errors",errors,"skipped",skipped).entrySet())
                    if(!suite.getAttribute(count.getKey()).equals(count.getValue().toString()))throw new IllegalArgumentException("Inconsistent JUnit counters: "+count.getKey());
            }
        }
        if(!files.equals(receipt.reportHashes().keySet())||cases.isEmpty())throw new IllegalArgumentException("Missing/empty required report set");
        verifyBindings(bindings);
        return new TestInventory(receipt,cases,bindings);
    }
    private static String simpleSignature(String value) {
        return value.replaceAll("(?:[a-zA-Z_$][\\w$]*\\.)+([A-Za-z_$][\\w$]*)","$1").replace(" ","");
    }
    public void requireCompleteSelection(TestInventory actual, TestSelection expected) {
        Set<TestIdentity> ids=new HashSet<>();actual.cases().forEach(c->ids.add(c.identity()));
        if(!ids.equals(expected.identities()))throw new IllegalArgumentException("Lost/extra executed test invocation");
    }
    public void requireSuccessful(TestInventory actual) {
        if(actual.receipt().exitCode()!=0||actual.cases().isEmpty()||actual.cases().stream().anyMatch(c->!c.outcome().equals("PASSED")))throw new IllegalArgumentException("Unsuccessful/empty execution");
    }
    public CanonicalReportSet projectPerSuite(TestInventory actual, Path target) throws Exception {
        verifyBindings(actual.rawBindings());
        if(Files.exists(target)) {try(var files=Files.list(target)){if(files.findAny().isPresent())throw new IllegalArgumentException("Projection target not empty");}}
        Files.createDirectories(target);Map<String,List<TestCase>> suites=new TreeMap<>();actual.cases().forEach(c->suites.computeIfAbsent(c.identity().className(),k->new ArrayList<>()).add(c));
        Map<Path,String> written=new TreeMap<>();
        for(var suite:suites.entrySet()) {
            if(!suite.getKey().matches("[\\w.$]+"))throw new IllegalArgumentException("Unsafe suite name");
            var doc=factory().newDocumentBuilder().newDocument();Element root=doc.createElement("testsuite");doc.appendChild(root);
            root.setAttribute("name",suite.getKey());root.setAttribute("tests",""+suite.getValue().size());
            root.setAttribute("timestamp",actual.receipt().started().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString());
            root.setAttribute("failures",""+suite.getValue().stream().filter(c->c.outcome().equals("FAILED")).count());
            root.setAttribute("errors",""+suite.getValue().stream().filter(c->c.outcome().equals("ERROR")).count());
            root.setAttribute("skipped",""+suite.getValue().stream().filter(c->c.outcome().equals("SKIPPED")).count());
            for(TestCase row:suite.getValue()) {
                Element testcase=doc.createElement("testcase");root.appendChild(testcase);testcase.setAttribute("classname",suite.getKey());testcase.setAttribute("name",row.displayName());
                var originalCases=xml(row.rawReport()).getElementsByTagName("testcase");Element original=null;
                for(int i=0;i<originalCases.getLength();i++) {
                    var candidate=(Element)originalCases.item(i);
                    boolean same=row.nativeId().isEmpty()?candidate.getAttribute("name").equals(row.displayName())
                            :candidate.getTextContent().contains("unique-id: "+row.nativeId()+"\n");
                    if(candidate.getAttribute("classname").equals(row.identity().className())&&same) {
                        if(original!=null)throw new IllegalArgumentException("Ambiguous raw projection row");original=candidate;
                    }
                }
                if(original==null)throw new IllegalArgumentException("Missing original projection row");
                if(original.hasAttribute("time"))testcase.setAttribute("time",original.getAttribute("time"));
                for(String kind:List.of("failure","error","skipped"))for(int i=0;i<original.getElementsByTagName(kind).getLength();i++)
                    testcase.appendChild(doc.importNode(original.getElementsByTagName(kind).item(i),true));
                Element origin=doc.createElement("system-out");origin.setTextContent("native-report: "+row.rawReport()+"\nsha256: "+row.rawSha256()+"\nnative-id: "+row.nativeId());testcase.appendChild(origin);
            }
            Path file=target.resolve("TEST-"+suite.getKey()+".xml");
            var transform=TransformerFactory.newInstance().newTransformer();transform.setOutputProperty(OutputKeys.ENCODING,StandardCharsets.UTF_8.name());
            try(var out=Files.newOutputStream(file)){transform.transform(new DOMSource(doc),new StreamResult(out));}written.put(file.toAbsolutePath().normalize(),HostChecks.hash(file));
        }
        var result=new CanonicalReportSet(actual,target.toAbsolutePath().normalize(),written,adapterHash());verifyRawBindings(result);return result;
    }
    public void verifyRawBindings(CanonicalReportSet derived) throws Exception {
        if(!derived.adapterHash().equals(adapterHash()))throw new IllegalArgumentException("Changed report adapter");
        verifyBindings(derived.original().rawBindings());verifyBindings(derived.projectedHashes());
        try(var files=Files.list(derived.directory())) {
            if(!new HashSet<>(files.map(p->p.toAbsolutePath().normalize()).toList()).equals(derived.projectedHashes().keySet()))throw new IllegalArgumentException("Projection file-set drift");
        }
    }
    private static void verifyBindings(Map<Path,String> bindings) throws Exception {
        for(var entry:bindings.entrySet())if(!Files.isRegularFile(entry.getKey(),LinkOption.NOFOLLOW_LINKS)||!HostChecks.hash(entry.getKey()).equals(entry.getValue()))throw new IllegalArgumentException("Changed report binding");
    }
    private static String adapterHash() throws Exception {
        try(var input=TestReportInventory.class.getResourceAsStream("TestReportInventory.class")){if(input==null)throw new IllegalArgumentException("Missing adapter bytecode");return HostChecks.hash(input.readAllBytes());}
    }
    private static DocumentBuilderFactory factory() throws Exception {
        var factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);factory.setFeature("http://xml.org/sax/features/external-general-entities",false);factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);factory.setXIncludeAware(false);factory.setExpandEntityReferences(false);return factory;
    }
    private static Document xml(Path file) throws Exception {try(var input=Files.newInputStream(file)){return factory().newDocumentBuilder().parse(input);}}
}

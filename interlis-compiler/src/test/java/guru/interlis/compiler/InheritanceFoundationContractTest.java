package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.compiler.query.LanguageQueries;
import guru.interlis.model.*;
import guru.interlis.source.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.compiler.CompilerTestSupport.*;

/** AC1-05/07: actual inheritance paths, effective positions and source-order-independent rules. */
class InheritanceFoundationContractTest {
    private static final CancellationToken TOKEN=CancellationToken.none();
    private static final List<String> VERSIONS=List.of("2.3","2.4");
    private static String model(String v,String name,String body) {
        return "INTERLIS "+v+";CONTRACTED MODEL "+name+" AT \"https://example.org\" VERSION \"1\"="+body+"END "+name+".";
    }
    private static CompilationRequest input(String v,String body) {
        var s=source("memory:/inheritance.ili",model(v,"F",body));
        return request(List.of("F"),Map.of(s.id(),s),List.of(),CompilerOptions.defaults());
    }
    private static CompilationOutcome.Succeeded ok(CompilationRequest in) {
        var out=CompilerEngines.create().compile(in,TOKEN);
        return assertInstanceOf(CompilationOutcome.Succeeded.class,out,()->out instanceof CompilationOutcome.Rejected r?r.analysis().diagnostics().toString():out.toString());
    }
    private static CompilationOutcome.Rejected bad(CompilationRequest in,String reason) {
        var out=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(in,TOKEN));
        assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),out.rejection().reasons());
        assertTrue(out.analysis().diagnostics().stream().anyMatch(d->d.arguments().toString().contains(reason)),()->out.analysis().diagnostics().toString());
        assertTrue(out.analysis().diagnostics().stream().allMatch(d->d.failureId().isEmpty()));
        for(var d:out.analysis().diagnostics().stream().filter(d->d.arguments().toString().contains(reason)).toList()) {
            assertEquals("ILI-SEM-0010",d.code().value());assertEquals(guru.interlis.diagnostics.Severity.ERROR,d.severity());
            assertEquals(guru.interlis.diagnostics.Phase.SEMANTIC_CHECKING,d.phase());
            var range=d.primaryRange().orElseThrow();var source=in.sources().sources().get(range.source());
            assertNotNull(source);assertEquals(source.revision(),range.revision());assertFalse(source.slice(range).isEmpty());
        }
        return out;
    }
    private static Symbol named(SemanticGraph g,String name) {
        return g.symbols().stream().filter(s->s.name().value().equals(name)).findFirst().orElseThrow();
    }
    private static AttributeSymbol attribute(SemanticGraph g,String owner,String name) {
        return g.symbols().stream().filter(AttributeSymbol.class::isInstance).map(AttributeSymbol.class::cast)
                .filter(a->a.name().value().equals(name)&&a.owner().filter(named(g,owner).id()::equals).isPresent()).findFirst().orElseThrow();
    }
    private static String family(String kind) {
        return "TOPIC T="+kind+" Root=alpha:TEXT*10;beta:0..10;END Root;"
                +kind+" Middle EXTENDS Root=alpha(EXTENDED):TEXT*5;middle:BOOLEAN;END Middle;"
                +kind+" Leaf EXTENDS Middle=beta(EXTENDED):0..5;leaf:TEXT*3;END Leaf;END T;";
    }
    @Test void effectivePathsRunFromTheActualDeclaringOwnerThroughEveryIntermediateOwner() {
        for(String v:VERSIONS)for(String kind:List.of("CLASS","STRUCTURE")) {
            var g=ok(input(v,family(kind))).schema().semanticGraph();
            var root=named(g,"Root");var middle=named(g,"Middle");var leaf=named(g,"Leaf");
            var members=g.effectiveMembers(leaf.id());
            assertEquals(List.of(attribute(g,"Middle","alpha").id(),attribute(g,"Leaf","beta").id(),
                    attribute(g,"Middle","middle").id(),attribute(g,"Leaf","leaf").id()),members.stream().map(EffectiveMember::declaredSymbol).toList());
            assertEquals(List.of(List.of(middle.id(),leaf.id()),List.of(leaf.id()),List.of(middle.id(),leaf.id()),List.of(leaf.id())),
                    members.stream().map(EffectiveMember::inheritancePath).toList());
            assertEquals(List.of(EffectiveMemberOrigin.INHERITED,EffectiveMemberOrigin.OVERRIDDEN,EffectiveMemberOrigin.INHERITED,EffectiveMemberOrigin.LOCAL),
                    members.stream().map(EffectiveMember::origin).toList());
            assertEquals(List.of(true,true,false,false),members.stream().map(EffectiveMember::overridden).toList());
            assertEquals(List.of(0,1,2,3),members.stream().map(EffectiveMember::position).toList());
            assertTrue(members.stream().allMatch(m->m.effectiveOwner().equals(leaf.id())));
            assertEquals(List.of(root.id(),middle.id()),g.effectiveMembers(middle.id()).get(1).inheritancePath());
            assertEquals(List.of(root.id()),g.effectiveMembers(root.id()).getFirst().inheritancePath());
            assertEquals(attribute(g,"Root","alpha").id(),attribute(g,"Middle","alpha").overrides().orElseThrow());
            assertEquals(attribute(g,"Root","beta").id(),attribute(g,"Leaf","beta").overrides().orElseThrow());
            assertThrows(UnsupportedOperationException.class,()->members.add(members.getFirst()));
            assertThrows(UnsupportedOperationException.class,()->members.getFirst().inheritancePath().add(root.id()));
        }
    }
    private static CompilationRequest imported(String v,String base,String child,boolean baseFirst,long revision) {
        var b=new SourceText(new SourceId("memory:/"+(baseFirst?"a":"z")+"-base.ili"),new SourceRevision(revision),model(v,"F",base));
        var use=source("memory:/"+(baseFirst?"z":"a")+"-use.ili",model(v,"M","IMPORTS F;"+child));
        var sources=new LinkedHashMap<SourceId,SourceText>();sources.put(use.id(),use);sources.put(b.id(),b);
        return request(List.of("M"),sources,List.of(binding(parse(use),"F",b,parse(b))),CompilerOptions.defaults());
    }
    @Test void finalImportedClassesAndStructuresAreRejectedInBothCanonicalSourceOrders() {
        for(String v:VERSIONS)for(boolean order:List.of(true,false))for(String kind:List.of("CLASS","STRUCTURE")) {
            String base="TOPIC T="+kind+" Parent(FINAL)=value:TEXT*10;END Parent;END T;";
            String use="TOPIC T=DEPENDS ON F.T;"+kind+" Child EXTENDS F.T.Parent=END Child;END T;";
            bad(imported(v,base,use,order,7),"OBJECT_FINAL");
            ok(imported(v,base.replace("(FINAL)",""),use,order,8));
        }
    }
    @Test void finalAttributesCannotBeOverriddenEvenByAnOtherwiseValidRestriction() {
        for(String v:VERSIONS)for(String kind:List.of("CLASS","STRUCTURE"))for(String type:List.of("TEXT*5","MANDATORY")) {
            String body="TOPIC T="+kind+" Parent=value(FINAL):TEXT*10;END Parent;"
                    +kind+" Child EXTENDS Parent=value(EXTENDED):"+type+";END Child;END T;";
            bad(input(v,body),"ATTRIBUTE_FINAL");
            ok(input(v,body.replace("value(FINAL)","value")));
        }
    }
    @Test void extendedAttributeRequiresAnActualInheritedAttribute() {
        for(String v:VERSIONS)for(String kind:List.of("CLASS","STRUCTURE")) {
            bad(input(v,"TOPIC T="+kind+" C=value(EXTENDED):TEXT*10;END C;END T;"),"EXTENDED_BASE");
            bad(input(v,"TOPIC T="+kind+" Parent=other:TEXT*10;END Parent;"
                    +kind+" Child EXTENDS Parent=value(EXTENDED):TEXT*10;END Child;END T;"),"EXTENDED_BASE");
        }
    }
    @Test void finalTopicsCannotBeExtended() {
        for(String v:VERSIONS) {
            var body="TOPIC Parent(FINAL)=CLASS C=END C;END Parent;TOPIC Child EXTENDS Parent=END Child;";
            bad(input(v,body),"TOPIC_FINAL");
            ok(input(v,body.replace("(FINAL)","")));
        }
    }
    private static String association(String value,String childValue) {
        return "TOPIC T=OID AS INTERLIS.UUIDOID;CLASS A=END A;CLASS A1 EXTENDS A=END A1;CLASS B=END B;"
                +"ASSOCIATION Root=left--{1}A;right--{1}B;value:"+value+";END Root;"
                +"ASSOCIATION Middle EXTENDS Root=left(EXTENDED)--{1}A1;value(EXTENDED):"+childValue+";END Middle;"
                +"ASSOCIATION Leaf EXTENDS Middle=tail:BOOLEAN;END Leaf;END T;";
    }
    @Test void associationsKeepActualRoleTargetsAttributePositionsAndEveryInheritanceStep() {
        for(String v:VERSIONS) {
            var out=ok(input(v,association("TEXT*10","TEXT*5")));var g=out.schema().semanticGraph();
            var root=named(g,"Root");var middle=named(g,"Middle");var leaf=named(g,"Leaf");
            var members=out.analysis().queries().effectiveMembers(new SymbolRef(g.snapshotId(),leaf.id())).known().orElseThrow();
            assertEquals(List.of("left","right","value","tail"),members.stream().map(m->g.symbol(m.declaredSymbol()).orElseThrow().name().value()).toList());
            assertEquals(List.of(List.of(middle.id(),leaf.id()),List.of(root.id(),middle.id(),leaf.id()),
                    List.of(middle.id(),leaf.id()),List.of(leaf.id())),members.stream().map(EffectiveMember::inheritancePath).toList());
            assertEquals(List.of(EffectiveMemberOrigin.INHERITED,EffectiveMemberOrigin.INHERITED,EffectiveMemberOrigin.INHERITED,EffectiveMemberOrigin.LOCAL),
                    members.stream().map(EffectiveMember::origin).toList());
            var role=assertInstanceOf(RoleSymbol.class,g.symbol(members.getFirst().declaredSymbol()).orElseThrow());
            assertEquals(List.of(named(g,"A1").id()),role.targetRoots());
            assertEquals(Cardinality.exactly(1),role.cardinality());
            assertEquals(attribute(g,"Middle","value").id(),members.get(2).declaredSymbol());
            assertEquals(attribute(g,"Root","value").id(),attribute(g,"Middle","value").overrides().orElseThrow());
        }
    }
    @Test void associationAttributesUseTheSameOverrideRulesAsClassAttributes() {
        for(String v:VERSIONS) {
            bad(input(v,association("TEXT*5","TEXT*10")),"ATTRIBUTE_OVERRIDE_TYPE");
            bad(input(v,association("MANDATORY TEXT*10","TEXT*5")),"ATTRIBUTE_OVERRIDE_MANDATORY");
            ok(input(v,association("TEXT*10","MANDATORY")));
        }
    }
    @Test void inheritedAbstractRoleDeclarationsKeepTheirIdentityAndEstablishedProfile() {
        for(String v:VERSIONS) {
            String roles="TOPIC T=OID AS INTERLIS.UUIDOID;CLASS A=END A;CLASS B=END B;ASSOCIATION Root(ABSTRACT)="
                    +"left(ABSTRACT)--{1}A;right--{1}B;END Root;ASSOCIATION Leaf EXTENDS Root=END Leaf;END T;";
            var g=ok(input(v,roles)).schema().semanticGraph();
            var member=g.effectiveMembers(named(g,"Leaf").id()).getFirst();
            var role=assertInstanceOf(RoleSymbol.class,g.symbol(member.declaredSymbol()).orElseThrow());
            assertTrue(role.header().has(Modifier.ABSTRACT));assertEquals(named(g,"Root").id(),role.owner().orElseThrow());
            assertEquals(List.of(named(g,"Root").id(),named(g,"Leaf").id()),member.inheritancePath());
            assertEquals(EffectiveMemberOrigin.INHERITED,member.origin());
            ok(input(v,roles.replace("EXTENDS Root=END Leaf","EXTENDS Root=left(EXTENDED)--{1}A;END Leaf")));
            bad(input(v,roles.replace("Root(ABSTRACT)","Root")),"ABSTRACT_ROLE");
        }
    }
    @Test void concreteAssociationsMustConcretizeInheritedAbstractAttributes() {
        for(String v:VERSIONS) {
            String roles="TOPIC T=OID AS INTERLIS.UUIDOID;CLASS A=END A;CLASS B=END B;ASSOCIATION Root(ABSTRACT)="
                    +"left--{1}A;right--{1}B;END Root;ASSOCIATION Leaf EXTENDS Root=END Leaf;END T;";
            String attrs=roles.replace("left(ABSTRACT)","left").replace("END Root;","value(ABSTRACT):TEXT*10;END Root;");
            bad(input(v,attrs),"ABSTRACT_MEMBER");
            ok(input(v,attrs.replace("EXTENDS Root=END Leaf","EXTENDS Root=value(EXTENDED):TEXT*5;END Leaf")));
        }
    }
    @Test void importedEditsKeepPublicOrderPathsAndQueriesEqualToFreshAnalysis() {
        for(String v:VERSIONS)for(boolean order:List.of(true,false)) {
            var engine=CompilerEngines.create();AnalysisSnapshot previous=null;List<EffectiveMember> published=null;SemanticGraph original=null;
            String use="TOPIC T=DEPENDS ON F.T;STRUCTURE Leaf EXTENDS F.T.Middle=alpha(EXTENDED):TEXT*5;END Leaf;END T;";
            for(int revision=1;revision<=4;revision++) {
                String base="TOPIC T=STRUCTURE Root="+(revision<3?"alpha"+(revision==2?"(FINAL)":"")+":TEXT*10;beta:0..10;":"beta:0..10;alpha:TEXT*10;")
                        +"END Root;STRUCTURE Middle EXTENDS Root="+(revision<4?"middle":"tail")+":BOOLEAN;END Middle;END T;";
                var in=imported(v,base,use,order,revision);
                var reused=engine.analyze(in,previous==null?AnalysisReuse.none():new AnalysisReuse(Optional.of(previous),previous.syntaxSnapshots()),TOKEN);
                var fresh=CompilerEngines.create().analyze(in,TOKEN);
                assertEquals(fresh.diagnostics(),reused.diagnostics());assertEquals(revision!=2,reused.diagnostics().isEmpty());
                var g=reused.semanticGraph();var leaf=named(g,"Leaf");
                var members=reused.queries().effectiveMembers(new SymbolRef(g.snapshotId(),leaf.id())).known().orElseThrow();
                // Reuse may retain stable declaration IDs that a fresh index
                // allocates differently after reordering. Compare bound meaning.
                assertEquals(memberShapes(fresh.semanticGraph(),fresh.semanticGraph().effectiveMembers(named(fresh.semanticGraph(),"Leaf").id())),memberShapes(g,members));
                assertEquals(revision<3?List.of("alpha","beta","middle"):revision==3?List.of("beta","alpha","middle"):List.of("beta","alpha","tail"),
                        members.stream().map(m->g.symbol(m.declaredSymbol()).orElseThrow().name().value()).toList());
                var baseSource=in.sources().sources().values().stream().filter(s->s.text().contains("MODEL F ")).findFirst().orElseThrow();
                var useSource=in.sources().sources().values().stream().filter(s->s.text().contains("MODEL M ")).findFirst().orElseThrow();
                int cursor=useSource.text().indexOf("F.T.Middle")+4;
                var definition=LanguageQueries.create().definition(reused,useSource.range(cursor,cursor),TOKEN).known().orElseThrow();
                assertEquals(named(g,"Middle").id(),definition.symbol().symbol());
                assertEquals(baseSource.id(),definition.nameRange().source());assertEquals(new SourceRevision(revision),definition.nameRange().revision());
                assertEquals("Middle",baseSource.slice(definition.nameRange()));
                if(revision==1){published=members;original=g;}
                previous=reused;
            }
            var frozen=original;
            assertEquals(List.of("alpha","beta","middle"),published.stream().map(m->originalName(m,frozen)).toList());
        }
    }
    private static String originalName(EffectiveMember member,SemanticGraph g) {return g.symbol(member.declaredSymbol()).orElseThrow().name().value();}
    private static List<List<Object>> memberShapes(SemanticGraph g,List<EffectiveMember> members) {
        return members.stream().map(m->{var declaration=g.symbol(m.declaredSymbol()).orElseThrow();
            return List.<Object>of(g.symbol(declaration.owner().orElseThrow()).orElseThrow().name().value(),declaration.name().value(),
                    g.symbol(m.effectiveOwner()).orElseThrow().name().value(),m.inheritancePath().stream().map(id->g.symbol(id).orElseThrow().name().value()).toList(),
                    m.position(),m.origin(),m.overridden());}).toList();
    }
    @Test void cyclicTopicClassAndStructureInheritanceCannotProduceACompiledSchema() {
        for(String v:VERSIONS)for(String body:List.of("TOPIC A EXTENDS B=END A;TOPIC B EXTENDS A=END B;",
                "TOPIC T=CLASS A EXTENDS B=END A;CLASS B EXTENDS A=END B;END T;",
                "STRUCTURE A EXTENDS B=END A;STRUCTURE B EXTENDS A=END B;")) {
            var out=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(v,body),TOKEN));
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),out.rejection().reasons());
            assertTrue(out.analysis().diagnostics().stream().anyMatch(d->d.code().value().equals("ILI-SEM-0006")));
            assertTrue(out.analysis().diagnostics().stream().allMatch(d->d.failureId().isEmpty()));
        }
    }
    @Test void extendedTopicClassesKeepDistinctBindingsInheritedPropertiesAndOidProvenance() {
        for(String v:VERSIONS) {
            var out=ok(input(v,"TOPIC Base=OID AS INTERLIS.UUIDOID;CLASS A=inherited:MANDATORY TEXT*10;END A;END Base;"
                    +"TOPIC Child EXTENDS Base=CLASS A(EXTENDED)=local:BOOLEAN;END A;END Child;"));
            var g=out.schema().semanticGraph();var q=out.analysis().queries();
            var base=assertInstanceOf(TopicSymbol.class,named(g,"Base"));var child=assertInstanceOf(TopicSymbol.class,named(g,"Child"));
            var baseClass=assertInstanceOf(ClassSymbol.class,q.symbol(q.findQualifiedName(QualifiedName.of("F","Base","A")).getFirst()).known().orElseThrow());
            var childClass=assertInstanceOf(ClassSymbol.class,q.symbol(q.findQualifiedName(QualifiedName.of("F","Child","A")).getFirst()).known().orElseThrow());
            assertNotEquals(baseClass.id(),childClass.id());assertEquals(base.id(),child.baseTopic().orElseThrow());
            assertEquals(baseClass.id(),childClass.baseType().orElseThrow());assertTrue(childClass.header().has(Modifier.EXTENDED));
            assertEquals(base.header().oidRules(),child.header().oidRules());
            assertEquals(baseClass.header().oidRules(),childClass.header().oidRules());
            var members=q.effectiveMembers(new SymbolRef(g.snapshotId(),childClass.id())).known().orElseThrow();
            assertEquals(List.of("inherited","local"),members.stream().map(m->g.symbol(m.declaredSymbol()).orElseThrow().name().value()).toList());
            var inherited=assertInstanceOf(AttributeSymbol.class,g.symbol(members.getFirst().declaredSymbol()).orElseThrow());
            assertTrue(inherited.mandatory());assertEquals(baseClass.id(),inherited.owner().orElseThrow());
            assertEquals(List.of(baseClass.id(),childClass.id()),members.getFirst().inheritancePath());
            assertEquals(EffectiveMemberOrigin.INHERITED,members.getFirst().origin());
            assertInstanceOf(Origin.DeclaredOrigin.class,inherited.origin());
        }
    }
}

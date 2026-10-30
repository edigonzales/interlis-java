package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.compiler.CompilerTestSupport.*;

/** AC2-02: actual public declarations and calls, not a count of registry entries. */
class BuiltinSignatureFoundationContractTest {
    private static final CancellationToken TOKEN=CancellationToken.none();
    private static Map<String,List<String>> signatures(String v) {
        var m=new LinkedHashMap<String,List<String>>();
        m.put("len",List.of("NUMERIC","TEXT"));m.put("lenM",List.of("NUMERIC","MTEXT"));
        m.put("trim",List.of("TEXT","TEXT"));m.put("trimM",List.of("MTEXT","MTEXT"));
        m.put("objectCount",List.of("NUMERIC","OBJECTS"));m.put("elementCount",List.of("NUMERIC","BAG"));
        m.put("myClass",List.of("STRUCTURE","ANYSTRUCTURE"));m.put("isSubClass",List.of("BOOLEAN","STRUCTURE","STRUCTURE"));
        m.put("isOfClass",List.of("BOOLEAN","ANYSTRUCTURE","STRUCTURE"));
        m.put("isEnumSubVal",List.of("BOOLEAN","ENUMTREEVAL","ENUMTREEVAL"));
        m.put("inEnumRange",List.of("BOOLEAN","ENUMVAL","ENUMTREEVAL","ENUMTREEVAL"));
        m.put("convertUnit",List.of("NUMERIC","NUMERIC"));
        m.put("areAreas",List.of("BOOLEAN","OBJECTS","ATTRIBUTE","ATTRIBUTE"));
        if(v.equals("2.4")) {
            m.put("length",List.of("NUMERIC","POLYLINE"));m.put("multilength",List.of("NUMERIC","MULTIPOLYLINE"));
            m.put("surface",List.of("NUMERIC","SURFACE"));m.put("multisurface",List.of("NUMERIC","MULTISURFACE"));
            m.put("areAreas2",List.of("BOOLEAN","OBJECTS","TEXT","TEXT"));
            m.put("areAreas3",List.of("BOOLEAN","OBJECTS","TEXT","TEXT"));
            m.put("areAreasWithoutGaps",List.of("BOOLEAN","OBJECTS","ATTRIBUTE","ATTRIBUTE"));
            m.put("areAreasWithoutGaps2",List.of("BOOLEAN","OBJECTS","TEXT","TEXT"));
        }
        return m;
    }
    private static CompilationRequest input(String v,String body) {
        var text=source("memory:/builtin-foundation.ili","INTERLIS "+v+";CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"="+body+"END F.");
        return request(List.of("F"),Map.of(text.id(),text),List.of(),CompilerOptions.defaults());
    }
    private static CompilationOutcome.Succeeded ok(String v,String body) {
        var out=CompilerEngines.create().compile(input(v,body),TOKEN);
        return assertInstanceOf(CompilationOutcome.Succeeded.class,out,()->out instanceof CompilationOutcome.Rejected r?r.analysis().diagnostics().toString():out.toString());
    }
    private static String tag(TypeDescriptor type) {
        if(type instanceof TextType text)return text.textKind().name();
        if(type instanceof ObjectArgumentType object)return object.objects()?"OBJECTS":"OBJECT";
        if(type instanceof ClassValueType value)return value.structureValue()?"STRUCTURE":"CLASS";
        if(type instanceof StructureValueType)return "ANYSTRUCTURE";
        if(type instanceof EnumArgumentType enumeration)return enumeration.treeValues()?"ENUMTREEVAL":"ENUMVAL";
        if(type instanceof AttributePathType)return "ATTRIBUTE";
        if(type instanceof CollectionType collection)return collection.collectionKind().name();
        if(type instanceof GeometryType geometry)return geometry.geometryKind().name();
        return type.kind().name();
    }
    @Test void allAvailableBuiltinsKeepTheirRealSignaturesAndBoundCallsInCompiledSchema() {
        for(String v:List.of("2.3","2.4")) {
            String body="DOMAIN P=COORD 0.000..100.000,0.000..100.000;STRUCTURE Item=code:TEXT;END Item;"
                    +"TOPIC T=CLASS C=txt:TEXT;multiText:MTEXT;e:(a(x,y),b) ORDERED;items:BAG OF Item;n:0..100[INTERLIS.m];"
                    +"shape:SURFACE WITH(STRAIGHTS) VERTEX P WITHOUT OVERLAPS >0.010;"
                    +"converted(TRANSIENT):0..100[INTERLIS.m]:=INTERLIS.convertUnit(n);"
                    +"MANDATORY CONSTRAINT INTERLIS.len(txt)>=0 AND INTERLIS.lenM(multiText)>=0 AND DEFINED(INTERLIS.trim(txt))"
                    +" AND DEFINED(INTERLIS.trimM(multiText)) AND INTERLIS.elementCount(items)>=0"
                    +" AND DEFINED(INTERLIS.myClass(THIS)) AND INTERLIS.isSubClass(>C,>C) AND INTERLIS.isOfClass(THIS,>C)"
                    +" AND INTERLIS.isEnumSubVal(e,#a) AND INTERLIS.inEnumRange(e,#a.x,#b);"
                    +"SET CONSTRAINT INTERLIS.objectCount(ALL)>=0;SET CONSTRAINT INTERLIS.areAreas(ALL,UNDEFINED,>>shape);";
            if(v.equals("2.4"))body+="line:POLYLINE WITH(STRAIGHTS) VERTEX P;multiLine:MULTIPOLYLINE WITH(STRAIGHTS) VERTEX P;"
                    +"multiShape:MULTISURFACE WITH(STRAIGHTS) VERTEX P WITHOUT OVERLAPS >0.010;"
                    +"MANDATORY CONSTRAINT INTERLIS.length(line)>=0 AND INTERLIS.multilength(multiLine)>=0"
                    +" AND INTERLIS.surface(shape)>=0 AND INTERLIS.multisurface(multiShape)>=0;"
                    +"SET CONSTRAINT INTERLIS.areAreas2(UNDEFINED,UNDEFINED,\"shape\");"
                    +"SET CONSTRAINT INTERLIS.areAreas3(UNDEFINED,UNDEFINED,\"shape\");"
                    +"SET CONSTRAINT INTERLIS.areAreasWithoutGaps(ALL,UNDEFINED,>>shape);"
                    +"SET CONSTRAINT INTERLIS.areAreasWithoutGaps2(ALL,UNDEFINED,\"shape\");";
            var out=ok(v,body+"END C;END T;");var g=out.schema().semanticGraph();
            var calls=g.expressions().stream().filter(FunctionCallExpression.class::isInstance).map(FunctionCallExpression.class::cast).toList();
            var expected=signatures(v);assertEquals(v.equals("2.3")?13:21,calls.size());
            assertEquals(expected.keySet(),new HashSet<>(calls.stream().map(c->c.functionName().substring("INTERLIS.".length())).toList()));
            for(var call:calls) {
                assertEquals(ExpressionTypeState.RESOLVED,call.typeState());
                var function=assertInstanceOf(FunctionSymbol.class,g.symbol(call.targetFunction().orElseThrow()).orElseThrow());
                var signature=new ArrayList<String>();signature.add(tag(g.type(function.returnType()).orElseThrow()));
                function.parameters().forEach(p->signature.add(tag(g.type(p.typeId()).orElseThrow())));
                assertEquals(expected.get(call.functionName().substring("INTERLIS.".length())),signature);
                assertEquals(function.parameters().size(),call.children().size());
                assertEquals(v.equals("2.3")?LanguageVersion.ILI23:LanguageVersion.ILI24,function.header().languageVersion());
                var origin=assertInstanceOf(Origin.PredefinedOrigin.class,function.origin());assertEquals(call.functionName(),origin.key());
                assertInstanceOf(ModelSymbol.class,g.symbol(function.owner().orElseThrow()).orElseThrow());
                assertTrue(function.parameters().stream().noneMatch(FunctionParameter::mandatory));assertFalse(function.returnMandatory());
                if(function.name().value().equals("INTERLIS.areAreas")||function.name().value().equals("INTERLIS.areAreasWithoutGaps"))
                    for(int i=1;i<3;i++)assertEquals(new AttributePathType.Argument(function.id(),i-1),
                            assertInstanceOf(AttributePathType.class,g.type(function.parameters().get(i).typeId()).orElseThrow()).owner().orElseThrow());
            }
        }
    }
    @Test void everyBuiltinRejectsExtraArgumentsWithoutAnInternalFailureOrAnInventedSchema() {
        for(String v:List.of("2.3","2.4"))for(var signature:signatures(v).entrySet()) {
            var args=Collections.nCopies(signature.getValue().size(),"UNDEFINED"); // one more than the formal count
            var out=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(v,
                    "TOPIC T=CLASS C=SET CONSTRAINT DEFINED(INTERLIS."+signature.getKey()+"("+String.join(",",args)+"));END C;END T;"),TOKEN),signature.getKey());
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),out.rejection().reasons());
            assertTrue(out.analysis().diagnostics().stream().anyMatch(d->d.code().value().equals("ILI-SEM-0005")));
            assertTrue(out.analysis().diagnostics().stream().allMatch(d->d.failureId().isEmpty()));
        }
    }
}

package guru.interlis.compiler.internal.antlr;

import guru.interlis.model.LanguageVersion;

import java.util.Optional;

/** Explicit version profile for grammar constructs whose availability differs by INTERLIS version. */
public final class LanguageProfile {
    enum Feature {
        TEMPORAL_TYPES("DATE/TIMEOFDAY/DATETIME"),
        MODEL_TRANSFER_OPTIONS("model transfer options"),
        MULTICOORD("MULTICOORD"),
        MULTIGEOMETRY("MULTIPOLYLINE/MULTISURFACE/MULTIAREA"),
        COORDINATE_CRS("REFSYS"),
        GENERIC_DOMAIN("GENERIC domain"),
        DEFERRED_GENERICS("DEFERRED GENERICS"),
        CONTEXT_DEFINITION("CONTEXT"),
        DOMAIN_CONSTRAINT("domain constraint"),
        NAMED_OR_BASKET_CONSTRAINT("named or basket constraint"),
        INLINE_ENUM_COLLECTION("inline enumeration collection"),
        EMPTY_FUNCTION_PARAMETERS("empty function parameter list"),
        EMPTY_FUNCTION_ARGUMENTS("empty function argument list");

        private final String label;

        Feature(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    private LanguageProfile() {
    }

    // Pinned reference manuals: 2.3 §2.2.7 and 2.4 §3.2.7, Table 1.
    private static final java.util.Set<String> RESERVED_23=words("""
ABSTRACT ACCORDING AGGREGATES AGGREGATION ALL AND ANY ANYCLASS ANYSTRUCTURE ARCS AREA AS ASSOCIATION AT ATTRIBUTE ATTRIBUTES BAG BASE BASED BASKET BINARY BLACKBOX BLANK BOOLEAN BY CARDINALITY CIRCULAR CLASS CLOCKWISE CODE CONSTRAINT CONSTRAINTS CONTINUE CONTINUOUS CONTOUR CONTRACTED COORD COORD2 COORD3 COUNTERCLOCKWISE DATE DEFAULT DEFINED DEGREES DEPENDS DERIVATIVES DERIVED DIM1 DIM2 DIRECTED DOMAIN END ENUMTREEVAL ENUMVAL EQUAL EXISTENCE EXTENDED EXTENDS EXTERNAL FINAL FIRST FIX FONT FORM FORMAT FREE FROM FUNCTION GRADS GRAPHIC HALIGNMENT HIDING I16 I32 IDENT IMPORTS IN INHERITANCE INSPECTION INTERLIS JOIN LAST LINE LINEATTR LINESIZE LIST LNBASE LOCAL MANDATORY METAOBJECT MODEL MTEXT NAME NO NOT NULL NUMERIC OBJECT OBJECTS OF OID ON OPTIONAL OR ORDERED OTHERS OVERLAPS PARAMETER PARENT PERIPHERY PI POLYLINE PROJECTION RADIANS REFERENCE REFSYSTEM REQUIRED RESTRICTION ROTATION SET SIGN STRAIGHTS STRUCTURE SUBDIVISION SURFACE SYMBOLOGY TABLE TEXT THATAREA THIS THISAREA TID TIDSIZE TO TOPIC TRANSFER TRANSIENT TRANSLATION TYPE UNDEFINED UNION UNIQUE UNIT UNQUALIFIED URI VALIGNMENT VERSION VERTEX VERTEXINFO VIEW WHEN WHERE WITH WITHOUT
""");
    private static final java.util.Set<String> RESERVED_24=words("""
ABSTRACT ACCORDING AGGREGATES AGGREGATION ALL AND ANY ANYCLASS ANYSTRUCTURE ARCS AREA AS ASSOCIATION AT ATTRIBUTE ATTRIBUTES BAG BASE BASED BASKET BINARY BLACKBOX BLANK BOOLEAN BY CARDINALITY CHARSET CIRCULAR CLASS CLOCKWISE CODE CONSTRAINT CONSTRAINTS CONTEXT CONTINUE CONTINUOUS CONTOUR CONTRACTED COORD COORD2 COORD3 COUNTERCLOCKWISE DATE DATETIME DEFAULT DEFERRED DEFINED DEGREES DEPENDS DERIVATIVES DERIVED DIM1 DIM2 DIRECTED DOMAIN END ENUMTREEVAL ENUMVAL EQUAL EXISTENCE EXTENDED EXTENDS EXTERNAL FINAL FIRST FIX FONT FORM FORMAT FREE FROM FUNCTION GENERIC GENERICS GRADS GRAPHIC HALIGNMENT HIDING I16 I32 IDENT IMPORTS IN INHERITANCE INSPECTION INTERLIS JOIN LAST LINE LINEATTR LINESIZE LIST LNBASE LOCAL MANDATORY METAOBJECT MODEL MTEXT MULTIAREA MULTICOORD MULTIPOLYLINE MULTISURFACE NAME NO NOINCREMENTALTRANSFER NOT NULL NUMERIC OBJECT OBJECTS OF OID ON OPTIONAL OR ORDERED OTHERS OVERLAPS PARAMETER PARENT PERIPHERY PI POLYLINE PROJECTION RADIANS REFERENCE REFSYS REFSYSTEM REQUIRED RESTRICTION ROTATION SET SIGN STRAIGHTS STRUCTURE SUBDIVISION SURFACE SYMBOLOGY TABLE TEXT THATAREA THIS THISAREA TID TIDSIZE TIMEOFDAY TO TOPIC TRANSFER TRANSIENT TRANSLATION TYPE UNDEFINED UNION UNIQUE UNIT UNQUALIFIED URI VALIGNMENT VERSION VERTEX VERTEXINFO VIEW WHEN WHERE WITH WITHOUT XMLNS
""");
    private static java.util.Set<String> words(String text) { return java.util.Set.copyOf(java.util.Arrays.asList(text.strip().split("\\s+"))); }

    /** Version-specific identifier validation shared with rename. Lexer objects
     * remain local; no source or graph is retained. */
    public static boolean isIdentifier(String text,LanguageVersion language) {
        java.util.Objects.requireNonNull(text);java.util.Objects.requireNonNull(language);
        if(text.length()>255)return false;
        var lexer=new InterlisLexer(org.antlr.v4.runtime.CharStreams.fromString(text));lexer.removeErrorListeners();
        var token=lexer.nextToken();return classifiedType(token,language)==InterlisLexer.Name && text.equals(token.getText())
                && lexer.nextToken().getType()==org.antlr.v4.runtime.Token.EOF;
    }

    static void classify(org.antlr.v4.runtime.CommonTokenStream tokens,LanguageVersion language) {
        var visible=tokens.getTokens().stream().filter(t->t.getChannel()==org.antlr.v4.runtime.Token.DEFAULT_CHANNEL).toList();
        int previous=org.antlr.v4.runtime.Token.INVALID_TYPE;
        for(int i=0;i<visible.size();i++) {
            var token=visible.get(i);int next=i+1<visible.size()?visible.get(i+1).getType():org.antlr.v4.runtime.Token.EOF;
            int type=classifiedType(token,language);
            // Nonreserved contextual words still carry grammar meaning at these sites.
            boolean contextual=previous==InterlisLexer.BLACKBOX && token.getType()==InterlisLexer.XML
                    || language==LanguageVersion.ILI23 && switch(token.getType()) {
                        case InterlisLexer.NOINCREMENTALTRANSFER -> next==InterlisLexer.MODEL;
                        case InterlisLexer.CHARSET,InterlisLexer.XMLNS,InterlisLexer.REFSYS -> next==InterlisLexer.STRING;
                        case InterlisLexer.CONTEXT -> next==InterlisLexer.Name;
                        case InterlisLexer.DEFERRED -> next==InterlisLexer.GENERICS;
                        case InterlisLexer.MULTICOORD -> next==InterlisLexer.NUMERIC || next==InterlisLexer.PosNumber || next==InterlisLexer.Number || next==InterlisLexer.Dec;
                        case InterlisLexer.MULTIPOLYLINE,InterlisLexer.MULTISURFACE,InterlisLexer.MULTIAREA -> next==InterlisLexer.WITH || next==InterlisLexer.VERTEX;
                        default -> false;
                    };
            if(contextual)type=token.getType();
            if(token instanceof org.antlr.v4.runtime.WritableToken writable)writable.setType(type);
            previous=type;
        }
    }

    private static int classifiedType(org.antlr.v4.runtime.Token token,LanguageVersion language) {
        if(token.getType()==org.antlr.v4.runtime.Token.EOF || token.getChannel()!=org.antlr.v4.runtime.Token.DEFAULT_CHANNEL)return token.getType();
        String text=token.getText();if(text==null || text.isEmpty() || !Character.isLetter(text.charAt(0)))return token.getType();
        if((language==LanguageVersion.ILI23?RESERVED_23:RESERVED_24).contains(text))
            return token.getType()==InterlisLexer.Name?InterlisLexer.RESERVED_WORD:token.getType();
        return text.length()<=255?InterlisLexer.Name:InterlisLexer.RESERVED_WORD;
    }

    static boolean supports(LanguageVersion version, Feature feature) {
        return version == LanguageVersion.ILI24;
    }

    static Optional<Feature> featureForToken(int tokenType) {
        return switch (tokenType) {
            case InterlisLexer.DATE, InterlisLexer.TIMEOFDAY, InterlisLexer.DATETIME -> Optional.of(Feature.TEMPORAL_TYPES);
            case InterlisLexer.NOINCREMENTALTRANSFER, InterlisLexer.CHARSET, InterlisLexer.XMLNS -> Optional.of(Feature.MODEL_TRANSFER_OPTIONS);
            case InterlisLexer.MULTICOORD -> Optional.of(Feature.MULTICOORD);
            case InterlisLexer.MULTIPOLYLINE,InterlisLexer.MULTISURFACE,InterlisLexer.MULTIAREA -> Optional.of(Feature.MULTIGEOMETRY);
            case InterlisLexer.REFSYS -> Optional.of(Feature.COORDINATE_CRS);
            case InterlisLexer.GENERIC -> Optional.of(Feature.GENERIC_DOMAIN);
            case InterlisLexer.DEFERRED -> Optional.of(Feature.DEFERRED_GENERICS);
            case InterlisLexer.CONTEXT -> Optional.of(Feature.CONTEXT_DEFINITION);
            default -> Optional.empty();
        };
    }
}

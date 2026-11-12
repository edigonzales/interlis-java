package guru.interlis.validation;

import guru.interlis.model.LanguageVersion;
import guru.interlis.model.TextKind;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

final class TextRules {
    private static final Pattern NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,254}");
    // Reserved words from the 2.3 §2.2.7 and 2.4 §3.2.7 tables, not lexer tokens.
    private static final Set<String> RESERVED_23 = Set.of("""
            ABSTRACT ACCORDING AGGREGATES AGGREGATION ALL AND ANY ANYCLASS ANYSTRUCTURE ARCS
            AREA AS ASSOCIATION AT ATTRIBUTE ATTRIBUTES BAG BASE BASED BASKET
            BINARY BLACKBOX BLANK BOOLEAN BY CARDINALITY CIRCULAR CLASS CLOCKWISE CODE
            CONSTRAINT CONSTRAINTS CONTINUE CONTINUOUS CONTOUR CONTRACTED COORD COORD2 COORD3 COUNTERCLOCKWISE
            DATE DEFAULT DEFINED DEGREES DEPENDS DERIVATIVES DERIVED DIM1 DIM2 DIRECTED
            DOMAIN END ENUMTREEVAL ENUMVAL EQUAL EXISTENCE EXTENDED EXTENDS EXTERNAL FINAL
            FIRST FIX FONT FORM FORMAT FREE FROM FUNCTION GRADS GRAPHIC
            HALIGNMENT HIDING I16 I32 IDENT IMPORTS IN INHERITANCE INSPECTION INTERLIS
            JOIN LAST LINE LINEATTR LINESIZE LIST LNBASE LOCAL MANDATORY METAOBJECT
            MODEL MTEXT NAME NO NOT NULL NUMERIC OBJECT OBJECTS OF
            OID ON OPTIONAL OR ORDERED OTHERS OVERLAPS PARAMETER PARENT PERIPHERY
            PI POLYLINE PROJECTION RADIANS REFERENCE REFSYSTEM REQUIRED RESTRICTION ROTATION SET
            SIGN STRAIGHTS STRUCTURE SUBDIVISION SURFACE SYMBOLOGY TABLE TEXT THATAREA THIS
            THISAREA TID TIDSIZE TO TOPIC TRANSFER TRANSIENT TRANSLATION TYPE UNDEFINED
            UNION UNIQUE UNIT UNQUALIFIED URI VALIGNMENT VERSION VERTEX VERTEXINFO VIEW
            WHEN WHERE WITH WITHOUT
            """.strip().split("\\s+"));
    private static final Set<String> RESERVED_24_ADDITIONS = Set.of("CHARSET", "CONTEXT", "DATETIME", "DEFERRED", "GENERIC", "GENERICS", "MULTIAREA", "MULTICOORD", "MULTIPOLYLINE", "MULTISURFACE", "NOINCREMENTALTRANSFER", "REFSYS", "TIMEOFDAY", "XMLNS");

    // Non-ASCII entries in Annex B (2.3) and Annex D (2.4).
    private static final String REPERTOIRE_23 = "§«»ÄÆÇÈÉÑÖÜàáâäæçèéêëìíîïñòóôõöùúûü€";
    private static final String REPERTOIRE_24 = "£§«»ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖØÙÚÛÜÝÞßàáâãäåæçèéêëìíîïðñòóôõöøùúûüýþÿ€";

    private TextRules() { }

    static boolean validName(String value, LanguageVersion language) {
        return NAME.matcher(value).matches() && !RESERVED_23.contains(value)
                && (language != LanguageVersion.ILI24 || !RESERVED_24_ADDITIONS.contains(value));
    }

    static boolean validUri(String value) {
        if (value.length() > 1023 || value.chars().anyMatch(c -> c <= 32 || c >= 127 || c == '[' || c == ']')) {
            return false;
        }
        try {
            return new URI(value).isAbsolute();
        } catch (URISyntaxException invalid) {
            return false;
        }
    }

    static RepertoireResult repertoire(String value, TextKind kind, LanguageVersion language,
                                       Optional<String> charset) {
        var characters = value.codePoints().iterator();
        var defaults = language == LanguageVersion.ILI24 ? REPERTOIRE_24 : REPERTOIRE_23;
        boolean standard = true;
        while (characters.hasNext()) {
            int cp = characters.nextInt();
            if (Character.isISOControl(cp)) {
                if (kind != TextKind.MTEXT || cp != 9 && cp != 10 && cp != 13) {
                    return RepertoireResult.REJECTED;
                }
            } else if (cp >= 0xD800 && cp <= 0xDFFF) {
                return RepertoireResult.REJECTED;
            } else if (!(cp >= 32 && cp <= 126) && defaults.indexOf(cp) < 0) {
                standard = false;
            }
        }
        if (charset.isEmpty()) {
            // An extension repertoire can be agreed externally; the neutral stream
            // carries no such agreement. Keep the Annex fallback's uncertainty visible.
            return standard ? RepertoireResult.ACCEPTED : RepertoireResult.UNKNOWN;
        }
        try {
            return Charset.forName(charset.orElseThrow()).newEncoder().canEncode(value)
                    ? RepertoireResult.ACCEPTED : RepertoireResult.REJECTED;
        } catch (UnsupportedCharsetException | java.nio.charset.IllegalCharsetNameException unsupported) {
            return RepertoireResult.UNKNOWN;
        }
    }

    enum RepertoireResult { ACCEPTED, REJECTED, UNKNOWN }
}

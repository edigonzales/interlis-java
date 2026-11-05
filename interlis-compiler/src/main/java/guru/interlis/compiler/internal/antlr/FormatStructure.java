package guru.interlis.compiler.internal.antlr;

import guru.interlis.execution.CancellationToken;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static guru.interlis.compiler.internal.antlr.InterlisParser.*;

/** Formatter-only concrete syntax roles, indexed by original token ordinal (including hidden tokens). */
public final class FormatStructure {
    public record Indent(int first, int endExclusive) { }
    public record Boundary(int ordinal, int lines, boolean exact) { }
    public record Header(int first, int last) { }
    public record Break(int ordinal, int extraIndent) { }
    public record Group(int first, int last, List<Break> breaks) {
        public Group { breaks = List.copyOf(breaks); }
    }


    private final List<Indent> indents = new ArrayList<>();
    private final List<Boundary> boundaries = new ArrayList<>();
    private final List<Integer> tight = new ArrayList<>();
    private final List<Header> headers = new ArrayList<>();
    private final List<Group> expressionGroups = new ArrayList<>();
    private final List<Group> constraints = new ArrayList<>();

    public List<Indent> indents() { return List.copyOf(indents); }
    public List<Boundary> boundaries() { return List.copyOf(boundaries); }
    public List<Integer> tight() { return List.copyOf(tight); }
    public List<Header> headers() { return List.copyOf(headers); }
    public List<Group> expressionGroups() { return List.copyOf(expressionGroups); }
    public List<Group> constraints() { return List.copyOf(constraints); }

    void index(Interlis2defContext root, AntlrOffsetMap offsets, CancellationToken cancellation) {
        var pending = new ArrayDeque<ParserRuleContext>();
        pending.push(root);
        while (!pending.isEmpty()) {
            cancellation.throwIfCancellationRequested();
            var node = pending.pop();
            for (int i = node.getChildCount() - 1; i >= 0; i--) {
                if (node.getChild(i) instanceof ParserRuleContext child) pending.push(child);
            }
            switch (node.getRuleIndex()) {
                case RULE_modeldef, RULE_topicDef, RULE_classDef, RULE_structureDef,
                        RULE_associationDef, RULE_viewDef, RULE_graphicDef, RULE_constraintsDef -> block(node);
                case RULE_attributeDef, RULE_roleDef, RULE_parameterDef, RULE_runTimeParameterDef,
                        RULE_viewAttribute, RULE_drawingRule,
                        RULE_basketOidRule, RULE_objectOidRule, RULE_contextMapping,
                        RULE_lineFormTypeDef -> boundary(first(node), 1, false);
                case RULE_functionDef, RULE_metaDataBasketDef -> boundary(first(node), 2, false);
                case RULE_constraintDef -> constraint((ConstraintDefContext) node);
                case RULE_domainDef -> domain(node);
                case RULE_unitDef, RULE_contextDef -> prefixedSection(node);
                case RULE_enumeration -> enumeration((EnumerationContext) node, offsets);
                case RULE_cardinality -> {
                    for (int i = first(node) + 1; i <= last(node); i++) tight.add(i);
                }
                case RULE_textType -> {
                    if (node.getChildCount() > 1) {
                        tight.add(first(node) + 1);
                        tight.add(last(node));
                    }
                }
                case RULE_pathEl -> {
                    if (node.getToken(COLON, 0) != null) tight.add(last(node));
                }
                case RULE_objectOrAttributePath, RULE_localUniqueness,
                        RULE_rotationDef, RULE_inspection -> arrows(node);
                case RULE_attributePathConst -> {
                    tight.add(ordinal(node.getToken(GT, 1)));
                    tight.add(ordinal(node.getToken(GT, 1)) + 1);
                    arrows(node);
                }
                case RULE_classConst -> tight.add(first(node) + 1);
                case RULE_relation -> {
                    if (node.getChildCount() == 2) tight.add(last(node));
                }
                case RULE_term -> {
                    if (node.getToken(GT, 0) != null) tight.add(ordinal(node.getToken(GT, 0)));
                }
                default -> { }
            }
            if (node instanceof RoleDefContext role) {
                var signs = role.getTokens(MINUS);
                if (!signs.isEmpty()) {
                    for (int i = 0; i < role.getChildCount() - 1; i++) {
                        if (role.getChild(i) == signs.getFirst()) {
                            for (int j = i + 1; j < role.getChildCount(); j++) {
                                if (!(role.getChild(j) instanceof TerminalNode sign)) break;
                                int type = sign.getSymbol().getType();
                                if (type != MINUS && type != LT && type != HASH && type != GT) break;
                                tight.add(ordinal(sign));
                            }
                            break;
                        }
                    }
                }
            }
            callOpening(node);
            expressionGroup(node);
        }
    }

    private void constraint(ConstraintDefContext node) {
        int first = first(node);
        int last = last(node);
        boundary(first, 1, false);
        var breaks = switch (node.getChild(0)) {
            case MandatoryConstraintContext mandatory -> List.of(new Break(first(mandatory.expression()), 1));
            case PlausibilityConstraintContext plausibility -> List.of(new Break(first(plausibility.expression()), 1));
            case ExistenceConstraintContext existence -> existenceBreaks(existence);
            case UniquenessConstraintContext unique -> uniquenessBreaks(unique);
            case SetConstraintContext set -> setBreaks(set);
            default -> throw new IllegalStateException("Missing layout role for constraint");
        };
        constraints.add(new Group(first, last, breaks));
    }

    private static List<Break> existenceBreaks(ExistenceConstraintContext node) {
        var breaks = new ArrayList<Break>();
        breaks.add(new Break(first(node.attributePath(0)), 1));
        breaks.add(new Break(ordinal(node.REQUIRED()), 1));
        for (var alternative : node.OR()) breaks.add(new Break(ordinal(alternative), 1));
        return breaks;
    }

    private static List<Break> uniquenessBreaks(UniquenessConstraintContext node) {
        var breaks = new ArrayList<Break>();
        boolean conditional = node.WHERE() != null;
        if (conditional) breaks.add(new Break(ordinal(node.WHERE()), 1));
        if (node.globalUniqueness() != null) {
            for (var key : node.globalUniqueness().uniqueEl()) {
                breaks.add(new Break(first(key), conditional ? 2 : 1));
            }
        } else {
            var local = node.localUniqueness();
            if (conditional) breaks.add(new Break(first(local), 2));
            for (var field : local.fields) {
                breaks.add(new Break(field.getTokenIndex(), conditional ? 3 : 1));
            }
        }
        return breaks;
    }

    private static List<Break> setBreaks(SetConstraintContext node) {
        if (node.WHERE() == null) return List.of(new Break(first(node.expression(0)), 1));
        int whereEnd = last(node.expression(0));
        int separator = node.COLON().stream().mapToInt(FormatStructure::ordinal)
                .filter(colon -> colon > whereEnd).findFirst().orElseThrow();
        return List.of(new Break(ordinal(node.WHERE()), 1), new Break(separator + 1, 1));
    }

    private void expressionGroup(ParserRuleContext node) {
        if (node instanceof TermContext term) {
            if (term.EQ() != null) addGroup(node, List.of(new Break(ordinal(term.EQ()), 0)));
        } else if (node instanceof Term0Context || node instanceof Term1Context) {
            var breaks = new ArrayList<Break>();
            for (int i = 1; i < node.getChildCount(); i += 2) {
                if (node.getChild(i) instanceof TerminalNode operator) {
                    int type = operator.getSymbol().getType();
                    breaks.add(new Break(ordinal(operator), type == OR || type == AND ? 0 : 1));
                }
            }
            addGroup(node, breaks);
        } else if (node instanceof Term2Context comparison && comparison.relation() != null) {
            addGroup(node, List.of(new Break(first(comparison.relation()), 1)));
        } else if (node instanceof PredicateContext predicate && predicate.expression() != null) {
            addGroup(node, List.of(new Break(ordinal(predicate.LPAR()) + 1, 1),
                    new Break(ordinal(predicate.RPAR()), 0)));
        } else if (node instanceof FunctionCallContext call) {
            callGroup(node, call.LPAR(), call.RPAR(), call.argument());
        } else if (node instanceof FactorContext factor && factor.INTERLIS() != null && factor.LPAR() != null) {
            callGroup(node, factor.LPAR(), factor.RPAR(), factor.argument());
        }
    }

    private void callGroup(ParserRuleContext node, TerminalNode open, TerminalNode close,
                           List<ArgumentContext> arguments) {
        if (arguments.isEmpty()) return;
        var breaks = new ArrayList<Break>();
        breaks.add(new Break(ordinal(open) + 1, 1));
        for (int i = 1; i < arguments.size(); i++) {
            breaks.add(new Break(first(arguments.get(i)), 1));
        }
        breaks.add(new Break(ordinal(close), 0));
        addGroup(node, breaks);
    }

    private void addGroup(ParserRuleContext node, List<Break> breaks) {
        if (!breaks.isEmpty()) expressionGroups.add(new Group(first(node), last(node), breaks));
    }

    private void block(ParserRuleContext node) {
        int first = first(node);
        boundary(first, 2, false);
        int open = ordinal(node.getToken(EQ, 0));
        headers.add(new Header(first, open));
        int close = ordinal(node.getToken(END, 0));
        boolean airy = node instanceof ModeldefContext || node instanceof TopicDefContext;
        boolean empty = open + 1 == close;
        if (!empty) {
            indents.add(new Indent(open + 1, close));
            boundary(open + 1, airy ? 2 : 1, true);
        }
        boundary(close, airy && !empty ? 2 : 1, true);
        for (int i = 0; i < node.getChildCount(); i++) {
            ParseTree child = node.getChild(i);
            if (child instanceof TerminalNode token && ordinal(token) > open && ordinal(token) < close) {
                int kind = token.getSymbol().getType();
                if (kind == IMPORTS || kind == DEPENDS || kind == CHARSET || kind == XMLNS
                        || kind == DEFERRED || kind == CARDINALITY) boundary(ordinal(token), 1, false);
            }
        }
        sections(node);
    }

    private void sections(ParserRuleContext node) {
        List<ParseTree> children = new ArrayList<>();
        for (int i = 0; i < node.getChildCount(); i++) {
            ParseTree child = node.getChild(i);
            children.add(child instanceof DefinitionsContext definition ? definition.getChild(0) : child);
        }
        for (int i = 0; i < children.size(); i++) {
            if (!(children.get(i) instanceof TerminalNode token)) continue;
            int kind = token.getSymbol().getType();
            String sectionRule = switch (kind) {
                case DOMAIN -> "domainDef";
                case UNIT -> "unitDef";
                case CONTEXT -> "contextDef";
                case PARAMETER -> "runTimeParameterDef";
                case ATTRIBUTE -> "attributeDef";
                case LINE -> "lineFormTypeDef";
                default -> null;
            };
            if (sectionRule == null) continue;
            if (kind == LINE) {
                if (i + 1 >= children.size() || !(children.get(i + 1) instanceof TerminalNode form)
                        || form.getSymbol().getType() != FORM) continue;
                i++;
            }
            boundary(ordinal(token), 2, false);
            int afterHeading = ordinal((TerminalNode) children.get(i)) + 1;
            int last = afterHeading - 1;
            for (int j = i + 1; j < children.size(); j++) {
                if (!(children.get(j) instanceof ParserRuleContext member)
                        || !(ruleNames[member.getRuleIndex()].equals(sectionRule)
                        || kind == PARAMETER && member instanceof ParameterDefContext
                        || kind == ATTRIBUTE && member instanceof ViewAttributeContext)) break;
                last = last(member);
            }
            if (last >= afterHeading) {
                indents.add(new Indent(afterHeading, last + 1));
                boundary(afterHeading, 1, true);
            }
        }
    }

    private void domain(ParserRuleContext node) {
        prefixedSection(node);
        for (TerminalNode separator : node.getTokens(SEMI)) {
            int next = ordinal(separator) + 1;
            if (next <= last(node)) boundary(next, 1, false);
        }
    }

    private void prefixedSection(ParserRuleContext node) {
        if (node.getStart().getType() == DOMAIN || node.getStart().getType() == UNIT
                || node.getStart().getType() == CONTEXT) {
            boundary(first(node), 2, false);
            boundary(first(node) + 1, 1, true);
            indents.add(new Indent(first(node) + 1, last(node) + 1));
        } else {
            boundary(first(node), 1, false);
        }
    }

    private void enumeration(EnumerationContext node, AntlrOffsetMap offsets) {
        String text = offsets.source().text();
        int start = offsets.tokenRange(node.getStart()).startOffset();
        int end = offsets.tokenRange(node.getStop()).endOffset();
        boolean multiline = text.indexOf('\n', start, end) >= 0 || text.indexOf('\r', start, end) >= 0;
        boolean nested = node.enumElement().stream().anyMatch(item -> item.enumeration() != null)
                || node.getParent() instanceof EnumElementContext;
        if (!multiline && !nested) return;
        int open = ordinal(node.LPAR());
        int close = ordinal(node.RPAR());
        indents.add(new Indent(open + 1, close));
        boundary(open + 1, 1, true);
        for (var item : node.enumElement()) boundary(first(item), 1, true);
        boundary(close, 1, true);
    }

    private void callOpening(ParserRuleContext node) {
        var opening = node.getToken(LPAR, 0);
        if (opening != null && (node instanceof FunctionCallContext || node instanceof FunctionDefContext
                || node instanceof PredicateContext && node.getToken(DEFINED, 0) != null
                || node instanceof FactorContext && node.getToken(INTERLIS, 0) != null
                || node instanceof ArgumentContext && node.getToken(ALL, 0) != null)) {
            tight.add(ordinal(opening));
        }
        if (node instanceof ClassOrStructureDefContext || node instanceof ViewAttributesContext) sections(node);
    }

    private void arrows(ParserRuleContext node) {
        for (int i = 0; i + 1 < node.getChildCount(); i++) {
            if (node.getChild(i) instanceof TerminalNode left && left.getSymbol().getType() == MINUS
                    && node.getChild(i + 1) instanceof TerminalNode right && right.getSymbol().getType() == GT) {
                tight.add(ordinal(left));
                tight.add(ordinal(right));
                tight.add(ordinal(right) + 1);
            }
        }
    }

    private void boundary(int ordinal, int lines, boolean exact) {
        boundaries.add(new Boundary(ordinal, lines, exact));
    }

    private static int first(ParserRuleContext node) { return node.getStart().getTokenIndex(); }
    private static int last(ParserRuleContext node) { return node.getStop().getTokenIndex(); }
    private static int ordinal(TerminalNode node) { return node.getSymbol().getTokenIndex(); }
}

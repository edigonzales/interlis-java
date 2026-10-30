package guru.interlis.compiler.internal.antlr;

import guru.interlis.compiler.AnalysisBudget;
import guru.interlis.compiler.internal.AnalysisBudgetExceededException;
import guru.interlis.execution.CancellationToken;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

/** Per-parser guard. Counts live rule depth and recovery notifications before AST construction. */
final class ParseBudget extends BaseErrorListener implements ParseTreeListener {
    private final AnalysisBudget budget;
    private final CancellationToken cancellation;
    private int depth;
    private int recoveries;
    ParseBudget(AnalysisBudget budget,CancellationToken cancellation){this.budget=budget;this.cancellation=cancellation;}
    @Override public void enterEveryRule(ParserRuleContext context) {
        cancellation.throwIfCancellationRequested();
        if(++depth>budget.maxParseRuleDepth())throw new AnalysisBudgetExceededException("maxParseRuleDepth",budget.maxParseRuleDepth());
    }
    @Override public void exitEveryRule(ParserRuleContext context){depth--;}
    @Override public void visitTerminal(TerminalNode terminal){cancellation.throwIfCancellationRequested();}
    @Override public void visitErrorNode(ErrorNode error){cancellation.throwIfCancellationRequested();}
    @Override public void syntaxError(Recognizer<?,?> recognizer,Object symbol,int line,int column,String message,RecognitionException error) {
        cancellation.throwIfCancellationRequested();
        if(++recoveries>budget.maxRecoveryEvents())throw new AnalysisBudgetExceededException("maxRecoveryEvents",budget.maxRecoveryEvents());
    }
}

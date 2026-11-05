/** Compiler, syntax, queries and formatter; ANTLR remains an implementation detail. */
module guru.interlis.compiler {
    requires transitive guru.interlis.model;
    requires org.antlr.antlr4.runtime;
    exports guru.interlis.compiler;
    exports guru.interlis.compiler.query;
    exports guru.interlis.syntax;
    exports guru.interlis.format;
}

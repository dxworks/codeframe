package org.dxworks.codeframe.analyzer.sql;

import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.dxworks.codeframe.analyzer.sql.generated.PlSqlLexer;
import org.dxworks.codeframe.analyzer.sql.generated.PlSqlParser;
import org.dxworks.codeframe.analyzer.sql.generated.TSqlLexer;
import org.dxworks.codeframe.analyzer.sql.generated.TSqlParser;

/**
 * Factory for creating ANTLR lexers and parsers with quiet error handling.
 * Centralizes the boilerplate setup for T-SQL and PL/SQL parsing.
 */
public final class AntlrParserFactory {

    private AntlrParserFactory() {
        // utility class
    }

    /**
     * Quiet error listener that suppresses all syntax errors.
     * Used for best-effort parsing where we want to extract as much as possible.
     */
    public static final ANTLRErrorListener QUIET_LISTENER = new BaseErrorListener() {
        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                int line, int charPositionInLine, String msg,
                                RecognitionException e) {
            // no-op: suppress all errors for best-effort extraction
        }
    };

    /**
     * Creates a T-SQL parser configured for quiet parsing.
     */
    public static TSqlParser createTSqlParser(String source) {
        TSqlLexer lexer = new TSqlLexer(CharStreams.fromString(source));
        configureQuietLexer(lexer);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        TSqlParser parser = new TSqlParser(tokens);
        configureQuietParser(parser);
        return parser;
    }

    /**
     * Creates a T-SQL parser with token stream access (for extractors that need it).
     */
    public static ParserWithTokens<TSqlParser> createTSqlParserWithTokens(String source) {
        TSqlLexer lexer = new TSqlLexer(CharStreams.fromString(source));
        configureQuietLexer(lexer);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        TSqlParser parser = new TSqlParser(tokens);
        configureQuietParser(parser);
        return new ParserWithTokens<>(parser, tokens);
    }

    /**
     * Creates a PL/SQL parser configured for quiet parsing.
     */
    public static PlSqlParser createPlSqlParser(String source) {
        PlSqlLexer lexer = new PlSqlLexer(CharStreams.fromString(source));
        configureQuietLexer(lexer);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PlSqlParser parser = new PlSqlParser(tokens);
        configureQuietParser(parser);
        return parser;
    }

    private static void configureQuietLexer(Lexer lexer) {
        lexer.removeErrorListeners();
        lexer.addErrorListener(QUIET_LISTENER);
    }

    private static void configureQuietParser(Parser parser) {
        parser.removeErrorListeners();
        parser.addErrorListener(QUIET_LISTENER);
        parser.setErrorHandler(new DefaultErrorStrategy());
    }

    /**
     * Container for parser and its token stream.
     */
    public static class ParserWithTokens<P extends Parser> {
        private final P parser;
        private final CommonTokenStream tokens;

        public ParserWithTokens(P parser, CommonTokenStream tokens) {
            this.parser = parser;
            this.tokens = tokens;
        }

        public P getParser() {
            return parser;
        }

        public CommonTokenStream getTokens() {
            return tokens;
        }
    }
}

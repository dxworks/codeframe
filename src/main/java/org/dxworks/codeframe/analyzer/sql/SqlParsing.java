package org.dxworks.codeframe.analyzer.sql;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParser;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statements;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

// Always releases the parser thread; JSqlParser's own overload leaks it when parsing fails.
final class SqlParsing {

    private SqlParsing() {
    }

    static Statements parseStatements(String sql, Consumer<CCJSqlParser> config) throws JSQLParserException {
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "codeframe-jsqlparser");
            t.setDaemon(true);
            return t;
        });
        try {
            return CCJSqlParserUtil.parseStatements(sql, executor, config);
        } finally {
            executor.shutdownNow();
        }
    }
}

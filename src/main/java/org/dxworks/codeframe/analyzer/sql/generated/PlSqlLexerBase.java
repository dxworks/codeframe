package org.dxworks.codeframe.analyzer.sql.generated;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;

public abstract class PlSqlLexerBase extends Lexer {
    protected PlSqlLexerBase(CharStream input) {
        super(input);
    }

    protected boolean IsNewlineAtPos(int pos) {
        int la = _input.LA(pos);
        return la == -1 || la == '\n';
    }
}

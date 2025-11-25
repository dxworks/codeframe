package org.dxworks.codeframe.analyzer.sql.generated;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.TokenStream;

public abstract class PlSqlParserBase extends Parser {
    private boolean isVersion12 = true;
    private boolean isVersion11 = true;
    private boolean isVersion10 = true;

    protected PlSqlParserBase(TokenStream input) {
        super(input);
    }

    public boolean isVersion12() {
        return isVersion12;
    }

    public void setVersion12(boolean value) {
        this.isVersion12 = value;
    }

    public boolean isVersion11() {
        return isVersion11;
    }

    public void setVersion11(boolean value) {
        this.isVersion11 = value;
    }

    public boolean isVersion10() {
        return isVersion10;
    }

    public void setVersion10(boolean value) {
        this.isVersion10 = value;
    }
}

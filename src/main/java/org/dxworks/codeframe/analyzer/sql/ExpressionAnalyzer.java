package org.dxworks.codeframe.analyzer.sql;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.Values;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.statement.update.Update;

import java.util.Collection;

/**
 * Utility for analyzing SQL expressions and extracting function calls.
 * Consolidates duplicate expression visitor logic used across analyzers.
 */
public final class ExpressionAnalyzer {

    private ExpressionAnalyzer() {
        // utility class
    }

    /**
     * Collects function calls from a statement.
     */
    public static void collectFunctions(Statement st, Collection<String> sink) {
        ExpressionVisitorAdapter visitor = createFunctionVisitor(sink);

        if (st instanceof Select) {
            visitSelect((Select) st, visitor);
        } else if (st instanceof Insert) {
            Insert ins = (Insert) st;
            if (ins.getSelect() != null) {
                visitSelect(ins.getSelect(), visitor);
            }
        } else if (st instanceof Update) {
            visitUpdate((Update) st, visitor);
        } else if (st instanceof Delete) {
            Delete del = (Delete) st;
            if (del.getWhere() != null) {
                visitExpression(del.getWhere(), visitor);
            }
        }
    }

    /**
     * Creates an expression visitor that collects function names.
     */
    public static ExpressionVisitorAdapter createFunctionVisitor(Collection<String> sink) {
        return new ExpressionVisitorAdapter() {
            @Override
            public void visit(Function function) {
                if (function == null) return;
                String name = function.getName();
                if (name != null && !name.isEmpty() && !RoutineSqlUtils.isSqlTypeName(name)) {
                    sink.add(name);
                }
                if (function.getParameters() != null) {
                    for (Expression param : function.getParameters()) {
                        if (param == null) continue;
                        if (param instanceof Function) {
                            visit((Function) param);
                        } else {
                            param.accept(this);
                        }
                    }
                }
            }

            @Override
            public void visit(net.sf.jsqlparser.expression.CastExpression cast) {
                if (cast == null) return;
                Expression inner = cast.getLeftExpression();
                if (inner != null) {
                    inner.accept(this);
                }
            }

            @Override
            public void visit(net.sf.jsqlparser.expression.operators.relational.ExistsExpression exists) {
                if (exists == null) return;
                Expression right = exists.getRightExpression();
                if (right instanceof net.sf.jsqlparser.statement.select.ParenthesedSelect) {
                    net.sf.jsqlparser.statement.select.ParenthesedSelect ps = 
                        (net.sf.jsqlparser.statement.select.ParenthesedSelect) right;
                    if (ps.getSelect() != null) {
                        visitSelect(ps.getSelect(), this);
                    }
                } else if (right != null) {
                    right.accept(this);
                }
            }
        };
    }

    /**
     * Visits an expression, ensuring functions are properly handled.
     */
    public static void visitExpression(Expression expr, ExpressionVisitorAdapter visitor) {
        if (expr == null) return;
        if (expr instanceof Function) {
            visitor.visit((Function) expr);
        } else if (expr instanceof net.sf.jsqlparser.expression.CastExpression) {
            net.sf.jsqlparser.expression.CastExpression cast = 
                (net.sf.jsqlparser.expression.CastExpression) expr;
            Expression inner = cast.getLeftExpression();
            if (inner != null) {
                visitExpression(inner, visitor);
            }
        } else {
            expr.accept(visitor);
        }
    }

    /**
     * Visits a SELECT statement and all its expressions.
     */
    public static void visitSelect(Select select, ExpressionVisitorAdapter visitor) {
        if (select == null) return;
        
        if (select instanceof PlainSelect) {
            PlainSelect ps = (PlainSelect) select;
            
            // WITH clauses
            if (ps.getWithItemsList() != null) {
                for (WithItem wi : ps.getWithItemsList()) {
                    if (wi.getSelect() != null) {
                        visitSelect(wi.getSelect(), visitor);
                    }
                }
            }
            
            // SELECT items
            if (ps.getSelectItems() != null) {
                for (SelectItem<?> si : ps.getSelectItems()) {
                    Expression e = si.getExpression();
                    if (e != null) {
                        visitExpression(e, visitor);
                    }
                }
            }
            
            // WHERE clause
            if (ps.getWhere() != null) {
                visitExpression(ps.getWhere(), visitor);
            }
            
            // HAVING clause
            if (ps.getHaving() != null) {
                visitExpression(ps.getHaving(), visitor);
            }
            
            // ORDER BY
            if (ps.getOrderByElements() != null) {
                for (OrderByElement o : ps.getOrderByElements()) {
                    if (o.getExpression() != null) {
                        visitExpression(o.getExpression(), visitor);
                    }
                }
            }
            
            // JOINs
            if (ps.getJoins() != null) {
                for (Join j : ps.getJoins()) {
                    if (j.getOnExpressions() != null) {
                        for (Expression e : j.getOnExpressions()) {
                            if (e != null) {
                                visitExpression(e, visitor);
                            }
                        }
                    }
                }
            }
        } else if (select instanceof SetOperationList) {
            for (Select s : ((SetOperationList) select).getSelects()) {
                visitSelect(s, visitor);
            }
        } else if (select instanceof Values) {
            Values values = (Values) select;
            if (values.getExpressions() != null) {
                for (Expression e : values.getExpressions()) {
                    if (e != null) {
                        visitExpression(e, visitor);
                    }
                }
            }
        }
    }

    /**
     * Visits an UPDATE statement and its expressions.
     */
    private static void visitUpdate(Update update, ExpressionVisitorAdapter visitor) {
        if (update.getUpdateSets() != null) {
            for (net.sf.jsqlparser.statement.update.UpdateSet us : update.getUpdateSets()) {
                if (us.getValues() != null) {
                    for (Expression e : us.getValues()) {
                        if (e != null) {
                            visitExpression(e, visitor);
                        }
                    }
                }
            }
        }
        if (update.getWhere() != null) {
            visitExpression(update.getWhere(), visitor);
        }
    }
}

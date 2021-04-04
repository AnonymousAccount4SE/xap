package com.gigaspaces.jdbc.handlers;

import com.gigaspaces.jdbc.model.table.TableContainer;
import com.gigaspaces.metadata.StorageType;
import com.j_spaces.jdbc.builder.QueryTemplatePacket;
import com.j_spaces.jdbc.builder.range.*;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WhereHandler extends UnsupportedWhereHandler {
    private final List<TableContainer> tables;
    private final Map<TableContainer, QueryTemplatePacket> qtpMap;

    public Map<TableContainer, QueryTemplatePacket> getQTPMap() {
        return qtpMap;
    }

    public WhereHandler(List<TableContainer> tables) {
        this.tables = tables;
        this.qtpMap = new LinkedHashMap<>();
    }

    @Override
    public void visit(AndExpression andExpression) {
        WhereHandler leftHandler = new WhereHandler(tables);
        andExpression.getLeftExpression().accept(leftHandler);
        WhereHandler rightHandler = new WhereHandler(tables);
        andExpression.getRightExpression().accept(rightHandler);

        and(leftHandler, rightHandler);
    }

    private void and(WhereHandler leftHandler, WhereHandler rightHandler) {
        for (Map.Entry<TableContainer, QueryTemplatePacket> leftTable : leftHandler.getQTPMap().entrySet()) {
            QueryTemplatePacket rightTable = rightHandler.getQTPMap().get(leftTable.getKey());
            if (rightTable == null) {
                this.qtpMap.put(leftTable.getKey(), leftTable.getValue());
            } else {
                this.qtpMap.put(leftTable.getKey(), leftTable.getValue().and(rightTable));
            }
        }

        for (Map.Entry<TableContainer, QueryTemplatePacket> rightTable : rightHandler.getQTPMap().entrySet()) {
            QueryTemplatePacket leftTable = leftHandler.getQTPMap().get(rightTable.getKey());
            if (leftTable == null) {
                this.qtpMap.put(rightTable.getKey(), rightTable.getValue());
            } else {
                // already handled
            }
        }
    }

    @Override
    public void visit(OrExpression orExpression) {
        WhereHandler leftHandler = new WhereHandler(tables);
        orExpression.getLeftExpression().accept(leftHandler);
        WhereHandler rightHandler = new WhereHandler(tables);
        orExpression.getRightExpression().accept(rightHandler);

        or(leftHandler, rightHandler);
    }

    private void or(WhereHandler leftHandler, WhereHandler rightHandler) {
        for (Map.Entry<TableContainer, QueryTemplatePacket> leftTable : leftHandler.getQTPMap().entrySet()) {
            QueryTemplatePacket rightTable = rightHandler.getQTPMap().get(leftTable.getKey());
            if (rightTable == null) {
                this.qtpMap.put(leftTable.getKey(), leftTable.getValue());
            } else {
                this.qtpMap.put(leftTable.getKey(), leftTable.getValue().union(rightTable));
            }
        }

        for (Map.Entry<TableContainer, QueryTemplatePacket> rightTable : rightHandler.getQTPMap().entrySet()) {
            QueryTemplatePacket leftTable = leftHandler.getQTPMap().get(rightTable.getKey());
            if (leftTable == null) {
                this.qtpMap.put(rightTable.getKey(), rightTable.getValue());
            } else {
                // Already handled above
            }
        }
    }

    @Override
    public void visit(EqualsTo equalsTo) {
        SingleConditionHandler handler = new SingleConditionHandler();
        equalsTo.getLeftExpression().accept(handler);
        equalsTo.getRightExpression().accept(handler);

        TableContainer table = handler.getTable(tables);
        Range range = new EqualValueRange(handler.getColumn().getColumnName(), handler.getValue());
        qtpMap.put(table, table.createQueryTemplatePacketWithRange(range));
    }

    @Override
    public void visit(NotEqualsTo notEqualsTo) {
        SingleConditionHandler handler = new SingleConditionHandler();
        notEqualsTo.getLeftExpression().accept(handler);
        notEqualsTo.getRightExpression().accept(handler);

        TableContainer table = handler.getTable(tables);
        Range range = new NotEqualValueRange(handler.getColumn().getColumnName(), handler.getValue());
        qtpMap.put(table, table.createQueryTemplatePacketWithRange(range));
    }

    @Override
    public void visit(MinorThanEquals minorThanEquals) {
        SingleConditionHandler handler = new SingleConditionHandler();
        minorThanEquals.getLeftExpression().accept(handler);
        minorThanEquals.getRightExpression().accept(handler);
        if (handler.getColumn().getColumnName().equalsIgnoreCase("rowNum") && handler.getValue() instanceof Integer) {
            Integer value = ((Integer) handler.getValue());
            tables.forEach(t -> t.setLimit(value));
        } else {
            TableContainer table = handler.getTable(tables);
            Range range = new SegmentRange(handler.getColumn().getColumnName(), null, false, castToComparable(handler.getValue()), true);
            qtpMap.put(table, table.createQueryTemplatePacketWithRange(range));
        }
    }

    @Override
    public void visit(MinorThan minorThan) {
        SingleConditionHandler handler = new SingleConditionHandler();
        minorThan.getLeftExpression().accept(handler);
        minorThan.getRightExpression().accept(handler);
        if (handler.getColumn().getColumnName().equalsIgnoreCase("rowNum") && handler.getValue() instanceof Integer) {
            Integer value = ((Integer) handler.getValue());
            tables.forEach(t -> t.setLimit(value - 1));
        } else {
            TableContainer table = handler.getTable(tables);
            Range range = new SegmentRange(handler.getColumn().getColumnName(), null, false, castToComparable(handler.getValue()), false);
            qtpMap.put(table, table.createQueryTemplatePacketWithRange(range));
        }
    }

    @Override
    public void visit(GreaterThan greaterThan) {
        SingleConditionHandler handler = new SingleConditionHandler();
        greaterThan.getLeftExpression().accept(handler);
        greaterThan.getRightExpression().accept(handler);
        TableContainer table = handler.getTable(tables);
        Range range = new SegmentRange(handler.getColumn().getColumnName(), castToComparable(handler.getValue()), false, null, false);
        qtpMap.put(table, table.createQueryTemplatePacketWithRange(range));
    }

    @Override
    public void visit(GreaterThanEquals greaterThanEquals) {
        SingleConditionHandler handler = new SingleConditionHandler();
        greaterThanEquals.getLeftExpression().accept(handler);
        greaterThanEquals.getRightExpression().accept(handler);
        TableContainer table = handler.getTable(tables);
        Range range = new SegmentRange(handler.getColumn().getColumnName(), castToComparable(handler.getValue()), true, null, false);
        qtpMap.put(table, table.createQueryTemplatePacketWithRange(range));
    }

    /**
     * Cast the object to Comparable otherwise throws an IllegalArgumentException exception
     */
    private static Comparable castToComparable(Object obj) {
        try {
            //NOTE- a check for Comparable interface implementation is be done in the proxy
            return (Comparable) obj;
        } catch (ClassCastException cce) {
            throw new IllegalArgumentException("Type " + obj.getClass() +
                    " doesn't implement Comparable, Serialization mode might be different than " + StorageType.OBJECT + ".", cce);
        }
    }


    @Override
    public void visit(IsNullExpression isNullExpression) {
        SingleConditionHandler handler = new SingleConditionHandler();
        isNullExpression.getLeftExpression().accept(handler);
        TableContainer table = handler.getTable(tables);
        Range range = isNullExpression.isNot() ? new NotNullRange(handler.getColumn().getColumnName()) : new IsNullRange(handler.getColumn().getColumnName());
        qtpMap.put(table, table.createQueryTemplatePacketWithRange(range));
    }

    @Override
    public void visit(LikeExpression likeExpression) {
        SingleConditionHandler handler = new SingleConditionHandler();
        likeExpression.getLeftExpression().accept(handler);
        likeExpression.getRightExpression().accept(handler);
        TableContainer table = handler.getTable(tables);
        String regex = ((String) handler.getValue()).replaceAll("%", ".*").replaceAll("_", ".");

        Range range = likeExpression.isNot() ? new NotRegexRange(handler.getColumn().getColumnName(), regex) : new RegexRange(handler.getColumn().getColumnName(), regex);
        qtpMap.put(table, table.createQueryTemplatePacketWithRange(range));
    }

    @Override
    public void visit(Between between) {
        SingleConditionHandler handlerStart = new SingleConditionHandler();
        between.getLeftExpression().accept(handlerStart);
        between.getBetweenExpressionStart().accept(handlerStart);
        TableContainer table = handlerStart.getTable(tables);

        SingleConditionHandler handlerEnd = new SingleConditionHandler();
        between.getBetweenExpressionEnd().accept(handlerEnd);

        if (!between.isNot()) {
            Range range = new SegmentRange(handlerStart.getColumn().getColumnName(), castToComparable(handlerStart.getValue()), true, castToComparable(handlerEnd.getValue()), true);
            qtpMap.put(table, table.createQueryTemplatePacketWithRange(range));
        } else {
            throw new UnsupportedOperationException("NOT BETWEEN is not supported");
        }
    }
}
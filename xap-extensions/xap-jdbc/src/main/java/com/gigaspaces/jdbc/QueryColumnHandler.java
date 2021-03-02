package com.gigaspaces.jdbc;

import com.gigaspaces.jdbc.exceptions.ColumnNotFoundException;
import com.gigaspaces.jdbc.model.table.QueryColumn;
import com.gigaspaces.jdbc.model.table.TableContainer;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItemVisitorAdapter;

import java.util.List;

public class QueryColumnHandler extends SelectItemVisitorAdapter {
    private final List<TableContainer> tables;
    private final List<QueryColumn> queryColumns;

    public QueryColumnHandler(QueryExecutor queryExecutor) {
        this.tables = queryExecutor.getTables();
        this.queryColumns = queryExecutor.getQueryColumns();
    }

    private TableContainer getTableForColumn(Column column) {
        TableContainer tableContainer = null;
        for (TableContainer table : tables) {
            if (column.getTable() != null && !column.getTable().getFullyQualifiedName().equals(table.getTableNameOrAlias()))
                continue;
            for (String columnName : table.getAllColumnNames()) {
                if (column.getColumnName().equals(columnName)) {
                    if (tableContainer == null) {
                        tableContainer = table;
                    } else {
                        throw new IllegalArgumentException("Ambiguous column name [" + column.getColumnName() + "]");
                    }
                }
            }
        }
        if (tableContainer == null) {
            throw new ColumnNotFoundException("Could not find column [" + column.getColumnName() + "]");
        }
        return tableContainer;
    }

    @Override
    public void visit(AllColumns columns) {
        for (TableContainer table : tables) {
            for (String columnName : table.getAllColumnNames()) {
                QueryColumn qc = table.addQueryColumn(columnName, null);
                queryColumns.add(qc);
            }
        }
    }

    @Override
    public void visit(SelectExpressionItem selectExpressionItem) {
        selectExpressionItem.getExpression().accept(new ExpressionVisitorAdapter() {
            @Override
            public void visit(Column column) {
                TableContainer table = getTableForColumn(column);
                QueryColumn qc = table.addQueryColumn(column.getColumnName(), getStringOrNull(selectExpressionItem.getAlias()));
                queryColumns.add(qc);
            }
        });
    }


    private String getStringOrNull(Alias alias) {
        return alias == null ? null : alias.getName();
    }

}
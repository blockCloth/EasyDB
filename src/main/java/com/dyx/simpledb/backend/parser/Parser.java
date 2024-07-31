package com.dyx.simpledb.backend.parser;

import java.util.ArrayList;
import java.util.List;

import com.dyx.simpledb.backend.parser.statement.*;
import com.dyx.simpledb.backend.parser.statement.DeleteObj;
import com.dyx.simpledb.common.Error;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.ItemsListVisitorAdapter;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.create.table.ColumnDefinition;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.create.table.Index;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.drop.Drop;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectVisitorAdapter;
import net.sf.jsqlparser.statement.update.Update;

public class Parser {
    public static Object Parse(byte[] statement) throws Exception {
        String sql = new String(statement).trim();

        if (sql.toUpperCase().startsWith("BEGIN")) {
            return parseBegin(sql);
        } else if (sql.equalsIgnoreCase("ABORT")) {
            return parseAbort();
        } else if (sql.equalsIgnoreCase("COMMIT")) {
            return parseCommit();
        }

        Statement parsedStatement;
        try {
            parsedStatement = CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException e) {
            throw new RuntimeException("Invalid statement: " + sql, e);
        }

        if (parsedStatement instanceof CreateTable) {
            return parseCreate((CreateTable) parsedStatement);
        } else if (parsedStatement instanceof Select) {
            return parseSelect((Select) parsedStatement);
        } else if (parsedStatement instanceof Insert) {
            return parseInsert((Insert) parsedStatement);
        } else if (parsedStatement instanceof Update) {
            return parseUpdate((Update) parsedStatement);
        } else if (parsedStatement instanceof Delete) {
            return parseDelete((Delete) parsedStatement);
        } else if (parsedStatement instanceof Drop) {
            return parseDrop((Drop) parsedStatement);
        } else {
            throw new RuntimeException("Unsupported statement: " + sql);
        }
    }

    private static SelectObj parseSelect(Select select) {
        SelectObj read = new SelectObj();
        List<String> fields = new ArrayList<>();
        select.getSelectBody().accept(new SelectVisitorAdapter() {
            @Override
            public void visit(PlainSelect plainSelect) {
                plainSelect.getSelectItems().forEach(selectItem -> {
                    if (selectItem instanceof SelectExpressionItem) {
                        SelectExpressionItem expressionItem = (SelectExpressionItem) selectItem;
                        fields.add(expressionItem.getExpression().toString());
                    } else {
                        fields.add(selectItem.toString());
                    }
                });
                read.fields = fields.toArray(new String[0]);
                read.tableName = plainSelect.getFromItem().toString();
                if (plainSelect.getWhere() != null) {
                    read.where = parseWhere(plainSelect.getWhere().toString());
                }
            }
        });
        return read;
    }

    private static Show parseShow(Tokenizer tokenizer) throws Exception {
        String tmp = tokenizer.peek();
        if ("".equals(tmp)) {
            return new Show();
        }
        throw Error.InvalidCommandException;
    }

    private static UpdateObj parseUpdate(Update updateStmt) {
        UpdateObj updateObj = new UpdateObj();
        updateObj.tableName = updateStmt.getTable().getName();
        updateObj.fieldName = updateStmt.getColumns().get(0).getColumnName();
        updateObj.value = updateStmt.getExpressions().get(0).toString();
        if (updateStmt.getWhere() != null) {
            updateObj.where = parseWhere(updateStmt.getWhere().toString());
        }
        return updateObj;
    }

    private static DeleteObj parseDelete(Delete deleteStmt) {
        DeleteObj deleteObj = new DeleteObj();
        deleteObj.tableName = deleteStmt.getTable().getName();
        if (deleteStmt.getWhere() != null) {
            deleteObj.where = parseWhere(deleteStmt.getWhere().toString());
        }
        return deleteObj;
    }

    private static InsertObj parseInsert(Insert insertStmt) throws Exception {
        InsertObj insertObj = new InsertObj();
        insertObj.tableName = insertStmt.getTable().getName();
        List<String> values = new ArrayList<>();
        List<String> columnNames = new ArrayList<>();

        // 获取列名
        if (insertStmt.getColumns() != null && !insertStmt.getColumns().isEmpty()) {
            insertStmt.getColumns().forEach(column -> columnNames.add(column.getColumnName()));
        }

        // 获取值
        insertStmt.getItemsList().accept(new ItemsListVisitorAdapter() {
            @Override
            public void visit(ExpressionList expressionList) {
                expressionList.getExpressions().forEach(expression -> {
                    // 将表达式转换为字符串并去掉单引号
                    String value = expression.toString().replace("'", "");
                    // 去掉前后的括号
                    value = value.replaceAll("^\\(|\\)$", "");
                    values.add(value);
                });
            }
        });

        // 检查列名与值的数量是否匹配
        if (!columnNames.isEmpty() && columnNames.size() != values.size()) {
            throw new Exception("Column count does not match value count.");
        }

        insertObj.fields = columnNames.toArray(new String[0]);
        insertObj.values = values.toArray(new String[0]);

        return insertObj;
    }

    private static Where parseWhere(String whereClause) {
        Where where = new Where();
        // 简单解析 where 条件
        String[] parts = whereClause.split("\\s+");
        if (parts.length >= 3) {
            SingleExpression exp1 = new SingleExpression();
            exp1.field = parts[0];
            exp1.compareOp = parts[1];
            exp1.value = parts[2];
            where.singleExp1 = exp1;
            if (parts.length > 3) {
                where.logicOp = parts[3];
                if (parts.length >= 7) {
                    SingleExpression exp2 = new SingleExpression();
                    exp2.field = parts[4];
                    exp2.compareOp = parts[5];
                    exp2.value = parts[6];
                    where.singleExp2 = exp2;
                }
            }
        }
        return where;
    }

    private static Create parseCreate(CreateTable createTable) {
        Create create = new Create();
        create.tableName = createTable.getTable().getName();
        List<String> fieldNames = new ArrayList<>();
        List<String> fieldTypes = new ArrayList<>();
        List<String> indexes = new ArrayList<>();
        List<String> autoIncrement = new ArrayList<>();
        List<String> notNull = new ArrayList<>();
        List<String> unique = new ArrayList<>();


        for (ColumnDefinition columnDefinition : createTable.getColumnDefinitions()) {
            fieldNames.add(columnDefinition.getColumnName());
            fieldTypes.add(columnDefinition.getColDataType().toString());

            if (columnDefinition.getColumnSpecs() != null) {
                for (String columnSpec : columnDefinition.getColumnSpecs()) {
                    if (columnSpec.equalsIgnoreCase("PRIMARY")) {
                        create.primaryKey = columnDefinition.getColumnName();
                    } else if (columnSpec.equalsIgnoreCase("AUTO_INCREMENT")) {
                        autoIncrement.add(columnDefinition.getColumnName());
                    } else if (columnSpec.equalsIgnoreCase("NOT")) {
                        notNull.add(columnDefinition.getColumnName());
                    }else if (columnSpec.equalsIgnoreCase("UNIQUE")) {
                        unique.add(columnDefinition.getColumnName());
                    }
                }
            }
        }

        if (createTable.getIndexes() != null) {
            for (Index index : createTable.getIndexes()) {
                // 只处理单列索引
                if (index.getColumnsNames().size() == 1) {
                    indexes.add(index.getColumnsNames().get(0));
                }
            }
        }

        create.fieldName = fieldNames.toArray(new String[0]);
        create.fieldType = fieldTypes.toArray(new String[0]);
        create.index = indexes.toArray(new String[0]);
        create.autoIncrement = autoIncrement.toArray(new String[0]);
        create.notNull = notNull.toArray(new String[0]);
        create.unique = unique.toArray(new String[0]);

        return create;
    }

    private static DropObj parseDrop(Drop dropStmt) {
        DropObj dropObj = new DropObj();
        dropObj.tableName = dropStmt.getName().getName();
        return dropObj;
    }

    private static Abort parseAbort() {
        return new Abort();
    }

    private static Commit parseCommit() {
        return new Commit();
    }

    private static Begin parseBegin(String sql) throws Exception {
        Tokenizer tokenizer = new Tokenizer(sql.getBytes());
        tokenizer.peek();
        tokenizer.pop();

        String isolation = tokenizer.peek();
        Begin begin = new Begin();
        if ("".equals(isolation)) {
            return begin;
        }
        if (!"isolation".equals(isolation)) {
            throw Error.InvalidCommandException;
        }

        tokenizer.pop();
        String level = tokenizer.peek();
        if (!"level".equals(level)) {
            throw Error.InvalidCommandException;
        }
        tokenizer.pop();

        String tmp1 = tokenizer.peek();
        if ("read".equals(tmp1)) {
            tokenizer.pop();
            String tmp2 = tokenizer.peek();
            if ("committed".equals(tmp2)) {
                tokenizer.pop();
                if (!"".equals(tokenizer.peek())) {
                    throw Error.InvalidCommandException;
                }
                return begin;
            } else {
                throw Error.InvalidCommandException;
            }
        } else if ("repeatable".equals(tmp1)) {
            tokenizer.pop();
            String tmp2 = tokenizer.peek();
            if ("read".equals(tmp2)) {
                begin.isRepeatableRead = true;
                tokenizer.pop();
                if (!"".equals(tokenizer.peek())) {
                    throw Error.InvalidCommandException;
                }
                return begin;
            } else {
                throw Error.InvalidCommandException;
            }
        } else {
            throw Error.InvalidCommandException;
        }
    }

    private static boolean isName(String name) {
        return name.matches("[A-Za-z_][A-Za-z0-9_]*");
    }

    private static boolean isCmpOp(String op) {
        return ("=".equals(op) || ">".equals(op) || "<".equals(op));
    }

    private static boolean isLogicOp(String op) {
        return ("and".equals(op) || "or".equals(op));
    }
}

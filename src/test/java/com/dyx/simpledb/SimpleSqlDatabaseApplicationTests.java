package com.dyx.simpledb;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.create.table.ColumnDefinition;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import org.junit.Test;


public class SimpleSqlDatabaseApplicationTests {

    @Test
    public void testCreateTableParsing() {
        String createTableSQL = "CREATE TABLEs my_table (id INT PRIMARY KEY, name VARCHAR(100))";

        try {
            Statement statement = CCJSqlParserUtil.parse(createTableSQL);
            if (statement instanceof CreateTable) {
                CreateTable createTable = (CreateTable) statement;
                System.out.println("Table Name: " + createTable.getTable().getName());
                for (ColumnDefinition columnDefinition : createTable.getColumnDefinitions()) {
                    System.out.println("Column: " + columnDefinition.getColumnName() + " " + columnDefinition.getColDataType());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

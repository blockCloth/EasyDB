package com.dyx.simpledb.parser;

import com.dyx.simpledb.backend.parser.Parser;
import com.dyx.simpledb.backend.parser.statement.Create;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.create.table.ColumnDefinition;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.create.table.Index;
import net.sf.jsqlparser.statement.create.table.NamedConstraint;
import org.junit.Test;
import org.mockito.internal.util.collections.ListUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * @User Administrator
 * @CreateTime 2024/7/24 15:00
 * @className com.dyx.simpledb.parser.ParserTest
 */
public class ParserTest {

    @Test
    public void select() throws Exception {
        String sql = "SELECT user.id, user.name, orders.order_id FROM user INNER JOIN orders ON user.id = orders.user_id WHERE user.id > 10 AND user.name = 'zhangsan';";
        System.out.println(Parser.Parse(sql.getBytes()));
    }

    @Test
    public void update() throws Exception {
        String sql = "UPDATE user SET name = 'lisi',email = 'zhangsan@qq.com' WHERE id = 1;";
        System.out.println(Parser.Parse(sql.getBytes()));
    }

    @Test
    public void delete() throws Exception {
        String sql = "DELETE FROM user WHERE id = 1;";
        System.out.println(Parser.Parse(sql.getBytes()));
    }

    @Test
    public void testParseCreate() throws Exception {
        // Create a mock SQL create table statement
        String sql = "CREATE TABLE employee (" +
                "employee_id INT AUTO_INCREMENT PRIMARY_KEY, " +
                "name VARCHAR(255) NOT_NULL, " +
                "email VARCHAR(255), " +
                "INDEX idx_email (email)" +
                ")";

        // Parse the SQL create table statement
        CreateTable createTable = (CreateTable) CCJSqlParserUtil.parse(sql);
        Create create = parseCreate(createTable);

        System.out.println(create);
    }

    private static Create parseCreate(CreateTable createTable) {
        Create create = new Create();
        create.tableName = createTable.getTable().getName();
        List<String> fieldNames = new ArrayList<>();
        List<String> fieldTypes = new ArrayList<>();
        List<String> indexes = new ArrayList<>();
        List<String> primaryKey = new ArrayList<>();
        List<String> autoIncrement = new ArrayList<>();
        List<String> notNull = new ArrayList<>();


        for (ColumnDefinition columnDefinition : createTable.getColumnDefinitions()) {
            fieldNames.add(columnDefinition.getColumnName());
            fieldTypes.add(columnDefinition.getColDataType().toString());

            if (columnDefinition.getColumnSpecs() != null) {
                for (String columnSpec : columnDefinition.getColumnSpecs()) {
                    if (columnSpec.equalsIgnoreCase("PRIMARY_KEY")) {
                        primaryKey.add(columnDefinition.getColumnName());
                    } else if (columnSpec.equalsIgnoreCase("AUTO_INCREMENT")) {
                        autoIncrement.add(columnDefinition.getColumnName());
                    } else if (columnSpec.equalsIgnoreCase("NOT_NULL")) {
                        notNull.add(columnDefinition.getColumnName());
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

        return create;
    }

    @Test
    public void insert() throws Exception {
        String sql = "INSERT INTO user VALUES (\"zhangsan\");";
        System.out.println(Parser.Parse(sql.getBytes()));
    }

    @Test
    public void begin() throws Exception {
        String sql1 = "BEGIN isolation level read committed";
        String sql2 = "BEGIN isolation level repeatable read";
        String sql3 = "BEGIN";

        System.out.println(Parser.Parse(sql1.getBytes()));
        System.out.println(Parser.Parse(sql2.getBytes()));
        System.out.println(Parser.Parse(sql3.getBytes()));
    }
}

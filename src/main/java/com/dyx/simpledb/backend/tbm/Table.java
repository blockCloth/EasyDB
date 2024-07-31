package com.dyx.simpledb.backend.tbm;

import java.util.*;
import java.util.stream.Collectors;

import cn.hutool.core.util.StrUtil;
import com.dyx.simpledb.backend.parser.statement.*;
import com.google.common.primitives.Bytes;

import com.dyx.simpledb.backend.parser.statement.DeleteObj;
import com.dyx.simpledb.backend.tbm.Field.ParseValueRes;
import com.dyx.simpledb.backend.tm.TransactionManagerImpl;
import com.dyx.simpledb.backend.utils.Panic;
import com.dyx.simpledb.backend.utils.ParseStringRes;
import com.dyx.simpledb.backend.utils.Parser;
import com.dyx.simpledb.common.Error;

/**
 * Table 维护了表结构
 * 二进制结构如下：
 * [TableName][NextTable]
 * [Field1Uid][Field2Uid]...[FieldNUid]
 */
public class Table {
    TableManager tbm;
    long uid;
    String name;
    byte status;
    long nextUid;
    List<Field> fields = new ArrayList<>();
    public static final String GEN_CLUST_INDEX = "GEN_CLUST_INDEX";
    // 存储需要自增的字段
    static Set<String> autoIncrementFields = new HashSet<>();
    static Set<Long> fullScanUid = new HashSet<>();

    public static Table loadTable(TableManager tbm, long uid) {
        byte[] raw = null;
        try {
            raw = ((TableManagerImpl) tbm).vm.read(TransactionManagerImpl.SUPER_XID, uid);
        } catch (Exception e) {
            Panic.panic(e);
        }
        assert raw != null;
        Table tb = new Table(tbm, uid);
        return tb.parseSelf(raw);
    }

    public static Table createTable(TableManager tbm, long nextUid, long xid, Create create) throws Exception {
        Table tb = new Table(tbm, create.tableName, nextUid);

        String primaryKey = create.primaryKey;
        // 获取非空字段名
        Set<String> notNullFields = new HashSet<>(Arrays.asList(create.notNull));
        // 获取自增字段名
        autoIncrementFields = new HashSet<>(Arrays.asList(create.autoIncrement));
        // 获取唯一约束
        Set<String> uniqueFields = new HashSet<>(Arrays.asList(create.unique));

        boolean hideIndex = false;

        for (int i = 0; i < create.fieldName.length; i++) {
            String fieldName = create.fieldName[i];
            String fieldType = create.fieldType[i];

            // 判断是否为主键
            boolean isPrimaryKey = StrUtil.isNotEmpty(primaryKey) && primaryKey.equalsIgnoreCase(fieldName);
            boolean isNotNull = notNullFields.contains(fieldName) || isPrimaryKey;
            boolean isUnique = uniqueFields.contains(fieldName) || isPrimaryKey;
            boolean isAutoIncrement = autoIncrementFields.contains(fieldName);

            // 检查自增字段的类型是否为整数
            if (isAutoIncrement) {
                if (!fieldType.equalsIgnoreCase("int")) {
                    throw new IllegalArgumentException("Field " + fieldName + " is set to auto-increment but its type is not 'int'. Auto-increment fields must be of type 'int'.");
                }
            }

            boolean indexed = false;
            if (isPrimaryKey || (!hideIndex && isNotNull)) {
                indexed = true;
                hideIndex = true;
            }

            tb.fields.add(Field.createField(tb, xid, fieldName, fieldType, indexed, isAutoIncrement, isNotNull, isUnique));
        }

        if (!hideIndex) {
            tb.fields.add(Field.createField(tb, xid, GEN_CLUST_INDEX, "int", true, true, true, true));
            autoIncrementFields.add(GEN_CLUST_INDEX);
        }

        return tb.persistSelf(xid);
    }


    public Table(TableManager tbm, long uid) {
        this.tbm = tbm;
        this.uid = uid;
    }

    public Table(TableManager tbm, String tableName, long nextUid) {
        this.tbm = tbm;
        this.name = tableName;
        this.nextUid = nextUid;
    }

    private Table parseSelf(byte[] raw) {
        int position = 0;
        ParseStringRes res = Parser.parseString(raw);
        name = res.str;
        position += res.next;
        nextUid = Parser.parseLong(Arrays.copyOfRange(raw, position, position + 8));
        position += 8;

        while (position < raw.length) {
            long uid = Parser.parseLong(Arrays.copyOfRange(raw, position, position + 8));
            position += 8;
            fields.add(Field.loadField(this, uid));
        }
        return this;
    }

    private Table persistSelf(long xid) throws Exception {
        byte[] nameRaw = Parser.string2Byte(name);
        byte[] nextRaw = Parser.long2Byte(nextUid);
        byte[] fieldRaw = new byte[0];
        for (Field field : fields) {
            fieldRaw = Bytes.concat(fieldRaw, Parser.long2Byte(field.uid));
        }
        uid = ((TableManagerImpl) tbm).vm.insert(xid, Bytes.concat(nameRaw, nextRaw, fieldRaw));
        return this;
    }

    public int delete(long xid, DeleteObj deleteObj) throws Exception {
        List<Long> uids = parseWhere(deleteObj.where);
        int count = 0;
        for (Long uid : uids) {
            if (((TableManagerImpl) tbm).vm.delete(xid, uid)) {
                count++;
                fullScanUid.remove(uid);
            }
        }
        return count;
    }

    public int update(long xid, UpdateObj updateObj) throws Exception {
        List<Long> uids = parseWhere(updateObj.where);
        Field fd = null;
        for (Field f : fields) {
            if (f.fieldName.equals(updateObj.fieldName)) {
                fd = f;
                break;
            }
        }
        if (fd == null) {
            throw Error.FieldNotFoundException;
        }
        Object value = fd.string2Value(updateObj.value);
        int count = 0;
        for (Long uid : uids) {
            byte[] raw = ((TableManagerImpl) tbm).vm.read(xid, uid);
            if (raw == null) continue;

            ((TableManagerImpl) tbm).vm.delete(xid, uid);

            Map<String, Object> entry = parseEntry(raw);
            entry.put(fd.fieldName, value);
            raw = entry2Raw(entry);
            long uuid = ((TableManagerImpl) tbm).vm.insert(xid, raw);

            count++;

            for (Field field : fields) {
                if (field.isIndexed()) {
                    field.insert(entry.get(field.fieldName), uuid);
                    fullScanUid.remove(uid);
                    fullScanUid.add(uuid);
                }
            }
        }
        return count;
    }

    public String read(long xid, SelectObj read) throws Exception {
        List<Long> uids = parseWhere(read.where);
        StringBuilder sb = new StringBuilder();
        for (Long uid : uids) {
            byte[] raw = ((TableManagerImpl) tbm).vm.read(xid, uid);
            if (raw == null) continue;
            Map<String, Object> entry = parseEntry(raw);
            sb.append(printEntry(entry)).append("\n");
        }
        return sb.toString();
    }

    public void insert(long xid, InsertObj insertObj) throws Exception {
        Map<String, Object> entry = string2Entry(insertObj);
        byte[] raw = entry2Raw(entry);
        long uid = ((TableManagerImpl) tbm).vm.insert(xid, raw);
        fullScanUid.add(uid);
        for (Field field : fields) {
            if (field.isIndexed()) {
                field.insert(entry.get(field.fieldName), uid);
            }
        }
        // 更新唯一值集合
        updateUniqueValues(entry);
    }

    private void updateUniqueValues(Map<String, Object> entry) {
        for (Field field : fields) {
            if (field.isUnique && entry.containsKey(field.fieldName)) {
                field.uniqueValues.add(entry.get(field.fieldName));
            }
        }
    }

    private Map<String, Object> string2Entry(InsertObj insertObj) {
        Map<String, Object> entry = new HashMap<>();
        int valuesIndex = 0;

        // 判断字段表是否为空，若为空则表示需要插入所有字段
        if (insertObj.fields.length == 0) {
            for (Field field : fields) {
                Object v = null;
                // 判断是否存在自增字段
                if (autoIncrementFields.contains(field.fieldName)) {
                    if (valuesIndex < insertObj.values.length && insertObj.values[valuesIndex] != null && !insertObj.values[valuesIndex].isEmpty()) {
                        // 使用提供的值并更新自增器
                        v = field.string2Value(insertObj.values[valuesIndex]);
                        field.atomicInteger.set(Integer.parseInt(insertObj.values[valuesIndex]) + 1);
                        valuesIndex++;
                    } else {
                        // 自增
                        v = field.string2Value(String.valueOf(field.atomicInteger.getAndIncrement()));
                    }
                } else if (valuesIndex < insertObj.values.length) {
                    v = field.string2Value(insertObj.values[valuesIndex]);
                    valuesIndex++;
                } else {
                    v = field.defaultValue;
                }
                entry.put(field.fieldName, v);

                // 检查唯一性约束
                if (field.isUnique && field.valueExists(v)) {
                    throw new IllegalArgumentException("Field " + field.fieldName + " must be unique.");
                }

                // 检查非空约束
                if (field.isNotNull && (v == null || (v instanceof String && ((String) v).isEmpty()))) {
                    throw new IllegalArgumentException("Field " + field.fieldName + " cannot be null or empty.");
                }
            }
            return entry;
        }

        // 获取需要插入的字段
        Set<String> specifiedFields = new HashSet<>(Arrays.asList(insertObj.fields));
        valuesIndex = 0;

        // 处理需要插入的字段
        for (Field field : fields) {
            Object v;
            if (specifiedFields.contains(field.fieldName)) {
                v = field.string2Value(insertObj.values[valuesIndex++]);
                // 检查唯一性约束
                if (field.isUnique && field.valueExists(v)) {
                    throw new IllegalArgumentException("Field " + field.fieldName + " must be unique.");
                }
                // 更新自增器
                if (autoIncrementFields.contains(field.fieldName) && v != null && !v.toString().isEmpty()) {
                    field.atomicInteger.set(Integer.parseInt(v.toString()) + 1);
                }
            } else if (autoIncrementFields.contains(field.fieldName)) {
                v = field.string2Value(String.valueOf(field.atomicInteger.getAndIncrement()));
            } else {
                v = field.defaultValue;
            }

            // 检查非空约束
            if (field.isNotNull && (v == null || (v instanceof String && ((String) v).isEmpty()))) {
                throw new IllegalArgumentException("Field " + field.fieldName + " cannot be null or empty.");
            }
            entry.put(field.fieldName, v);
        }

        return entry;
    }

    private List<Long> parseWhere(Where where) throws Exception {
        // 初始化搜索范围和标志位
        long l0 = 0, r0 = 0, l1 = 0, r1 = 0;
        boolean single = false;
        Field fd = null;

        // 如果 WHERE 子句为空，则搜索所有记录
        if (where == null) {
            return new ArrayList<>(fullScanUid);
        } else {
            // 如果 WHERE 子句不为空，则根据 WHERE 子句解析搜索范围
            // 寻找 WHERE 子句中涉及的字段
            for (Field field : fields) {
                if (field.fieldName.equals(where.singleExp1.field)) {
                    // 如果字段没有索引，则抛出异常,还需要改进，若没有索引则
                    if (!field.isIndexed()) {
                        throw Error.FieldNotIndexedException;
                    }
                    fd = field;
                    break;
                }
            }
            // 如果字段不存在，则抛出异常
            if (fd == null) {
                throw Error.FieldNotFoundException;
            }
            // 计算 WHERE 子句的搜索范围
            CalWhereRes res = calWhere(fd, where);
            l0 = res.l0;
            r0 = res.r0;
            l1 = res.l1;
            r1 = res.r1;
            single = res.single;
        }

        // 在计算出的搜索范围内搜索记录
        List<Long> uids = fd.search(l0, r0);
        // 如果 WHERE 子句包含 OR 运算符，则需要搜索两个范围，并将结果合并
        if (!single) {
            List<Long> tmp = fd.search(l1, r1);
            uids.addAll(tmp);
        }
        // 返回搜索结果
        return uids;
    }

    class CalWhereRes {
        long l0, r0, l1, r1;
        boolean single;
    }

    private CalWhereRes calWhere(Field fd, Where where) throws Exception {
        CalWhereRes res = new CalWhereRes();
        switch (where.logicOp) {
            case "":
                res.single = true;
                FieldCalRes r = fd.calExp(where.singleExp1);
                res.l0 = r.left;
                res.r0 = r.right;
                break;
            case "or":
                res.single = false;
                r = fd.calExp(where.singleExp1);
                res.l0 = r.left;
                res.r0 = r.right;
                r = fd.calExp(where.singleExp2);
                res.l1 = r.left;
                res.r1 = r.right;
                break;
            case "and":
                res.single = true;
                r = fd.calExp(where.singleExp1);
                res.l0 = r.left;
                res.r0 = r.right;
                r = fd.calExp(where.singleExp2);
                res.l1 = r.left;
                res.r1 = r.right;
                if (res.l1 > res.l0) res.l0 = res.l1;
                if (res.r1 < res.r0) res.r0 = res.r1;
                break;
            default:
                throw Error.InvalidLogOpException;
        }
        return res;
    }

    /*  private String printEntry(Map<String, Object> entry) {
          StringBuilder sb = new StringBuilder("[");
          for (int i = 0; i < fields.size(); i++) {
              Field field = fields.get(i);
              if (field.fieldName.equals(GEN_CLUST_INDEX)){
                  continue;
              }
              sb.append(field.printValue(entry.get(field.fieldName)));
              if (i == fields.size() - 1) {
                  sb.append("]");
              } else {
                  sb.append(", ");
              }
          }
          return sb.toString();
      }*/
    private String printEntry(Map<String, Object> entry) {
        // 存储每列的最大宽度
        Map<String, Integer> columnWidths = new HashMap<>();

        // 计算每列的最大宽度
        for (Field field : fields) {
            if (field.fieldName.equals(GEN_CLUST_INDEX)) {
                continue;
            }
            String fieldName = field.fieldName;
            int maxLength = fieldName.length();
            String value = field.printValue(entry.get(fieldName));
            if (value.length() > maxLength) {
                maxLength = value.length();
            }
            columnWidths.put(fieldName, maxLength);
        }

        StringBuilder sb = new StringBuilder();

        // 输出列名
        sb.append("|");
        for (Field field : fields) {
            if (field.fieldName.equals(GEN_CLUST_INDEX)) {
                continue;
            }
            String fieldName = field.fieldName;
            int width = columnWidths.get(fieldName);
            sb.append(String.format("%-" + width + "s|", fieldName));
        }
        sb.append("\n");

        // 输出分隔符
        sb.append("|");
        for (Field field : fields) {
            if (field.fieldName.equals(GEN_CLUST_INDEX)) {
                continue;
            }
            String fieldName = field.fieldName;
            int width = columnWidths.get(fieldName);
            sb.append(repeat("-", width)).append("|");
        }
        sb.append("\n");

        // 输出数据
        sb.append("|");
        for (Field field : fields) {
            if (field.fieldName.equals(GEN_CLUST_INDEX)) {
                continue;
            }
            String fieldName = field.fieldName;
            String value = field.printValue(entry.get(fieldName));
            int width = columnWidths.get(fieldName);
            sb.append(String.format("%-" + width + "s|", value));
        }

        return sb.toString();
    }
    
    public static String repeat(String str, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    private Map<String, Object> parseEntry(byte[] raw) {
        int pos = 0;
        Map<String, Object> entry = new HashMap<>();
        for (Field field : fields) {
            ParseValueRes r = field.parserValue(Arrays.copyOfRange(raw, pos, raw.length));
            entry.put(field.fieldName, r.v);
            pos += r.shift;
        }
        return entry;
    }


    private byte[] entry2Raw(Map<String, Object> entry) {
        byte[] raw = new byte[0];
        for (Field field : fields) {
            raw = Bytes.concat(raw, field.value2Raw(entry.get(field.fieldName)));
        }
        return raw;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        sb.append(name).append(": ");
        for (Field field : fields) {
            sb.append(field.toString());
            if (field == fields.get(fields.size() - 1)) {
                sb.append("}");
            } else {
                sb.append(", ");
            }
        }
        return sb.toString();
    }
}

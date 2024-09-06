package com.dyx.simpledb.backend.tbm;

import java.util.*;
import java.util.stream.Collectors;

import cn.hutool.core.util.StrUtil;
import com.dyx.simpledb.backend.im.UniqueIndex;
import com.dyx.simpledb.backend.parser.statement.*;
import com.dyx.simpledb.backend.utils.*;
import com.dyx.simpledb.backend.vm.Transaction;
import com.google.common.primitives.Bytes;

import com.dyx.simpledb.backend.parser.statement.DeleteObj;
import com.dyx.simpledb.backend.tbm.Field.ParseValueRes;
import com.dyx.simpledb.backend.tm.TransactionManagerImpl;
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
    // 定义一个字段缓存，用于全表查询
    private Map<String, Field> fieldCache = new HashMap<>();
    // 在 Table 类中定义一个哈希索引的存储结构
    private Map<String, UniqueIndex> hasUniqueIndexes = new HashMap<>();

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
        Set<String> autoIncrementFields = new HashSet<>(Arrays.asList(create.autoIncrement));
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
                if (!fieldType.equalsIgnoreCase("int") && !fieldType.equalsIgnoreCase("long")) {
                    throw new IllegalArgumentException("Field " + fieldName + " is set to auto-increment but its type is not 'int' or 'long'. Auto-increment fields must be of type 'int' or 'long'.");
                }
            }

            boolean indexed = false;
            if (isPrimaryKey) {
                indexed = true;
                hideIndex = true;
            }

            // 如果字段是唯一的，创建哈希索引
            if (isUnique) {
                tb.hasUniqueIndexes.put(fieldName, new UniqueIndex());
            }
            tb.fields.add(Field.createField(tb, xid, fieldName, fieldType, indexed, isAutoIncrement, isNotNull, isUnique, isPrimaryKey));
        }

        if (!hideIndex) {
            // 创建自增的隐藏字段
            tb.fields.add(Field.createField(tb, xid, GEN_CLUST_INDEX, "int", true, true, true, true, false));
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

    public Table persistSelf(long xid) throws Exception {
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
        checkColumn(deleteObj.where);
        List<Long> uids = parseWhere(deleteObj.where, xid);
        int count = 0;
        for (Long uid : uids) {
            Map<String, Object> entry = parseEntry(((TableManagerImpl) tbm).vm.read(xid, uid));

            // 删除哈希索引中的记录
            for (Field field : fields) {
                if (field.isUnique) {
                    UniqueIndex index = hasUniqueIndexes.get(field.fieldName);
                    if (index != null) {
                        index.delete(field.fieldName, entry.get(field.fieldName), xid);
                    }
                }
            }
            // 删除数据
            if (((TableManagerImpl) tbm).vm.delete(xid, uid)) {
                count++;
            }
        }

        // 记录该表被修改
        Transaction t = ((TableManagerImpl) tbm).vm.getActiveTransaction(xid);
        t.addModifiedTable(this);
        return count;
    }

    public int update(long xid, UpdateObj updateObj) throws Exception {
        checkColumn(updateObj.where);
        List<Long> uids = parseWhere(updateObj.where, xid);

        boolean fieldIsExist = false;
        // 匹配需要修改的字段是否存在
        for (Field field : fields) {
            for (String fieldName : updateObj.fieldName) {
                if (field.fieldName.equalsIgnoreCase(fieldName)) {
                    fieldIsExist = true;
                }
            }
        }
        if (!fieldIsExist)
            throw Error.FieldNotFoundException;

        int count = 0;
        for (Long uid : uids) {
            byte[] raw = ((TableManagerImpl) tbm).vm.read(xid, uid);
            if (raw == null) continue;

            Map<String, Object> entry = parseEntry(raw);
            // 删除旧值的唯一索引，只针对变更的字段
            for (Field field : fields) {
                for (int i = 0; i < updateObj.fieldName.length; i++) {
                    String fieldName = updateObj.fieldName[i];
                    Object newValue = updateObj.value[i];
                    Object oldValue = entry.get(fieldName);
                    // 仅删除发生变化的唯一索引
                    if (!newValue.equals(oldValue) && field.isUnique && field.fieldName.equalsIgnoreCase(fieldName)) {
                        hasUniqueIndexes.get(field.fieldName).update(field.fieldName, oldValue, newValue, xid);
                    }
                }
            }

            // 更新字段的值
            for (int i = 0; i < updateObj.fieldName.length; i++) {
                entry.put(updateObj.fieldName[i], updateObj.value[i]);
            }

            // 写回更新后的记录
            raw = entry2Raw(entry);
            long newUid = ((TableManagerImpl) tbm).vm.insert(xid, raw);

            // 删除旧记录
            ((TableManagerImpl) tbm).vm.delete(xid, uid);
            count++;
            // 插入新的唯一索引，仅针对变更的字段
            for (Field field : fields) {
                if (field.isIndexed()) {
                    field.insert(entry.get(field.fieldName), newUid);
                }
            }
        }

        // 记录该表被修改
        Transaction t = ((TableManagerImpl) tbm).vm.getActiveTransaction(xid);
        t.addModifiedTable(this);

        return count;
    }

    public String read(long xid, SelectObj read) throws Exception {
        checkColumn(read.where);
        List<Long> uids = parseWhere(read.where, xid);
        List<Map<String, Object>> entries = new ArrayList<>();
        String[] fieldsToOutput;

        // 获取需要输出的列名，实现指定查找
        if (read.fields.length == 1 && read.fields[0].equals("*")) {
            fieldsToOutput = fields.stream()
                    .filter(field -> !field.fieldName.equalsIgnoreCase(GEN_CLUST_INDEX))
                    .map(field -> field.fieldName)
                    .toArray(String[]::new);
        } else {
            fieldsToOutput = read.fields;
        }

        for (Long uid : uids) {
            byte[] raw = ((TableManagerImpl) tbm).vm.read(xid, uid);
            if (raw == null) continue;

            Map<String, Object> entry = parseEntry(raw);
            // 保留用户选择字段
            if (read.fields.length == 1 && read.fields[0].equals("*")) {
                entries.add(entry);
            } else {
                Map<String, Object> filterEntry = new HashMap<>();
                for (String fieldName : read.fields) {
                    if (fieldName.equals(GEN_CLUST_INDEX))
                        continue;
                    if (entry.containsKey(fieldName)) {
                        filterEntry.put(fieldName, entry.get(fieldName));
                    }
                }
                entries.add(filterEntry);
            }
        }

        // 对 entries 进行排序
        if (read.orderByExpression != null && read.orderByExpression.fields.length > 0) {
            entries = entries.stream().sorted(new Comparator<Map<String, Object>>() {
                @Override
                public int compare(Map<String, Object> o1, Map<String, Object> o2) {
                    for (int i = 0; i < read.orderByExpression.fields.length; i++) {
                        String field = read.orderByExpression.fields[i];
                        boolean ascending = read.orderByExpression.order[i];

                        Comparable val1 = (Comparable) o1.get(field);
                        Comparable val2 = (Comparable) o2.get(field);

                        int cmp = val1.compareTo(val2);
                        if (cmp != 0) {
                            return ascending ? cmp : -cmp;
                        }
                    }
                    return 0;
                }
            }).collect(Collectors.toList());
        }

        return printEntries(entries, fieldsToOutput);
    }

    public void insert(long xid, InsertObj insertObj) throws Exception {
        Map<String, Object> entry = string2Entry(insertObj);
        Set<String> insertedUniqueFields = new HashSet<>();  // 用于记录成功插入的唯一索引字段

        try {
            // 在执行插入操作之前，确保所有唯一索引检查通过
            for (Field field : fields) {
                if (field.isUnique) {
                    // 先检查唯一索引，确保没有冲突
                    hasUniqueIndexes.get(field.fieldName).insert(field.fieldName, entry.get(field.fieldName), xid);
                    // 如果插入成功，记录这个字段的唯一索引
                    insertedUniqueFields.add(field.fieldName);
                }
            }
        } catch (Exception e) {
            // 如果发生异常，回滚所有唯一索引的插入操作
            for (String fieldName : insertedUniqueFields) {
                hasUniqueIndexes.get(fieldName).delete(fieldName, entry.get(fieldName), xid);
            }
            throw new RuntimeException("Insert failed," + e.getMessage(), e);
        }

        // 插入数据到存储中
        byte[] raw = entry2Raw(entry);
        long uid = ((TableManagerImpl) tbm).vm.insert(xid, raw);

        // 将数据插入索引
        for (Field field : fields) {
            if (field.isIndexed()) {
                field.insert(entry.get(field.fieldName), uid);
            }
        }

        // 记录该表被修改
        Transaction t = ((TableManagerImpl) tbm).vm.getActiveTransaction(xid);
        t.addModifiedTable(this);
    }


    public void drop(long xid) throws Exception {
        // 先删除表中所有数据
        List<Long> allUid = getAllUid();
        for (Long uid : allUid) {
            // 先逻辑删除，确保无事务引用
            ((TableManagerImpl) tbm).vm.delete(xid, uid);
        }
        for (Long uid : allUid) {
            // 再物理删除
            ((TableManagerImpl) tbm).vm.physicalDelete(xid, uid);
        }
        // 4. 删除表的字段和索引元数据
        for (Field field : fields) {
            ((TableManagerImpl) tbm).vm.physicalDelete(xid, field.uid); // 物理删除字段元数据
            // 删除对应的唯一索引
            if (field.isUnique) {
                hasUniqueIndexes.get(field.fieldName).remove(field.fieldName);
                hasUniqueIndexes.remove(field.fieldName);
            }
        }
        // 5. 删除表的自身元数据
        ((TableManagerImpl) tbm).vm.physicalDelete(xid, this.uid); // 物理删除表元数据
    }

    private Map<String, Object> string2Entry(InsertObj insertObj) {
        Map<String, Object> entry = new HashMap<>();
        int valuesIndex = 0;

        // 判断字段表是否为空，若为空则表示需要插入所有字段
        if (insertObj.fields.length == 0) {
            for (Field field : fields) {
                Object v = null;
                // 判断是否存在自增字段
                if (field.isAutoIncrement) {
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
                    v = field.string2Value(removeQuotes(insertObj.values[valuesIndex]));
                    valuesIndex++;
                } else {
                    v = field.defaultValue;
                }

                entry.put(field.fieldName, v);

                // 检查唯一性约束
                if (field.isUnique && hasUniqueIndexes.get(field.fieldName).search(field.fieldName, entry.get(field.fieldName))) {
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
                v = field.string2Value(removeQuotes(insertObj.values[valuesIndex++]));
                // 检查唯一性约束
                if (field.isUnique && hasUniqueIndexes.get(field.fieldName).search(field.fieldName, entry.get(field.fieldName))) {
                    throw new IllegalArgumentException("Field " + field.fieldName + " must be unique.");
                }
                // 更新自增器
                if (field.isAutoIncrement && v != null && !v.toString().isEmpty()) {
                    field.atomicInteger.set(Integer.parseInt(v.toString()) + 1);
                }
            } else if (field.isPrimaryKey) {
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

    // 去除字符串前后引号
    private String removeQuotes(String value) {
        if (value != null && value.length() > 1 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private List<Long> parseWhere(Where where, long xid) throws Exception {
        if (where == null)
            return getAllUid();

        Field indexedField1 = findIndexedField(where.singleExp1.field);
        Field indexedField2 = where.singleExp2 != null ? findIndexedField(where.singleExp2.field) : null;

        List<Long> uids;

        // 如果两个条件字段都没有索引，执行全表扫描
        if (indexedField1 == null && indexedField2 == null) {
            return performFullTableScanWithCondition(where, xid);
        }

        // 如果第一个条件字段有索引，使用第一个条件字段进行初步查询
        if (indexedField1 != null) {
            CalWhereRes res = calWhere(indexedField1, where.singleExp1);
            uids = indexedField1.search(res.l0, res.r0);

            // 如果存在第二个条件字段
            if (where.singleExp2 != null) {
                // 如果第二个条件字段也有索引，进行第二次索引查询
                if (indexedField2 != null) {
                    CalWhereRes res2 = calWhere(indexedField2, where.singleExp2);
                    List<Long> additionalUids = indexedField2.search(res2.l0, res2.r0);

                    if ("and".equals(where.logicOp)) {
                        uids.retainAll(additionalUids); // 取交集
                    } else {
                        Set<Long> mergedSet = new HashSet<>(uids);
                        mergedSet.addAll(additionalUids);
                        uids = new ArrayList<>(mergedSet); // 取并集
                    }
                } else {
                    // 如果第二个条件字段没有索引，执行全表扫描，并取并集
                    List<Long> additionalUids = performFullTableScanWithCondition(new Where(where.singleExp2), xid);
                    if ("and".equals(where.logicOp)) {
                        uids.retainAll(additionalUids); // 取交集
                    } else {
                        Set<Long> mergedSet = new HashSet<>(uids);
                        mergedSet.addAll(additionalUids);
                        uids = new ArrayList<>(mergedSet); // 取并集
                    }
                }
            }
        } else {
            // 如果第一个条件字段没有索引但第二个条件字段有索引
            CalWhereRes res = calWhere(indexedField2, where.singleExp2);
            uids = indexedField2.search(res.l0, res.r0);

            // 因为第一个条件字段没有索引，需要全表扫描
            List<Long> additionalUids = performFullTableScanWithCondition(new Where(where.singleExp1), xid);
            if ("and".equals(where.logicOp)) {
                uids.retainAll(additionalUids); // 取交集
            } else {
                Set<Long> mergedSet = new HashSet<>(uids);
                mergedSet.addAll(additionalUids);
                uids = new ArrayList<>(mergedSet); // 取并集
            }
        }

        return uids;
    }

    private Field findIndexedField(String fieldName) {
        return fields.stream()
                .filter(field -> field.fieldName.equals(fieldName) && field.isIndexed())
                .findFirst()
                .orElse(null);
    }

    private List<Long> getAllUid() throws Exception {
        Field fd = null;
        for (Field field : fields) {
            if (field.isIndexed()) {
                fd = field;
                break;
            }
        }
        return fd.search(0, Integer.MAX_VALUE);
    }

    private List<Long> performFullTableScanWithCondition(Where where, long xid) throws Exception {
        List<Long> uids = new ArrayList<>();
        for (Long uid : getAllUid()) {
            byte[] data = ((TableManagerImpl) tbm).vm.read(xid, uid);
            if (data == null) continue;

            Map<String, Object> record = parseEntry(data);

            if (satisfiesCondition(record, where)) {
                uids.add(uid);
            }
        }
        return uids;
    }

    private boolean satisfiesCondition(Map<String, Object> record, Where where) throws Exception {
        // 先初始化处理singleExp1
        boolean result1 = checkSingleCondition(record, where.singleExp1);
        if (where.singleExp2 == null) {
            return result1;
        }

        // 再次处理singleExp2的结果
        boolean result2 = checkSingleCondition(record, where.singleExp2);

        switch (where.logicOp) {
            case "and":
                return result1 && result2;
            case "or":
                return result1 || result2;
            default:
                throw new IllegalArgumentException("Unsupported logical operation: " + where.logicOp);
        }
    }

    private boolean checkSingleCondition(Map<String, Object> record, SingleExpression singleExp) throws Exception {
        // 从记录中获取字段值
        Object valueInRecord = record.get(singleExp.field);
        if (valueInRecord == null) return false; // 记录中没有对应的字段

        // 使用 string2Value 将条件的字符串值转换为适当的对象类型
        Object conditionValue = string2Value(singleExp.value, singleExp.field);

        // 如果转换后的值为空，说明类型不匹配或字段不存在，返回 false
        if (conditionValue == null) return false;

        // 执行比较操作,将 valueInRecord 强制转换为 Comparable<Object>，以便后续可以调用 compareTo 方法进行比较
        @SuppressWarnings("unchecked")
        Comparable<Object> comparableValueInRecord = (Comparable<Object>) valueInRecord;

        switch (singleExp.compareOp.toLowerCase()) {
            case "=":
                return comparableValueInRecord.compareTo(conditionValue) == 0;
            case ">":
                return comparableValueInRecord.compareTo(conditionValue) > 0;
            case "<":
                return comparableValueInRecord.compareTo(conditionValue) < 0;
            case ">=":
                return comparableValueInRecord.compareTo(conditionValue) >= 0;
            case "<=":
                return comparableValueInRecord.compareTo(conditionValue) <= 0;
            case "!=":
                return comparableValueInRecord.compareTo(conditionValue) != 0;
            case "like":
                return ((String) valueInRecord).contains((String) conditionValue);
            // 其他比较操作
            default:
                throw new IllegalArgumentException("Unsupported comparison operation: " + singleExp.compareOp);
        }
    }

    private Object string2Value(String value, String fieldName) {
        // 引入 fieldCache用于缓存字段名和 Field 对象之间的映射关系，减少多次查找同一字段的开销。
        Field field = fieldCache.computeIfAbsent(fieldName, k -> fields.stream()
                .filter(f -> f.fieldName.equals(k))
                .findFirst()
                .orElse(null));

        if (field != null) {
            Types.SupportedType type = Types.SupportedType.fromTypeName(field.fieldType);
            return type.parseValue(value);
        }
        return null;
    }


    class CalWhereRes {
        long l0, r0, l1, r1;
        boolean single;
    }

    private CalWhereRes calWhere(Field field, SingleExpression exp) throws Exception {
        CalWhereRes res = new CalWhereRes();
        FieldCalRes r = field.calExp(exp);
        res.l0 = r.left;
        res.r0 = r.right;
        res.single = true;
        return res;
    }

    private String printEntries(List<Map<String, Object>> entries, String[] selectedFields) {
        // if (entries == null || entries.isEmpty()) return "";
        return PrintUtil.printTable(selectedFields, entries);
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

    private boolean checkColumn(Where where) throws Exception {
        if (where == null) return true;

        // 检查 singleExp1 字段
        if (where.singleExp1 != null && where.singleExp1.field != null) {
            boolean found = false;
            for (Field field : fields) {
                if (field.fieldName.equalsIgnoreCase(where.singleExp1.field)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw Error.FieldNotFoundException;
            }
        }

        // 检查 singleExp2 字段
        if (where.singleExp2 != null && where.singleExp2.field != null) {
            boolean found = false;
            for (Field field : fields) {
                if (field.fieldName.equalsIgnoreCase(where.singleExp2.field)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw Error.FieldNotFoundException;
            }
        }

        return true;
    }

    // 事务提交操作
    public void commit(long xid) throws Exception {
        // 提交唯一索引
        for (UniqueIndex index : hasUniqueIndexes.values()) {
            index.commit(xid); // 提交唯一索引的更改
        }
    }

    // 事务回滚操作
    public void rollback(long xid) {
        // 回滚唯一索引
        for (UniqueIndex index : hasUniqueIndexes.values()) {
            index.rollback(xid); // 回滚唯一索引的更改
        }
    }
}

package com.dyx.simpledb.backend.tbm;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.dyx.simpledb.backend.utils.Types;
import com.google.common.primitives.Bytes;

import com.dyx.simpledb.backend.im.BPlusTree;
import com.dyx.simpledb.backend.parser.statement.SingleExpression;
import com.dyx.simpledb.backend.tm.TransactionManagerImpl;
import com.dyx.simpledb.backend.utils.Panic;
import com.dyx.simpledb.backend.utils.ParseStringRes;
import com.dyx.simpledb.backend.utils.Parser;
import com.dyx.simpledb.common.Error;

/**
 * field 表示字段信息
 * 二进制格式为：
 * [FieldName][TypeName][IndexUid]
 * 如果field无索引，IndexUid为0
 */
public class Field {
    long uid;
    private Table tb;
    String fieldName;
    String fieldType;
    private long index;
    private BPlusTree bt;
    Object defaultValue;
    boolean isAutoIncrement;
    // 增加非空约束
    boolean isNotNull;
    // 定义主键自增行为
    AtomicInteger atomicInteger;
    // 唯一约束
    boolean isUnique;
    boolean isPrimaryKey;

    public static Field loadField(Table tb, long uid) {
        byte[] raw = null;
        try {
            raw = ((TableManagerImpl) tb.tbm).vm.read(TransactionManagerImpl.SUPER_XID, uid);
        } catch (Exception e) {
            Panic.panic(e);
        }
        assert raw != null;
        return new Field(uid, tb).parseSelf(raw);
    }

    public Field(long uid, Table tb) {
        this.uid = uid;
        this.tb = tb;
        this.atomicInteger = new AtomicInteger(1);
    }

    public Field(Table tb, String fieldName, String fieldType, long index,
                 boolean isAutoIncrement, boolean isNotNull, boolean isUnique,boolean isPrimaryKey) {
        this.tb = tb;
        this.fieldName = fieldName;
        this.fieldType = fieldType;
        this.index = index;
        this.defaultValue = getDefaultValue();
        this.isAutoIncrement = isAutoIncrement;
        this.isNotNull = isNotNull;
        this.isUnique = isUnique;
        this.atomicInteger = new AtomicInteger(1);
        this.isPrimaryKey = isPrimaryKey;
    }

    private Object getDefaultValue() {
        Types.SupportedType type = Types.SupportedType.fromTypeName(fieldType);
        return type.getDefaultValue();
    }

    private Field parseSelf(byte[] raw) {
        int position = 0;
        // 解析约束条件
        byte[] constraintRaw = Arrays.copyOf(raw, 4);
        parseSelfConstraint(constraintRaw);
        position += 4;

        // 解析字段名称
        ParseStringRes res = Parser.parseString(Arrays.copyOfRange(raw,position,raw.length));
        fieldName = res.str;
        position += res.next;

        // 解析字段类型
        res = Parser.parseString(Arrays.copyOfRange(raw, position, raw.length));
        fieldType = res.str;
        position += res.next;
        // 获取初始值
        defaultValue = getDefaultValue();

        // 解析索引 UID
        this.index = Parser.parseLong(Arrays.copyOfRange(raw, position, position + 8));

        // 如果字段有索引，加载 B+ 树
        if (index != 0) {
            try {
                bt = BPlusTree.load(index, ((TableManagerImpl) tb.tbm).dm);
            } catch (Exception e) {
                Panic.panic(e);
            }
        }
        return this;
    }

    private void parseSelfConstraint(byte[] constraintRaw) {
        int position = 0;
        // 遍历所有布尔属性，解析每一个字节，并设置相应的字段
        isPrimaryKey = (constraintRaw[position++] == 1);
        isAutoIncrement = (constraintRaw[position++] == 1);
        isNotNull = (constraintRaw[position++] == 1);
        isUnique = (constraintRaw[position] == 1);
    }

    /**
     * 创建字段信息
     *
     * @param tb              表
     * @param xid             事务ID
     * @param fieldName       字段名
     * @param fieldType       字段类型
     * @param indexed         是否为索引
     * @param isAutoIncrement 是否自增
     * @param isNotNull       非空约束
     * @param isUnique        唯一约束
     */
    public static Field createField(Table tb, long xid, String fieldName,
                                    String fieldType, boolean indexed,
                                    boolean isAutoIncrement, boolean isNotNull, boolean isUnique,
                                    boolean isPrimaryKey) throws Exception {
        typeCheck(fieldType);
        Field f = new Field(tb, fieldName, fieldType, 0, isAutoIncrement, isNotNull, isUnique,isPrimaryKey);
        if (indexed) {
            long index = BPlusTree.create(((TableManagerImpl) tb.tbm).dm);
            BPlusTree bt = BPlusTree.load(index, ((TableManagerImpl) tb.tbm).dm);
            f.index = index;
            f.bt = bt;
        }
        f.persistSelf(xid);
        return f;
    }

    private void persistSelf(long xid) throws Exception {
        byte[] constraintRaw = Parser.constraintByte(isPrimaryKey,isAutoIncrement,isNotNull,isUnique);
        byte[] nameRaw = Parser.string2Byte(fieldName);
        byte[] typeRaw = Parser.string2Byte(fieldType);
        byte[] indexRaw = Parser.long2Byte(index);
        this.uid = ((TableManagerImpl) tb.tbm).vm.insert(xid, Bytes.concat(constraintRaw,nameRaw, typeRaw, indexRaw));
    }

    private static void typeCheck(String fieldType) throws Exception {
        if (!Types.SupportedType.isSupported(fieldType)) {
            throw Error.InvalidFieldException;
        }
    }

    public boolean isIndexed() {
        return index != 0;
    }

    public void insert(Object key, long uid) throws Exception {
        long uKey = value2Uid(key);
        bt.insert(uKey, uid);
    }

    public List<Long> search(long left, long right) throws Exception {
        return bt.searchRange(left, right);
    }

    public Object string2Value(String str) {
        if (!Types.SupportedType.isSupported(fieldType)) {
            throw new IllegalArgumentException("Unsupported type: " + fieldType);
        }
        Types.SupportedType type = Types.SupportedType.fromTypeName(fieldType);
        return type.parseValue(str);
    }

    public long value2Uid(Object key) {
        try {
            // 将 fieldType 转换为 SupportedType 枚举
            Types.SupportedType type = Types.SupportedType.valueOf(fieldType.toUpperCase());
            // 使用枚举的 parseValue 方法进行转换
            return type.parseValueUid(key);
        } catch (IllegalArgumentException e) {
            // 如果枚举中不存在该类型，返回 0
            return 0;
        }
    }

    public byte[] value2Raw(Object key) {
        try {
            // 将 fieldType 转换为 SupportedType 枚举
            Types.SupportedType type = Types.SupportedType.valueOf(fieldType.toUpperCase());
            // 使用枚举的 toByteArray 方法进行转换
            return type.parseValueRaw(key);
        } catch (IllegalArgumentException e) {
            // 如果枚举中不存在该类型，返回 null 或者其他默认值
            return null;
        }
    }

    class ParseValueRes {
        Object v;
        int shift;
    }

    public ParseValueRes parserValue(byte[] raw) {
        ParseValueRes res = new ParseValueRes();
        try {
            // 将 fieldType 转换为 SupportedType 枚举
            Types.SupportedType type = Types.SupportedType.fromTypeName(fieldType);

            // 使用枚举的 parseValueFromBytes 方法进行解析
            Object parsedValue = type.parseValueFromBytes(raw);
            if (parsedValue instanceof ParseStringRes) {
                ParseStringRes stringRes = (ParseStringRes) parsedValue;
                res.v = stringRes.str;
                res.shift = stringRes.next;
            } else {
                res.v = parsedValue;
                res.shift = type.getShift(parsedValue);
            }

        } catch (IllegalArgumentException e) {
            // 处理不支持的类型
            e.printStackTrace();
            // 或者设置一个默认值和偏移量
            res.v = null;
            res.shift = 0;
        }
        return res;
    }

    @Override
    public String toString() {
        return new StringBuilder("(")
                .append(fieldName)
                .append(", ")
                .append(fieldType)
                .append(index != 0 ? ", Index" : ", NoIndex")
                .append(")")
                .toString();
    }

    public FieldCalRes calExp(SingleExpression exp) throws Exception {
        Object v = null;
        FieldCalRes res = new FieldCalRes();
        switch (exp.compareOp) {
            case "<":
                res.left = 0;
                v = string2Value(exp.value);
                res.right = value2Uid(v);
                if (res.right > 0) {
                    res.right--;
                }
                break;
            case "=":
                v = string2Value(exp.value);
                res.left = value2Uid(v);
                res.right = res.left;
                break;
            case ">":
                res.right = Long.MAX_VALUE;
                v = string2Value(exp.value);
                res.left = value2Uid(v) + 1;
                break;
        }
        return res;
    }

    public String printConstraint() {
        StringBuilder sb = new StringBuilder();
        if (isPrimaryKey){
            sb.append("PrimaryKey  ");
        }else {
            sb.append(isNotNull ? "NotNull  " : "");
            sb.append(isUnique ? "Unique  " : "");
        }
        sb.append(isAutoIncrement ? "AutoIncr  " : "");
        return sb.toString();
    }
}

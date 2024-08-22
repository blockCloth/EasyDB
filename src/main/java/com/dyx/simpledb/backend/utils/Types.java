package com.dyx.simpledb.backend.utils;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;

public class Types {
    public static long addressToUid(int pgno, short offset) {
        long u0 = (long) pgno;
        long u1 = (long) offset;
        return u0 << 32 | u1;
    }

    public enum SupportedType {
        INT("int") {
            @Override
            public Object parseValue(String str) {
                return Integer.parseInt(str);
            }

            @Override
            public long parseValueUid(Object key) {
                return (long) (int) key;
            }

            @Override
            public byte[] parseValueRaw(Object key) {
                return Parser.int2Byte((int) key);
            }

            @Override
            public String printValue(Object v) {
                return String.valueOf((int) v);
            }

            @Override
            public Object parseValueFromBytes(byte[] raw) {
                return Parser.parseInt(Arrays.copyOf(raw, 4));
            }

            @Override
            public int getShift(Object parsedValue) {
                return 4;
            }

            @Override
            public Object getDefaultValue() {
                return 0;
            }
        },
        VARCHAR("varchar") {
            @Override
            public Object parseValue(String str) {
                return str;
            }

            @Override
            public long parseValueUid(Object key) {
                return Parser.str2Uid((String) key);
            }

            @Override
            public byte[] parseValueRaw(Object key) {
                return Parser.string2Byte((String) key);
            }

            @Override
            public String printValue(Object v) {
                return (String) v;
            }

            @Override
            public Object parseValueFromBytes(byte[] raw) {
                return Parser.parseString(raw);
            }

            @Override
            public int getShift(Object parsedValue) {
                return ((ParseStringRes) parsedValue).next;
            }

            @Override
            public Object getDefaultValue() {
                return "NULL";
            }
        },
        DATETIME("datetime") {
            private final DateTimeFormatter fullFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            @Override
            public Object parseValue(String str) {
                if (str.length() == 10) {
                    // 如果只有年月日，将时间部分补为 "00:00:00"
                    return java.time.LocalDateTime.parse(str + " 00:00:00", fullFormatter);
                } else {
                    return java.time.LocalDateTime.parse(str, fullFormatter);
                }
            }

            @Override
            public long parseValueUid(Object key) {
                return ((java.time.LocalDateTime) key).toEpochSecond(java.time.ZoneOffset.UTC);
            }

            @Override
            public byte[] parseValueRaw(Object key) {
                return Parser.long2Byte(((java.time.LocalDateTime) key).toEpochSecond(java.time.ZoneOffset.UTC));
            }

            @Override
            public String printValue(Object v) {
                // 使用自定义格式化器来格式化LocalDateTime，去除T字符
                return ((java.time.LocalDateTime) v).format(fullFormatter);
            }

            @Override
            public Object parseValueFromBytes(byte[] raw) {
                return java.time.LocalDateTime.ofEpochSecond(Parser.parseLong(Arrays.copyOf(raw, 8)), 0, java.time.ZoneOffset.UTC);
            }

            @Override
            public int getShift(Object parsedValue) {
                return 8;
            }

            @Override
            public Object getDefaultValue() {
                return java.time.LocalDateTime.ofEpochSecond(0, 0, java.time.ZoneOffset.UTC);
            }
        },
        FLOAT("float") {
            @Override
            public Object parseValue(String str) {
                return Float.parseFloat(str);
            }

            @Override
            public long parseValueUid(Object key) {
                return (long) (float) key;
            }

            @Override
            public byte[] parseValueRaw(Object key) {
                return Parser.float2Byte((float) key);
            }

            @Override
            public String printValue(Object v) {
                return String.valueOf((float) v);
            }

            @Override
            public Object parseValueFromBytes(byte[] raw) {
                return Parser.parseFloat(Arrays.copyOf(raw, 4));
            }

            @Override
            public int getShift(Object parsedValue) {
                return 4;
            }

            @Override
            public Object getDefaultValue() {
                return 0.0f;
            }
        },
        LONG("long") {
            @Override
            public Object parseValue(String str) {
                return Long.parseLong(str);
            }

            @Override
            public long parseValueUid(Object key) {
                return (long) key;
            }

            @Override
            public byte[] parseValueRaw(Object key) {
                return Parser.long2Byte((long) key);
            }

            @Override
            public String printValue(Object v) {
                return String.valueOf((long) v);
            }

            @Override
            public Object parseValueFromBytes(byte[] raw) {
                return Parser.parseLong(Arrays.copyOf(raw, 8));
            }

            @Override
            public int getShift(Object parsedValue) {
                return 8;
            }

            @Override
            public Object getDefaultValue() {
                return 0L;
            }
        },
        STRING("string") {
            @Override
            public Object parseValue(String str) {
                return str;
            }

            @Override
            public long parseValueUid(Object key) {
                return Parser.str2Uid((String) key);
            }

            @Override
            public byte[] parseValueRaw(Object key) {
                return Parser.string2Byte((String) key);
            }

            @Override
            public String printValue(Object v) {
                return (String) v;
            }

            @Override
            public Object parseValueFromBytes(byte[] raw) {
                return Parser.parseString(raw);
            }

            @Override
            public int getShift(Object parsedValue) {
                return ((ParseStringRes) parsedValue).next;
            }

            @Override
            public Object getDefaultValue() {
                return "NULL";
            }
        },
        DOUBLE("double") {
            @Override
            public Object parseValue(String str) {
                return Double.parseDouble(str);
            }

            @Override
            public long parseValueUid(Object key) {
                return (long) (double) key;
            }

            @Override
            public byte[] parseValueRaw(Object key) {
                return Parser.double2Byte((double) key);
            }

            @Override
            public String printValue(Object v) {
                return String.valueOf((double) v);
            }

            @Override
            public Object parseValueFromBytes(byte[] raw) {
                return Parser.parseDouble(Arrays.copyOf(raw, 8));
            }

            @Override
            public int getShift(Object parsedValue) {
                return 8;
            }

            @Override
            public Object getDefaultValue() {
                return 0.0;
            }
        };

        private final String typeName;

        SupportedType(String typeName) {
            this.typeName = typeName;
        }

        public String getTypeName() {
            return typeName;
        }

        public static boolean isSupported(String typeName) {
            for (SupportedType type : values()) {
                if (type.getTypeName().equalsIgnoreCase(typeName)) {
                    return true;
                }
            }
            return false;
        }

        public static SupportedType fromTypeName(String typeName) {
            for (SupportedType type : values()) {
                if (type.getTypeName().equalsIgnoreCase(typeName)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unsupported type: " + typeName);
        }

        public abstract Object parseValue(String str);

        public abstract long parseValueUid(Object key);

        public abstract byte[] parseValueRaw(Object key);

        public abstract String printValue(Object v);

        public abstract Object parseValueFromBytes(byte[] raw);

        public abstract int getShift(Object parsedValue);

        public abstract Object getDefaultValue();
    }
}

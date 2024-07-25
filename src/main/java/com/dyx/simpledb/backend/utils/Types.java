package com.dyx.simpledb.backend.utils;

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
                return "";
            }
        },
        DATE("date") {
            @Override
            public Object parseValue(String str) {
                return java.time.LocalDate.parse(str);
            }

            @Override
            public long parseValueUid(Object key) {
                return ((java.time.LocalDate) key).toEpochDay();
            }

            @Override
            public byte[] parseValueRaw(Object key) {
                return Parser.long2Byte(((java.time.LocalDate) key).toEpochDay());
            }

            @Override
            public String printValue(Object v) {
                return ((java.time.LocalDate) v).toString();
            }

            @Override
            public Object parseValueFromBytes(byte[] raw) {
                return java.time.LocalDate.ofEpochDay(Parser.parseLong(Arrays.copyOf(raw, 8)));
            }

            @Override
            public int getShift(Object parsedValue) {
                return 8;
            }

            @Override
            public Object getDefaultValue() {
                return java.time.LocalDate.ofEpochDay(0);
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
                return "";
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

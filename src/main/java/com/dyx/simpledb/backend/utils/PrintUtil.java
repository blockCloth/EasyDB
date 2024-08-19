package com.dyx.simpledb.backend.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PrintUtil {

    /**
     * 生成单列或多列表格并返回其字符串表示
     *
     * @param columnNames 列名数组
     * @param entries     数据列表，每个Map代表一行，key是列名，value是列值
     * @return 格式化的表格字符串
     */
    public static String printTable(String[] columnNames, List<Map<String, Object>> entries) {
        // 计算每列的最大宽度
        Map<String, Integer> columnWidths = calculateColumnWidths(entries, columnNames);

        // 创建StringBuilder用于构建输出
        StringBuilder sb = new StringBuilder();

        // 打印表头分隔符
        printSeparator(sb, columnWidths, columnNames);

        // 打印列名
        printColumnNames(sb, columnWidths, columnNames);

        // 打印表头分隔符
        printSeparator(sb, columnWidths, columnNames);

        // 打印数据行
        printData(sb, columnWidths, columnNames, entries);

        // 打印表尾分隔符
        printSeparator(sb, columnWidths, columnNames);

        // 返回最终结果字符串
        return sb.toString().endsWith("\n")
                ? sb.toString().substring(0, sb.toString().length() - 1)
                : sb.toString();
    }

    private static Map<String, Integer> calculateColumnWidths(List<Map<String, Object>> entries, String[] selectedFields) {
        Map<String, Integer> columnWidths = new HashMap<>();
        for (String col : selectedFields) {
            int maxLength = col.length();
            for (Map<String, Object> entry : entries) {
                String value = entry.get(col) != null ? entry.get(col).toString() : "NULL";
                if (value.length() > maxLength) {
                    maxLength = value.length();
                }
            }
            columnWidths.put(col, maxLength);
        }
        return columnWidths;
    }

    private static void printSeparator(StringBuilder sb, Map<String, Integer> columnWidths, String[] selectedFields) {
        sb.append("+");
        for (String col : selectedFields) {
            int width = columnWidths.get(col);
            sb.append(repeat("-", width)).append("+");
        }
        sb.append("\n");
    }

    private static void printColumnNames(StringBuilder sb, Map<String, Integer> columnWidths, String[] selectedFields) {
        sb.append("|");
        for (String col : selectedFields) {
            int width = columnWidths.get(col);
            sb.append(String.format("%-" + width + "s|", col));
        }
        sb.append("\n");
    }

    private static void printData(StringBuilder sb, Map<String, Integer> columnWidths, String[] selectedFields, List<Map<String, Object>> entries) {
        for (Map<String, Object> entry : entries) {
            sb.append("|");
            for (String col : selectedFields) {
                String value = entry.get(col) != null ? entry.get(col).toString() : "NULL";
                int width = columnWidths.get(col);
                sb.append(String.format("%-" + width + "s|", value));
            }
            sb.append("\n");
        }
    }

    private static String repeat(String str, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) {
            sb.append(str);
        }
        return sb.toString();
    }
}

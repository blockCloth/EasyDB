package com.dyx.simpledb.backend.im;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class UniqueIndex {

    // 正式的唯一索引：已提交的数据
    private Map<String, Set<Object>> index;

    // 临时数据：用于存储每个事务的插入或删除操作
    private Map<Long, Map<String, Set<Object>>> tempIndex;

    // 删除标记：记录哪些数据在事务中被删除（用于回滚恢复）
    private Map<Long, Map<String, Set<Object>>> deleteMark;

    // 构造方法
    public UniqueIndex() {
        index = new HashMap<>();
        tempIndex = new HashMap<>();
        deleteMark = new HashMap<>();
    }

    // 插入操作，确保唯一性
    public void insert(String column, Object data, long xid) throws Exception {
        // 如果该列还没有索引数据，初始化
        index.putIfAbsent(column, new HashSet<>());
        tempIndex.putIfAbsent(xid, new HashMap<>());
        tempIndex.get(xid).putIfAbsent(column, new HashSet<>());

        // 检查该列中的数据是否已经存在于正式索引或事务中的临时数据中
        if (index.get(column).contains(data) || tempIndex.get(xid).get(column).contains(data)) {
            throw new Exception("Unique constraint violation. Data already exists.");
        }

        // 插入事务临时数据
        tempIndex.get(xid).get(column).add(data);
    }

    // 删除操作，支持事务中的删除，并标记待删除的数据
    public void delete(String column, Object data, long xid) throws Exception {
        tempIndex.putIfAbsent(xid, new HashMap<>());
        deleteMark.putIfAbsent(xid, new HashMap<>());
        deleteMark.get(xid).putIfAbsent(column, new HashSet<>());

        // 优先检查正式索引并标记删除
        if (index.containsKey(column) && index.get(column).contains(data)) {
            deleteMark.get(xid).get(column).add(data); // 标记为删除
        } else if (tempIndex.get(xid).containsKey(column) && tempIndex.get(xid).get(column).contains(data)) {
            // 检查事务中的临时数据并删除
            tempIndex.get(xid).get(column).remove(data);
        } else {
            throw new Exception("Data not found in the column for deletion.");
        }
    }


    // 提交操作：提交事务中的修改到正式索引中
    public void commit(long xid) {
        if (!tempIndex.containsKey(xid)) return; // 如果没有修改则直接返回

        // 将临时插入数据合并到正式索引中
        Map<String, Set<Object>> xidData = tempIndex.get(xid);
        for (String column : xidData.keySet()) {
            index.putIfAbsent(column, new HashSet<>());
            index.get(column).addAll(xidData.get(column));
        }

        // 处理删除标记：将被标记为删除的数据从正式索引中真正删除
        if (deleteMark.containsKey(xid)) {
            for (String column : deleteMark.get(xid).keySet()) {
                index.get(column).removeAll(deleteMark.get(xid).get(column)); // 执行正式删除
            }
        }

        // 清除事务临时数据和删除标记
        tempIndex.remove(xid);
        deleteMark.remove(xid);
    }

    // 回滚操作：撤销事务中的操作
    public void rollback(long xid) {
        if (!tempIndex.containsKey(xid)) return;

        // 撤销事务中的插入操作：事务中的数据不合并到正式索引
        tempIndex.remove(xid);

        // 恢复删除标记的数据：撤销删除操作
        if (deleteMark.containsKey(xid)) {
            deleteMark.remove(xid);
        }
    }

    // 查询操作：检查唯一索引中某个列是否包含指定数据
    public boolean search(String column, Object data) {
        // 如果该列不存在，返回 false
        if (!index.containsKey(column)) {
            return false;
        }
        return index.get(column).contains(data); // 检查正式索引是否包含数据
    }

    // 更新操作：先删除旧数据，再插入新数据
    public void update(String column, Object oldData, Object newData, long xid) throws Exception {
        delete(column, oldData, xid);
        insert(column, newData, xid);
    }

    // 删除列索引的方法
    public void remove(String column) {
        index.remove(column);
    }

    // 显示哈希表内容（调试用）
    public void display() {
        System.out.println("Main Index:");
        for (String column : index.keySet()) {
            System.out.println("Column: " + column + ", Values: " + index.get(column));
        }
    }
}

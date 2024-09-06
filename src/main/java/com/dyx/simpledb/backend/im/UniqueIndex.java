package com.dyx.simpledb.backend.im;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class UniqueIndex {

    // 正式的唯一索引：已提交的数据
    private Map<String, Set<Object>> index;

    // 临时数据：用于存储每个事务的插入或删除操作
    private Map<Long, Map<String, Set<Object>>> tempIndex;

    // 删除标记：记录哪些数据在事务中被删除（用于回滚恢复）
    private Map<Long, Map<String, Set<Object>>> deleteMark;
    private Lock indexLock;

    // 构造方法
    public UniqueIndex() {
        index = new HashMap<>();
        tempIndex = new HashMap<>();
        deleteMark = new HashMap<>();
        indexLock = new ReentrantLock();
    }

    // 插入操作，确保唯一性
    public synchronized void insert(String column, Object data, long xid) throws Exception {
        indexLock.lock();
        try {
            index.putIfAbsent(column, new HashSet<>());
            tempIndex.putIfAbsent(xid, new HashMap<>());
            tempIndex.get(xid).putIfAbsent(column, new HashSet<>());

            // 检查正式索引、事务中的临时数据、其他事务的临时数据
            for (Map.Entry<Long, Map<String, Set<Object>>> entry : tempIndex.entrySet()) {
                Long otherXid = entry.getKey();
                if (!otherXid.equals(xid)) {
                    // 检查其他事务中是否有相同的数据
                    if (tempIndex.get(otherXid).getOrDefault(column, new HashSet<>()).contains(data)) {
                        throw new Exception("Unique constraint violation. Data already exists in another active transaction.");
                    }
                }
            }

            // 检查正式索引是否已经有该数据
            if (index.get(column).contains(data) || tempIndex.get(xid).get(column).contains(data)) {
                throw new Exception("Unique constraint violation. Data already exists in this transaction.");
            }

            // 插入事务临时数据
            tempIndex.get(xid).get(column).add(data);
        } finally {
            indexLock.unlock();
        }
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
    // 提交操作：提交事务中的修改到正式索引中
    public synchronized void commit(long xid) throws Exception {
        indexLock.lock();
        try {
            if (!tempIndex.containsKey(xid)) return; // 如果没有修改则直接返回

            // 检查冲突
            for (String column : tempIndex.get(xid).keySet()) {
                for (Object data : tempIndex.get(xid).get(column)) {
                    if (index.get(column).contains(data)) {
                        throw new Exception("Unique constraint violation during commit. Data already exists.");
                    }
                }
            }

            // 合并临时数据到正式索引中
            for (String column : tempIndex.get(xid).keySet()) {
                index.putIfAbsent(column, new HashSet<>());
                index.get(column).addAll(tempIndex.get(xid).get(column));
            }

            // 从正式索引中删除在 deleteMark 中标记为删除的数据
            if (deleteMark.containsKey(xid)) {
                for (String column : deleteMark.get(xid).keySet()) {
                    index.get(column).removeAll(deleteMark.get(xid).get(column)); // 执行正式删除
                }
            }

            // 清除事务临时数据和删除标记
            tempIndex.remove(xid);
            deleteMark.remove(xid);

        } finally {
            indexLock.unlock();
        }
    }

    // 回滚操作：撤销事务中的操作
    public void rollback(long xid) {
        indexLock.lock();
        try {
            if (!tempIndex.containsKey(xid)) return;

            // 撤销插入操作：事务中的数据不合并到正式索引
            tempIndex.remove(xid);

            // 恢复删除的数据：撤销事务中的删除标记
            if (deleteMark.containsKey(xid)) {
                for (String column : deleteMark.get(xid).keySet()) {
                    for (Object data : deleteMark.get(xid).get(column)) {
                        index.putIfAbsent(column, new HashSet<>());
                        index.get(column).add(data); // 恢复删除的数据
                    }
                }
            }

            // 清除删除标记
            deleteMark.remove(xid);

        } finally {
            indexLock.unlock();
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

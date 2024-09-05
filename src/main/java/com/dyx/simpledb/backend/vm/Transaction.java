package com.dyx.simpledb.backend.vm;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.dyx.simpledb.backend.tbm.Table;
import com.dyx.simpledb.backend.tm.TransactionManagerImpl;

// vm对一个事务的抽象
public class Transaction {
    public long xid;
    public IsolationLevel isolationLevel;
    public Map<Long, Boolean> snapshot;
    public Exception err;
    public boolean autoAborted;
    public long startTime; // 添加开始时间属性
    // 新增字段：记录事务中修改的表
    private Set<Table> modifiedTables = new HashSet<>();

    // 添加修改表的方法
    public void addModifiedTable(Table table) {
        modifiedTables.add(table);
    }

    // 获取被修改的表
    public Set<Table> getModifiedTables() {
        return modifiedTables;
    }

    public static Transaction newTransaction(long xid, IsolationLevel isolationLevel, Map<Long, Transaction> active) {
        Transaction t = new Transaction();
        t.xid = xid;
        t.isolationLevel = isolationLevel;
        t.startTime = System.currentTimeMillis();
        if(isolationLevel != IsolationLevel.READ_COMMITTED && isolationLevel != IsolationLevel.READ_UNCOMMITTED) {
            t.snapshot = new HashMap<>();
            for(Long x : active.keySet()) {
                t.snapshot.put(x, true);
            }
        }
        return t;
    }

    public boolean isInSnapshot(long xid) {
        if(xid == TransactionManagerImpl.SUPER_XID) {
            return false;
        }
        return snapshot.containsKey(xid);
    }
}

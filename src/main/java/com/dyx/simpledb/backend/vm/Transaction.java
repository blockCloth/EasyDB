package com.dyx.simpledb.backend.vm;

import java.util.HashMap;
import java.util.Map;

import com.dyx.simpledb.backend.tm.TransactionManagerImpl;

// vm对一个事务的抽象
public class Transaction {
    public long xid;
    public IsolationLevel isolationLevel;
    public Map<Long, Boolean> snapshot;
    public Exception err;
    public boolean autoAborted;
    public long startTime; // 添加开始时间属性

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

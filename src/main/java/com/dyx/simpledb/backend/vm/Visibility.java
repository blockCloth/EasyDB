package com.dyx.simpledb.backend.vm;

import com.dyx.simpledb.backend.tm.TransactionManager;

public class Visibility {

    public static boolean isVersionSkip(TransactionManager tm, Transaction t, Entry e) {
        long xmax = e.getXmax();

        if (t.isolationLevel == IsolationLevel.READ_UNCOMMITTED){
            return false;
        }else if (t.isolationLevel == IsolationLevel.READ_COMMITTED){
            return tm.isCommitted(xmax);
        }else if (t.isolationLevel == IsolationLevel.REPEATABLE_READ){
            return tm.isCommitted(xmax) && (xmax > t.xid || t.isInSnapshot(xmax));
        }else if (t.isolationLevel == IsolationLevel.SERIALIZABLE){
            return tm.isCommitted(xmax) && (xmax > t.xid || t.isInSnapshot(xmax));
        }else {
            throw new IllegalArgumentException("Unknown isolation level: " + t.isolationLevel);
        }
    }

    public static boolean isVisible(TransactionManager tm, Transaction t, Entry e) {
        switch (t.isolationLevel) {
            case READ_UNCOMMITTED:
                return readUnCommitted(tm, t, e);
            case READ_COMMITTED:
                return readCommitted(tm, t, e);
            case REPEATABLE_READ:
                return repeatableRead(tm, t, e);
            case SERIALIZABLE:
                return serializable(tm, t, e);
            default:
                throw new IllegalArgumentException("Unknown isolation level: " + t.isolationLevel);
        }
    }


    // 读未提交，允许所有事务读取数据
    private static boolean readUnCommitted(TransactionManager tm, Transaction t, Entry e) {
        return true;
    }

    private static boolean readCommitted(TransactionManager tm, Transaction t, Entry e) {
        long xid = t.xid;
        long xmin = e.getXmin();
        long xmax = e.getXmax();
        if (xmin == xid && xmax == 0) return true;

        if (tm.isCommitted(xmin)) {
            if (xmax == 0) return true;
            if (xmax != xid) {
                if (!tm.isCommitted(xmax)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean repeatableRead(TransactionManager tm, Transaction t, Entry e) {
        long xid = t.xid;
        long xmin = e.getXmin();
        long xmax = e.getXmax();
        if (xmin == xid && xmax == 0) return true;

        if (tm.isCommitted(xmin) && xmin < xid && !t.isInSnapshot(xmin)) {
            if (xmax == 0) return true;
            if (xmax != xid) {
                if (!tm.isCommitted(xmax) || xmax > xid || t.isInSnapshot(xmax)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean serializable(TransactionManager tm, Transaction t, Entry e) {
        long xid = t.xid;
        long xmin = e.getXmin();
        long xmax = e.getXmax();

        // 当前事务创建且尚未删除
        if (xmin == xid && xmax == 0) return true;

        // 由已提交事务创建且在当前事务之前提交
        if (tm.isCommitted(xmin) && xmin < xid && !t.isInSnapshot(xmin)) {
            // 尚未删除
            if (xmax == 0) return true;
            // 由其他事务删除，但该删除操作尚未提交，或在当前事务之后开始，或在当前事务开始时仍未提交
            if (xmax != xid) {
                if (!tm.isCommitted(xmax) || xmax > xid || t.isInSnapshot(xmax)) {
                    return true;
                }
            }
        }
        return false;
    }
}

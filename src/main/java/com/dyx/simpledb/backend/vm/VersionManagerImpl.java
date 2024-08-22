package com.dyx.simpledb.backend.vm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.dyx.simpledb.backend.common.AbstractCache;
import com.dyx.simpledb.backend.dm.DataManager;
import com.dyx.simpledb.backend.tm.TransactionManager;
import com.dyx.simpledb.backend.tm.TransactionManagerImpl;
import com.dyx.simpledb.backend.utils.Panic;
import com.dyx.simpledb.common.Error;

public class VersionManagerImpl extends AbstractCache<Entry> implements VersionManager {

    TransactionManager tm;
    DataManager dm;
    Map<Long, Transaction> activeTransaction;
    Lock lock;
    LockTable lt;
    private final Lock globalLock = new ReentrantLock();


    public VersionManagerImpl(TransactionManager tm, DataManager dm) {
        super(0);
        this.tm = tm;
        this.dm = dm;
        this.activeTransaction = new ConcurrentHashMap<>();
        activeTransaction.put(TransactionManagerImpl.SUPER_XID, Transaction.newTransaction(TransactionManagerImpl.SUPER_XID, IsolationLevel.READ_COMMITTED, null));
        this.lock = new ReentrantLock();
        this.lt = new LockTable();
    }

    @Override
    public byte[] read(long xid, long uid) throws Exception {
        lock.lock();
        Transaction t = activeTransaction.get(xid);
        lock.unlock();

        if (t.err != null) {
            throw t.err;
        }

        Entry entry = null;
        try {
            entry = super.get(uid);
        } catch (Exception e) {
            if (e == Error.NullEntryException) {
                return null;
            } else {
                throw e;
            }
        }
        try {
            if (Visibility.isVisible(tm, t, entry)) {
                return entry.data();
            } else {
                return null;
            }
        } finally {
            entry.release();
        }
    }

    @Override
    public long insert(long xid, byte[] data) throws Exception {
        lock.lock();
        Transaction t = activeTransaction.get(xid);
        lock.unlock();

        if (t.err != null) {
            throw t.err;
        }

        byte[] raw = Entry.wrapEntryRaw(xid, data);
        return dm.insert(xid, raw);
    }

    @Override
    public void physicalDelete(long xid, Long uid) throws Exception {
        lock.lock();
        Transaction t = activeTransaction.get(xid);
        lock.unlock();

        if (t.err != null) {
            throw t.err;
        }
        dm.physicalDelete(uid);

        super.release(uid);
    }

    @Override
    public boolean delete(long xid, long uid) throws Exception {
        lock.lock();
        Transaction t = activeTransaction.get(xid);
        lock.unlock();

        if (t.err != null) {
            throw t.err;
        }
        Entry entry = null;
        try {
            entry = super.get(uid);
        } catch (Exception e) {
            if (e == Error.NullEntryException) {
                return false;
            } else {
                throw e;
            }
        }
        try {
            if (!Visibility.isVisible(tm, t, entry)) {
                return false;
            }
            Lock l = null;
            try {
                l = lt.add(xid, uid);
            } catch (Exception e) {
                t.err = Error.ConcurrentUpdateException;
                internAbort(xid, true);
                t.autoAborted = true;
                throw t.err;
            }
            if (l != null) {
                l.lock();
                l.unlock();
            }

            if (entry.getXmax() == xid) {
                return false;
            }

            if (Visibility.isVersionSkip(tm, t, entry)) {
                t.err = Error.ConcurrentUpdateException;
                internAbort(xid, true);
                t.autoAborted = true;
                throw t.err;
            }

            entry.setXmax(xid);
            return true;

        } finally {
            entry.release();
        }
    }

    @Override
    public long begin(IsolationLevel isolationLevel) {
        globalLock.lock(); // 获取全局锁
        lock.lock();
        try {
            if (isolationLevel != IsolationLevel.SERIALIZABLE) {
                globalLock.unlock(); // 解除非全局锁
            }
            long xid = tm.begin();
            Transaction t = Transaction.newTransaction(
                    xid, isolationLevel == null ? IsolationLevel.READ_COMMITTED : isolationLevel, activeTransaction);
            activeTransaction.put(xid, t);

            return xid;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void commit(long xid) throws Exception {
        lock.lock();
        Transaction t = activeTransaction.get(xid);
        lock.unlock();

        try {
            if (t.err != null) {
                throw t.err;
            }
        } catch (NullPointerException n) {
            System.out.println(xid);
            System.out.println(activeTransaction.keySet());
            Panic.panic(n);
        }

        lock.lock();
        activeTransaction.remove(xid);
        lock.unlock();

        lt.remove(xid);
        tm.commit(xid);

        if (t.isolationLevel == IsolationLevel.SERIALIZABLE && globalLock.tryLock()) {
            globalLock.unlock();  // 释放全局锁
        }
    }

    @Override
    public void abort(long xid) {
        internAbort(xid, false);
    }

    private void internAbort(long xid, boolean autoAborted) {
        lock.lock();
        Transaction t = activeTransaction.get(xid);
        if (!autoAborted) {
            activeTransaction.remove(xid);
        }
        lock.unlock();

        if (t.isolationLevel == IsolationLevel.SERIALIZABLE) {
            globalLock.unlock();  // 释放全局锁
        }

        if (t.autoAborted) return;
        lt.remove(xid);
        tm.abort(xid);
    }

    public void releaseEntry(Entry entry) {
        super.release(entry.getUid());
    }

    @Override
    protected Entry getForCache(long uid) throws Exception {
        Entry entry = Entry.loadEntry(this, uid);
        if (entry == null) {
            throw Error.NullEntryException;
        }
        return entry;
    }

    @Override
    protected void releaseForCache(Entry entry) {
        entry.remove();
    }

}

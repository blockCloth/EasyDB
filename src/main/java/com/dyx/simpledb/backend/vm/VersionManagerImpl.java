package com.dyx.simpledb.backend.vm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final Lock globalLock;
    private static final int CHECK_INTERVAL_MS = 600; // 检查间隔（0.6秒）
    private static final int TIMEOUT_THRESHOLD_MS = 10000; // 超时时间阈值（10秒）

    public VersionManagerImpl(TransactionManager tm, DataManager dm) {
        super(0);
        this.tm = tm;
        this.dm = dm;
        this.activeTransaction = new HashMap<>();
        activeTransaction.put(TransactionManagerImpl.SUPER_XID, Transaction.newTransaction(TransactionManagerImpl.SUPER_XID, IsolationLevel.READ_COMMITTED, null));
        this.lock = new ReentrantLock();
        this.lt = new LockTable();
        this.globalLock = new ReentrantLock();
        // 启动超时和死锁检查线程
        startTimeoutDeadlockChecker();
    }

    // 启动后台线程来定期检查事务超时和死锁
    private void startTimeoutDeadlockChecker() {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(CHECK_INTERVAL_MS);
                    checkForTimeouts();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();
    }

    // 检查超时和死锁
    private void checkForTimeouts() {
        long now = System.currentTimeMillis();
        List<Long> toRollback = new ArrayList<>();

        lock.lock();
        try {
            for (Map.Entry<Long, Transaction> entry : activeTransaction.entrySet()) {
                long xid = entry.getKey();
                Transaction tx = entry.getValue();

                // 超时检测逻辑
                if (now - tx.startTime > TIMEOUT_THRESHOLD_MS) {
                    toRollback.add(xid); // 将需要回滚的事务ID加入列表
                }
            }
        } finally {
            lock.unlock();
        }

        for (Long xid : toRollback) {
            abortTransaction(xid);
        }
    }

    // 回滚事务
    private void abortTransaction(long xid) {
        lock.lock();
        try {
            Transaction tx = activeTransaction.get(xid);
            if (tx != null) {
                lt.remove(xid); // 从锁表中移除相关锁
                tm.abort(xid);  // 通过事务管理器回滚事务
                activeTransaction.remove(xid); // 从活跃事务列表中移除
                System.out.println("Transaction " + xid + " has been automatically rolled back due to timeout or deadlock.");
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public byte[] read(long xid, long uid) throws Exception {
        lock.lock();
        Transaction t = activeTransaction.get(xid);
        lock.unlock();

        if(t.err != null) {
            throw t.err;
        }

        Entry entry = null;
        try {
            entry = super.get(uid);
        } catch(Exception e) {
            if(e == Error.NullEntryException) {
                return null;
            } else {
                throw e;
            }
        }
        try {
            if(Visibility.isVisible(tm, t, entry)) {
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

        if(t.err != null) {
            throw t.err;
        }

        byte[] raw = Entry.wrapEntryRaw(xid, data);
        return dm.insert(xid, raw);
    }

    @Override
    public boolean delete(long xid, long uid) throws Exception {
        lock.lock();
        Transaction t = activeTransaction.get(xid);
        lock.unlock();

        if(t.err != null) {
            throw t.err;
        }
        Entry entry = null;
        try {
            entry = super.get(uid);
        } catch(Exception e) {
            if(e == Error.NullEntryException) {
                return false;
            } else {
                throw e;
            }
        }
        try {
            if(!Visibility.isVisible(tm, t, entry)) {
                return false;
            }
            Lock l = null;
            try {
                l = lt.add(xid, uid);
            } catch(Exception e) {
                t.err = Error.ConcurrentUpdateException;
                internAbort(xid, true);
                t.autoAborted = true;
                throw t.err;
            }
            if(l != null) {
                l.lock();
                l.unlock();
            }

            if(entry.getXmax() == xid) {
                return false;
            }

            if(Visibility.isVersionSkip(tm, t, entry)) {
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
        lock.lock();
        try {
            long xid = tm.begin();
            Transaction t = Transaction.newTransaction(xid, isolationLevel, activeTransaction);
            activeTransaction.put(xid, t);

            if (isolationLevel == IsolationLevel.SERIALIZABLE){
                globalLock.lock();
            }
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
            if(t.err != null) {
                throw t.err;
            }
        } catch(NullPointerException n) {
            System.out.println(xid);
            System.out.println(activeTransaction.keySet());
            Panic.panic(n);
        }

        lock.lock();
        activeTransaction.remove(xid);
        lock.unlock();

        if (t.isolationLevel == IsolationLevel.SERIALIZABLE){
            globalLock.unlock();
        }

        lt.remove(xid);
        tm.commit(xid);
    }

    @Override
    public void abort(long xid) {
        internAbort(xid, false);
    }

    private void internAbort(long xid, boolean autoAborted) {
        lock.lock();
        Transaction t = activeTransaction.get(xid);
        if(!autoAborted) {
            activeTransaction.remove(xid);
        }
        lock.unlock();

        if (t.isolationLevel == IsolationLevel.SERIALIZABLE){
            globalLock.unlock();
        }
        if(t.autoAborted) return;
        lt.remove(xid);
        tm.abort(xid);
    }

    public void releaseEntry(Entry entry) {
        super.release(entry.getUid());
    }

    @Override
    protected Entry getForCache(long uid) throws Exception {
        Entry entry = Entry.loadEntry(this, uid);
        if(entry == null) {
            throw Error.NullEntryException;
        }
        return entry;
    }

    @Override
    protected void releaseForCache(Entry entry) {
        entry.remove();
    }

}

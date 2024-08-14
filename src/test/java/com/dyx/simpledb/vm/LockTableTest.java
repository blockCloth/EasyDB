package com.dyx.simpledb.vm;

import com.dyx.simpledb.backend.vm.LockTable;
import lombok.extern.log4j.Log4j2;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.locks.Lock;

@Log4j2
public class LockTableTest {
    private LockTable lockTable;

    @Before
    public void setUp() {
        lockTable = new LockTable();
    }

    @Test
    public void testDeadlockAndTimeout() {
        try {
            // 启动两个线程以创建死锁
            new Thread(() -> {
                try {
                    long xid1 = 1L;
                    long resourceA = 100L;
                    long resourceB = 200L;

                    log.info("Transaction " + xid1 + " trying to acquire resource A.");
                    Lock lockA = lockTable.add(xid1, resourceA);

                    if (lockA != null) {
                        log.info("Transaction " + xid1 + " acquired resource A.");
                    }

                    // Simulate some work
                    Thread.sleep(1000);

                    log.info("Transaction " + xid1 + " trying to acquire resource B.");
                    Lock lockB = lockTable.add(xid1, resourceB);

                    if (lockB != null) {
                        log.info("Transaction " + xid1 + " acquired resource B.");
                    } else {
                        log.info("Transaction " + xid1 + " is waiting for resource B.");
                    }

                    // Simulate some more work
                    Thread.sleep(10000);

                    lockTable.remove(xid1);
                    log.info("Transaction " + xid1 + " completed.");

                } catch (Exception e) {
                    log.error("Error in transaction", e);
                }
            }).start();

            new Thread(() -> {
                try {
                    long xid2 = 2L;
                    long resourceA = 100L;
                    long resourceB = 200L;

                    log.info("Transaction " + xid2 + " trying to acquire resource B.");
                    Lock lockB = lockTable.add(xid2, resourceB);

                    if (lockB != null) {
                        log.info("Transaction " + xid2 + " acquired resource B.");
                    }

                    // Simulate some work
                    Thread.sleep(1000);

                    log.info("Transaction " + xid2 + " trying to acquire resource A.");
                    Lock lockA = lockTable.add(xid2, resourceA);

                    if (lockA != null) {
                        log.info("Transaction " + xid2 + " acquired resource A.");
                    } else {
                        log.info("Transaction " + xid2 + " is waiting for resource A.");
                    }

                    // Simulate some more work
                    Thread.sleep(10000);

                    lockTable.remove(xid2);
                    log.info("Transaction " + xid2 + " completed.");

                } catch (Exception e) {
                    log.error("Error in transaction", e);
                }
            }).start();

            // 等待足够的时间以确保死锁发生
            Thread.sleep(20000);

        } catch (Exception e) {
            log.error("Error in deadlock and timeout test", e);
        }
    }
}

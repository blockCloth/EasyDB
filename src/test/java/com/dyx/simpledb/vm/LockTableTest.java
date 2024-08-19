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

    @Test
    public void checkTimeout(){
        LockTable lockTable = new LockTable();

        // 创建几个模拟的事务ID和资源ID
        long xid1 = 1L;
        long xid2 = 2L;
        long xid3 = 3L;
        long uid1 = 100L;
        long uid2 = 200L;

        // 测试1：事务1获取资源1
        try {
            Lock lock1 = lockTable.add(xid1, uid1);
            System.out.println("Transaction " + xid1 + " acquired resource " + uid1);
            if (lock1 != null) {
                lock1.unlock();
            }
        } catch (Exception e) {
            System.out.println("Transaction " + xid1 + " failed to acquire resource " + uid1);
        }

        // 测试2：事务2尝试获取已经被占用的资源1，这将导致它进入等待状态
        try {
            Lock lock2 = lockTable.add(xid2, uid1);
            System.out.println("Transaction " + xid2 + " is waiting to acquire resource " + uid1);
        } catch (Exception e) {
            System.out.println("Transaction " + xid2 + " failed to acquire resource " + uid1);
        }

        // 测试3：事务3获取资源2，不冲突
        try {
            Lock lock3 = lockTable.add(xid3, uid2);
            System.out.println("Transaction " + xid3 + " acquired resource " + uid2);
            if (lock3 != null) {
                lock3.unlock();
            }
        } catch (Exception e) {
            System.out.println("Transaction " + xid3 + " failed to acquire resource " + uid2);
        }

        // 模拟事务2超时的情景，等待时间超过设定的阈值
        try {
            Thread.sleep(35000); // 35秒后，事务2应该被回滚，因为它等待超过了30秒的阈值
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // 测试4：检查事务2是否超时并被回滚
        try {
            lockTable.remove(xid2);
            System.out.println("Transaction " + xid2 + " has been successfully rolled back due to timeout.");
        } catch (Exception e) {
            System.out.println("Failed to roll back transaction " + xid2);
        }

        // 测试5：事务1释放资源1后，事务2重新获取资源1
        try {
            lockTable.remove(xid1); // 释放资源1
            System.out.println("Transaction " + xid1 + " released resource " + uid1);

            // 事务2应该在此时获取到资源1
            Lock lock2 = lockTable.add(xid2, uid1);
            if (lock2 != null) {
                lock2.unlock();
                System.out.println("Transaction " + xid2 + " acquired resource " + uid1 + " after xid1 released it.");
            }
        } catch (Exception e) {
            System.out.println("Transaction " + xid2 + " failed to acquire resource " + uid1);
        }
    }
}

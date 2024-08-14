package com.dyx.simpledb.vm;

import com.dyx.simpledb.backend.dm.DataManager;
import com.dyx.simpledb.backend.parser.statement.Create;
import com.dyx.simpledb.backend.tm.TransactionManager;
import com.dyx.simpledb.backend.tm.TransactionManagerImpl;
import com.dyx.simpledb.backend.vm.IsolationLevel;
import com.dyx.simpledb.backend.vm.VersionManager;
import com.dyx.simpledb.backend.vm.VersionManagerImpl;
import org.junit.Test;

public class TransactionTimeoutTest {

    @Test
    public void timeoutTest() {
        // 初始化事务管理器和数据管理器
        TransactionManager tm = TransactionManager.open("D:\\JavaCount\\mydb\\windows\\127.0.0.1");
        // 打开数据管理器，传入路径、内存大小和事务管理器
        DataManager dm = DataManager.open("D:\\JavaCount\\mydb\\windows\\127.0.0.1", (1 << 20) * 64, tm);
        // 创建版本管理器，传入事务管理器和数据管理器
        VersionManager vm = new VersionManagerImpl(tm, dm);

        // 开启一个长时间运行的事务
        long xid1 = vm.begin(IsolationLevel.READ_COMMITTED);

        // 在另一个线程中执行此事务，模拟长时间操作
        new Thread(() -> {
            try {
                // 模拟长时间操作
                System.out.println("Transaction " + xid1 + " started and sleeping...");
                Thread.sleep(35000); // 超过30秒的超时时间
                System.out.println("Transaction " + xid1 + " completed.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        // 在主线程中启动一个短时间事务以测试超时效果
        long xid2 = vm.begin(IsolationLevel.READ_COMMITTED);

        try {
            // 执行一些操作
            System.out.println("Transaction " + xid2 + " started.");
            vm.insert(xid2, "insert into user(id) values (10)".getBytes());
            vm.commit(xid2);
            System.out.println("Transaction " + xid2 + " committed.");

            // 等待足够的时间让第一个事务超时
            Thread.sleep(40000);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 检查第一个事务的状态
        if (tm.isAborted(xid1)) {
            System.out.println("Transaction " + xid1 + " was rolled back due to timeout.");
        } else {
            System.out.println("Transaction " + xid1 + " completed without timeout.");
        }
    }
}

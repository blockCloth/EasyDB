package com.dyx.simpledb.im;

import com.dyx.simpledb.backend.im.UniqueIndex;
import org.junit.Test;

/**
 * @User Administrator
 * @CreateTime 2024/9/5 15:28
 * @className com.dyx.simpledb.im.UniqueIndexTest
 */
public class UniqueIndexTest {

    @Test
    public void testInsertInTransaction() throws Exception {
        long xid = 1L;  // 模拟事务ID
        UniqueIndex index = new UniqueIndex();

        // 事务中插入数据
        index.insert("name", "Alice", xid);

        // 事务未提交时，数据不应出现在正式索引中
        assert !index.search("name", "Alice");

        // 提交事务
        index.commit(xid);

        // 提交后，数据应出现在正式索引中
        assert index.search("name", "Alice");
    }

    @Test
    public void testDeleteInTransaction() throws Exception {
        long xid = 2L;  // 模拟事务ID
        UniqueIndex index = new UniqueIndex();

        // 插入数据
        index.insert("name", "Bob", xid);
        index.commit(xid);

        // 在事务中删除数据
        long xid2 = 3L;  // 新的事务
        index.delete("name", "Bob", xid2);

        // 事务未提交时，数据应仍然存在
        assert index.search("name", "Bob");

        // 回滚事务，数据应恢复
        index.rollback(xid2);
        assert index.search("name", "Bob");

        // 再次删除并提交事务
        index.delete("name", "Bob", xid2);
        index.commit(xid2);

        // 提交后，数据应被真正删除
        assert !index.search("name", "Bob");
    }

    @Test
    public void testUpdateInTransaction() throws Exception {
        long xid = 4L;  // 模拟事务ID
        UniqueIndex index = new UniqueIndex();

        // 插入数据
        index.insert("name", "Charlie", xid);
        index.commit(xid);

        // 在事务中更新数据
        long xid2 = 5L;  // 新的事务
        index.update("name", "Charlie", "David", xid2);

        // 事务未提交时，旧数据应存在，新数据暂未生效
        assert index.search("name", "Charlie");
        assert !index.search("name", "David");

        // 回滚事务，数据应恢复
        index.rollback(xid2);
        assert index.search("name", "Charlie");
        assert !index.search("name", "David");

        // 再次更新并提交事务
        index.update("name", "Charlie", "David", xid2);
        index.commit(xid2);

        // 提交后，旧数据应被删除，新数据应存在
        assert !index.search("name", "Charlie");
        assert index.search("name", "David");
    }

    @Test
    public void testRollback() throws Exception {
        UniqueIndex index = new UniqueIndex();

        // 插入数据并提交事务
        long xid = 6L;  // 模拟事务ID
        index.insert("name", "Eve", xid);
        index.commit(xid);  // 提交事务

        // 事务中删除并回滚
        long xid2 = 7L;  // 新的事务
        index.delete("name", "Eve", xid2);
        index.rollback(xid2);  // 回滚删除操作

        // 回滚后，数据应恢复
        assert index.search("name", "Eve");  // Eve 应该存在

        // 事务中更新并回滚
        index.update("name", "Eve", "Frank", xid2);
        index.rollback(xid2);  // 回滚更新操作

        // 回滚后，旧数据应恢复
        assert index.search("name", "Eve");  // Eve 应该恢复
        assert !index.search("name", "Frank");  // Frank 不应该存在
    }

    @Test
    public void testCommit() throws Exception {
        long xid = 8L;  // 模拟事务ID
        UniqueIndex index = new UniqueIndex();

        // 插入数据
        index.insert("name", "Grace", xid);
        index.commit(xid);

        // 提交后，数据应存在于唯一索引中
        assert index.search("name", "Grace");

        // 事务中更新并提交
        long xid2 = 9L;  // 新的事务
        index.update("name", "Grace", "Hank", xid2);
        index.commit(xid2);

        // 提交后，数据应更新
        assert !index.search("name", "Grace");
        assert index.search("name", "Hank");

        // 事务中删除并提交
        index.delete("name", "Hank", xid2);
        index.commit(xid2);

        // 提交后，数据应被删除
        assert !index.search("name", "Hank");
    }

}

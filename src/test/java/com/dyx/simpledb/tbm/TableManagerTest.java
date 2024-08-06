package com.dyx.simpledb.tbm;

import com.dyx.simpledb.backend.server.Executor;
import org.junit.Test;

/**
 * @User Administrator
 * @CreateTime 2024/7/25 21:27
 * @className com.dyx.simpledb.tbm.TableManagerTest
 */
public class TableManagerTest {
    @Test
    public void createTable(){
        String sql = "create table stu(id int,name varchar,index idx_name (name));";

        // Executor executor = new Executor();
    }

    @Test
    public void init(){

    }
}

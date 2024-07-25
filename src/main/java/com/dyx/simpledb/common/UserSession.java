package com.dyx.simpledb.common;

import com.dyx.simpledb.backend.dm.DataManager;
import com.dyx.simpledb.backend.server.Executor;
import com.dyx.simpledb.backend.tbm.TableManager;
import com.dyx.simpledb.backend.tm.TransactionManager;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class UserSession {
    private String userId;
    private long startTime;
    private long lastAccessedTime;
    private TableManager tableManager;
    private TransactionManager transactionManager;
    private DataManager dataManager;
    private Executor executor;

    public UserSession(String userId, long startTime) {
        this.userId = userId;
        this.startTime = startTime;
        this.lastAccessedTime = startTime; // 初始化最后访问时间
    }

    public void updateLastAccessedTime() {
        this.lastAccessedTime = System.currentTimeMillis();
    }

    public void close() {
        if (dataManager != null) {
            dataManager.close();
        }
        if (transactionManager != null) {
            transactionManager.close();
        }
    }
}

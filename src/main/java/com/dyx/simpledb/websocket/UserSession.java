package com.dyx.simpledb.websocket;

import com.dyx.simpledb.backend.dm.DataManager;
import com.dyx.simpledb.backend.server.Executor;
import com.dyx.simpledb.backend.tbm.TableManager;
import com.dyx.simpledb.backend.tm.TransactionManager;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Setter
@Getter
public class UserSession {
    private String userId;
    private long startTime;
    private long lastAccessedTime;
    private TableManager tableManager;
    private TransactionManager transactionManager;
    private DataManager dataManager;
    private Map<String,Executor> executorMap;
    private final Set<String> sessionIds = ConcurrentHashMap.newKeySet();

    public UserSession(String userId, long startTime) {
        this.userId = userId;
        this.startTime = startTime;
        this.lastAccessedTime = startTime; // 初始化最后访问时间
        executorMap = new HashMap<>();
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

    public Executor getExecutor(String sessionId) {
        return executorMap.get(sessionId);
    }

    public Executor removeExecutor(String sessionId) {
        return executorMap.remove(sessionId);
    }

    public void setExecutor(String sessionId, Executor executor) {
        executorMap.put(sessionId,executor);
    }

    public void addSession(String sessionId) {
        sessionIds.add(sessionId);
    }

    public void removeSession(String sessionId) {
        sessionIds.remove(sessionId);
    }

    public boolean hasActiveSessions() {
        return !sessionIds.isEmpty();
    }
}

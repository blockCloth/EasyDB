package com.dyx.simpledb.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class UserManager {
    private static final int MAX_USERS = 20;
    private static final int SESSION_EXPIRY_CHECK_INTERVAL = 100 * 60 * 1000; // 10 minutes in milliseconds
    private static final int MAX_SESSION_DURATION = 2 * 60 * 60 * 1000; // 2 hours in milliseconds

    private ConcurrentHashMap<String, UserSession> activeUsers = new ConcurrentHashMap<>();
    private AtomicInteger userCount = new AtomicInteger(0);

    @Value("${custom.db.path}")
    private String dbPath;

    public boolean canInit(String userId) {
        if (userCount.get() >= MAX_USERS) {
            return false;
        }
        activeUsers.put(userId, new UserSession(userId, System.currentTimeMillis()));
        userCount.incrementAndGet();
        return true;
    }

    public UserSession getUserSession(String userId) {
        return activeUsers.get(userId);
    }

    public void addUserSession(String userId, UserSession userSession) {
        activeUsers.put(userId, userSession);
        userCount.incrementAndGet();
    }

    public void removeUserSession(String userId) {
        // 先关闭数据库文件
        UserSession session = activeUsers.get(userId);
        if (session != null) {
            session.close();
        }
        activeUsers.remove(userId);
        userCount.decrementAndGet();
    }

    @Scheduled(fixedRate = 60000) // 每分钟执行一次
    public void checkSessions() {
        long currentTime = System.currentTimeMillis();
        activeUsers.forEach((userId, session) -> {
            if (currentTime - session.getLastAccessedTime() >= SESSION_EXPIRY_CHECK_INTERVAL ||
                    currentTime - session.getStartTime() >= MAX_SESSION_DURATION) {
                removeUserSession(userId);
                destroyDatabase(userId);
            }
        });
    }

    public void destroyDatabase(String userId) {
        // 删除数据库目录和文件的逻辑
        String directoryPath = dbPath + File.separator + userId;
        Path directory = Paths.get(directoryPath);
        try {
            deleteDirectory(directory);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void deleteDirectory(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc == null) {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                } else {
                    throw exc;
                }
            }
        });
    }
}

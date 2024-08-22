package com.dyx.simpledb.websocket;

import cn.hutool.json.JSONObject;
import com.dyx.simpledb.backend.dm.DataManager;
import com.dyx.simpledb.backend.server.Executor;
import com.dyx.simpledb.backend.tbm.TableManager;
import com.dyx.simpledb.backend.tm.TransactionManager;
import com.dyx.simpledb.backend.vm.VersionManager;
import com.dyx.simpledb.backend.vm.VersionManagerImpl;
import com.dyx.simpledb.common.UserManager;
import com.dyx.simpledb.common.UserSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.dyx.simpledb.backend.Launcher.DEFALUT_MEM;

@Component
public class TerminalWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private UserManager userManager;

    @Value("${custom.db.path}")
    private String dbPath;
    private final Map<String, ExecutorService> sessionExecutorMap = new ConcurrentHashMap<>();

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        String payload = message.getPayload();
        JSONObject jsonObject = new JSONObject(payload);
        String sql = jsonObject.getStr("command");
        String clientIp = (String) session.getAttributes().get("clientIp");
        String sessionId = session.getId(); // 每个 WebSocket 会话的唯一标识符

        // 获取当前 WebSocket 连接的专属线程池
        ExecutorService executorService = sessionExecutorMap.computeIfAbsent(sessionId, key -> Executors.newSingleThreadExecutor());

        executorService.submit(() -> {
            try {
                if ("init".equalsIgnoreCase(sql.trim())) {
                    handleInitCommand(session, clientIp, sessionId);
                } else {
                    UserSession userSession = userManager.getUserSession(clientIp);
                    if (userSession == null || userSession.getExecutor(sessionId) == null) {
                        session.sendMessage(new TextMessage(createMessage("Please init database", "error")));
                    } else {
                        userSession.updateLastAccessedTime(); // 更新最后访问时间
                        handleSqlCommand(session, userSession, sessionId, sql);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void handleInitCommand(WebSocketSession session, String clientIp, String sessionId) throws IOException {
        UserSession userSession = userManager.getUserSession(clientIp);
        if (userSession != null && userSession.getExecutor(sessionId) != null) {
            session.sendMessage(new TextMessage(createMessage("Database is already initialized in this session.", "success")));
            return;
        }

        if (userSession == null) {
            userSession = new UserSession(clientIp, System.currentTimeMillis());
            userManager.addUserSession(clientIp, userSession);
        }

        String directoryPath = dbPath + File.separator + clientIp;
        String dbFilePath = directoryPath + File.separator + clientIp;

        File ipDirectory = new File(directoryPath);
        if (!ipDirectory.exists() && !ipDirectory.mkdirs()) {
            session.sendMessage(new TextMessage(createMessage("Database init failed: cannot create directory.", "error")));
            return;
        }
        boolean databaseExists = checkIfDatabaseFilesExist(directoryPath, clientIp);

        if (databaseExists) {
            initializeDatabase(userSession, dbFilePath, sessionId);
        } else {
            createDatabase(dbFilePath);
            initializeDatabase(userSession, dbFilePath, sessionId);
        }

        session.sendMessage(new TextMessage(createMessage("Database init and load success!", "success")));
    }

    private boolean checkIfDatabaseFilesExist(String directoryPath, String filePrefix) {
        String[] requiredFiles = {".bt", ".db", ".log", ".xid"};

        File dir = new File(directoryPath);
        if (dir.exists() && dir.isDirectory()) {
            for (String suffix : requiredFiles) {
                File file = new File(directoryPath, filePrefix + suffix);
                if (!file.exists()) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private void createDatabase(String path) {
        TransactionManager tm = TransactionManager.create(path);
        DataManager dm = DataManager.create(path, DEFALUT_MEM, tm);
        VersionManager vm = new VersionManagerImpl(tm, dm);
        TableManager.create(path, vm, dm);
        tm.close();
        dm.close();
    }

    private void initializeDatabase(UserSession userSession, String dbFilePath, String sessionId) {
        if (userSession.getTableManager() == null) {
            TransactionManager tm = TransactionManager.open(dbFilePath);
            DataManager dm = DataManager.open(dbFilePath, 32 * 1024 * 1024, tm);
            VersionManager vm = new VersionManagerImpl(tm, dm);
            TableManager tbm = TableManager.open(dbFilePath, vm, dm);

            userSession.setTransactionManager(tm);
            userSession.setDataManager(dm);
            userSession.setTableManager(tbm);
        }

        Executor executor = new Executor(userSession.getTableManager());
        userSession.setExecutor(sessionId, executor);
    }

    private void handleSqlCommand(WebSocketSession session, UserSession userSession, String sessionId, String sql) throws IOException {
        try {
            Executor executor = userSession.getExecutor(sessionId);
            byte[] execute = executor.execute(sql.getBytes());
            String result = new String(execute).trim();
            session.sendMessage(new TextMessage(createMessage(result, "success")));
        } catch (Exception e) {
            e.printStackTrace();
            session.sendMessage(new TextMessage(createMessage(e.getMessage(), "error")));
        }
    }

    private String createMessage(String data, String type) {
        return "{\"type\": \"" + type + "\", \"data\": \"" + escapeJson(data) + "\"}";
    }

    private String escapeJson(String data) {
        if (data == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder();
        for (char c : data.toCharArray()) {
            switch (c) {
                case '\"':
                    escaped.append("\\\"");
                    break;
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '\b':
                    escaped.append("\\b");
                    break;
                case '\f':
                    escaped.append("\\f");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    if (c <= 0x1F) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
            }
        }
        return escaped.toString();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String clientIp = (String) session.getAttributes().get("clientIp");
        String sessionId = session.getId();

        UserSession userSession = userManager.getUserSession(clientIp);
        if (userSession != null) {
            userSession.removeExecutor(sessionId);
            userSession.removeSession(sessionId);

            ExecutorService executorService = sessionExecutorMap.remove(sessionId);
            if (executorService != null) {
                executorService.shutdown();
            }

            if (!userSession.hasActiveSessions()) {
                userSession.updateLastAccessedTime();
            }
        }

        super.afterConnectionClosed(session, status);
    }
}

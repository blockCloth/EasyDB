package com.dyx.simpledb.websocket;

import com.dyx.simpledb.backend.Launcher;
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

@Component
public class TerminalWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private UserManager userManager;

    @Value("${custom.db.path}")
    private String dbPath;

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        String payload = message.getPayload();
        String clientIp = (String) session.getAttributes().get("clientIp");

        if (payload.trim().equalsIgnoreCase("init")) {
            handleInitCommand(session, clientIp);
        } else {
            UserSession userSession = userManager.getUserSession(clientIp);
            if (userSession == null || userSession.getExecutor() == null) {
                session.sendMessage(new TextMessage(createMessage("Please init database", "error")));
            } else {
                userSession.updateLastAccessedTime(); // 更新最后访问时间
                handleSqlCommand(session, userSession, payload);
            }
        }
    }

    private void handleInitCommand(WebSocketSession session, String clientIp) throws IOException {
        UserSession userSession = userManager.getUserSession(clientIp);
        if (userSession != null && userSession.getExecutor() != null) {
            session.sendMessage(new TextMessage(createMessage("Database is already initialized.", "info")));
            return;
        }

        if (!userManager.canInit(clientIp)) {
            session.sendMessage(new TextMessage(createMessage("Maximum user limit reached or user session expired.", "error")));
            return;
        }

        userSession = new UserSession(clientIp, System.currentTimeMillis());
        userManager.addUserSession(clientIp, userSession); // 添加新的用户会话

        String directoryPath = dbPath + File.separator + clientIp;
        String dbFilePath = directoryPath + File.separator + clientIp;

        // 创建IP地址对应的目录
        File ipDirectory = new File(directoryPath);
        if (!ipDirectory.exists() && !ipDirectory.mkdirs()) {
            session.sendMessage(new TextMessage(createMessage("Database init failed: cannot create directory.", "error")));
            return;
        }

        handleDatabaseInitialization(userSession, dbFilePath);

        session.sendMessage(new TextMessage(createMessage("Database init and load success!", "info")));
    }

    private void handleDatabaseInitialization(UserSession userSession, String dbFilePath) {
        try {
            // 尝试打开现有数据库文件
            File dbFile = new File(dbFilePath);
            if (dbFile.exists()) {
                initializeDatabase(userSession, dbFilePath);
            } else {
                // 如果数据库文件不存在，则创建一个新的数据库文件
                // Launcher.createDB(dbFilePath);
               /* public static void createDB(String path) {
                    TransactionManager tm = TransactionManager.create(path);
                    DataManager dm = DataManager.create(path, DEFALUT_MEM, tm);
                    VersionManager vm = new VersionManagerImpl(tm, dm);
                    TableManager.create(path, vm, dm);
                    tm.close();
                    dm.close();
                }*/
                initializeDatabase(userSession, dbFilePath);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initializeDatabase(UserSession userSession, String dbFilePath) {
        TransactionManager tm = TransactionManager.open(dbFilePath);
        DataManager dm = DataManager.open(dbFilePath, 32 * 1024 * 1024, tm);
        VersionManager vm = new VersionManagerImpl(tm, dm);
        TableManager tbm = TableManager.open(dbFilePath, vm, dm);
        Executor executor = new Executor(tbm); // 创建Executor实例

        userSession.setTransactionManager(tm);
        userSession.setDataManager(dm);
        userSession.setTableManager(tbm);
        userSession.setExecutor(executor); // 保存Executor实例
    }

    private void handleSqlCommand(WebSocketSession session, UserSession userSession, String sql) throws IOException {
        try {
            byte[] execute = userSession.getExecutor().execute(sql.getBytes()); // 使用用户特定的Executor实例
            String result = new String(execute).trim(); // 去掉多余的空格
            session.sendMessage(new TextMessage(createMessage(result, "info")));
        } catch (Exception e) {
            e.printStackTrace();
            session.sendMessage(new TextMessage(createMessage("SQL execution error: " + e.getMessage(), "error")));
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

    /*@Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String clientIp = (String) session.getAttributes().get("clientIp");
        UserSession userSession = userManager.getUserSession(clientIp);
        if (userSession != null) {
            userSession.updateLastAccessedTime(); // 更新最后访问时间
        }
    }*/
}

package com.dyx.simpledb.controller;

import com.dyx.simpledb.backend.Launcher;
import com.dyx.simpledb.backend.dm.DataManager;
import com.dyx.simpledb.backend.server.Executor;
import com.dyx.simpledb.backend.tbm.TableManager;
import com.dyx.simpledb.backend.tm.TransactionManager;
import com.dyx.simpledb.backend.vm.VersionManager;
import com.dyx.simpledb.backend.vm.VersionManagerImpl;
import com.dyx.simpledb.common.UserManager;
import com.dyx.simpledb.common.UserSession;
import com.dyx.simpledb.util.IpUtil;
import com.dyx.simpledb.util.ResponseV;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("db")
public class LauncherController {

    private static final Logger logger = LoggerFactory.getLogger(LauncherController.class);

    @Value("${custom.db.path}")
    private String dbPath;

    @Autowired
    private UserManager userManager;

    @PostConstruct
    public void init() {
        File baseDir = new File(dbPath);
        if (!baseDir.exists()) {
            if (baseDir.mkdirs()) {
                logger.info("Base directory created at: {}", dbPath);
            } else {
                logger.error("Failed to create base directory at: {}", dbPath);
            }
        }
    }

    @GetMapping("/get-client-ip")
    public ResponseEntity<Map<String, String>> getClientIp(HttpServletRequest request) {
        String clientIp = IpUtil.getClientIp(request);
        Map<String, String> response = new HashMap<>();
        response.put("ip", clientIp);
        return ResponseEntity.ok(response);
    }

    @GetMapping("init")
    public ResponseV initDB(HttpServletRequest request) {

        String clientIp = IpUtil.getClientIp(request);
        if (!userManager.canInit(clientIp)) {
            return new ResponseV("Maximum user limit reached or user session expired.");
        }

        String directoryPath = dbPath + File.separator + clientIp;
        String dbFilePath = directoryPath + File.separator + clientIp;

        // 创建IP地址对应的目录
        File ipDirectory = new File(directoryPath);
        if (!ipDirectory.exists() && !ipDirectory.mkdirs()) {
            return new ResponseV("Database init failed: cannot create directory.");
        }

        UserSession userSession = userManager.getUserSession(clientIp);
        if (userSession.getTableManager() == null) {
            handleDatabaseInitialization(userSession, dbFilePath);
        }

        return new ResponseV("Database init and load success!");
    }

    private void handleDatabaseInitialization(UserSession userSession, String dbFilePath) {
        try {
            File dbFile = new File(dbFilePath);
            if (dbFile.exists()) {
                initializeDatabase(userSession, dbFilePath);
            } else {
                Launcher.createDB(dbFilePath);
                initializeDatabase(userSession, dbFilePath);
            }
        } catch (Exception e) {
            logger.error("Database operation failed", e);
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

    @PostMapping("sql")
    public ResponseV parseSql(@RequestParam String sql, HttpServletRequest request) {
        String clientIp = IpUtil.getClientIp(request);
        UserSession userSession = userManager.getUserSession(clientIp);

        if (userSession == null || userSession.getExecutor() == null) {
            return new ResponseV("Please init database");
        }

        try {
            byte[] execute = userSession.getExecutor().execute(sql.getBytes()); // 使用用户特定的Executor实例
            return new ResponseV(new String(execute));
        } catch (Exception e) {
            logger.error("SQL execution error", e);
            return new ResponseV("SQL exception, please rewrite\n" + e.getMessage());
        }
    }
}

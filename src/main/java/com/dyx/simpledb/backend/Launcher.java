package com.dyx.simpledb.backend;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.dyx.simpledb.backend.dm.DataManager;
import com.dyx.simpledb.backend.server.Server;
import com.dyx.simpledb.backend.tbm.TableManager;
import com.dyx.simpledb.backend.tm.TransactionManager;
import com.dyx.simpledb.backend.utils.Panic;
import com.dyx.simpledb.backend.vm.VersionManager;
import com.dyx.simpledb.backend.vm.VersionManagerImpl;
import com.dyx.simpledb.common.Error;

public class Launcher {
    // 定义服务器监听的端口号
    public static final int port = 9999;
    // 定义默认的内存大小，这里是64MB，用于数据管理器
    public static final long DEFALUT_MEM = (1 << 20) * 64;
    // 定义一些内存单位，用于解析命令行参数中的内存大小
    public static final long KB = 1 << 10; // 1KB
    public static final long MB = 1 << 20; // 1MB
    public static final long GB = 1 << 30; // 1GB

    public static void main(String[] args) throws ParseException {
        Options options = new Options();
        options.addOption("open", true, "-open D:/");
        options.addOption("create", true, "-create DBPath");
        options.addOption("mem", true, "-mem 64MB");
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        if (cmd.hasOption("open")) {
            openDB(cmd.getOptionValue("open"), parseMem(cmd.getOptionValue("mem")));
            return;
        }
        if (cmd.hasOption("create")) {
            createDB(cmd.getOptionValue("create"));
            return;
        }
        System.out.println("Usage: launcher (open|create) DBPath");
    }

    /**
     * 创建新的数据库
     *
     * @param path 数据库路径
     */
    private static void createDB(String path) {
        // 创建事务管理器
        TransactionManager tm = TransactionManager.create(path);
        // 创建数据管理器
        DataManager dm = DataManager.create(path, DEFALUT_MEM, tm);
        // 创建版本管理器
        VersionManager vm = new VersionManagerImpl(tm, dm);
        // 创建表管理器
        TableManager.create(path, vm, dm);
        tm.close();
        dm.close();
    }

    /**
     * 启动已有的数据库
     */
    private static void openDB(String path, long mem) {
        // 打开事务管理器
        TransactionManager tm = TransactionManager.open(path);
        // 打开数据管理器，传入路径、内存大小和事务管理器
        DataManager dm = DataManager.open(path, mem, tm);
        // 创建版本管理器，传入事务管理器和数据管理器
        VersionManager vm = new VersionManagerImpl(tm, dm);
        // 打开表管理器，传入路径、版本管理器和数据管理器
        TableManager tbm = TableManager.open(path, vm, dm);
        // 创建服务器对象，并启动服务器
        new Server(port, tbm).start();
    }

    // 定义一个方法，用于解析命令行参数中的内存大小
    private static long parseMem(String memStr) {
        // 如果内存大小为空或者为空字符串，那么返回默认的内存大小
        if (memStr == null || "".equals(memStr)) {
            return DEFALUT_MEM;
        }
        // 如果内存大小的字符串长度小于2，那么抛出异常
        if (memStr.length() < 2) {
            Panic.panic(Error.InvalidMemException);
        }
        // 获取内存大小的单位，即字符串的后两个字符
        String unit = memStr.substring(memStr.length() - 2);
        // 获取内存大小的数值部分，即字符串的前部分，并转换为数字
        long memNum = Long.parseLong(memStr.substring(0, memStr.length() - 2));
        // 根据内存单位，计算并返回最终的内存大小
        switch (unit) {
            case "KB":
                return memNum * KB;
            case "MB":
                return memNum * MB;
            case "GB":
                return memNum * GB;
            // 如果内存单位不是KB、MB或GB，那么抛出异常
            default:
                Panic.panic(Error.InvalidMemException);
        }
        // 如果没有匹配到任何情况，那么返回默认的内存大小
        return DEFALUT_MEM;
    }
}

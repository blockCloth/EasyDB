---

# EasyDB 简介

EasyDB 是一个用 Java 实现的简单数据库，部分原理参照自 MySQL、PostgreSQL 和 SQLite，以及参考了[MYDB](https://github.com/CN-GuoZiyang/MYDB)，旨在提供一个轻量级的数据库解决方案。它具备以下核心功能：

- 数据的可靠性和数据恢复
- 两段锁协议（2PL）实现可串行化调度
- MVCC（多版本并发控制）
- 四种事务隔离级别（读未提交、读提交、可重复读和串行化）
- 死锁处理以及超时检测
- 简单的表和字段管理，常见约束以及主键索引

项目体验地址: [http://db.blockcloth.cn](http://db.blockcloth.cn)

## 项目特点

### Spring Boot 构建

项目采用 Spring Boot 进行构建，大大简化了项目的启动流程和配置管理。用户只需修改 `application.yml` 中的路径信息，即可快速启动项目。这种方式不仅提升了启动效率，也使得与前端的对接更加方便。

### WebSocket 实时通信

为提升用户界面的互动体验，项目采用 WebSocket 进行实时通信。每个用户在建立连接时，都会有独立的数据区，以防止数据冲突。同时，针对单个用户多页面的访问行为进行了线程管理，确保每个界面只会使用一个线程，避免锁定冲突。此外，系统还实现了用户体验时间管理和数据区的自动销毁。

### JSQLParser 处理 SQL 语句

EasyDB 引入了 JSQLParser 这一 Java 库，用于解析和处理 SQL 语句。JSQLParser 可以将 SQL 语句转换为抽象语法树 (AST)，从而简化了 SQL 查询的分析和修改过程。开发者无需手动解析 SQL 字符串，只需使用 `CCJSqlParserUtil.parse(sql)` 便可将 SQL 语句解析为具体的对象（如 SELECT、UPDATE 等）。

### 全表扫描与索引处理

在 EasyDB 中，即使字段没有建立索引，也能进行条件筛选。全表扫描功能确保在没有条件限制的情况下，依然可以获取整张表的数据，保证数据展示的完整性和可靠性。

### 条件约束与主键索引

EasyDB 支持为字段设置唯一、非空、自增等条件约束。例如，在建立主键索引时，字段需要满足唯一性和非空性约束。通过引入 `Set` 集合，系统确保了数据插入前的唯一性匹配检查。此外，自增约束通过 `AtomicInteger` 实现，只对 `int` 和 `long` 类型的数据有效。

### 事务隔离机制与死锁检测

EasyDB 完善了事务的隔离机制，支持读未提交和可串行化级别的事务隔离。对于可串行化事务，系统通过定义全局锁的方式实现序列化处理，其他事务需等待该事务完成后才能继续。为了避免锁定卡死现象，系统增强了死锁检测功能，当等待队列中的事务超过 30 秒未被处理时，系统会自动触发回滚，以防止长时间的系统资源消耗。

### 优化 SHOW 和 DROP 语法

EasyDB 对 `SHOW` 和 `DROP` 语法进行了优化，使数据库操作更加简便。`SHOW` 语法包括查看当前数据区内的所有表和查看表结构两种方式；而 `DROP` 语法则用于删除表信息，确保在无任何事务引用的情况下，安全地删除表数据及其相关信息。

### 数据展示格式

为了提升数据展示的直观性和美观性，EasyDB 对数据展示格式进行了重新设计，避免因展示不清晰而导致用户的误解。

## 前端界面

前端部分基于一个开源的终端项目进行了定制化开发，结合 WebSocket 实现了一个功能完整的终端界面。项目地址：[https://github.com/Tomotoes/react-terminal](https://github.com/Tomotoes/react-terminal)

## 如何启动项目

1. 克隆项目到本地：
   ```bash
   git clone https://github.com/blockCloth/EasyDB.git
   ```
2. 修改 `application.yml` 配置文件中的路径信息。
3. 使用 IDE 打开项目，运行主类 `SimpleSqlDatabaseApplication.java` 启动项目。

## 致谢

欢迎所有需要参与和支持 EasyDB 项目开发的朋友。如果你喜欢这个项目，欢迎在 GitHub 上点个 Star 支持我们！

项目地址：[https://github.com/blockCloth/EasyDB](https://github.com/blockCloth/EasyDB)

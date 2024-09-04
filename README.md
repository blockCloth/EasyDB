# EasyDB

<div align="center">
  <img src="https://blockcloth.cn/codingblog/logo_transparent.png" alt="EasyDB Logo" width="200"/>
</div>

**轻量级、高性能的自定义数据库解决方案**

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Version](https://img.shields.io/badge/version-1.0.0-brightgreen.svg)](https://github.com/blockCloth/EasyDB)

---

## 简介

**EasyDB** 是一个使用 Java 实现的轻量级数据库，灵感来源于 MySQL、PostgreSQL 和 SQLite。项目参考了 MYDB 的设计，专注于提供高效的数据库解决方案，支持数据存储、事务管理、多版本并发控制（MVCC）和索引管理等核心功能，同时集成了日志管理、事务隔离与死锁检测等高级特性。

---

## 主要特性

### 🛠️ 核心功能

- **可靠性**：采用 MySQL、PostgreSQL 和 SQLite 的部分原理，确保数据的安全性与一致性。
- **两阶段锁协议（2PL）**：实现串行化调度，支持多种事务隔离级别。
- **多版本并发控制（MVCC）**：优化并发操作，减少阻塞，提高系统性能。

### 🌐 WebSocket 实时通信

- **独立的数据区**：每个用户拥有独立的数据区，确保数据安全性和用户操作的互不干扰。
- **多页面管理**：优化多页面访问体验，提升用户操作的流畅度。

### 🔍 高效 SQL 解析

- **JSQLParser 集成**：使用 JSQLParser 将 SQL 语句解析为抽象语法树 (AST)，简化 SQL 查询的分析与修改。

### ⚙️ 数据管理与优化

- **全表扫描与索引处理**：支持在字段未建立索引的情况下进行条件筛选操作。
- **条件约束**：内置丰富的条件约束与主键索引功能，支持唯一性、非空性、自增性等多种约束条件。

### 🚦 事务控制与死锁检测

- **事务隔离机制**：支持从读未提交到串行化的多种隔离级别。
- **死锁检测**：通过超时检测功能防止系统资源长期占用，增强系统的可靠性。

### 📝 日志管理与故障恢复

- **日志管理**：内置强大的日志管理机制，确保数据库操作的可追溯性和数据一致性保障。
- **故障恢复**：支持故障恢复功能，增强系统的容错能力和数据安全性。

---

## 快速开始

### 1. 克隆项目

```bash
git clone https://github.com/blockCloth/EasyDB.git
cd EasyDB
```

### 2. 配置环境

请确保你已经安装了以下工具：

- **JDK 8+**：Java 运行环境。
- **Maven**：用于管理项目依赖。

### 3. 启动项目

```bash
启动SimpleSqlDatabaseApplication.java类即可
```

项目启动成功后，可以通过访问 `http://localhost:8081/index.html` 进行体验。

---

## 使用指南

欲了解如何使用 EasyDB 的详细信息，请查阅以下文档：

- [项目体验](http://db.blockcloth.cn/)
- [使用指南](http://easydb.blockcloth.cn/document/)
- [项目文档](http://easydb.blockcloth.cn/demo)

---

## 贡献

欢迎贡献代码和提交 Issue。如果你有任何问题或建议，请随时提交。

1. Fork 本仓库
2. 创建你的特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交你的修改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 提交 Pull Request

---

## 许可证

本项目使用 [MIT 许可证](https://opensource.org/licenses/MIT) 开源，详情请查阅 `LICENSE` 文件。

---

© 2024-至今 blockCloth

---

### 注意事项

- 项目使用 Spring Boot 构建，确保项目配置正确。
- 为了项目的顺利运行，请参阅文档中的详细配置步骤。

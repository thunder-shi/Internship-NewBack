---
name: update-claude-md
description: 扫描当前代码库，自动更新 .claude/CLAUDE.md 中的项目统计、目录结构、实体列表、接口列表等
user_invocable: true
---

# 自动更新 CLAUDE.md

扫描当前项目源码，刷新 CLAUDE.md 中的所有可自动化统计的章节。

## 使用方式

```
/update-claude-md
```

## 执行步骤

### 1. 统计项目规模

扫描 `src/main/java/newcms/` 下所有 `.java` 文件，更新以下统计项：

| 统计项 | 扫描路径 |
|--------|----------|
| Java 文件总数 | `**/*.java` |
| 控制器 (Controller) | `controller/**/*Controller.java`（排除 commonCtrl 下的基类） |
| 服务接口 (Service) | `service/I*.java` |
| 服务实现 (ServiceImpl) | `service/impl/*.java` |
| 数据库实体 (Entity) | `entity/db/*.java`（排除 `View*.java`） |
| 视图实体 (View Entity) | `entity/db/View*.java` |
| 基础实体类 (Base Entity) | `entity/base/*.java` |
| DAO 接口 | `repository/db/*.java` |
| 工具类 (Utils) | `utils/*.java` |
| 配置类 (Config) | `config/*.java` |
| 注解类 (Annotation) | `annotation/*.java` |
| 基础类 (Base) | `base/*.java` |

### 2. 更新目录结构

用 `tree` 或 `find` 重新生成 `src/main/java/newcms/` 的目录结构，包含所有 `.java` 文件和注释。

### 3. 更新实体列表

扫描 `entity/db/` 下所有实体类，按前缀分组（Main/Base/Rel/Sys/View），读取每个实体的：
- 类名
- `@Entity` + `@Table` 注解确定表名（无 @Table 则 JPA 自动转换）
- 类注释（JavaDoc）
- 关键字段（`@Column` 注解的字段名列表）

更新 **`.claude/docs/entities.md`** 中的业务实体、基础实体、系统实体、视图实体四个表格（不修改主 CLAUDE.md）。

### 4. 更新 API 接口列表

扫描所有 Controller 类，提取：
- `@PathRestController` 的 value（基础路径）
- 每个方法的 `@PostMapping` / `@GetMapping` / `@PutMapping` / `@DeleteMapping`（路径）
- `@Operation` 注解的 summary 和 description
- 参数类型（@RequestBody / @PathVariable / @RequestParam）

更新 **`.claude/docs/api.md`**（不修改主 CLAUDE.md）。

### 5. 更新常量定义

读取 `Constant.java`，提取：
- `PROCESS_TYPE` 所有常量及注释
- `AUDIT_STATUS` 所有常量
- `VERIFY_LEVEL` 所有常量
- `ROLE_TABLE` 所有常量

更新 CLAUDE.md 中涉及这些常量的章节。

### 6. 更新 Service 接口列表

扫描 `service/I*.java`，提取每个接口的公开方法签名和 JavaDoc 注释，更新目录结构中 service 部分的注释。

### 7. 检查已移除内容

扫描 CLAUDE.md 中引用的类名和文件路径，验证它们是否仍然存在：
- 若类已不存在，标注或移除相关描述
- 若类已重命名（如 `RelStuInternship` → `RelStuInternshipPost`），更新引用

### 8. 保持不变的章节

以下章节不自动修改（需要人工维护）：
- 项目概述、技术栈、应用配置
- 关键业务规则（nowPersonNum、审核完成判断、视图语义说明）
- 开发规则
- `.claude/docs/verify-flow.md`（审核流程详解，业务逻辑描述）

## 输出

直接编辑 `.claude/CLAUDE.md` 文件，仅更新可自动化的章节，保留人工编写的内容不变。更新完成后输出变更摘要。

---
name: new-entity
description: 新增数据库实体 + DAO + 视图实体 + 视图 DAO 全套脚手架，遵循项目命名约定
user_invocable: true
---

# 新增实体全套脚手架

根据用户提供的表名和字段信息，生成完整的 Entity + DAO + View Entity + View DAO。

## 使用方式

```
/new-entity <实体名> [字段描述]
```

示例：
- `/new-entity RelCompanyPost companyId:Integer, postName:String, salary:Integer`
- `/new-entity MainDiary` — 只给名称，交互式询问字段

## 执行步骤

### 1. 确认实体信息

从用户输入中解析：
- **实体类名**（驼峰，如 `RelCompanyPost`）
- **字段列表**（名称、类型、注释）
- **基类选择**：
  - `BaseInfo` — 默认，含 id / isDeleted / createTime / updateTime
  - `NameRemarkInfo` — 继承 BaseInfo + name / remark
  - `NameRemarkOrderInfo` — 继承 NameRemarkInfo + theOrder
  - `OrderInfo` — 继承 BaseInfo + theOrder
  - `BaseTreeInfo` — 树形结构，含 parentId / theLevel / isLeaf / childNum
  - `VerifyConfigInfo` — 审核配置，含 verifyTypeId / verifyFirstRoleId ~ verifyFifthRoleId
- **是否需要 currentVerifyTypeId**：如果实体需要接入审核流程，添加此字段（默认值 1）
- **是否需要视图**：默认需要，用户可指定不需要

### 2. 生成实体类

路径：`src/main/java/newcms/entity/db/<EntityName>.java`

模板：
```java
package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.<BaseName>;

@Getter
@Setter
@Entity
public class <EntityName> extends <BaseName> {
    @Column(columnDefinition = "<类型> comment '<注释>'")
    private <JavaType> <fieldName>;
    // ...
}
```

**关键约定**：
- 外键字段用 `int unsigned not null comment '外键，关联表X（说明）'`
- 可选字段不加 `not null`
- 默认值用 `default '<值>'`
- 需要审核的实体加 `currentVerifyTypeId` 字段：
  ```java
  @Column(columnDefinition = "integer default '1' comment '当前处在的审核级别'")
  private Integer currentVerifyTypeId = 1;
  ```

### 3. 生成 DAO 接口

路径：`src/main/java/newcms/repository/db/<EntityName>Dao.java`

模板：
```java
package newcms.repository.db;

import newcms.entity.db.<EntityName>;
import newcms.repository.base.BaseDao;

public interface <EntityName>Dao extends BaseDao<<EntityName>> {
}
```

若基类是 `BaseTreeInfo`，则继承 `BaseTreeDao`：
```java
public interface <EntityName>Dao extends BaseTreeDao<<EntityName>> {
}
```

### 4. 生成视图实体（如需要）

路径：`src/main/java/newcms/entity/db/View<EntityName>.java`

**重要**：视图实体是只读的，使用 `@Immutable` 和 `@Subselect` 或 `@Table(name = "view_xxx")`

模板：
```java
package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;
import org.hibernate.annotations.Immutable;

@Getter
@Setter
@Entity
@Immutable
@Table(name = "view_<snake_case_name>")
public class View<EntityName> extends BaseInfo {
    // 包含原表字段 + 关联表的冗余字段
    // 字段名与视图列名一一对应（JPA 自动下划线转驼峰）
}
```

### 5. 生成视图 DAO（如需要）

路径：`src/main/java/newcms/repository/db/View<EntityName>Dao.java`

模板：
```java
package newcms.repository.db;

import newcms.entity.db.View<EntityName>;
import newcms.repository.base.BaseDao;

public interface View<EntityName>Dao extends BaseDao<View<EntityName>> {
}
```

### 6. 提供 SQL 视图建议

输出创建数据库视图的 SQL 语句建议（不自动执行，不修改 src/sql/ 下的文件）：

```sql
CREATE OR REPLACE VIEW view_<snake_case> AS
SELECT
    t.*,
    r.name AS <relation>_name
FROM <table_name> t
LEFT JOIN <related_table> r ON t.<fk_field> = r.id
WHERE t.is_deleted = 0;
```

### 7. 更新 DaoClassUtil 映射

检查 `DaoClassUtil.java` 中是否使用了硬编码映射。如果是动态反射加载（当前项目采用此方式），则无需修改，提醒用户新实体已自动可用。

## 命名规范

| 类型 | 命名格式 | 示例 |
|------|----------|------|
| 实体类 | 驼峰 | `RelCompanyPost` |
| 表名 | JPA 自动转下划线 | `rel_company_post` |
| DAO | `<Entity>Dao` | `RelCompanyPostDao` |
| 视图实体 | `View<Entity>` | `ViewRelCompanyPost` |
| 视图 DAO | `View<Entity>Dao` | `ViewRelCompanyPostDao` |
| 视图名 | `view_<snake_case>` | `view_rel_company_post` |

## 实体前缀约定

| 前缀 | 含义 | 示例 |
|------|------|------|
| `Main` | 核心业务表 | `MainInternship`, `MainVerifyProcess` |
| `Base` | 基础/字典数据表 | `BaseUser`, `BaseMajor` |
| `Rel` | 关联关系表 | `RelTeacherStudent`, `RelUserRole` |
| `Sys` | 系统管理表 | `SysRole`, `SysMenu` |
| `View` | 视图实体（只读） | `ViewMainInternship` |

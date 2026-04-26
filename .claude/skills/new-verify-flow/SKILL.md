---
name: new-verify-flow
description: 将新业务实体接入多级审核流程，生成审核相关代码和视图
user_invocable: true
---

# 接入多级审核流程

将指定业务实体接入项目的多级审核工作流，包括审核记录创建、审核推进、退回处理、审核人刷新等完整流程。

## 使用方式

```
/new-verify-flow <业务实体名> [流程类型代码]
```

示例：
- `/new-verify-flow RelCompanyPost EXTERNAL_ENTERPRISE_POST_DECLARATION`
- `/new-verify-flow RelTitleStudent INTERNAL_STUDENT_TEACHER_MATCH`

## 前置条件

确认业务实体满足以下条件：
1. 实体类已存在且包含 `currentVerifyTypeId` 字段（默认值 1）
2. 对应的流程类型代码已在 `Constant.PROCESS_TYPE` 中定义
3. `RelProcessInternshipType` 中已配置该流程类型的审核角色模板

## 执行步骤

### 1. 检查业务实体

读取目标实体类，确认：
- 是否有 `currentVerifyTypeId` 字段，没有则添加：
  ```java
  @Column(columnDefinition = "integer default '1' comment '当前处在的审核级别'")
  private Integer currentVerifyTypeId = 1;
  ```
- 确认实体的 DAO 已存在

### 2. 检查流程类型常量

读取 `Constant.java`，确认 `PROCESS_TYPE` 中是否已定义目标流程类型代码。若未定义，在 `PROCESS_TYPE` 类中添加新常量：
```java
public static final String XXX = "XXX"; // 描述
```

### 3. 在 IInternshipService 中添加方法

根据业务需求，添加以下方法（按需选择）：

```java
/**
 * 创建首条审核记录
 * 需审核时 isAudit=-1（保存未提交），无需审核时 isAudit=1（直接通过）
 */
void createFirstVerifyProcess(Integer relationId, Integer internshipId, Integer createUserId);
```

### 4. 在 InternshipServiceImpl 中实现

**核心流程**（参考现有 `createFirstVerifyProcessForRelTeacherStudent` 实现）：

```java
public void createFirstVerifyProcess(Integer relationId, Integer internshipId, Integer createUserId) {
    // 1. 获取流程配置
    Object processObj = iVerifyProcessService.GetInternshipProcess(
        internshipId, Constant.PROCESS_TYPE.XXX);
    JSONObject processJson = FastJsonUtil.toJson(processObj);
    Integer processId = processJson.getInteger("id");

    // 2. 判断是否需要审核
    Integer verifyTypeId = processJson.getInteger("verifyTypeId");
    boolean needsVerify = verifyTypeId != null && verifyTypeId >= Constant.VERIFY_LEVEL.ONE_VERIFY;

    // 3. 计算审核人
    String verifyUserId;
    int isAudit;
    if (needsVerify) {
        Integer verifyFirstRoleId = processJson.getInteger("verifyFirstRoleId");
        verifyUserId = iVerifyProcessService.GetVerifyUserId(verifyFirstRoleId, createUserId);
        isAudit = Constant.AUDIT_STATUS.SAVE; // -1
    } else {
        verifyUserId = "系统自动通过";
        isAudit = Constant.AUDIT_STATUS.PASS; // 1
    }

    // 4. 创建 MainVerifyProcess 记录
    JSONObject verifyJson = new JSONObject();
    verifyJson.put("relationId", relationId);
    verifyJson.put("processId", processId);
    verifyJson.put("createUserId", createUserId);
    verifyJson.put("verifyUserId", verifyUserId);
    verifyJson.put("isAudit", isAudit);
    verifyJson.put("reason", "");
    verifyJson.put("tableName", "<EntityName>");  // 实体类名，非表名
    iCommonService.saveOneRecord("MainVerifyProcess", verifyJson);
}
```

**关键要点**：
- `tableName` 字段存的是**实体类名**（如 `"RelTeacherStudent"`），不是数据库表名
- `relationId` 是业务实体的主键 ID
- `processId` 是 `RelProcessInternship` 的主键 ID
- `createUserId` 是发起人的用户 ID

### 5. 审核推进（已有通用实现）

审核推进由 `InternshipServiceImpl.auditProcess()` 统一处理，通过 `VerifyProcessServiceImpl.onVerifyProcessApproved()` 自动推进。**无需为新实体单独编写推进逻辑**。

`onVerifyProcessApproved` 的通用机制：
1. 读取 `MainVerifyProcess` 获取 `tableName` 和 `relationId`
2. 通过 `iCommonService.getOneRecordById(tableName, relationId)` 动态读取业务实体
3. 获取 `currentVerifyTypeId` 并 +1
4. 通过 `iCommonService.saveOneRecord(tableName, ...)` 动态更新业务实体

**因此新实体只需确保有 `currentVerifyTypeId` 字段即可自动参与审核推进。**

### 6. 退回处理（已有通用实现）

退回逻辑由 `InternshipServiceImpl.auditProcess()` 中的 `createPendingRecordAfterBack()` 统一处理：
- 退回时 `currentVerifyTypeId - 1`（不低于 2）
- 新建 `isAudit = -1` 的记录等待重新提交

**特殊情况**：如果业务有特殊退回逻辑（如学生报名岗位退回要减人数），需要在 `auditProcess` 中按 `tableName` 判断并处理。

### 7. 创建审核综合视图

创建 Merge 视图实体，关联审核记录和业务数据，用于前端列表展示。

**视图命名规则**：
- 基础审核视图：`ViewVerifyProcess<EntityName>` — 审核记录 + 业务实体字段
- 合并审核视图：`ViewVerifyProcess<EntityName>Merge` — 将同一 relationId 的多条审核记录合并为一行

**Merge 视图 SQL 模板**：
```sql
CREATE OR REPLACE VIEW view_verify_process_<snake_case>_merge AS
SELECT
    biz.*,
    vp.id AS verify_process_id,
    vp.is_audit,
    vp.reason,
    vp.verify_user_id,
    vp.create_user_id AS verify_create_user_id,
    vp.process_id
FROM <business_table> biz
LEFT JOIN (
    SELECT *
    FROM main_verify_process
    WHERE table_name = '<EntityName>'
      AND is_deleted = 0
      AND id = (
          SELECT MAX(id)
          FROM main_verify_process sub
          WHERE sub.relation_id = main_verify_process.relation_id
            AND sub.table_name = main_verify_process.table_name
            AND sub.is_deleted = 0
      )
) vp ON vp.relation_id = biz.id
WHERE biz.is_deleted = 0;
```

### 8. 创建视图实体和 DAO

参照 `/new-entity` 技能创建视图实体和 DAO。

## 审核流程状态机速查

```
创建业务记录
    │
    ▼
isAudit = -1 (保存未提交)
    │ 用户提交（改为 0）
    ▼
isAudit = 0 (待审核) ◄─── 退回后重新提交
    │
    ├─ 通过 → isAudit = 1
    │         │
    │         ├─ nextLevel <= verifyTypeId → 创建下一级 (isAudit=0)
    │         └─ nextLevel >  verifyTypeId → 审核完成
    │
    ├─ 未通过 → isAudit = 2 (流程终止)
    │
    └─ 退回 → isAudit = 3
              │
              └─ currentVerifyTypeId - 1，新建 isAudit=-1 记录
```

## 检查清单

- [ ] 业务实体有 `currentVerifyTypeId` 字段
- [ ] `Constant.PROCESS_TYPE` 中有对应常量
- [ ] `RelProcessInternshipType` 中已配置审核角色模板
- [ ] `IInternshipService` 中添加了创建首条审核记录的方法
- [ ] `InternshipServiceImpl` 中实现了该方法
- [ ] `MainVerifyProcess.tableName` 使用实体类名（非表名）
- [ ] 创建了 Merge 视图 + 视图实体 + 视图 DAO
- [ ] 如有特殊退回逻辑，在 `auditProcess` 中按 tableName 处理

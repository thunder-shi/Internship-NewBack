# 审核流程详解

## 多级审核架构

支持最多 **5 级审核**，每级配置不同审核角色（verifyFirstRoleId ~ verifyFifthRoleId）。

### 审核状态码（isAudit）

| 状态码 | 含义 |
|--------|------|
| -1 | 保存未提交（SAVE） |
| 0 | 提交待审核（SUBMIT） |
| 1 | 当前级别审核通过（PASS） |
| 2 | 审核未通过（NOTPASS） |
| 3 | 审核退回（BACK） |

### 审核完成判断

**不通过 isAudit 判断**，而是：`currentVerifyTypeId > verifyTypeId` → 审核全部完成。

每次审核通过后 `currentVerifyTypeId +1`，包括最后一级。

### 级别与角色字段映射

`getVerifyRoleIdByLevel(relJson, verifyLevel)`：
- verifyLevel = 2 → verifyFirstRoleId
- verifyLevel = 3 → verifySecondRoleId
- verifyLevel = 4 → verifyThirdRoleId
- verifyLevel = 5 → verifyFourthRoleId
- verifyLevel = 6 → verifyFifthRoleId

## 核心表结构

### RelProcessInternship（实习流程配置）

| 字段 | 说明 |
|------|------|
| internshipId | 实习项目 ID |
| processTypeId | 流程类型 ID |
| verifyFirstRoleId ~ verifyFifthRoleId | 各级审核角色 ID |
| currentVerifyTypeId | 当前审核级别（初始1，完成时为 verifyTypeId+1） |
| verifyTypeId | 最大级别数 |
| startTime / endTime | 流程开始/截止时间 |

### MainVerifyProcess（审核记录）

| 字段 | 说明 |
|------|------|
| relationId | 关联业务 ID |
| tableName | 关联表名（如 "main_internship"） |
| createUserId | 提交人 ID |
| verifyUserId | 可审核人 ID 列表（格式：`12\|14\|17`） |
| isAudit | 审核状态（-1/0/1/2/3） |
| reason | 拒绝原因 |

## 审核人计算逻辑

`IVerifyProcessService.GetVerifyUserId(verifyRoleId, createUserId)`：
1. 根据 createUserId 查询创建人的 schoolId
2. 查询同校所有用户（ViewBaseUser.schoolId 相同）
3. 筛选 RelUserRole 中 roleId = verifyRoleId 且属于同校的用户
4. 将 userId 用 `|` 连接返回（如 `12|14|17`）

## 审核流程执行

### 创建实习项目（addNewInternship）
1. 创建 MainInternship
2. 根据 internshipTypeId 查询流程模板（RelProcessInternshipType），复制创建 RelProcessInternship
3. 若 startTime <= 当前时间，立即创建 MainVerifyProcess（isAudit=0）

### 审核推进（onVerifyProcessApproved）
1. 读取 currentVerifyTypeId 和 verifyTypeId
2. nextLevel = currentVerifyTypeId + 1
3. 若 nextLevel <= verifyTypeId：更新 currentVerifyTypeId，创建新的 MainVerifyProcess（isAudit=0）
4. 若 nextLevel > verifyTypeId：仅更新 currentVerifyTypeId，不创建新记录（审核完成）

### 岗位录用级联（审核完成后自动执行）
- `cancelOtherStuPostsOnApproval(approvedRelStuPostId, studentId, internshipId)`  
  软删除该学生在同实习项目下的其余报名及审核记录（一人只能被录用到一个岗位）
- `cancelPendingApplicationsIfPostFull(postId, approvedRelStuPostId)`  
  若岗位已满（nowPersonNum >= allPersonNum），软删除该岗位所有其余待审核报名

## 自动同步机制

### 审核人刷新（角色/部门变更时触发）
```
refreshPendingVerifyUsers()              // 刷新所有待审核记录
refreshPendingVerifyUsersByUser(userId)  // 刷新指定用户相关记录
```
触发时机：`UserServiceImpl.saveUserRoles()` / `editUserInfo()`（部门变更）

### 流程自动激活
```
activateStartedProcesses()  // 检查并激活已到开始时间的流程
```
触发时机：系统启动（ApplicationReadyEvent）、每天零点（@Scheduled）

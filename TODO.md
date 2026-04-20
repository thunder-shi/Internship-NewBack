# 并发安全 & 业务逻辑修正计划

> 生成日期：2026-04-19  
> 最后更新：2026-04-19  
> 背景：并发安全审查 + 校内实习选题业务规则梳理
>
> **数据库变更已全部完成（2026-04-19）**，剩余工作均为 Java 代码侧修改。

---

## 一、业务规则完整描述（以此为修改依据）

### 校内实习选题流程

```
老师申报题目 (RelTitleTeacher)
  ├─ isLimit=0（竞选题）：多名学生可同时报名同一题目，老师审核后择一录取
  └─ isLimit=1（限选题）：仅一名学生可被录取，选题后系统自动通过，无需人工审核

学生选题 (RelTitleStudent)
  ├─ 一个学生可同时报名多个不同题目
  ├─ 多个学生可同时报名同一题目（isLimit=0）
  └─ 同一学生不能重复选同一题目（(titleId, stuId) 应唯一）

录取（最终审核通过）触发的级联操作：
  ① 把同一 titleId 下其他学生的所有待审/已提交记录 → NOTPASS(2)
     原因："该题目已录取其他学生，请重新报名其他题目"
  ② 把被录取学生(stuId=X)的其他题目申请（titleId≠被录取题）→ NOTPASS(2)
     原因："已被其他题目录取，本申请关闭"
  注：NOTPASS 而非 BACK(3)，学生需要重新 new 一条申请，不是修改旧申请
```

### 校外实习选岗流程

```
岗位 (MainInternshipPost) 有容量上限：allPersonNum
学生选岗 (RelStuInternshipPost)：nowPersonNum 跟踪当前已选人数
  ├─ 选岗前检查：nowPersonNum < allPersonNum
  ├─ 选岗成功：nowPersonNum + 1
  └─ 审核拒绝/取消：nowPersonNum - 1
```

---

## 二、业务逻辑漏洞（缺失的功能，非并发问题）

### BIZ-01 ★★★ 竞选题录取后未自动拒绝其他申请人
**现状**：老师通过学生X对题目Y的申请后，其他报名题目Y的学生记录无任何变化，依然显示"待审核"。  
**影响**：其他学生无法感知该题目已被录取，审核页面数据混乱。  
**需要新增**：在 `onVerifyProcessApproved()` 判断 `nextLevel > verifyTypeId`（最终通过）且 `tableName = RelTitleStudent` 时：
1. 查出同 titleId 下所有 stuId ≠ 被录取学生 的 MainVerifyProcess 记录（isAudit 为 SAVE/-1 或 SUBMIT/0）
2. 批量置为 NOTPASS(2)，reason = "该题目已录取其他学生，请重新报名其他题目"
3. 同步更新对应 RelTitleStudent 的 topicReasons 字段

**位置**：`VerifyProcessServiceImpl.onVerifyProcessApproved()` 末尾（第 407~411 行附近）

---

### BIZ-02 ★★★ 学生被录取后未自动关闭其余申请
**现状**：学生X被题目Y录取后，X报名其他题目（titleId ≠ Y）的申请仍显示"待审核"，老师还能继续审核并通过，导致一个学生可能被多个题目同时录取。  
**影响**：破坏"一学生对应一题目"的业务约束。  
**需要新增**：在 BIZ-01 的同一触发点，追加：
1. 查出被录取学生 stuId=X 的其他 RelTitleStudent 记录（titleId ≠ Y，isAllVerified ≠ true）
2. 对这些记录的所有 SAVE/SUBMIT 状态 MainVerifyProcess → NOTPASS(2)，reason = "已被其他题目录取，本申请关闭"
3. 同步更新对应 RelTitleStudent 的 topicReasons 字段

**位置**：`VerifyProcessServiceImpl.onVerifyProcessApproved()` 末尾，与 BIZ-01 同步新增

---

### BIZ-03 ★★★ 限选题(isLimit=1)自动通过后未级联处理
**现状**：`markLimitedTitleSelectionAsFullyApproved()` 只更新了被自动通过学生的 currentVerifyTypeId，没有执行 BIZ-01 和 BIZ-02 的级联逻辑。  
**影响**：
- 其他学生选同一限选题后依然可以被自动通过
- 被自动通过的学生的其他申请未关闭
**需要新增**：`markLimitedTitleSelectionAsFullyApproved()` 完成自动通过后，复用 BIZ-01 + BIZ-02 的级联拒绝逻辑。

**位置**：`InternshipServiceImpl.markLimitedTitleSelectionAsFullyApproved()` 第 2545~2568 行

---

### BIZ-04 ★★ BACK(退回)逻辑对 RelTitleStudent 语义不符
**现状**：`createPendingRecordAfterBack()` 对所有 tableName 一视同仁，退回时创建新的 SAVE(-1) 记录。  
**问题**：若该 RelTitleStudent 对应的 titleId 已被其他学生录取（isAllVerified=true 存在），则为被拒学生创建新的 SAVE 记录没有意义——该学生无法重新提交同一题目的申请（题目已满）。  
**需要修改**：在 `createPendingRecordAfterBack()` 中，若 tableName = RelTitleStudent，先检查该 titleId 是否已被录取；若已录取，则不创建新 SAVE 记录，直接标记 NOTPASS 并更新 reason。

**位置**：`InternshipServiceImpl.createPendingRecordAfterBack()` 第 2656~2712 行

---

### BIZ-05 ★ getLatestRejectedTitleSelection 仅处理单条
**现状**：`getLatestRejectedTitleSelection()` 只返回最近一条被拒记录，供学生确认后删除。  
**问题**：引入 BIZ-01/BIZ-02 的级联拒绝后，一个学生可能同时存在多条被拒记录（被录取后其他申请全部关闭），前端若逐条确认效率低，且 UI 需要相应调整。  
**建议**：评估是否改为"批量确认所有被拒申请"的接口，或前端支持列表展示。  
**暂不修改**，留待前端联调后决定。

---

## 三、并发安全问题

### CONC-01 ★★★ 岗位人数读-改-写非原子（高优先级）
**现状**：`updatePostPersonNum()` 先 SELECT nowPersonNum，再 +delta，再 UPDATE，三步非原子。  
**竞态**：两个并发选岗请求同时读到 nowPersonNum=1，各自写入 2，实际应为 3。  
**影响文件**：
- `InternshipPostServiceImpl.updatePostPersonNum()` 第 218~238 行
- `InternshipServiceImpl.decreasePostPersonNumByRelation()` 第 2631~2649 行

**修复方案**：改为数据库原子 SQL：
```sql
UPDATE main_internship_post SET now_person_num = now_person_num + 1 WHERE id = ?
UPDATE main_internship_post SET now_person_num = now_person_num - 1 WHERE id = ? AND now_person_num > 0
```
通过 `@Query` 或 DAO 自定义方法实现，不再走读-改-写路径。

---

### CONC-02 ★★★ 岗位容量检查与写入之间存在时间窗口（TOCTOU）
**现状**：`checkPostCapacity()` 先查 nowPersonNum >= allPersonNum 抛异常，通过后再调 `increasePostPersonNum()`，两步之间无原子保证。  
**竞态**：两个请求都通过检查（nowPersonNum=1 < allPersonNum=2），然后各自 +1，最终 nowPersonNum=3 > allPersonNum=2。  
**影响文件**：`InternshipPostServiceImpl.checkPostCapacity()` 第 187~199 行

**修复方案**：把容量检查与 +1 合并为一条原子 SQL：
```sql
UPDATE main_internship_post 
SET now_person_num = now_person_num + 1 
WHERE id = ? AND now_person_num < all_person_num
```
影响行数 = 0 则说明已满，抛出"岗位已满"异常。CONC-01 与 CONC-02 合并处理。

---

### CONC-03 ★★★ currentVerifyTypeId 推进读-改-写非原子（✅ DB已完成，Java待做）
**现状**：`onVerifyProcessApproved()` 先读 currentVerifyTypeId，+1 后写回，再创建下一级审核记录，三步非原子。  
**竞态**：两个并发通过请求同时读到 currentVerifyTypeId=2，各自写入 3，各自创建一条下一级审核记录 → 重复。  
**影响文件**：
- `VerifyProcessServiceImpl.onVerifyProcessApproved()` 第 370~411 行
- `InternshipServiceImpl.auditProcessMultiLevelRelationBiz()` 第 2383~2405 行

**DB侧**：已为 `rel_title_student`、`rel_stu_internship_post`、`rel_process_internship`、`main_diary`、`main_sign` 添加 `version INT DEFAULT 0` 列。  
**Java侧待做**：给对应实体类加 `@Version private Integer version;`，在审核推进方法中捕获 `OptimisticLockException` 返回"请勿重复提交"。

---

### CONC-04 ★★★ 竞选题两位老师并发录取不同学生（与 BIZ-01 的交叉问题）
**现状**：两位老师同时审核同一题目下的两名学生并各自通过，两人都进入最终通过状态，BIZ-01 的级联拒绝也会互相覆盖对方状态。  
**竞态**：
```
老师A 通过学生X → 查同 titleId 其他申请 → 准备 NOTPASS 学生Y
老师B 同时通过学生Y → 查同 titleId 其他申请 → 准备 NOTPASS 学生X
最终：X 和 Y 同时被通过，互相把对方标为 NOTPASS（但 currentVerifyTypeId 已更新，状态混乱）
```
**修复方案**：在 BIZ-01 的级联逻辑入口加按 titleId 分段的应用层锁（`ConcurrentHashMap<Integer, ReentrantLock>`），确保同一 titleId 的最终通过操作串行执行：
1. 进入临界区后，再次确认该 titleId 下是否已有 isAllVerified=true 的学生
2. 若已有，则拒绝当前审核通过（抛出业务异常）
3. 若无，执行通过 + 级联 NOTPASS

---

### CONC-05 ★★ 限选题并发自动通过
**现状**：两个学生同时选同一 isLimit=1 的题目，`shouldAutoApproveLimitedTitleSelectionSubmit()` 各自判断"无人通过"，两人都被自动通过。  
**修复方案**：`markLimitedTitleSelectionAsFullyApproved()` 中加同 titleId 的应用层锁（复用 CONC-04 的锁表），进入后：
1. 查询该 titleId 下是否已有 isAllVerified=true 的 RelTitleStudent
2. 有则不执行自动通过，改为 NOTPASS 本条申请
3. 无则执行自动通过 + 级联 NOTPASS（BIZ-03）

---

### CONC-06 ★★ 首条审核记录 check-then-create 重复创建
**现状**：`ensureSubmitVerifyProcess()`（打卡）和 `ensureDiaryVerifyProcess()`（日志）都是先查是否存在 SUBMIT 记录，再决定是否创建；无原子保证。  
**竞态**：两个并发提交请求都查到"不存在"，各自创建，同一打卡/日志出现两条待审记录。  
**影响文件**：
- `MainSignServiceImpl.ensureSubmitVerifyProcess()` 第 41~47 行
- `DiaryServiceImpl.ensureDiaryVerifyProcess()` 第 127~132 行

**修复方案**：在数据库层为 `main_verify_process` 增加唯一约束（部分唯一，仅对 SUBMIT 状态生效较难实现），或改用数据库级幂等 INSERT：
```sql
INSERT IGNORE INTO main_verify_process (...) 
SELECT ... WHERE NOT EXISTS (SELECT 1 FROM main_verify_process WHERE relation_id=? AND table_name=? AND is_audit=0 AND is_deleted=0)
```
短期替代方案：方法入口加按 `(tableName + relationId)` 分段的应用层锁。

---

### CONC-07 ★★ 同学生重复选同一题（DB 无唯一约束）✅ DB已完成
**现状**：`RelTitleStudent` 没有 `(title_id, stu_id)` 的数据库唯一约束，并发两次选同一题会插入两条相同记录。  
**DB侧**：已添加虚拟列 `is_active` + 唯一索引 `uk_title_stu_active(title_id, stu_id, is_active)`（软删除记录不参与唯一校验）。清理了 id=83 的历史重复脏数据。  
**Java侧待做**：在 `DataListServiceImpl.editOneNode()` 对 `RelTitleStudent` 新增前加应用层去重检查，捕获 `DataIntegrityViolationException` 返回友好报错"您已报名过该题目"。

---

### CONC-08 ★★ 日志同期次重复创建（DB 无唯一约束）✅ DB已完成
**现状**：`MainDiary` 没有 `(relation_id, table_name, period_id)` 的唯一约束，并发两次提交同一期日志会插入两条记录。  
**DB侧**：已添加虚拟列 `is_active` + 唯一索引 `uk_diary_period_active(relation_id, table_name, period_id, is_active)`。清理了 2 条 relation_id=0 的测试垃圾数据。  
**Java侧待做**：在 `DiaryServiceImpl.submitDiary()` 捕获 `DataIntegrityViolationException` 返回友好报错。

---

## 四、实施进度

| 步骤 | 任务 | DB | Java | 说明 |
|------|------|----|----|------|
| 1 | **CONC-07 + CONC-08** 唯一约束 | ✅ | ⬜ 友好报错 | DDL 已完成，Java 侧捕获异常即可 |
| 2 | **CONC-01 + CONC-02** 岗位人数原子化 | — | ✅ | `MainInternshipPostDao` 新增两个 `@Modifying @Query` 原子方法；`InternshipPostServiceImpl` 和 `InternshipServiceImpl` 均已切换为原子调用 |
| 3 | **BIZ-01 + BIZ-02 + BIZ-03** 录取级联拒绝 | — | ⬜ | 核心新功能，需新增方法 |
| 4 | **CONC-04 + CONC-05** 录取并发锁 | — | ⬜ | 依赖步骤 3 完成后加锁保护 |
| 5 | **CONC-03** 乐观锁 @Version | ✅ | ✅ | 5个实体均已加 `@Version private Integer version` |
| 6 | **CONC-06** 打卡/日志审核幂等 | — | ✅ | `MainSignServiceImpl` 和 `DiaryServiceImpl` 均加了按 id 分段的 `ConcurrentHashMap` 应用层锁 |
| 7 | **BIZ-04** BACK 对 RelTitleStudent 特殊处理 | — | ⬜ | 小改动 |
| 8 | **BIZ-05** 批量确认被拒申请 | — | ⬜ | 待前后端联调后决定 |

---

## 五、需要新增/修改的 DAO 方法（供参考）

```java
// RelTitleStudentDao
List<RelTitleStudent> findByTitleIdAndIsDeletedFalse(Integer titleId);
List<RelTitleStudent> findByStuIdAndIsDeletedFalse(Integer stuId);

// MainVerifyProcessDao（已有 findByRelationIdAndTableNameAndIsDeletedFalse）
// 需要新增：
List<MainVerifyProcess> findByRelationIdInAndTableNameAndIsAuditInAndIsDeletedFalse(
    List<Integer> relationIds, String tableName, List<Integer> isAuditList);

// MainInternshipPostDao（新增原子更新）
@Modifying
@Query("UPDATE MainInternshipPost p SET p.nowPersonNum = p.nowPersonNum + 1 WHERE p.id = :id AND p.nowPersonNum < p.allPersonNum")
int incrementNowPersonNumIfNotFull(@Param("id") Integer id);

@Modifying
@Query("UPDATE MainInternshipPost p SET p.nowPersonNum = p.nowPersonNum - 1 WHERE p.id = :id AND p.nowPersonNum > 0")
int decrementNowPersonNum(@Param("id") Integer id);
```

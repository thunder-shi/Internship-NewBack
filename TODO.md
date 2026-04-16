# 代码质量问题清单

> 由代码质量扫描工具生成，日期：2026-04-15
> 共 27 个问题，全部已修复（2026-04-16）

---

## 一、潜在 Bug

- [x] **BUG-01** `UserServiceImpl.java` — `viewBaseUser` 未判 null → 已添加 null 检查
- [x] **BUG-02** `UserServiceImpl.java` — 无角色时 `substring(0, -1)` 越界 → 已改为 `deleteCharAt`
- [x] **BUG-03** `UserServiceImpl.java` — `departmentName` 错误从 `jobMap` 取值 → 已恢复 `departmentMap`
- [x] **BUG-04** `UserServiceImpl.java` — `userIdSet` 为空时 `substring` 越界 → 已添加空集合判断
- [x] **BUG-05** `ImportAndExportImpl.java` — 父专业查不到时 NPE → 已添加 null 检查
- [x] **BUG-06** `UserServiceImpl.java` — `roleIds` 为 null 时 `split()` NPE → 已添加 null 检查
- [x] **BUG-07** `EncryptUtil.java` — 密钥超 99 条时索引截取错误 → 已限制最大槽数 MAX_KEY_SLOTS=99

---

## 二、异常处理

- [x] **EXC-01** `UserServiceImpl.java` — `catch (Exception ignored){}` 吞异常 → 已改为 warn 日志
- [x] **EXC-02** `ImportAndExportImpl.java` — `FileInputStream` 未用 try-with-resources → 已修复
- [x] **EXC-03** `ImportAndExportImpl.java` — `OutputStream` 异常时未关闭 → 已改用 try-with-resources
- [x] **EXC-04** `UserServiceImpl.java` — 事务内 try-catch 屏蔽回滚 → `refreshPendingVerifyUsersByUser` 改为 REQUIRES_NEW 独立事务
- [x] **EXC-05** `UserServiceImpl.java` — 同 EXC-04，`saveUserRoles` 中相同模式 → 同上修复
- [x] **EXC-06** `ImportAndExportImpl.java` — Excel 写入异常被吞 → 已改为 rethrow

---

## 三、数据一致性

- [x] **DATA-01** `UserServiceImpl.java` — `roleSet` 为 null 时角色数据丢失 → 已添加 null 检查并抛出异常
- [x] **DATA-02** `VerifyProcessServiceImpl.java` — `activateProcess` 标注 `NOT_SUPPORTED` 导致无事务 → 已改为 `REQUIRED`
- [x] **DATA-03** `VerifyProcessServiceImpl.java` — 并发场景下可能覆盖最新状态 → `REQUIRES_NEW` 隔离刷新操作
- [x] **DATA-04** `ImportAndExportImpl.java` — 批量导入每行 catch+continue 形成脏数据 → 已改为 throw，确保全有或全无

---

## 四、安全问题

- [x] **SEC-01** `EncryptUtil.java` — 硬编码密钥 `"abcdefgabcdefg12"` → 已移除该字段（密钥由运行时随机生成）
- [x] **SEC-02** `UserServiceImpl.java` — 密码强度校验被注释 → 已恢复，并将 password 声明前移
- [x] **SEC-03** `ShiroConfig.java` — `/common/**` 全部匿名，写接口无需认证 → 仅 `/common/getKey` 匿名，其余需认证
- [x] **SEC-04** `EncryptUtil.java` — `allKeys` 为 `public static`，无界增长可 OOM → 已改为 `private static final`，并加 MAX_KEY_SLOTS=99 上限

---

## 五、性能问题（N+1 查询）

- [x] **PERF-01** `UserServiceImpl.java` — 每用户单独查角色 → 已改为批量查询后按 userId 分组
- [x] **PERF-02** `UserServiceImpl.java` — 每用户分别查角色关联和角色名称 → 已合并为两次批量查询
- [ ] **PERF-03** `VerifyProcessServiceImpl.java` — `GetVerifyUserId` 每次全校用户扫描 → 已有局部缓存（`verifyUserIdCache`），深度优化需重构，暂缓
- [x] **PERF-04** `InternshipServiceImpl.java` — 遍历岗位时每条单独查 `BasePostType` → 已改为批量查询后建 Map
- [x] **PERF-05** `VerifyProcessServiceImpl.java` — `pageSize` 硬编码 10000 截断 → 已改为分页循环获取全量数据

---

## 修复说明

除 **PERF-03** 外，其余 26 个问题均已修复。PERF-03 已有局部缓存机制，完整优化需重构 `GetVerifyUserId` 接口，留待后续迭代。

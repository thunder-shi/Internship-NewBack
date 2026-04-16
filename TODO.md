# 代码质量问题清单

> 由代码质量扫描工具生成，日期：2026-04-15
> 共 27 个问题，待有时间逐一修复。

---

## 一、潜在 Bug

- [ ] **BUG-01** `UserServiceImpl.java:93–96` — `viewBaseUser` 未判 null 直接调用方法，用户不存在时登录接口 500
- [ ] **BUG-02** `UserServiceImpl.java:496–500` — 用户无角色时 `role.substring(0, -1)` → `StringIndexOutOfBoundsException`
- [ ] **BUG-03** `UserServiceImpl.java:507–510` — `departmentName` 错误地从 `jobMap` 取值，部门名称始终显示为岗位名称
- [ ] **BUG-04** `UserServiceImpl.java:536–543` — `userIdSet` 为空时 `substring(0, -1)` 越界
- [ ] **BUG-05** `ImportAndExportImpl.java:611–614` — 父专业查不到时直接 `major.getId()` → NPE；后面的 `if (parentId == null)` 保护逻辑永远不执行
- [ ] **BUG-06** `UserServiceImpl.java:549` — `roleIds` 字段为 null 时直接 `.split(...)` → NPE
- [ ] **BUG-07** `EncryptUtil.java:127–131` — 密钥索引用 `substring(length-2)` 截取，key 超 99 条时解析错误

---

## 二、异常处理

- [ ] **EXC-01** `UserServiceImpl.java:100–118` — `catch (Exception ignored) {}` 吞掉查询实习类型的所有异常，掩盖真实错误
- [ ] **EXC-02** `ImportAndExportImpl.java:416–430` — `FileInputStream` 未用 try-with-resources，构造异常时句柄泄漏
- [ ] **EXC-03** `ImportAndExportImpl.java:442–453` — `OutputStream` 在写入异常时未关闭
- [ ] **EXC-04** `UserServiceImpl.java:444–451` — `@Transactional` 方法内 try-catch 吃掉异常，`refreshPendingVerifyUsers` 失败时事务不回滚，用户数据已提交但审核人未更新
- [ ] **EXC-05** `UserServiceImpl.java:653–658` — 同 EXC-04，`saveUserRoles` 中相同模式
- [ ] **EXC-06** `ImportAndExportImpl.java:286–291` — Excel 写入异常被吞，返回不完整文件给用户

---

## 三、数据一致性

- [ ] **DATA-01** `UserServiceImpl.java:353–376` — `roleSet` 为 null 时旧角色已删但新角色未写入，角色数据丢失
- [ ] **DATA-02** `VerifyProcessServiceImpl.java:297–341` — `activateProcess` 标注 `NOT_SUPPORTED`，方法内 DB 写操作无事务保护，失败不回滚
- [ ] **DATA-03** `VerifyProcessServiceImpl.java:185–295` — 全表扫描后逐条更新，并发场景下可能覆盖最新状态
- [ ] **DATA-04** `ImportAndExportImpl.java:634–820` — 批量导入每行 catch+continue，部分行失败不回滚，形成"部分导入"脏数据

---

## 四、安全问题

- [ ] **SEC-01** `EncryptUtil.java:28–29` — AES 密钥 `"abcdefgabcdefg12"` 硬编码在源码中，且使用不安全的 ECB 模式
- [ ] **SEC-02** `UserServiceImpl.java:240–243` — 密码强度校验代码被注释，允许任意弱密码注册
- [ ] **SEC-03** `ShiroConfig.java:135` — `/common/**` 设为匿名访问，但 `CommonController` 暴露了 `saveOneRecord`、`deleteRecords` 等写操作接口，未登录用户可直接操作数据库
- [ ] **SEC-04** `EncryptUtil.java:37–39` — `allKeys` 是 `public static ArrayList`，无界增长可 OOM，且敏感密钥暴露在 public 字段

---

## 五、性能问题（N+1 查询）

- [ ] **PERF-01** `UserServiceImpl.java:388–406` — 每个用户单独查角色，20 用户/页 = 21 次 DB 查询
- [ ] **PERF-02** `UserServiceImpl.java:478–501` — 每用户查两次 DB（角色关联 + 角色名称），40+ 次额外查询/页
- [ ] **PERF-03** `VerifyProcessServiceImpl.java:267–279` — `GetVerifyUserId` 每次全校用户扫描，多 `createUserId` 时缓存命中率极低
- [ ] **PERF-04** `InternshipServiceImpl.java:393–404` — 遍历岗位时每条单独查 `BasePostType`，应批量查询后建 Map
- [ ] **PERF-05** `VerifyProcessServiceImpl.java:498–501` — `pageSize` 硬编码 10000，超限时审核记录被截断，级别计算错误

---

## 优先修复建议

1. **SEC-03** — `/common/**` 写接口无需认证，严重安全漏洞
2. **BUG-03** — `departmentName` 显示错误，影响所有用户列表
3. **DATA-01** — 角色删除后 NPE 导致角色丢失
4. **EXC-04 / EXC-05** — 事务方法内 try-catch 屏蔽回滚，数据不一致
5. **BUG-02 / BUG-04** — 空集合 substring 越界，接口崩溃

# 实习项目管理系统 (Internship CMS) - 后端

Spring Boot 4.0 + Java 25 的实习管理系统后端，RESTful API 架构。

## 技术栈

Spring Boot 4.0 · Java 25 · Apache Shiro 2.0.6 · Spring Data JPA · MySQL · Redis(Lettuce，当前禁用自动配置) · MinIO 8.5.x · FastJson · Lombok · Hutool · Apache POI · SpringDoc OpenAPI

## 应用配置

- **端口**: 8111 | **数据库**: MySQL (internship) | **API文档**: http://localhost:8111/swagger-ui.html
- **JPA DDL**: update | **MinIO**: http://47.96.172.199:9000，bucket=internship

## 常用命令

```bash
./mvnw spring-boot:run                                              # 运行
mvn clean package -DskipTests                                       # 打包
java --enable-native-access=ALL-UNNAMED -jar target/newcms-0.0.1-SNAPSHOT.jar
mvn -Pdebug spring-boot:run                                         # 调试模式（端口5005）
```

## 关键文件

| 文件 | 说明 |
|------|------|
| `src/main/java/newcms/NewcmsApplication.java` | 入口 |
| `src/main/resources/application.properties` | 配置 |
| `src/main/java/newcms/config/ShiroConfig.java` | Shiro 安全配置 |
| `src/main/java/newcms/service/ICommonService.java` | 通用 CRUD 服务 |
| `src/main/java/newcms/controller/commonCtrl/CommonController.java` | 通用控制器 + MinIO 接口 |
| `src/main/java/newcms/config/MinIOConfig.java` | MinIO 配置 |
| `src/main/java/newcms/utils/MinIOUtils.java` | MinIO 工具类 |
| `src/sql/internship.sql` | 数据库备份 |

## 命名约定

- 实体: `MainInternship`, `BaseUser`（驼峰）
- DAO: `*Dao`，Service 接口: `I*Service`，实现: `*ServiceImpl`
- 视图实体: `View*`（如 `ViewMainInternship`）
- Controller: `*Controller`

## 目录结构

```
src/main/java/newcms/
├── annotation/      # @PathRestController, @Permissions
├── base/            # Base, BaseException, BaseResponse, Constant
├── config/          # CorsConfig, ShiroConfig, GlobalExceptionHandler, MinIOConfig
├── controller/      # commonCtrl/, systemManage/, userCtrl/, Diary/InternshipPost/InternshipProcess/MainLeave/MainSign/ImportAndExport Controller
├── entity/          # base/（6个基础实体）, db/（34个表实体 + 40个视图实体）
├── repository/      # base/（BaseDao, BaseTreeDao）, db/（73个DAO）
├── service/         # 12个 I*Service 接口 + impl/（12个实现）
└── utils/           # CollectionUtil, DateUtil, DaoClassUtil, EncodeUtil, EncryptUtil,
                     # FastJsonUtil, GeneralUtil, LogUtil, MinIOUtils, RedisUtil, TreeUtil
```

## 统一响应格式

```java
BaseResponse.ok(data)                          // {status: 200, ...}
throw BaseResponse.parameterInvalid.error("msg"); // 400
throw BaseResponse.unAuthorization.error("msg");  // 401
throw BaseResponse.moreInfoError.error("msg");    // 500
```

## 通用服务 (ICommonService)

通过表名动态操作任意表，无需为每个表写 Controller：

```java
getSomeRecords(tblName, searchKeys, regMap, sort, page, size)  // 条件查询（EQ/GT/LT/LIKE/RANGE）
saveOneRecord(tblName, json)       // 自动判断新增/更新（id 为空则 insert）
deleteRecordByDelflag(tblName, id) // 软删除
deleteSomeRecords(tblName, ids)    // 批量软删除
```

## 加密通信

敏感参数使用 AES-ECB 加密，密钥 5 分钟过期、用后即焚：
```java
EncryptUtil.getKeyWord(keyWord)  // 解密（用后销毁密钥）
```
前端先调 `/common/getKey` 获取密钥后加密参数。

## 自定义注解

```java
@PathRestController("path")                          // 等同于 @RestController + @RequestMapping
@Permissions(value = "user", c = true, r = true)    // CRUD 权限控制
```

## Shiro 过滤链

匿名访问：`/sign/login`, `/sign/logout`, `/common/getKey`, `/swagger-ui/**`, `/v3/api-docs/**`  
其他所有路径需认证（authc）。

## MinIO 文件上传限制

- 单次最多 5 个文件
- **日志(MainDiary)**：单文件 ≤ 50MB，总计 ≤ 100MB
- **其他场景**：单文件 ≤ 20MB，总计 ≤ 50MB
- 格式白名单：图片(jpg/png/gif/bmp/webp)、文档(pdf/doc/docx/xls/xlsx/ppt/pptx/txt)、压缩包(zip/rar/7z)、视频(mp4/avi/mkv)
- 同名文件（原始文件名相同）拒绝上传
- **文件访问权限**：预览/下载需同校权限校验（file owner schoolId == current user schoolId）；删除仅限文件上传者本人

---

## 关键业务规则

### nowPersonNum 生命周期

`MainInternshipPost.nowPersonNum` 仅在企业岗位审核全部通过时 +1：
- 审核通过 → `MainInternshipPostDao.incrementNowPersonNum(id)`
- **被作废的曾 PASS 记录（NO_VERIFY 自动通过场景）**：`cancelOtherStuPostsOnApproval` 内会按 `markRelationCancelled` 返回的 `wasApproved=true` 调 `decrementNowPersonNum` 扣回。
- 自主实习岗位（virtual post，allPersonNum=-1）不参与此计数。

### 系统自动作废的报名/申请保留可见

学生因"同项目一岗位"互斥被自动作废的记录**不软删**，由 `markRelationCancelled` 统一处理：
- `RelStuInternshipPost`、`SysOssFile`（附件）保留可见，学生看得到曾提交的内容；
- 旧 `MainVerifyProcess` 全部软删（清掉残留 PASS 状态，避免 pre-check 误判）；
- 追加一条 `isAudit=NOTPASS, verifyUserId='系统自动'` 的标记 MVP，`reason` 说明作废原因；
- 自主侧另删由该自主 PASS 触发生成的 `RelTeacherStudent` 占位（源已作废，导师占位无意义）。
- 触发场景与 reason：① 企业 PASS → 自主："该项目已有企业岗位审核通过，自主实习申请自动作废"；② 自主 PASS → 企业："您已通过自主实习申请，本企业岗位报名自动作废"；③ 企业之间互斥："您已选定其他企业岗位，本报名自动作废"；④ 岗位满员：`cancelPendingApplicationsIfPostFull` 用"该岗位已招满，本报名自动作废"。

### 审核完成判断

**不通过 isAudit 判断**，用 `currentVerifyTypeId > verifyTypeId` → 审核全部完成。

### ViewRelTitleTeacherStudent 注意

该视图中 `isAudit` 来自 `RelTitleTeacher`（校内导师审核），**不是**学生选题审核。  
判断学生选题是否完全通过应用 `ViewVerifyProcessRelTitleStudentMerge.isAllVerified`。

### 自主实习机制

- 校外实习项目新建时自动创建 **虚拟岗位**：`MainInternshipPost(code='SELF_INTERNSHIP', name='自主实习', allPersonNum=-1, companyId=null)`（幂等，`afterCommit` 独立事务触发，失败只记日志不影响主流程）。
- 学生端入口：`/internshipProcess/applySelfInternship`（独立接口，不走 `stuSelPost`）；同学生同项目下最多 1 条自主记录；SAVE/SUBMIT/PASS/BACK 拒绝；NOTPASS 重投 update-in-place（复用 id、覆盖 self_* 字段、清空旧 `MainVerifyProcess`、清空 `SysOssFile`）。
- **同项目一岗位（对称互斥）**：**申请期**（SUBMIT/SAVE/BACK/NOTPASS）自主与企业岗位可并存；**任一方 PASS** 即独占该项目，另一方所有记录（含 SUBMIT/SAVE 等）级联软删：
  - 企业 PASS → `cancelSelfInternshipOnEnterpriseApproval` 删自主 `RelStuInternshipPost` + `MainVerifyProcess` + `SysOssFile` + 自主触发生成的 `RelTeacherStudent` 及其审核。
  - 自主 PASS → `cancelOtherStuPostsOnApproval`（传自主 id）删所有企业岗位报名 + 其 `MainVerifyProcess`（内部自动跳过自主自身）。
  - 入口预检兜底：`applySelfInternship` 拒"已有企业 PASS"；`stuSelPost` 拒"已有企业 PASS"+"已有自主 PASS"。企业岗位之间仍互斥（同项目最多 1 个企业 PASS）。
- **级联豁免**：自主 PASS **不**触发 `incrementNowPersonNum` / `cancelPendingApplicationsIfPostFull`（虚拟岗位 allPersonNum=-1 无人数概念）；但仍触发 `ensureSeparateTutorAssignmentsAfterStuPostApproved`（生成导师分配占位）。
- 流程常量：`PROCESS_TYPE.EXTERNAL_STUDENT_SELF_DECLARATION`（code=17）；项目必须在 `RelProcessInternship` 里配了该流程才能申请，否则 `applySelfInternship` 抛 `当前项目未开通自主实习申请`。
- 附件：`SysOssFile.tableName='RelStuInternshipPost'`（PascalCase），`relationIds` = `RelStuInternshipPost.id`。

---

## 开发规则

1. **三层架构**: Controller → Service → Repository，严格遵循
2. **权限控制**: 使用 `@Permissions` 注解
3. **异常处理**: 抛出 `BaseException`，由 `GlobalExceptionHandler` 统一处理
4. **加密通信**: 敏感参数通过 `EncryptUtil.getKeyWord()` 解密
5. **日志记录**: 使用 `LogUtil.loggerRecord()` 记录关键操作
6. **SQL 文件**: `src/sql/` 下的文件**未经用户明确要求不得修改**
7. **软删除**: 删除操作使用 `deleteRecordByDelflag()`，不物理删除
8. **动态更新**: 实体使用 `@DynamicInsert` / `@DynamicUpdate`，只更新变更字段

---

## 详细参考文档

- 审核流程详解（状态码、推进逻辑、审核人计算）→ @.claude/docs/verify-flow.md
- 完整实体/视图列表 → @.claude/docs/entities.md
- 完整 API 接口列表 → @.claude/docs/api.md

---

## 行为准则

减少常见 LLM 编码错误的行为规范。与项目特定指令合并使用。

权衡：这些准则偏向谨慎而非速度。对于简单任务，自行判断。

### 1. 先思考再编码

不要假设。不要隐藏困惑。暴露权衡。

实施前：
- 明确陈述你的假设。如果不确定，提问。
- 如果存在多种解读，列出它们——不要默默选择一个。
- 如果存在更简单的方案，说出来。必要时反驳。
- 如果不清楚，停下来。说清楚什么困惑你，提问。

### 2. 简单优先

最小代码解决问题。不做投机性开发。

- 不做超出要求的功能。
- 不为单次使用的代码做抽象。
- 不做未被要求的"灵活性"或"可配置性"。
- 不为不可能的场景做错误处理。
- 如果写了 200 行而 50 行就够了，重写。
- 问自己："高级工程师会觉得这过度复杂了吗？" 如果是，简化。

### 3. 精确修改

只碰必须碰的。只清理自己造成的混乱。

编辑现有代码时：
- 不要"改进"相邻代码、注释或格式。
- 不要重构没有问题的东西。
- 匹配现有风格，即使你会写得不同。
- 如果发现无关的死代码，提出来——不要删除它。

当你的修改产生孤立代码时：
- 删除你的修改使其变得无用的 import/变量/函数。
- 不要删除先前存在的死代码，除非被要求。

检验标准：每一行修改都应直接追溯到用户的需求。

### 4. 目标驱动执行

定义成功标准。循环直到验证通过。

将任务转化为可验证的目标：
- "添加验证" → "为无效输入写测试，然后让它们通过"
- "修复 bug" → "写一个复现它的测试，然后让它通过"
- "重构 X" → "确保重构前后测试都通过"

对于多步任务，陈述简要计划：
1. [步骤] → 验证：[检查项]
2. [步骤] → 验证：[检查项]
3. [步骤] → 验证：[检查项]

强成功标准让你可以独立循环。弱标准（"让它工作"）需要不断澄清。

这些准则有效的标志：diff 中不必要的变更更少、因过度复杂而重写更少、澄清问题在实施前提出而非在犯错后提出。

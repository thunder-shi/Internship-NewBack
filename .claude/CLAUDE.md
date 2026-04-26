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
├── controller/      # commonCtrl/, systemManage/, userCtrl/, Diary/InternshipPost/InternshipProcess/MainSign Controller
├── entity/          # base/（6个基础实体）, db/（33个表实体 + 36个视图实体）
├── repository/      # base/（BaseDao, BaseTreeDao）, db/（68个DAO）
├── service/         # 11个 I*Service 接口 + impl/（11个实现）
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

- 单次最多 5 个文件，单文件 ≤ 20MB，总计 ≤ 50MB
- 格式白名单：图片(jpg/png/gif/bmp/webp)、文档(pdf/doc/docx/xls/xlsx/ppt/pptx/txt)、压缩包(zip/rar/7z)、视频(mp4/avi/mkv)
- 同名文件（原始文件名相同）拒绝上传

---

## 关键业务规则

### nowPersonNum 生命周期

`MainInternshipPost.nowPersonNum` **仅在审核全部通过时 +1**，选岗/取消/拒绝均不改变：
- 审核通过 → `MainInternshipPostDao.incrementNowPersonNum(id)`（无条件+1）
- 通过后级联：软删除该学生其余报名；若岗位已满则软删除该岗位其余待审核报名

### 审核完成判断

**不通过 isAudit 判断**，用 `currentVerifyTypeId > verifyTypeId` → 审核全部完成。

### ViewRelTitleTeacherStudent 注意

该视图中 `isAudit` 来自 `RelTitleTeacher`（校内导师审核），**不是**学生选题审核。  
判断学生选题是否完全通过应用 `ViewVerifyProcessRelTitleStudentMerge.isAllVerified`。

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

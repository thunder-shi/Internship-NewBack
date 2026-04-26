---
name: enable-minio
description: 启用 MinIO 文件存储功能（当前已完整启用，此技能用于记录配置详情或重新配置）
user_invocable: true
---

# MinIO 文件存储（已启用）

> MinIO 已完整启用，所有接口正常运行。本技能文档保留为配置参考，如需重新配置或修改参数可参考以下内容。

## 当前配置

| 配置项 | 值 |
|--------|-----|
| endpoint | `http://47.96.172.199:9000` |
| accessKey | `minioAdmin` |
| secretKey | `minioAdmin` |
| bucketName | `internship` |

配置文件：`src/main/resources/application.properties`（`minio.*` 配置块）

## 已完成的工作

### 1. Maven 依赖（pom.xml）

```xml
<dependency>
    <groupId>io.minio</groupId>
    <artifactId>minio</artifactId>
    <version>8.5.14</version>
</dependency>
```

### 2. 配置类（MinIOConfig.java）

路径：`src/main/java/newcms/config/MinIOConfig.java`

注入 `MinioClient` Bean，从 `application.properties` 读取 `minio.endpoint`、`minio.accessKey`、`minio.secretKey`。

### 3. 工具类（MinIOUtils.java）

路径：`src/main/java/newcms/utils/MinIOUtils.java`

主要方法：
- `upload(bucketName, files, type, relationId, tableName)` — 上传多个文件，含格式/大小/数量校验，返回 `List<SysOssFile>`
- `getObject(bucketName, ossPath)` — 获取文件输入流（用于预览）
- `presignedUrl(bucketName, ossPath, fileName, expireSeconds)` — 生成带 `response-content-disposition` 的 presigned URL（用于下载）
- `removeObjects(bucketName, ossPaths)` — 批量删除文件

### 4. 已激活接口（CommonController.java）

| 接口 | 方法 | 说明 |
|------|------|------|
| `POST /common/minio/upload` | POST (multipart) | 上传文件，返回 `List<SysOssFile>` |
| `GET /common/minio/file/{id}` | GET | 预览文件（字节流） |
| `GET /common/minio/download/{id}` | GET | 下载文件（返回 presigned URL，有效期 10 分钟） |
| `DELETE /common/minio/deleteFile` | DELETE | 批量删除文件（传 `ossFileIds` 数组）|

## 上传限制

- 单次最多 **5 个文件**
- 支持格式：图片（jpg/png/gif/bmp/webp）、文档（pdf/doc/docx/xls/xlsx/ppt/pptx/txt）、压缩包（zip/rar/7z）、视频（mp4/avi/mkv）
- 单文件 ≤ **20 MB**，总大小 ≤ **50 MB**
- 同名文件（原始文件名相同）会被拒绝（防重复）

## 如需修改配置

直接编辑 `src/main/resources/application.properties` 中的 `minio.*` 配置块。

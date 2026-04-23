package newcms.utils;

import com.alibaba.fastjson.JSONObject;
import io.minio.*;
import io.minio.messages.DeleteObject;
import jakarta.servlet.http.HttpServletResponse;
import newcms.base.BaseResponse;
import newcms.entity.db.SysOssFile;
import newcms.repository.db.SysOssFileDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
// endpoint 字段已移除，MinIO URL 不对外暴露，文件访问统一走后端代理
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class MinIOUtils {

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private SysOssFileDao sysOssFileDao;

    @Value("${minio.bucketName}")
    private String defaultBucket;

    private static final Set<String> ALLOWED_SUFFIXES = Set.of(
            "doc", "docx", "xls", "xlsx", "ppt", "pptx", "wps", "et", "dps", "wpt", "ett", "dpt",
            "pdf", "zip", "rar", "7z", "tar", "gz",
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "tiff", "tif",
            "mp4", "avi", "mov", "mkv", "wmv", "flv", "rmvb", "m4v", "webm"
    );
    private static final long MAX_SINGLE_SIZE = 20L * 1024 * 1024;  // 20 MB
    private static final long MAX_TOTAL_SIZE  = 50L * 1024 * 1024;  // 50 MB
    private static final int  MAX_FILE_COUNT  = 5;

    /**
     * 上传多个文件，保存元信息到 sys_oss_file。
     * 返回结果中的 "url" 为后端代理路径（/common/minio/file/{id}），不暴露 MinIO 地址。
     */
    public List<JSONObject> upload(MultipartFile[] files,
                                   Integer relationIds, String tableName,
                                   Integer userId) throws Exception {
        // ---- 数量校验 ----
        if (files == null || files.length == 0)
            throw BaseResponse.parameterInvalid.error("请选择要上传的文件");
        if (files.length > MAX_FILE_COUNT)
            throw BaseResponse.parameterInvalid.error("单次最多上传 " + MAX_FILE_COUNT + " 个文件");

        // ---- 格式 / 单文件大小 / 总大小校验 ----
        long totalSize = 0;
        for (MultipartFile file : files) {
            String name = file.getOriginalFilename();
            String suffix = (name != null && name.contains("."))
                    ? name.substring(name.lastIndexOf(".") + 1).toLowerCase() : "";
            if (!ALLOWED_SUFFIXES.contains(suffix))
                throw BaseResponse.parameterInvalid.error(
                        "不支持的文件格式：" + suffix + "，支持：文档/表格/演示/PDF/压缩包/图片/视频");
            if (file.getSize() > MAX_SINGLE_SIZE)
                throw BaseResponse.parameterInvalid.error(
                        "文件 [" + name + "] 超过单文件大小限制（最大 20 MB）");
            totalSize += file.getSize();
        }
        if (totalSize > MAX_TOTAL_SIZE)
            throw BaseResponse.parameterInvalid.error("文件总大小超过限制（最大 50 MB）");

        // ---- 重复文件检测（同 relationIds + tableName 下文件名已存在）----
        List<SysOssFile> existing = sysOssFileDao
                .findByRelationIdsAndTableNameAndIsDeletedFalse(relationIds, tableName);
        Set<String> existingNames = existing.stream()
                .map(SysOssFile::getFileName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (MultipartFile file : files) {
            String name = file.getOriginalFilename();
            if (name != null && existingNames.contains(name))
                throw BaseResponse.parameterInvalid.error(
                        "文件 [" + name + "] 已存在，请先删除旧文件再重新上传");
        }

        ensureBucketExists(defaultBucket);
        List<JSONObject> result = new ArrayList<>();

        for (MultipartFile file : files) {
            String originalName = file.getOriginalFilename();
            String suffix = (originalName != null && originalName.contains("."))
                    ? originalName.substring(originalName.lastIndexOf(".") + 1) : "";
            String ossPath = buildOssPath(suffix);
            String contentType = file.getContentType() != null
                    ? file.getContentType() : "application/octet-stream";

            try (InputStream in = file.getInputStream()) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(defaultBucket)
                        .object(ossPath)
                        .stream(in, file.getSize(), -1)
                        .contentType(contentType)
                        .build());
            }

            SysOssFile ossFile = new SysOssFile()
                    .setUserId(userId)
                    .setBucketName(defaultBucket)
                    .setFileName(originalName)
                    .setOssPath(ossPath)
                    .setSuffix(suffix)
                    .setFileSize(String.valueOf(file.getSize()))
                    .setRelationIds(relationIds)
                    .setTableName(tableName);
            ossFile = sysOssFileDao.save(ossFile);

            JSONObject item = FastJsonUtil.toJson(ossFile);
            // 返回后端代理地址，不暴露 MinIO 服务器 URL
            item.put("url", "/common/minio/file/" + ossFile.getId());
            result.add(item);
        }
        return result;
    }

    /**
     * 生成 presigned 预览链接（供 kkFileView 等预览服务调用），有效期 expireSeconds 秒。
     * 不附加 response-content-disposition / response-content-type 覆写参数，
     * 避免 MinIO 因签名不匹配返回 400。
     */
    public String presignedPreviewUrl(String bucketName, String ossPath, int expireSeconds) {
        try {
            return minioClient.getPresignedObjectUrl(
                    io.minio.GetPresignedObjectUrlArgs.builder()
                            .method(io.minio.http.Method.GET)
                            .bucket(bucketName)
                            .object(ossPath)
                            .expiry(expireSeconds, java.util.concurrent.TimeUnit.SECONDS)
                            .build());
        } catch (Exception e) {
            throw new RuntimeException("生成预览链接失败: " + e.getMessage(), e);
        }
    }

    /**
     * 生成 presigned 下载链接，有效期 expireSeconds 秒。
     * 附加 response-content-disposition 和 response-content-type，
     * 确保浏览器直连 MinIO 时强制下载并使用原始文件名。
     */
    public String presignedUrl(String bucketName, String ossPath,
                               String fileName, int expireSeconds) {
        try {
            String encoded = URLEncoder.encode(
                    fileName != null ? fileName : "file", StandardCharsets.UTF_8);
            return minioClient.getPresignedObjectUrl(
                    io.minio.GetPresignedObjectUrlArgs.builder()
                            .method(io.minio.http.Method.GET)
                            .bucket(bucketName)
                            .object(ossPath)
                            .expiry(expireSeconds, java.util.concurrent.TimeUnit.SECONDS)
                            .extraQueryParams(Map.of(
                                    "response-content-disposition",
                                    "attachment; filename=\"" + encoded + "\"",
                                    "response-content-type", "application/octet-stream"
                            ))
                            .build());
        } catch (Exception e) {
            throw new RuntimeException("生成下载链接失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将 MinIO 文件流式输出到 HTTP 响应。
     *
     * @param bucketName MinIO bucket
     * @param ossPath    文件在 MinIO 中的路径
     * @param fileName   原始文件名（用于 Content-Disposition）
     * @param inline     true=内联预览（img/pdf），false=强制下载
     */
    public void stream(String bucketName, String ossPath,
                       String fileName, boolean inline,
                       HttpServletResponse response) {
        try (InputStream in = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucketName).object(ossPath).build());
             OutputStream out = response.getOutputStream()) {
            String encoded = URLEncoder.encode(fileName != null ? fileName : "file",
                    StandardCharsets.UTF_8);
            String disposition = inline
                    ? "inline;filename=" + encoded
                    : "attachment;filename=" + encoded;
            response.setHeader("Content-Disposition", disposition);
            if (!inline) {
                response.setContentType("application/octet-stream");
            }
            in.transferTo(out);
        } catch (Exception e) {
            throw new RuntimeException("文件读取失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批量从 MinIO 删除文件对象。
     */
    public void removeObjects(String bucketName, List<String> ossPaths) {
        List<DeleteObject> objects = ossPaths.stream()
                .map(DeleteObject::new)
                .collect(Collectors.toList());
        minioClient.removeObjects(RemoveObjectsArgs.builder()
                .bucket(bucketName)
                .objects(objects)
                .build())
                .forEach(r -> {
                    try { r.get(); } catch (Exception ignored) {}
                });
    }

    /**
     * 生成 OSS 存储路径，格式：{year}/{month}/{day}/{UUID}.{suffix}
     * 月日无前导零，如 2026/4/4/abc123.pdf
     */
    private String buildOssPath(String suffix) {
        LocalDateTime now = LocalDateTime.now();
        String dir = now.getYear() + "/" + now.getMonthValue() + "/" + now.getDayOfMonth();
        String name = UUID.randomUUID().toString().replace("-", "");
        return suffix.isEmpty() ? dir + "/" + name : dir + "/" + name + "." + suffix;
    }

    /**
     * Bucket 不存在则自动创建。
     */
    private void ensureBucketExists(String bucketName) throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucketName).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }
}

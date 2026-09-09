package newcms.utils;

import com.alibaba.fastjson.JSONObject;
import io.minio.*;
import io.minio.messages.DeleteObject;
import newcms.base.BaseResponse;
import newcms.entity.db.SysOssFile;
import newcms.repository.db.SysOssFileDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.io.OutputStream;
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

    @Value("${minio.accessKey}")
    private String accessKey;

    @Value("${minio.secretKey}")
    private String secretKey;

    /** kkFileView 拉取文件时使用的 MinIO 地址；开发和 Docker 生产环境分别配置。 */
    @Value("${minio.kkfileview-endpoint:${minio.endpoint}}")
    private String kkFileViewEndpoint;

    private static final Map<String, String> MIME_BY_EXT = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"),
            Map.entry("gif", "image/gif"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("webp", "image/webp"),
            Map.entry("tif", "image/tiff"),
            Map.entry("tiff", "image/tiff"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("wps", "application/vnd.ms-works"),
            Map.entry("wpt", "application/vnd.ms-works"),
            Map.entry("et", "application/vnd.ms-excel"),
            Map.entry("ett", "application/vnd.ms-excel"),
            Map.entry("dps", "application/vnd.ms-powerpoint"),
            Map.entry("dpt", "application/vnd.ms-powerpoint"),
            Map.entry("rmvb", "application/vnd.rn-realmedia-vbr"),
            Map.entry("zip", "application/zip"),
            Map.entry("rar", "application/vnd.rar"),
            Map.entry("7z", "application/x-7z-compressed"),
            Map.entry("tar", "application/x-tar"),
            Map.entry("gz", "application/gzip"),
            Map.entry("mp4", "video/mp4"),
            Map.entry("avi", "video/x-msvideo"),
            Map.entry("mov", "video/quicktime"),
            Map.entry("mkv", "video/x-matroska"),
            Map.entry("wmv", "video/x-ms-wmv"),
            Map.entry("flv", "video/x-flv"),
            Map.entry("webm", "video/webm"),
            Map.entry("m4v", "video/x-m4v")
    );

    private static final Set<String> ALLOWED_SUFFIXES = Set.of(
            "doc", "docx", "xls", "xlsx", "ppt", "pptx", "wps", "et", "dps", "wpt", "ett", "dpt",
            "pdf", "zip", "rar", "7z", "tar", "gz",
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "tiff", "tif",
            "mp4", "avi", "mov", "mkv", "wmv", "flv", "rmvb", "m4v", "webm"
    );
    private static final long MAX_SINGLE_SIZE       = 20L * 1024 * 1024;   // 20 MB（默认）
    private static final long MAX_TOTAL_SIZE        = 50L * 1024 * 1024;   // 50 MB（默认）
    private static final long DIARY_MAX_SINGLE_SIZE = 50L * 1024 * 1024;   // 50 MB（日志）
    private static final long DIARY_MAX_TOTAL_SIZE  = 100L * 1024 * 1024;  // 100 MB（日志）
    private static final int  MAX_FILE_COUNT        = 5;

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
        boolean isDiary = "MainDiary".equals(tableName);
        long singleLimit = isDiary ? DIARY_MAX_SINGLE_SIZE : MAX_SINGLE_SIZE;
        long totalLimit  = isDiary ? DIARY_MAX_TOTAL_SIZE  : MAX_TOTAL_SIZE;
        long totalSize = 0;
        for (MultipartFile file : files) {
            String name = file.getOriginalFilename();
            String suffix = (name != null && name.contains("."))
                    ? name.substring(name.lastIndexOf(".") + 1).toLowerCase() : "";
            if (!ALLOWED_SUFFIXES.contains(suffix))
                throw BaseResponse.parameterInvalid.error(
                        "不支持的文件格式：" + suffix + "，支持：文档/表格/演示/PDF/压缩包/图片/视频");
            if (file.getSize() > singleLimit)
                throw BaseResponse.parameterInvalid.error(
                        "文件 [" + name + "] 超过单文件大小限制（最大 " + (singleLimit / 1024 / 1024) + " MB）");
            totalSize += file.getSize();
        }
        if (totalSize > totalLimit)
            throw BaseResponse.parameterInvalid.error("文件总大小超过限制（最大 " + (totalLimit / 1024 / 1024) + " MB）");

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
     * 用后端自己的 MinIO endpoint 签 GET URL（Coze 等外网调用）。
     * 不要拿去给 kkFileView：生产 kkFileView 只能访问 docker 内网 minio:9000。
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
     * 生成 kkFileView 拉取文件所需的预签名 GET 地址。
     * 开发环境签公网 MinIO 地址，Docker 生产环境签容器网络内的 MinIO 地址。
     */
    public String presignedKkFileViewUrl(String bucketName, String ossPath, int expireSeconds) {
        try {
            return MinioClient.builder()
                    .endpoint(kkFileViewEndpoint.trim())
                    .credentials(accessKey, secretKey)
                    .region("us-east-1")
                    .build()
                    .getPresignedObjectUrl(
                            GetPresignedObjectUrlArgs.builder()
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
     * 从 MinIO 读取文件字节。
     */
    public byte[] readBytes(String bucketName, String ossPath) {
        try (InputStream in = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucketName).object(ossPath).build())) {
            return in.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("MinIO 文件读取失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将 MinIO 文件流式输出到 HTTP 响应（不包 JSON）。
     *
     * @param ossFile  文件元信息（扩展名决定 MIME，不使用 octet-stream）
     * @param inline   true=预览（Content-Disposition: inline），false=下载（attachment）
     * @param response HTTP 响应
     */
    public void stream(SysOssFile ossFile, boolean inline, HttpServletResponse response) {
        String mime = resolveMime(ossFile.getFileName(), ossFile.getSuffix(), ossFile.getOssPath());
        String disposition = contentDisposition(inline, ossFile.getFileName());
        try (InputStream in = minioClient.getObject(
                GetObjectArgs.builder().bucket(ossFile.getBucketName()).object(ossFile.getOssPath()).build())) {
            applyBinaryHeaders(response, mime, disposition, ossFile.getFileSize());
            OutputStream out = response.getOutputStream();
            in.transferTo(out);
            out.flush();
        } catch (Exception e) {
            throw new RuntimeException("文件读取失败: " + e.getMessage(), e);
        }
    }

    static void applyBinaryHeaders(HttpServletResponse response, String mime,
                                   String disposition, String fileSize) {
        // 先清掉 Spring/Tomcat 默认的 UTF-8，否则 Content-Type 会变成 application/pdf;charset=UTF-8
        response.setCharacterEncoding((String) null);
        response.setHeader("Content-Type", mime);
        if (response.getContentType() != null && response.getContentType().toLowerCase(Locale.ROOT).contains("charset")) {
            response.setCharacterEncoding((String) null);
            response.setHeader("Content-Type", mime);
        }
        response.setHeader("Content-Disposition", disposition);
        Long size = parseFileSize(fileSize);
        if (size != null) {
            response.setContentLengthLong(size);
        }
    }

    static String resolveMime(String fileName, String suffix, String ossPath) {
        String ext = firstExt(suffix, fileName, ossPath);
        String mime = MIME_BY_EXT.get(ext);
        if (mime == null || mime.isBlank()) {
            throw BaseResponse.parameterInvalid.error("无法识别文件类型，无法预览/下载");
        }
        return mime;
    }

    static String contentDisposition(boolean inline, String fileName) {
        String raw = (fileName == null || fileName.isBlank()) ? "file" : fileName;
        String sanitized = raw.replace("\"", "").replace("\r", "").replace("\n", "");
        ContentDisposition.Builder builder = inline
                ? ContentDisposition.inline()
                : ContentDisposition.attachment();
        return builder.filename(sanitized, StandardCharsets.UTF_8).build().toString();
    }

    private static String firstExt(String suffix, String fileName, String ossPath) {
        if (suffix != null && !suffix.isBlank()) {
            String ext = suffix.trim().toLowerCase(Locale.ROOT);
            if (ext.startsWith(".")) {
                ext = ext.substring(1);
            }
            if (!ext.isEmpty() && ext.indexOf('/') < 0 && ext.indexOf('\\') < 0 && ext.indexOf('.') < 0) {
                return ext;
            }
        }
        String fromName = dottedExtension(fileName);
        if (!fromName.isEmpty()) {
            return fromName;
        }
        return dottedExtension(ossPath);
    }

    private static String dottedExtension(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String name = value;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int query = name.indexOf('?');
        if (query >= 0) {
            name = name.substring(0, query);
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static Long parseFileSize(String fileSize) {
        if (fileSize == null || fileSize.isBlank()) {
            return null;
        }
        try {
            long size = Long.parseLong(fileSize.trim());
            return size >= 0 ? size : null;
        } catch (NumberFormatException ignored) {
            return null;
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

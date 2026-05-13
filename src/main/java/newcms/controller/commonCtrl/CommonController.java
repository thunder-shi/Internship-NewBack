package newcms.controller.commonCtrl;


import newcms.annotation.PathRestController;
import newcms.base.Base;
import newcms.base.BaseResponse;
import newcms.base.Constant;
import newcms.entity.db.SysOssFile;
import newcms.repository.db.SysOssFileDao;
import newcms.service.ICommonService;
import newcms.service.IDataListService;
import newcms.service.IDataTreeService;
import newcms.service.IEnterpriseInfoService;
import newcms.utils.EncryptUtil;
import newcms.utils.FastJsonUtil;
import newcms.utils.MinIOUtils;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 查询：@getOneRecordById，@getRecordsByID，@getSomeRecords
 * 保存：@saveOneRecord，@saveSomeRecords
 * 新增：addOneRecord，addRecords
 * 删除：@deleteRecordByDelflag，@deleteRecordsByDelflag，@deleteRecords(这个是真的删除)
 */
@PathRestController(path = "common")
public class CommonController extends Base {
    @Resource
    protected EncryptUtil encryptUtil;
    @Resource
    protected ICommonService iCommonService;
    @Resource
    protected IDataTreeService iDataTreeService;
    @Resource
    protected IDataListService iDataListService;
    @Resource
    private MinIOUtils minIOUtils;

    @Resource
    private SysOssFileDao sysOssFileDao;
    @Resource
    private IEnterpriseInfoService enterpriseInfoService;


    public String getRandomString(int length){
        String str="abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random=new Random();
        StringBuilder sb=new StringBuilder();
        for(int i=0;i<length;i++){
            int number = random.nextInt(62);
            sb.append(str.charAt(number));
        }
        return sb.toString();
    }


    @PostMapping(path = "getKey" )
    public Object getKey() {
        String key = getRandomString(7);
        JSONObject val = encryptUtil.setAllKeys(key);
        return BaseResponse.ok(val);
    }



     /**
     * 编号查询
     * @param tblName
     * @param id
     * @param delFlag   null：全部    0: 未逻辑删除的   else:报参数异常
     * @return
     */
    @GetMapping(path = "getRecordsById/{tblName}" )
    public Object getRecordsById(@PathVariable String tblName, @RequestParam Set<Integer> id, Boolean delFlag) {
        return BaseResponse.ok(iCommonService.getRecordsByIds(tblName, id, delFlag));
    }
    /**
     * @param tblName
     * @param json
     * @return
     */
    @PostMapping(path = "getSomeRecords/{tblName}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object getSomeRecords(@PathVariable String tblName, @RequestBody JSONObject json) {
        String sort = json.getString("sort");
        String order = json.getString("order");
        Sort sorts;
        if (ObjectUtils.isEmpty(sort) || ObjectUtils.isEmpty(order)) {
            sorts = Sort.unsorted();
        } else {
            List<Sort.Order> orders = new ArrayList<>();
            String[] sortArr = sort.split(",", -1);
            String[] orderArr = order.split(",", -1);
            if (sortArr.length != orderArr.length) {
                throw BaseResponse.parameterInvalid.error();
            }
            for (int i = 0; i < sortArr.length; i++) {
                String sortField = sortArr[i];
                String orderField = orderArr[i];
                if (sortField == null || orderField == null || sortField.isEmpty() || orderField.isEmpty()) {
                    continue;
                }
                if (Sort.Direction.ASC.name().equals(orderField)) {
                    orders.add(Sort.Order.asc(sortField));
                } else if (Sort.Direction.DESC.name().equals(orderField)) {
                    orders.add(Sort.Order.desc(sortField));
                } else {
                    throw BaseResponse.parameterInvalid.error();
                }
            }
            sorts = Sort.by(orders);
        }
        normalizeStudentInternshipTerminationCandidateSearch(tblName, json, null);
        return BaseResponse.ok(iCommonService.getSomeRecords(tblName, json, null, sorts, json.getInteger("page"), json.getInteger("size")));
    }

    protected void normalizeStudentInternshipTerminationCandidateSearch(String tblName, JSONObject searchKeys, Map<String, String> regMap) {
        if (!"ViewStudentInternshipTerminationCandidate".equals(tblName) || searchKeys == null) {
            return;
        }
        normalizeTerminationCandidateInternshipMode(searchKeys, regMap);
        normalizeTerminationCandidateStatus(searchKeys, regMap);
        searchKeys.put("studentId", Base.getLoginUserId());
        if (regMap != null) {
            regMap.remove("studentId");
        }
    }

    private void normalizeTerminationCandidateInternshipMode(JSONObject searchKeys, Map<String, String> regMap) {
        Object raw = firstPresent(searchKeys, "internshipMode", "internshipType", "type", "intTypeId", "relationTable");
        removeSearchAliases(searchKeys, regMap, "internshipType", "type", "intTypeId");
        if (raw == null) {
            return;
        }
        String mode = normalizeTerminationInternshipMode(raw);
        if (mode == null) {
            searchKeys.remove("internshipMode");
            searchKeys.remove("relationTable");
            removeSearchAliases(searchKeys, regMap, "internshipMode", "relationTable");
            return;
        }
        searchKeys.put("internshipMode", mode);
        searchKeys.remove("relationTable");
        if (regMap != null) {
            regMap.remove("internshipMode");
            regMap.remove("relationTable");
        }
    }

    private void normalizeTerminationCandidateStatus(JSONObject searchKeys, Map<String, String> regMap) {
        Object raw = firstPresent(searchKeys, "internshipStatus", "relationStatus", "status", "terminationStatus");
        removeSearchAliases(searchKeys, regMap, "relationStatus", "status", "terminationStatus");
        if (raw == null) {
            return;
        }
        Integer status = normalizeTerminationInternshipStatus(raw);
        if (status == null) {
            searchKeys.remove("internshipStatus");
            if (regMap != null) {
                regMap.remove("internshipStatus");
            }
            return;
        }
        searchKeys.put("internshipStatus", status);
        if (regMap != null) {
            regMap.remove("internshipStatus");
        }
    }

    private Object firstPresent(JSONObject json, String... keys) {
        for (String key : keys) {
            Object value = json.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return value;
            }
        }
        return null;
    }

    private void removeSearchAliases(JSONObject searchKeys, Map<String, String> regMap, String... keys) {
        for (String key : keys) {
            searchKeys.remove(key);
            if (regMap != null) {
                regMap.remove(key);
            }
        }
    }

    private String normalizeTerminationInternshipMode(Object raw) {
        String value = String.valueOf(raw).trim();
        if (value.isBlank() || "全部".equals(value) || "全部类型".equals(value) || "ALL".equalsIgnoreCase(value)) {
            return null;
        }
        String normalized = value.replace("_", "").replace("-", "").toUpperCase();
        if ("2".equals(value)
                || "EXTERNAL".equals(normalized)
                || "OUT".equals(normalized)
                || "OUTSCHOOL".equals(normalized)
                || "RELSTUINTERNSHIPPOST".equals(normalized)
                || value.contains("校外")) {
            return "EXTERNAL";
        }
        if ("1".equals(value)
                || "INTERNAL".equals(normalized)
                || "IN".equals(normalized)
                || "INSCHOOL".equals(normalized)
                || "RELTITLESTUDENT".equals(normalized)
                || value.contains("校内")) {
            return "INTERNAL";
        }
        return value;
    }

    private Integer normalizeTerminationInternshipStatus(Object raw) {
        String value = String.valueOf(raw).trim();
        if (value.isBlank() || "全部".equals(value) || "全部状态".equals(value) || "ALL".equalsIgnoreCase(value)) {
            return null;
        }
        String normalized = value.replace("_", "").replace("-", "").toUpperCase();
        if ("TERMINATING".equals(normalized)
                || "PENDING".equals(normalized)
                || value.contains("审核中")
                || value.contains("终止审核")) {
            return Constant.INTERNSHIP_RELATION_STATUS.TERMINATING;
        }
        if ("TERMINATED".equals(normalized)
                || value.contains("已终止")
                || value.contains("终止完成")) {
            return Constant.INTERNSHIP_RELATION_STATUS.TERMINATED;
        }
        if ("ACTIVE".equals(normalized) || "NORMAL".equals(normalized) || value.contains("正常")) {
            return Constant.INTERNSHIP_RELATION_STATUS.ACTIVE;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @PutMapping(path = "saveOneRecord", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object saveOneRecord(@RequestParam String tblName, @RequestBody JSONObject json) {
        return BaseResponse.ok(iCommonService.saveOneRecord(tblName, json));
    }

    @PutMapping(path = "saveSomeRecords", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object saveSomeRecords(@RequestParam String tblName, @RequestBody JSONArray array) {
        return BaseResponse.ok(iCommonService.saveSomeRecords(tblName, array));
    }

    @DeleteMapping(path = "deleteRecords")
    public Object deleteRecords(@RequestParam String tblName, @RequestParam Integer id) {
        return BaseResponse.ok(iCommonService.deleteRecord(tblName, id));
    }

    @DeleteMapping(path = "deleteRecordByDelflag")
    public Object deleteRecordByDelflag(@RequestParam String tblName, @RequestParam Integer id) {
        return BaseResponse.ok(iCommonService.deleteRecordByDelflag(tblName, id));
    }

    @DeleteMapping(path = "deleteRecordsByDelflag")
    public Object deleteRecordsByDelflag(@RequestParam String tblName) {
        return BaseResponse.ok(iCommonService.deleteRecordsByDelflag(tblName));
    }

    /**
     * 上传文件到 MinIO，保存元信息到 sys_oss_file。
     * 参数：files（multipart），relationIds（关联业务记录ID），tableName（关联表名，如 MainDiary）
     */
    @PostMapping(value = "/minio/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object miniUpload(@RequestParam MultipartFile[] files,
                             @RequestParam Integer relationIds,
                             @RequestParam String tableName) {
        try {
            return BaseResponse.ok(minIOUtils.upload(files, relationIds, tableName, getLoginUserId()));
        } catch (Exception ex) {
            logger.error("MinIO 上传失败", ex);
            throw BaseResponse.moreInfoError.error("文件上传失败: " + ex.getMessage());
        }
    }

    /**
     * 文件内联预览（图片、PDF 等），可作为 <img src> 或 <embed src> 的地址。
     * GET /common/minio/file/{id}
     */
    @GetMapping(value = "/minio/file/{id}")
    public void previewFile(@PathVariable Integer id, HttpServletResponse response) {
        SysOssFile ossFile = sysOssFileDao.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> BaseResponse.parameterInvalid.error("文件不存在"));
        checkFileReadAccess(ossFile);
        minIOUtils.stream(ossFile.getBucketName(), ossFile.getOssPath(),
                ossFile.getFileName(), true, response);
    }

    /**
     * 获取文件预览链接（presigned URL，有效期 10 分钟，不含 Content-Disposition 覆写参数）。
     * 供 kkFileView 等预览服务调用，避免 MinIO 因签名不匹配返回 400。
     * GET /common/minio/preview/{id}
     */
    @GetMapping(value = "/minio/preview/{id}")
    public Object previewUrl(@PathVariable Integer id) {
        SysOssFile ossFile = sysOssFileDao.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> BaseResponse.parameterInvalid.error("文件不存在"));
        checkFileReadAccess(ossFile);
        String url = minIOUtils.presignedPreviewUrl(ossFile.getBucketName(), ossFile.getOssPath(), 600);
        return BaseResponse.ok(url);
    }

    /**
     * 获取文件下载链接（presigned URL，有效期 10 分钟）。
     * GET /common/minio/download/{id}
     */
    @GetMapping(value = "/minio/download/{id}")
    public Object downloadFile(@PathVariable Integer id) {
        SysOssFile ossFile = sysOssFileDao.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> BaseResponse.parameterInvalid.error("文件不存在"));
        checkFileReadAccess(ossFile);
        String url = minIOUtils.presignedUrl(ossFile.getBucketName(), ossFile.getOssPath(),
                ossFile.getFileName(), 600);
        return BaseResponse.ok(url);
    }

    /**
     * 批量删除文件（MinIO 对象 + 数据库软删除），传 ossFileIds 数组。
     * 只允许删除属于当前登录用户的文件。
     */
    @DeleteMapping(value = "/minio/deleteFile")
    public Object deleteMinioByIds(@RequestParam Integer[] ossFileIds) {
        List<Integer> ids = Arrays.asList(ossFileIds);
        Integer currentUserId = getLoginUserId();
        List<SysOssFile> ossFiles = sysOssFileDao.getByIdInAndIsDeletedFalse(ids);
        boolean hasUnowned = ossFiles.stream()
                .anyMatch(f -> !currentUserId.equals(f.getUserId()));
        if (hasUnowned) {
            throw BaseResponse.unAuthorization.error("只能删除自己上传的文件");
        }
        List<String> ossPaths = ossFiles.stream()
                .filter(f -> f.getOssPath() != null)
                .map(SysOssFile::getOssPath)
                .collect(Collectors.toList());
        if (!ObjectUtils.isEmpty(ossPaths)) {
            minIOUtils.removeObjects(Constant.BUCKET_NAME, ossPaths);
        }
        sysOssFileDao.updateByIds(ids);
        return BaseResponse.ok(null);
    }

    /**
     * 文件读取权限校验：文件上传者与当前用户必须属于同一学校。
     * 允许同校跨角色访问（教师查看学生文件等），拒绝跨校访问。
     */
    private void checkFileReadAccess(SysOssFile ossFile) {
        if (ossFile != null
                && "MainEnterpriseInfo".equals(ossFile.getTableName())
                && ossFile.getRelationIds() != null) {
            if (!enterpriseInfoService.canAccessAttachment(getLoginUserId(), ossFile.getRelationIds())) {
                throw BaseResponse.unAuthorization.error("鏃犳潈璁块棶姝ゆ枃浠?");
            }
            return;
        }
        Integer fileOwnerSchoolId = getSchoolIdForUser(ossFile.getUserId());
        Integer currentUserSchoolId = getSchoolIdForUser(getLoginUserId());
        if (fileOwnerSchoolId != null && !fileOwnerSchoolId.equals(currentUserSchoolId)) {
            throw BaseResponse.unAuthorization.error("无权访问此文件");
        }
    }

    private Integer getSchoolIdForUser(Integer userId) {
        if (userId == null) return null;
        Object userObj = iCommonService.getOneRecordById("ViewBaseUser", userId);
        if (userObj == null) return null;
        return FastJsonUtil.toJson(userObj).getInteger("schoolId");
    }
}

package newcms.controller.commonCtrl;


import newcms.annotation.PathRestController;
import newcms.base.Base;
import newcms.base.BaseResponse;
// import newcms.base.Constant;
// import newcms.entity.db.SysOssFile;
import newcms.service.ICommonService;
import newcms.service.IDataListService;
import newcms.service.IDataTreeService;
import newcms.utils.EncryptUtil;
// import newcms.utils.FileUtil;
// import newcms.utils.MinIOUtils;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;
// import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
// import jakarta.servlet.http.HttpServletResponse;
import java.util.*;
// import java.util.stream.Collectors;

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
    // @Resource
    // private MinIOUtils minIOUtils;


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
        return BaseResponse.ok(iCommonService.getSomeRecords(tblName, json, null, sorts, json.getInteger("page"), json.getInteger("size")));
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

//     @PostMapping(value = "/minio/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//     public Object miniUpload(@RequestParam MultipartFile[] files, @RequestParam Integer userId, @RequestParam Integer relId, @RequestParam Integer type) {
//         List<Object> resList = new ArrayList<>();
//         try {
//             Object fileInfo = minIOUtils.upload(Constant.BUCKET_NAME, files, type, relId);
//             resList.add(fileInfo);
// //            for (MultipartFile file : files) {
// //                Object fileInfo = minIOUtils.upload(Constant.BUCKET_NAME, file, type, relId);
// //                resList.add(fileInfo);
// //            }
//         } catch (Exception ex) {
//             logger.error("error", ex);
//         }
//         return BaseResponse.ok(resList);
//     }

    // @PostMapping(value = "/minio/downloadFile", consumes = MediaType.APPLICATION_JSON_VALUE)
    // public Object downloadFile(@RequestBody JSONObject requestJson, HttpServletResponse response) {
    //     Integer id = requestJson.getInteger("id");
    //     SysOssFile ossFile = sysOssFileDao.getByIdAndIsDeletedFalse(id);
    //     minIOUtils.download(ossFile.getBucketName(), ossFile.getOssPath(), ossFile.getName(), response);
    //     return BaseResponse.ok;
    // }


    // @DeleteMapping(value = "/minio/deleteFile")
    // public Object deleteMinioByIds(@RequestParam Integer[] ossFileIds) {
    //     List<SysOssFile> ossFiles = sysOssFileDao.findByIdIn(Arrays.asList(ossFileIds));
    //     List<String> ossPaths = ossFiles.stream().map(x -> x.getOssPath()).collect(Collectors.toList());
    //     if (!ObjectUtils.isEmpty(ossPaths)) {
    //         minIOUtils.removeObjectsResult(Constant.BUCKET_NAME, ossPaths);
    //     }
    //     sysOssFileDao.updateByIds(Arrays.asList(ossFileIds));
    //     return BaseResponse.ok;
    // }
}

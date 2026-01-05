package newcms.controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import newcms.annotation.PathRestController;
import newcms.base.BaseResponse;
import newcms.service.IImportAndExportService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@Tag(name = "导入导出")
@PathRestController("importAndExport")
public class ImportAndExportController {

    @Resource
    private IImportAndExportService iImportAndExportService;

    /**
     * MultipartFile转File
     */
    private File multipartFileToFile(MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            return null;
        }
        try {
            String originalFilename = multipartFile.getOriginalFilename();
            String suffix = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".tmp";
            File tempFile = File.createTempFile("upload_", suffix);
            multipartFile.transferTo(tempFile);
            tempFile.deleteOnExit();
            return tempFile;
        } catch (IOException e) {
            throw BaseResponse.moreInfoError.error("文件转换失败: " + e.getMessage());
        }
    }

    @Operation(summary = "导入信息（测试）", description = "导入测试")
    @PostMapping(value = "importProduct/{type}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object importProduct(MultipartFile file, @PathVariable Integer type) {
        //用户
//        if(type == 1){
//            return BaseResponse.ok(iImportAndExportService.importUsers(multipartFileToFile(file)));
//        }
        return BaseResponse.ok;
    }

    @Operation(summary = "导入", description = "各页面信息导入")
    @PostMapping(value = "importInfo")
    public Object importInfo(MultipartFile file, @RequestParam String keyWord, @RequestParam(required = false) Boolean type, @RequestParam(required = false) Integer userId) {
        switch (keyWord) {
            case "BaseMajor":
                return BaseResponse.ok(iImportAndExportService.importBaseMajor(multipartFileToFile(file)));
            case "BaseUser":
                return BaseResponse.ok(iImportAndExportService.importBaseUser(multipartFileToFile(file)));
            default:
                throw BaseResponse.moreInfoError.error("keyWord异常");
        }
    }

    @Operation(summary = "导出", description = "各页面信息导出")
    @PostMapping(value = "exportInfo")
    public void exportInfo(@RequestBody JSONObject requestJson) {
        String keyWords = requestJson.getString("keyWords");
        JSONArray nodes = requestJson.getJSONArray("nodes");
        JSONArray allTableColumns = requestJson.getJSONArray("allTableColumns");
        JSONObject searchWords = requestJson.getJSONObject("searchWords");
        iImportAndExportService.exportInfo(keyWords, nodes, allTableColumns, searchWords);
    }

    @Operation(summary = "模版下载")
    @PostMapping(value = "/template")
    public void template(@RequestBody JSONObject object) {
        String keyWords = object.getString("keyWords");
        iImportAndExportService.downTemplate(keyWords);
    }
}

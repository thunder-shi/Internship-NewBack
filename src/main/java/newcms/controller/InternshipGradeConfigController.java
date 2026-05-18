package newcms.controller;

import com.alibaba.fastjson.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import newcms.annotation.PathRestController;
import newcms.base.BaseResponse;
import newcms.service.IInternshipGradeConfigService;
import newcms.utils.LogUtil;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "实习评分配置")
@PathRestController("internshipGradeConfig")
public class InternshipGradeConfigController {

    @Resource
    private IInternshipGradeConfigService gradeConfigService;

    @Operation(summary = "列出某实习项目下某业务表的全部评分项")
    @PostMapping(value = "/list", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object list(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("internshipGradeConfig.list", requestJson);
        JSONObject node = requireNode(requestJson);
        Integer internshipId = node.getInteger("internshipId");
        String sourceTable = node.getString("sourceTable");
        return BaseResponse.ok(gradeConfigService.list(internshipId, sourceTable));
    }

    @Operation(summary = "新增/编辑评分项")
    @PostMapping(value = "/save", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object save(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("internshipGradeConfig.save", requestJson);
        JSONObject node = requireNode(requestJson);
        return BaseResponse.ok(gradeConfigService.save(node));
    }

    @Operation(summary = "批量替换式保存评分项（事务内：软删旧的、插入新的、校验 SUM=100）")
    @PostMapping(value = "/saveBatch", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object saveBatch(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("internshipGradeConfig.saveBatch", requestJson);
        JSONObject node = requireNode(requestJson);
        return BaseResponse.ok(gradeConfigService.saveBatch(node));
    }

    @Operation(summary = "软删除评分项")
    @PostMapping(value = "/delete", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object delete(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("internshipGradeConfig.delete", requestJson);
        JSONObject node = requireNode(requestJson);
        Integer id = node.getInteger("id");
        gradeConfigService.delete(id);
        return BaseResponse.ok(null);
    }

    @Operation(summary = "校验某 (internshipId, sourceTable) 下权重总和")
    @PostMapping(value = "/validateWeights", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object validateWeights(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("internshipGradeConfig.validateWeights", requestJson);
        JSONObject node = requireNode(requestJson);
        Integer internshipId = node.getInteger("internshipId");
        String sourceTable = node.getString("sourceTable");
        return BaseResponse.ok(gradeConfigService.validateWeights(internshipId, sourceTable));
    }

    private JSONObject requireNode(JSONObject requestJson) {
        JSONObject node = requestJson == null ? null : requestJson.getJSONObject("node");
        if (node == null) {
            throw BaseResponse.parameterInvalid.error("node cannot be empty");
        }
        return node;
    }
}

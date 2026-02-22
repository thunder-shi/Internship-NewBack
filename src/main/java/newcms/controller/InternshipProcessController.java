package newcms.controller;

import com.alibaba.fastjson.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import newcms.annotation.PathRestController;
import newcms.base.BaseResponse;
import newcms.base.Constant;
import newcms.service.IInternshipService;
import newcms.service.IVerifyProcessService;
import newcms.utils.EncryptUtil;
import newcms.utils.LogUtil;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "实习项目管理")
@PathRestController("internshipProcess")
public class InternshipProcessController {

    @Resource
    private IInternshipService iInternshipService;

    @Resource
    private EncryptUtil encryptUtil;

    @Resource
    private IVerifyProcessService iVerifyProcessService;

    // ==================== 实习项目管理（无需审核） ====================

    @Operation(summary = "新增实习项目", description = "创建实习项目并自动配置流程")
    @PostMapping(value = "/addNewInternship", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object addNewInternship(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("addNewInternship", requestJson);
        if (requestJson == null) {
            throw BaseResponse.parameterInvalid.error("请求参数不能为空");
        }
        JSONObject node = requestJson.getJSONObject("node");
        if (node == null) {
            throw BaseResponse.parameterInvalid.error("node 参数不能为空");
        }
        return BaseResponse.ok(iInternshipService.addNewInternship(node));
    }

    // ==================== 实习计划流程（需要审核） ====================

    // @Operation(summary = "提交实习计划", description = "提交计划制定流程，创建审核记录")
    // @PostMapping(value = "/submitInternshipPlan", consumes = MediaType.APPLICATION_JSON_VALUE)
    // public Object submitInternshipPlan(@RequestBody JSONObject requestJson) {
    //     LogUtil.loggerRecord("submitInternshipPlan", requestJson);
    //     if (requestJson == null) {
    //         throw BaseResponse.parameterInvalid.error("请求参数不能为空");
    //     }
    //     return BaseResponse.ok(iInternshipService.submitInternshipPlan(requestJson));
    // }

    @Operation(summary = "删除实习项目", description = "删除实习项目及其关联的流程配置")
    @PostMapping(value = "/deleteNewInternship", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object deleteNewInternship(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("deleteNewInternship", requestJson);
        if (requestJson == null) {
            throw BaseResponse.parameterInvalid.error("请求参数不能为空");
        }
        String idsStr = requestJson.getString("ids");
        if (idsStr == null || idsStr.trim().isEmpty()) {
            throw BaseResponse.parameterInvalid.error("ids 参数不能为空");
        }
        // 使用 encryptUtil 解密并解析逗号分隔的id字符串
        List<String> idsList = Arrays.asList(encryptUtil.getKeyWord(idsStr).split(Constant.SPLIT_OPERATOR.COMMA));
        List<Integer> ids = idsList.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            throw BaseResponse.parameterInvalid.error("ids 参数不能为空");
        }
        return BaseResponse.ok(iInternshipService.deleteNewInternship(ids));
    }


    @PostMapping(value = "/auditProcess", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object auditProcess(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("auditProcess", requestJson);
        JSONObject node = requestJson.getJSONObject("node");
        return BaseResponse.ok(iInternshipService.auditProcess(node));
    }

    @PostMapping(value = "/activateProcess")
    public Object activateProcess (@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("activateProcess", requestJson);
        JSONObject node = requestJson.getJSONObject("node");
        return BaseResponse.ok(iVerifyProcessService.activateProcess(node));
    }

    @Operation(summary = "获取当前进行中的实习项目", description = "根据流程类型代码查询当前时间范围内的实习项目")
    @PostMapping(value = "/getNowInternship", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object getNowInternship(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("getNowInternship", requestJson);
        if (requestJson == null) {
            throw BaseResponse.parameterInvalid.error("请求参数不能为空");
        }
        String processTypeCode = requestJson.getString("processTypeCode");
        return BaseResponse.ok(iInternshipService.getNowInternship(processTypeCode));
    }

}

package newcms.controller;

import com.alibaba.fastjson.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import newcms.annotation.PathRestController;
import newcms.base.BaseResponse;
import newcms.service.IInternshipService;
import newcms.utils.LogUtil;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "实习项目管理")
@PathRestController("internshipProcess")
public class InternshipProcessController {

    @Resource
    private IInternshipService iInternshipService;

    @Operation(summary = "新增实习项目", description = "创建新的实习项目")
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

        @Operation(summary = "提交新增实习项目", description = "保存并创建审核记录")
        @PostMapping(value = "/submitNewInternship", consumes = MediaType.APPLICATION_JSON_VALUE)
        public Object submitNewInternship(@RequestBody JSONObject requestJson) {
            LogUtil.loggerRecord("submitNewInternship", requestJson);
            if (requestJson == null) {
                throw BaseResponse.parameterInvalid.error("请求参数不能为空");
            }
            return BaseResponse.ok(iInternshipService.submitNewInternship(requestJson));
        }
}

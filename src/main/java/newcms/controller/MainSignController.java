package newcms.controller;

import com.alibaba.fastjson.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import newcms.annotation.PathRestController;
import newcms.base.Base;
import newcms.base.BaseResponse;
import newcms.service.IMainSignService;
import newcms.utils.LogUtil;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 实习打卡（MainSign）接口，与 {@link DiaryController} 中日志提交后创建审核记录的方式对齐。
 */
@Tag(name = "实习打卡")
@PathRestController("main-sign")
public class MainSignController {

    @Resource
    private IMainSignService iMainSignService;

    @Operation(summary = "打卡提交审核（写入首条待审 MainVerifyProcess）",
            description = "仅针对实习打卡：在已保存的 MainSign 上创建 MainVerifyProcess，relationId=打卡 id，tableName=MainSign。"
                    + "若已存在「提交待审核」或「审核通过」记录则幂等跳过。"
                    + "审核人：仅按 MainSign.verifyFirstRoleId 解析；解析不到则系统自动通过（verifyUserId/reason 均为「系统自动通过」）。")
    @PostMapping(value = "/submit-audit", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object submitSignAudit(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("mainSign.submitSignAudit", requestJson);
        JSONObject node = requestJson.getJSONObject("node");
        if (node == null) {
            throw BaseResponse.parameterInvalid.error("node 不能为空");
        }
        Integer signId = node.getInteger("signId");
        if (signId == null) {
            throw BaseResponse.parameterInvalid.error("signId 不能为空");
        }
        iMainSignService.ensureSubmitVerifyProcess(signId, Base.getLoginUserId());
        return BaseResponse.ok(null);
    }
}

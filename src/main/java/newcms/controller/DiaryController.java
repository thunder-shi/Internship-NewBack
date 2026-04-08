package newcms.controller;

import com.alibaba.fastjson.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import newcms.annotation.PathRestController;
import newcms.base.Base;
import newcms.base.BaseResponse;
import newcms.service.IDiaryService;
import newcms.utils.LogUtil;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "实习日志管理")
@PathRestController("diary")
public class DiaryController {

    @Resource
    private IDiaryService iDiaryService;

    @Operation(summary = "提交/保存实习日志",
            description = "传 relationId + tableName 标识岗位或题目（tableName=\"RelStuInternshipPost\" 或 \"RelTitleStudent\"）。"
                    + "submit=false 时保存草稿，submit=true 时提交审核。"
                    + "同一 relationId+tableName+periodId 只存一条，重复调用时就地更新。"
                    + "返回日志 id，可用于 /common/minio/upload 上传附件（tableName=\"main_diary\"）。")
    @PostMapping(value = "/submit", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object submitDiary(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("submitDiary", requestJson);
        JSONObject node = requestJson.getJSONObject("node");
        if (node == null) throw BaseResponse.parameterInvalid.error("node 不能为空");
        Integer relationId = node.getInteger("relationId");
        String tableName = node.getString("tableName");
        Integer periodId = node.getInteger("periodId");
        String content = node.getString("content");
        Boolean submit = node.getBoolean("submit");
        if (relationId == null) throw BaseResponse.parameterInvalid.error("relationId 不能为空");
        if (tableName == null || tableName.isBlank()) throw BaseResponse.parameterInvalid.error("tableName 不能为空");
        if (periodId == null) throw BaseResponse.parameterInvalid.error("periodId 不能为空");
        return BaseResponse.ok(
                iDiaryService.submitDiary(relationId, tableName, periodId, content, submit, Base.getLoginUserId())
        );
    }

    @Operation(summary = "获取期次列表（学生端）",
            description = "返回该岗位/题目的所有期次，每期包含 period 信息和 diary（未提交时为 null）。"
                    + "传 relationId + tableName（\"RelStuInternshipPost\" 或 \"RelTitleStudent\"）。")
    @PostMapping(value = "/periods", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object getDiaryPeriods(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("getDiaryPeriods", requestJson);
        JSONObject node = requestJson.getJSONObject("node");
        if (node == null) throw BaseResponse.parameterInvalid.error("node 不能为空");
        Integer relationId = node.getInteger("relationId");
        String tableName = node.getString("tableName");
        if (relationId == null) throw BaseResponse.parameterInvalid.error("relationId 不能为空");
        if (tableName == null || tableName.isBlank()) throw BaseResponse.parameterInvalid.error("tableName 不能为空");
        return BaseResponse.ok(iDiaryService.getDiaryPeriods(relationId, tableName));
    }

    @Operation(summary = "获取实习项目所有期次定义（老师端）",
            description = "返回该实习项目的 MainDiaryPeriod 列表，按 periodIndex 升序，用于老师端期次选择器。")
    @PostMapping(value = "/internship-periods", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object getInternshipPeriods(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("getInternshipPeriods", requestJson);
        JSONObject node = requestJson.getJSONObject("node");
        if (node == null) throw BaseResponse.parameterInvalid.error("node 不能为空");
        Integer internshipId = node.getInteger("internshipId");
        if (internshipId == null) throw BaseResponse.parameterInvalid.error("internshipId 不能为空");
        return BaseResponse.ok(iDiaryService.getInternshipPeriods(internshipId));
    }

    @Operation(summary = "老师查看某期学生日志列表",
            description = "返回该实习项目某期所有学生的日志状态。"
                    + "校外实习：每项含 stuRelationId、studentName、internshipPostName 及 diary；"
                    + "校内实习：每项含 titleRelationId、studentName、titleName、teacherName 及 diary。"
                    + "userId 不传则返回全部（超管视角）。")
    @PostMapping(value = "/period-students", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object getPeriodStudents(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("getPeriodStudents", requestJson);
        JSONObject node = requestJson.getJSONObject("node");
        if (node == null) throw BaseResponse.parameterInvalid.error("node 不能为空");
        Integer internshipId = node.getInteger("internshipId");
        Integer periodId = node.getInteger("periodId");
        if (internshipId == null || periodId == null) {
            throw BaseResponse.parameterInvalid.error("internshipId 和 periodId 不能为空");
        }
        Integer userId = node.getInteger("userId");
        return BaseResponse.ok(iDiaryService.getPeriodStudents(internshipId, periodId, userId));
    }
}

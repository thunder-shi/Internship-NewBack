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

import java.time.LocalDateTime;
import java.util.List;

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
        String title = node.getString("title");
        String content = node.getString("content");
        Boolean submit = node.getBoolean("submit");
        if (relationId == null) throw BaseResponse.parameterInvalid.error("relationId 不能为空");
        if (tableName == null || tableName.isBlank()) throw BaseResponse.parameterInvalid.error("tableName 不能为空");
        if (periodId == null) throw BaseResponse.parameterInvalid.error("periodId 不能为空");
        return BaseResponse.ok(
                iDiaryService.submitDiary(relationId, tableName, periodId, title, content, submit, Base.getLoginUserId())
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

    @Operation(summary = "生成/重新生成实习项目日志期次（管理员端）",
            description = "传 internshipId + reportStartTime + reportEndTime，再二选一传 cron（Spring 6-field格式）或 periodNum（期数）。"
                    + "若已有学生提交（submit=true）的日志，则拒绝操作。"
                    + "成功后删除旧期次及未提交桩，写入新期次，并为所有已审核通过的学生创建 submit=false 的日志桩。")
    @PostMapping(value = "/generatePeriods", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object generatePeriods(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("generatePeriods", requestJson);
        JSONObject node = requestJson.getJSONObject("node");
        if (node == null) throw BaseResponse.parameterInvalid.error("node 不能为空");
        Integer internshipId = node.getInteger("internshipId");
        LocalDateTime reportStartTime = node.getObject("reportStartTime", LocalDateTime.class);
        LocalDateTime reportEndTime = node.getObject("reportEndTime", LocalDateTime.class);
        String cron = node.getString("cron");
        Integer periodNum = node.getInteger("periodNum");
        if (internshipId == null) throw BaseResponse.parameterInvalid.error("internshipId 不能为空");
        if (reportStartTime == null || reportEndTime == null)
            throw BaseResponse.parameterInvalid.error("reportStartTime 和 reportEndTime 不能为空");
        iDiaryService.generatePeriods(internshipId, reportStartTime, reportEndTime, cron, periodNum);
        return BaseResponse.ok(null);
    }

    @Operation(summary = "新增/编辑单条期次（管理员端）",
            description = "id=null 时新增（必传 internshipId），id 有值时更新（只修改 beginTime/endTime）。"
                    + "保存后自动按 beginTime 重建同项目所有期次的 periodIndex。")
    @PostMapping(value = "/period/save", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object savePeriod(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("savePeriod", requestJson);
        JSONObject node = requestJson.getJSONObject("node");
        if (node == null) throw BaseResponse.parameterInvalid.error("node 不能为空");
        Integer id = node.getInteger("id");
        Integer internshipId = node.getInteger("internshipId");
        LocalDateTime beginTime = node.getObject("beginTime", LocalDateTime.class);
        LocalDateTime endTime = node.getObject("endTime", LocalDateTime.class);
        iDiaryService.savePeriod(id, internshipId, beginTime, endTime);
        return BaseResponse.ok(null);
    }

    @Operation(summary = "删除期次（管理员端，支持批量）",
            description = "若任意期次存在 submit=true 的日志则拒绝，返回错误。"
                    + "否则自动删除关联草稿桩，再删期次，最后重建 periodIndex。")
    @PostMapping(value = "/period/delete", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object deletePeriods(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("deletePeriods", requestJson);
        JSONObject node = requestJson.getJSONObject("node");
        if (node == null) throw BaseResponse.parameterInvalid.error("node 不能为空");
        List<Integer> ids = node.getJSONArray("ids") == null ? null
                : node.getJSONArray("ids").toJavaList(Integer.class);
        if (ids == null || ids.isEmpty()) throw BaseResponse.parameterInvalid.error("ids 不能为空");
        iDiaryService.deletePeriods(ids);
        return BaseResponse.ok(null);
    }

    @Operation(summary = "批量初始化实习项目日志占位记录",
            description = "给指定实习项目下所有学生、所有期次创建 submit=false 的日志占位记录（幂等）。"
                    + "校外实习遍历 RelStuInternshipPost，校内遍历 RelTitleStudent。"
                    + "前端在 initTeacherStudentByInternshipId 成功后调用。")
    @PostMapping(value = "/init-by-internship", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object initDiaryByInternship(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("initDiaryByInternship", requestJson);
        JSONObject node = requestJson.getJSONObject("node");
        if (node == null) throw BaseResponse.parameterInvalid.error("node 不能为空");
        Integer internshipId = node.getInteger("internshipId");
        if (internshipId == null) throw BaseResponse.parameterInvalid.error("internshipId 不能为空");
        iDiaryService.initDiaryByInternship(internshipId);
        return BaseResponse.ok(null);
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

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

    @Operation(summary = "提交实习日志",
            description = "校外实习传 stuInternshipPostId，校内实习传 relTitleStudentId，二者不能同时为空。"
                    + "返回 MainDiary 的 id（首次提交为新建，重新提交时返回已有记录的 id，内容就地更新）。"
                    + "可用该 id 调用 /common/minio/upload 上传附件（tableName=\"main_diary\"）；"
                    + "重新提交不会删除旧附件，前端需自行管理附件的增删。")
    @PostMapping(value = "/submit", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object submitDiary(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("submitDiary", requestJson);
        JSONObject node = requestJson.getJSONObject("node");
        if (node == null) throw BaseResponse.parameterInvalid.error("node 不能为空");
        Integer stuInternshipPostId = node.getInteger("stuInternshipPostId");
        Integer relTitleStudentId = node.getInteger("relTitleStudentId");
        Integer periodIndex = node.getInteger("periodIndex");
        String content = node.getString("content");
        if (stuInternshipPostId == null && relTitleStudentId == null) {
            throw BaseResponse.parameterInvalid.error("stuInternshipPostId 和 relTitleStudentId 不能同时为空");
        }
        if (periodIndex == null) throw BaseResponse.parameterInvalid.error("periodIndex 不能为空");
        return BaseResponse.ok(
                iDiaryService.submitDiary(stuInternshipPostId, relTitleStudentId, periodIndex, content, Base.getLoginUserId())
        );
    }

    @Operation(summary = "获取日志期数列表",
            description = "返回该学生的所有周期（从实习开始到当前期），每期包含 periodIndex 和 diary（未提交时为 null）。"
                    + "校外传 stuInternshipPostId，校内传 relTitleStudentId。")
    @PostMapping(value = "/periods", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object getDiaryPeriods(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("getDiaryPeriods", requestJson);
        JSONObject node = requestJson.getJSONObject("node");
        if (node == null) throw BaseResponse.parameterInvalid.error("node 不能为空");
        Integer stuInternshipPostId = node.getInteger("stuInternshipPostId");
        Integer relTitleStudentId = node.getInteger("relTitleStudentId");
        if (stuInternshipPostId == null && relTitleStudentId == null) {
            throw BaseResponse.parameterInvalid.error("stuInternshipPostId 和 relTitleStudentId 不能同时为空");
        }
        return BaseResponse.ok(iDiaryService.getDiaryPeriods(stuInternshipPostId, relTitleStudentId));
    }

    @Operation(summary = "获取实习项目的总期数（老师端）",
            description = "老师端期数选择器使用。根据实习项目的 cron 和最早流程 startTime 推算当前总期数，"
                    + "返回 { \"totalPeriods\": N }，0 表示尚未开始或无流程配置。")
    @PostMapping(value = "/internship-periods", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object getInternshipPeriodCount(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("getInternshipPeriodCount", requestJson);
        JSONObject node = requestJson.getJSONObject("node");
        if (node == null) throw BaseResponse.parameterInvalid.error("node 不能为空");
        Integer internshipId = node.getInteger("internshipId");
        if (internshipId == null) throw BaseResponse.parameterInvalid.error("internshipId 不能为空");
        JSONObject result = new JSONObject();
        result.put("totalPeriods", iDiaryService.getInternshipPeriodCount(internshipId));
        return BaseResponse.ok(result);
    }

    @Operation(summary = "老师查看某期学生日志列表",
            description = "返回该实习项目某期所有学生的日志状态。"
                    + "校外实习：每项含 stuInternshipPostId、studentName、internshipPostName 及 diary；"
                    + "校内实习：每项含 relTitleStudentId、studentName、titleName、teacherName 及 diary。")
    @PostMapping(value = "/period-students", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object getPeriodStudents(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("getPeriodStudents", requestJson);
        JSONObject node = requestJson.getJSONObject("node");
        if (node == null) throw BaseResponse.parameterInvalid.error("node 不能为空");
        Integer internshipId = node.getInteger("internshipId");
        Integer periodIndex = node.getInteger("periodIndex");
        if (internshipId == null || periodIndex == null) {
            throw BaseResponse.parameterInvalid.error("internshipId 和 periodIndex 不能为空");
        }
        Integer userId = node.getInteger("userId"); // 可选，不传则返回全部学生（超管视角）
        return BaseResponse.ok(iDiaryService.getPeriodStudents(internshipId, periodIndex, userId));
    }
}

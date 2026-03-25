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
import org.springframework.data.domain.Sort;

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

     @Operation(summary = "根据角色和创建人获取审核人ID串", description = "返回同校、指定审核角色下的所有审核人ID，使用竖线分隔，如：12|14|17")
     @PostMapping(value = "/getVerifyUserIds", consumes = MediaType.APPLICATION_JSON_VALUE)
     public Object getVerifyUserIds(@RequestBody JSONObject requestJson) {
         LogUtil.loggerRecord("getVerifyUserIds", requestJson);
         if (requestJson == null) {
             throw BaseResponse.parameterInvalid.error("请求参数不能为空");
         }
         JSONObject node = requestJson.getJSONObject("node");
         Integer verifyRoleId = node != null ? node.getInteger("verifyRoleId") : requestJson.getInteger("verifyRoleId");
         Integer createUserId = node != null ? node.getInteger("createUserId") : requestJson.getInteger("createUserId");
         if (createUserId == null) {
             throw BaseResponse.parameterInvalid.error("createUserId 不能为空");
         }
         // verifyRoleId 允许为 null/0，此时服务方法会按约定返回空字符串
         String verifyUserIds = iVerifyProcessService.GetVerifyUserId(verifyRoleId, createUserId);
         return BaseResponse.ok(verifyUserIds);
     }

     @Operation(
             summary = "获取实习项目可选用户列表",
             description = "根据 internshipId 和 jobId 查询 BaseUser 中尚未在 RelIntershipUser 中关联的用户"
     )
     @PostMapping(value = "/getAvailableUsersForInternship", consumes = MediaType.APPLICATION_JSON_VALUE)
     public Object getAvailableUsersForInternship(@RequestBody JSONObject requestJson) {
         LogUtil.loggerRecord("getAvailableUsersForInternship", requestJson);
         if (requestJson == null) {
             throw BaseResponse.parameterInvalid.error("请求参数不能为空");
         }

         // 仿照 getSomeRecords 的参数形式，从 node.searchKey 或顶层 searchKey 里取值
         JSONObject node = requestJson.getJSONObject("node");
         JSONObject searchKey = node != null ? node.getJSONObject("searchKey") : requestJson.getJSONObject("searchKey");
         if (searchKey == null) {
             throw BaseResponse.parameterInvalid.error("searchKey 不能为空");
         }

         Integer internshipId = searchKey.getInteger("internshipId");
         Integer jobId = searchKey.getInteger("jobId");

         if (internshipId == null) {
             throw BaseResponse.parameterInvalid.error("internshipId 不能为空");
         }
         if (jobId == null) {
             throw BaseResponse.parameterInvalid.error("jobId 不能为空");
         }

         // 分页信息：优先从 node.pageInfo 取值
         JSONObject pageInfo = node != null ? node.getJSONObject("pageInfo") : requestJson.getJSONObject("pageInfo");
         int page = pageInfo != null && pageInfo.getInteger("page") != null
                 ? pageInfo.getInteger("page")
                 : Constant.DEFAULT_PAGE;
         int size = pageInfo != null && pageInfo.getInteger("size") != null
                 ? pageInfo.getInteger("size")
                 : Constant.DEFAULT_SIZE;

         // 排序信息：优先从 node.sort 取值
         JSONObject sortJson = node != null ? node.getJSONObject("sort") : requestJson.getJSONObject("sort");
         Sort sort = Sort.unsorted();
         if (sortJson != null) {
             String properties = sortJson.getString("properties");
             String directionStr = sortJson.getString("direction");
             if (properties != null && !properties.trim().isEmpty()) {
                 Sort.Direction direction;
                 try {
                     direction = directionStr != null
                             ? Sort.Direction.fromString(directionStr)
                             : Sort.Direction.ASC;
                 } catch (IllegalArgumentException e) {
                     direction = Sort.Direction.ASC;
                 }

                 // 支持逗号分隔的多字段排序
                 String[] props = properties.split(Constant.SPLIT_OPERATOR.COMMA);
                 sort = Sort.by(direction, props);
             }
         }

         return BaseResponse.ok(
                 iInternshipService.getAvailableUsersForInternship(internshipId, jobId, page, size, sort)
         );
     }

    @Operation(
            summary = "根据实习项目初始化师生关系和审核记录",
            description = "按 internshipId 关联岗位和学生选择记录，创建 RelTeacherStudent，并同步创建 MainVerifyProcess。"
                    + "tutorAssignKind：1=校内导师（自动均衡分配 teacherId，支持待审核重分配）；"
                    + "2=企业导师（teacherId 留空，后续手动分配）。缺省为 1。"
    )
    @PostMapping(value = "/initTeacherStudentByInternshipId", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object initTeacherStudentByInternshipId(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("initTeacherStudentByInternshipId", requestJson);
        if (requestJson == null) {
            throw BaseResponse.parameterInvalid.error("请求参数不能为空");
        }
        JSONObject node = requestJson.getJSONObject("node");
        Integer internshipId = node != null ? node.getInteger("internshipId") : requestJson.getInteger("internshipId");
        Integer processId = node != null ? node.getInteger("processId") : requestJson.getInteger("processId");
        Integer createUserId = node != null ? node.getInteger("createUserId") : requestJson.getInteger("createUserId");
        String verifyUserId = node != null ? node.getString("verifyUserId") : requestJson.getString("verifyUserId");
        Integer tutorAssignKind = node != null ? node.getInteger("tutorAssignKind") : requestJson.getInteger("tutorAssignKind");
        if (internshipId == null || processId == null || createUserId == null || verifyUserId == null) {
            throw BaseResponse.parameterInvalid.error("internshipId、processId、createUserId、verifyUserId 不能为空");
        }
        return BaseResponse.ok(
                iInternshipService.initTeacherStudentByInternshipId(internshipId, processId, createUserId, verifyUserId,
                        tutorAssignKind)
        );
    }

    // @Operation(summary = "获取当前进行中的实习项目", description = "根据流程类型代码查询当前时间范围内的实习项目")
    // @PostMapping(value = "/getNowInternship", consumes = MediaType.APPLICATION_JSON_VALUE)
    // public Object getNowInternship(@RequestBody JSONObject requestJson) {
    //     LogUtil.loggerRecord("getNowInternship", requestJson);
    //     if (requestJson == null) {
    //         throw BaseResponse.parameterInvalid.error("请求参数不能为空");
    //     }
    //     String processTypeCode = requestJson.getString("processTypeCode");
    //     return BaseResponse.ok(iInternshipService.getNowInternship(processTypeCode));
    // }

}

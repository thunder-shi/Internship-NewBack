package newcms.controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import newcms.annotation.PathRestController;
import newcms.base.BaseResponse;
import newcms.service.IInternshipPostService;
import newcms.utils.EncryptUtil;
import newcms.utils.LogUtil;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "实习岗位管理")
@PathRestController("internshipPost")
public class InternshipPostController {

    @Resource
    private EncryptUtil encryptUtil;

    @Resource
    private IInternshipPostService iInternshipPostService;

    @Operation(summary = "学生选择岗位", description = "学生选择新的实习岗位")
    @PostMapping(value = "/StuSelPost", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object StuSelPost(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("StuSelPost", requestJson);
        if (requestJson == null) {
            throw BaseResponse.parameterInvalid.error("请求参数不能为空");
        }
        // 获取密文参数并解码
        String studentIdStr = requestJson.getString("StudentId");
        String oldPostIdStr = requestJson.getString("oldPostId");
        String newPostIdStr = requestJson.getString("newPostId");
        // 解码密文并转换为Integer
        Integer studentId = Integer.parseInt(encryptUtil.getKeyWord(studentIdStr));
        Integer oldPostId = Integer.parseInt(encryptUtil.getKeyWord(oldPostIdStr));
        Integer newPostId = Integer.parseInt(encryptUtil.getKeyWord(newPostIdStr));
        
        return BaseResponse.ok(iInternshipPostService.stuSelPost(studentId, oldPostId, newPostId));
    }

    @Operation(summary = "学生批量报名岗位", description = "批量首次选择岗位（oldPostId=0），单条失败不阻断其余。")
    @PostMapping(value = "/StuSelPostBatch", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object StuSelPostBatch(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("StuSelPostBatch", requestJson);
        if (requestJson == null) {
            throw BaseResponse.parameterInvalid.error("请求参数不能为空");
        }
        String studentIdStr = requestJson.getString("StudentId");
        Integer studentId = Integer.parseInt(encryptUtil.getKeyWord(studentIdStr));
        JSONArray postIdsArr = requestJson.getJSONArray("internshipPostIds");
        if (postIdsArr == null || postIdsArr.isEmpty()) {
            throw BaseResponse.parameterInvalid.error("internshipPostIds 不能为空");
        }
        List<Integer> postIds = new ArrayList<>();
        for (int i = 0; i < postIdsArr.size(); i++) {
            postIds.add(Integer.parseInt(encryptUtil.getKeyWord(postIdsArr.getString(i))));
        }
        return BaseResponse.ok(iInternshipPostService.stuSelPostBatch(studentId, postIds));
    }

}

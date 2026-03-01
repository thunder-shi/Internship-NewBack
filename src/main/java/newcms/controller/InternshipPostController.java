package newcms.controller;

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

}

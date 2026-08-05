package newcms.controller.userCtrl;

import newcms.annotation.PathRestController;
import newcms.annotation.Permissions;
import newcms.base.BaseResponse;
import newcms.base.Constant;
import newcms.controller.commonCtrl.CommonController;
import newcms.service.IUserService;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresUser;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;


@PathRestController(value = "sign")
public class SignController extends CommonController {
    @Resource
    private IUserService iUserService;

    //默认账号：15505096851， 密码：Axt-1234
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object login(@RequestBody JSONObject requestJson) {
        String account = requestJson.getString("account");
        String password = requestJson.getString("password");
        Boolean rememberMe = requestJson.getBoolean("rememberMe");
        iUserService.login(account, password, rememberMe);
        return BaseResponse.ok("login success");
    }

    @PostMapping(value = "/logout")
    public Object logout() {
        if (SecurityUtils.getSubject().getPrincipal() != null) {
            SecurityUtils.getSubject().logout();
        }
        return BaseResponse.ok;
    }

    @GetMapping(value = "/info")
    public Object info() {
        return BaseResponse.ok(iUserService.getLoginUser());
    }


    @RequiresUser
    @Permissions(value = "user", c = false, r = true, u = true, d = false, orAndNon = Constant.OR)
    @PutMapping("/userRoleSet/{userId}/{roleSet}")
    public Object userRoleSet(@PathVariable Integer[] roleSet, @PathVariable Integer userId) {
        return BaseResponse.ok(iUserService.userRoleSet(roleSet, userId));
    }

    @PostMapping(value = "/getUserListIsNotDelete", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object getUserListIsNotDelete(@RequestBody JSONObject requestJson) {
        JSONObject pageInfo = requestJson.getJSONObject("pageInfo");
        JSONObject sort = requestJson.getJSONObject("sort");
        return BaseResponse.ok(iUserService.getUserListIsNotDelete(pageInfo, sort));
    }

    @PostMapping(value = "/editUserInfo/{userId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object editUserInfo(@RequestBody JSONObject requestJson, @PathVariable Integer userId) {
        return BaseResponse.ok(iUserService.editUserInfo(requestJson, userId));
    }

    /** 固定接收 userId、oldPassword、password、reset；reset=true 按学工号重置；reset=false 且 oldPassword 为空时为新增，password 可省略并自动按学工号生成 */
    @PostMapping(value = "editPassword", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object editPassword(@RequestBody JSONObject requestJson) {
        String userId = requestJson.getString("userId");
        String oldPassword = requestJson.getString("oldPassword");
        String password = requestJson.getString("password");
        boolean reset = parseResetFlag(requestJson);
        iUserService.editPassword(userId, oldPassword, password, reset);
        return BaseResponse.ok;
    }

    /**
     * 判断账号是否仍为初始密码。
     * <p>初始密码明文：{@code SLSDsx#} + 学工号（workId）后四位（不足四位左补 0），与库中摘要比对方式同登录。</p>
     * body 示例：{@code { "userId": 123 }} 或 {@code { "node": { "userId": 123 } }}
     */
    @PostMapping(value = "/isInitialPassword", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object isInitialPassword(@RequestBody JSONObject requestJson) {
        if (requestJson == null) {
            throw BaseResponse.parameterInvalid.error("请求参数不能为空");
        }
        JSONObject node = requestJson.getJSONObject("node");
        Integer userId = node != null ? node.getInteger("userId") : requestJson.getInteger("userId");
        if (userId == null && requestJson.getString("userId") != null) {
            try {
                userId = Integer.parseInt(requestJson.getString("userId").trim());
            } catch (NumberFormatException e) {
                throw BaseResponse.parameterInvalid.error("userId 无效");
            }
        }
        return BaseResponse.ok(iUserService.isInitialPassword(userId));
    }

    private static boolean parseResetFlag(JSONObject requestJson) {
        Object reset = requestJson.get("reset");
        if (reset instanceof Boolean) {
            return Boolean.TRUE.equals(reset);
        }
        if (reset != null) {
            return "true".equalsIgnoreCase(String.valueOf(reset).trim());
        }
        return false;
    }
    @PostMapping(value = "/oss/uploadAvatar",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object uploadAvatar(@RequestParam MultipartFile file ){
        return BaseResponse.ok(iUserService.uploadAvatar(file));
    }

    @PostMapping(value = "/getUserRoles", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object getUserRoles(@RequestBody JSONObject requestJson) {
        String userId = requestJson.getString("userId");
        return BaseResponse.ok(iUserService.getUserRoles(userId));
    }
    @PostMapping(value = "/saveUserRoles", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object saveUserRoles(@RequestBody JSONObject requestJson) {
        String userId = requestJson.getString("userId");
        String strRoleIds = requestJson.getString("roleIds");
        Integer[] roleIds = (Integer[]) ConvertUtils.convert(strRoleIds.substring(1, strRoleIds.length() - 1).split(Constant.SPLIT_OPERATOR.COMMA), Integer.class);
        return BaseResponse.ok(iUserService.saveUserRoles(userId, roleIds));
    }

    @PostMapping(value = "/getDepartment", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object getDepartment(@RequestBody JSONObject requestJson) {
        String parentId = requestJson.getString("parentId");
        return BaseResponse.ok(iUserService.getDepartment(parentId));
    }
}

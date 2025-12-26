package newcms.controller.systemManage;

import newcms.annotation.PathRestController;
import newcms.base.BaseResponse;
import newcms.controller.commonCtrl.CommonController;
import newcms.service.IRoleService;
import com.alibaba.fastjson.JSONObject;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.annotation.Resource;

@PathRestController("role")
public class RoleInfoController extends CommonController {
    @Resource
    private IRoleService iRoleService;

    @PostMapping(value = "/editRolePermissions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object editRolePermission(@RequestBody JSONObject requestJson) {
        String roleId = requestJson.getString("roleId");
        JSONObject permissions = requestJson.getJSONObject("permissions");
        return BaseResponse.ok(iRoleService.editPermission(roleId, permissions));
    }

    @PostMapping(value = "/getRolePermissions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object getRolePermissions(@RequestBody JSONObject requestJson) {
        String roleIds = requestJson.getString("roleId");
        return BaseResponse.ok(iRoleService.getRolePermissions(roleIds));
    }

}

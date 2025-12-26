package newcms.service;

import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Service;

/**
 * 角色服务接口
 * @author hongzhangming
 */
@Service
public interface IRoleService {
    /**
     * 编辑角色权限
     * @param roleId 角色ID
     * @param permissions 权限信息
     * @return 操作结果
     */
    Object editPermission(String roleId, JSONObject permissions);

    /**
     * 查询角色权限
     * @param roleIds 角色ID（可以是多个，用逗号分隔）
     * @return 权限信息
     */
    Object getRolePermissions(String roleIds);
}


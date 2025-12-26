package newcms.service.impl;

import newcms.base.Base;
import newcms.entity.db.RelRoleMenu;
import newcms.repository.db.RelRoleMenuDao;
import newcms.service.ICommonService;
import newcms.service.IRoleService;
import com.alibaba.fastjson.JSONObject;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

/**
 * 角色服务实现类
 * @author hongzhangming
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class RoleServiceImpl extends Base implements IRoleService {
    @Resource
    private RelRoleMenuDao relRoleMenuDao;
    @Resource
    protected ICommonService iCommonService;

    @Override
    public Object editPermission(String roleId, JSONObject permissions) {
        Integer roleIdInt = Integer.parseInt(roleId);
        
        // 先删除该角色的所有旧权限
        JSONObject searchKeys = new JSONObject();
        searchKeys.put("roleId", roleIdInt);
        Object oldPermissions = iCommonService.getSomeRecords("RelRoleMenu", searchKeys);
        
        List<Integer> oldPermissionIds = new ArrayList<>();
        if (oldPermissions instanceof Page) {
            @SuppressWarnings("unchecked")
            Page<Object> oldPermissionPage = (Page<Object>) oldPermissions;
            for (Object obj : oldPermissionPage.getContent()) {
                JSONObject jsonObj = com.alibaba.fastjson.JSONObject.parseObject(
                    com.alibaba.fastjson.JSONObject.toJSONString(obj));
                oldPermissionIds.add(jsonObj.getIntValue("id"));
            }
        }
        
        // 删除旧权限（逻辑删除）
        if (!oldPermissionIds.isEmpty()) {
            relRoleMenuDao.deleteByIdIn(oldPermissionIds);
        }
        
        // 保存新权限
        List<RelRoleMenu> newPermissions = new ArrayList<>();
        if (permissions != null) {
            for (String menuIdStr : permissions.keySet()) {
                Integer menuId = Integer.parseInt(menuIdStr);
                JSONObject permissionData = permissions.getJSONObject(menuIdStr);
                
                RelRoleMenu relRoleMenu = new RelRoleMenu();
                relRoleMenu.setRoleId(roleIdInt);
                relRoleMenu.setMenuId(menuId);
                relRoleMenu.setVisibleFlag(permissionData.getBoolean("visibleFlag"));
                relRoleMenu.setAddFlag(permissionData.getBoolean("addFlag"));
                relRoleMenu.setDeleteFlag(permissionData.getBoolean("deleteFlag"));
                relRoleMenu.setModifyFlag(permissionData.getBoolean("modifyFlag"));
                
                newPermissions.add(relRoleMenu);
            }
        }
        
        if (!newPermissions.isEmpty()) {
            return relRoleMenuDao.saveAll(newPermissions);
        }
        
        return new ArrayList<>();
    }

    @Override
    public Object getRolePermissions(String roleIds) {
        if (ObjectUtils.isEmpty(roleIds)) {
            return new ArrayList<>();
        }
        
        // 解析角色ID（支持逗号分隔的多个ID）
        Set<Integer> roleIdSet = new HashSet<>();
        String[] roleIdArray = roleIds.split(",");
        for (String roleIdStr : roleIdArray) {
            try {
                roleIdSet.add(Integer.parseInt(roleIdStr.trim()));
            } catch (NumberFormatException e) {
                // 忽略无效的角色ID
            }
        }
        
        if (roleIdSet.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 查询角色权限
        List<RelRoleMenu> permissions = relRoleMenuDao.findByRoleIdInAndIsDeletedFalse(roleIdSet);
        
        // 如果查询结果为空，直接返回空 Map
        if (permissions.isEmpty()) {
            return new HashMap<>();
        }
        
        // 按角色ID分组返回
        Map<Integer, List<RelRoleMenu>> permissionsByRole = permissions.stream()
            .collect(Collectors.groupingBy(RelRoleMenu::getRoleId));
        
        return permissionsByRole;
    }
}


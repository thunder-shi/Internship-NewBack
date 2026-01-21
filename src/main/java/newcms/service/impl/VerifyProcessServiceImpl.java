package newcms.service.impl;

import jakarta.annotation.Resource;
import newcms.base.Base;
import newcms.base.BaseResponse;
import newcms.service.ICommonService;
import newcms.service.IVerifyProcessService;
import newcms.utils.FastJsonUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson.JSONObject;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 审核流程服务实现类
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class VerifyProcessServiceImpl extends Base implements IVerifyProcessService {

    @Resource
    private ICommonService iCommonService;

    @Override
    @SuppressWarnings("unchecked")
    public Object GetInternshipFoundProcess(Integer internshipId) {
        if (internshipId == null) {
            throw BaseResponse.parameterInvalid.error("实习项目ID不能为空");
        }

        // 查找流程关联记录（取第一条）
        JSONObject searchKeys = new JSONObject();
        searchKeys.put("internshipId", internshipId);
        Page<Object> relPage = (Page<Object>) iCommonService.getSomeRecords(
                "ViewRelProcessInternship", searchKeys, null,
                Sort.by(Sort.Direction.ASC, "theOrder"), 1, 1);
        List<Object> relList = relPage.getContent();
        
        if (relList == null || relList.isEmpty()) {
            throw BaseResponse.moreInfoError.error("未找到实习项目的流程配置，请先创建流程模板");
        }
        
        return relList.get(0);
    }

    @Override
    @SuppressWarnings("unchecked")
    public String GetVerifyUserId(Integer verifyFirstRoleId, Integer createUserId) {
        if (verifyFirstRoleId == null) {
            // 如果没有审核角色ID，返回空字符串
            return "";
        }
        if (createUserId == null) {
            throw BaseResponse.parameterInvalid.error("创建用户ID不能为空");
        }

        // (1) 获取当前用户的 schoolId
        Object currentUserObj = iCommonService.getOneRecordById("ViewBaseUser", createUserId);
        if (currentUserObj == null) {
            throw BaseResponse.moreInfoError.error("未找到当前用户信息");
        }
        JSONObject currentUserJson = FastJsonUtil.toJson(currentUserObj);
        Integer schoolId = currentUserJson.getInteger("schoolId");
        
        if (schoolId == null) {
            // 如果当前用户没有 schoolId，返回空字符串
            return "";
        }

        // (2) 查找 ViewBaseUser 中所有 schoolId 相同的用户，获取他们的 id 列表
        JSONObject schoolSearchKeys = new JSONObject();
        schoolSearchKeys.put("schoolId", schoolId);
        Page<Object> schoolUserPage = (Page<Object>) iCommonService.getSomeRecords(
                "ViewBaseUser", schoolSearchKeys, null, Sort.unsorted());
        List<Object> schoolUserList = schoolUserPage.getContent();
        Set<Integer> schoolUserIds = schoolUserList.stream()
                .map(user -> {
                    JSONObject userJson = FastJsonUtil.toJson(user);
                    return userJson.getInteger("id");
                })
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        if (schoolUserIds.isEmpty()) {
            return "";
        }

        // (3) 查找 RelUserRole 中所有 roleId = verifyFirstRoleId 的记录
        JSONObject roleSearchKeys = new JSONObject();
        roleSearchKeys.put("roleId", verifyFirstRoleId);
        Page<Object> userRolePage = (Page<Object>) iCommonService.getSomeRecords(
                "RelUserRole", roleSearchKeys, null, Sort.unsorted());
        List<Object> userRoleList = userRolePage.getContent();

        // (4) 筛选出 userId 在 schoolUserIds 中的记录，并提取 userId
        List<Integer> verifyUserIds = userRoleList.stream()
                .map(role -> {
                    JSONObject roleJson = FastJsonUtil.toJson(role);
                    return roleJson.getInteger("userId");
                })
                .filter(userId -> userId != null && schoolUserIds.contains(userId))
                .distinct()
                .collect(Collectors.toList());

        // (5) 将 userId 用竖线连接成字符串
        if (verifyUserIds.isEmpty()) {
            return "";
        }
        
        return verifyUserIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining("|"));
    }
}

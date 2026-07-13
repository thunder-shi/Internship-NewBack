package newcms.service.impl;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import newcms.base.Base;
import newcms.base.BaseResponse;
import newcms.base.Constant;
import newcms.entity.db.*;
import newcms.repository.db.BaseUserDao;
import newcms.repository.db.RelUserRoleDao;
import newcms.repository.db.BaseInternshipTypeDao;
import newcms.repository.db.RelIntershipUserDao;
import newcms.service.ICommonService;
import newcms.service.IDataListService;
import newcms.service.IDataTreeService;
import newcms.service.IUserService;
import newcms.service.IVerifyProcessService;
import newcms.utils.*;
import com.alibaba.fastjson.JSONObject;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.IncorrectCredentialsException;
import org.apache.shiro.authc.UnknownAccountException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author hongzhangming
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class UserServiceImpl extends Base implements IUserService {
    @Resource
    private BaseUserDao tblUserInfoDao;
    @Resource
    private RelUserRoleDao tblUserRoleRelDao;
    @Resource
    protected ICommonService iCommonService;
    @Resource
    private IDataListService iDataListService;
    @Resource
    protected IDataTreeService iDataTreeService;
    @Resource
    private DataTreeServiceImpl dataTreeServiceImpl;
    @Resource
    private IVerifyProcessService iVerifyProcessService;
    @Resource
    private RelIntershipUserDao relIntershipUserDao;
    @Resource
    private BaseInternshipTypeDao baseInternshipTypeDao;

    private static Pattern pattern = Pattern.compile("-?[0-9]+(\\\\.[0-9]+)?");

/**
     * 当前登录用户信息(包含 roles  permissions)
     * 写入redis中三个键，1 userInfo，包含用户的基本信息；2 roles包含用户的角色名称信息；
     * 3 permissions包含用户的角色Id信息；4 menuList 可打开的菜单信息；
     *
     * @return
     */
public Object getLoginUser() {
    return getLoginUser(null, null);
}
public Object getLoginUser(Date date, String userAgent) {
    Integer userId = getLoginUserId();
    String key = APPLICATION_NAME + ":userInfo:" + userId;
    //防止缓存导致无法立即更新相关信息
    if (redis.hasKey(key)) {
        redis.delete(key);
    }
    if (!redis.hasKey(key)) {
//            if (date!=null) {
//                Calendar cal = Calendar.getInstance();
//                long timeSpan = (cal.getTimeInMillis() - date.getTime());
//                redis.set("timeSpan", timeSpan);
//                redis.set("userAgent", userAgent);
//            }
        JSONObject jsReturnKey = new JSONObject();
        //下面首先创建基本信息键值
        BaseUser userInfo = (BaseUser) iCommonService.getOneRecordById(USER_INFO, userId);
        if (userInfo == null) {
            SecurityUtils.getSubject().logout();
            throw BaseResponse.unAuthorization.error();
        }
        ViewBaseUser viewBaseUser = ((ViewBaseUser) iCommonService.getOneRecordById("ViewBaseUser", userInfo.getId()));
        JSONObject userInfoJSON = FastJsonUtil.toJson(userInfo);
        // BUG-01: 用户对应视图不存在时跳过，避免 NPE 导致登录接口 500
        if (viewBaseUser != null) {
            userInfoJSON.put("departmentName", viewBaseUser.getDepartmentName());
            userInfoJSON.put("jobName", viewBaseUser.getJobName());
            userInfoJSON.put("schoolId", viewBaseUser.getSchoolId());
        }
        // 学生端校内外实习类型：查 rel_intership_user 确认学生是否已被纳入实习项目
        String internshipType = null;
        try {
            List<RelIntershipUser> relList = relIntershipUserDao.findByUserIdAndIsDeletedFalse(userId);
            for (RelIntershipUser rel : relList) {
                BaseInternshipType internshipType1 = (BaseInternshipType)
                        iCommonService.getOneRecordById("BaseInternshipType",
                                FastJsonUtil.toJson(iCommonService.getOneRecordById(
                                        "MainInternship", rel.getInternshipId()))
                                        .getInteger("internshipTypeId"));
                if (internshipType1 == null) continue;
                // intTypeId: 1=校内实习, 2=校外实习
                if (Integer.valueOf(2).equals(internshipType1.getIntTypeId())) {
                    internshipType = "external";
                    break;
                } else if (Integer.valueOf(1).equals(internshipType1.getIntTypeId())) {
                    internshipType = "internal";
                    break;
                }
            }
        } catch (Exception e) {
            // EXC-01: 记录日志而非静默吞掉异常
            logger.warn("查询学生实习类型失败，userId={}: {}", userId, e.getMessage());
        }
        userInfoJSON.put("internshipType", internshipType);
        jsReturnKey.put("userInfo", userInfoJSON);
        //下面创建role、contestType和menuList的键值信息
        JSONObject jsRoleSearch = new JSONObject();
        jsRoleSearch.put("userId", userId);
        @SuppressWarnings("unchecked")
        Page<RelUserRole> rolePage = (Page<RelUserRole>)iCommonService.getSomeRecords("RelUserRole", jsRoleSearch);
        List<RelUserRole> roleInfoList = rolePage.getContent();
        
        // 获取角色ID集合（直接使用 SysRole.id，不再映射 ROLE_TABLE 常量）
        Set<Integer> roleIdSet = roleInfoList.stream().map(RelUserRole::getRoleId).collect(Collectors.toSet());
        jsReturnKey.put("roles", roleIdSet);

        //下面创建permissions键值信息
        // roleIdSet 已经在上面定义过了
        Set<String> permissions = new HashSet<>();
        //找到roles能够操作的所有菜单
        Object menuList = new ArrayList<>();
        if (!roleIdSet.isEmpty()) {
            Set<Integer> menuIdSet = new HashSet<>();
            JSONObject jsRoleMenuSearch = new JSONObject();
            Map<String, String> roleMenuMap = new HashMap<>(1);
            GeneralUtil.addInCondition(jsRoleMenuSearch, roleMenuMap, "roleId", roleIdSet);
            @SuppressWarnings("unchecked")
            Page<RelRoleMenu> roleMenuPage = (Page<RelRoleMenu>)iCommonService.getSomeRecords("RelRoleMenu", jsRoleMenuSearch, roleMenuMap);
            List<RelRoleMenu> roleMenuRelList = roleMenuPage.getContent();
//                tblAuthorizationDao.findByRoleIdInAndIsDeletedFalse(roleIdSet).forEach(authorizationInfo -> {
            roleMenuRelList.forEach(rolemenurel -> {
                menuIdSet.add(rolemenurel.getMenuId());
                if (rolemenurel.getAddFlag()) {
                    permissions.add(rolemenurel.getMenuId() + ":" + Constant.CREATE);
                }
                if (rolemenurel.getVisibleFlag()) {
                    permissions.add(rolemenurel.getMenuId() + ":" + Constant.RETRIEVE);
                }
                if (rolemenurel.getModifyFlag()) {
                    permissions.add(rolemenurel.getMenuId() + ":" + Constant.UPDATE);
                }
                if (rolemenurel.getDeleteFlag()) {
                    permissions.add(rolemenurel.getMenuId() + ":" + Constant.DELETE);
                }
            });
            // 然后返回能操作的节点
            @SuppressWarnings("unchecked")
            List<SysMenu> treeOriList = (List<SysMenu>)iCommonService.getRecordsByIds("SysMenu", menuIdSet);
            if (!treeOriList.isEmpty()) {
                List<SysMenu> treeList = TreeUtil.sortTree(treeOriList);
                List<Object> subNodes = dataTreeServiceImpl.getSubNodes(treeList, -1, false, "");
                menuList = subNodes;
            } else {
                menuList = treeOriList;
            }
        }
        jsReturnKey.put("permissions", permissions);
        jsReturnKey.put("menuList", menuList);
        redis.set(key, jsReturnKey, 0x12c);
        if (permissions.size() == 0) {
           return null;
        }
    }
    Object result = redis.get(key);
    return result;
}



    private Integer getDepartmentId(JSONObject json) {
        Integer departmentId = json.getInteger("departmentId");
        if (departmentId == null) {
            Integer departmentId1 =  json.getInteger("departmentId1");
            String departmentId2 =  json.getString("departmentId2");
            String departmentId3 =  json.getString("departmentId3");
            Matcher m3 = pattern.matcher(departmentId3);
            if (m3.matches()) {
                departmentId = Integer.parseInt(departmentId3);
            } else {
                Matcher m2 = pattern.matcher(departmentId2);
                if (m2.matches()) {
                    JSONObject node = new JSONObject();
                    node.put("name", departmentId3);
                    node.put("parentId", departmentId2);
                    JSONObject nodeInfo = FastJsonUtil.toJson(iDataTreeService.editOneNode("BaseDepartment", node)).getJSONObject("nodeInfo");
                    departmentId = nodeInfo.getInteger("id");
                } else {
                    JSONObject node1 = new JSONObject();
                    node1.put("name", departmentId2.trim());
                    node1.put("parentId", departmentId1);
                    JSONObject nodeInfo1 = new JSONObject();
                    @SuppressWarnings("unchecked")
                    Page<BaseDepartment> deptPage1 = (Page<BaseDepartment>)iCommonService.getSomeRecords("BaseDepartment",node1);
                    List<BaseDepartment> department1 = deptPage1.getContent();
                    if (department1.size()!=0) {
                        nodeInfo1 = FastJsonUtil.toJson(department1.get(0)).getJSONObject("nodeInfo");
                    } else {
                        nodeInfo1 = FastJsonUtil.toJson(iDataTreeService.editOneNode("BaseDepartment", node1)).getJSONObject("nodeInfo");
                    }
                    JSONObject node2 = new JSONObject();
                    node2.put("name", departmentId3.trim());
                    node2.put("parentId", nodeInfo1.getInteger("id"));
                    JSONObject nodeInfo2 = new JSONObject();
                    @SuppressWarnings("unchecked")
                    Page<BaseDepartment> deptPage2 = (Page<BaseDepartment>)iCommonService.getSomeRecords("BaseDepartment",node2);
                    List<BaseDepartment> department2 = deptPage2.getContent();
                    if (department2.size()!=0) {
                        nodeInfo2 = FastJsonUtil.toJson(department2.get(0)).getJSONObject("nodeInfo");
                    } else {
                        nodeInfo2 = FastJsonUtil.toJson(iDataTreeService.editOneNode("BaseDepartment", node2)).getJSONObject("nodeInfo");
                    }
                    departmentId = nodeInfo2.getInteger("id");
                }
            }
        }
        return departmentId;
    }

    /**
     * 用户注册
     */
    @Override
    public Object register(JSONObject json) {
        String password = json.getString("password");
        // SEC-02: 恢复密码强度校验
        if (!EncodeUtil.isStrongPwd(password)) {
            throw BaseResponse.moreInfoError.error("弱密码(应包含字母、特殊符号及数字且长度不小于8位)");
        }
        //查重
        String account = json.getString("account");
        String phone = json.getString("phone");
        JSONObject searchKeys = new JSONObject();
        searchKeys.put("account",account);
        @SuppressWarnings("unchecked")
        Page<BaseUser> userPage1 = (Page<BaseUser>)iCommonService.getSomeRecords("BaseUser",searchKeys);
        List<BaseUser> userList = userPage1.getContent();
        if (userList.size() != 0) {
            throw BaseResponse.moreInfoError.error("用户已存在");
        }
        searchKeys.clear();
        searchKeys.put("phone", phone);
        @SuppressWarnings("unchecked")
        Page<BaseUser> userPage2 = (Page<BaseUser>)iCommonService.getSomeRecords("BaseUser",searchKeys);
        userList = userPage2.getContent();
        if (userList.size()!=0) {
            throw BaseResponse.moreInfoError.error("用户已存在");
        }
        //注册
        // password 已在方法顶部声明
        String name = json.getString("name");
        Integer departmentId = getDepartmentId(json);
        Integer jobId = json.getInteger("jobId");

        JSONObject userInfo=new JSONObject();
        userInfo.put("phone", phone.trim());
        userInfo.put("account",account.trim());
//        userInfo.put("registerTime",new Date());
        if (name!=null) {
            userInfo.put("name", name.trim());
        }
        if (departmentId != null) {
            userInfo.put("departmentId", departmentId);
        }
        if (jobId != null) {
            userInfo.put("jobId", jobId);
        }
        // 先新增
        BaseUser userEntity = (BaseUser)iCommonService.saveOneRecord("BaseUser",userInfo);
        //再加密
        userInfo.put("id", userEntity.getId());
        userInfo.put("password",EncodeUtil.pwdShiro(password.trim(), userEntity.getId()));
        //再保存
        logger.info("用户注册 phone:{} account:{}", phone, userInfo.getString("account"));
        return iCommonService.saveOneRecord("BaseUser",userInfo);
    }


    /**
     * 登录
     * @param account
     * @param password
     * @param rememberMe
     */
    @Override
    public void login(String account, String password, Boolean rememberMe) {
        try {
            // 查用户 isDeleted=false  && (username == account || phone == account)
            JSONObject searchKeys = new JSONObject();
            searchKeys.put("account",account);
            @SuppressWarnings("unchecked")
            Page<BaseUser> userPage1 = (Page<BaseUser>)iCommonService.getSomeRecords("BaseUser",searchKeys);
            List<BaseUser> userInfo = userPage1.getContent();
            if (userInfo.size()==0) {
                searchKeys.clear();
                searchKeys.put("phone",account);
                @SuppressWarnings("unchecked")
                Page<BaseUser> userPage2 = (Page<BaseUser>)iCommonService.getSomeRecords("BaseUser",searchKeys);
                userInfo = userPage2.getContent();
                if (userInfo.size()==0) {
                    throw new UnknownAccountException();
                }
            }
            SecurityUtils.getSubject().login(new UsernamePasswordToken(userInfo.get(0).getId().toString(), password, rememberMe));
        } catch (UnknownAccountException | IncorrectCredentialsException e) {
            // 统一文案，避免用户名枚举（存在用户→密码错误 / 不存在→用户不存在）
            throw BaseResponse.moreInfoError.error("用户名或密码错误");
        } catch (Exception e) {
            LogUtil.error(logger, e);
            throw BaseResponse.notCaptured.error();
        }
    }
    /**
     * 修改/重置/新增密码。调用方传 userId、oldPassword、password、reset。
     * reset=true：按学工号自动生成新密码（SLSDsx# + 学工号后四位），忽略 oldPassword 与 password，不做弱密码校验；
     * reset=false 且 oldPassword 有值：修改密码，校验原密码与弱密码，且仅能改当前登录用户；
     * reset=false 且 oldPassword 为空：新增密码，不校验原密码与弱密码；password 为空时按学工号自动生成（同重置逻辑）。
     */
    @Override
    public void editPassword(String userId, String oldPassword, String password, boolean reset) {
        if (!StringUtils.hasText(userId)) {
            throw BaseResponse.moreInfoError.error("userId不能为空");
        }
        int targetUserId = Integer.parseInt(userId);
        BaseUser user = tblUserInfoDao.findById(targetUserId)
                .orElseThrow(() -> BaseResponse.moreInfoError.error("用户不存在"));
        String newPassword;
        if (reset) {
            newPassword = buildInitialPassword(user);
        } else {
            boolean isAppend = !StringUtils.hasText(oldPassword);
            if (isAppend && !StringUtils.hasText(password)) {
                newPassword = buildInitialPassword(user);
            } else {
                if (!StringUtils.hasText(password)) {
                    throw BaseResponse.moreInfoError.error("新密码不能为空");
                }
                newPassword = password.trim();
                if (!isAppend) {
                    if (!EncodeUtil.isStrongPwd(password)) {
                        throw BaseResponse.moreInfoError.error("弱密码(应包含字母、特殊符号及数字且长度不小于8位)");
                    }
                    if (!Objects.equals(targetUserId, getLoginUserId())) {
                        throw BaseResponse.moreInfoError.error("只能修改当前登录用户密码");
                    }
                    String encryptedOld = EncodeUtil.pwdShiro(oldPassword.trim(), userId);
                    if (!encryptedOld.equals(user.getPassword())) {
                        throw BaseResponse.moreInfoError.error("原密码错误");
                    }
                }
            }
        }
        JSONObject obj = FastJsonUtil.toJson(user);
        obj.put("password", EncodeUtil.pwdShiro(newPassword, userId));
        iCommonService.saveOneRecord("BaseUser", obj);
    }

    private String buildInitialPassword(BaseUser user) {
        try {
            return EncodeUtil.buildInitialPasswordFromWorkId(user.getWorkId());
        } catch (IllegalArgumentException e) {
            throw BaseResponse.moreInfoError.error(e.getMessage());
        }
    }


    @Override
    public Object userList() {
        return tblUserInfoDao.findByIsDeletedFalse();
    }

    @Override
    public void deleteUser(String phone) {
        List<BaseUser> userInfo = tblUserInfoDao.findByPhoneAndIsDeletedFalse(phone);
        userInfo.forEach(u -> iCommonService.deleteRecordByDelflag(USER_INFO, u.getId()));
    }

    @Override
    public Object userRoleSet(Integer[] roleSet, Integer userId) {
        // DATA-01: roleSet 为 null 时先删后不写，导致角色数据丢失
        if (roleSet == null) {
            throw BaseResponse.parameterInvalid.error("角色列表不能为空");
        }
        //通过userId查找出对应的 UserRoleRel Ids,然后删除
        JSONObject searchKeys = new JSONObject();
        searchKeys.put("userId",userId);
        Object oldUserRoleSet = iCommonService.getSomeRecords("RelUserRole",searchKeys);
        ArrayList<Integer> oldUserRoleIds = new ArrayList<>();
        @SuppressWarnings("unchecked")
        Page<Object> oldUserRolePage = (Page<Object>)oldUserRoleSet;
        for(Object obj:oldUserRolePage.getContent()){
            oldUserRoleIds.add(FastJsonUtil.toJson(obj).getIntValue("id"));
        }
        @SuppressWarnings("null")
        List<RelUserRole> rolesToDelete = tblUserRoleRelDao.findByIdInAndIsDeletedFalse(oldUserRoleIds);
        tblUserRoleRelDao.deleteAll(rolesToDelete);
        //保存新的记录
        List<RelUserRole> userRoleRels = new ArrayList<>();
        Arrays.stream(roleSet).forEach(integer -> {
            RelUserRole userRoleRel = new RelUserRole();
            userRoleRel.setRoleId(integer);
            userRoleRel.setUserId(userId);
            userRoleRels.add(userRoleRel);
        });
        return tblUserRoleRelDao.saveAll(userRoleRels);
    }

    /**
     * 获取所有未删除的用户信息，并添加所用所拥有的roleIds
     * @param pageInfo
     * @param sortJson
     * @return
     */
    @Override
    public Object getUserListIsNotDelete(JSONObject pageInfo, JSONObject sortJson) {
        Sort sort = GeneralUtil.getSortInfo(sortJson);
        Object res = iDataListService.getSomeRecords(USER_INFO, null, null, sort, pageInfo.getInteger("page"), pageInfo.getInteger("size"));
        JSONObject resJson = FastJsonUtil.toJson(res);
        // PERF-01: 批量查询当前页所有用户的角色，避免 N+1 查询
        List<Integer> userIds = new ArrayList<>();
        for (Object obj : resJson.getJSONArray("content")) {
            userIds.add(FastJsonUtil.toJson(obj).getInteger("id"));
        }
        Map<Integer, Set<Integer>> userRoleMap = loadRoleIdsForUsers(userIds);
        for (Object obj : resJson.getJSONArray("content")) {
            int userId = FastJsonUtil.toJson(obj).getInteger("id");
            ((JSONObject) obj).put("roleIds", userRoleMap.getOrDefault(userId, new HashSet<>()));
        }
        return resJson;
    }

    /**
     * 编辑用户信息的内容
     * @param json
     * @param userId
     * @return
     */
    @Override
    public Object editUserInfo( JSONObject json, Integer userId){
        BaseUser userInfo = json.toJavaObject(BaseUser.class);

        // 检查部门是否变更（部门变更可能影响 schoolId）
        Integer newDepartmentId = userInfo.getDepartmentId();
        BaseUser oldUserInfo = tblUserInfoDao.findById(userInfo.getId()).orElse(null);
        Integer oldDepartmentId = oldUserInfo != null ? oldUserInfo.getDepartmentId() : null;
        boolean departmentChanged = (newDepartmentId != null && !newDepartmentId.equals(oldDepartmentId))
                || (oldDepartmentId != null && !oldDepartmentId.equals(newDepartmentId));

        // 更新redis缓存的用户信息
        String key = APPLICATION_NAME + ":userInfo:" + userInfo.getId();
        redis.delete(key);
        //返回给前端头像的headImage 和fileId
        JSONObject searchKey = new JSONObject();
        searchKey.put("relationIds", getLoginUserId());
        searchKey.put("tableName", "base_user");
        @SuppressWarnings("unchecked")
        Page<SysOssFile> ossFilePage = (Page<SysOssFile>)iCommonService.getSomeRecords(OSS_FILE_INFO, searchKey);
        List<SysOssFile> tblOssFileInfos = ossFilePage.getContent();
        SysOssFile tblOssFileInfo;
        if(tblOssFileInfos.size()!=0){
            tblOssFileInfo=tblOssFileInfos.get(0);
            // userInfo.setHeadImage(ossUtil.getUrl(tblOssFileInfo.getId()));
            userInfo.setOssFileId(tblOssFileInfo.getId());
        }
        tblUserInfoDao.save(userInfo);

        // 部门变更后，待外层事务提交后再刷新，避免 REQUIRES_NEW 读到未提交的旧 departmentId
        if (departmentChanged) {
            Integer targetUserId = userInfo.getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        iVerifyProcessService.refreshPendingVerifyUsersByUser(targetUserId);
                    } catch (Exception e) {
                        logger.warn("刷新待审核记录失败，不影响用户信息保存: {}", e.getMessage());
                    }
                }
            });
        }

        return userInfo;
    }

    /**
     * @param tblName
     * @param sort
     * @param page
     * @param size
     * @Des 用户获取List
     * @Author yukai
     * @Date 2020/12/10 16:14
     */
    @Override
    public Object tblUserGetList(String tblName, JSONObject searchKeys, Map<String, String> repMap, Sort sort, Integer page, Integer size) {
        Object res = iCommonService.getSomeRecords(tblName, searchKeys, repMap, sort, page, size);
        JSONObject resJson = FastJsonUtil.toJson(res);
        JSONObject searchKey2 = new JSONObject();
        searchKey2.put("isDeleted", false);
        @SuppressWarnings("unchecked")
        Page<BaseJobPosition> jobPage = (Page<BaseJobPosition>) iCommonService.getSomeRecords("BaseJobPosition", searchKey2);
        Map<Integer, String> jobMap = jobPage.getContent().stream()
                .collect(Collectors.toMap(BaseJobPosition::getId, BaseJobPosition::getName));
        // BUG-03: 使用独立的 departmentMap 而非错误地从 jobMap 取值
        @SuppressWarnings("unchecked")
        Page<BaseDepartment> deptPage = (Page<BaseDepartment>) iCommonService.getSomeRecords("BaseDepartment", searchKey2);
        Map<Integer, String> departmentMap = deptPage.getContent().stream()
                .collect(Collectors.toMap(BaseDepartment::getId, BaseDepartment::getName));

        // PERF-02: 批量查询所有用户角色，避免 N+1 查询
        List<Integer> userIds = new ArrayList<>();
        for (Object obj : resJson.getJSONArray("content")) {
            userIds.add(FastJsonUtil.toJson(obj).getInteger("id"));
        }
        Map<Integer, Set<Integer>> userRoleIdsMap = loadRoleIdsForUsers(userIds);
        Set<Integer> allRoleIds = userRoleIdsMap.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
        // 批量查询角色名称
        Map<Integer, String> roleNameMap = new HashMap<>();
        if (!allRoleIds.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<SysRole> allRoleInfos = (List<SysRole>) iCommonService.getRecordsByIds("SysRole", allRoleIds, false);
            for (SysRole r : allRoleInfos) {
                roleNameMap.put(r.getId(), r.getName());
            }
        }

        for (Object obj : resJson.getJSONArray("content")) {
            int userId = FastJsonUtil.toJson(obj).getInteger("id");
            Set<Integer> roleIds = userRoleIdsMap.getOrDefault(userId, new HashSet<>());
            ((JSONObject) obj).put("roleIds", roleIds);

            // BUG-02: 空角色列表时跳过 substring，避免越界
            StringBuilder role = new StringBuilder();
            for (Integer rid : roleIds) {
                String roleName = roleNameMap.get(rid);
                if (roleName != null) {
                    role.append(roleName).append("&");
                }
            }
            if (role.length() > 0) {
                role.deleteCharAt(role.length() - 1);
            }
            ((JSONObject) obj).put("role", role.toString());

            Integer jobId = FastJsonUtil.toJson(obj).getInteger("jobId");
            if (jobId != null) {
                ((JSONObject) obj).put("job", jobMap.get(jobId));
            }
            // BUG-03: 使用 departmentMap 而非 jobMap
            Integer departmentId = FastJsonUtil.toJson(obj).getInteger("departmentId");
            if (departmentId != null) {
                ((JSONObject) obj).put("departmentName", departmentMap.get(departmentId));
            }
        }
        return resJson;
    }

    /**
     * @param tblName
     * @param sort
     * @param page
     * @param size
     * @Des 用户获取List
     * @Author yukai
     * @Date 2020/12/10 16:14
     */
    @Override
    public Object ViewUserGetList(String tblName, JSONObject searchKeys, Map<String, String> repMap, Sort sort, Integer page, Integer size) {
        if(searchKeys.containsKey("roleIds")&&repMap.containsKey("roleIds")){
            JSONObject roleSearchKey = new JSONObject();
            Map<String, String> roleMap = new HashMap<>();
            roleSearchKey.put("roleId",searchKeys.getString("roleIds"));
            roleMap.put("roleId",repMap.get("roleIds"));
            @SuppressWarnings("unchecked")
            Page<RelUserRole> userRolePage = (Page<RelUserRole>)iCommonService.getSomeRecords("RelUserRole", roleSearchKey, roleMap, Sort.unsorted());
            List<RelUserRole> userRoleRelList = userRolePage.getContent();
            Set<Integer> userIdSet = userRoleRelList.stream().map(RelUserRole::getUserId).collect(Collectors.toSet());
            searchKeys.remove("roleIds");
            repMap.remove("roleIds");
            // BUG-04: userIdSet 为空时 substring(0,-1) 越界；用不可能匹配的条件返回空结果
            if (userIdSet.isEmpty()) {
                searchKeys.put("id", "-1");
                repMap.put("id", Constant.EQ);
            } else {
                StringBuilder stringBuilder = new StringBuilder();
                for (Integer userId : userIdSet) {
                    stringBuilder.append(userId).append(",");
                }
                searchKeys.put("id", stringBuilder.substring(0, stringBuilder.length() - 1));
                repMap.put("id", "()");
            }
        }
        Object res = iCommonService.getSomeRecords(tblName, searchKeys, repMap, sort, page, size);
        JSONObject resJson = FastJsonUtil.toJson(res);
        for(Object obj : resJson.getJSONArray("content")) {
            Set<Integer> roleIds = new HashSet<>();
            // BUG-06: roleIds 字段为 null 时直接 split 会 NPE
            String roleIdsStr = FastJsonUtil.toJson(obj).getString("roleIds");
            if (roleIdsStr != null && !roleIdsStr.isEmpty()) {
                for (String c : roleIdsStr.split(SPLIT_OPERATOR.COMMA)) {
                    try {
                        roleIds.add(Integer.parseInt(c.trim()));
                    } catch (NumberFormatException ignored) {}
                }
            }
            ((JSONObject)obj).put("roleIds",roleIds);
        }
        return resJson;
    }

    @Resource
    private MinIOUtils minIOUtils;

    /**
     * 用户上传头像：上传至 MinIO，软删除旧头像记录，更新 BaseUser.ossFileId
     */
    @Override
    public Object uploadAvatar(MultipartFile file) {
        Integer userId = getLoginUserId();
        try {
            // 删除旧头像（MinIO 对象 + DB 软删除）
            List<SysOssFile> oldFiles = sysOssFileDao
                    .findByRelationIdsAndTableNameAndIsDeletedFalse(userId, "base_user");
            if (!oldFiles.isEmpty()) {
                List<String> oldPaths = oldFiles.stream()
                        .map(SysOssFile::getOssPath)
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toList());
                if (!oldPaths.isEmpty()) {
                    minIOUtils.removeObjects(Constant.BUCKET_NAME, oldPaths);
                }
                sysOssFileDao.updateByIds(
                        oldFiles.stream().map(SysOssFile::getId).collect(Collectors.toList()));
            }

            // 上传新头像
            List<com.alibaba.fastjson.JSONObject> result =
                    minIOUtils.upload(new MultipartFile[]{file}, userId, "base_user", userId);
            if (result.isEmpty()) throw BaseResponse.moreInfoError.error("头像上传失败");

            com.alibaba.fastjson.JSONObject fileJson = result.get(0);
            Integer newOssFileId = fileJson.getInteger("id");

            // 更新 BaseUser.ossFileId
            BaseUser user = tblUserInfoDao.findById(userId).orElse(null);
            if (user != null) {
                user.setOssFileId(newOssFileId);
                tblUserInfoDao.save(user);
            }
            return fileJson;
        } catch (Exception e) {
            logger.error("头像上传失败", e);
            throw BaseResponse.moreInfoError.error("头像上传失败: " + e.getMessage());
        }
    }
    @Override
    public Object getUserRoles(String userId) {
        List<RelUserRole> relations = getRelUserRolesByUserId(userId);
        Set<Integer> roleIdSet = relations.stream().map(RelUserRole::getRoleId).collect(Collectors.toSet());
        if (roleIdSet.isEmpty()) return new ArrayList<>();
        JSONObject searchKey = new JSONObject();
        Map<String, String> repMap = new HashMap<>();
        GeneralUtil.addInCondition(searchKey, repMap, "id", roleIdSet);
        @SuppressWarnings("unchecked")
        Page<SysRole> rolesPage = (Page<SysRole>) iCommonService.getSomeRecords("SysRole", searchKey, repMap);
        return rolesPage.getContent();
    }
    @Override
    public Object saveUserRoles(String userId, Integer[] roleIds) {
        // 先查看下有否修改
        List<RelUserRole> relations = getRelUserRolesByUserId(userId);
        @SuppressWarnings("unused")
        List<Integer> oldIds = relations.stream().map(RelUserRole::getId).collect(Collectors.toList());
        Set<Integer> oldRoleIdSet = relations.stream().map(RelUserRole::getRoleId).collect(Collectors.toSet());
        Set<Integer> roleIdSet = Arrays.stream(roleIds).collect(Collectors.toSet());
        // 如果有修改
        if (!oldRoleIdSet.equals(roleIdSet)) {
            //旧的有新的没有，删除
            for (int i=0;i<relations.size();i++) {
                if (!roleIdSet.contains(relations.get(i).getRoleId())) {
                    iCommonService.deleteRecordByDelflag("RelUserRole",relations.get(i).getId());
                }
            }
            //旧的没有新的有，新增
            List<RelUserRole> userRoleRels = new ArrayList<>();
            for (int i=0;i<roleIdSet.size();i++) {
                if (!oldRoleIdSet.contains(roleIdSet.toArray()[i])) {
                    RelUserRole userRoleRel = new RelUserRole();
                    userRoleRel.setRoleId((Integer)roleIdSet.toArray()[i]);
                    userRoleRel.setUserId(Integer.valueOf(userId));
                    userRoleRels.add(userRoleRel);
                }
            }
            Object res = iCommonService.saveSomeRecords("RelUserRole", userRoleRels);

            // 必须在本事务提交后再刷新：refreshPendingVerifyUsersByUser 使用 REQUIRES_NEW 新事务，
            // 若在提交前调用，新事务读不到当前事务未提交的 RelUserRole 记录，导致新角色不生效。
            Integer userIdInt = Integer.valueOf(userId);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        iVerifyProcessService.refreshPendingVerifyUsersByUser(userIdInt);
                    } catch (Exception e) {
                        logger.warn("刷新待审核记录失败，不影响角色保存: {}", e.getMessage());
                    }
                }
            });

            return res;
        } else {
            return null;
        }
    }


    private Map<Integer, Set<Integer>> loadRoleIdsForUsers(List<Integer> userIds) {
        Map<Integer, Set<Integer>> userRoleMap = new HashMap<>();
        if (userIds.isEmpty()) return userRoleMap;
        JSONObject searchKeys = new JSONObject();
        Map<String, String> regMap = new HashMap<>();
        GeneralUtil.addInCondition(searchKeys, regMap, "userId", userIds);
        @SuppressWarnings("unchecked")
        Page<Object> rolePage = (Page<Object>) iCommonService.getSomeRecords(
                "RelUserRole", searchKeys, regMap, Sort.unsorted(), 1, userIds.size() * 20 + 100);
        for (Object roleObj : rolePage.getContent()) {
            JSONObject roleJson = FastJsonUtil.toJson(roleObj);
            Integer uid = roleJson.getInteger("userId");
            Integer rid = roleJson.getInteger("roleId");
            if (uid != null && rid != null) {
                userRoleMap.computeIfAbsent(uid, k -> new HashSet<>()).add(rid);
            }
        }
        return userRoleMap;
    }

    private List<RelUserRole> getRelUserRolesByUserId(String userId) {
        JSONObject searchKey = new JSONObject();
        searchKey.put("userId", userId);
        @SuppressWarnings("unchecked")
        Page<RelUserRole> relationsPage = (Page<RelUserRole>) iCommonService.getSomeRecords("RelUserRole", searchKey);
        return relationsPage.getContent();
    }

    @Override
    public Object getDepartment(String parentId) {
        JSONObject searchKey = new JSONObject();
        Matcher m = pattern.matcher(parentId);
        if (m.matches()) {
            searchKey.put("parentId", parentId);
        } else {
            searchKey.put("parentId", 0);
        }
        return iCommonService.getSomeRecords("BaseDepartment", searchKey,null, Sort.by(Sort.Direction.ASC, "theOrder"));

    }


}

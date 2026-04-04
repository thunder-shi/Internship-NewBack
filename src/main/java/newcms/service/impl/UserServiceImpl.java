package newcms.service.impl;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import newcms.base.Base;
import newcms.base.BaseResponse;
import newcms.base.Constant;
import newcms.entity.db.*;
import newcms.repository.db.BaseUserDao;
import newcms.repository.db.RelUserRoleDao;
import newcms.repository.db.ViewRelStuInternshipPostDao;
import newcms.repository.db.ViewRelTitleTeacherStudentDao;
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
    private ViewRelStuInternshipPostDao viewRelStuInternshipPostDao;
    @Resource
    private ViewRelTitleTeacherStudentDao viewRelTitleTeacherStudentDao;

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
        userInfoJSON.put("departmentName", viewBaseUser.getDepartmentName());
        userInfoJSON.put("jobName", viewBaseUser.getJobName());
        userInfoJSON.put("schoolId", viewBaseUser.getSchoolId());
        // 学生端校内外实习类型（非学生用户返回 null）
        String internshipType = null;
        try {
            if (!viewRelStuInternshipPostDao.findByStudentIdAndIsDeletedFalse(userId).isEmpty()) {
                internshipType = "external";
            } else if (!viewRelTitleTeacherStudentDao.findByStuIdAndIsDeletedFalse(userId).isEmpty()) {
                internshipType = "internal";
            }
        } catch (Exception ignored) {}
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
            jsRoleMenuSearch.put("roleId", StringUtils.collectionToDelimitedString(roleIdSet, ","));
            Map<String, String> roleMenuMap = new HashMap<>(1);
            roleMenuMap.put("roleId", Constant.IN);
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
        //首先校验密码
//        if (!EncodeUtil.isStrongPwd(password)) {
//            throw BaseResponse.moreInfoError.error("弱密码(应包含大小写字母、特殊符号及数字且长度大于8位)");
//        }
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
        String password = json.getString("password");
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
        } catch (UnknownAccountException e) {
            throw BaseResponse.moreInfoError.error("用户不存在");
        } catch (IncorrectCredentialsException e) {
            throw BaseResponse.moreInfoError.error("密码错误！");
        } catch (Exception e) {
            LogUtil.error(logger, e);
            throw BaseResponse.notCaptured.error();
        }
    }
    /**
     * 修gai密码
     * @param password
     */
    @Override
    public void editPassword(String userId, String password) {

        JSONObject obj = FastJsonUtil.toJson(iCommonService.getOneRecordById("BaseUser",Integer.parseInt(userId)));
        obj.put("Password",EncodeUtil.pwdShiro(password.trim(), userId));
        iCommonService.saveOneRecord("BaseUser",obj);
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
        for(Object obj : resJson.getJSONArray("content")){
            int userId = FastJsonUtil.toJson(obj).getInteger("id");
            //按userId查找TblUserRoleRel
            JSONObject searchKeys = new JSONObject();
            searchKeys.put("userId", userId);
            JSONObject pagInfo2 = new JSONObject();
            pagInfo2.put("page", -1);
            pagInfo2.put("size", 10);
            Object obj1 = iDataListService.getSomeRecords("RelUserRole", searchKeys, null, null, GeneralUtil.getPageInfo(pagInfo2).get("page"), GeneralUtil.getPageInfo(pagInfo2).get("size"));
            Set<Integer> roleIds = new HashSet<>();
            @SuppressWarnings("unchecked")
            ArrayList<Object> obj1List = (ArrayList<Object>)obj1;
            for(Object obj2: obj1List){
                roleIds.add(FastJsonUtil.toJson(obj2).getInteger("roleId"));
            }
            ((JSONObject)obj).put("roleIds",roleIds);
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

        // 部门变更后，刷新相关的待审核记录
        if (departmentChanged) {
            try {
                iVerifyProcessService.refreshPendingVerifyUsersByUser(userInfo.getId());
            } catch (Exception e) {
                logger.warn("刷新待审核记录失败，不影响用户信息保存: {}", e.getMessage());
            }
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
        Page<BaseJobPosition> jobPage = (Page<BaseJobPosition>)iCommonService.getSomeRecords("BaseJobPosition",searchKey2);
        List<BaseJobPosition> jobInfoList = jobPage.getContent();
        // Page<BaseDepartment> deptPage = (Page<BaseDepartment>)iCommonService.getSomeRecords("BaseDepartment",searchKey2);
        // List<BaseDepartment> departmentInfoList = deptPage.getContent();
        Map<Integer, String> jobMap = jobInfoList.stream().collect(Collectors.toMap(BaseJobPosition::getId, BaseJobPosition::getName));
        // Map<Integer, String> departmentMap = departmentInfoList.stream().collect(Collectors.toMap(BaseDepartment::getId, BaseDepartment::getName));
        for(Object obj : resJson.getJSONArray("content")) {
            int userId = FastJsonUtil.toJson(obj).getInteger("id");
            //按userId查找TblUserRoleRel
            JSONObject searchKey1 = new JSONObject();
            searchKey1.put("userId", userId);
            @SuppressWarnings("unchecked")
            Page<Object> rolePage = (Page<Object>)iCommonService.getSomeRecords("RelUserRole", searchKey1);
            Object obj1 = rolePage.getContent();
            Set<Integer> roleIds = new HashSet<>();
            @SuppressWarnings("unchecked")
            ArrayList<Object> obj1List = (ArrayList<Object>)obj1;
            for(Object obj2: obj1List){
                roleIds.add(FastJsonUtil.toJson(obj2).getInteger("roleId"));
            }
            ((JSONObject)obj).put("roleIds",roleIds);

            @SuppressWarnings("unchecked")
            List<SysRole> roleInfos = (List<SysRole>) iCommonService.getRecordsByIds("SysRole", roleIds, false);
            StringBuilder role = new StringBuilder();
            for (SysRole roleInfo : roleInfos) {
                role.append(roleInfo.getName()).append("&");
            }
            role = new StringBuilder(role.substring(0, role.length() - 1));
            ((JSONObject)obj).put("role",role);

            Integer jobId =  FastJsonUtil.toJson(obj).getInteger("jobId");
            if (jobId!=null){
                ((JSONObject)obj).put("job",jobMap.get(jobId));
            }
            Integer departmentId =  FastJsonUtil.toJson(obj).getInteger("departmentId");
            if (departmentId!=null){
                ((JSONObject)obj).put("departmentName",jobMap.get(jobId));
            }
        }
        Object rawRet = resJson;
        return rawRet;
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
            StringBuilder stringBuilder = new StringBuilder();
            for (Integer userId : userIdSet) {
                stringBuilder.append(userId).append(",");
            }
            searchKeys.put("id",stringBuilder.substring(0,stringBuilder.length()-1));
            repMap.put("id","()");
            searchKeys.remove("roleIds");
            repMap.remove("roleIds");
        }
        Object res = iCommonService.getSomeRecords(tblName, searchKeys, repMap, sort, page, size);
        JSONObject resJson = FastJsonUtil.toJson(res);
        for(Object obj : resJson.getJSONArray("content")) {
            Set<Integer> roleIds = new HashSet<>();
            String[] str = FastJsonUtil.toJson(obj).getString("roleIds").split(SPLIT_OPERATOR.COMMA);
            for(String c:str){
                roleIds.add(Integer.parseInt(c));
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
        JSONObject searchKey = new JSONObject();
        searchKey.put("userId", userId);
        @SuppressWarnings("unchecked")
        Page<RelUserRole> relationsPage = (Page<RelUserRole>) iCommonService.getSomeRecords("RelUserRole", searchKey);
        List<RelUserRole> relations = relationsPage.getContent();
        searchKey.clear();
        Set<Integer> RoleIdSet = relations.stream().map(RelUserRole::getRoleId).collect(Collectors.toSet());
        searchKey.put("id", StringUtils.collectionToDelimitedString(RoleIdSet, ","));
        Map<String, String> repMap=new HashMap<>();
        repMap.put("id", Constant.IN);
        @SuppressWarnings("unchecked")
        Page<SysRole> rolesPage = (Page<SysRole>)iCommonService.getSomeRecords("SysRole", searchKey, repMap);
        List<SysRole> roles = rolesPage.getContent();
        return roles;
    }
    @Override
    public Object saveUserRoles(String userId, Integer[] roleIds) {
        // 先查看下有否修改
        JSONObject searchKey = new JSONObject();
        searchKey.put("userId", userId);
        @SuppressWarnings("unchecked")
        Page<RelUserRole> relationsPage = (Page<RelUserRole>) iCommonService.getSomeRecords("RelUserRole", searchKey);
        List<RelUserRole> relations = relationsPage.getContent();
        searchKey.clear();
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

            // 用户角色变更后，刷新相关的待审核记录（异步执行，避免影响主流程）
            try {
                iVerifyProcessService.refreshPendingVerifyUsersByUser(Integer.valueOf(userId));
            } catch (Exception e) {
                logger.warn("刷新待审核记录失败，不影响角色保存: {}", e.getMessage());
            }

            return res;
        } else {
            return null;
        }
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

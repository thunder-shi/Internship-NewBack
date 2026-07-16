package newcms.service.impl;

import newcms.base.Base;
import newcms.base.BaseResponse;
import newcms.base.Constant;
import newcms.entity.base.BaseTreeInfo;
import newcms.entity.db.BaseDepartment;
import newcms.entity.db.RelUserRole;
import newcms.entity.db.SysRole;
import newcms.repository.db.RelUserRoleDao;
import newcms.service.ICommonService;
import newcms.service.IDataTreeService;
import newcms.utils.DateUtil;
import newcms.utils.FastJsonUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author hongzhangming
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class DataTreeServiceImpl extends Base implements IDataTreeService {
    @Resource
    protected ICommonService iCommonService;
    @Resource
    private RelUserRoleDao relUserRoleDao;

    //树接口多结果查询排序默认按theOrder
    private final Sort orderSort = Sort.by(Sort.Direction.ASC, "theOrder");

    // ===================== 部门树访问范围控制 =====================

    /** 封装用户对部门树的可见范围 */
    private static class DeptScope {
        /** null 表示超级管理员，无任何限制 */
        final Integer rootId;
        /** true=rootId 节点及其全部子树；false=仅 rootId 节点本身 */
        final boolean includeSubtree;

        private DeptScope(Integer rootId, boolean includeSubtree) {
            this.rootId = rootId;
            this.includeSubtree = includeSubtree;
        }

        static DeptScope noRestriction()           { return new DeptScope(null, true); }
        static DeptScope subtree(Integer rootId)   { return new DeptScope(rootId, true); }
        static DeptScope singleNode(Integer rootId){ return new DeptScope(rootId, false); }

        boolean isRestricted() { return rootId != null; }
    }

    /**
     * 根据当前登录用户的角色，计算其对 BaseDepartment 树的可见范围。
     * <p>
     * 注意：Shiro Realm 的 doGetAuthorizationInfo 未加载角色，不能用 subject.hasRole()。
     * 角色通过 RelUserRole → SysRole.code 查询，与 DB 里的角色 ID 无关，只依赖 code 字符串。
     * </p>
     */
    private DeptScope getDeptScope() {
        Integer userId = getLoginUserId();

        // 查当前用户的所有角色 code（RelUserRole → SysRole.code）
        Set<String> roleCodes = relUserRoleDao.findByUserIdAndIsDeletedFalse(userId)
                .stream()
                .map(rel -> tblRoleInfoDao.findById(rel.getRoleId())
                        .map(role -> role.getCode())
                        .orElse(null))
                .filter(code -> code != null)
                .collect(Collectors.toCollection(HashSet::new));

        // 超级管理员：全量，不限制
        if (roleCodes.contains(Constant.USER_JOB_CODE.SUPER_ADMIN)) {
            return DeptScope.noRestriction();
        }

        Integer deptId = getLoginDepartmentId();
        if (deptId == null || deptId <= 0) {
            return DeptScope.noRestriction(); // 未设置部门，放行
        }

        // 校级管理员 / 教务管理员：本校全树（向上溯源到根节点）
        if (roleCodes.contains(Constant.USER_JOB_CODE.SCHOOL_ADMIN)
                || roleCodes.contains(Constant.USER_JOB_CODE.ACADEMIC_AFFAIRS_ADMIN)) {
            return DeptScope.subtree(findSchoolRootId(deptId));
        }

        // 院系管理员 / 校内教师：本院子树
        if (roleCodes.contains(Constant.USER_JOB_CODE.DEPARTMENT_ADMIN)
                || roleCodes.contains(Constant.USER_JOB_CODE.SCHOOL_TEACHER)) {
            return DeptScope.subtree(deptId);
        }

        // 企业管理员 / 企业导师 / 学生：仅本节点（不含子树）
        if (roleCodes.contains(Constant.USER_JOB_CODE.COMPANY_ADMIN)
                || roleCodes.contains(Constant.USER_JOB_CODE.COMPANY_TUTOR)
                || roleCodes.contains(Constant.USER_JOB_CODE.STUDENT)) {
            return DeptScope.singleNode(deptId);
        }

        return DeptScope.noRestriction(); // 未知角色，放行
    }

    /**
     * 从 deptId 向上遍历，直到找到根节点（parentId == -1）作为"学校"入口。
     */
    private Integer findSchoolRootId(Integer deptId) {
        int maxDepth = 20;
        Integer cur = deptId;
        while (cur != null && cur > 0 && maxDepth-- > 0) {
            BaseDepartment dept = tblDepartmentInfoDao.findById(cur).orElse(null);
            if (dept == null) return deptId;
            Integer pid = dept.getParentId();
            if (pid == null || pid == -1) return cur;
            cur = pid;
        }
        return deptId;
    }

    /**
     * 判断 tblName 是否为部门树（需要权限限制）。
     */
    private boolean isDeptTblName(String tblName) {
        return Constant.DEPARTMENT_INFO.equals(tblName) || "ViewBaseDepartment".equals(tblName);
    }

    /**
     * 构建 scopeRootId 子树的全部节点 ID 集合（总是用 BaseDepartment 实体查询）。
     */
    private Set<Integer> buildAllowedIds(Integer scopeRootId) {
        List<Integer> ids = new ArrayList<>();
        getAllChildIdsRecursive(scopeRootId, Constant.DEPARTMENT_INFO, ids);
        return new HashSet<>(ids);
    }

    /**
     * 校验 nodeId 在授权范围内；不在则抛 401。
     */
    private void requireInScope(Integer nodeId, DeptScope scope) {
        if (!scope.isRestricted()) return;
        if (!scope.includeSubtree) {
            if (!scope.rootId.equals(nodeId)) {
                throw BaseResponse.unAuthorization.error("无权操作该节点");
            }
        } else {
            if (!buildAllowedIds(scope.rootId).contains(nodeId)) {
                throw BaseResponse.unAuthorization.error("无权操作该节点");
            }
        }
    }

    // ===================== 原有树形结构辅助方法 =====================

    //region 一些private
    public List<Object> getSubNodes(Object wholeObj, Integer parentId, Boolean lazy, String preName) {
        return privGetSubNodes(wholeObj, parentId, lazy, preName);
    }
    @SuppressWarnings("unchecked")
    private List<Object> privGetSubNodes(Object wholeObj, Integer parentId, Boolean lazy, String preStr) {
        List<Object> tree = new ArrayList<>();
        List<BaseTreeInfo<Object>> res = (List<BaseTreeInfo<Object>>) wholeObj;
        for (BaseTreeInfo<Object> nowObj : res) {
            if (nowObj.getParentId().equals(parentId)) {
                String nowStr = "";
                if (!preStr.equals("")) {
                    nowStr = preStr + "-" + nowObj.getName();
                } else {
                    nowStr = nowObj.getName();
                }
                nowObj.setAllNodeNames(nowStr);
                if (lazy) {
                    nowObj.setHasChildren(!nowObj.getIsLeaf());
                }
                else {
                    if (nowObj.getIsLeaf().equals(false)) {
                        nowObj.setChildren(privGetSubNodes(wholeObj, nowObj.getId(), lazy, nowStr));
                    }
                }
                //nowObj转换json过程中自动去除值为null的字段，这样最后一级就没有children字段了
                tree.add(FastJsonUtil.toJson(nowObj));
            }
        }
        return tree;
    }
    private void getArraySubNodes(ArrayList<Object> tree, List<BaseTreeInfo<Object>> wholeObj, Integer parentId) {
        privGetArraySubNodes(tree, wholeObj, parentId, "");
    }
    private void privGetArraySubNodes(ArrayList<Object> tree, List<BaseTreeInfo<Object>> wholeObj, Integer parentId, String preStr) {
        ArrayList<BaseTreeInfo<Object>> res = (ArrayList<BaseTreeInfo<Object>>) wholeObj;
        for (BaseTreeInfo<Object> nowObj : res) {
            if (nowObj.getParentId().equals(parentId)) {
                String nowStr = "";
                if (preStr != "") {
                    nowStr = preStr + "/" + nowObj.getName();
                } else {
                    nowStr = nowObj.getName();
                }
                nowObj.setAllNodeNames(nowStr);
                // nowObj.setHasChildren(nowObj.getIsLeaf());
                tree.add(nowObj);
                if (nowObj.getIsLeaf().equals(false)) {
                    privGetArraySubNodes(tree, wholeObj, nowObj.getId(), nowStr);
                }
            }
        }
    }
    //endregion

    // ===================== getTreeList（含角色权限控制） =====================

    /**
     * 查询树结构
     */
    @Override
    public Object getTreeList(String tblName, Boolean virtualRootFlag, JSONObject searchKey, Boolean lazy, String preName, Integer parentId, Sort sortJson) {

        // --- 1. 计算访问范围 ---
        DeptScope scope = isDeptTblName(tblName) ? getDeptScope() : DeptScope.noRestriction();

        // --- 2. 有范围限制时的分支处理 ---
        if (scope.isRestricted()) {

            if (lazy) {
                if (searchKey == null) searchKey = new JSONObject();
                if (!searchKey.containsKey("parentId")) {

                    if (parentId == null || parentId == -1) {
                        // 根级懒加载：直接返回授权根节点本身（前端点击后再展开子节点）
                        Object obj = iCommonService.getOneRecordById(tblName, scope.rootId);
                        if (obj == null) return new ArrayList<>();
                        @SuppressWarnings("unchecked")
                        BaseTreeInfo<Object> rootNode = (BaseTreeInfo<Object>) obj;
                        if (!scope.includeSubtree) {
                            // 单节点权限：强制标记为叶子，不可展开
                            rootNode.setIsLeaf(true);
                            rootNode.setHasChildren(false);
                        } else {
                            rootNode.setHasChildren(!rootNode.getIsLeaf());
                        }
                        List<Object> result = new ArrayList<>();
                        result.add(FastJsonUtil.toJson(rootNode));
                        return result;
                    } else {
                        // 展开子节点：单节点权限不允许展开
                        if (!scope.includeSubtree) {
                            return new ArrayList<>();
                        }
                        // 验证 parentId 在授权子树内
                        if (!buildAllowedIds(scope.rootId).contains(parentId)) {
                            return new ArrayList<>();
                        }
                        searchKey.put("parentId", parentId);
                    }
                }
                // lazy + 已设好 searchKey → 走下方通用 fetch 流程
            } else {
                // 非懒加载：一次性拉取全部，过滤后重建树
                @SuppressWarnings("unchecked")
                Page<BaseTreeInfo<Object>> pageResult = (Page<BaseTreeInfo<Object>>) iCommonService.getSomeRecords(tblName, searchKey, null, sortJson);
                List<BaseTreeInfo<Object>> array = new ArrayList<>(pageResult.getContent());

                // 计算允许节点集合
                Set<Integer> allowed = scope.includeSubtree
                        ? buildAllowedIds(scope.rootId)
                        : new HashSet<>(List.of(scope.rootId));
                array = array.stream()
                        .filter(n -> allowed.contains(n.getId()))
                        .collect(Collectors.toList());

                // 以授权根节点的父节点作为 parentId，使授权根节点出现在树顶层
                BaseDepartment scopeRootDept = tblDepartmentInfoDao.findById(scope.rootId).orElse(null);
                int effectiveParentId = (scopeRootDept != null && scopeRootDept.getParentId() != null)
                        ? scopeRootDept.getParentId() : -1;

                List<Object> tree = new ArrayList<>();
                if (!array.isEmpty()) {
                    if (!virtualRootFlag) {
                        tree = getSubNodes(array, effectiveParentId, false, preName);
                    } else {
                        BaseTreeInfo<Object> root = new BaseTreeInfo<>();
                        root.setName("全部");
                        root.setId(-1);
                        root.setChildren(getSubNodes(array, effectiveParentId, false, preName));
                        tree.add(root);
                    }
                }
                return tree;
            }
        }

        // --- 3. 无范围限制（或 lazy 已处理好 searchKey）的通用流程 ---
        if (lazy) {
            if (searchKey == null) searchKey = new JSONObject();
            if (!searchKey.containsKey("parentId")) {
                searchKey.put("parentId", parentId);
            }
        }
        @SuppressWarnings("unchecked")
        Page<BaseTreeInfo<Object>> pageResult = (Page<BaseTreeInfo<Object>>) iCommonService.getSomeRecords(tblName, searchKey, null, sortJson);
        List<BaseTreeInfo<Object>> array = pageResult.getContent();

        List<Object> tree = new ArrayList<>();
        BaseTreeInfo<Object> root = new BaseTreeInfo<>();
        if (array.size() != 0) {
            if (!virtualRootFlag) {
                tree = getSubNodes(array, parentId, lazy, preName);
            } else {
                root.setName("全部");
                root.setId(-1);
                root.setChildren(getSubNodes(array, parentId, lazy, preName));
                tree.add(root);
            }
        }
        return tree;
    }

    @Override
    public Object getTreeArrayList(String tblName, JSONObject searchKey) {
        if (searchKey == null) {
            searchKey = new JSONObject();
        }
        @SuppressWarnings("unchecked")
        Page<BaseTreeInfo<Object>> pageResult = (Page<BaseTreeInfo<Object>>) iCommonService.getSomeRecords(tblName, searchKey,null, orderSort);
        List<BaseTreeInfo<Object>> array = pageResult.getContent();
        ArrayList<Object> tree = new ArrayList<>();
        if (array.size() != 0) {
            getArraySubNodes(tree, array, -1);
        }
        return tree;
    }

    /**
     * 交换两个节点顺序。上移/下移按钮用到，修改TheOrder
     */
    @Override
    public Object changeTwoNodes(String tblName, int nodeId, int nodeChangeId) {
        JSONObject obj1 = FastJsonUtil.toJson(iCommonService.getOneRecordById(tblName, nodeId));
        JSONObject obj2 = FastJsonUtil.toJson(iCommonService.getOneRecordById(tblName, nodeChangeId));
        if(!obj1.getInteger("parentId").equals(obj2.getInteger("parentId"))){
            throw BaseResponse.moreInfoError.error("节点交换失败，parentId不一致");
        } else {
            int order1 = obj1.getInteger("theOrder");
            int order2 = obj2.getInteger("theOrder");
            obj1.put("theOrder", order2);
            obj2.put("theOrder", order1);
            iCommonService.saveOneRecord(tblName, obj1);
            iCommonService.saveOneRecord(tblName, obj2);
        }
        return null;
    }

    /**
     * 编辑/新增节点。如果node中的ID=0就是新增；否则就是修改。
     * 新增操作需要修改父节点的childNum和isLeaf
     */
    @Override
    public Object editOneNode(String tblName, JSONObject node) {
        // 部门树：校验操作节点（或其父节点）在授权范围内
        if (isDeptTblName(tblName)) {
            DeptScope scope = getDeptScope();
            if (scope.isRestricted()) {
                Integer nodeId = node.getInteger("id");
                Integer parentNodeId = node.getInteger("parentId");
                if (nodeId != null && nodeId != 0) {
                    requireInScope(nodeId, scope); // 更新：校验目标节点
                } else if (parentNodeId != null) {
                    requireInScope(parentNodeId, scope); // 新增：校验父节点
                }
            }
        }

        JSONObject retuInfo = new JSONObject();
        Integer nodeId = node.getInteger("id");
        int parentNodeId = node.getInteger("parentId");
        if (nodeId == null || nodeId == 0) {
            if(parentNodeId == -1) {
                node.put("theLevel", 1);
            } else {
                node.put("theLevel", FastJsonUtil.toJson(iCommonService.getOneRecordById(tblName, parentNodeId)).getInteger("theLevel") + 1);
            }
            JSONObject searchKey = new JSONObject();
            searchKey.put("parentId", parentNodeId);
            Object rawRet = iCommonService.getSomeRecords(tblName, searchKey,null, Sort.by(Sort.Direction.DESC, "theOrder"), 1, 1,true);
            if(FastJsonUtil.toJson(rawRet).getJSONArray("content").size() == 0) {
                node.put("theOrder",1);
            } else {
                JSONObject maxOrderObj = FastJsonUtil.toJson(rawRet).getJSONArray("content").getJSONObject(0);
                node.put("theOrder",maxOrderObj.getIntValue("theOrder") + 1);
            }
            node.put("updateTime",DateUtil.format(new Date(),"yyyy-MM-dd HH:mm:ss"));
            node.put("createTime",DateUtil.format(new Date(),"yyyy-MM-dd HH:mm:ss"));
            node.put("childNum", 0);
            node.put("isLeaf", 1);
            Object objAdd = iCommonService.saveOneRecord(tblName, node);
            if(parentNodeId != -1) {
                Object objParent = iCommonService.getOneRecordById(tblName, parentNodeId);
                JSONObject json = FastJsonUtil.toJson(objParent);
                Integer childNum = (Integer) json.get("childNum");
                json.put("childNum", ++childNum);
                json.put("isLeaf", 0);
                iCommonService.saveOneRecord(tblName, json);
            }
            retuInfo.put("operating", "addOneNode");
            retuInfo.put("nodeInfo", FastJsonUtil.toJson(objAdd));
        } else {
            iCommonService.saveOneRecord(tblName, node);
            retuInfo.put("operating", "updateOneNode");
            retuInfo.put("nodeInfo", node);
        }
        return retuInfo;
    }

    /**
     * 删除节点。注意还需要修改上级节点相关信息
     */
    @Override
    public Object delOneNode(String tblName, JSONObject node) {
        // 部门树：校验节点在授权范围内
        if (isDeptTblName(tblName)) {
            DeptScope scope = getDeptScope();
            if (scope.isRestricted()) {
                requireInScope(node.getInteger("id"), scope);
            }
        }

        int nodeId = node.getInteger("id");
        int parentNodeId = node.getInteger("parentId");
        iCommonService.deleteRecordByDelflag(tblName, nodeId);
        if (parentNodeId != -1) {
            Object obj = iCommonService.getOneRecordById(tblName, parentNodeId);
            JSONObject json = FastJsonUtil.toJson(obj);
            Integer childNum = (Integer) json.get("childNum");
            childNum = Math.max(0,childNum - 1);
            json.put("childNum", childNum);
            if (childNum == 0) {
                json.put("isLeaf", 1);
            }
            iCommonService.saveOneRecord(tblName, json);
        }
        return null;
    }

    /**
     * 删除一个节点-包括非叶子节点
     */
    public Object delOneNodeIncludeNoLeaf(String tblName, JSONObject node) {
        ArrayList<JSONObject> children = new ArrayList<>();
        getChildren(node,tblName,children);
        iCommonService.deleteSomeRecords(tblName, children.stream().map(p->p.getInteger("id")).collect(Collectors.toList()));

        int parentNodeId = node.getInteger("parentId");
        if (parentNodeId != -1) {
            Object obj = iCommonService.getOneRecordById(tblName, parentNodeId);
            JSONObject json = FastJsonUtil.toJson(obj);
            Integer childNum = (Integer) json.get("childNum");
            childNum = Math.max(0,childNum - 1);
            json.put("childNum", childNum);
            if (childNum == 0) {
                json.put("isLeaf", 1);
            }
            iCommonService.saveOneRecord(tblName, json);
        }
        return null;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Object delManyNodes(String tblName, JSONArray nodes) {
        List<BaseTreeInfo> rawTreeNodes = nodes.toJavaList(BaseTreeInfo.class);
        List<BaseTreeInfo<Object>> treeNodes = (List<BaseTreeInfo<Object>>)(List<?>)rawTreeNodes;

        // 部门树：批量校验所有节点在授权范围内
        if (isDeptTblName(tblName)) {
            DeptScope scope = getDeptScope();
            if (scope.isRestricted()) {
                for (BaseTreeInfo<Object> n : treeNodes) {
                    requireInScope(n.getId(), scope);
                }
            }
        }

        treeNodes.sort(((o1, o2) -> o2.getTheLevel() -o1.getTheLevel()));
        for(BaseTreeInfo<Object> nodeInfo : treeNodes){
            delOneNodeIncludeNoLeaf(tblName , FastJsonUtil.toJson(nodeInfo));
        }
        return null;
    }

    /**
     * 返回当前节点和其所有父节点。
     */
    @Override
    public Object getAllParentIndex(String tblName, Integer nodeId) {
        // 部门树：校验起始节点在授权范围内
        if (isDeptTblName(tblName)) {
            DeptScope scope = getDeptScope();
            if (scope.isRestricted()) {
                requireInScope(nodeId, scope);
            }
        }

        if (nodeId > 0) {
            List<Object> list = new ArrayList<>();
            Object obj = iCommonService.getOneRecordById(tblName, nodeId, true);
            list.add(obj);
            JSONObject json = FastJsonUtil.toJson(obj);
            Integer parentId = (Integer) json.get("parentId");
            while (parentId != -1) {
                obj = iCommonService.getOneRecordById(tblName, parentId, true);
                json = FastJsonUtil.toJson(obj);
                list.add(obj);
                parentId = (Integer) json.get("parentId");
            }
            return list;
        }
        else {
            return null;
        }
    }

    /**
     * 返回当前节点直接父节点信息
     */
    @Override
    public Object getNearestParent(String tblName, Integer nodeId) {
        Object objTemp = null;
        if(nodeId == 0) {
            return objTemp;
        }
        try{
            objTemp = iCommonService.getOneRecordById(tblName, nodeId);
            JSONObject json = FastJsonUtil.toJson(objTemp);
            Integer parentId =  (Integer)json.get("parentId");
            objTemp = iCommonService.getOneRecordById(tblName, parentId);
            JSONObject searchkey = new JSONObject();
            @SuppressWarnings("unchecked")
            Page<Object> pageResult = (Page<Object>) iCommonService.getSomeRecords(tblName, searchkey,null,orderSort);
            Object rawRet = pageResult.getContent();
            if (rawRet != null) {
                @SuppressWarnings("unchecked")
                BaseTreeInfo<Object> treeInfo = (BaseTreeInfo<Object>) objTemp;
                treeInfo.setChildren(getSubNodes(rawRet, parentId, false, ""));
            }
        }catch (Exception e){
            logger.error("返回当前节点直接父节点信息失败",e);
        }
        return objTemp;
    }

    /**
     * 获取当前节点的父亲根节点，即 parentId=-1;
     */
    @Override
    public Object getFirstParent(String tblName, Integer nodeId, Integer theLevel) {
        Object objTemp = null;
        if(nodeId == 0){
            return objTemp;
        }
        try{
            while(nodeId != null && nodeId != -1){
                objTemp = iCommonService.getOneRecordById(tblName, nodeId);
                nodeId = (int)FastJsonUtil.toJson(objTemp).get("parentId");
                if ((theLevel != null) && (theLevel == (int)FastJsonUtil.toJson(objTemp).get("theLevel"))) {
                    break;
                }
            }
        }catch (Exception e){
            logger.error(" 获取当前节点的父亲根节点失败",e);
        }
        return objTemp;
    }

    /**
     * 获取所有的兄弟节点
     */
    @Override
    public Object getAllBrotherIndex(String tblName, Integer nodeId) {
        // 部门树：校验起始节点在授权范围内
        if (isDeptTblName(tblName)) {
            DeptScope scope = getDeptScope();
            if (scope.isRestricted()) {
                requireInScope(nodeId, scope);
            }
        }

        Object listObject = null;
        try{
            JSONObject json = FastJsonUtil.toJson(iCommonService.getOneRecordById(tblName, nodeId));
            Integer parentId =  (Integer)json.get("parentId");
            JSONObject searchKey = new JSONObject();
            searchKey.put("parentId", parentId);
            @SuppressWarnings("unchecked")
            Page<Object> pageResult = (Page<Object>)iCommonService.getSomeRecords(tblName, searchKey, null, orderSort);
            listObject = pageResult.getContent();
        }catch (Exception e){
            logger.error("返回当前节点直接父节点信息失败", e);
        }
        return listObject;
    }

    @Override
    public ArrayList<JSONObject> getAllChildren(JSONObject treeInfo) {
        ArrayList<JSONObject> treeNodeIds = new ArrayList<>();
        String treeTblName = FastJsonUtil.toJson(treeInfo).getString("treeKeyWords");
        int treeNodeId = FastJsonUtil.toJson(treeInfo).getInteger("treeNodeId");
        Object nodeInfo = iCommonService.getOneRecordById(treeTblName, treeNodeId);
        if (nodeInfo == null) {
            throw BaseResponse.parameterInvalid.error("节点不存在或已删除,请刷新浏览器重试");
        }
        getChildren(FastJsonUtil.toJson(nodeInfo), treeTblName, treeNodeIds);
        return treeNodeIds;
    }

    /**
     * 树结构关联查询列表结构
     */
    @Override
    public Object getTreeJointList(JSONArray treeInfo, String listTblName, JSONObject searchKey, Map<String ,String> regMap, Sort sort, Integer page, Integer size){
        return null;
    }

    //获取当前节点及其子节点Id,放在treeNodeIds中
    public void getChildren(JSONObject nodeInfo, String treeTblName, ArrayList<JSONObject> treeNodeIds){
        treeNodeIds.add(nodeInfo);
        int curNodeId = nodeInfo.getInteger("id");
        if(!nodeInfo.getBoolean("isLeaf")){
            JSONObject searchKey = new JSONObject();
            searchKey.put("parentId", curNodeId);
            @SuppressWarnings("unchecked")
            Page<Object> pageResult = (Page<Object>)iCommonService.getSomeRecords(treeTblName, searchKey);
            List<Object> raw = pageResult.getContent();
            for(Object obj : raw){
                getChildren(FastJsonUtil.toJson(obj),treeTblName, treeNodeIds);
            }
        }
    }

    /**
     * 递归获取指定节点的所有子节点 ID（包括当前节点）
     */
    private void getAllChildIdsRecursive(Integer nodeId, String treeTblName, List<Integer> nodeIds) {
        nodeIds.add(nodeId);
        JSONObject searchKey = new JSONObject();
        searchKey.put("parentId", nodeId);
        @SuppressWarnings("unchecked")
        Page<Object> pageResult = (Page<Object>) iCommonService.getSomeRecords(treeTblName, searchKey);
        List<Object> children = pageResult.getContent();
        if (children != null && !children.isEmpty()) {
            for (Object obj : children) {
                JSONObject childJson = FastJsonUtil.toJson(obj);
                Integer childId = childJson.getInteger("id");
                if (childId != null) {
                    getAllChildIdsRecursive(childId, treeTblName, nodeIds);
                }
            }
        }
    }

    public String idToParentName(String tblName, Integer nodeId){
        Object obj = iCommonService.getOneRecordById(tblName, nodeId);
        JSONObject json = FastJsonUtil.toJson(obj);
        Integer parentId = json.getInteger("parentId");
        String name =json.getString("name");
        while (parentId != -1) {
            obj = iCommonService.getOneRecordById(tblName, parentId);
            json = FastJsonUtil.toJson(obj);
            name=json.getString("name");
        }
        return name;
    }

    @Override
    public List<Integer> getAllChildIndex(String tblName, Integer nodeId) {
        if (nodeId == null || nodeId <= 0) {
            return new ArrayList<>();
        }

        // 部门树：校验起始节点在授权范围内
        if (isDeptTblName(tblName)) {
            DeptScope scope = getDeptScope();
            if (scope.isRestricted()) {
                requireInScope(nodeId, scope);
            }
        }

        Object nodeObj = iCommonService.getOneRecordById(tblName, nodeId);
        if (nodeObj == null) {
            return new ArrayList<>();
        }
        List<Integer> nodeIds = new ArrayList<>();
        getAllChildIdsRecursive(nodeId, tblName, nodeIds);
        return nodeIds;
    }

    @Override
    public Object hasUndeletedUsersInDepartmentSubtree(Integer departmentId) {
        if (departmentId == null || departmentId <= 0) {
            throw BaseResponse.parameterInvalid.error("departmentId 不能为空");
        }
        List<Integer> departmentIds = getAllChildIndex(Constant.DEPARTMENT_INFO, departmentId);
        if (departmentIds.isEmpty()) {
            throw BaseResponse.parameterInvalid.error("节点不存在或已删除");
        }

        JSONObject searchKeys = new JSONObject();
        Map<String, String> regMap = new HashMap<>();
        if (departmentIds.size() == 1) {
            searchKeys.put("departmentId", departmentIds.get(0));
        } else {
            String idStr = departmentIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(Constant.SPLIT_OPERATOR.COMMA));
            searchKeys.put("departmentId", idStr);
            regMap.put("departmentId", Constant.IN);
        }

        @SuppressWarnings("unchecked")
        Page<Object> userPage = (Page<Object>) iCommonService.getSomeRecords(
                Constant.USER_INFO, searchKeys, regMap, Sort.unsorted(), 1, 1);
        long userCount = userPage == null ? 0L : userPage.getTotalElements();

        JSONObject result = new JSONObject();
        result.put("hasUsers", userCount > 0);
        result.put("userCount", userCount);
        result.put("departmentCount", departmentIds.size());
        return result;
    }

}

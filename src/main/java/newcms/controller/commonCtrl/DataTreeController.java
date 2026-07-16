package newcms.controller.commonCtrl;

import newcms.annotation.PathRestController;
import newcms.base.BaseResponse;
import newcms.utils.EncryptUtil;
import newcms.utils.GeneralUtil;
import newcms.utils.LogUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.annotation.Resource;
import java.util.*;


@PathRestController(value = "dataTree")
public class DataTreeController extends CommonController {
    @Resource
    protected EncryptUtil encryptUtil;


    @PostMapping(value = "/readAllTreeNodes", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object readAllTreeNodes(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("readAllTreeNodes", requestJson);
        String key = encryptUtil.getKeyWord(requestJson.getString("keyWords"));
//        String key = requestJson.getString("keyWords");
        String preName = requestJson.getString("preName");
        Boolean virtualRootFlag = true;
        Boolean lazy = true;
        Integer parentId = -1;
        if(requestJson.getBoolean("virtualRootFlag") !=  null) {
            virtualRootFlag = requestJson.getBoolean("virtualRootFlag");
        }
        if(requestJson.getBoolean("lazy") != null) {
            lazy = requestJson.getBoolean("lazy");
            parentId = requestJson.getInteger("parentId");
        }
        JSONObject searchKey = requestJson.getJSONObject("searchKey");
        Sort sortJson = GeneralUtil.getSortInfo(requestJson.getJSONObject("sort"));
        return BaseResponse.ok(iDataTreeService.getTreeList(key, virtualRootFlag, searchKey, lazy, preName, parentId, sortJson));
    }
    @PostMapping(value = "/changeTwoNodes", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object changeTwoNodes(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("changeTwoNodes", requestJson);
        String key = encryptUtil.getKeyWord(requestJson.getString("keyWords"));
//        String key = requestJson.getString("keyWords");
        int nodeId = requestJson.getInteger("nodeId");
        int nodeChangeId = requestJson.getInteger("nodeChangeId");
        return BaseResponse.ok(iDataTreeService.changeTwoNodes(key, nodeId, nodeChangeId));
    }
    @PostMapping(value = "/editOneNode", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object editOneNode(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("editOneNode", requestJson);
        String key = encryptUtil.getKeyWord(requestJson.getString("keyWords"));
//        String key = requestJson.getString("keyWords");
        JSONObject node = requestJson.getJSONObject("node");
        System.out.println(key + node.toJSONString());
        return BaseResponse.ok(iDataTreeService.editOneNode(key, node));
    }
    @PostMapping(value = "/delOneNode", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object delOneNode(@RequestBody JSONObject requestJson) {
        String key = encryptUtil.getKeyWord(requestJson.getString("keyWords"));
//        String key = requestJson.getString("keyWords");
        JSONObject node = requestJson.getJSONObject("node");
        JSONObject logger = new JSONObject();
        logger.put("keyWords", key);
        logger.put("node",node);
        LogUtil.loggerRecord("delOneNode", logger);
        return BaseResponse.ok(iDataTreeService.delOneNode(key, node));
    }
    @PostMapping(value = "/delManyNode", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object delManyNode(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("delManyNode", requestJson);
        String key = encryptUtil.getKeyWord(requestJson.getString("keyWords"));
        JSONArray treeNodes = requestJson.getJSONArray("nodes");
        return BaseResponse.ok(iDataTreeService.delManyNodes(key,treeNodes));
//        String key = encryptUtil.getKeyWord(requestJson.getString("keyWords"));
//        List<String> treeNodes = Arrays.asList(encryptUtil.getKeyWord(requestJson.getString("nodes")).split(SPLIT_OPERATOR.COMMA));
//        return BaseResponse.ok(iDataListService.deleteSomeNodes(key, treeNodes.stream().map(Integer::parseInt).collect(Collectors.toList())));
    }
    @PostMapping(value = "/getAllParentIndex", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object getAllParentIndex(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("getAllParentIndex", requestJson);
        String key = encryptUtil.getKeyWord(requestJson.getString("keyWords"));
        Integer nodeId = requestJson.getInteger("nodeId");
        return BaseResponse.ok(iDataTreeService.getAllParentIndex(key, nodeId));
    }
    @PostMapping(value = "/getNearestParent", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object getNearestParent(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("getNearestParent", requestJson);
        String key = encryptUtil.getKeyWord(requestJson.getString("keyWords"));
//        String key = requestJson.getString("keyWords");
        Integer nodeId = requestJson.getInteger("nodeId");
        return BaseResponse.ok(iDataTreeService.getNearestParent(key, nodeId));
    }
    @PostMapping(value = "/getAllBrotherIndex", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object getAllBrotherIndex(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("getAllBrotherIndex", requestJson);
        String key = encryptUtil.getKeyWord(requestJson.getString("keyWords"));
//        String key = requestJson.getString("keyWords");
        Integer nodeId = requestJson.getInteger("nodeId");
        return BaseResponse.ok(iDataTreeService.getAllBrotherIndex(key, nodeId));
    }
    @PostMapping(value = "/getFirstParent", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object getFirstParent(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("getFirstParent", requestJson);
        String key = encryptUtil.getKeyWord(requestJson.getString("keyWords"));
//        String key = requestJson.getString("keyWords");
        Integer nodeId = requestJson.getInteger("nodeId");
        return BaseResponse.ok(iDataTreeService.getFirstParent(key, nodeId, null));
    }

    @PostMapping(value = "/getAllChildIndex", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object getAllChildIndex(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("getAllChildIndex", requestJson);
        String key = encryptUtil.getKeyWord(requestJson.getString("keyWords"));
        Integer nodeId = requestJson.getInteger("nodeId");
        return BaseResponse.ok(iDataTreeService.getAllChildIndex(key, nodeId));
    }

    /**
     * 判断当前部门及其所有未删除后代部门下是否存在未删除用户。
     * body: { "departmentId": 123 }，兼容传 nodeId。
     */
    @PostMapping(value = "/hasUndeletedUsersInDepartmentSubtree", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object hasUndeletedUsersInDepartmentSubtree(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("hasUndeletedUsersInDepartmentSubtree", requestJson);
        Integer departmentId = requestJson.getInteger("departmentId");
        if (departmentId == null) {
            departmentId = requestJson.getInteger("nodeId");
        }
        return BaseResponse.ok(iDataTreeService.hasUndeletedUsersInDepartmentSubtree(departmentId));
    }

    @PostMapping(value = "/commonSearch", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object commonSearch(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("commonSearch", requestJson);
        JSONArray treeInfo = requestJson.getJSONArray("treeInfo");
        String listKeyWords = requestJson.getString("listKeyWords");
        JSONObject searchKey = new JSONObject();
        JSONObject regJson = new JSONObject();
        JSONObject pageInfo = new JSONObject();
        JSONObject sortJson = new JSONObject();
        if(requestJson.getJSONObject("searchKey") != null){
            searchKey = requestJson.getJSONObject("searchKey");
        }
        if(requestJson.getJSONObject("regKey") != null){
            regJson = requestJson.getJSONObject("regKey");
        }
        if(requestJson.getJSONObject("pageInfo") != null){
            pageInfo = requestJson.getJSONObject("pageInfo");
        }
        if(requestJson.getJSONObject("sortJson") != null){
            sortJson = requestJson.getJSONObject("sortJson");
        }

        Map<String, String> regMap =  new HashMap<>();
        JSONObject searchKeys = new JSONObject();
        if(regJson != null && regJson.size() > 0){
            //各种类型查询
            for(String key : regJson.keySet()){
                if(key.equals("createTime") || key.equals("updateTime")){
                    if(regJson.getJSONObject(key) != null){
                        searchKeys.put(key, regJson.getJSONObject(key));
                    }
                } else {
                    searchKeys.put(key, searchKey.getString(key));
                    regMap.put(key, regJson.getString(key));
                }
            }
        }
        if(searchKey != null && searchKey.size() > 0) {
            //条件查询
            searchKeys.putAll(searchKey);
        }
        Sort sort = GeneralUtil.getSortInfo(sortJson);
        Map<String,Integer> pageRes = GeneralUtil.getPageInfo(pageInfo);


        Object res = new Object();
        if(listKeyWords != null) {
            if(treeInfo != null && treeInfo.size() > 0){
                //树表联合查询
                res = BaseResponse.ok(iDataTreeService.getTreeJointList(treeInfo, listKeyWords, searchKeys, regMap, sort, pageRes.get("page"), pageRes.get("size")));
            } else {
                //单列表查询
                res = BaseResponse.ok(iDataListService.getSomeRecords(listKeyWords, searchKeys, regMap, sort, pageRes.get("page"),pageRes.get("size")));
            }
        }
        return res;
    }
}

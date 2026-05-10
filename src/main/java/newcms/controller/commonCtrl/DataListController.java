package newcms.controller.commonCtrl;

import newcms.annotation.PathRestController;
import newcms.base.BaseResponse;
import newcms.utils.EncryptUtil;
import newcms.utils.FastJsonUtil;
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
import java.util.stream.Collectors;

//import static newcms.utils.EncryptUtil.aesDecrypt;
//import static newcms.utils.EncryptUtil.getKeyWord;

/**
 * @author cherish
 */

@PathRestController(value = "dataList")
public class DataListController extends CommonController {
    @Resource
    protected EncryptUtil encryptUtil;


    @PostMapping(value = "/getSomeRecords", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object getSomeRecords(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("getSomeRecords", requestJson);
        //条件查询searchKey //模糊查询reg
        String tblName = encryptUtil.getKeyWord(requestJson.getString("keyWords").replace("@","+"));
        JSONObject treeInfo = requestJson.getJSONObject("treeInfo");
        JSONObject searchKeys = new JSONObject();
        JSONObject regJson = new JSONObject();
        try{
            searchKeys = FastJsonUtil.toJson(encryptUtil.getKeyWord(requestJson.getString("searchKey")));
        } catch (Exception e) {
            logger.warn("搜索条件解密失败", e);
        }

        // ★ 在这里加一行：
        logger.info("getSomeRecords 解密后: tblName={}, searchKeys={}", tblName, searchKeys);
        try {
            regJson = FastJsonUtil.toJson(encryptUtil.getKeyWord(requestJson.getString("reg")));
        } catch (Exception e) {
            logger.warn("查询条件解密失败", e);
        }
        JSONObject andorJson = requestJson.getJSONObject("andor");
        Map<String,String> regMap =  new HashMap<>();
        if(regJson != null) { //各种条件查询
            for(String key : regJson.keySet()){
                regMap.put(key, regJson.getString(key));
            }
        }
        Map<String,Boolean> andor =  new HashMap<>();
        if(andorJson != null) { //各种条件查询
            for(String key : andorJson.keySet()){
                andor.put(key, andorJson.getBoolean(key));
            }
        }
        Sort sortJson = GeneralUtil.getSortInfo(requestJson.getJSONObject("sort"));
        Map<String, Integer> pageInfo = GeneralUtil.getPageInfo(requestJson.getJSONObject("pageInfo"));
        if (treeInfo != null && treeInfo.size() > 0) { //有树结构查询搜索要求
            ArrayList<JSONObject> children = iDataTreeService.getAllChildren(treeInfo);
            String treeRelColName = treeInfo.getString("treeRelColName");
            String ids = children.stream().map(p->p.getString("id")).collect(Collectors.joining(SPLIT_OPERATOR.COMMA));
            if (searchKeys == null) {
                searchKeys = new JSONObject();
            }
            searchKeys.put(treeRelColName, ids);
            regMap.put(treeRelColName,IN);
        }
        if (searchKeys == null) {
            searchKeys = new JSONObject();
        }
        normalizeStudentInternshipTerminationCandidateSearch(tblName, searchKeys, regMap);
        return BaseResponse.ok(iCommonService.getSomeRecords(tblName, searchKeys, regMap, sortJson, pageInfo.get("page"), pageInfo.get("size"),false, andor));
    }

    @PostMapping(value = "/delOneOrManyNodes", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object delOneOrManyNode(@RequestBody JSONObject requestJson) {
        String key = encryptUtil.getKeyWord(requestJson.getString("keyWords"));
        List<String> ids = Arrays.asList(encryptUtil.getKeyWord(requestJson.getString("ids")).split(SPLIT_OPERATOR.COMMA));

        JSONObject logger = new JSONObject();
        logger.put("keyWords", key);
        logger.put("node",ids);
        LogUtil.loggerRecord("delOneOrManyNode", logger);


        return BaseResponse.ok(iDataListService.deleteSomeNodes(key, ids.stream().map(Integer::parseInt).collect(Collectors.toList())));
    }

    @PostMapping(value = "/editOneNode", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object editOneNode(@RequestBody JSONObject requestJson) {
      LogUtil.loggerRecord("editOneNode", requestJson);
      String key = encryptUtil.getKeyWord(requestJson.getString("keyWords"));
      JSONObject node = requestJson.getJSONObject("node");
      return BaseResponse.ok(iDataListService.editOneNode(key, node));
    }

    /**
     * 批量编辑/新增。keyWords 经 {@link EncryptUtil#getKeyWord} 解密得到表名。<br>
     * nodes：可为明文 JSON 数组字符串（trim 后以 {@code [} 开头则不再走解密）；或与 searchKey 相同传**加密字符串**，解密后为 JSON 数组；也支持明文 {@link JSONArray}。<br>
     * 路径：dataList/editManyNodes
     */
    @PostMapping(value = "/editManyNodes", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object editManyNodes(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("editManyNodes", requestJson);
        String key = encryptUtil.getKeyWord(requestJson.getString("keyWords"));
        Object nodesRaw = requestJson.get("nodes");
        JSONArray nodesArr;
        if (nodesRaw instanceof String) {
            String nodesStr = ((String) nodesRaw).trim();
            // 前端常把数组序列化成字符串传入；若以 [ 开头则为明文，勿对整段调用 getKeyWord（会报 keyWord 格式错误）
            if (nodesStr.startsWith("[")) {
                nodesArr = JSONArray.parseArray(nodesStr);
            } else {
                nodesArr = JSONArray.parseArray(encryptUtil.getKeyWord(nodesStr));
            }
        } else {
            nodesArr = requestJson.getJSONArray("nodes");
        }
        if (nodesArr == null || nodesArr.isEmpty()) {
            throw BaseResponse.parameterInvalid.error("nodes 不能为空");
        }
        List<JSONObject> nodes = new ArrayList<>(nodesArr.size());
        for (int i = 0; i < nodesArr.size(); i++) {
            JSONObject item = nodesArr.getJSONObject(i);
            if (item == null) {
                throw BaseResponse.parameterInvalid.error("nodes 中存在空项或非对象");
            }
            nodes.add(item);
        }
        return BaseResponse.ok(iDataListService.editManyNodes(key, nodes));
    }

    @PostMapping(value = "/changeTwoNodes", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object changeTwoNodes(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("changeTwoNodes", requestJson);
        String key = encryptUtil.getKeyWord(requestJson.getString("keyWords"));
        //String key = requestJson.getString("keyWords");
        int nodeId = requestJson.getInteger("nodeId");
        int nodeChangeId = requestJson.getInteger("nodeChangeId");
        return BaseResponse.ok(iDataListService.changeTwoNodes(key, nodeId, nodeChangeId));
    }
    @PostMapping(value = "/changeNodeOrder", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object changeNodeOrder(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("changeNodeOrder", requestJson);
        String keyWords = encryptUtil.getKeyWord(requestJson.getString("keyWords"));
        //String keyWords = requestJson.getString("keyWords");
        int nodeId = requestJson.getInteger("nodeId");
        boolean up = requestJson.getBoolean("up");
        JSONObject searchKeys = requestJson.getJSONObject("moveSearchKeys");
        JSONObject regKeys = requestJson.getJSONObject("moveRegKeys");
        if (searchKeys == null) {
            searchKeys = new JSONObject();
        }
        Map<String,String> regMap =  new HashMap<>();
        if(regKeys != null) { //各种条件查询
            for(String key : regKeys.keySet()){
                regMap.put(key, regKeys.getString(key));
            }
        }
        Object obj = iDataListService.changeNodeOrder(keyWords, nodeId, up, searchKeys, regMap);
        if (obj != null) {
            return BaseResponse.ok(obj);
        }
        else {
            throw BaseResponse.moreInfoError.error("已经是第一个/最后一个节点，无法移动！");
        }
    }
}


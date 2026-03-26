package newcms.service;


import com.alibaba.fastjson.JSONObject;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public interface IDataListService {

    /**
    *@Des List结构相应接口
    *@Author cherish
    *@Date 2020/10/27 14:14
    */
    Object getSomeRecords(String tblName, JSONObject searchKeys, Map<String,String> reg, Sort sort, Integer page, Integer size);
    Object changeTwoNodes(String tblName, int nodeId, int nodeChangeId);
    Object editOneNode(String tblName, JSONObject node);

    /**
     * 批量新增/编辑，与 {@link #editOneNode} 规则相同（含 RelTeacherStudent / RelProcessInternship 等后置逻辑），按 nodes 顺序逐条处理。
     */
    List<Object> editManyNodes(String tblName, List<JSONObject> nodes);

    Object deleteSomeNodes(String tblName, List<Integer> ids);
    Object changeNodeOrder(String tblName, int nodeId, boolean up, JSONObject searchKeys, Map<String,String> regMap);
}

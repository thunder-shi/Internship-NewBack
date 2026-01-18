package newcms.service.impl;

import newcms.base.Base;
import newcms.base.BaseResponse;
import newcms.service.ICommonService;
import newcms.service.IDataListService;
import newcms.service.IInternshipService;
import newcms.utils.FastJsonUtil;
import newcms.utils.LogUtil;
import com.alibaba.fastjson.JSONObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import jakarta.annotation.Resource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
@Transactional(rollbackFor = Exception.class)
public class DataListServiceImpl extends Base implements IDataListService {
    @Resource
    protected ICommonService iCommonService;

    @Resource
    protected IInternshipService iInternshipService;

    /**
     * 条件查询/模糊查询
     * searchKey中的字段和reg中的字段对应
     * @param tblName
     * @param searchKeys
     * @param regMap
     * @return
     */
    @Override
    public Object getSomeRecords(String tblName, JSONObject searchKeys, Map<String, String> regMap, Sort sort, Integer page, Integer size) {
        Object rawRet;
        rawRet = iCommonService.getSomeRecords(tblName, searchKeys, regMap, sort, page, size);
        return rawRet;
    }

    @Override
    public Object changeTwoNodes(String tblName, int nodeId, int nodeChangeId) {
        JSONObject obj1 = FastJsonUtil.toJson(iCommonService.getOneRecordById(tblName, nodeId));
        JSONObject obj2 = FastJsonUtil.toJson(iCommonService.getOneRecordById(tblName, nodeChangeId));
        //step1判断
        //todo:未判空
        int order1 = obj1.getInteger("theOrder");
        int order2 = obj2.getInteger("theOrder");
        obj1.put("theOrder",order2);
        obj2.put("theOrder",order1);
        List<Object> list = new ArrayList<>();
        list.add(obj1);
        list.add(obj2);
        return iCommonService.saveSomeRecords(tblName, list);
    }

    @Override
    public Object changeNodeOrder(String tblName, int nodeId, boolean up, JSONObject searchKeys, Map<String,String> regMap) {
        JSONObject obj1 = FastJsonUtil.toJson(iCommonService.getOneRecordById(tblName, nodeId));
        Sort sort = Sort.by(Sort.Direction.DESC, "theOrder");
        searchKeys.put("theOrder", obj1.getInteger("theOrder"));
        if (up) {
            regMap.put("theOrder", LT);
        } else {
            regMap.put("theOrder", GT);
            sort = Sort.by(Sort.Direction.ASC, "theOrder");
        }
        if (searchKeys != null) {
            for (Map.Entry<String, Object> entry : searchKeys.entrySet()) {
                if (ObjectUtils.isEmpty(entry.getValue())) {
                    regMap.remove(entry.getKey());
                    searchKeys.remove(entry.getKey());
                }
            }
        }
        Object objArr = iCommonService.getSomeRecords(tblName,searchKeys, regMap, sort);
        if (FastJsonUtil.toJson(objArr).getJSONArray("content").size() != 0) {
            JSONObject obj2 = FastJsonUtil.toJson(objArr).getJSONArray("content").getJSONObject(0);
            int tempOrder = obj2.getIntValue("theOrder");
            obj2.put("theOrder", obj1.getIntValue("theOrder"));
            obj1.put("theOrder", tempOrder);
            List<Object> list = new ArrayList<>();
            list.add(obj1);
            list.add(obj2);
            Object obtemp = iCommonService.saveSomeRecords(tblName, list);
            return obtemp;
        } else {
            return null;
        }
    }
    @Override
    public Object editOneNode(String tblName, JSONObject node) {
        // 判断是否为新增操作
        boolean isNew = node.getInteger("id") == null || node.getInteger("id") == 0;

        // 编辑MainInternship时，记录旧的internshipTypeId用于比较
        Integer oldInternshipTypeId = null;
        if (!isNew && "MainInternship".equals(tblName)) {
            JSONObject oldRecord = FastJsonUtil.toJson(iCommonService.getOneRecordById(tblName, node.getInteger("id")));
            if (oldRecord != null) {
                oldInternshipTypeId = oldRecord.getInteger("internshipTypeId");
            }
        }

        //新增
        if (isNew) {
            try {
                Class<?> clazzInfo = Class.forName(Base.entityPackage + tblName);
                if (clazzInfo.getSuperclass().getName().contains("OrderInfo") && (node.getString("theOrder")==null) ) {
                    Sort sort = Sort.by(Sort.Direction.DESC, "theOrder");
                    //获取最大值theOrder,+1后加入
                    @SuppressWarnings("unchecked")
                    List<Object> rawRet = ((Page<Object>)iCommonService.getSomeRecords(tblName, null, null, sort)).getContent();
                    if (rawRet.size() == 0) {
                        node.put("theOrder", 1);
                    } else {
                        JSONObject maxOrder = FastJsonUtil.toJson(rawRet.get(0));
                        node.put("theOrder", maxOrder.getIntValue("theOrder") + 1);
                    }
                }
            }  catch (ClassNotFoundException e) {
                LogUtil.error(logger, e);
                throw BaseResponse.moreInfoError.error("tblName 异常");
            }
        } else { //修改

        }
        if(tblName.equals("MainChannelContent")){
            if(ObjectUtils.isEmpty(node.getDate("channelCTime"))
                    &&!ObjectUtils.isEmpty(node.getBoolean("publishStatus"))
                    &&node.getBoolean("publishStatus")){
                node.put("channelCTime", Date.from(Instant.now()));
            }else if(!ObjectUtils.isEmpty(node.getBoolean("publishStatus"))&&!node.getBoolean("publishStatus")){
                node.put("channelCTime",null);
            }
        }

        Object result = iCommonService.saveOneRecord(tblName, node);

        // 处理MainInternship的流程配置
        if ("MainInternship".equals(tblName)) {
            JSONObject savedNode = FastJsonUtil.toJson(result);
            Integer internshipId = savedNode.getInteger("id");
            Integer newInternshipTypeId = savedNode.getInteger("internshipTypeId");

            if (isNew) {
                // 新建时，复制模板流程配置
                iInternshipService.copyProcessFromTemplate(internshipId, newInternshipTypeId);

                // 如果前端传递了 isAudit，创建审核记录
                Integer isAudit = node.getInteger("isAudit");
                if (isAudit != null) {
                    Integer createUserId = savedNode.getInteger("creatorId");
                    iInternshipService.createVerifyProcessIfNeeded(internshipId, newInternshipTypeId,
                            createUserId, isAudit);
                }
            } else {
                // 编辑时，检测模板类型是否变化
                boolean typeChanged = !java.util.Objects.equals(oldInternshipTypeId, newInternshipTypeId);
                if (typeChanged) {
                    // 模板类型变化，更新流程配置
                    iInternshipService.updateProcessFromTemplate(internshipId, newInternshipTypeId);
                }
            }
        }

        return result;
    }

    /**
     * 删除某些节点
     * @param tblName
     * @param ids
     * @return
     */
    @Override
    public Object deleteSomeNodes(String tblName, List<Integer> ids) {
        iCommonService.deleteSomeRecords(tblName, ids);
        return null;
    }
}

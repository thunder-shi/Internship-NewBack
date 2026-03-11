package newcms.service.impl;

import newcms.base.Base;
import newcms.base.BaseResponse;
import newcms.service.ICommonService;
import newcms.service.IDataListService;
import newcms.service.IInternshipService;
import newcms.service.IVerifyProcessService;
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

    @Resource
    protected IVerifyProcessService iVerifyProcessService;

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
        Object saved = iCommonService.saveOneRecord(tblName, node);

        // 老师申报题目：新增 RelTeacherStudent 后创建首条 MainVerifyProcess（保存未提交/无需审核直接通过）
        if ("RelTeacherStudent".equals(tblName) && isNew && saved != null) {
            try {
                JSONObject savedJson = FastJsonUtil.toJson(saved);
                Integer relationId = savedJson.getInteger("id");
                Integer internshipId = savedJson.getInteger("internshipId");
                Integer createUserId = savedJson.getInteger("teacherId");
                if (relationId != null && internshipId != null && createUserId != null) {
                    iInternshipService.createFirstVerifyProcessForRelTeacherStudent(relationId, internshipId, createUserId);
                }
            } catch (Exception e) {
                logger.warn("老师申报题目创建审核记录失败，不影响保存: {}", e.getMessage());
            }
        }

        // 修改 RelProcessInternship 的审核角色后，刷新对应的待审核记录
        if ("RelProcessInternship".equals(tblName) && !isNew) {
            try {
                Integer processId = node.getInteger("id");
                iVerifyProcessService.refreshPendingVerifyUsersByProcess(processId);
            } catch (Exception e) {
                logger.warn("刷新流程审核记录失败，不影响保存: {}", e.getMessage());
            }
        }

        return saved;
    }

    /**
     * 删除某些节点
     * @param tblName
     * @param ids
     * @return
     */
    @Override
    public Object deleteSomeNodes(String tblName, List<Integer> ids) {
        // 老师申报题目：删除题目时同步清理其 MainVerifyProcess 审核记录
        if ("RelTeacherStudent".equals(tblName) && ids != null) {
            for (Integer relationId : ids) {
                if (relationId != null) {
                    try {
                        iInternshipService.deleteVerifyProcessByRelationIdAndTableName(relationId, "RelTeacherStudent");
                    } catch (Exception e) {
                        logger.warn("删除题目时清理审核记录失败, relationId={}: {}", relationId, e.getMessage());
                    }
                }
            }
        }
        iCommonService.deleteSomeRecords(tblName, ids);
        return null;
    }
}

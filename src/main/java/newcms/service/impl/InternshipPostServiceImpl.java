package newcms.service.impl;

import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import newcms.base.Base;
import newcms.base.BaseResponse;
import newcms.base.Constant;
import newcms.service.ICommonService;
import newcms.service.IInternshipPostService;
import newcms.service.IVerifyProcessService;
import newcms.utils.FastJsonUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(rollbackFor = Exception.class)
public class InternshipPostServiceImpl extends Base implements IInternshipPostService {

    @Resource
    private ICommonService iCommonService;

    @Resource
    private IVerifyProcessService iVerifyProcessService;

    private static final String TABLE_REL_STU_INTERNSHIP = "RelStuInternshipPost";
    private static final String TABLE_MAIN_VERIFY_PROCESS = "MainVerifyProcess";
    private static final String TABLE_MAIN_INTERNSHIP_POST = "MainInternshipPost";
    private static final String VIEW_VERIFY_PROCESS_REL_STU_INTERNSHIP = "ViewVerifyProcessRelStuInternshipPost";

    @Override
    public Object stuSelPost(Integer studentId, Integer oldPostId, Integer newPostId) {
        Integer finalRelStuInternshipPostId = null;
        
        if (oldPostId != 0 && newPostId == 0) {
            // 情况一：取消岗位选择
            JSONObject recordA = findRelStuInternshipPostRecord(studentId, oldPostId);
            if (recordA == null) {
                throw BaseResponse.moreInfoError.error("未找到对应的学生实习岗位选择记录");
            }
            Integer recordAId = recordA.getInteger("id");
            cancelPostSelection(recordAId, oldPostId);
            // 取消选择后，返回 null
            return null;
        } else if (oldPostId != 0 && newPostId != 0) {
            // 情况二：更换岗位
            JSONObject recordA = findRelStuInternshipPostRecord(studentId, oldPostId);
            if (recordA == null) {
                throw BaseResponse.moreInfoError.error("未找到对应的学生实习岗位选择记录");
            }
            Integer recordAId = recordA.getInteger("id");
            changePost(recordA, oldPostId, newPostId);
            finalRelStuInternshipPostId = recordAId;
        } else if (oldPostId == 0 && newPostId != 0) {
            // 情况三：第一次选择岗位
            JSONObject recordA = selectPostFirstTime(studentId, newPostId);
            finalRelStuInternshipPostId = recordA.getInteger("id");
        } else {
            throw BaseResponse.parameterInvalid.error("参数错误：oldPostId 和 newPostId 不能同时为 0");
        }

        // 查询并返回 ViewVerifyProcessRelStuInternshipPost 完整实体
        return findViewVerifyProcessRelStuInternshipPost(finalRelStuInternshipPostId);
    }

    /**
     * 查询 RelStuInternshipPost 表，根据 studentId 和 internshipPostId 找到对应记录
     */
    private JSONObject findRelStuInternshipPostRecord(Integer studentId, Integer internshipPostId) {
        JSONObject searchKeys = new JSONObject();
        searchKeys.put("studentId", studentId);
        searchKeys.put("internshipPostId", internshipPostId);

        @SuppressWarnings("unchecked")
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                TABLE_REL_STU_INTERNSHIP, searchKeys, null, Sort.unsorted(), 1, 1);

        List<Object> content = page.getContent();
        if (content == null || content.isEmpty()) {
            return null;
        }

        return FastJsonUtil.toJson(content.get(0));
    }

    /**
     * 取消岗位选择
     */
    private void cancelPostSelection(Integer recordAId, Integer oldPostId) {
        // 1. 删除 MainVerifyProcess 记录
        deleteMainVerifyProcessRecord(recordAId);

        // 2. 删除 RelStuInternshipPost 记录
        iCommonService.deleteRecordByDelflag(TABLE_REL_STU_INTERNSHIP, recordAId);

        // 3. 更新 MainInternshipPost 的 nowPersonNum - 1
        decreasePostPersonNum(oldPostId);
    }

    /**
     * 更换岗位
     */
    private void changePost(JSONObject recordA, Integer oldPostId, Integer newPostId) {
        // 检查新岗位是否已满
        checkPostCapacity(newPostId);

        Integer recordAId = recordA.getInteger("id");

        // 1. 修改 RelStuInternshipPost 的 internshipPostId 为 newPostId
        updateRelStuInternshipPostId(recordAId, newPostId);

        // 2. 修改 MainVerifyProcess 的 isAudit 为 0（已提交）
        updateMainVerifyProcessIsAudit(recordAId, Constant.AUDIT_STATUS.SUBMIT);

        // 3. 更新 MainInternshipPost 的 nowPersonNum
        decreasePostPersonNum(oldPostId);
        increasePostPersonNum(newPostId);
    }

    /**
     * 删除 MainVerifyProcess 记录（根据 relationId 和 tableName）
     */
    private void deleteMainVerifyProcessRecord(Integer relationId) {
        JSONObject searchKeys = new JSONObject();
        searchKeys.put("relationId", relationId);
        searchKeys.put("tableName", TABLE_REL_STU_INTERNSHIP);

        @SuppressWarnings("unchecked")
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                TABLE_MAIN_VERIFY_PROCESS, searchKeys, null, Sort.unsorted(), 1, 100);

        List<Object> content = page.getContent();
        if (content != null && !content.isEmpty()) {
            for (Object obj : content) {
                JSONObject verifyProcessJson = FastJsonUtil.toJson(obj);
                Integer id = verifyProcessJson.getInteger("id");
                if (id != null) {
                    iCommonService.deleteRecordByDelflag(TABLE_MAIN_VERIFY_PROCESS, id);
                }
            }
        }
    }

    /**
     * 更新 RelStuInternshipPost 的 internshipPostId
     */
    private void updateRelStuInternshipPostId(Integer recordAId, Integer newPostId) {
        JSONObject updateJson = new JSONObject();
        updateJson.put("id", recordAId);
        updateJson.put("internshipPostId", newPostId);
        iCommonService.saveOneRecord(TABLE_REL_STU_INTERNSHIP, updateJson);
    }

    /**
     * 更新 MainVerifyProcess 的 isAudit
     */
    private void updateMainVerifyProcessIsAudit(Integer relationId, Integer isAudit) {
        JSONObject searchKeys = new JSONObject();
        searchKeys.put("relationId", relationId);
        searchKeys.put("tableName", TABLE_REL_STU_INTERNSHIP);

        @SuppressWarnings("unchecked")
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                TABLE_MAIN_VERIFY_PROCESS, searchKeys, null, Sort.unsorted(), 1, 100);

        List<Object> content = page.getContent();
        if (content != null && !content.isEmpty()) {
            for (Object obj : content) {
                JSONObject verifyProcessJson = FastJsonUtil.toJson(obj);
                Integer id = verifyProcessJson.getInteger("id");
                if (id != null) {
                    JSONObject updateJson = new JSONObject();
                    updateJson.put("id", id);
                    updateJson.put("isAudit", isAudit);
                    iCommonService.saveOneRecord(TABLE_MAIN_VERIFY_PROCESS, updateJson);
                }
            }
        }
    }

    /**
     * 检查岗位是否已达最大可选人数
     */
    private void checkPostCapacity(Integer postId) {
        Object postObj = iCommonService.getOneRecordById(TABLE_MAIN_INTERNSHIP_POST, postId);
        if (postObj == null) {
            throw BaseResponse.moreInfoError.error("未找到岗位记录，ID: " + postId);
        }
        JSONObject postJson = FastJsonUtil.toJson(postObj);
        Integer nowPersonNum = postJson.getInteger("nowPersonNum");
        Integer allPersonNum = postJson.getInteger("allPersonNum");
        if (nowPersonNum == null) nowPersonNum = 0;
        if (allPersonNum != null && allPersonNum > 0 && nowPersonNum >= allPersonNum) {
            throw BaseResponse.parameterInvalid.error("该岗位已达到最大可选人数");
        }
    }

    /**
     * 减少岗位当前人数
     */
    private void decreasePostPersonNum(Integer postId) {
        updatePostPersonNum(postId, -1);
    }

    /**
     * 增加岗位当前人数
     */
    private void increasePostPersonNum(Integer postId) {
        updatePostPersonNum(postId, 1);
    }

    /**
     * 更新岗位当前人数（通用方法）
     */
    private void updatePostPersonNum(Integer postId, int delta) {
        Object postObj = iCommonService.getOneRecordById(TABLE_MAIN_INTERNSHIP_POST, postId);
        if (postObj == null) {
            throw BaseResponse.moreInfoError.error("未找到岗位记录，ID: " + postId);
        }

        JSONObject postJson = FastJsonUtil.toJson(postObj);
        Integer currentNum = postJson.getInteger("nowPersonNum");
        if (currentNum == null) {
            currentNum = 0;
        }

        int newNum = currentNum + delta;
        if (newNum < 0) {
            throw BaseResponse.moreInfoError.error("岗位当前人数不能为负数");
        }

        JSONObject updateJson = new JSONObject();
        updateJson.put("id", postId);
        updateJson.put("nowPersonNum", newNum);
        iCommonService.saveOneRecord(TABLE_MAIN_INTERNSHIP_POST, updateJson);
    }

    /**
     * 第一次选择岗位
     * @return 创建的 RelStuInternshipPost 记录
     */
    private JSONObject selectPostFirstTime(Integer studentId, Integer newPostId) {
        // 1. 检查岗位是否已满
        checkPostCapacity(newPostId);

        // 2. 新增 RelStuInternshipPost 记录
        JSONObject newRelJson = new JSONObject();
        newRelJson.put("studentId", studentId);
        newRelJson.put("internshipPostId", newPostId);
        Object savedRelObj = iCommonService.saveOneRecord(TABLE_REL_STU_INTERNSHIP, newRelJson);
        JSONObject recordA = FastJsonUtil.toJson(savedRelObj);
        Integer recordAId = recordA.getInteger("id");

        // 3. 更新 MainInternshipPost 的 nowPersonNum + 1
        increasePostPersonNum(newPostId);

        // 4. 获取 internshipId
        Object postObj = iCommonService.getOneRecordById(TABLE_MAIN_INTERNSHIP_POST, newPostId);
        JSONObject postJson = FastJsonUtil.toJson(postObj);
        Integer internshipId = postJson.getInteger("internshipId");
        if (internshipId == null) {
            throw BaseResponse.moreInfoError.error("岗位记录缺少 internshipId");
        }

        // 5. 创建 MainVerifyProcess 记录
        createMainVerifyProcessForFirstSelection(recordAId, internshipId, studentId);
        
        return recordA;
    }

    /**
     * 为第一次选择岗位创建 MainVerifyProcess 记录
     */
    private void createMainVerifyProcessForFirstSelection(Integer relationId, Integer internshipId, Integer createUserId) {
        // 获取流程配置
        Object processObj = iVerifyProcessService.GetInternshipProcess(
                internshipId, Constant.PROCESS_TYPE.EXTERNAL_STUDENT_SELECT_POST);
        JSONObject processJson = FastJsonUtil.toJson(processObj);
        Integer processId = processJson.getInteger("id");
        if (processId == null) {
            throw BaseResponse.moreInfoError.error("未找到流程配置信息");
        }

        // 判断是否需要审核（verifyTypeId >= 2 表示需要审核）
        Integer verifyTypeId = processJson.getInteger("verifyTypeId");
        boolean needsVerify = verifyTypeId != null && verifyTypeId >= 2;

        if (needsVerify) {
            // 需要审核：从第一级（level=2）开始
            Integer verifyRoleId = iVerifyProcessService.getVerifyRoleIdByLevel(processJson, 2);
            String verifyUserId = iVerifyProcessService.GetVerifyUserId(verifyRoleId, createUserId);

            // 更新 RelStuInternshipPost 的 currentVerifyTypeId 为 2（第一级审核开始）
            JSONObject updateEntityJson = new JSONObject();
            updateEntityJson.put("id", relationId);
            updateEntityJson.put("currentVerifyTypeId", 2);
            iCommonService.saveOneRecord(TABLE_REL_STU_INTERNSHIP, updateEntityJson);

            // 创建待审核记录
            JSONObject verifyJson = new JSONObject();
            verifyJson.put("relationId", relationId);
            verifyJson.put("processId", processId);
            verifyJson.put("createUserId", createUserId);
            verifyJson.put("verifyUserId", verifyUserId);
            verifyJson.put("isAudit", Constant.AUDIT_STATUS.SUBMIT); // 0-已提交待审核
            verifyJson.put("reason", "");
            verifyJson.put("tableName", TABLE_REL_STU_INTERNSHIP);
            iCommonService.saveOneRecord(TABLE_MAIN_VERIFY_PROCESS, verifyJson);
        } else {
            // 无需审核：直接通过（currentVerifyTypeId=2 > verifyTypeId=1，标记审核完成）
            JSONObject updateEntityJson = new JSONObject();
            updateEntityJson.put("id", relationId);
            updateEntityJson.put("currentVerifyTypeId", 2);
            iCommonService.saveOneRecord(TABLE_REL_STU_INTERNSHIP, updateEntityJson);

            // 创建自动通过记录
            JSONObject verifyJson = new JSONObject();
            verifyJson.put("relationId", relationId);
            verifyJson.put("processId", processId);
            verifyJson.put("createUserId", createUserId);
            verifyJson.put("verifyUserId", "系统自动通过");
            verifyJson.put("isAudit", 1); // 直接通过
            verifyJson.put("reason", "系统自动通过");
            verifyJson.put("tableName", TABLE_REL_STU_INTERNSHIP);
            iCommonService.saveOneRecord(TABLE_MAIN_VERIFY_PROCESS, verifyJson);
        }
    }

    /**
     * 查询 ViewVerifyProcessRelStuInternshipPost 视图，根据 relationId 找到对应记录
     */
    private Object findViewVerifyProcessRelStuInternshipPost(Integer relationId) {
        if (relationId == null) {
            return null;
        }

        JSONObject searchKeys = new JSONObject();
        searchKeys.put("relationId", relationId);
        searchKeys.put("tableName", TABLE_REL_STU_INTERNSHIP);

        @SuppressWarnings("unchecked")
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                VIEW_VERIFY_PROCESS_REL_STU_INTERNSHIP, searchKeys, null, Sort.unsorted(), 1, 1);

        List<Object> content = page.getContent();
        if (content == null || content.isEmpty()) {
            return null;
        }

        return content.get(0);
    }
}

package newcms.service.impl;

import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import newcms.base.Base;
import newcms.base.BaseResponse;
import newcms.base.Constant;
import newcms.entity.db.MainInternshipPost;
import newcms.repository.db.MainInternshipPostDao;
import newcms.repository.db.RelStuInternshipPostDao;
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

    @Resource
    private MainInternshipPostDao mainInternshipPostDao;

    @Resource
    private RelStuInternshipPostDao relStuInternshipPostDao;

    private static final String TABLE_REL_STU_INTERNSHIP = "RelStuInternshipPost";
    private static final String TABLE_MAIN_VERIFY_PROCESS = "MainVerifyProcess";

    @Override
    public Object stuSelPost(Integer studentId, Integer oldPostId, Integer newPostId) {
        if (oldPostId != 0 && newPostId == 0) {
            // 情况一：取消岗位选择
            JSONObject recordA = findRelStuInternshipPostRecord(studentId, oldPostId);
            if (recordA == null) {
                throw BaseResponse.moreInfoError.error("未找到对应的学生实习岗位选择记录");
            }
            cancelPostSelection(recordA.getInteger("id"), oldPostId);
            return null;

        } else if (oldPostId == 0 && newPostId != 0) {
            // 情况三：第一次选择岗位，直接返回 {isAudit, internshipPostId, verifyTypeId}
            return selectPostFirstTime(studentId, newPostId);

        } else if (oldPostId != 0 && newPostId != 0) {
            // 情况二：更换岗位，直接返回 {isAudit, internshipPostId, verifyTypeId}
            JSONObject recordA = findRelStuInternshipPostRecord(studentId, oldPostId);
            if (recordA == null) {
                throw BaseResponse.moreInfoError.error("未找到对应的学生实习岗位选择记录");
            }
            return changePost(recordA, studentId, oldPostId, newPostId);

        } else {
            throw BaseResponse.parameterInvalid.error("参数错误：oldPostId 和 newPostId 不能同时为 0");
        }
    }

    // ─────────────────────────── 核心业务方法 ───────────────────────────

    /**
     * 第一次选择岗位。
     * <p>先校验同实习项目下是否已有通过审核的报名，通过后原子占位，
     * 创建 RelStuInternshipPost 和 MainVerifyProcess。</p>
     *
     * @return {@code {isAudit, internshipPostId, verifyTypeId}}
     */
    private JSONObject selectPostFirstTime(Integer studentId, Integer newPostId) {
        // 1. 取岗位信息（含 internshipId）
        MainInternshipPost post = mainInternshipPostDao.getByIdAndIsDeletedFalse(newPostId);
        if (post == null) {
            throw BaseResponse.moreInfoError.error("未找到岗位记录，ID: " + newPostId);
        }
        Integer internshipId = post.getInternshipId();
        if (internshipId == null) {
            throw BaseResponse.moreInfoError.error("岗位记录缺少 internshipId");
        }

        // 2. 服务端兜底：已有通过审核的报名则拦截
        checkNoApprovedPostInSameInternship(studentId, internshipId);

        // 3. 原子性占位（容量检查 + nowPersonNum+1 合并为一条 SQL）
        int affected = mainInternshipPostDao.incrementNowPersonNumIfNotFull(newPostId);
        if (affected == 0) {
            throw BaseResponse.parameterInvalid.error("该岗位已达到最大可选人数");
        }

        // 4. 新增 RelStuInternshipPost 记录
        JSONObject newRelJson = new JSONObject();
        newRelJson.put("studentId", studentId);
        newRelJson.put("internshipPostId", newPostId);
        Object savedRelObj = iCommonService.saveOneRecord(TABLE_REL_STU_INTERNSHIP, newRelJson);
        Integer recordAId = FastJsonUtil.toJson(savedRelObj).getInteger("id");

        // 5. 创建 MainVerifyProcess，获取最终 isAudit 和 verifyTypeId
        int[] verifyResult = createVerifyProcessForSelection(recordAId, internshipId, studentId);
        int isAudit = verifyResult[0];
        int verifyTypeId = verifyResult[1];

        JSONObject result = new JSONObject();
        result.put("isAudit", isAudit);
        result.put("internshipPostId", newPostId);
        result.put("verifyTypeId", verifyTypeId);
        return result;
    }

    /**
     * 更换岗位。
     * <p>若当前岗位已审核通过则拒绝更换；否则原子性更换后重置审核状态。</p>
     *
     * @return {@code {isAudit, internshipPostId, verifyTypeId}}
     */
    private JSONObject changePost(JSONObject recordA, Integer studentId,
                                  Integer oldPostId, Integer newPostId) {
        // 取新岗位的 internshipId
        MainInternshipPost newPost = mainInternshipPostDao.getByIdAndIsDeletedFalse(newPostId);
        if (newPost == null) {
            throw BaseResponse.moreInfoError.error("未找到目标岗位记录，ID: " + newPostId);
        }
        Integer internshipId = newPost.getInternshipId();

        // 服务端兜底：已有通过审核的报名则拦截（含当前 oldPost 若已通过）
        checkNoApprovedPostInSameInternship(studentId, internshipId);

        // 原子性尝试为新岗位 +1
        int affected = mainInternshipPostDao.incrementNowPersonNumIfNotFull(newPostId);
        if (affected == 0) {
            throw BaseResponse.parameterInvalid.error("该岗位已达到最大可选人数");
        }

        Integer recordAId = recordA.getInteger("id");

        // 修改 RelStuInternshipPost.internshipPostId 为 newPostId
        updateRelStuInternshipPostId(recordAId, newPostId);

        // 重置 MainVerifyProcess.isAudit 为 SUBMIT（0）
        updateMainVerifyProcessIsAudit(recordAId, Constant.AUDIT_STATUS.SUBMIT);

        // 原子性扣减旧岗位人数
        mainInternshipPostDao.decrementNowPersonNum(oldPostId);

        // 取 verifyTypeId 用于返回
        Object processObj = iVerifyProcessService.GetInternshipProcess(
                internshipId, Constant.PROCESS_TYPE.EXTERNAL_STUDENT_SELECT_POST);
        Integer verifyTypeId = FastJsonUtil.toJson(processObj).getInteger("verifyTypeId");

        JSONObject result = new JSONObject();
        result.put("isAudit", Constant.AUDIT_STATUS.SUBMIT);
        result.put("internshipPostId", newPostId);
        result.put("verifyTypeId", verifyTypeId);
        return result;
    }

    /**
     * 取消岗位选择。
     */
    private void cancelPostSelection(Integer recordAId, Integer oldPostId) {
        deleteMainVerifyProcessRecord(recordAId);
        iCommonService.deleteRecordByDelflag(TABLE_REL_STU_INTERNSHIP, recordAId);
        mainInternshipPostDao.decrementNowPersonNum(oldPostId);
    }

    // ─────────────────────────── 辅助方法 ───────────────────────────

    /**
     * 校验学生在同一实习项目下是否已有审核通过的报名，有则抛错拦截。
     */
    private void checkNoApprovedPostInSameInternship(Integer studentId, Integer internshipId) {
        long count = relStuInternshipPostDao
                .countApprovedPostForStudentInInternship(studentId, internshipId);
        if (count > 0) {
            throw BaseResponse.parameterInvalid.error("您已有审核通过的报名，无法继续选择岗位");
        }
    }

    /**
     * 为选岗创建 MainVerifyProcess 记录。
     *
     * @return int[]{isAudit, verifyTypeId}
     *         isAudit：0=SUBMIT（待审核），1=PASS（系统自动通过）
     *         verifyTypeId：流程的审核级别数（1=NO_VERIFY，>=2=需审核）
     */
    private int[] createVerifyProcessForSelection(Integer relationId,
                                                   Integer internshipId,
                                                   Integer createUserId) {
        Object processObj = iVerifyProcessService.GetInternshipProcess(
                internshipId, Constant.PROCESS_TYPE.EXTERNAL_STUDENT_SELECT_POST);
        JSONObject processJson = FastJsonUtil.toJson(processObj);
        Integer processId = processJson.getInteger("id");
        if (processId == null) {
            throw BaseResponse.moreInfoError.error("未找到流程配置信息");
        }

        Integer verifyTypeId = processJson.getInteger("verifyTypeId");
        if (verifyTypeId == null) {
            verifyTypeId = 1; // 默认 NO_VERIFY
        }
        boolean needsVerify = verifyTypeId >= 2;
        int isAudit;

        if (needsVerify) {
            // 需要审核：从第一级（level=2）开始
            Integer verifyRoleId = iVerifyProcessService.getVerifyRoleIdByLevel(processJson, 2);
            String verifyUserId = iVerifyProcessService.GetVerifyUserId(verifyRoleId, createUserId, internshipId);

            JSONObject updateEntityJson = new JSONObject();
            updateEntityJson.put("id", relationId);
            updateEntityJson.put("currentVerifyTypeId", 2);
            iCommonService.saveOneRecord(TABLE_REL_STU_INTERNSHIP, updateEntityJson);

            JSONObject verifyJson = new JSONObject();
            verifyJson.put("relationId", relationId);
            verifyJson.put("processId", processId);
            verifyJson.put("createUserId", createUserId);
            verifyJson.put("verifyUserId", verifyUserId);
            verifyJson.put("isAudit", Constant.AUDIT_STATUS.SUBMIT);
            verifyJson.put("reason", "");
            verifyJson.put("tableName", TABLE_REL_STU_INTERNSHIP);
            iCommonService.saveOneRecord(TABLE_MAIN_VERIFY_PROCESS, verifyJson);
            isAudit = Constant.AUDIT_STATUS.SUBMIT;
        } else {
            // 无需审核：直接通过（currentVerifyTypeId=2 > verifyTypeId=1）
            JSONObject updateEntityJson = new JSONObject();
            updateEntityJson.put("id", relationId);
            updateEntityJson.put("currentVerifyTypeId", 2);
            iCommonService.saveOneRecord(TABLE_REL_STU_INTERNSHIP, updateEntityJson);

            JSONObject verifyJson = new JSONObject();
            verifyJson.put("relationId", relationId);
            verifyJson.put("processId", processId);
            verifyJson.put("createUserId", createUserId);
            verifyJson.put("verifyUserId", "系统自动通过");
            verifyJson.put("isAudit", Constant.AUDIT_STATUS.PASS);
            verifyJson.put("reason", "系统自动通过");
            verifyJson.put("tableName", TABLE_REL_STU_INTERNSHIP);
            iCommonService.saveOneRecord(TABLE_MAIN_VERIFY_PROCESS, verifyJson);
            isAudit = Constant.AUDIT_STATUS.PASS;

            // 级联软删除同实习项目下该学生的其余报名记录
            iVerifyProcessService.cancelOtherStuPostsOnApproval(relationId, createUserId, internshipId);
        }

        return new int[]{isAudit, verifyTypeId};
    }

    /**
     * 查询 RelStuInternshipPost，根据 studentId 和 internshipPostId 找记录。
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
     * 删除 MainVerifyProcess 中与指定 relationId 关联的选岗记录（软删除）。
     */
    private void deleteMainVerifyProcessRecord(Integer relationId) {
        JSONObject searchKeys = new JSONObject();
        searchKeys.put("relationId", relationId);
        searchKeys.put("tableName", TABLE_REL_STU_INTERNSHIP);

        @SuppressWarnings("unchecked")
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                TABLE_MAIN_VERIFY_PROCESS, searchKeys, null, Sort.unsorted(), 1, 100);

        List<Object> content = page.getContent();
        if (content != null) {
            for (Object obj : content) {
                Integer id = FastJsonUtil.toJson(obj).getInteger("id");
                if (id != null) {
                    iCommonService.deleteRecordByDelflag(TABLE_MAIN_VERIFY_PROCESS, id);
                }
            }
        }
    }

    /**
     * 更新 RelStuInternshipPost.internshipPostId。
     */
    private void updateRelStuInternshipPostId(Integer recordAId, Integer newPostId) {
        JSONObject updateJson = new JSONObject();
        updateJson.put("id", recordAId);
        updateJson.put("internshipPostId", newPostId);
        iCommonService.saveOneRecord(TABLE_REL_STU_INTERNSHIP, updateJson);
    }

    /**
     * 更新与 relationId 关联的选岗审核记录的 isAudit 状态。
     */
    private void updateMainVerifyProcessIsAudit(Integer relationId, Integer isAudit) {
        JSONObject searchKeys = new JSONObject();
        searchKeys.put("relationId", relationId);
        searchKeys.put("tableName", TABLE_REL_STU_INTERNSHIP);

        @SuppressWarnings("unchecked")
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                TABLE_MAIN_VERIFY_PROCESS, searchKeys, null, Sort.unsorted(), 1, 100);

        List<Object> content = page.getContent();
        if (content != null) {
            for (Object obj : content) {
                Integer id = FastJsonUtil.toJson(obj).getInteger("id");
                if (id != null) {
                    JSONObject updateJson = new JSONObject();
                    updateJson.put("id", id);
                    updateJson.put("isAudit", isAudit);
                    iCommonService.saveOneRecord(TABLE_MAIN_VERIFY_PROCESS, updateJson);
                }
            }
        }
    }
}

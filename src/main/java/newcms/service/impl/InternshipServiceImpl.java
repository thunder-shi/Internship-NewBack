package newcms.service.impl;

import cn.hutool.poi.excel.ExcelReader;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import newcms.base.Base;
import newcms.base.BaseException;
import newcms.base.BaseResponse;
import newcms.base.Constant;
import newcms.entity.db.MainInternshipPost;
import newcms.entity.db.RelTitleStudent;
import newcms.entity.db.RelTitleTeacher;
import newcms.entity.db.ViewBaseUser;
import newcms.entity.db.ViewExternalInternshipStudentPostBreakdown;
import newcms.repository.db.MainInternshipPostDao;
import newcms.repository.db.RelStuInternshipPostDao;
import newcms.repository.db.RelTitleStudentDao;
import newcms.repository.db.RelTitleTeacherDao;
import newcms.repository.db.ViewExternalInternshipCollegeStatsDao;
import newcms.repository.db.ViewExternalInternshipStudentPostBreakdownDao;
import newcms.service.ICommonService;
import newcms.service.IDataTreeService;
import newcms.service.IDiaryService;
import newcms.service.IEnterpriseInfoService;
import newcms.service.IInternshipGradeConfigService;
import newcms.service.IInternshipPostService;
import newcms.service.IInternshipService;
import newcms.service.IInternshipTerminationService;
import newcms.service.IVerifyProcessService;
import newcms.utils.FastJsonUtil;
import newcms.utils.GeneralUtil;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class InternshipServiceImpl extends Base implements IInternshipService {
    private static final String TABLE_MAIN_DIARY = "MainDiary";
    private static final BigDecimal MIN_DIARY_SCORE = BigDecimal.ZERO;
    private static final BigDecimal MAX_DIARY_SCORE = new BigDecimal("100");
    /** 师生审核综合视图：企业/校外导师（原 ViewVerifyProcessRelTeacherStudentMerge 拆分） */
    private static final String VIEW_VERIFY_REL_ASS_TEA_STU_MERGE = "ViewVerifyProcessRelEntTeacherStudentMerge";
    /** 师生审核综合视图：校内导师 */
    private static final String VIEW_VERIFY_REL_INT_TEA_STU_MERGE = "ViewVerifyProcessRelIntTeacherStudentMerge";

    /** Int 合并视图查询统一限定：仅「校外实习-分配校内导师」流程（与 RelProcessInternship.processTypeCode 一致） */
    private static void applyExternalAssignInternalTutorMergeFilter(JSONObject searchKeys) {
        searchKeys.put("processTypeCode", Constant.PROCESS_TYPE.EXTERNAL_ASSIGN_INTERNAL_TUTOR);
    }
    private static final int LARGE_PAGE_SIZE = 100000;
    private static final int POST_PAGE_SIZE = 10000;
    /** 与库中 base_int_type / view_main_internship.int_type_name 一致，用于识别校外实习 */
    private static final String EXTERNAL_INT_TYPE_NAME = "校外实习";
    /** {@code base_department.the_level}：学校下学院节点（与 {@code base_internship_type.university_id} 一致） */
    private static final int COLLEGE_DEPARTMENT_LEVEL = 2;
    /** 与库中 view_main_internship.int_type_name 一致，用于识别校内实习 */
    private static final String INTERNAL_INT_TYPE_NAME = "校内实习";
    private static final String STU_POST_STATUS_ALL = "all";
    private static final String STU_POST_STATUS_NOT_SELECTED = "notSelected";
    /** 已报名：有任意选岗记录（合并 selectedPendingAudit + postApproved，按 userId 去重） */
    private static final String STU_POST_STATUS_SELECTED = "selected";
    private static final String STU_POST_STATUS_SELECTED_PENDING = "selectedPendingAudit";
    private static final String STU_POST_STATUS_POST_APPROVED = "postApproved";

    private static final String TITLE_SEL_STATUS_ALL = "all";
    private static final String TITLE_SEL_STATUS_NOT_SUBMITTED = "notSubmitted";
    private static final String TITLE_SEL_STATUS_PENDING = "pendingAudit";
    private static final String TITLE_SEL_STATUS_APPROVED = "titleApproved";
    private static final String TITLE_SOURCE_STUDENT_CANDIDATE = "STUDENT_CANDIDATE";
    private static final String TITLE_SOURCE_TEACHER_ASSIGN = "TEACHER_ASSIGN";
    private static final ConcurrentHashMap<String, Object> TITLE_STUDENT_LOCKS = new ConcurrentHashMap<>();

    @Resource
    private ICommonService iCommonService;

    @Resource
    private IEnterpriseInfoService enterpriseInfoService;

    @Resource
    private IVerifyProcessService iVerifyProcessService;

    @Resource
    private IInternshipTerminationService internshipTerminationService;

    @Resource
    private ViewExternalInternshipCollegeStatsDao viewExternalInternshipCollegeStatsDao;

    @Resource
    private ViewExternalInternshipStudentPostBreakdownDao viewExternalInternshipStudentPostBreakdownDao;

    @Resource
    private IDataTreeService iDataTreeService;

    @Resource
    private MainInternshipPostDao mainInternshipPostDao;

    @Resource
    private RelTitleStudentDao relTitleStudentDao;

    @Resource
    private RelTitleTeacherDao relTitleTeacherDao;

    @Resource
    private RelStuInternshipPostDao relStuInternshipPostDao;

    @Resource
    private IInternshipGradeConfigService gradeConfigService;

    @Resource
    private IInternshipPostService iInternshipPostService;

    @Resource
    private IDiaryService iDiaryService;

    /** 自注入代理：用于在主事务 afterCommit 后以独立事务调用本类方法（绕开 this. 的 AOP 失效问题）。 */
    @Resource
    @Lazy
    private IInternshipService selfProxy;

    /** 自主实习虚拟岗位 code（与前端 CONSTANT.SELF_INTERNSHIP.POST_CODE 一致）。 */
    private static final String SELF_INTERNSHIP_POST_CODE = "SELF_INTERNSHIP";
    /** 自主实习虚拟岗位 name（与前端 CONSTANT.SELF_INTERNSHIP.POST_NAME 一致）。 */
    private static final String SELF_INTERNSHIP_POST_NAME = "自主实习";
    /** 自主实习虚拟岗位无限招（allPersonNum=-1，跳过满员校验）。 */
    private static final int SELF_INTERNSHIP_UNLIMITED = -1;
    /** SysOssFile 关联表名（PascalCase，与 MainVerifyProcess.tableName 统一）。 */
    private static final String TABLE_REL_STU_INTERNSHIP_POST = "RelStuInternshipPost";

    // ==================== 实习项目管理====================

    @Override
    public Object addNewInternship(JSONObject node) {
        if (node == null) {
            throw BaseResponse.parameterInvalid.error("node 不能为空");
        }
        Integer internshipTypeIdForCheck = node.getInteger("internshipTypeId");
        if (isExternalInternshipType(internshipTypeIdForCheck) && isCurrentUserCompanyAdmin()) {
            enterpriseInfoService.assertCurrentUserCanDeclareExternal();
        }
        // (1) 在 MainInternship 实体增加一条记录
        Object savedInternship = iCommonService.saveOneRecord("MainInternship", node);
        JSONObject savedInternshipJson = FastJsonUtil.toJson(savedInternship);
        Integer internshipId = savedInternshipJson.getInteger("id");
        Integer internshipTypeId = savedInternshipJson.getInteger("internshipTypeId");
        // 参数校验
        if (internshipId == null || internshipTypeId == null) {
            throw BaseResponse.moreInfoError.error("实习项目ID或实习类型ID不能为空");
        }
        // (2) 创建专业范围关联记录
        createInternshipMajorRecords(internshipId, internshipTypeId);
        // (3) 创建流程关联记录
        createProcessInternshipRecords(internshipId, internshipTypeId);
        // (4) 根据流程配置设置 MainInternship 的初始 currentVerifyTypeId
        initInternshipVerifyLevel(internshipId);
        // (5) 创建"实习计划制定"流程的第一条 MainVerifyProcess 记录
        createFirstVerifyProcessRecord(internshipId);
        // (6) 若是校外实习，在主事务 commit 之后以独立事务幂等创建 SELF_INTERNSHIP 虚拟岗位。
        //     用 afterCommit + 自注入代理，而不是 try-catch —— 共享事务里内层异常会把整体标为
        //     rollback-only，try-catch 拦不住。独立事务失败只记日志，不影响主流程。
        if (isExternalInternship(internshipId)) {
            final Integer finalInternshipId = internshipId;
            Runnable hook = () -> {
                try {
                    selfProxy.createSelfInternshipPost(finalInternshipId);
                } catch (Exception e) {
                    logger.warn("为校外实习项目 {} 创建自主实习虚拟岗位失败（不回滚主流程）：{}",
                            finalInternshipId, e.getMessage(), e);
                }
            };
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        hook.run();
                    }
                });
            } else {
                hook.run();
            }
        }
        return savedInternship;
    }

    /**
     * 判定实习项目是否为「校外实习」。以 {@code ViewMainInternship.intTypeName} 字符串匹配为准。
     */
    @SuppressWarnings("unchecked")
    private boolean isExternalInternship(Integer internshipId) {
        Object viewObj = iCommonService.getOneRecordById("ViewMainInternship", internshipId);
        if (viewObj == null) {
            return false;
        }
        String intTypeName = FastJsonUtil.toJson(viewObj).getString("intTypeName");
        return EXTERNAL_INT_TYPE_NAME.equals(intTypeName);
    }

    private boolean isExternalInternshipType(Integer internshipTypeId) {
        if (internshipTypeId == null) {
            return false;
        }
        Object typeObj = iCommonService.getOneRecordById("ViewBaseInternshipType", internshipTypeId);
        if (typeObj == null) {
            return false;
        }
        String typeName = FastJsonUtil.toJson(typeObj).getString("typeName");
        return EXTERNAL_INT_TYPE_NAME.equals(typeName);
    }

    private boolean isCurrentUserCompanyAdmin() {
        Object userObj = iCommonService.getOneRecordById("ViewBaseUser", getLoginUserId());
        if (userObj == null) {
            return false;
        }
        String jobCode = FastJsonUtil.toJson(userObj).getString("jobCode");
        return Constant.USER_JOB_CODE.COMPANY_ADMIN.equals(jobCode);
    }

    @Override
    public JSONObject createSelfInternshipPost(Integer internshipId) {
        if (internshipId == null) {
            throw BaseResponse.parameterInvalid.error("internshipId 不能为空");
        }
        // 幂等：已存在则直接返回
        java.util.Optional<MainInternshipPost> existing = mainInternshipPostDao
                .findFirstByInternshipIdAndCodeAndIsDeletedFalse(internshipId, SELF_INTERNSHIP_POST_CODE);
        if (existing.isPresent()) {
            JSONObject result = new JSONObject();
            result.put("postId", existing.get().getId());
            result.put("created", false);
            return result;
        }
        // 新建虚拟岗位
        JSONObject postJson = new JSONObject();
        postJson.put("code", SELF_INTERNSHIP_POST_CODE);
        postJson.put("name", SELF_INTERNSHIP_POST_NAME);
        postJson.put("allPersonNum", SELF_INTERNSHIP_UNLIMITED);
        postJson.put("nowPersonNum", 0);
        postJson.put("internshipId", internshipId);
        postJson.put("currentVerifyTypeId", Constant.VERIFY_LEVEL.NO_VERIFY);
        Object saved = iCommonService.saveOneRecord("MainInternshipPost", postJson);
        Integer postId = FastJsonUtil.toJson(saved).getInteger("id");
        if (postId == null) {
            throw BaseResponse.moreInfoError.error("创建自主实习虚拟岗位失败");
        }
        // 若项目下存在「企业岗位申报」流程，为该岗位挂一条自动通过审核；不存在则跳过
        tryWriteAutoPassPostDeclaration(internshipId, postId);

        JSONObject result = new JSONObject();
        result.put("postId", postId);
        result.put("created", true);
        return result;
    }

    /**
     * 若实习项目下存在 {@code EXTERNAL_ENTERPRISE_POST_DECLARATION} 流程，为自主实习虚拟岗位追加一条
     * 自动通过的 {@code MainVerifyProcess}；流程不存在时静默跳过。
     */
    @SuppressWarnings("unchecked")
    private void tryWriteAutoPassPostDeclaration(Integer internshipId, Integer postId) {
        JSONObject sk = new JSONObject();
        sk.put("internshipId", internshipId);
        sk.put("processTypeCode", Constant.PROCESS_TYPE.EXTERNAL_ENTERPRISE_POST_DECLARATION);
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                "ViewRelProcessInternship", sk, null, Sort.unsorted(), 1, 1);
        if (page == null || page.getContent() == null || page.getContent().isEmpty()) {
            return;
        }
        JSONObject process = FastJsonUtil.toJson(page.getContent().get(0));
        Integer processId = process.getInteger("id");
        Integer verifyTypeId = process.getInteger("verifyTypeId");
        if (processId == null) {
            return;
        }
        JSONObject vp = new JSONObject();
        vp.put("relationId", postId);
        vp.put("processId", processId);
        vp.put("createUserId", Base.getLoginUserId());
        vp.put("verifyUserId", Constant.SYSTEM_AUDIT_NOTE.AUTO_PASS);
        vp.put("isAudit", Constant.AUDIT_STATUS.PASS);
        vp.put("reason", "系统创建：自主实习岗位");
        vp.put("tableName", "MainInternshipPost");
        if (verifyTypeId != null) {
            vp.put("verifyTypeId", verifyTypeId);
        }
        iCommonService.saveOneRecord("MainVerifyProcess", vp);
    }

    @Override
    @SuppressWarnings("unchecked")
    public JSONObject applySelfInternship(Integer internshipId, Integer studentId,
                                          String selfCompanyName, String selfPostName,
                                          String selfAddress, String selfRemarks) {
        if (internshipId == null) {
            throw BaseResponse.parameterInvalid.error("internshipId 不能为空");
        }
        if (studentId == null) {
            throw BaseResponse.parameterInvalid.error("学生 id 不能为空");
        }
        if (selfCompanyName == null || selfCompanyName.trim().isEmpty()) {
            throw BaseResponse.parameterInvalid.error("自主实习单位名称不能为空");
        }
        if (selfPostName == null || selfPostName.trim().isEmpty()) {
            throw BaseResponse.parameterInvalid.error("自主实习岗位名称不能为空");
        }
        if (selfAddress == null || selfAddress.trim().isEmpty()) {
            throw BaseResponse.parameterInvalid.error("自主实习地址不能为空");
        }
        // 预检：若该学生在本项目下已有企业岗位 PASS，则禁止再申请自主实习
        // （企业岗位 PASS 触发时已级联软删历史自主记录；此处兜底拦截新申请）
        if (relStuInternshipPostDao.countApprovedCompanyPostForStudentInInternship(studentId, internshipId) > 0) {
            throw BaseResponse.parameterInvalid.error("当前项目已通过企业岗位审核，不能再申请自主实习");
        }
        // 1. 确保虚拟岗位存在
        JSONObject postInfo = createSelfInternshipPost(internshipId);
        Integer selfPostId = postInfo.getInteger("postId");

        // 2. 预检：项目下必须配 SELF_DECLARATION 流程
        JSONObject processJson = findSelfDeclarationProcessOrThrow(internshipId);
        Integer processId = processJson.getInteger("id");
        Integer verifyTypeId = processJson.getInteger("verifyTypeId");
        if (verifyTypeId == null) {
            verifyTypeId = Constant.VERIFY_LEVEL.NO_VERIFY;
        }

        // 3. 查已有自主实习记录
        java.util.Optional<Integer> existingRelIdOpt = relStuInternshipPostDao
                .findActiveSelfInternshipRelId(studentId, internshipId);

        boolean reapplyInPlace = false;
        Integer relStuPostId;
        if (existingRelIdOpt.isPresent()) {
            Integer existingRelId = existingRelIdOpt.get();
            Integer latestIsAudit = getLatestIsAuditOfRelStuInternshipPost(existingRelId);
            if (latestIsAudit == null) {
                // 无审核记录：视为新建路径，但 relStuInternshipPost 已存在，复用
                relStuPostId = existingRelId;
                reapplyInPlace = true;
            } else if (latestIsAudit == Constant.AUDIT_STATUS.NOTPASS) {
                relStuPostId = existingRelId;
                reapplyInPlace = true;
            } else {
                // SAVE / SUBMIT / PASS / BACK → 拒绝
                throw BaseResponse.parameterInvalid.error(audittStatusToMessage(latestIsAudit));
            }
        } else {
            relStuPostId = null;
        }

        if (reapplyInPlace) {
            return reapplySelfInternshipInPlace(relStuPostId, studentId, internshipId, selfPostId, processId,
                    verifyTypeId, selfCompanyName, selfPostName, selfAddress, selfRemarks);
        }
        return createSelfInternshipFirstTime(studentId, internshipId, selfPostId, processId, verifyTypeId,
                selfCompanyName, selfPostName, selfAddress, selfRemarks);
    }

    private String audittStatusToMessage(int isAudit) {
        if (isAudit == Constant.AUDIT_STATUS.PASS) {
            return "已有审核通过的自主实习申请，无法重复申请";
        }
        if (isAudit == Constant.AUDIT_STATUS.SUBMIT) {
            return "自主实习申请正在审核中，请勿重复提交";
        }
        if (isAudit == Constant.AUDIT_STATUS.SAVE) {
            return "已有未提交的自主实习草稿，请先完成提交或撤回";
        }
        if (isAudit == Constant.AUDIT_STATUS.BACK) {
            return "已有审核退回的自主实习记录，请在原记录上修改后重新提交";
        }
        return "已有进行中的自主实习申请";
    }

    @SuppressWarnings("unchecked")
    private Integer getLatestIsAuditOfRelStuInternshipPost(Integer relationId) {
        JSONObject sk = new JSONObject();
        sk.put("relationId", relationId);
        sk.put("tableName", TABLE_REL_STU_INTERNSHIP_POST);
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                "MainVerifyProcess", sk, null, Sort.by(Sort.Direction.DESC, "id"), 1, 1);
        if (page == null || page.getContent() == null || page.getContent().isEmpty()) {
            return null;
        }
        return FastJsonUtil.toJson(page.getContent().get(0)).getInteger("isAudit");
    }

    @SuppressWarnings("unchecked")
    private JSONObject findSelfDeclarationProcessOrThrow(Integer internshipId) {
        JSONObject sk = new JSONObject();
        sk.put("internshipId", internshipId);
        sk.put("processTypeCode", Constant.PROCESS_TYPE.EXTERNAL_STUDENT_SELF_DECLARATION);
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                "ViewRelProcessInternship", sk, null, Sort.unsorted(), 1, 1);
        if (page == null || page.getContent() == null || page.getContent().isEmpty()) {
            throw BaseResponse.parameterInvalid.error("当前项目未开通自主实习申请");
        }
        return FastJsonUtil.toJson(page.getContent().get(0));
    }

    /**
     * 首次申请自主实习：新建 RelStuInternshipPost + 写首条 MainVerifyProcess。
     */
    private JSONObject createSelfInternshipFirstTime(Integer studentId, Integer internshipId,
                                                     Integer selfPostId, Integer processId, Integer verifyTypeId,
                                                     String selfCompanyName, String selfPostName,
                                                     String selfAddress, String selfRemarks) {
        JSONObject relJson = new JSONObject();
        relJson.put("studentId", studentId);
        relJson.put("internshipPostId", selfPostId);
        relJson.put("selfCompanyName", selfCompanyName);
        relJson.put("selfPostName", selfPostName);
        relJson.put("selfAddress", selfAddress);
        relJson.put("selfRemarks", selfRemarks);
        Object saved = iCommonService.saveOneRecord("RelStuInternshipPost", relJson);
        Integer relStuPostId = FastJsonUtil.toJson(saved).getInteger("id");
        if (relStuPostId == null) {
            throw BaseResponse.moreInfoError.error("保存自主实习申请失败");
        }
        int isAudit = writeSelfInternshipVerifyProcess(relStuPostId, processId, verifyTypeId, studentId);

        JSONObject out = new JSONObject();
        out.put("relStuInternshipPostId", relStuPostId);
        out.put("isAudit", isAudit);
        out.put("verifyTypeId", verifyTypeId);
        out.put("created", true);
        return out;
    }

    /**
     * 重投自主实习：复用 RelStuInternshipPost.id，覆盖 self_* + 软删旧审核记录 + 写新 MainVerifyProcess
     * + 清空该 relationId 下的所有 SysOssFile（按前端澄清：NOTPASS 重投不保留附件）。
     */
    @SuppressWarnings("unchecked")
    private JSONObject reapplySelfInternshipInPlace(Integer relStuPostId, Integer studentId, Integer internshipId,
                                                    Integer selfPostId, Integer processId, Integer verifyTypeId,
                                                    String selfCompanyName, String selfPostName,
                                                    String selfAddress, String selfRemarks) {
        // update in place：内部字段覆盖；currentVerifyTypeId 视是否需审核决定
        JSONObject updateJson = new JSONObject();
        updateJson.put("id", relStuPostId);
        updateJson.put("selfCompanyName", selfCompanyName);
        updateJson.put("selfPostName", selfPostName);
        updateJson.put("selfAddress", selfAddress);
        updateJson.put("selfRemarks", selfRemarks);
        updateJson.put("internshipPostId", selfPostId); // 兜底：防旧记录指向已删岗位
        iCommonService.saveOneRecord("RelStuInternshipPost", updateJson);

        // 软删旧审核记录
        JSONObject sk = new JSONObject();
        sk.put("relationId", relStuPostId);
        sk.put("tableName", TABLE_REL_STU_INTERNSHIP_POST);
        Page<Object> oldVps = (Page<Object>) iCommonService.getSomeRecords(
                "MainVerifyProcess", sk, null, Sort.unsorted(), 1, 100);
        if (oldVps != null && oldVps.getContent() != null) {
            for (Object vp : oldVps.getContent()) {
                Integer vpId = FastJsonUtil.toJson(vp).getInteger("id");
                if (vpId != null) {
                    iCommonService.deleteRecordByDelflag("MainVerifyProcess", vpId);
                }
            }
        }

        // 清空附件（按前端澄清 #2：NOTPASS 重投 = 清空附件，学生重新上传）
        JSONObject fileSk = new JSONObject();
        fileSk.put("relationIds", relStuPostId);
        fileSk.put("tableName", TABLE_REL_STU_INTERNSHIP_POST);
        Page<Object> files = (Page<Object>) iCommonService.getSomeRecords(
                "SysOssFile", fileSk, null, Sort.unsorted(), 1, 1000);
        if (files != null && files.getContent() != null) {
            for (Object f : files.getContent()) {
                Integer fid = FastJsonUtil.toJson(f).getInteger("id");
                if (fid != null) {
                    iCommonService.deleteRecordByDelflag("SysOssFile", fid);
                }
            }
        }

        int isAudit = writeSelfInternshipVerifyProcess(relStuPostId, processId, verifyTypeId, studentId);

        JSONObject out = new JSONObject();
        out.put("relStuInternshipPostId", relStuPostId);
        out.put("isAudit", isAudit);
        out.put("verifyTypeId", verifyTypeId);
        out.put("created", false);
        return out;
    }

    /**
     * 为自主实习报名写一条 MainVerifyProcess。需审核时 isAudit=SUBMIT 并按首级审核角色解析 verifyUserId；
     * 无需审核（verifyTypeId=NO_VERIFY）时直接 PASS，但**不触发级联**删除其他企业岗位报名。
     *
     * @return 写入的 isAudit
     */
    private int writeSelfInternshipVerifyProcess(Integer relStuPostId, Integer processId, Integer verifyTypeId,
                                                 Integer studentId) {
        Object processObj = iCommonService.getOneRecordById("ViewRelProcessInternship", processId);
        JSONObject processJson = FastJsonUtil.toJson(processObj);
        boolean needsVerify = verifyTypeId != null && verifyTypeId >= Constant.VERIFY_LEVEL.ONE_VERIFY;

        if (needsVerify) {
            Integer verifyRoleId = iVerifyProcessService.getVerifyRoleIdByLevel(processJson, 2);
            Integer internshipId = processJson.getInteger("internshipId");
            String verifyUserId = iVerifyProcessService.GetVerifyUserId(verifyRoleId, studentId, internshipId);

            JSONObject upd = new JSONObject();
            upd.put("id", relStuPostId);
            upd.put("currentVerifyTypeId", 2);
            iCommonService.saveOneRecord("RelStuInternshipPost", upd);

            JSONObject vp = new JSONObject();
            vp.put("relationId", relStuPostId);
            vp.put("processId", processId);
            vp.put("createUserId", studentId);
            vp.put("verifyUserId", verifyUserId);
            vp.put("isAudit", Constant.AUDIT_STATUS.SUBMIT);
            vp.put("reason", "");
            vp.put("tableName", TABLE_REL_STU_INTERNSHIP_POST);
            iCommonService.saveOneRecord("MainVerifyProcess", vp);
            return Constant.AUDIT_STATUS.SUBMIT;
        }

        // NO_VERIFY：直接通过（currentVerifyTypeId=2 > verifyTypeId=1）
        JSONObject upd = new JSONObject();
        upd.put("id", relStuPostId);
        upd.put("currentVerifyTypeId", 2);
        iCommonService.saveOneRecord("RelStuInternshipPost", upd);

        JSONObject vp = new JSONObject();
        vp.put("relationId", relStuPostId);
        vp.put("processId", processId);
        vp.put("createUserId", studentId);
        vp.put("verifyUserId", Constant.SYSTEM_AUDIT_NOTE.AUTO_PASS);
        vp.put("isAudit", Constant.AUDIT_STATUS.PASS);
        vp.put("reason", Constant.SYSTEM_AUDIT_NOTE.AUTO_PASS);
        vp.put("tableName", TABLE_REL_STU_INTERNSHIP_POST);
        Object savedVp = iCommonService.saveOneRecord("MainVerifyProcess", vp);
        Integer vpId = FastJsonUtil.toJson(savedVp).getInteger("id");
        // 统一走审核通过入口触发下游级联：
        //   - cancelOtherStuPostsOnApproval 删该学生该项目下所有企业岗位报名（"同项目一岗位"互斥）
        //   - ensureSeparateTutorAssignmentsAfterStuPostApproved 生成导师分配占位
        // 自主岗位 code=SELF_INTERNSHIP 在 onVerifyProcessApproved 里会自动豁免 incrementNowPersonNum 等人数操作。
        if (vpId != null) {
            iVerifyProcessService.onVerifyProcessApproved(vpId);
        }
        return Constant.AUDIT_STATUS.PASS;
    }



    /**
     * 创建实习项目的流程关联记录
     * @param internshipId 实习项目ID
     * @param internshipTypeId 实习类型ID
     */
    private void createProcessInternshipRecords(Integer internshipId, Integer internshipTypeId) {
        // (1) 查找 RelProcessInternshipType 所有 internshipTypeId 和新增实体的 internshipTypeId 值相等的记录
        JSONObject searchKeys = new JSONObject();
        searchKeys.put("internshipTypeId", internshipTypeId);
        @SuppressWarnings("unchecked")
        Page<Object> processTypePage = (Page<Object>) iCommonService.getSomeRecords("ViewRelProcessInternshipType", searchKeys, null, Sort.unsorted());
        List<Object> processTypeList = processTypePage.getContent();
        // (2) 在 RelProcessInternship 实体中增加若干条记录
        List<Object> processInternshipList = new ArrayList<>();
        for (Object processTypeObj : processTypeList) {
            JSONObject processTypeJson = FastJsonUtil.toJson(processTypeObj);
            JSONObject processInternshipJson = new JSONObject();
            // internshipId 是当前实习项目的id
            processInternshipJson.put("internshipId", internshipId);
            // 其他属性直接带入 RelProcessInternshipType 中的对应值
            processInternshipJson.put("processTypeId", processTypeJson.getInteger("processTypeId"));
            processInternshipJson.put("verifyTypeId", processTypeJson.getInteger("verifyTypeId"));
            processInternshipJson.put("verifyFirstRoleId", processTypeJson.getInteger("verifyFirstRoleId"));
            processInternshipJson.put("verifySecondRoleId", processTypeJson.getInteger("verifySecondRoleId"));
            processInternshipJson.put("verifyThirdRoleId", processTypeJson.getInteger("verifyThirdRoleId"));
            processInternshipJson.put("verifyFourthRoleId", processTypeJson.getInteger("verifyFourthRoleId"));
            processInternshipJson.put("verifyFifthRoleId", processTypeJson.getInteger("verifyFifthRoleId"));
            processInternshipList.add(processInternshipJson);
        }
        // 批量保存 RelProcessInternship 记录
        if (!processInternshipList.isEmpty()) {
            iCommonService.saveSomeRecords("RelProcessInternship", processInternshipList);
        }
    }

    /**
     * 根据流程配置设置 MainInternship 的初始 currentVerifyTypeId
     * @param internshipId 实习项目ID
     */
    private void initInternshipVerifyLevel(Integer internshipId) {
        Object foundProcess = iVerifyProcessService.GetInternshipProcess(internshipId, null);
        JSONObject relJson = FastJsonUtil.toJson(foundProcess);
        String verifyTypeCode = relJson.getString("verifyTypeCode");
        int initialLevel;
        if ("NO_VERIFY".equals(verifyTypeCode)) {
            initialLevel = Constant.VERIFY_LEVEL.NO_VERIFY;
        } else {
            initialLevel = Constant.VERIFY_LEVEL.ONE_VERIFY;
        }
        JSONObject updateJson = new JSONObject();
        updateJson.put("id", internshipId);
        updateJson.put("currentVerifyTypeId", initialLevel);
        iCommonService.saveOneRecord("MainInternship", updateJson);
    }

    /**
     * 创建"实习计划制定"流程的第一条 MainVerifyProcess 记录
     * @param internshipId 实习项目ID
     */
    private void createFirstVerifyProcessRecord(Integer internshipId) {
        // 1. 查找流程关联记录（取第一条，对应计划制定流程）
        Object foundProcess = iVerifyProcessService.GetInternshipProcess(internshipId, null);
        JSONObject relJson = FastJsonUtil.toJson(foundProcess);
        Integer relProcessId = relJson.getInteger("id");
        Integer verifyFirstRoleId = relJson.getInteger("verifyFirstRoleId");
        // 2. 从 MainInternship 获取 createUserId 和 currentVerifyTypeId
        Object internshipObj = iCommonService.getOneRecordById("MainInternship", internshipId);
        if (internshipObj == null) {
            throw BaseResponse.moreInfoError.error("未找到实习项目记录");
        }
        JSONObject internshipJson = FastJsonUtil.toJson(internshipObj);
        Integer createUserId = internshipJson.getInteger("creatorId");
        if (createUserId == null) {
            throw BaseResponse.parameterInvalid.error("creatorId 参数不能为空");
        }
        Integer currentVerifyTypeId = internshipJson.getInteger("currentVerifyTypeId");

        // 判断是否无需审核（currentVerifyTypeId == 1 即 NO_VERIFY）
        boolean noVerify = currentVerifyTypeId != null
                && currentVerifyTypeId == Constant.VERIFY_LEVEL.NO_VERIFY;

        String verifyUserId;
        int isAudit;
        if (noVerify) {
            // 无需审核：直接标记为通过
            verifyUserId = Constant.SYSTEM_AUDIT_NOTE.AUTO_PASS;
            isAudit = 1;
        } else {
            // 需要审核：获取审核用户ID字符串，状态为未提交
            verifyUserId = iVerifyProcessService.GetVerifyUserId(verifyFirstRoleId, createUserId, internshipId);
            isAudit = -1;
        }

        // 创建审核记录
        JSONObject verifyJson = new JSONObject();
        verifyJson.put("relationId", internshipId);
        verifyJson.put("processId", relProcessId);
        verifyJson.put("createUserId", createUserId);
        verifyJson.put("isAudit", isAudit);
        verifyJson.put("reason", "");
        verifyJson.put("tableName", "MainInternship");
        verifyJson.put("verifyUserId", verifyUserId);
        iCommonService.saveOneRecord("MainVerifyProcess", verifyJson);
    }

    /**
     * 创建实习项目的专业范围关联记录
     * @param internshipId 实习项目ID
     * @param internshipTypeId 实习类型ID
     */
    private void createInternshipMajorRecords(Integer internshipId, Integer internshipTypeId) {
        // (1) 查找当前新增实习项目的模板 internshipTypeId 在 RelInterTypeMajor 中所有 internshipTypeId 相等的记录，得到它们的 majorId
        JSONObject searchKeys = new JSONObject();
        searchKeys.put("internshipTypeId", internshipTypeId);
        @SuppressWarnings("unchecked")
        Page<Object> interTypeMajorPage = (Page<Object>) iCommonService.getSomeRecords("RelInterTypeMajor", searchKeys, null, Sort.unsorted());
        List<Object> interTypeMajorList = interTypeMajorPage.getContent();

        // (2) 在 RelInterMajor 实体中创建数量相同的若干记录
        List<Object> interMajorList = new ArrayList<>();
        for (Object interTypeMajorObj : interTypeMajorList) {
            JSONObject interTypeMajorJson = FastJsonUtil.toJson(interTypeMajorObj);
            JSONObject interMajorJson = new JSONObject();
            
            // internshipId 都是当前的 internshipId
            interMajorJson.put("internshipId", internshipId);
            // majorId 就是查找到的 majorId
            interMajorJson.put("majorId", interTypeMajorJson.getInteger("majorId"));
            interMajorList.add(interMajorJson);
        }
        // 批量保存 RelInterMajor 记录
        if (!interMajorList.isEmpty()) {
            iCommonService.saveSomeRecords("RelInterMajor", interMajorList);
        }
    }

    @Override
    public Object getAvailableUsersForInternship(Integer internshipId, String jobCode, List<Integer> departmentIds, Integer page, Integer size, Sort sort) {
        return getAvailableUsersForInternshipCore(internshipId, jobCode, departmentIds, page, size, sort, true);
    }

    /**
     * @param expandDepartmentSubtree true：每个 departmentId 展开为其子树后再 IN；false：仅按传入 id 列表 IN（用于 batchInit，前端传末级部门数组）
     */
    @SuppressWarnings("unchecked")
    private Object getAvailableUsersForInternshipCore(Integer internshipId, String jobCode, List<Integer> departmentIds,
            Integer page, Integer size, Sort sort, boolean expandDepartmentSubtree) {
        if (internshipId == null || jobCode == null || jobCode.trim().isEmpty()) {
            throw BaseResponse.parameterInvalid.error("internshipId 和 jobCode 不能为空");
        }

        int pageNum = (page == null || page < 1) ? Constant.DEFAULT_PAGE : page;
        int pageSize = (size == null || size < 1) ? Constant.DEFAULT_SIZE : size;
        if (departmentIds == null || departmentIds.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), PageRequest.of(pageNum - 1, pageSize), 0);
        }

        // 1. 查出该实习项目下已经在 RelIntershipUser 中关联过的 userId 列表（只看未删除的关联）
        JSONObject relSearchKeys = new JSONObject();
        relSearchKeys.put("internshipId", internshipId);
        relSearchKeys.put("isDeleted", 0);

        Page<Object> relPage = (Page<Object>) iCommonService.getSomeRecords(
                "RelIntershipUser", relSearchKeys, null, Sort.unsorted()
        );

        List<Object> relList = relPage.getContent();
        Set<Integer> usedUserIdSet = relList.stream()
                .map(FastJsonUtil::toJson)
                .map(json -> json.getInteger("userId"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 2. 组装 ViewBaseUser 的查询条件：
        //    - jobCode：STUDENT 仍按等值；SCHOOL_TEACHER / COMPANY_TUTOR 改为「非学生」（jobCode != STUDENT）
        //    - departmentId IN（expand=true：各节点及其子树并集；expand=false：仅传入列表，不判断父子）
        //    - id NOT IN (已关联且未删除的 userId 列表)
        JSONObject userSearchKeys = new JSONObject();
        Map<String, String> repMap = new HashMap<>();
        // userSearchKeys.put("jobCode", jobCode); // 原：严格按传入 jobCode 过滤
        if (Constant.USER_JOB_CODE.SCHOOL_TEACHER.equals(jobCode)
                || Constant.USER_JOB_CODE.COMPANY_TUTOR.equals(jobCode)) {
            userSearchKeys.put("jobCode", Constant.USER_JOB_CODE.STUDENT);
            repMap.put("jobCode", Constant.NE);
        } else {
            userSearchKeys.put("jobCode", jobCode);
        }

        Set<Integer> allowedDepartmentIds = new HashSet<>();
        for (Integer departmentId : departmentIds) {
            if (departmentId == null) {
                continue;
            }
            if (expandDepartmentSubtree) {
                allowedDepartmentIds.addAll(collectDepartmentIdsInSubtree(departmentId));
            } else {
                allowedDepartmentIds.add(departmentId);
            }
        }
        if (allowedDepartmentIds.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), PageRequest.of(pageNum - 1, pageSize), 0);
        }
        if (allowedDepartmentIds.size() == 1) {
            userSearchKeys.put("departmentId", allowedDepartmentIds.iterator().next());
        } else {
            String departmentIdStr = allowedDepartmentIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(Constant.SPLIT_OPERATOR.COMMA));
            userSearchKeys.put("departmentId", departmentIdStr);
            repMap.put("departmentId", Constant.IN);
        }

        if (!usedUserIdSet.isEmpty()) {
            String idStr = usedUserIdSet.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(Constant.SPLIT_OPERATOR.COMMA));
            userSearchKeys.put("id", idStr);
            // 使用 Constant.NOT_IN 实现 “id NOT IN (...)”
            repMap.put("id", Constant.NOT_IN);
        }

        // 3. 通过通用的 getSomeRecords 分页查询 ViewBaseUser（带排序）
        Sort finalSort = (sort == null) ? Sort.unsorted() : sort;
        return iCommonService.getSomeRecords(
                "ViewBaseUser",
                userSearchKeys,
                repMap,
                finalSort,
                pageNum,
                pageSize);
    }

    @Override
    public Object batchInitRelIntershipUserFromAvailable(Integer internshipId, String jobCode, List<Integer> departmentIds,
                                                         Integer processId, Integer createUserId, Integer verifyRoleId,
                                                         Integer currentVerifyTypeId) {
        if (internshipId == null || processId == null || createUserId == null) {
            throw BaseResponse.parameterInvalid.error("internshipId、processId、createUserId 不能为空");
        }
        if (jobCode == null || jobCode.trim().isEmpty()) {
            throw BaseResponse.parameterInvalid.error("jobCode 不能为空");
        }
        if (departmentIds == null || departmentIds.isEmpty()) {
            throw BaseResponse.parameterInvalid.error("departmentIds 不能为空");
        }
        int verifyType = (currentVerifyTypeId == null ? 1 : currentVerifyTypeId);
        if (verifyType <= 0) {
            throw BaseResponse.parameterInvalid.error("currentVerifyTypeId 无效，必须为正整数");
        }

        String verifyUserId = iVerifyProcessService.GetVerifyUserId(verifyRoleId, createUserId, internshipId);
        if (verifyUserId == null) {
            verifyUserId = "";
        }

        List<Integer> candidateUserIds = new ArrayList<>();
        int pageNum = 1;
        while (true) {
            Object pageObj = getAvailableUsersForInternshipCore(
                    internshipId, jobCode, departmentIds, pageNum, LARGE_PAGE_SIZE, Sort.by(Sort.Direction.ASC, "id"), false);
            if (!(pageObj instanceof Page)) {
                throw BaseResponse.moreInfoError.error("查询可选用户结果异常");
            }
            Page<?> userPage = (Page<?>) pageObj;
            List<?> rows = userPage.getContent();
            if (rows == null || rows.isEmpty()) {
                break;
            }
            for (Object rowObj : rows) {
                JSONObject row = FastJsonUtil.toJson(rowObj);
                Integer userId = row.getInteger("id");
                if (userId != null) {
                    candidateUserIds.add(userId);
                }
            }
            if (pageNum >= userPage.getTotalPages()) {
                break;
            }
            pageNum++;
        }

        int createdRelIntershipUserCount = 0;
        int createdVerifyProcessCount = 0;
        for (Integer userId : candidateUserIds) {
            if (userId == null) {
                continue;
            }
            JSONObject relIntershipUserJson = new JSONObject();
            relIntershipUserJson.put("internshipId", internshipId);
            relIntershipUserJson.put("userId", userId);
            relIntershipUserJson.put("currentVerifyTypeId", verifyType);
            Object savedRelIntershipUser = iCommonService.saveOneRecord("RelIntershipUser", relIntershipUserJson);
            Integer relationId = FastJsonUtil.toJson(savedRelIntershipUser).getInteger("id");
            if (relationId == null) {
                continue;
            }
            createdRelIntershipUserCount++;

            JSONObject verifyJson = new JSONObject();
            verifyJson.put("relationId", relationId);
            verifyJson.put("processId", processId);
            verifyJson.put("createUserId", createUserId);
            verifyJson.put("verifyUserId", verifyUserId);
            verifyJson.put("isAudit", Constant.AUDIT_STATUS.SAVE);
            verifyJson.put("reason", "");
            verifyJson.put("tableName", "RelIntershipUser");
            iCommonService.saveOneRecord("MainVerifyProcess", verifyJson);
            createdVerifyProcessCount++;
        }

        JSONObject result = new JSONObject();
        result.put("createdRelIntershipUserCount", createdRelIntershipUserCount);
        result.put("createdVerifyProcessCount", createdVerifyProcessCount);
        result.put("totalCandidateCount", candidateUserIds.size());
        result.put("verifyUserId", verifyUserId);
        return result;
    }

    private static final String IMPORT_ROLE_STUDENT = "student";
    private static final String IMPORT_ROLE_TEACHER = "teacher";

    @Override
    public Object importRelIntershipUserByExcel(MultipartFile file, Integer internshipId,
                                                Integer processId, Integer createUserId, Integer verifyRoleId,
                                                Integer currentVerifyTypeId, String role) {
        if (file == null || file.isEmpty()) {
            throw BaseResponse.parameterInvalid.error("请上传 Excel 文件");
        }
        if (internshipId == null || processId == null || createUserId == null) {
            throw BaseResponse.parameterInvalid.error("internshipId、processId、createUserId 不能为空");
        }
        String normalizedRole = normalizeImportRole(role);
        int verifyType = (currentVerifyTypeId == null ? 1 : currentVerifyTypeId);
        if (verifyType <= 0) {
            throw BaseResponse.parameterInvalid.error("currentVerifyTypeId 无效，必须为正整数");
        }

        boolean teacherMode = IMPORT_ROLE_TEACHER.equals(normalizedRole);
        List<ExcelStudentNoRow> excelRows = parseRelIntershipUserExcelStudentNos(file);
        if (excelRows.isEmpty()) {
            throw BaseResponse.parameterInvalid.error(teacherMode
                    ? "Excel 中没有有效的工号数据"
                    : "Excel 中没有有效的学号数据");
        }

        String verifyUserId = iVerifyProcessService.GetVerifyUserId(verifyRoleId, createUserId, internshipId);
        if (verifyUserId == null) {
            verifyUserId = "";
        }

        int createdRelIntershipUserCount = 0;
        int createdVerifyProcessCount = 0;
        int skippedExistingCount = 0;
        JSONArray failures = new JSONArray();
        Set<String> seenWorkIds = new HashSet<>();
        String emptyLabel = teacherMode ? "工号为空" : "学号为空";
        String dupLabel = teacherMode ? "Excel 内工号重复" : "Excel 内学号重复";
        String notFoundLabel = teacherMode ? "未找到该工号对应用户" : "未找到该学号对应工号的用户";

        for (ExcelStudentNoRow excelRow : excelRows) {
            String workIdValue = excelRow.studentNo;
            int excelRowNum = excelRow.rowNum;
            if (workIdValue == null || workIdValue.isBlank()) {
                failures.add(buildImportFailure(excelRowNum, workIdValue, emptyLabel));
                continue;
            }
            String normalizedWorkId = workIdValue.trim();
            if (!seenWorkIds.add(normalizedWorkId)) {
                failures.add(buildImportFailure(excelRowNum, normalizedWorkId, dupLabel));
                continue;
            }

            JSONObject user = findUserByWorkId(normalizedWorkId);
            if (user == null) {
                failures.add(buildImportFailure(excelRowNum, normalizedWorkId, notFoundLabel));
                continue;
            }
            String jobCode = user.getString("jobCode");
            String identityError = validateImportUserIdentity(normalizedRole, jobCode);
            if (identityError != null) {
                failures.add(buildImportFailure(excelRowNum, normalizedWorkId, identityError));
                continue;
            }
            Integer userId = user.getInteger("id");
            if (userId == null) {
                failures.add(buildImportFailure(excelRowNum, normalizedWorkId, "用户 id 无效"));
                continue;
            }

            Integer existingRelId = findExistingRelIntershipUserId(internshipId, userId);
            if (existingRelId != null) {
                if (!hasRelIntershipUserVerifyProcess(existingRelId, processId)) {
                    createRelIntershipUserVerifyProcess(existingRelId, processId, createUserId, verifyUserId);
                    createdVerifyProcessCount++;
                }
                skippedExistingCount++;
                continue;
            }

            JSONObject relIntershipUserJson = new JSONObject();
            relIntershipUserJson.put("internshipId", internshipId);
            relIntershipUserJson.put("userId", userId);
            relIntershipUserJson.put("currentVerifyTypeId", verifyType);
            Object savedRelIntershipUser = iCommonService.saveOneRecord("RelIntershipUser", relIntershipUserJson);
            Integer relationId = FastJsonUtil.toJson(savedRelIntershipUser).getInteger("id");
            if (relationId == null) {
                failures.add(buildImportFailure(excelRowNum, normalizedWorkId, "创建 RelIntershipUser 失败"));
                continue;
            }
            createdRelIntershipUserCount++;
            createRelIntershipUserVerifyProcess(relationId, processId, createUserId, verifyUserId);
            createdVerifyProcessCount++;
        }

        JSONObject result = new JSONObject();
        result.put("role", normalizedRole);
        result.put("createdRelIntershipUserCount", createdRelIntershipUserCount);
        result.put("createdVerifyProcessCount", createdVerifyProcessCount);
        result.put("skippedExistingCount", skippedExistingCount);
        result.put("failedCount", failures.size());
        result.put("failures", failures);
        result.put("totalExcelRowCount", excelRows.size());
        result.put("verifyUserId", verifyUserId);
        return result;
    }

    private String normalizeImportRole(String role) {
        if (role == null || role.isBlank()) {
            return IMPORT_ROLE_STUDENT;
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        if (IMPORT_ROLE_STUDENT.equals(normalized) || IMPORT_ROLE_TEACHER.equals(normalized)) {
            return normalized;
        }
        throw BaseResponse.parameterInvalid.error("role 仅支持 student 或 teacher");
    }

    /**
     * @return 身份不符时的错误文案；通过返回 null
     */
    private String validateImportUserIdentity(String role, String jobCode) {
        if (IMPORT_ROLE_STUDENT.equals(role)) {
            if (jobCode != null && !jobCode.isBlank()
                    && !Constant.USER_JOB_CODE.STUDENT.equals(jobCode)) {
                return "该用户不是学生身份(jobCode=" + jobCode + ")";
            }
            return null;
        }
        // teacher：排除学生、企业导师
        if (Constant.USER_JOB_CODE.STUDENT.equals(jobCode)) {
            return "教师导入不允许学生身份";
        }
        if (Constant.USER_JOB_CODE.COMPANY_TUTOR.equals(jobCode)) {
            return "教师导入不允许企业导师身份";
        }
        return null;
    }

    @Override
    public void downloadRelIntershipUserImportTemplate(String role) {
        String normalizedRole = normalizeImportRole(role);
        boolean teacherMode = IMPORT_ROLE_TEACHER.equals(normalizedRole);
        ExcelWriter writer = null;
        try {
            List<List<Object>> rows = new ArrayList<>();
            if (teacherMode) {
                rows.add(Arrays.asList("工号", "姓名"));
                rows.add(Arrays.asList("T001", "李老师"));
            } else {
                rows.add(Arrays.asList("学号", "姓名"));
                rows.add(Arrays.asList("2401012307", "张三"));
            }
            writer = ExcelUtil.getWriter(true);
            writer.write(rows, false);
            writer.setColumnWidth(0, 20);
            writer.setColumnWidth(1, 16);

            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null || attributes.getResponse() == null) {
                throw BaseResponse.moreInfoError.error("无法获取响应对象");
            }
            HttpServletResponse response = attributes.getResponse();
            String rawName = teacherMode ? "教师实习项目安排导入模板" : "学生实习项目安排导入模板";
            String fileName = URLEncoder.encode(rawName, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8");
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName + ".xlsx");
            OutputStream outputStream = response.getOutputStream();
            writer.flush(outputStream, true);
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            throw BaseResponse.moreInfoError.error("下载模板失败: " + e.getMessage());
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    private static final class ExcelStudentNoRow {
        private final int rowNum;
        private final String studentNo;

        private ExcelStudentNoRow(int rowNum, String studentNo) {
            this.rowNum = rowNum;
            this.studentNo = studentNo;
        }
    }

    private JSONObject buildImportFailure(int rowNum, String studentNo, String reason) {
        JSONObject f = new JSONObject();
        f.put("row", rowNum);
        f.put("studentNo", studentNo);
        f.put("reason", reason);
        return f;
    }

    private List<ExcelStudentNoRow> parseRelIntershipUserExcelStudentNos(MultipartFile file) {
        try (java.io.InputStream in = file.getInputStream()) {
            ExcelReader reader = ExcelUtil.getReader(in);
            List<Map<String, Object>> maps = reader.readAll();
            List<ExcelStudentNoRow> out = new ArrayList<>();
            if (maps != null && !maps.isEmpty()) {
                int rowNum = 2; // 第 1 行表头，数据从第 2 行起
                for (Map<String, Object> map : maps) {
                    if (map == null || map.isEmpty()) {
                        rowNum++;
                        continue;
                    }
                    String studentNo = extractStudentNoFromExcelRow(map);
                    if (studentNo != null && !studentNo.isBlank()) {
                        out.add(new ExcelStudentNoRow(rowNum, studentNo.trim()));
                    } else if (!isExcelRowBlank(map)) {
                        out.add(new ExcelStudentNoRow(rowNum, null));
                    }
                    rowNum++;
                }
                return out;
            }
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            throw BaseResponse.moreInfoError.error("解析 Excel 失败: " + e.getMessage());
        }
        // 无表头映射时退回按列读取：第 0 列=学号
        try (java.io.InputStream in = file.getInputStream()) {
            ExcelReader reader = ExcelUtil.getReader(in);
            List<List<Object>> rows = reader.read(0);
            List<ExcelStudentNoRow> out = new ArrayList<>();
            if (rows == null || rows.size() <= 1) {
                return out;
            }
            for (int i = 1; i < rows.size(); i++) {
                List<Object> row = rows.get(i);
                if (row == null || row.isEmpty()) {
                    continue;
                }
                Object cell0 = row.get(0);
                String studentNo = normalizeExcelCellToStudentNo(cell0);
                if (studentNo != null && !studentNo.isEmpty()) {
                    out.add(new ExcelStudentNoRow(i + 1, studentNo));
                }
            }
            return out;
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            throw BaseResponse.moreInfoError.error("解析 Excel 失败: " + e.getMessage());
        }
    }

    private String extractStudentNoFromExcelRow(Map<String, Object> map) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            String key = e.getKey().trim();
            if ("学号".equals(key) || "工号".equals(key) || "workId".equalsIgnoreCase(key)
                    || "studentNo".equalsIgnoreCase(key)) {
                return normalizeExcelCellToStudentNo(e.getValue());
            }
        }
        // 未识别表头时取第一列
        for (Object v : map.values()) {
            String s = normalizeExcelCellToStudentNo(v);
            if (s != null && !s.isEmpty()) {
                return s;
            }
        }
        return null;
    }

    /** Excel 数字学号常被读成 2401.0 / 科学计数，统一转成纯字符串 */
    private String normalizeExcelCellToStudentNo(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return new BigDecimal(v.toString()).stripTrailingZeros().toPlainString();
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
            return null;
        }
        if (s.matches("^-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?$")) {
            try {
                return new BigDecimal(s).stripTrailingZeros().toPlainString();
            } catch (NumberFormatException ignored) {
                return s;
            }
        }
        return s;
    }

    private boolean isExcelRowBlank(Map<String, Object> map) {
        for (Object v : map.values()) {
            if (v == null) {
                continue;
            }
            String s = String.valueOf(v).trim();
            if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private JSONObject findUserByWorkId(String workId) {
        JSONObject sk = new JSONObject();
        sk.put("workId", workId);
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                "ViewBaseUser", sk, null, Sort.unsorted(), 1, 10);
        List<Object> content = page.getContent();
        if (content == null || content.isEmpty()) {
            return null;
        }
        return FastJsonUtil.toJson(content.get(0));
    }

    @SuppressWarnings("unchecked")
    private Integer findExistingRelIntershipUserId(Integer internshipId, Integer userId) {
        JSONObject sk = new JSONObject();
        sk.put("internshipId", internshipId);
        sk.put("userId", userId);
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                "RelIntershipUser", sk, null, Sort.unsorted(), 1, 1);
        List<Object> content = page.getContent();
        if (content == null || content.isEmpty()) {
            return null;
        }
        return FastJsonUtil.toJson(content.get(0)).getInteger("id");
    }

    @SuppressWarnings("unchecked")
    private boolean hasRelIntershipUserVerifyProcess(Integer relationId, Integer processId) {
        JSONObject sk = new JSONObject();
        sk.put("relationId", relationId);
        sk.put("processId", processId);
        sk.put("tableName", "RelIntershipUser");
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                "MainVerifyProcess", sk, null, Sort.unsorted(), 1, 1);
        return page.getContent() != null && !page.getContent().isEmpty();
    }

    private void createRelIntershipUserVerifyProcess(Integer relationId, Integer processId,
                                                     Integer createUserId, String verifyUserId) {
        JSONObject verifyJson = new JSONObject();
        verifyJson.put("relationId", relationId);
        verifyJson.put("processId", processId);
        verifyJson.put("createUserId", createUserId);
        verifyJson.put("verifyUserId", verifyUserId == null ? "" : verifyUserId);
        verifyJson.put("isAudit", Constant.AUDIT_STATUS.SAVE);
        verifyJson.put("reason", "");
        verifyJson.put("tableName", "RelIntershipUser");
        iCommonService.saveOneRecord("MainVerifyProcess", verifyJson);
    }

    @Override
    public Object listAssignableTeachers(Integer internshipId, Integer departmentId, String jobCode) {
        if (jobCode == null || jobCode.trim().isEmpty()) {
            throw BaseResponse.parameterInvalid.error("jobCode 不能为空");
        }
        String normalizedJobCode = jobCode.trim();
        if (!Constant.USER_JOB_CODE.SCHOOL_TEACHER.equals(normalizedJobCode)
                && !Constant.USER_JOB_CODE.COMPANY_TUTOR.equals(normalizedJobCode)) {
            throw BaseResponse.parameterInvalid.error("jobCode 仅支持 SCHOOL_TEACHER 或 COMPANY_TUTOR");
        }
        JSONObject teacherSearchKeys = new JSONObject();
        teacherSearchKeys.put("internshipId", internshipId);
        teacherSearchKeys.put("isAudit", Constant.AUDIT_STATUS.PASS);
        Map<String, String> regMap = null;
        // SCHOOL_TEACHER：所有非学生；COMPANY_TUTOR：仍按企业导师等值过滤
        if (Constant.USER_JOB_CODE.SCHOOL_TEACHER.equals(normalizedJobCode)) {
            teacherSearchKeys.put("jobCode", Constant.USER_JOB_CODE.STUDENT);
            regMap = new HashMap<>(1);
            regMap.put("jobCode", Constant.NE);
        } else {
            teacherSearchKeys.put("jobCode", normalizedJobCode);
        }
        @SuppressWarnings("unchecked")
        Page<Object> teacherPage = (Page<Object>) iCommonService.getSomeRecords(
                "ViewVerifyProcessRelIntershipUserMerge", teacherSearchKeys, regMap, Sort.unsorted(), 1, LARGE_PAGE_SIZE);

        JSONArray rows = new JSONArray();
        Set<Integer> seenUserIds = new HashSet<>();
        List<Object> content = teacherPage.getContent();
        if (content == null) {
            content = Collections.emptyList();
        }
        for (Object obj : content) {
            JSONObject j = FastJsonUtil.toJson(obj);
            Integer userId = j.getInteger("userId");
            Integer rowDeptId = j.getInteger("departmentId");
            if (userId == null || !seenUserIds.add(userId)) {
                continue;
            }
            if (departmentId != null && !Objects.equals(rowDeptId, departmentId)) {
                continue;
            }
            JSONObject row = new JSONObject();
            row.put("userId", userId);
            row.put("userName", firstNonEmpty(j.getString("userName"), j.getString("name")));
            row.put("departmentId", rowDeptId);
            row.put("jobCode", j.getString("jobCode"));
            row.put("jobName", j.getString("jobName"));
            row.put("phone", j.getString("phone"));
            row.put("account", j.getString("account"));
            row.put("relIntershipUserId", j.getInteger("relIntershipUserId"));
            row.put("currentVerifyTypeId", j.getInteger("currentVerifyTypeId"));
            row.put("isAudit", j.getInteger("isAudit"));
            rows.add(row);
        }
        JSONObject result = new JSONObject();
        result.put("rows", rows);
        result.put("total", rows.size());
        return result;
    }

    @Override
    public Object listAssignableStudents(Integer internshipId, Integer departmentId) {
        List<Object> relStuList;
        try {
            relStuList = getStudentInternshipSelections(internshipId);
        } catch (RuntimeException e) {
            JSONObject emptyResult = new JSONObject();
            emptyResult.put("rows", new JSONArray());
            emptyResult.put("total", 0);
            return emptyResult;
        }
        // 校内导师已写入 teacherId 的选岗不再出现；仅有空老师占位（或尚无占位）仍可分配
        Set<Integer> internalTutorAssignedRelIds = loadRelInternshipIdsWithInternalTutorAssigned(internshipId);
        Map<Integer, JSONObject> mergeSample = new LinkedHashMap<>();
        List<Integer> studentIds = new ArrayList<>();
        for (Object obj : relStuList) {
            JSONObject j = FastJsonUtil.toJson(obj);
            Integer userId = parseStudentUserIdFromStuPostMerge(j);
            Integer relInternshipId = j.getInteger("relationId");
            if (relInternshipId == null) {
                relInternshipId = j.getInteger("id");
            }
            Integer rowDeptId = j.getInteger("departmentId");
            if (userId == null || mergeSample.containsKey(userId)) {
                continue;
            }
            if (relInternshipId != null && internalTutorAssignedRelIds.contains(relInternshipId)) {
                continue;
            }
            if (departmentId != null && !Objects.equals(rowDeptId, departmentId)) {
                continue;
            }
            mergeSample.put(userId, j);
            studentIds.add(userId);
        }
        JSONObject result = new JSONObject();
        result.put("rows", buildStudentBriefList(studentIds, mergeSample));
        result.put("total", studentIds.size());
        return result;
    }

    @Override
    public Object manualAssignTeacherStudent(Integer internshipId, Integer processId, Integer createUserId,
                                             String verifyUserId, Integer currentVerifyTypeId, Integer teacherId,
                                             List<Integer> studentIds) {
        validateInitTeacherStudentParams(internshipId, processId, createUserId, verifyUserId);
        if (teacherId == null) {
            throw BaseResponse.parameterInvalid.error("teacherId 不能为空");
        }
        if (studentIds == null || studentIds.isEmpty()) {
            throw BaseResponse.parameterInvalid.error("studentIds 不能为空");
        }
        int verifyType = (currentVerifyTypeId == null ? 1 : currentVerifyTypeId);
        if (verifyType <= 0) {
            throw BaseResponse.parameterInvalid.error("currentVerifyTypeId 无效，必须为正整数");
        }

        List<Object> relStuList = getStudentInternshipSelections(internshipId);
        Map<Integer, Integer> studentRelInternshipMap = new HashMap<>();
        for (Object relStuObj : relStuList) {
            JSONObject relStuJson = FastJsonUtil.toJson(relStuObj);
            Integer studentId = parseStudentUserIdFromStuPostMerge(relStuJson);
            Integer relInternshipId = relStuJson.getInteger("relationId");
            if (relInternshipId == null) {
                relInternshipId = relStuJson.getInteger("id");
            }
            if (studentId != null && relInternshipId != null) {
                studentRelInternshipMap.put(studentId, relInternshipId);
            }
        }

        int createdRelTeacherStudentCount = 0;
        int createdVerifyProcessCount = 0;
        int updatedRelTeacherStudentCount = 0;
        int skippedSubmittedCount = 0;

        for (Integer studentId : studentIds) {
            if (studentId == null) {
                continue;
            }

            Integer relInternshipId = studentRelInternshipMap.get(studentId);
            if (relInternshipId == null) {
                throw BaseResponse.moreInfoError.error("studentId=" + studentId + " 未找到对应的通过学生选岗记录");
            }

            JSONObject existing = findTutorAssignmentByProcess(studentId, internshipId, relInternshipId, processId);
            if (existing != null) {
                Integer rtsId = existing.getInteger("relationId");
                Integer isAudit = existing.getInteger("isAudit");
                if (isAudit != null && isAudit != Constant.AUDIT_STATUS.SAVE) {
                    skippedSubmittedCount++;
                    logger.warn("手动分配跳过：studentId={} relInternshipId={} processId={} 已提交(isAudit={})",
                            studentId, relInternshipId, processId, isAudit);
                    continue;
                }
                JSONObject upd = new JSONObject();
                upd.put("id", rtsId);
                upd.put("teacherId", teacherId);
                upd.put("currentVerifyTypeId", verifyType);
                iCommonService.saveOneRecord(TABLE_REL_TEACHER_STUDENT, upd);
                updateMainVerifyProcessCreatorAndVerifier(rtsId, processId, createUserId, verifyUserId);
                ensureDiaryEntriesForAssignedStuPost(relInternshipId);
                updatedRelTeacherStudentCount++;
                continue;
            }

            JSONObject relTeacherStudentJson = new JSONObject();
            relTeacherStudentJson.put("stuId", studentId);
            relTeacherStudentJson.put("teacherId", teacherId);
            relTeacherStudentJson.put("currentVerifyTypeId", verifyType);
            relTeacherStudentJson.put("relInternshipId", relInternshipId);
            relTeacherStudentJson.put("internshipId", internshipId);
            Object savedRelTeacherStudent = iCommonService.saveOneRecord(TABLE_REL_TEACHER_STUDENT, relTeacherStudentJson);
            JSONObject savedRelTeacherStudentJson = FastJsonUtil.toJson(savedRelTeacherStudent);
            Integer relationId = savedRelTeacherStudentJson.getInteger("id");
            if (relationId == null) {
                continue;
            }
            createdRelTeacherStudentCount++;

            JSONObject verifyJson = new JSONObject();
            verifyJson.put("relationId", relationId);
            verifyJson.put("processId", processId);
            verifyJson.put("createUserId", createUserId);
            verifyJson.put("verifyUserId", verifyUserId);
            verifyJson.put("isAudit", Constant.AUDIT_STATUS.SAVE);
            verifyJson.put("reason", "");
            verifyJson.put("tableName", TABLE_REL_TEACHER_STUDENT);
            iCommonService.saveOneRecord(TABLE_MAIN_VERIFY_PROCESS, verifyJson);
            createdVerifyProcessCount++;
            ensureDiaryEntriesForAssignedStuPost(relInternshipId);
        }

        JSONObject result = buildInitTeacherStudentResult(createdRelTeacherStudentCount, createdVerifyProcessCount);
        result.put("updatedRelTeacherStudentCount", updatedRelTeacherStudentCount);
        result.put("skippedSubmittedCount", skippedSubmittedCount);
        return result;
    }

    @Override
    public Object importManualAssignTeacherStudentByExcel(MultipartFile file, Integer internshipId, Integer processId,
                                                          Integer createUserId, Integer verifyRoleId,
                                                          Integer currentVerifyTypeId) {
        if (file == null || file.isEmpty()) {
            throw BaseResponse.parameterInvalid.error("请上传 Excel 文件");
        }
        if (internshipId == null || processId == null || createUserId == null) {
            throw BaseResponse.parameterInvalid.error("internshipId、processId、createUserId 不能为空");
        }
        int verifyType = (currentVerifyTypeId == null ? Constant.VERIFY_LEVEL.NO_VERIFY : currentVerifyTypeId);
        if (verifyType <= 0) {
            throw BaseResponse.parameterInvalid.error("currentVerifyTypeId 无效，必须为正整数");
        }

        List<ExcelAssignRow> excelRows = parseManualAssignExcelRows(file);
        if (excelRows.isEmpty()) {
            throw BaseResponse.parameterInvalid.error("Excel 中没有有效的学号/教师工号数据");
        }

        // 预加载本项目选岗已通过的学生，避免分配时报错中断整批
        Set<Integer> selectableStudentIds = new HashSet<>();
        try {
            for (Object relStuObj : getStudentInternshipSelections(internshipId)) {
                Integer sid = parseStudentUserIdFromStuPostMerge(FastJsonUtil.toJson(relStuObj));
                if (sid != null) {
                    selectableStudentIds.add(sid);
                }
            }
        } catch (RuntimeException e) {
            // 无通过选岗记录时，后续逐行记失败
            selectableStudentIds = Collections.emptySet();
        }

        JSONArray failures = new JSONArray();
        // teacherId -> studentIds（按教师聚合后调用手动分配）
        Map<Integer, List<Integer>> teacherToStudents = new LinkedHashMap<>();
        Map<Integer, String> teacherWorkIdLabel = new HashMap<>();
        Set<String> seenStudentNos = new HashSet<>();
        int resolvedPairCount = 0;

        for (ExcelAssignRow row : excelRows) {
            int rowNum = row.rowNum;
            String studentNo = row.studentNo;
            String teacherWorkId = row.teacherWorkId;
            if (studentNo == null || studentNo.isBlank()) {
                failures.add(buildAssignImportFailure(rowNum, studentNo, teacherWorkId, "学号为空"));
                continue;
            }
            if (teacherWorkId == null || teacherWorkId.isBlank()) {
                failures.add(buildAssignImportFailure(rowNum, studentNo, teacherWorkId, "教师工号为空"));
                continue;
            }
            String normalizedStudentNo = studentNo.trim();
            String normalizedTeacherWorkId = teacherWorkId.trim();
            if (!seenStudentNos.add(normalizedStudentNo)) {
                failures.add(buildAssignImportFailure(rowNum, normalizedStudentNo, normalizedTeacherWorkId, "Excel 内学号重复"));
                continue;
            }

            JSONObject studentUser = findUserByWorkId(normalizedStudentNo);
            if (studentUser == null) {
                failures.add(buildAssignImportFailure(rowNum, normalizedStudentNo, normalizedTeacherWorkId,
                        "未找到该学号对应工号的学生用户"));
                continue;
            }
            String studentIdentityError = validateImportUserIdentity(IMPORT_ROLE_STUDENT, studentUser.getString("jobCode"));
            if (studentIdentityError != null) {
                failures.add(buildAssignImportFailure(rowNum, normalizedStudentNo, normalizedTeacherWorkId, studentIdentityError));
                continue;
            }
            Integer studentId = studentUser.getInteger("id");
            if (studentId == null) {
                failures.add(buildAssignImportFailure(rowNum, normalizedStudentNo, normalizedTeacherWorkId, "学生用户 id 无效"));
                continue;
            }
            if (!selectableStudentIds.contains(studentId)) {
                failures.add(buildAssignImportFailure(rowNum, normalizedStudentNo, normalizedTeacherWorkId,
                        "该学生无本项目选岗审核通过记录，无法分配导师"));
                continue;
            }

            JSONObject teacherUser = findUserByWorkId(normalizedTeacherWorkId);
            if (teacherUser == null) {
                failures.add(buildAssignImportFailure(rowNum, normalizedStudentNo, normalizedTeacherWorkId,
                        "未找到该教师工号对应用户"));
                continue;
            }
            String teacherIdentityError = validateImportUserIdentity(IMPORT_ROLE_TEACHER, teacherUser.getString("jobCode"));
            if (teacherIdentityError != null) {
                failures.add(buildAssignImportFailure(rowNum, normalizedStudentNo, normalizedTeacherWorkId, teacherIdentityError));
                continue;
            }
            Integer teacherId = teacherUser.getInteger("id");
            if (teacherId == null) {
                failures.add(buildAssignImportFailure(rowNum, normalizedStudentNo, normalizedTeacherWorkId, "教师用户 id 无效"));
                continue;
            }

            teacherToStudents.computeIfAbsent(teacherId, k -> new ArrayList<>()).add(studentId);
            teacherWorkIdLabel.put(teacherId, normalizedTeacherWorkId);
            resolvedPairCount++;
        }

        String verifyUserId = iVerifyProcessService.GetVerifyUserId(verifyRoleId, createUserId, internshipId);
        if (verifyUserId == null) {
            verifyUserId = "";
        }

        int createdRelTeacherStudentCount = 0;
        int createdVerifyProcessCount = 0;
        int updatedRelTeacherStudentCount = 0;
        int skippedSubmittedCount = 0;
        int assignedTeacherGroupCount = 0;

        for (Map.Entry<Integer, List<Integer>> entry : teacherToStudents.entrySet()) {
            Integer teacherId = entry.getKey();
            List<Integer> studentIds = entry.getValue();
            if (studentIds == null || studentIds.isEmpty()) {
                continue;
            }
            try {
                Object assignResult = manualAssignTeacherStudent(
                        internshipId, processId, createUserId, verifyUserId, verifyType, teacherId, studentIds);
                JSONObject ar = FastJsonUtil.toJson(assignResult);
                createdRelTeacherStudentCount += nullToZero(ar.getInteger("createdRelTeacherStudentCount"));
                createdVerifyProcessCount += nullToZero(ar.getInteger("createdVerifyProcessCount"));
                updatedRelTeacherStudentCount += nullToZero(ar.getInteger("updatedRelTeacherStudentCount"));
                skippedSubmittedCount += nullToZero(ar.getInteger("skippedSubmittedCount"));
                assignedTeacherGroupCount++;
            } catch (BaseException e) {
                String msg = e.getBaseResponse() != null ? e.getBaseResponse().getMessage() : e.toString();
                failures.add(buildAssignImportFailure(null, null, teacherWorkIdLabel.get(teacherId),
                        "教师工号=" + teacherWorkIdLabel.get(teacherId) + " 分配失败: " + msg));
            } catch (RuntimeException e) {
                failures.add(buildAssignImportFailure(null, null, teacherWorkIdLabel.get(teacherId),
                        "教师工号=" + teacherWorkIdLabel.get(teacherId) + " 分配失败: " + e.getMessage()));
            }
        }

        JSONObject result = new JSONObject();
        result.put("verifyUserId", verifyUserId);
        result.put("resolvedPairCount", resolvedPairCount);
        result.put("assignedTeacherGroupCount", assignedTeacherGroupCount);
        result.put("createdRelTeacherStudentCount", createdRelTeacherStudentCount);
        result.put("createdVerifyProcessCount", createdVerifyProcessCount);
        result.put("updatedRelTeacherStudentCount", updatedRelTeacherStudentCount);
        result.put("skippedSubmittedCount", skippedSubmittedCount);
        result.put("failedCount", failures.size());
        result.put("failures", failures);
        result.put("totalExcelRowCount", excelRows.size());
        return result;
    }

    @Override
    public void downloadManualAssignTeacherStudentImportTemplate() {
        ExcelWriter writer = null;
        try {
            List<List<Object>> rows = new ArrayList<>();
            rows.add(Arrays.asList("学号", "学生姓名", "教师工号", "老师姓名"));
            rows.add(Arrays.asList("2401012307", "张三", "T001", "李老师"));
            writer = ExcelUtil.getWriter(true);
            writer.write(rows, false);
            writer.setColumnWidth(0, 20);
            writer.setColumnWidth(1, 16);
            writer.setColumnWidth(2, 20);
            writer.setColumnWidth(3, 16);

            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null || attributes.getResponse() == null) {
                throw BaseResponse.moreInfoError.error("无法获取响应对象");
            }
            HttpServletResponse response = attributes.getResponse();
            String fileName = URLEncoder.encode("师生手动分配导入模板", StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8");
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName + ".xlsx");
            OutputStream outputStream = response.getOutputStream();
            writer.flush(outputStream, true);
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            throw BaseResponse.moreInfoError.error("下载模板失败: " + e.getMessage());
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    private static final class ExcelAssignRow {
        private final int rowNum;
        private final String studentNo;
        private final String teacherWorkId;

        private ExcelAssignRow(int rowNum, String studentNo, String teacherWorkId) {
            this.rowNum = rowNum;
            this.studentNo = studentNo;
            this.teacherWorkId = teacherWorkId;
        }
    }

    private JSONObject buildAssignImportFailure(Integer rowNum, String studentNo, String teacherWorkId, String reason) {
        JSONObject f = new JSONObject();
        if (rowNum != null) {
            f.put("row", rowNum);
        }
        f.put("studentNo", studentNo);
        f.put("teacherWorkId", teacherWorkId);
        f.put("reason", reason);
        return f;
    }

    private int nullToZero(Integer v) {
        return v == null ? 0 : v;
    }

    private List<ExcelAssignRow> parseManualAssignExcelRows(MultipartFile file) {
        try (java.io.InputStream in = file.getInputStream()) {
            ExcelReader reader = ExcelUtil.getReader(in);
            List<Map<String, Object>> maps = reader.readAll();
            List<ExcelAssignRow> out = new ArrayList<>();
            if (maps != null && !maps.isEmpty()) {
                int rowNum = 2;
                for (Map<String, Object> map : maps) {
                    if (map == null || map.isEmpty() || isExcelRowBlank(map)) {
                        rowNum++;
                        continue;
                    }
                    String studentNo = extractExcelColumn(map, "学号", "studentNo", "studentWorkId");
                    String teacherWorkId = extractExcelColumn(map, "教师工号", "老师工号", "teacherWorkId", "tutorWorkId");
                    // 若未识别「教师工号」，再尝试普通「工号」列（避免与学号冲突）
                    if (teacherWorkId == null || teacherWorkId.isBlank()) {
                        teacherWorkId = extractExcelColumn(map, "工号", "workId");
                    }
                    if ((studentNo == null || studentNo.isBlank()) && (teacherWorkId == null || teacherWorkId.isBlank())) {
                        rowNum++;
                        continue;
                    }
                    out.add(new ExcelAssignRow(rowNum, studentNo, teacherWorkId));
                    rowNum++;
                }
                return out;
            }
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            throw BaseResponse.moreInfoError.error("解析 Excel 失败: " + e.getMessage());
        }
        // 无表头映射：第0列学号，第1列学生姓名(忽略)，第2列教师工号，第3列老师姓名(忽略)
        try (java.io.InputStream in = file.getInputStream()) {
            ExcelReader reader = ExcelUtil.getReader(in);
            List<List<Object>> rows = reader.read(0);
            List<ExcelAssignRow> out = new ArrayList<>();
            if (rows == null || rows.size() <= 1) {
                return out;
            }
            for (int i = 1; i < rows.size(); i++) {
                List<Object> row = rows.get(i);
                if (row == null || row.isEmpty()) {
                    continue;
                }
                String studentNo = normalizeExcelCellToStudentNo(row.get(0));
                String teacherWorkId = row.size() > 2 ? normalizeExcelCellToStudentNo(row.get(2)) : null;
                // 兼容旧两列模板：第1列即教师工号
                if ((teacherWorkId == null || teacherWorkId.isBlank()) && row.size() == 2) {
                    teacherWorkId = normalizeExcelCellToStudentNo(row.get(1));
                }
                if ((studentNo == null || studentNo.isBlank()) && (teacherWorkId == null || teacherWorkId.isBlank())) {
                    continue;
                }
                out.add(new ExcelAssignRow(i + 1, studentNo, teacherWorkId));
            }
            return out;
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            throw BaseResponse.moreInfoError.error("解析 Excel 失败: " + e.getMessage());
        }
    }

    private String extractExcelColumn(Map<String, Object> map, String... aliases) {
        if (map == null || aliases == null) {
            return null;
        }
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            String key = e.getKey().trim();
            for (String alias : aliases) {
                if (alias != null && alias.equalsIgnoreCase(key)) {
                    return normalizeExcelCellToStudentNo(e.getValue());
                }
            }
        }
        return null;
    }

    /**
     * 收集校内导师合并视图中，该实习下已写入 teacherId 的选岗 id（{@code rel_internship_id}）。
     * 企业导师占位不参与本列表排除。
     */
    @SuppressWarnings("unchecked")
    private Set<Integer> loadRelInternshipIdsWithInternalTutorAssigned(Integer internshipId) {
        Set<Integer> assigned = new HashSet<>();
        JSONObject searchKeys = new JSONObject();
        searchKeys.put("internshipId", internshipId);
        applyExternalAssignInternalTutorMergeFilter(searchKeys);
        Page<Object> mergePage = (Page<Object>) iCommonService.getSomeRecords(
                VIEW_VERIFY_REL_INT_TEA_STU_MERGE, searchKeys, null, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
        List<Object> rows = mergePage.getContent();
        if (rows == null || rows.isEmpty()) {
            return assigned;
        }
        for (Object row : rows) {
            if (row == null) {
                continue;
            }
            JSONObject j = FastJsonUtil.toJson(row);
            Integer rid = j.getInteger("relInternshipId");
            if (rid != null && j.getInteger("teacherId") != null) {
                assigned.add(rid);
            }
        }
        return assigned;
    }

    /**
     * 按选岗 + 流程定位已有师生分配（同一选岗下校内/企业各一条 RelTeacherStudent，靠 processId 区分）。
     *
     * @return 含 relationId、isAudit；未找到返回 null
     */
    @SuppressWarnings("unchecked")
    private JSONObject findTutorAssignmentByProcess(Integer stuId, Integer internshipId,
            Integer relInternshipId, Integer processId) {
        if (stuId == null || internshipId == null || relInternshipId == null || processId == null) {
            return null;
        }
        JSONObject rtsSk = new JSONObject();
        rtsSk.put("internshipId", internshipId);
        rtsSk.put("relInternshipId", relInternshipId);
        rtsSk.put("stuId", stuId);
        Page<Object> rtsPage = (Page<Object>) iCommonService.getSomeRecords(
                TABLE_REL_TEACHER_STUDENT, rtsSk, null, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
        List<Object> rtsList = rtsPage.getContent();
        if (rtsList == null || rtsList.isEmpty()) {
            return null;
        }
        for (Object rtsObj : rtsList) {
            JSONObject rts = FastJsonUtil.toJson(rtsObj);
            Integer rtsId = rts.getInteger("id");
            if (rtsId == null) {
                continue;
            }
            JSONObject vpSk = new JSONObject();
            vpSk.put("relationId", rtsId);
            vpSk.put("processId", processId);
            vpSk.put("tableName", TABLE_REL_TEACHER_STUDENT);
            Page<Object> vpPage = (Page<Object>) iCommonService.getSomeRecords(
                    TABLE_MAIN_VERIFY_PROCESS, vpSk, null, Sort.by(Sort.Direction.DESC, "id"), 1, 1);
            List<Object> vpList = vpPage.getContent();
            if (vpList == null || vpList.isEmpty()) {
                continue;
            }
            JSONObject vp = FastJsonUtil.toJson(vpList.get(0));
            JSONObject found = new JSONObject();
            found.put("relationId", rtsId);
            found.put("isAudit", vp.getInteger("isAudit"));
            return found;
        }
        return null;
    }

    /**
     * 实习统计（校内/校外学院汇总）数据范围：校级管理员看全校；院系管理员固定本院系子树，忽略前端传入的 departmentId。
     */
    private static final class CollegeStatsUserScope {
        private final Integer reportDepartmentId;
        private final Set<Integer> studentUserIds;
        private final Set<Integer> schoolTeacherUserIds;

        private CollegeStatsUserScope(Integer reportDepartmentId, Set<Integer> studentUserIds, Set<Integer> schoolTeacherUserIds) {
            this.reportDepartmentId = reportDepartmentId;
            this.studentUserIds = studentUserIds;
            this.schoolTeacherUserIds = schoolTeacherUserIds;
        }
    }

    private ViewBaseUser requireLoginViewBaseUser() {
        Object uo = iCommonService.getOneRecordById("ViewBaseUser", getLoginUserId());
        if (uo == null) {
            throw BaseResponse.moreInfoError.error("用户信息不存在");
        }
        return (ViewBaseUser) uo;
    }

    private static boolean isSchoolWideCollegeStatsRole(String jobCode) {
        if (jobCode == null) {
            return false;
        }
        return Constant.USER_JOB_CODE.SUPER_ADMIN.equals(jobCode)
                || Constant.USER_JOB_CODE.SCHOOL_ADMIN.equals(jobCode)
                || Constant.USER_JOB_CODE.ACADEMIC_AFFAIRS_ADMIN.equals(jobCode);
    }

    private static boolean isDepartmentCollegeStatsRole(String jobCode) {
        return jobCode != null && Constant.USER_JOB_CODE.DEPARTMENT_ADMIN.equals(jobCode);
    }

    private void assertDepartmentAccessibleToStatsUser(Integer departmentId, ViewBaseUser u) {
        if (departmentId == null) {
            return;
        }
        if (Constant.USER_JOB_CODE.SUPER_ADMIN.equals(u.getJobCode())) {
            return;
        }
        Object dObj = iCommonService.getOneRecordById("ViewBaseDepartment", departmentId);
        if (dObj == null) {
            throw BaseResponse.parameterInvalid.error("部门不存在");
        }
        Integer deptSchoolId = FastJsonUtil.toJson(dObj).getInteger("schoolId");
        Integer userSchoolId = u.getSchoolId();
        if (userSchoolId == null || !userSchoolId.equals(deptSchoolId)) {
            throw BaseResponse.lackPermissions.error("仅能查看本校学院数据");
        }
    }

    @SuppressWarnings("unchecked")
    private List<Integer> loadAllDepartmentIdsPaged(String tableName, JSONObject searchKeys) {
        List<Integer> out = new ArrayList<>();
        int p = 1;
        while (true) {
            Page<Object> pg = (Page<Object>) iCommonService.getSomeRecords(
                    tableName, searchKeys, null, Sort.unsorted(), p, POST_PAGE_SIZE);
            List<Object> c = pg.getContent();
            if (c == null || c.isEmpty()) {
                break;
            }
            for (Object o : c) {
                Integer id = FastJsonUtil.toJson(o).getInteger("id");
                if (id != null) {
                    out.add(id);
                }
            }
            if (c.size() < POST_PAGE_SIZE) {
                break;
            }
            p++;
        }
        return out.stream().distinct().collect(Collectors.toList());
    }

    private List<Integer> loadAllViewDepartmentIdsForSchool(Integer schoolId) {
        JSONObject sk = new JSONObject();
        sk.put("schoolId", schoolId);
        return loadAllDepartmentIdsPaged("ViewBaseDepartment", sk);
    }

    private List<Integer> loadAllViewDepartmentIdsGlobal() {
        return loadAllDepartmentIdsPaged("ViewBaseDepartment", new JSONObject());
    }

    /**
     * 从统计查询范围内的部门 id 解析所属学院（{@code the_level=2}），用于限定可见的校内/校外实习项目。
     * 院系管理员即使挂在班级节点，也会沿父链上溯到学院再过滤。
     */
    @SuppressWarnings("unchecked")
    private List<Integer> resolveOwningCollegeIdsForStatsDeptScope(List<Integer> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Integer> collegeIds = new LinkedHashSet<>();
        for (Integer deptId : deptIds) {
            if (deptId == null || deptId <= 0) {
                continue;
            }
            List<Object> chain = (List<Object>) iDataTreeService.getAllParentIndex("BaseDepartment", deptId);
            if (chain == null || chain.isEmpty()) {
                continue;
            }
            for (Object node : chain) {
                JSONObject j = FastJsonUtil.toJson(node);
                Integer level = j.getInteger("theLevel");
                if (level != null && level == COLLEGE_DEPARTMENT_LEVEL) {
                    Integer collegeId = j.getInteger("id");
                    if (collegeId != null) {
                        collegeIds.add(collegeId);
                    }
                    break;
                }
            }
        }
        return new ArrayList<>(collegeIds);
    }

    /** 校内/校外实习统计：按当前登录人权限与 {@code requestDepartmentId} 解析可见的所属学院 id 列表。 */
    private List<Integer> resolveCollegeStatsOwningCollegeIds(Integer requestDepartmentId) {
        List<Integer> deptIds = resolveExternalCollegeStatsDepartmentIds(requestDepartmentId);
        return resolveOwningCollegeIdsForStatsDeptScope(deptIds);
    }

    /**
     * 查询指定学院下、指定校内外类型（{@code intTypeName}）的实习类型 id 列表。
     */
    @SuppressWarnings("unchecked")
    private List<Integer> loadInternshipTypeIdsForColleges(List<Integer> collegeIds, String intTypeName) {
        if (collegeIds == null || collegeIds.isEmpty() || intTypeName == null || intTypeName.isEmpty()) {
            return Collections.emptyList();
        }
        JSONObject sk = new JSONObject();
        sk.put("typeName", intTypeName);
        Map<String, String> repMap = new HashMap<>();
        GeneralUtil.addInCondition(sk, repMap, "universityId", collegeIds);
        List<Integer> out = new ArrayList<>();
        int p = 1;
        while (true) {
            Page<Object> pg = (Page<Object>) iCommonService.getSomeRecords(
                    "ViewBaseInternshipType", sk, repMap, Sort.unsorted(), p, POST_PAGE_SIZE);
            List<Object> c = pg.getContent();
            if (c == null || c.isEmpty()) {
                break;
            }
            for (Object o : c) {
                Integer id = FastJsonUtil.toJson(o).getInteger("id");
                if (id != null) {
                    out.add(id);
                }
            }
            if (c.size() < POST_PAGE_SIZE) {
                break;
            }
            p++;
        }
        return out.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 校验实习项目属于当前统计口径下可见的学院（{@code base_internship_type.university_id}）。
     */
    private void assertInternshipAccessibleInCollegeStats(Integer internshipId, Integer requestDepartmentId,
                                                          String expectedIntTypeName) {
        if (internshipId == null) {
            throw BaseResponse.parameterInvalid.error("internshipId 不能为空");
        }
        Object v = iCommonService.getOneRecordById("ViewMainInternship", internshipId);
        if (v == null) {
            throw BaseResponse.parameterInvalid.error("实习项目不存在");
        }
        JSONObject j = FastJsonUtil.toJson(v);
        String intTypeName = j.getString("intTypeName");
        if (intTypeName == null || !expectedIntTypeName.equals(intTypeName.trim())) {
            throw BaseResponse.parameterInvalid.error("仅支持" + expectedIntTypeName + "项目");
        }
        List<Integer> owningCollegeIds = resolveCollegeStatsOwningCollegeIds(requestDepartmentId);
        if (owningCollegeIds.isEmpty()) {
            throw BaseResponse.lackPermissions.error("无权查看该学院实习统计");
        }
        Integer internshipTypeId = j.getInteger("internshipTypeId");
        if (internshipTypeId == null) {
            throw BaseResponse.parameterInvalid.error("实习类型无效");
        }
        Object typeObj = iCommonService.getOneRecordById("ViewBaseInternshipType", internshipTypeId);
        if (typeObj == null) {
            throw BaseResponse.parameterInvalid.error("实习类型不存在");
        }
        Integer universityId = FastJsonUtil.toJson(typeObj).getInteger("universityId");
        if (universityId == null || !owningCollegeIds.contains(universityId)) {
            throw BaseResponse.lackPermissions.error("无权查看其他学院的实习项目");
        }
    }

    private List<Integer> resolveExternalCollegeStatsDepartmentIds(Integer requestDepartmentId) {
        ViewBaseUser u = requireLoginViewBaseUser();
        String jc = u.getJobCode();
        if (isDepartmentCollegeStatsRole(jc)) {
            Integer anchor = u.getDepartmentId();
            if (anchor == null || anchor <= 0) {
                throw BaseResponse.parameterInvalid.error("院系管理员未关联部门");
            }
            List<Integer> ids = iDataTreeService.getAllChildIndex("BaseDepartment", anchor);
            if (ids == null || ids.isEmpty()) {
                throw BaseResponse.parameterInvalid.error("部门不存在或无效");
            }
            return ids;
        }
        if (isSchoolWideCollegeStatsRole(jc)) {
            if (requestDepartmentId != null) {
                assertDepartmentAccessibleToStatsUser(requestDepartmentId, u);
                List<Integer> ids = iDataTreeService.getAllChildIndex("BaseDepartment", requestDepartmentId);
                if (ids == null || ids.isEmpty()) {
                    throw BaseResponse.parameterInvalid.error("部门不存在或无效");
                }
                return ids;
            }
            if (Constant.USER_JOB_CODE.SUPER_ADMIN.equals(jc)) {
                List<Integer> all = loadAllViewDepartmentIdsGlobal();
                if (all.isEmpty()) {
                    throw BaseResponse.parameterInvalid.error("无部门数据");
                }
                return all;
            }
            Integer sid = u.getSchoolId();
            if (sid == null || sid <= 0) {
                throw BaseResponse.parameterInvalid.error("无法确定所属学校，请传入 departmentId");
            }
            List<Integer> schoolDepts = loadAllViewDepartmentIdsForSchool(sid);
            if (schoolDepts.isEmpty()) {
                throw BaseResponse.parameterInvalid.error("本校无部门数据");
            }
            return schoolDepts;
        }
        throw BaseResponse.lackPermissions.error("无权查看实习统计");
    }

    private CollegeStatsUserScope resolveInternalCollegeStatsUserScope(Integer requestDepartmentId) {
        ViewBaseUser u = requireLoginViewBaseUser();
        String jc = u.getJobCode();
        if (isDepartmentCollegeStatsRole(jc)) {
            Integer anchor = u.getDepartmentId();
            if (anchor == null || anchor <= 0) {
                throw BaseResponse.parameterInvalid.error("院系管理员未关联部门");
            }
            return new CollegeStatsUserScope(
                    anchor,
                    loadUserIdsByDepartmentSubtreeAndJobCode(anchor, Constant.USER_JOB_CODE.STUDENT),
                    loadUserIdsByDepartmentSubtreeAndJobCode(anchor, Constant.USER_JOB_CODE.SCHOOL_TEACHER));
        }
        if (isSchoolWideCollegeStatsRole(jc)) {
            if (requestDepartmentId != null) {
                assertDepartmentAccessibleToStatsUser(requestDepartmentId, u);
                return new CollegeStatsUserScope(
                        requestDepartmentId,
                        loadUserIdsByDepartmentSubtreeAndJobCode(requestDepartmentId, Constant.USER_JOB_CODE.STUDENT),
                        loadUserIdsByDepartmentSubtreeAndJobCode(requestDepartmentId, Constant.USER_JOB_CODE.SCHOOL_TEACHER));
            }
            if (Constant.USER_JOB_CODE.SUPER_ADMIN.equals(jc)) {
                List<Integer> allDeptIds = loadAllViewDepartmentIdsGlobal();
                if (allDeptIds.isEmpty()) {
                    throw BaseResponse.parameterInvalid.error("无部门数据");
                }
                return new CollegeStatsUserScope(
                        null,
                        loadUserIdsByDepartmentIdsAndJobCode(allDeptIds, Constant.USER_JOB_CODE.STUDENT),
                        loadUserIdsByDepartmentIdsAndJobCode(allDeptIds, Constant.USER_JOB_CODE.SCHOOL_TEACHER));
            }
            Integer sid = u.getSchoolId();
            if (sid == null || sid <= 0) {
                throw BaseResponse.parameterInvalid.error("无法确定所属学校，请传入 departmentId");
            }
            List<Integer> schoolDeptIds = loadAllViewDepartmentIdsForSchool(sid);
            if (schoolDeptIds.isEmpty()) {
                throw BaseResponse.parameterInvalid.error("本校无部门数据");
            }
            return new CollegeStatsUserScope(
                    null,
                    loadUserIdsByDepartmentIdsAndJobCode(schoolDeptIds, Constant.USER_JOB_CODE.STUDENT),
                    loadUserIdsByDepartmentIdsAndJobCode(schoolDeptIds, Constant.USER_JOB_CODE.SCHOOL_TEACHER));
        }
        throw BaseResponse.lackPermissions.error("无权查看实习统计");
    }

    // ==================== 校外实习统计（学院汇总 / 岗位 / 学生选岗） ====================

    @Override
    public Object listExternalInternshipCollegeStats(Integer departmentId, Integer page, Integer size) {
        int pageNum = (page == null || page < 1) ? Constant.DEFAULT_PAGE : page;
        int pageSize = (size == null || size < 1) ? Constant.DEFAULT_SIZE : size;
        List<Integer> deptIds = resolveExternalCollegeStatsDepartmentIds(departmentId);
        List<Integer> owningCollegeIds = resolveOwningCollegeIdsForStatsDeptScope(deptIds);
        if (owningCollegeIds.isEmpty()) {
            JSONObject empty = new JSONObject();
            empty.put("rows", new JSONArray());
            empty.put("totalElements", 0L);
            empty.put("totalPages", 0);
            empty.put("page", pageNum);
            empty.put("size", pageSize);
            return empty;
        }
        Page<Object[]> statsPage = viewExternalInternshipCollegeStatsDao.findByOwningCollegeIds(
                owningCollegeIds, deptIds, PageRequest.of(pageNum - 1, pageSize));
        JSONArray rows = new JSONArray();
        for (Object[] r : statsPage.getContent()) {
            if (r == null || r.length < 10) {
                continue;
            }
            JSONObject row = new JSONObject();
            row.put("internshipId", toInteger(r[0]));
            row.put("internshipName", r[1] != null ? String.valueOf(r[1]) : null);
            row.put("signupStudentTotalCount", nzInt(toInteger(r[3])));
            row.put("signupStudentCount", nzInt(toInteger(r[4])));
            row.put("signupTeacherCount", nzInt(toInteger(r[5])));
            row.put("postSignupCount", nzInt(toInteger(r[6])));
            row.put("totalRecruitmentHeadcount", nzNumberInt(r[7]));
            row.put("pendingAuditPostCount", nzInt(toInteger(r[8])));
            row.put("studentWithPostSelectionCount", nzInt(toInteger(r[9])));
            rows.add(row);
        }
        JSONObject result = new JSONObject();
        result.put("rows", rows);
        result.put("totalElements", statsPage.getTotalElements());
        result.put("totalPages", statsPage.getTotalPages());
        result.put("page", pageNum);
        result.put("size", pageSize);
        return result;
    }

    private static Integer toInteger(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int nzNumberInt(Object o) {
        if (o == null) {
            return 0;
        }
        if (o instanceof BigDecimal) {
            return nzBigDecimalInt((BigDecimal) o);
        }
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        try {
            return new BigDecimal(String.valueOf(o)).intValue();
        } catch (Exception e) {
            return 0;
        }
    }

    private static int nzBigDecimalInt(BigDecimal v) {
        return v == null ? 0 : v.intValue();
    }

    private static int nzInt(Integer v) {
        return v == null ? 0 : v;
    }

    // ==================== 校内实习统计（学院汇总 / 学生选题 / 教师申报题目） ====================

    @Override
    public Object listInternalInternshipCollegeStats(Integer departmentId, Integer page, Integer size) {
        int pageNum = (page == null || page < 1) ? Constant.DEFAULT_PAGE : page;
        int pageSize = (size == null || size < 1) ? Constant.DEFAULT_SIZE : size;

        CollegeStatsUserScope scope = resolveInternalCollegeStatsUserScope(departmentId);
        Set<Integer> deptStudentIds = scope.studentUserIds;
        Set<Integer> deptSchoolTeacherIds = scope.schoolTeacherUserIds;
        Integer reportDepartmentId = scope.reportDepartmentId;

        List<Integer> owningCollegeIds = resolveCollegeStatsOwningCollegeIds(departmentId);
        List<Integer> allowedTypeIds = loadInternshipTypeIdsForColleges(owningCollegeIds, INTERNAL_INT_TYPE_NAME);
        if (allowedTypeIds.isEmpty()) {
            JSONObject empty = new JSONObject();
            empty.put("departmentId", reportDepartmentId);
            empty.put("rows", new JSONArray());
            empty.put("totalElements", 0L);
            empty.put("totalPages", 0);
            empty.put("page", pageNum);
            empty.put("size", pageSize);
            return empty;
        }

        JSONObject internshipSk = new JSONObject();
        internshipSk.put("intTypeName", INTERNAL_INT_TYPE_NAME);
        Map<String, String> internshipRepMap = new HashMap<>();
        GeneralUtil.addInCondition(internshipSk, internshipRepMap, "internshipTypeId", allowedTypeIds);
        Sort newestFirst = Sort.by(Sort.Direction.DESC, "createTime");
        @SuppressWarnings("unchecked")
        Page<Object> internshipPage = (Page<Object>) iCommonService.getSomeRecords(
                "ViewMainInternship", internshipSk, internshipRepMap, newestFirst, pageNum, pageSize);
        List<Object> internshipList = internshipPage.getContent();
        if (internshipList == null || internshipList.isEmpty()) {
            JSONObject empty = new JSONObject();
            empty.put("departmentId", reportDepartmentId);
            empty.put("rows", new JSONArray());
            empty.put("totalElements", internshipPage.getTotalElements());
            empty.put("totalPages", internshipPage.getTotalPages());
            empty.put("page", pageNum);
            empty.put("size", pageSize);
            return empty;
        }

        JSONArray rows = new JSONArray();
        for (Object inv : internshipList) {
            JSONObject invJson = FastJsonUtil.toJson(inv);
            Integer internshipId = invJson.getInteger("id");
            if (internshipId == null) {
                continue;
            }
            String internshipName = invJson.getString("name");

            int signupStudentCount = countDistinctMergeUsersInSet(internshipId, Constant.USER_JOB_CODE.STUDENT, deptStudentIds);
            int signupTeacherCount = countDistinctMergeUsersInSet(internshipId, Constant.USER_JOB_CODE.SCHOOL_TEACHER, deptSchoolTeacherIds);

            List<Object> titleTeacherMerges = fetchAllTitleTeacherMergeByInternship(internshipId);
            int titleApprovedCount = countDistinctFullyApprovedTitleRelations(titleTeacherMerges);
            Set<Integer> teachersSubmittedTopic = collectTeacherIdsWithNonSaveTopicAudit(titleTeacherMerges);

            Set<Integer> deptTeachersEnrolled = mergeProjectUserIdsInAllowed(
                    internshipId, Constant.USER_JOB_CODE.SCHOOL_TEACHER, deptSchoolTeacherIds);
            int teachersNotSubmittedTopicCount = (int) deptTeachersEnrolled.stream()
                    .filter(t -> !teachersSubmittedTopic.contains(t))
                    .count();

            Set<Integer> deptStudentsEnrolled = mergeProjectUserIdsInAllowed(
                    internshipId, Constant.USER_JOB_CODE.STUDENT, deptStudentIds);
            Map<Integer, JSONObject> titleStuBest = buildStudentTitleBestMergeMap(internshipId);
            int studentsTitleApprovedCount = 0;
            int studentsTitlePendingCount = 0;
            int studentsNotSelectedTitleCount = 0;
            for (Integer sid : deptStudentsEnrolled) {
                String st = resolveInternalTitleSelectionStatusFromSample(titleStuBest.get(sid));
                if (TITLE_SEL_STATUS_APPROVED.equals(st)) {
                    studentsTitleApprovedCount++;
                } else if (TITLE_SEL_STATUS_PENDING.equals(st)) {
                    studentsTitlePendingCount++;
                } else {
                    studentsNotSelectedTitleCount++;
                }
            }

            JSONObject row = new JSONObject();
            row.put("internshipId", internshipId);
            row.put("internshipName", internshipName);
            row.put("signupStudentCount", signupStudentCount);
            row.put("signupTeacherCount", signupTeacherCount);
            row.put("titleApprovedCount", titleApprovedCount);
            row.put("teachersNotSubmittedTopicCount", teachersNotSubmittedTopicCount);
            row.put("studentsTitleApprovedCount", studentsTitleApprovedCount);
            row.put("studentsTitlePendingCount", studentsTitlePendingCount);
            row.put("studentsNotSelectedTitleCount", studentsNotSelectedTitleCount);
            rows.add(row);
        }

        JSONObject result = new JSONObject();
        result.put("departmentId", reportDepartmentId);
        result.put("rows", rows);
        result.put("totalElements", internshipPage.getTotalElements());
        result.put("totalPages", internshipPage.getTotalPages());
        result.put("page", pageNum);
        result.put("size", pageSize);
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object getInternalInternshipTitleSelectionBreakdown(Integer internshipId, Integer page, Integer size, String status,
                                                               Integer departmentId) {
        assertInternalInternship(internshipId);
        assertInternshipAccessibleInCollegeStats(internshipId, departmentId, INTERNAL_INT_TYPE_NAME);
        String st = normalizeTitleSelectionBreakdownStatus(status);
        int pageNum = (page == null || page < 1) ? Constant.DEFAULT_PAGE : page;
        int pageSize = (size == null || size < 1) ? Constant.DEFAULT_SIZE : size;

        CollegeStatsUserScope statsScope = resolveInternalCollegeStatsUserScope(departmentId);
        Set<Integer> projectStudentUserIds = mergeProjectUserIdsInAllowed(
                internshipId, Constant.USER_JOB_CODE.STUDENT, statsScope.studentUserIds);
        List<Object> mergeAll = fetchAllTitleStudentMergeByInternship(internshipId);

        Map<Integer, JSONObject> stuToBestMerge = new HashMap<>();
        for (Object o : mergeAll) {
            JSONObject j = FastJsonUtil.toJson(o);
            Integer stuId = j.getInteger("stuId");
            if (stuId == null || !projectStudentUserIds.contains(stuId)) {
                continue;
            }
            JSONObject prev = stuToBestMerge.get(stuId);
            stuToBestMerge.put(stuId, prev == null ? j : pickBetterTitleMergeRow(prev, j));
        }

        Map<Integer, JSONObject> mergeForDisplay = new HashMap<>(stuToBestMerge);

        Set<Integer> approvedSet = new HashSet<>();
        Set<Integer> pendingSet = new HashSet<>();
        Set<Integer> notSubmittedSet = new HashSet<>();
        for (Integer uid : projectStudentUserIds) {
            JSONObject best = stuToBestMerge.get(uid);
            String sel = resolveInternalTitleSelectionStatusFromSample(best);
            if (TITLE_SEL_STATUS_APPROVED.equals(sel)) {
                approvedSet.add(uid);
            } else if (TITLE_SEL_STATUS_PENDING.equals(sel)) {
                pendingSet.add(uid);
            } else {
                notSubmittedSet.add(uid);
            }
        }

        JSONObject counts = new JSONObject();
        counts.put(TITLE_SEL_STATUS_NOT_SUBMITTED, notSubmittedSet.size());
        counts.put(TITLE_SEL_STATUS_PENDING, pendingSet.size());
        counts.put(TITLE_SEL_STATUS_APPROVED, approvedSet.size());

        Object invObj = iCommonService.getOneRecordById("ViewMainInternship", internshipId);
        JSONObject invJ = invObj != null ? FastJsonUtil.toJson(invObj) : new JSONObject();

        if (TITLE_SEL_STATUS_ALL.equals(st)) {
            List<Integer> allOrdered = projectStudentUserIds.stream().sorted().collect(Collectors.toList());
            int total = allOrdered.size();
            int totalPages = pageSize <= 0 ? 0 : (int) Math.ceil((double) total / (double) pageSize);
            int from = Math.max(0, (pageNum - 1) * pageSize);
            int to = Math.min(from + pageSize, total);
            List<Integer> slice = from >= total ? Collections.emptyList() : allOrdered.subList(from, to);
            JSONArray rows = new JSONArray();
            for (Integer uid : slice) {
                JSONObject row = buildOneInternalTitleRow(uid, mergeForDisplay.get(uid));
                row.put("selectionStatus", resolveInternalTitleSelectionStatusFromSample(mergeForDisplay.get(uid)));
                rows.add(row);
            }
            JSONObject result = new JSONObject();
            result.put("internshipId", internshipId);
            result.put("internshipName", invJ.getString("name"));
            result.put("status", TITLE_SEL_STATUS_ALL);
            result.put("page", pageNum);
            result.put("size", pageSize);
            result.put("counts", counts);
            result.put("totalElements", total);
            result.put("totalPages", totalPages);
            result.put("rows", rows);
            result.put("departmentId", statsScope.reportDepartmentId);
            return result;
        }

        List<Integer> targetList;
        Map<Integer, JSONObject> mergeForRows;
        if (TITLE_SEL_STATUS_NOT_SUBMITTED.equals(st)) {
            targetList = notSubmittedSet.stream().sorted().collect(Collectors.toList());
            mergeForRows = mergeForDisplay;
        } else if (TITLE_SEL_STATUS_PENDING.equals(st)) {
            targetList = pendingSet.stream().sorted().collect(Collectors.toList());
            mergeForRows = mergeForDisplay;
        } else {
            targetList = approvedSet.stream().sorted().collect(Collectors.toList());
            mergeForRows = mergeForDisplay;
        }

        JSONObject paged = buildPagedInternalTitleCategory(targetList, mergeForRows, pageNum, pageSize);
        JSONObject result = new JSONObject();
        result.put("internshipId", internshipId);
        result.put("internshipName", invJ.getString("name"));
        result.put("status", st);
        result.put("page", paged.getInteger("page"));
        result.put("size", paged.getInteger("size"));
        result.put("counts", counts);
        result.put("totalElements", paged.getInteger("totalElements"));
        result.put("totalPages", paged.getInteger("totalPages"));
        result.put("rows", paged.get("rows"));
        result.put("departmentId", statsScope.reportDepartmentId);
        return result;
    }

    @Override
    public Object listInternalInternshipTeachersNotSubmittedTopic(Integer internshipId, Integer departmentId, Integer page, Integer size) {
        assertInternalInternship(internshipId);
        assertInternshipAccessibleInCollegeStats(internshipId, departmentId, INTERNAL_INT_TYPE_NAME);
        int pageNum = (page == null || page < 1) ? Constant.DEFAULT_PAGE : page;
        int pageSize = (size == null || size < 1) ? Constant.DEFAULT_SIZE : size;

        CollegeStatsUserScope statsScope = resolveInternalCollegeStatsUserScope(departmentId);
        Set<Integer> enrolledTeachers = mergeProjectUserIdsInAllowed(
                internshipId, Constant.USER_JOB_CODE.SCHOOL_TEACHER, statsScope.schoolTeacherUserIds);

        List<Object> titleMerges = fetchAllTitleTeacherMergeByInternship(internshipId);
        Set<Integer> submitted = collectTeacherIdsWithNonSaveTopicAudit(titleMerges);

        List<Integer> notSubmittedIds = enrolledTeachers.stream()
                .filter(t -> !submitted.contains(t))
                .sorted()
                .collect(Collectors.toList());

        int total = notSubmittedIds.size();
        int totalPages = pageSize <= 0 ? 0 : (int) Math.ceil((double) total / (double) pageSize);
        int from = Math.max(0, (pageNum - 1) * pageSize);
        int to = Math.min(from + pageSize, total);
        List<Integer> slice = from >= total ? Collections.emptyList() : notSubmittedIds.subList(from, to);

        JSONArray rows = new JSONArray();
        for (Integer tid : slice) {
            rows.add(buildTeacherNotSubmittedTopicRow(tid));
        }

        Object invObj = iCommonService.getOneRecordById("ViewMainInternship", internshipId);
        JSONObject invJ = invObj != null ? FastJsonUtil.toJson(invObj) : new JSONObject();

        JSONObject result = new JSONObject();
        result.put("internshipId", internshipId);
        result.put("internshipName", invJ.getString("name"));
        result.put("departmentId", statsScope.reportDepartmentId);
        result.put("notSubmittedCount", total);
        result.put("page", pageNum);
        result.put("size", pageSize);
        result.put("totalElements", total);
        result.put("totalPages", totalPages);
        result.put("rows", rows);
        return result;
    }

    @Override
    public Object listApprovedExternalInternshipPosts(Integer internshipId, Integer page, Integer size) {
        assertExternalInternship(internshipId);
        int pageNum = (page == null || page < 1) ? Constant.DEFAULT_PAGE : page;
        int pageSize = (size == null || size < 1) ? Constant.DEFAULT_SIZE : size;

        List<Object> list = fetchAllPagesInternshipPostMergePass(internshipId);
        List<JSONObject> allItems = new ArrayList<>();
        if (list != null) {
            Set<Integer> seenPostIds = new HashSet<>();
            for (Object o : list) {
                JSONObject j = FastJsonUtil.toJson(o);
                Integer postId = parseInternshipPostIdFromInternshipPostMergeRow(j);
                if (postId == null || seenPostIds.contains(postId)) {
                    continue;
                }
                seenPostIds.add(postId);
                JSONObject item = new JSONObject();
                item.put("internshipPostId", postId);
                item.put("internshipPostName", firstNonEmpty(j.getString("internshipPostName"), j.getString("name")));
                item.put("companyName", j.getString("companyName"));
                item.put("companyId", j.getInteger("companyId"));
                item.put("allPersonNum", j.getInteger("allPersonNum"));
                item.put("nowPersonNum", j.getInteger("nowPersonNum"));
                item.put("internshipPostCode", j.getString("internshipPostCode"));
                item.put("processTypeCode", j.getString("processTypeCode"));
                Object postEntity = iCommonService.getOneRecordById("MainInternshipPost", postId);
                if (postEntity != null) {
                    JSONObject pe = FastJsonUtil.toJson(postEntity);
                    item.put("postTypeId", pe.getInteger("postTypeId"));
                    item.put("remarks", pe.getString("remarks"));
                    Integer postTypeId = pe.getInteger("postTypeId");
                    if (postTypeId != null) {
                        Object postTypeObj = iCommonService.getOneRecordById("BasePostType", postTypeId);
                        if (postTypeObj != null) {
                            item.put("salary", FastJsonUtil.toJson(postTypeObj).getInteger("salary"));
                        }
                    }
                }
                allItems.add(item);
            }
        }

        int total = allItems.size();
        int totalPages = pageSize <= 0 ? 0 : (int) Math.ceil((double) total / (double) pageSize);
        int from = Math.max(0, (pageNum - 1) * pageSize);
        int to = Math.min(from + pageSize, total);
        JSONArray posts = new JSONArray();
        if (from < total) {
            for (int i = from; i < to; i++) {
                posts.add(allItems.get(i));
            }
        }

        JSONObject out = new JSONObject();
        out.put("internshipId", internshipId);
        out.put("posts", posts);
        out.put("page", pageNum);
        out.put("size", pageSize);
        out.put("totalElements", total);
        out.put("totalPages", totalPages);
        return out;
    }

    @Override
    public Object randomAssignPostsForUnselectedStudents(Integer internshipId) {
        assertExternalInternship(internshipId);
        List<Integer> studentIds = collectNotSelectedStudentUserIds(internshipId);
        List<Integer> postIds = collectApprovedAssignablePostIds(internshipId);

        JSONObject result = new JSONObject();
        result.put("internshipId", internshipId);
        result.put("candidateStudentCount", studentIds.size());
        result.put("candidatePostCount", postIds.size());

        if (postIds.isEmpty()) {
            throw BaseResponse.moreInfoError.error("未找到可分配的企业岗位（需岗位审核通过且未满员，不含自主实习岗位）");
        }
        if (studentIds.isEmpty()) {
            result.put("assignedCount", 0);
            result.put("failedCount", 0);
            result.put("unassignedCount", 0);
            result.put("details", new JSONArray());
            return result;
        }

        List<Integer> shuffled = new ArrayList<>(studentIds);
        Collections.shuffle(shuffled);

        int assignedCount = 0;
        int failedCount = 0;
        int unassignedCount = 0;
        JSONArray details = new JSONArray();

        for (Integer studentId : shuffled) {
            List<Integer> eligiblePostIds = filterPostIdsWithRemainingCapacity(postIds);
            if (eligiblePostIds.isEmpty()) {
                unassignedCount++;
                JSONObject item = new JSONObject();
                item.put("studentId", studentId);
                item.put("success", false);
                item.put("message", "所有岗位已满，无法继续分配");
                details.add(item);
                continue;
            }
            int postId = eligiblePostIds.get(ThreadLocalRandom.current().nextInt(eligiblePostIds.size()));
            try {
                Object selResult = iInternshipPostService.stuSelPost(studentId, 0, postId);
                assignedCount++;
                JSONObject item = new JSONObject();
                item.put("studentId", studentId);
                item.put("internshipPostId", postId);
                item.put("success", true);
                if (selResult instanceof JSONObject) {
                    item.put("selection", selResult);
                }
                details.add(item);
            } catch (RuntimeException ex) {
                failedCount++;
                JSONObject item = new JSONObject();
                item.put("studentId", studentId);
                item.put("internshipPostId", postId);
                item.put("success", false);
                item.put("message", ex.getMessage());
                details.add(item);
                logger.warn("随机分配岗位失败 internshipId={} studentId={} postId={}: {}",
                        internshipId, studentId, postId, ex.getMessage());
            }
        }

        result.put("assignedCount", assignedCount);
        result.put("failedCount", failedCount);
        result.put("unassignedCount", unassignedCount);
        result.put("details", details);
        return result;
    }

    /**
     * 与 {@link #getExternalInternshipStudentPostBreakdown} 中 status=notSelected 口径一致：已入项学生且无选岗记录。
     */
    private List<Integer> collectNotSelectedStudentUserIds(Integer internshipId) {
        Set<Integer> projectStudentUserIds = loadInternshipProjectStudentUserIds(internshipId);
        Set<Integer> anyStuPostUser = loadStudentUserIdsWithAnyPostSelection(internshipId);
        return projectStudentUserIds.stream()
                .filter(u -> !anyStuPostUser.contains(u))
                .sorted()
                .collect(Collectors.toList());
    }

    private Set<Integer> loadStudentUserIdsWithAnyPostSelection(Integer internshipId) {
        JSONObject stuSk = new JSONObject();
        stuSk.put("internshipId", internshipId);
        @SuppressWarnings("unchecked")
        Page<Object> mergePage = (Page<Object>) iCommonService.getSomeRecords(
                "ViewVerifyProcessRelStuInternshipPostMerge", stuSk, null, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
        List<Object> mergeList = mergePage.getContent() == null ? Collections.emptyList() : mergePage.getContent();
        Set<Integer> anyStuPostUser = new HashSet<>();
        for (Object o : mergeList) {
            Integer uid = parseStudentUserIdFromStuPostMerge(FastJsonUtil.toJson(o));
            if (uid != null) {
                anyStuPostUser.add(uid);
            }
        }
        return anyStuPostUser;
    }

    /**
     * 与 {@link #listApprovedExternalInternshipPosts} 岗位池一致：审核通过、去重，排除自主实习与已满员岗位。
     */
    private List<Integer> collectApprovedAssignablePostIds(Integer internshipId) {
        List<Object> list = fetchAllPagesInternshipPostMergePass(internshipId);
        List<Integer> postIds = new ArrayList<>();
        Set<Integer> seenPostIds = new HashSet<>();
        if (list == null) {
            return postIds;
        }
        for (Object o : list) {
            JSONObject j = FastJsonUtil.toJson(o);
            Integer postId = parseInternshipPostIdFromInternshipPostMergeRow(j);
            if (postId == null || !seenPostIds.add(postId)) {
                continue;
            }
            MainInternshipPost post = mainInternshipPostDao.getByIdAndIsDeletedFalse(postId);
            if (post == null || SELF_INTERNSHIP_POST_CODE.equals(post.getCode())) {
                continue;
            }
            if (!internshipPostHasRemainingCapacity(post)) {
                continue;
            }
            postIds.add(postId);
        }
        return postIds;
    }

    private List<Integer> filterPostIdsWithRemainingCapacity(List<Integer> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyList();
        }
        return postIds.stream()
                .filter(pid -> {
                    MainInternshipPost post = mainInternshipPostDao.getByIdAndIsDeletedFalse(pid);
                    return post != null && internshipPostHasRemainingCapacity(post);
                })
                .collect(Collectors.toList());
    }

    private boolean internshipPostHasRemainingCapacity(MainInternshipPost post) {
        if (post == null) {
            return false;
        }
        Integer all = post.getAllPersonNum();
        if (all == null || all <= 0 || all == SELF_INTERNSHIP_UNLIMITED) {
            return true;
        }
        int now = post.getNowPersonNum() != null ? post.getNowPersonNum() : 0;
        return now < all;
    }

    /**
     * 拉取某项目下岗位合并视图中 isAudit=PASS 的全部行（分页拼接），避免仅取第一页导致去重后 total 偏少。
     */
    @SuppressWarnings("unchecked")
    private List<Object> fetchAllPagesInternshipPostMergePass(Integer internshipId) {
        JSONObject sk = new JSONObject();
        sk.put("internshipId", internshipId);
        sk.put("isAudit", Constant.AUDIT_STATUS.PASS);
        List<Object> all = new ArrayList<>();
        int p = 1;
        while (true) {
            Page<Object> dbPage = (Page<Object>) iCommonService.getSomeRecords(
                    "ViewVerifyProcessInternshipPostMerge", sk, null, Sort.unsorted(), p, POST_PAGE_SIZE);
            List<Object> content = dbPage.getContent();
            if (content == null || content.isEmpty()) {
                break;
            }
            all.addAll(content);
            if (content.size() < POST_PAGE_SIZE) {
                break;
            }
            p++;
        }
        return all;
    }

    @Override
    public Object getExternalInternshipStudentPostBreakdown(Integer internshipId, Integer page, Integer size, String status,
                                                            Integer departmentId) {
        assertExternalInternship(internshipId);
        assertInternshipAccessibleInCollegeStats(internshipId, departmentId, EXTERNAL_INT_TYPE_NAME);
        CollegeStatsUserScope scope = resolveInternalCollegeStatsUserScope(departmentId);
        Set<Integer> deptStudentUserIds = scope.studentUserIds;
        String st = normalizeStudentPostBreakdownStatus(status);
        int pageNum = (page == null || page < 1) ? Constant.DEFAULT_PAGE : page;
        int pageSize = (size == null || size < 1) ? Constant.DEFAULT_SIZE : size;

        JSONObject counts = new JSONObject();
        counts.put(STU_POST_STATUS_NOT_SELECTED, 0);
        counts.put(STU_POST_STATUS_SELECTED, 0);
        counts.put(STU_POST_STATUS_SELECTED_PENDING, 0);
        counts.put(STU_POST_STATUS_POST_APPROVED, 0);

        if (deptStudentUserIds == null || deptStudentUserIds.isEmpty()) {
            return buildStudentPostBreakdownResult(internshipId, null, scope.reportDepartmentId, st,
                    pageNum, pageSize, 0, 0, counts, new JSONArray());
        }

        for (Object[] row : viewExternalInternshipStudentPostBreakdownDao
                .countGroupBySelectionStatus(internshipId, deptStudentUserIds)) {
            String sel = row[0] != null ? String.valueOf(row[0]) : null;
            long cnt = row[1] instanceof Number ? ((Number) row[1]).longValue() : 0L;
            if (STU_POST_STATUS_NOT_SELECTED.equals(sel)) {
                counts.put(STU_POST_STATUS_NOT_SELECTED, (int) cnt);
            } else if (STU_POST_STATUS_SELECTED_PENDING.equals(sel)) {
                counts.put(STU_POST_STATUS_SELECTED_PENDING, (int) cnt);
            } else if (STU_POST_STATUS_POST_APPROVED.equals(sel)) {
                counts.put(STU_POST_STATUS_POST_APPROVED, (int) cnt);
            }
        }
        int selectedCount = counts.getIntValue(STU_POST_STATUS_SELECTED_PENDING)
                + counts.getIntValue(STU_POST_STATUS_POST_APPROVED);
        counts.put(STU_POST_STATUS_SELECTED, selectedCount);

        PageRequest pageable = PageRequest.of(pageNum - 1, pageSize);
        Page<ViewExternalInternshipStudentPostBreakdown> paged;
        if (STU_POST_STATUS_ALL.equals(st)) {
            paged = viewExternalInternshipStudentPostBreakdownDao
                    .findAllByInternshipAndUsers(internshipId, deptStudentUserIds, pageable);
        } else if (STU_POST_STATUS_SELECTED.equals(st)) {
            paged = viewExternalInternshipStudentPostBreakdownDao.findByInternshipUsersAndStatusIn(
                    internshipId, deptStudentUserIds,
                    List.of(STU_POST_STATUS_SELECTED_PENDING, STU_POST_STATUS_POST_APPROVED),
                    pageable);
        } else {
            paged = viewExternalInternshipStudentPostBreakdownDao.findByInternshipUsersAndStatus(
                    internshipId, deptStudentUserIds, st, pageable);
        }

        String internshipName = null;
        JSONArray rows = new JSONArray();
        for (ViewExternalInternshipStudentPostBreakdown v : paged.getContent()) {
            if (internshipName == null) {
                internshipName = v.getInternshipName();
            }
            rows.add(toStudentPostBreakdownRow(v));
        }
        if (internshipName == null) {
            Object invObj = iCommonService.getOneRecordById("ViewMainInternship", internshipId);
            if (invObj != null) {
                internshipName = FastJsonUtil.toJson(invObj).getString("name");
            }
        }

        return buildStudentPostBreakdownResult(internshipId, internshipName, scope.reportDepartmentId, st,
                pageNum, pageSize, (int) paged.getTotalElements(), paged.getTotalPages(), counts, rows);
    }

    private static JSONObject toStudentPostBreakdownRow(ViewExternalInternshipStudentPostBreakdown v) {
        JSONObject row = new JSONObject();
        row.put("userId", v.getUserId());
        row.put("userName", v.getUserName());
        row.put("account", v.getAccount());
        row.put("departmentId", v.getDepartmentId());
        row.put("departmentName", v.getDepartmentName());
        row.put("selectionStatus", v.getSelectionStatus());
        if (v.getVerifyProcessId() != null) {
            row.put("verifyProcessId", v.getVerifyProcessId());
        }
        if (v.getIsAudit() != null) {
            row.put("isAudit", v.getIsAudit());
        }
        if (v.getInternshipPostName() != null) {
            row.put("internshipPostName", v.getInternshipPostName());
        }
        if (v.getCompanyName() != null) {
            row.put("companyName", v.getCompanyName());
        }
        return row;
    }

    private static JSONObject buildStudentPostBreakdownResult(Integer internshipId, String internshipName,
                                                             Integer reportDepartmentId, String status,
                                                             int pageNum, int pageSize, int totalElements,
                                                             int totalPages, JSONObject counts, JSONArray rows) {
        JSONObject result = new JSONObject();
        result.put("internshipId", internshipId);
        result.put("internshipName", internshipName);
        result.put("departmentId", reportDepartmentId);
        result.put("status", status);
        result.put("page", pageNum);
        result.put("size", pageSize);
        result.put("counts", counts);
        result.put("totalElements", totalElements);
        result.put("totalPages", totalPages);
        result.put("rows", rows);
        return result;
    }

    private static String normalizeStudentPostBreakdownStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return STU_POST_STATUS_ALL;
        }
        String s = status.trim();
        if (STU_POST_STATUS_ALL.equals(s)
                || STU_POST_STATUS_NOT_SELECTED.equals(s)
                || STU_POST_STATUS_SELECTED.equals(s)
                || STU_POST_STATUS_SELECTED_PENDING.equals(s)
                || STU_POST_STATUS_POST_APPROVED.equals(s)) {
            return s;
        }
        throw BaseResponse.parameterInvalid.error(
                "status 无效，可选：all、notSelected、selected、selectedPendingAudit、postApproved");
    }

    private void assertExternalInternship(Integer internshipId) {
        if (internshipId == null) {
            throw BaseResponse.parameterInvalid.error("internshipId 不能为空");
        }
        Object v = iCommonService.getOneRecordById("ViewMainInternship", internshipId);
        if (v == null) {
            throw BaseResponse.parameterInvalid.error("实习项目不存在");
        }
        JSONObject j = FastJsonUtil.toJson(v);
        String intTypeName = j.getString("intTypeName");
        if (intTypeName == null || !EXTERNAL_INT_TYPE_NAME.equals(intTypeName.trim())) {
            throw BaseResponse.parameterInvalid.error("仅支持校外实习项目");
        }
    }

    private void assertInternalInternship(Integer internshipId) {
        if (internshipId == null) {
            throw BaseResponse.parameterInvalid.error("internshipId 不能为空");
        }
        Object v = iCommonService.getOneRecordById("ViewMainInternship", internshipId);
        if (v == null) {
            throw BaseResponse.parameterInvalid.error("实习项目不存在");
        }
        JSONObject j = FastJsonUtil.toJson(v);
        String intTypeName = j.getString("intTypeName");
        if (intTypeName == null || !INTERNAL_INT_TYPE_NAME.equals(intTypeName.trim())) {
            throw BaseResponse.parameterInvalid.error("仅支持校内实习项目");
        }
    }

    private Set<Integer> mergeProjectUserIdsInAllowed(Integer internshipId, String jobCode, Set<Integer> allowedUserIds) {
        if (allowedUserIds == null || allowedUserIds.isEmpty()) {
            return new HashSet<>();
        }
        JSONObject sk = new JSONObject();
        sk.put("internshipId", internshipId);
        sk.put("jobCode", jobCode);
        @SuppressWarnings("unchecked")
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                "ViewVerifyProcessRelIntershipUserMerge", sk, null, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
        List<Object> list = page.getContent();
        if (list == null || list.isEmpty()) {
            return new HashSet<>();
        }
        return list.stream()
                .map(FastJsonUtil::toJson)
                .map(json -> json.getInteger("userId"))
                .filter(Objects::nonNull)
                .filter(allowedUserIds::contains)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private static int countDistinctFullyApprovedTitleRelations(List<Object> titleTeacherMerges) {
        if (titleTeacherMerges == null || titleTeacherMerges.isEmpty()) {
            return 0;
        }
        Set<Integer> ids = new HashSet<>();
        for (Object o : titleTeacherMerges) {
            JSONObject j = FastJsonUtil.toJson(o);
            if (Boolean.TRUE.equals(j.getBoolean("isAllVerified"))) {
                Integer rid = j.getInteger("relationId");
                if (rid != null) {
                    ids.add(rid);
                }
            }
        }
        return ids.size();
    }

    private static Set<Integer> collectTeacherIdsWithNonSaveTopicAudit(List<Object> titleTeacherMerges) {
        Set<Integer> set = new HashSet<>();
        if (titleTeacherMerges == null) {
            return set;
        }
        for (Object o : titleTeacherMerges) {
            JSONObject j = FastJsonUtil.toJson(o);
            Integer tid = j.getInteger("teacherId");
            Integer audit = j.getInteger("isAudit");
            if (tid != null && audit != null && audit != Constant.AUDIT_STATUS.SAVE) {
                set.add(tid);
            }
        }
        return set;
    }

    private Map<Integer, JSONObject> buildStudentTitleBestMergeMap(Integer internshipId) {
        List<Object> all = fetchAllTitleStudentMergeByInternship(internshipId);
        Map<Integer, JSONObject> map = new HashMap<>();
        for (Object o : all) {
            JSONObject j = FastJsonUtil.toJson(o);
            Integer stuId = j.getInteger("stuId");
            if (stuId == null) {
                continue;
            }
            JSONObject prev = map.get(stuId);
            map.put(stuId, prev == null ? j : pickBetterTitleMergeRow(prev, j));
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private List<Object> fetchAllTitleTeacherMergeByInternship(Integer internshipId) {
        JSONObject sk = new JSONObject();
        sk.put("internshipId", internshipId);
        List<Object> all = new ArrayList<>();
        int p = 1;
        while (true) {
            Page<Object> dbPage = (Page<Object>) iCommonService.getSomeRecords(
                    "ViewVerifyProcessRelTitleTeacherMerge", sk, null, Sort.unsorted(), p, POST_PAGE_SIZE);
            List<Object> content = dbPage.getContent();
            if (content == null || content.isEmpty()) {
                break;
            }
            all.addAll(content);
            if (content.size() < POST_PAGE_SIZE) {
                break;
            }
            p++;
        }
        return all;
    }

    private static String normalizeTitleSelectionBreakdownStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return TITLE_SEL_STATUS_ALL;
        }
        String s = status.trim();
        if (TITLE_SEL_STATUS_ALL.equals(s)
                || TITLE_SEL_STATUS_NOT_SUBMITTED.equals(s)
                || TITLE_SEL_STATUS_PENDING.equals(s)
                || TITLE_SEL_STATUS_APPROVED.equals(s)) {
            return s;
        }
        throw BaseResponse.parameterInvalid.error("status 无效，可选：all、notSubmitted、pendingAudit、titleApproved");
    }

    private static String resolveInternalTitleSelectionStatusFromSample(JSONObject best) {
        if (best == null) {
            return TITLE_SEL_STATUS_NOT_SUBMITTED;
        }
        int rank = internalTitleCategoryRank(best);
        if (rank == 2) {
            return TITLE_SEL_STATUS_APPROVED;
        }
        if (rank == 1) {
            return TITLE_SEL_STATUS_PENDING;
        }
        return TITLE_SEL_STATUS_NOT_SUBMITTED;
    }

    private static int internalTitleCategoryRank(JSONObject j) {
        if (j == null) {
            return 0;
        }
        Boolean allV = j.getBoolean("isAllVerified");
        Integer isAudit = j.getInteger("isAudit");
        if (Boolean.TRUE.equals(allV)) {
            return 2;
        }
        if (isAudit != null && isAudit == Constant.AUDIT_STATUS.SUBMIT) {
            return 1;
        }
        if (isAudit != null && isAudit == Constant.AUDIT_STATUS.PASS && !Boolean.TRUE.equals(allV)) {
            return 1;
        }
        return 0;
    }

    private static JSONObject pickBetterTitleMergeRow(JSONObject a, JSONObject b) {
        int ra = internalTitleCategoryRank(a);
        int rb = internalTitleCategoryRank(b);
        if (rb != ra) {
            return rb > ra ? b : a;
        }
        long ta = vpUpdateTimeMillis(a);
        long tb = vpUpdateTimeMillis(b);
        if (tb != ta) {
            return tb > ta ? b : a;
        }
        Integer ida = a.getInteger("id");
        Integer idb = b.getInteger("id");
        if (ida != null && idb != null && !idb.equals(ida)) {
            return idb > ida ? b : a;
        }
        return b;
    }

    private static long vpUpdateTimeMillis(JSONObject j) {
        if (j == null) {
            return 0L;
        }
        Date d = j.getDate("vpUpdateTime");
        return d != null ? d.getTime() : 0L;
    }

    @SuppressWarnings("unchecked")
    private List<Object> fetchAllTitleStudentMergeByInternship(Integer internshipId) {
        JSONObject sk = new JSONObject();
        sk.put("internshipId", internshipId);
        List<Object> all = new ArrayList<>();
        int p = 1;
        while (true) {
            Page<Object> dbPage = (Page<Object>) iCommonService.getSomeRecords(
                    "ViewVerifyProcessRelTitleStudentMerge", sk, null, Sort.unsorted(), p, POST_PAGE_SIZE);
            List<Object> content = dbPage.getContent();
            if (content == null || content.isEmpty()) {
                break;
            }
            all.addAll(content);
            if (content.size() < POST_PAGE_SIZE) {
                break;
            }
            p++;
        }
        return all;
    }

    private static boolean lacksTitleForDisplay(Integer titleId) {
        return titleId == null || titleId <= 0;
    }

    private static boolean lacksTeacherForDisplay(Integer teacherId) {
        return teacherId == null || teacherId <= 0;
    }

    private JSONObject buildOneInternalTitleRow(Integer stuId, JSONObject mergeSample) {
        JSONObject row = new JSONObject();
        row.put("stuId", stuId);
        String studentName = null;
        String titleName = "未选";
        String teacherName = "未选";
        if (mergeSample != null) {
            studentName = mergeSample.getString("studentName");
            if (!lacksTitleForDisplay(mergeSample.getInteger("titleId"))) {
                String n = mergeSample.getString("name");
                if (n != null && !n.isBlank()) {
                    titleName = n;
                }
            }
            if (!lacksTeacherForDisplay(mergeSample.getInteger("teacherId"))) {
                String tn = mergeSample.getString("teacherName");
                if (tn != null && !tn.isBlank()) {
                    teacherName = tn;
                }
            }
        }
        if (studentName == null || studentName.isBlank()) {
            Object uo = iCommonService.getOneRecordById("ViewBaseUser", stuId);
            if (uo != null) {
                studentName = FastJsonUtil.toJson(uo).getString("name");
            }
        }
        row.put("studentName", studentName);
        row.put("titleName", titleName);
        row.put("teacherName", teacherName);
        row.put("relTitleStudentId", mergeSample != null ? mergeSample.getInteger("relTitleStudentId") : null);
        return row;
    }

    private JSONObject buildPagedInternalTitleCategory(List<Integer> fullUserIds, Map<Integer, JSONObject> mergeMap,
                                                       int pageNum, int pageSize) {
        List<Integer> ids = fullUserIds == null ? Collections.emptyList() : fullUserIds;
        int total = ids.size();
        int totalPages = pageSize <= 0 ? 0 : (int) Math.ceil((double) total / (double) pageSize);
        int from = Math.max(0, (pageNum - 1) * pageSize);
        int to = Math.min(from + pageSize, total);
        List<Integer> slice = from >= total ? Collections.emptyList() : ids.subList(from, to);
        JSONArray rows = new JSONArray();
        for (Integer uid : slice) {
            JSONObject mergeSample = mergeMap != null ? mergeMap.get(uid) : null;
            JSONObject row = buildOneInternalTitleRow(uid, mergeSample);
            row.put("selectionStatus", resolveInternalTitleSelectionStatusFromSample(mergeSample));
            rows.add(row);
        }
        JSONObject block = new JSONObject();
        block.put("rows", rows);
        block.put("page", pageNum);
        block.put("size", pageSize);
        block.put("totalElements", total);
        block.put("totalPages", totalPages);
        return block;
    }

    private JSONObject buildTeacherNotSubmittedTopicRow(Integer teacherUserId) {
        JSONObject row = new JSONObject();
        row.put("teacherId", teacherUserId);
        String name = "";
        String deptName = "";
        String phone = "";
        Object uo = iCommonService.getOneRecordById("ViewBaseUser", teacherUserId);
        if (uo != null) {
            JSONObject uj = FastJsonUtil.toJson(uo);
            name = uj.getString("name");
            deptName = uj.getString("departmentName");
            phone = uj.getString("phone");
        }
        if (name == null) {
            name = "";
        }
        if (deptName == null) {
            deptName = "";
        }
        if (phone == null) {
            phone = "";
        }
        row.put("teacherName", name);
        row.put("departmentName", deptName);
        row.put("phone", phone);
        row.put("displayLine", name + " - " + deptName + " - " + phone);
        return row;
    }

    @SuppressWarnings("unchecked")
    private Set<Integer> loadInternshipProjectUserIds(Integer internshipId, String jobCode) {
        JSONObject sk = new JSONObject();
        sk.put("internshipId", internshipId);
        sk.put("jobCode", jobCode);
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                "ViewVerifyProcessRelIntershipUserMerge", sk, null, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
        List<Object> list = page.getContent();
        if (list == null || list.isEmpty()) {
            return new HashSet<>();
        }
        return list.stream()
                .map(FastJsonUtil::toJson)
                .map(json -> json.getInteger("userId"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Set<Integer> loadUserIdsByDepartmentAndJobCode(Integer departmentId, String jobCode) {
        JSONObject sk = new JSONObject();
        sk.put("departmentId", departmentId);
        sk.put("jobCode", jobCode);
        @SuppressWarnings("unchecked")
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                "ViewBaseUser", sk, null, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
        List<Object> list = page.getContent();
        if (list == null || list.isEmpty()) {
            return new HashSet<>();
        }
        return list.stream()
                .map(FastJsonUtil::toJson)
                .map(json -> json.getInteger("id"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * 收集 {@link newcms.entity.db.BaseDepartment} 中某节点及其全部子孙节点 id（仅未删除节点参与构图）。
     */
    private Set<Integer> collectDepartmentIdsInSubtree(Integer rootDepartmentId) {
        if (rootDepartmentId == null) {
            return new HashSet<>();
        }
        JSONObject sk = new JSONObject();
        sk.put("isDeleted", 0);
        @SuppressWarnings("unchecked")
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                "BaseDepartment", sk, null, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
        List<Object> list = page.getContent();
        if (list == null || list.isEmpty()) {
            return new HashSet<>(Collections.singletonList(rootDepartmentId));
        }
        Map<Integer, List<Integer>> childrenByParent = new HashMap<>();
        Set<Integer> allDeptIds = new HashSet<>();
        for (Object o : list) {
            JSONObject j = FastJsonUtil.toJson(o);
            Integer id = j.getInteger("id");
            if (id == null) {
                continue;
            }
            allDeptIds.add(id);
            Integer pid = j.getInteger("parentId");
            childrenByParent.computeIfAbsent(pid != null ? pid : -1, k -> new ArrayList<>()).add(id);
        }
        if (!allDeptIds.contains(rootDepartmentId)) {
            return new HashSet<>(Collections.singletonList(rootDepartmentId));
        }
        Set<Integer> out = new HashSet<>();
        ArrayDeque<Integer> dq = new ArrayDeque<>();
        dq.add(rootDepartmentId);
        while (!dq.isEmpty()) {
            Integer cur = dq.poll();
            if (cur == null || out.contains(cur)) {
                continue;
            }
            out.add(cur);
            List<Integer> ch = childrenByParent.get(cur);
            if (ch != null) {
                for (Integer c : ch) {
                    if (c != null && !out.contains(c)) {
                        dq.add(c);
                    }
                }
            }
        }
        return out;
    }

    /**
     * 与 {@link #loadUserIdsByDepartmentAndJobCode} 相同语义，但 departmentId 视为树根，
     * 包含其下所有子部门（院系→专业/班级等）内的用户。
     */
    private Set<Integer> loadUserIdsByDepartmentSubtreeAndJobCode(Integer rootDepartmentId, String jobCode) {
        if (rootDepartmentId == null || jobCode == null || jobCode.trim().isEmpty()) {
            return new HashSet<>();
        }
        Set<Integer> deptIds = collectDepartmentIdsInSubtree(rootDepartmentId);
        if (deptIds.isEmpty()) {
            return new HashSet<>();
        }
        if (deptIds.size() == 1) {
            return loadUserIdsByDepartmentAndJobCode(rootDepartmentId, jobCode);
        }
        String idStr = deptIds.stream().map(String::valueOf).collect(Collectors.joining(Constant.SPLIT_OPERATOR.COMMA));
        JSONObject userSearchKeys = new JSONObject();
        userSearchKeys.put("departmentId", idStr);
        userSearchKeys.put("jobCode", jobCode);
        Map<String, String> repMap = new HashMap<>();
        repMap.put("departmentId", Constant.IN);
        @SuppressWarnings("unchecked")
        Page<Object> userPage = (Page<Object>) iCommonService.getSomeRecords(
                "ViewBaseUser", userSearchKeys, repMap, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
        List<Object> userList = userPage.getContent();
        if (userList == null || userList.isEmpty()) {
            return new HashSet<>();
        }
        return userList.stream()
                .map(FastJsonUtil::toJson)
                .map(json -> json.getInteger("id"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Set<Integer> loadUserIdsByDepartmentIdsAndJobCode(Collection<Integer> departmentIds, String jobCode) {
        if (departmentIds == null || departmentIds.isEmpty() || jobCode == null || jobCode.trim().isEmpty()) {
            return new HashSet<>();
        }
        List<Integer> idList = departmentIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (idList.isEmpty()) {
            return new HashSet<>();
        }
        if (idList.size() == 1) {
            return loadUserIdsByDepartmentAndJobCode(idList.get(0), jobCode);
        }
        String idStr = idList.stream().map(String::valueOf).collect(Collectors.joining(Constant.SPLIT_OPERATOR.COMMA));
        JSONObject userSearchKeys = new JSONObject();
        userSearchKeys.put("departmentId", idStr);
        userSearchKeys.put("jobCode", jobCode);
        Map<String, String> repMap = new HashMap<>();
        repMap.put("departmentId", Constant.IN);
        @SuppressWarnings("unchecked")
        Page<Object> userPage = (Page<Object>) iCommonService.getSomeRecords(
                "ViewBaseUser", userSearchKeys, repMap, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
        List<Object> userList = userPage.getContent();
        if (userList == null || userList.isEmpty()) {
            return new HashSet<>();
        }
        return userList.stream()
                .map(FastJsonUtil::toJson)
                .map(json -> json.getInteger("id"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private int countDistinctMergeUsersInSet(Integer internshipId, String jobCode, Set<Integer> allowedUserIds) {
        if (allowedUserIds == null || allowedUserIds.isEmpty()) {
            return 0;
        }
        JSONObject sk = new JSONObject();
        sk.put("internshipId", internshipId);
        sk.put("jobCode", jobCode);
        @SuppressWarnings("unchecked")
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                "ViewVerifyProcessRelIntershipUserMerge", sk, null, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
        List<Object> list = page.getContent();
        if (list == null || list.isEmpty()) {
            return 0;
        }
        return (int) list.stream()
                .map(FastJsonUtil::toJson)
                .map(json -> json.getInteger("userId"))
                .filter(Objects::nonNull)
                .filter(allowedUserIds::contains)
                .distinct()
                .count();
    }

    private Set<Integer> loadInternshipProjectStudentUserIds(Integer internshipId) {
        return loadInternshipProjectUserIds(internshipId, Constant.USER_JOB_CODE.STUDENT);
    }

    private Integer parseStudentUserIdFromStuPostMerge(JSONObject j) {
        if (j == null) {
            return null;
        }
        Integer uid = j.getInteger("studentId");
        if (uid != null) {
            return uid;
        }
        String s = j.getString("studentId");
        if (s == null || s.isBlank()) {
            return j.getInteger("userId");
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private JSONObject buildPagedStudentCategory(List<Integer> fullUserIds, Map<Integer, JSONObject> mergeSample,
                                               int pageNum, int pageSize) {
        List<Integer> ids = fullUserIds == null ? Collections.emptyList() : fullUserIds;
        int total = ids.size();
        int totalPages = pageSize <= 0 ? 0 : (int) Math.ceil((double) total / (double) pageSize);
        int from = Math.max(0, (pageNum - 1) * pageSize);
        int to = Math.min(from + pageSize, total);
        List<Integer> slice = from >= total ? Collections.emptyList() : ids.subList(from, to);
        JSONObject block = new JSONObject();
        block.put("rows", buildStudentBriefList(slice, mergeSample));
        block.put("page", pageNum);
        block.put("size", pageSize);
        block.put("totalElements", total);
        block.put("totalPages", totalPages);
        return block;
    }

    private JSONObject buildOneStudentBriefRow(Integer uid, Map<Integer, JSONObject> mergeSample) {
        JSONObject row = new JSONObject();
        row.put("userId", uid);
        if (mergeSample != null && mergeSample.containsKey(uid)) {
            JSONObject m = mergeSample.get(uid);
            row.put("userName", m.getString("studentName"));
            row.put("account", firstNonEmpty(m.getString("studentAccount"), m.getString("account")));
            row.put("departmentId", m.getInteger("departmentId"));
            row.put("departmentName", m.getString("departmentName"));
            row.put("internshipPostName", m.getString("internshipPostName"));
            row.put("companyName", m.getString("companyName"));
            row.put("isAudit", m.getInteger("isAudit"));
            // 合并视图 id 即 main_verify_process.id
            row.put("verifyProcessId", m.getInteger("id"));
        } else {
            Object uo = iCommonService.getOneRecordById("ViewBaseUser", uid);
            if (uo != null) {
                JSONObject uj = FastJsonUtil.toJson(uo);
                row.put("userName", uj.getString("name"));
                row.put("account", uj.getString("account"));
                row.put("departmentId", uj.getInteger("departmentId"));
                row.put("departmentName", uj.getString("departmentName"));
            }
        }
        return row;
    }

    private JSONArray buildStudentBriefList(List<Integer> userIds, Map<Integer, JSONObject> mergeSample) {
        JSONArray arr = new JSONArray();
        if (userIds == null || userIds.isEmpty()) {
            return arr;
        }
        for (Integer uid : userIds) {
            arr.add(buildOneStudentBriefRow(uid, mergeSample));
        }
        return arr;
    }

    private static String firstNonEmpty(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return a;
    }

    @Override
    public Object initTeacherStudentByInternshipId(Integer internshipId, Integer processId, Integer createUserId, String verifyUserId,
                                                    Integer currentVerifyTypeId) {
        validateInitTeacherStudentParams(internshipId, processId, createUserId, verifyUserId);
        int verifyType = (currentVerifyTypeId == null ? 1 : currentVerifyTypeId);
        if (verifyType <= 0) {
            throw BaseResponse.parameterInvalid.error("currentVerifyTypeId 无效，必须为正整数");
        }

        List<Integer> teacherIds = getTeacherIdsForAssignment(internshipId);

        List<Object> saveMergeRows = listSaveInternalTutorMergeRowsForInitAssign(internshipId);
        if (saveMergeRows.isEmpty()) {
            return buildInitTeacherStudentResult(0, 0);
        }

        Set<Integer> reassignRelIds = new HashSet<>();
        for (Object row : saveMergeRows) {
            if (row == null) {
                continue;
            }
            JSONObject j = FastJsonUtil.toJson(row);
            Integer rtsId = j.getInteger("relationId");
            if (rtsId == null) {
                rtsId = j.getInteger("relTeaStuId");
            }
            if (rtsId != null) {
                reassignRelIds.add(rtsId);
            }
        }

        Map<Integer, Integer> teacherLoadMap = buildTeacherLoadMapExcluding(internshipId, teacherIds, reassignRelIds);
        int[] counts = assignInternalTeacherForExistingMergeRows(
                internshipId, processId, createUserId, verifyUserId, saveMergeRows, teacherLoadMap, verifyType);
        return buildInitTeacherStudentResult(counts[0], counts[1]);
    }

    @Override
    public Object initInternalTutorByInternshipId(Integer internshipId, Integer processId, Integer createUserId, String verifyUserId,
                                                  Integer currentVerifyTypeId) {
        return initTeacherStudentByInternshipId(internshipId, processId, createUserId, verifyUserId, currentVerifyTypeId);
    }

    private void validateInitTeacherStudentParams(Integer internshipId, Integer processId, Integer createUserId, String verifyUserId) {
        if (internshipId == null || processId == null || createUserId == null || verifyUserId == null) {
            throw BaseResponse.parameterInvalid.error("internshipId、processId、createUserId、verifyUserId 不能为空");
        }
    }

    /**
     * 从校内师生审核综合视图取当前实习项目下、校外分配校内导师流程、
     * 审核状态为待提交（SAVE）的行（含已写入 teacherId 的草稿），按 RelTeacherStudent.id 去重。
     * 已提交（非 SAVE）的记录不会出现在视图的 SAVE 查询中，不会被本接口改写。
     */
    @SuppressWarnings("unchecked")
    private List<Object> listSaveInternalTutorMergeRowsForInitAssign(Integer internshipId) {
        JSONObject sk = new JSONObject();
        sk.put("internshipId", internshipId);
        sk.put("isAudit", Constant.AUDIT_STATUS.SAVE);
        applyExternalAssignInternalTutorMergeFilter(sk);
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                VIEW_VERIFY_REL_INT_TEA_STU_MERGE, sk, null, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
        List<Object> content = page.getContent();
        if (content == null || content.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Integer> seenRtsIds = new HashSet<>();
        List<Object> out = new ArrayList<>();
        for (Object row : content) {
            if (row == null) {
                continue;
            }
            JSONObject j = FastJsonUtil.toJson(row);
            Integer rtsId = j.getInteger("relationId");
            if (rtsId == null) {
                rtsId = j.getInteger("relTeaStuId");
            }
            if (rtsId == null || !seenRtsIds.add(rtsId)) {
                continue;
            }
            out.add(row);
        }
        return out;
    }

    /**
     * 一次性返回某实习项目在校内导师分配合并视图里所有仍为 SAVE 的 RelTeacherStudent id 集合。
     * 用于循环写入前替代每条记录 2 次回查（{@link #isInternalTutorAssignMergeRowStillSave} 的批量等价）。
     */
    @SuppressWarnings("unchecked")
    private Set<Integer> snapshotStillSaveRtsIds(Integer internshipId) {
        if (internshipId == null) {
            return Collections.emptySet();
        }
        JSONObject sk = new JSONObject();
        sk.put("internshipId", internshipId);
        sk.put("isAudit", Constant.AUDIT_STATUS.SAVE);
        applyExternalAssignInternalTutorMergeFilter(sk);
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                VIEW_VERIFY_REL_INT_TEA_STU_MERGE, sk, null, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
        List<Object> rows = page.getContent();
        if (rows == null || rows.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Integer> ids = new HashSet<>(rows.size());
        for (Object row : rows) {
            JSONObject j = FastJsonUtil.toJson(row);
            Integer id = j.getInteger("relationId");
            if (id == null) {
                id = j.getInteger("relTeaStuId");
            }
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    /**
     * 再次确认该师生关联在校内导师分配合并视图中仍为 SAVE，避免列表拉取后用户已提交仍被改派。
     */
    @SuppressWarnings("unchecked")
    private boolean isInternalTutorAssignMergeRowStillSave(Integer internshipId, Integer relTeacherStudentId) {
        if (internshipId == null || relTeacherStudentId == null) {
            return false;
        }
        JSONObject sk = new JSONObject();
        sk.put("internshipId", internshipId);
        sk.put("isAudit", Constant.AUDIT_STATUS.SAVE);
        applyExternalAssignInternalTutorMergeFilter(sk);
        sk.put("relationId", relTeacherStudentId);
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                VIEW_VERIFY_REL_INT_TEA_STU_MERGE, sk, null, Sort.unsorted(), 1, 1);
        if (page.getContent() != null && !page.getContent().isEmpty()) {
            return true;
        }
        sk.remove("relationId");
        sk.put("relTeaStuId", relTeacherStudentId);
        Page<Object> page2 = (Page<Object>) iCommonService.getSomeRecords(
                VIEW_VERIFY_REL_INT_TEA_STU_MERGE, sk, null, Sort.unsorted(), 1, 1);
        return page2.getContent() != null && !page2.getContent().isEmpty();
    }

    /**
     * 对已存在的 RelTeacherStudent 写入均衡分配的校内导师；不新建 MainVerifyProcess。
     * 仅处理仍为 SAVE 的草稿行；已提交的审核状态不会通过合并视图进入本列表，写入前再次校验 SAVE。
     * 若已存在对应 processId 下的审核记录，则将其 createUserId、verifyUserId 更新为本次传入值。
     *
     * @return int[0]=已更新 teacherId 的 RelTeacherStudent 条数，int[1]=已更新的 MainVerifyProcess 行数（可能大于条数，因一对多）
     */
    private int[] assignInternalTeacherForExistingMergeRows(Integer internshipId, Integer processId, Integer createUserId, String verifyUserId,
            List<Object> mergeRows, Map<Integer, Integer> teacherLoadMap, int currentVerifyTypeId) {
        int assignedCount = 0;
        int verifyUpdatedCount = 0;
        // 一次性快照本实习当前仍为 SAVE 的 rtsId 集合，避免在循环里对每条行做 2 次回查（原 2N 次缩到 1 次）。
        Set<Integer> stillSaveRtsIds = snapshotStillSaveRtsIds(internshipId);
        for (Object row : mergeRows) {
            JSONObject j = FastJsonUtil.toJson(row);
            Integer rtsId = j.getInteger("relationId");
            if (rtsId == null) {
                rtsId = j.getInteger("relTeaStuId");
            }
            if (rtsId == null) {
                continue;
            }
            if (!stillSaveRtsIds.contains(rtsId)) {
                logger.warn("校内导师分配跳过：RelTeacherStudent id={} 已非待提交（SAVE）", rtsId);
                continue;
            }
            Object rtsObj = iCommonService.getOneRecordById(TABLE_REL_TEACHER_STUDENT, rtsId);
            if (rtsObj == null) {
                logger.warn("校内导师分配跳过：RelTeacherStudent id={} 不存在", rtsId);
                continue;
            }
            Integer selectedTeacherId = chooseBalancedTeacherId(teacherLoadMap);
            teacherLoadMap.put(selectedTeacherId, teacherLoadMap.get(selectedTeacherId) + 1);
            JSONObject upd = new JSONObject();
            upd.put("id", rtsId);
            upd.put("teacherId", selectedTeacherId);
            upd.put("currentVerifyTypeId", currentVerifyTypeId);
            iCommonService.saveOneRecord(TABLE_REL_TEACHER_STUDENT, upd);
            ensureDiaryEntriesForAssignedStuPost(j.getInteger("relInternshipId"));
            assignedCount++;
            verifyUpdatedCount += updateMainVerifyProcessCreatorAndVerifier(rtsId, processId, createUserId, verifyUserId);
        }
        return new int[]{assignedCount, verifyUpdatedCount};
    }

    private void ensureDiaryEntriesForAssignedStuPost(Integer relInternshipId) {
        if (relInternshipId != null) {
            iDiaryService.createDiaryEntriesForStudent(relInternshipId, TABLE_REL_STU_INTERNSHIP_POST);
        }
    }

    /**
     * 按师生业务主键与流程更新 MainVerifyProcess 的创建人、审核人字段（不新建记录）。
     */
    @SuppressWarnings("unchecked")
    private int updateMainVerifyProcessCreatorAndVerifier(Integer relationId, Integer processId,
            Integer createUserId, String verifyUserId) {
        if (relationId == null || processId == null || createUserId == null || verifyUserId == null) {
            return 0;
        }
        JSONObject sk = new JSONObject();
        sk.put("relationId", relationId);
        sk.put("processId", processId);
        sk.put("tableName", TABLE_REL_TEACHER_STUDENT);
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                TABLE_MAIN_VERIFY_PROCESS, sk, null, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
        List<Object> content = page.getContent();
        if (content == null || content.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (Object obj : content) {
            if (obj == null) {
                continue;
            }
            JSONObject v = FastJsonUtil.toJson(obj);
            Integer id = v.getInteger("id");
            if (id == null) {
                continue;
            }
            JSONObject upd = new JSONObject();
            upd.put("id", id);
            upd.put("createUserId", createUserId);
            upd.put("verifyUserId", verifyUserId);
            iCommonService.saveOneRecord(TABLE_MAIN_VERIFY_PROCESS, upd);
            n++;
        }
        return n;
    }

    /**
     * 取可参与校内导师均衡分配的用户：同实习项目、审核通过（PASS），
     * jobCode 排除学生（STUDENT）与企业导师（COMPANY_TUTOR），其余身份均可。
     */
    @SuppressWarnings("unchecked")
    private List<Integer> getTeacherIdsForAssignment(Integer internshipId) {
        JSONObject teacherSearchKeys = new JSONObject();
        teacherSearchKeys.put("internshipId", internshipId);
        teacherSearchKeys.put("jobCode",
                Constant.USER_JOB_CODE.STUDENT + "," + Constant.USER_JOB_CODE.COMPANY_TUTOR);
        teacherSearchKeys.put("isAudit", Constant.AUDIT_STATUS.PASS);
        Map<String, String> regMap = new HashMap<>(1);
        regMap.put("jobCode", Constant.NOT_IN);
        Page<Object> teacherPage = (Page<Object>) iCommonService.getSomeRecords(
                "ViewVerifyProcessRelIntershipUserMerge", teacherSearchKeys, regMap, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
        List<Integer> teacherIds = teacherPage.getContent().stream()
                .map(FastJsonUtil::toJson)
                .map(teacherJson -> teacherJson.getInteger("userId"))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (teacherIds.isEmpty()) {
            throw BaseResponse.moreInfoError.error(
                    "未找到审核通过的可分配导师（ViewVerifyProcessRelIntershipUserMerge.jobCode NOT IN STUDENT/COMPANY_TUTOR, isAudit=PASS）");
        }
        return teacherIds;
    }

    @SuppressWarnings("unchecked")
    private List<Object> getStudentInternshipSelections(Integer internshipId) {
        JSONObject postMergeSearchKeys = new JSONObject();
        postMergeSearchKeys.put("internshipId", internshipId);
        postMergeSearchKeys.put("isAudit", Constant.AUDIT_STATUS.PASS);
        Page<Object> postMergePage = (Page<Object>) iCommonService.getSomeRecords(
                "ViewVerifyProcessInternshipPostMerge", postMergeSearchKeys, null, Sort.unsorted(), 1, POST_PAGE_SIZE);
        List<Object> postMergeList = postMergePage.getContent();
        if (postMergeList == null || postMergeList.isEmpty()) {
            throw BaseResponse.moreInfoError.error("未找到审核已通过的实习岗位记录");
        }

        List<Integer> postIds = postMergeList.stream()
                .map(FastJsonUtil::toJson)
                .map(this::parseInternshipPostIdFromInternshipPostMergeRow)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (postIds.isEmpty()) {
            throw BaseResponse.moreInfoError.error("未找到有效岗位ID");
        }

        JSONObject mergeSearchKeys = new JSONObject();
        mergeSearchKeys.put("internshipPostId", postIds.stream().map(String::valueOf).collect(Collectors.joining(Constant.SPLIT_OPERATOR.COMMA)));
        mergeSearchKeys.put("isAudit", Constant.AUDIT_STATUS.PASS);
        Map<String, String> mergeRepMap = new HashMap<>();
        mergeRepMap.put("internshipPostId", Constant.IN);
        Page<Object> mergePage = (Page<Object>) iCommonService.getSomeRecords(
                "ViewVerifyProcessRelStuInternshipPostMerge", mergeSearchKeys, mergeRepMap, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
        List<Object> mergeList = mergePage.getContent();
        if (mergeList == null || mergeList.isEmpty()) {
            throw BaseResponse.moreInfoError.error("未找到审核已通过的学生岗位选择记录");
        }
        return mergeList;
    }

    private Integer parseInternshipPostIdFromInternshipPostMergeRow(JSONObject row) {
        if (row == null) {
            return null;
        }
        Integer id = row.getInteger("internshipPostId");
        if (id != null) {
            return id;
        }
        String raw = row.getString("internshipPostId");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object> getRelTeacherStudentRecords(Integer internshipId) {
        JSONObject relTeacherSearchKeys = new JSONObject();
        relTeacherSearchKeys.put("internshipId", internshipId);
        Page<Object> relTeacherPage = (Page<Object>) iCommonService.getSomeRecords(
                "RelTeacherStudent", relTeacherSearchKeys, null, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
        return relTeacherPage.getContent();
    }

    /**
     * 统计各导师在本实习项目下已占用的学生数；{@code excludeRelIds} 中的 RelTeacherStudent 行不计入（用于 SAVE 草稿重算前剥离旧分配）。
     */
    private Map<Integer, Integer> buildTeacherLoadMapExcluding(Integer internshipId, List<Integer> teacherIds, Set<Integer> excludeRelIds) {
        List<Object> relTeacherList = getRelTeacherStudentRecords(internshipId);
        return buildTeacherLoadMapFromRelList(relTeacherList, teacherIds, excludeRelIds);
    }

    private Map<Integer, Integer> buildTeacherLoadMapFromRelList(List<Object> relTeacherList, List<Integer> teacherIds,
            Set<Integer> excludeRelIds) {
        Map<Integer, Integer> teacherLoadMap = new HashMap<>();
        for (Integer teacherId : teacherIds) {
            teacherLoadMap.put(teacherId, 0);
        }
        if (relTeacherList != null) {
            for (Object relObj : relTeacherList) {
                JSONObject relJson = FastJsonUtil.toJson(relObj);
                Integer relId = relJson.getInteger("id");
                if (excludeRelIds != null && !excludeRelIds.isEmpty() && relId != null && excludeRelIds.contains(relId)) {
                    continue;
                }
                Integer teacherId = relJson.getInteger("teacherId");
                if (teacherId != null && teacherLoadMap.containsKey(teacherId)) {
                    teacherLoadMap.put(teacherId, teacherLoadMap.get(teacherId) + 1);
                }
            }
        }
        return teacherLoadMap;
    }

    private Integer chooseBalancedTeacherId(Map<Integer, Integer> teacherLoadMap) {
        int minLoad = teacherLoadMap.values().stream().min(Integer::compareTo).orElse(0);
        List<Integer> candidateTeacherIds = teacherLoadMap.entrySet().stream()
                .filter(entry -> entry.getValue() == minLoad)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        return candidateTeacherIds.get(ThreadLocalRandom.current().nextInt(candidateTeacherIds.size()));
    }

    private JSONObject buildInitTeacherStudentResult(int createdRelTeacherStudentCount, int createdVerifyProcessCount) {
        JSONObject result = new JSONObject();
        result.put("createdRelTeacherStudentCount", createdRelTeacherStudentCount);
        result.put("createdVerifyProcessCount", createdVerifyProcessCount);
        return result;
    }



    // ==================== 实习计划流程（需要审核） ====================

    /**
     * 支持 node 为 Fastjson 对象/数组，或前端传来的 JSON 字符串（如 {@code "[{...},{...}]"} ）。
     */
    private Object unwrapAuditProcessNodeArg(Object node) {
        if (!(node instanceof String)) {
            return node;
        }
        String t = ((String) node).trim();
        if (t.isEmpty()) {
            throw BaseResponse.parameterInvalid.error("node 不能为空");
        }
        if (t.startsWith("[")) {
            return JSONArray.parseArray(t);
        }
        if (t.startsWith("{")) {
            return JSONObject.parseObject(t);
        }
        throw BaseResponse.parameterInvalid.error("node 字符串须为 JSON 对象或 JSON 数组");
    }

    @Override
    public Object auditProcess(Object node) {
        node = unwrapAuditProcessNodeArg(node);
        if (node instanceof JSONArray) {
            JSONArray nodesArr = (JSONArray) node;
            if (nodesArr.isEmpty()) {
                throw BaseResponse.parameterInvalid.error("node 数组不能为空");
            }
            List<Object> results = new ArrayList<>(nodesArr.size());
            for (int i = 0; i < nodesArr.size(); i++) {
                Object el = nodesArr.get(i);
                if (!(el instanceof JSONObject)) {
                    throw BaseResponse.parameterInvalid.error("node 数组元素须为对象");
                }
                results.add(auditProcessOne((JSONObject) el));
            }
            return results;
        }
        if (node instanceof JSONObject) {
            return auditProcessOne((JSONObject) node);
        }
        throw BaseResponse.parameterInvalid.error("node 须为 JSON 对象或 JSON 数组");
    }

    /**
     * 实习日志、实习打卡等多级审核：{@code MainVerifyProcess.relationId} 为业务主键。
     *
     * @param clearSubmitOnBack 为 true 时（MainDiary）退回同时 {@code submit=false}；MainSign 传 false，不写入 submit。
     */
    private Object auditProcessMultiLevelRelationBiz(JSONObject node, Object verifyObj, String bizTableName,
            Integer isAudit, boolean clearSubmitOnBack) {
        JSONObject verifyJsonPre = FastJsonUtil.toJson(verifyObj);
        Integer bizIdPre = verifyJsonPre.getInteger("relationId");
        BigDecimal diaryScore = null;

        if (TABLE_MAIN_DIARY.equals(bizTableName) && isAudit != null
                && isAudit == Constant.AUDIT_STATUS.PASS) {
            diaryScore = requireDiaryTeacherScore(node);
        }

        // 非日志业务仍保留原评分配置校验；日志评分固定为教师一级总分，不再依赖 grade_config。
        if (!TABLE_MAIN_DIARY.equals(bizTableName) && isAudit != null
                && isAudit == Constant.AUDIT_STATUS.PASS && bizIdPre != null) {
            Integer internshipId = gradeConfigService.resolveInternshipIdForDiary(bizIdPre);
            if (internshipId != null) {
                JSONObject bizJsonPre = FastJsonUtil.toJson(iCommonService.getOneRecordById(bizTableName, bizIdPre));
                Integer currentLevel = bizJsonPre.getInteger("currentVerifyTypeId");
                if (currentLevel != null) {
                    // currentVerifyTypeId 2 表示一级在审，3 表示二级在审…… 对应 levelOrder = currentLevel - 1
                    int levelOrder = currentLevel - 1;
                    if (levelOrder >= 1) {
                        gradeConfigService.requireScoreOnPass(
                                internshipId, bizTableName, levelOrder, node.getBigDecimal("score"));
                    }
                }
            }
        }

        Object saved = iCommonService.saveOneRecord("MainVerifyProcess", node);

        JSONObject verifyJson = FastJsonUtil.toJson(verifyObj);
        Integer bizId = verifyJson.getInteger("relationId");
        Integer processId = verifyJson.getInteger("processId");
        Integer createUserId = verifyJson.getInteger("createUserId");
        String reason = node.getString("reason");

        if (isAudit != null && isAudit >= 1 && bizId != null
                && reason != null && !reason.isEmpty()) {
            JSONObject updateBiz = new JSONObject();
            updateBiz.put("id", bizId);
            updateBiz.put("remarks", reason);
            iCommonService.saveOneRecord(bizTableName, updateBiz);
        }

        if (isAudit != null && isAudit == Constant.AUDIT_STATUS.PASS && bizId != null) {
            JSONObject bizJson = FastJsonUtil.toJson(iCommonService.getOneRecordById(bizTableName, bizId));
            Integer currentLevel = bizJson.getInteger("currentVerifyTypeId");
            Integer verifyTypeId = bizJson.getInteger("verifyTypeId");
            if (currentLevel != null && verifyTypeId != null) {
                Integer nextLevel = currentLevel + 1;
                JSONObject levelUpdate = new JSONObject();
                levelUpdate.put("id", bizId);
                levelUpdate.put("currentVerifyTypeId", nextLevel);
                if (TABLE_MAIN_DIARY.equals(bizTableName) && diaryScore != null) {
                    levelUpdate.put("totalScore", diaryScore);
                    levelUpdate.put("scoreDetail", buildDiaryTeacherScoreDetail(diaryScore));
                    levelUpdate.put("totalScoreLockTime", new Date());
                }
                iCommonService.saveOneRecord(bizTableName, levelUpdate);
                if (nextLevel <= verifyTypeId) {
                    Integer nextRoleId = iVerifyProcessService.getVerifyRoleIdByLevel(bizJson, nextLevel);
                    if (nextRoleId != null && nextRoleId > 0) {
                        String nextVerifyUserId = iVerifyProcessService.GetVerifyUserId(nextRoleId, createUserId);
                        JSONObject newVerify = new JSONObject();
                        newVerify.put("relationId", bizId);
                        newVerify.put("processId", processId);
                        newVerify.put("createUserId", createUserId);
                        newVerify.put("verifyUserId", nextVerifyUserId);
                        newVerify.put("isAudit", Constant.AUDIT_STATUS.SUBMIT);
                        newVerify.put("tableName", bizTableName);
                        iCommonService.saveOneRecord("MainVerifyProcess", newVerify);
                    }
                } else {
                    // 最后一级 PASS 完成（nextLevel > verifyTypeId，即 currentVerifyTypeId > verifyTypeId，
                    // 符合 CLAUDE.md 中"审核完成判断用 currentVerifyTypeId > verifyTypeId"的约定）
                    if (!TABLE_MAIN_DIARY.equals(bizTableName)) {
                        // 非日志业务若 grade_config 有配置则触发总成绩计算与物化。
                        gradeConfigService.computeAndPersistTotalScore(bizId, bizTableName);
                    }
                }
            }
        } else if (isAudit != null && isAudit == Constant.AUDIT_STATUS.BACK && bizId != null) {
            JSONObject resetBiz = new JSONObject();
            resetBiz.put("id", bizId);
            if (clearSubmitOnBack) {
                resetBiz.put("submit", false);
            }
            resetBiz.put("currentVerifyTypeId", Constant.VERIFY_LEVEL.NO_VERIFY);
            iCommonService.saveOneRecord(bizTableName, resetBiz);
        }
        return saved;
    }

    private BigDecimal requireDiaryTeacherScore(JSONObject node) {
        BigDecimal score = node.getBigDecimal("score");
        if (score == null) {
            throw BaseResponse.parameterInvalid.error("日志审核通过必须填写 score");
        }
        if (score.compareTo(MIN_DIARY_SCORE) < 0 || score.compareTo(MAX_DIARY_SCORE) > 0) {
            throw BaseResponse.parameterInvalid.error("score 超出范围 [0, 100]");
        }
        return score;
    }

    private String buildDiaryTeacherScoreDetail(BigDecimal score) {
        JSONObject row = new JSONObject(true);
        row.put("levelOrder", 1);
        row.put("weight", MAX_DIARY_SCORE);
        row.put("maxScore", MAX_DIARY_SCORE);
        row.put("score", score);
        row.put("verifyUserId", String.valueOf(Base.getLoginUserId()));
        JSONArray detail = new JSONArray();
        detail.add(row);
        return detail.toJSONString();
    }

    private void assertCanAuditMainDiary(Object verifyObj) {
        if (verifyObj == null) {
            throw BaseResponse.parameterInvalid.error("审核记录不存在");
        }
        JSONObject verifyJson = FastJsonUtil.toJson(verifyObj);
        if (!TABLE_MAIN_DIARY.equals(verifyJson.getString("tableName"))) {
            return;
        }
        Integer isAudit = verifyJson.getInteger("isAudit");
        if (!Objects.equals(isAudit, Constant.AUDIT_STATUS.SUBMIT)) {
            throw BaseResponse.parameterInvalid.error("当前日志不是待审核状态，不能批阅");
        }
        Integer currentUserId = Base.getLoginUserId();
        if (!verifyUserIdContains(verifyJson.getString("verifyUserId"), currentUserId)) {
            throw BaseResponse.parameterInvalid.error("当前用户不是该日志的审核人，不能批阅");
        }
    }

    private boolean verifyUserIdContains(String verifyUserId, Integer userId) {
        if (verifyUserId == null || verifyUserId.isBlank() || userId == null) {
            return false;
        }
        String target = String.valueOf(userId);
        for (String part : verifyUserId.split("\\|")) {
            if (target.equals(part.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 单条审核推进（原 auditProcess 逻辑）。
     */
    private Object auditProcessOne(JSONObject node) {
        Integer isAudit = node.getInteger("isAudit");
        Integer Id = node.getInteger("id");

        // 提前读取记录以判断 tableName（save 不会改变 tableName，提前读不影响逻辑）
        Object verifyObj = Id != null ? iCommonService.getOneRecordById("MainVerifyProcess", Id) : null;
        String tableName = verifyObj != null ? FastJsonUtil.toJson(verifyObj).getString("tableName") : null;

        if ("MainEnterpriseInfo".equals(tableName)) {
            return enterpriseInfoService.audit(node, Base.getLoginUserId());
        }

        // 记录不存在（已被级联删除，如批量审核中岗位满额后 cancelPendingApplicationsIfPostFull 删除了后续项）
        if (Id != null && verifyObj == null) {
            logger.info("auditProcessOne: MainVerifyProcess id={} 不存在或已取消，跳过", Id);
            return null;
        }

        // 非限选题目：学生提交阶段不允许直接传审核通过，统一落到提交待审核。
        if (shouldForceSubmitForNonLimitedTitleSelection(isAudit, tableName, verifyObj)) {
            isAudit = Constant.AUDIT_STATUS.SUBMIT;
            node.put("isAudit", isAudit);
        }

        boolean noVerifyTitleAutoApproved = false;
        // 学生选题流程配置为“无需审核”时，提交后只能自动确认一个最终题目。
        if (shouldAutoApproveNoVerifyTitleSelectionSubmit(isAudit, tableName, verifyObj)) {
            isAudit = Constant.AUDIT_STATUS.PASS;
            node.put("isAudit", isAudit);
            node.put("verifyUserId", Constant.SYSTEM_AUDIT_NOTE.AUTO_PASS);
            markLimitedTitleSelectionAsFullyApproved(verifyObj);
            noVerifyTitleAutoApproved = true;
        }

        boolean limitedTitleAutoApproved = false;
        // 限选题目（isLimit=1）在学生提交时自动通过，避免继续进入人工审核链。
        if (!noVerifyTitleAutoApproved && shouldAutoApproveLimitedTitleSelectionSubmit(isAudit, tableName, verifyObj)) {
            isAudit = Constant.AUDIT_STATUS.PASS;
            node.put("isAudit", isAudit);
            node.put("verifyUserId", Constant.SYSTEM_AUDIT_NOTE.AUTO_PASS);
            markLimitedTitleSelectionAsFullyApproved(verifyObj);
            limitedTitleAutoApproved = true;
        }
        // 日志 / 打卡：业务表自带审核配置的多级流程（relationId = 业务主键）；打卡无 submit，退回只重置审核级别
        if (TABLE_MAIN_DIARY.equals(tableName)) {
            assertCanAuditMainDiary(verifyObj);
            return auditProcessMultiLevelRelationBiz(node, verifyObj, TABLE_MAIN_DIARY, isAudit, true);
        }
        if (isAudit != null && isAudit == 1 && Id != null && !limitedTitleAutoApproved && !noVerifyTitleAutoApproved) {
            // 审核通过：推进到下一级
            iVerifyProcessService.onVerifyProcessApproved(Id);
        }

        // 学生选题审核未通过：将原因落库到 RelTitleStudent.topicReasons
        if (isAudit != null && isAudit == Constant.AUDIT_STATUS.NOTPASS
                && TABLE_REL_TITLE_STUDENT.equals(tableName) && verifyObj != null) {
            Integer relationId = FastJsonUtil.toJson(verifyObj).getInteger("relationId");
            if (relationId != null) {
                JSONObject updateTopicReason = new JSONObject();
                updateTopicReason.put("id", relationId);
                updateTopicReason.put("topicReasons", node.getString("reason"));
                iCommonService.saveOneRecord(TABLE_REL_TITLE_STUDENT, updateTopicReason);
            }
        }

        // 保存当前审核记录（无论通过/退回，本条记录状态固化为历史）
        Object saved = iCommonService.saveOneRecord("MainVerifyProcess", node);

        if ("MainInternshipTermination".equals(tableName) && isAudit != null
                && isAudit == Constant.AUDIT_STATUS.PASS && verifyObj != null) {
            Integer terminationId = FastJsonUtil.toJson(verifyObj).getInteger("relationId");
            internshipTerminationService.afterAuditPassed(terminationId, getLoginUserId());
        }

        if (isAudit != null && (isAudit == 2 || isAudit == 3) && Id != null) {
            boolean isStuPost = "RelStuInternshipPost".equals(tableName);

            if (isStuPost) {
                // 学生报名岗位：退回/未通过 → 软删除选岗记录及其全部关联审核记录
                // nowPersonNum 仅在审核全部通过时递增，拒绝待审中的报名无需递减
                // 这样学生可以干净地重新选择同一岗位，不产生重复活跃记录
                Integer relationId = FastJsonUtil.toJson(verifyObj).getInteger("relationId");
                deleteVerifyProcessByRelationIdAndTableName(relationId, "RelStuInternshipPost");
                iCommonService.deleteRecordByDelflag("RelStuInternshipPost", relationId);
            } else if (isAudit == 3) {
                // 其他类型：退回时新建 isAudit=-1 的记录，允许用户修改后重新提交
                createPendingRecordAfterBack(Id);
            }
        }

        if ("MainInternshipTermination".equals(tableName) && isAudit != null
                && (isAudit == Constant.AUDIT_STATUS.NOTPASS || isAudit == Constant.AUDIT_STATUS.BACK)
                && verifyObj != null) {
            Integer terminationId = FastJsonUtil.toJson(verifyObj).getInteger("relationId");
            internshipTerminationService.afterAuditRejectedOrReturned(terminationId, isAudit);
        }

        return saved;
    }

    private boolean shouldAutoApproveNoVerifyTitleSelectionSubmit(Integer isAudit, String tableName, Object verifyObj) {
        if (isAudit == null || (isAudit != Constant.AUDIT_STATUS.SUBMIT && isAudit != Constant.AUDIT_STATUS.PASS)) {
            return false;
        }
        if (!TABLE_REL_TITLE_STUDENT.equals(tableName) || verifyObj == null) {
            return false;
        }
        Integer verifyTypeId = resolveTitleSelectionVerifyTypeId(verifyObj);
        return verifyTypeId != null && verifyTypeId < Constant.VERIFY_LEVEL.ONE_VERIFY;
    }

    private Integer resolveTitleSelectionVerifyTypeId(Object verifyObj) {
        if (verifyObj == null) {
            return null;
        }
        Integer processId = FastJsonUtil.toJson(verifyObj).getInteger("processId");
        if (processId == null) {
            return null;
        }
        Object processObj = iCommonService.getOneRecordById("RelProcessInternship", processId);
        if (processObj == null) {
            return null;
        }
        return FastJsonUtil.toJson(processObj).getInteger("verifyTypeId");
    }
    private boolean shouldAutoApproveLimitedTitleSelectionSubmit(Integer isAudit, String tableName, Object verifyObj) {
        // 学生端提交在不同入口可能传 0（提交待审）或 1（直接通过）；
        // 对限选题目统一按自动通过终态处理。
        if (isAudit == null || (isAudit != Constant.AUDIT_STATUS.SUBMIT && isAudit != Constant.AUDIT_STATUS.PASS)) {
            return false;
        }
        if (!TABLE_REL_TITLE_STUDENT.equals(tableName) || verifyObj == null) {
            return false;
        }
        Integer relationId = FastJsonUtil.toJson(verifyObj).getInteger("relationId");
        if (relationId == null) {
            return false;
        }
        Integer isLimit = resolveTitleLimitFlagByRelationId(relationId);
        return isLimit != null && isLimit == 1;
    }

    private Integer resolveTitleLimitFlagByRelationId(Integer relationId) {
        if (relationId == null) {
            return null;
        }
        Object relTitleStudentObj = iCommonService.getOneRecordById("RelTitleStudent", relationId);
        if (relTitleStudentObj == null) {
            return null;
        }
        Integer titleId = FastJsonUtil.toJson(relTitleStudentObj).getInteger("titleId");
        if (titleId == null) {
            return null;
        }
        Object relTitleTeacherObj = iCommonService.getOneRecordById("RelTitleTeacher", titleId);
        if (relTitleTeacherObj == null) {
            return null;
        }
        return FastJsonUtil.toJson(relTitleTeacherObj).getInteger("isLimit");
    }

    private boolean shouldForceSubmitForNonLimitedTitleSelection(Integer isAudit, String tableName, Object verifyObj) {
        if (isAudit == null || isAudit != Constant.AUDIT_STATUS.PASS) {
            return false;
        }
        if (!TABLE_REL_TITLE_STUDENT.equals(tableName) || verifyObj == null) {
            return false;
        }
        JSONObject verifyJson = FastJsonUtil.toJson(verifyObj);
        Integer beforeAudit = verifyJson.getInteger("isAudit");
        // 仅拦截“待提交(-1) -> 通过(1)”这类学生提交越权场景；
        // 老师审核通常是“待审核(0) -> 通过(1)”，不应被拦截。
        if (beforeAudit == null || beforeAudit != Constant.AUDIT_STATUS.SAVE) {
            return false;
        }
        Integer relationId = verifyJson.getInteger("relationId");
        Integer isLimit = resolveTitleLimitFlagByRelationId(relationId);
        return isLimit != null && isLimit != 1;
    }

    private void markLimitedTitleSelectionAsFullyApproved(Object verifyObj) {
        if (verifyObj == null) {
            return;
        }
        JSONObject verifyJson = FastJsonUtil.toJson(verifyObj);
        Integer relationId = verifyJson.getInteger("relationId");
        Integer processId = verifyJson.getInteger("processId");
        if (relationId == null || processId == null) {
            return;
        }

        Object processObj = iCommonService.getOneRecordById("RelProcessInternship", processId);
        if (processObj == null) {
            return;
        }
        Integer verifyTypeId = FastJsonUtil.toJson(processObj).getInteger("verifyTypeId");
        if (verifyTypeId == null) {
            verifyTypeId = Constant.VERIFY_LEVEL.ONE_VERIFY;
        }

        JSONObject updateRelTitleStudent = new JSONObject();
        updateRelTitleStudent.put("id", relationId);
        updateRelTitleStudent.put("currentVerifyTypeId", verifyTypeId + 1);
        RelTitleStudent current = relTitleStudentDao.getByIdAndIsDeletedFalse(relationId);
        Integer internshipId = resolveTitleStudentInternshipId(current);
        if (current != null && internshipId != null) {
            synchronized (titleStudentLock(internshipId)) {
                validateNoFinalTitleConflict(current.getStuId(), internshipId, current.getTitleId(), relationId);
                updateRelTitleStudent.put("internshipId", internshipId);
                updateRelTitleStudent.put("isFinal", 1);
                if (current.getSourceType() == null || current.getSourceType().isBlank()) {
                    updateRelTitleStudent.put("sourceType", TITLE_SOURCE_STUDENT_CANDIDATE);
                }
                updateRelTitleStudent.put("confirmedBy", resolveCurrentUserId());
                updateRelTitleStudent.put("confirmedTime", new Date());
                iCommonService.saveOneRecord(TABLE_REL_TITLE_STUDENT, updateRelTitleStudent);
                releaseOtherCandidatesOfStudent(current.getStuId(), internshipId, relationId);
                releaseOtherCandidatesOfTitle(current.getTitleId(), relationId);
                return;
            }
        }
        iCommonService.saveOneRecord(TABLE_REL_TITLE_STUDENT, updateRelTitleStudent);
    }

    @Override
    public Object createRelTitleStudent(JSONObject node) {
        if (node == null) {
            throw BaseResponse.parameterInvalid.error("node cannot be null");
        }
        Integer titleId = node.getInteger("titleId");
        Integer stuId = node.getInteger("stuId");
        if (titleId == null || stuId == null) {
            throw BaseResponse.parameterInvalid.error("titleId and stuId cannot be null");
        }
        RelTitleTeacher title = relTitleTeacherDao.getByIdAndIsDeletedFalse(titleId);
        if (title == null || title.getInternshipId() == null) {
            throw BaseResponse.parameterInvalid.error("title does not exist or has no internship");
        }
        Integer internshipId = title.getInternshipId();
        boolean teacherAssign = isTeacherAssignedTitleSelection(node);
        Object lock = titleStudentLock(internshipId);
        synchronized (lock) {
            validateNoFinalTitleConflict(stuId, internshipId, titleId, null);
            List<RelTitleStudent> sameRows = relTitleStudentDao.findByTitleIdAndStuIdAndIsDeletedFalse(titleId, stuId);
            if (sameRows != null && !sameRows.isEmpty()) {
                if (!teacherAssign) {
                    throw BaseResponse.moreInfoError.error("student already selected this title");
                }
                RelTitleStudent existing = sameRows.get(0);
                return confirmTeacherAssignedExistingCandidate(existing.getId(), internshipId);
            }
            if (!node.containsKey("topicReasons")) {
                node.put("topicReasons", null);
            }
            node.put("internshipId", internshipId);
            node.put("sourceType", teacherAssign ? TITLE_SOURCE_TEACHER_ASSIGN : TITLE_SOURCE_STUDENT_CANDIDATE);
            node.put("isFinal", teacherAssign ? 1 : 0);
            if (!node.containsKey("currentVerifyTypeId")) {
                node.put("currentVerifyTypeId", Constant.VERIFY_LEVEL.NO_VERIFY);
            }
            if (teacherAssign) {
                node.put("currentVerifyTypeId", Constant.VERIFY_LEVEL.NO_VERIFY);
                node.put("confirmedBy", resolveCurrentUserId());
                node.put("confirmedTime", new Date());
            }

            Object saved = iCommonService.saveOneRecord(TABLE_REL_TITLE_STUDENT, node);
            JSONObject savedJson = FastJsonUtil.toJson(saved);
            Integer relationId = savedJson.getInteger("id");
            if (teacherAssign) {
                releaseOtherCandidatesOfStudent(stuId, internshipId, relationId);
                releaseOtherCandidatesOfTitle(titleId, relationId);
            } else {
                createFirstVerifyProcessForRelTeacherStudent(relationId, internshipId, stuId, TABLE_REL_TITLE_STUDENT);
            }
            return saved;
        }
    }

    private Object confirmTeacherAssignedExistingCandidate(Integer relationId, Integer internshipId) {
        RelTitleStudent existing = relTitleStudentDao.getByIdAndIsDeletedFalse(relationId);
        if (existing == null) {
            throw BaseResponse.parameterInvalid.error("title selection does not exist");
        }
        validateNoFinalTitleConflict(existing.getStuId(), internshipId, existing.getTitleId(), relationId);
        JSONObject update = new JSONObject();
        update.put("id", relationId);
        update.put("sourceType", TITLE_SOURCE_TEACHER_ASSIGN);
        update.put("isFinal", 1);
        update.put("internshipId", internshipId);
        update.put("currentVerifyTypeId", Constant.VERIFY_LEVEL.NO_VERIFY);
        update.put("confirmedBy", resolveCurrentUserId());
        update.put("confirmedTime", new Date());
        Object saved = iCommonService.saveOneRecord(TABLE_REL_TITLE_STUDENT, update);
        deleteVerifyProcessByRelationIdAndTableName(relationId, TABLE_REL_TITLE_STUDENT);
        releaseOtherCandidatesOfStudent(existing.getStuId(), internshipId, relationId);
        releaseOtherCandidatesOfTitle(existing.getTitleId(), relationId);
        return saved;
    }

    private boolean isTeacherAssignedTitleSelection(JSONObject node) {
        String sourceType = normalizeTitleSourceType(node.getString("sourceType"));
        Integer isFinal = node.getInteger("isFinal");
        return TITLE_SOURCE_TEACHER_ASSIGN.equals(sourceType)
                || (isFinal != null && isFinal == 1);
    }

    private String normalizeTitleSourceType(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) {
            return TITLE_SOURCE_STUDENT_CANDIDATE;
        }
        String normalized = sourceType.trim().toUpperCase(Locale.ROOT);
        return TITLE_SOURCE_TEACHER_ASSIGN.equals(normalized)
                ? TITLE_SOURCE_TEACHER_ASSIGN
                : TITLE_SOURCE_STUDENT_CANDIDATE;
    }

    private Object titleStudentLock(Integer internshipId) {
        String key = "title-student:" + (internshipId == null ? "none" : internshipId);
        return TITLE_STUDENT_LOCKS.computeIfAbsent(key, k -> new Object());
    }

    private Integer resolveTitleStudentInternshipId(RelTitleStudent row) {
        if (row == null) {
            return null;
        }
        if (row.getInternshipId() != null) {
            return row.getInternshipId();
        }
        if (row.getTitleId() == null) {
            return null;
        }
        RelTitleTeacher title = relTitleTeacherDao.getByIdAndIsDeletedFalse(row.getTitleId());
        return title != null ? title.getInternshipId() : null;
    }

    private Integer resolveCurrentUserId() {
        try {
            Integer userId = Base.getLoginUserId();
            return userId != null ? userId : 0;
        } catch (Exception ex) {
            return 0;
        }
    }

    private void validateNoFinalTitleConflict(Integer stuId, Integer internshipId, Integer titleId, Integer keepRelationId) {
        if (stuId == null || internshipId == null || titleId == null) {
            throw BaseResponse.parameterInvalid.error("stuId, internshipId and titleId cannot be null");
        }
        boolean studentHasFinal = relTitleStudentDao
                .findByStuIdAndInternshipIdAndIsFinalAndIsDeletedFalse(stuId, internshipId, 1)
                .stream()
                .anyMatch(row -> !row.getId().equals(keepRelationId));
        if (studentHasFinal) {
            throw BaseResponse.moreInfoError.error("student already has a final title in this internship");
        }
        boolean titleHasFinal = relTitleStudentDao
                .findByTitleIdAndIsFinalAndIsDeletedFalse(titleId, 1)
                .stream()
                .anyMatch(row -> !row.getId().equals(keepRelationId));
        if (titleHasFinal) {
            throw BaseResponse.moreInfoError.error("title already has a final student");
        }
    }

    private void releaseOtherCandidatesOfStudent(Integer stuId, Integer internshipId, Integer keepRelationId) {
        if (stuId == null || internshipId == null) {
            return;
        }
        for (RelTitleStudent row : relTitleStudentDao.findByStuIdAndInternshipIdAndIsDeletedFalse(stuId, internshipId)) {
            releaseCandidate(row, keepRelationId);
        }
    }

    private void releaseOtherCandidatesOfTitle(Integer titleId, Integer keepRelationId) {
        if (titleId == null) {
            return;
        }
        for (RelTitleStudent row : relTitleStudentDao.findByTitleIdAndIsDeletedFalse(titleId)) {
            releaseCandidate(row, keepRelationId);
        }
    }

    private void releaseCandidate(RelTitleStudent row, Integer keepRelationId) {
        if (row == null || row.getId() == null || row.getId().equals(keepRelationId)) {
            return;
        }
        if (row.getIsFinal() != null && row.getIsFinal() == 1) {
            return;
        }
        deleteVerifyProcessByRelationIdAndTableName(row.getId(), TABLE_REL_TITLE_STUDENT);
        iCommonService.deleteRecordByDelflag(TABLE_REL_TITLE_STUDENT, row.getId());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object confirmStudentTopicSelection(JSONObject node) {
        if (node == null) {
            throw BaseResponse.parameterInvalid.error("node cannot be null");
        }
        Integer relationId = node.getInteger("relationId");
        if (relationId == null) {
            relationId = node.getInteger("relTitleStudentId");
        }
        if (relationId == null) {
            throw BaseResponse.parameterInvalid.error("relationId cannot be null");
        }
        RelTitleStudent selection = relTitleStudentDao.getByIdAndIsDeletedFalse(relationId);
        if (selection == null) {
            throw BaseResponse.parameterInvalid.error("title selection does not exist");
        }
        Integer internshipId = resolveTitleStudentInternshipId(selection);
        assertOptionalEquals(node.getInteger("stuId"), selection.getStuId(), "stuId");
        assertOptionalEquals(node.getInteger("titleId"), selection.getTitleId(), "titleId");
        assertOptionalEquals(node.getInteger("internshipId"), internshipId, "internshipId");
        validateNoFinalTitleConflict(selection.getStuId(), internshipId, selection.getTitleId(), relationId);

        JSONObject searchKeys = new JSONObject();
        searchKeys.put("relationId", relationId);
        searchKeys.put("tableName", TABLE_REL_TITLE_STUDENT);
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                TABLE_MAIN_VERIFY_PROCESS, searchKeys, null, Sort.by(Sort.Direction.DESC, "id"), 1, 1);
        if (page == null || page.getContent() == null || page.getContent().isEmpty()) {
            if (selection.getIsFinal() != null && selection.getIsFinal() == 1) {
                return selection;
            }
            throw BaseResponse.moreInfoError.error("title selection has no audit process");
        }
        JSONObject verifyProcessJson = FastJsonUtil.toJson(page.getContent().get(0));
        Integer currentAudit = verifyProcessJson.getInteger("isAudit");
        if (currentAudit != null && currentAudit == Constant.AUDIT_STATUS.PASS
                && selection.getIsFinal() != null && selection.getIsFinal() == 1) {
            return selection;
        }
        if (currentAudit == null || currentAudit != Constant.AUDIT_STATUS.SUBMIT) {
            throw BaseResponse.parameterInvalid.error("only submitted title candidates can be confirmed");
        }
        JSONObject auditNode = new JSONObject();
        auditNode.put("id", verifyProcessJson.getInteger("id"));
        auditNode.put("isAudit", Constant.AUDIT_STATUS.PASS);
        String reason = node.getString("reason");
        if (reason != null) {
            auditNode.put("reason", reason);
        }
        String verifyUserId = node.getString("verifyUserId");
        if (verifyUserId != null && !verifyUserId.isBlank()) {
            auditNode.put("verifyUserId", verifyUserId);
        }
        return auditProcess(auditNode);
    }

    private void assertOptionalEquals(Integer expected, Integer actual, String fieldName) {
        if (expected != null && actual != null && !expected.equals(actual)) {
            throw BaseResponse.parameterInvalid.error(fieldName + " does not match title selection");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object getLatestRejectedTitleSelection(Integer stuId) {
        if (stuId == null) {
            throw BaseResponse.parameterInvalid.error("stuId 不能为空");
        }
        JSONObject sk = new JSONObject();
        sk.put("stuId", stuId);
        sk.put("isAudit", Constant.AUDIT_STATUS.NOTPASS);
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                "ViewVerifyProcessRelTitleStudentMerge", sk, null,
                Sort.by(Sort.Direction.DESC, "vpUpdateTime").and(Sort.by(Sort.Direction.DESC, "id")),
                1, 1);
        List<Object> list = page.getContent();
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object acknowledgeRejectedTitleSelection(Integer relationId, Integer stuId) {
        if (relationId == null || stuId == null) {
            throw BaseResponse.parameterInvalid.error("relationId、stuId 不能为空");
        }
        Object relObj = iCommonService.getOneRecordById(TABLE_REL_TITLE_STUDENT, relationId);
        if (relObj == null) {
            throw BaseResponse.parameterInvalid.error("选题记录不存在");
        }
        JSONObject relJson = FastJsonUtil.toJson(relObj);
        Integer relStuId = relJson.getInteger("stuId");
        if (relStuId == null || !relStuId.equals(stuId)) {
            throw BaseResponse.parameterInvalid.error("该记录不属于当前学生");
        }

        JSONObject sk = new JSONObject();
        sk.put("relationId", relationId);
        sk.put("stuId", stuId);
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                "ViewVerifyProcessRelTitleStudentMerge", sk, null,
                Sort.by(Sort.Direction.DESC, "vpUpdateTime").and(Sort.by(Sort.Direction.DESC, "id")),
                1, 1);
        List<Object> list = page.getContent();
        if (list == null || list.isEmpty()) {
            throw BaseResponse.parameterInvalid.error("未找到该选题审核记录");
        }
        Integer isAudit = FastJsonUtil.toJson(list.get(0)).getInteger("isAudit");
        if (isAudit == null || isAudit != Constant.AUDIT_STATUS.NOTPASS) {
            throw BaseResponse.parameterInvalid.error("仅审核不通过的选题记录允许确认并删除");
        }

        deleteVerifyProcessByRelationIdAndTableName(relationId, TABLE_REL_TITLE_STUDENT);
        iCommonService.deleteRecordByDelflag(TABLE_REL_TITLE_STUDENT, relationId);
        return "确认成功，已清理该条不通过选题记录";
    }

    /**
     * 审核退回后，回退 currentVerifyTypeId 并新建一条 isAudit=-1 的记录，等待用户重新提交。
     * 用户重新提交时只需将该记录的 isAudit 改为 0（通过 auditProcess 接口即可），
     * 无需专门的 resubmit 接口。
     */
    private void createPendingRecordAfterBack(Integer rejectedProcessId) {
        // 1. 读取退回记录（此时 save 已完成，isAudit 已更新为退回状态）
        Object verifyProcessObj = iCommonService.getOneRecordById("MainVerifyProcess", rejectedProcessId);
        if (verifyProcessObj == null) {
            logger.warn("退回后新建记录失败：未找到审核记录 {}", rejectedProcessId);
            return;
        }
        JSONObject verifyProcessJson = FastJsonUtil.toJson(verifyProcessObj);

        Integer relationId  = verifyProcessJson.getInteger("relationId");
        Integer createUserId = verifyProcessJson.getInteger("createUserId");
        Integer processId   = verifyProcessJson.getInteger("processId");
        String  tableName   = verifyProcessJson.getString("tableName");

        // 2. 读取流程配置（使用 processId 查 RelProcessInternship）
        Object relObj = iCommonService.getOneRecordById("RelProcessInternship", processId);
        if (relObj == null) {
            logger.warn("退回后新建记录失败：未找到流程配置 {}", processId);
            return;
        }
        JSONObject relJson = FastJsonUtil.toJson(relObj);

        // 3. 从对应的业务实体表获取 currentVerifyTypeId（每个审核条目独立跟踪审核级别）
        Object entityObj = iCommonService.getOneRecordById(tableName, relationId);
        if (entityObj == null) {
            logger.warn("退回后新建记录失败：未找到业务实体 {} id={}", tableName, relationId);
            return;
        }
        JSONObject entityJson = FastJsonUtil.toJson(entityObj);
        Integer currentVerifyTypeId = entityJson.getInteger("currentVerifyTypeId");

        // 4. 退回时 currentVerifyTypeId - 1，回退到上一级审核
        //    但不低于 2（第一级审核的初始值），第一级退回时保持原级别
        if (currentVerifyTypeId != null && currentVerifyTypeId > 2) {
            currentVerifyTypeId -= 1;
            JSONObject updateJson = new JSONObject();
            updateJson.put("id", relationId);
            updateJson.put("currentVerifyTypeId", currentVerifyTypeId);
            iCommonService.saveOneRecord(tableName, updateJson);
        }

        // 5. 重新计算回退后级别的审核人
        Integer verifyRoleId = getVerifyRoleIdByLevel(relJson, currentVerifyTypeId);
        Integer internshipIdForBack = relJson.getInteger("internshipId");
        String verifyUserId = iVerifyProcessService.GetVerifyUserId(verifyRoleId, createUserId, internshipIdForBack);

        // 5. 新建待提交记录（isAudit=-1）
        JSONObject newVerifyJson = new JSONObject();
        newVerifyJson.put("relationId", relationId);
        newVerifyJson.put("processId", processId);
        newVerifyJson.put("createUserId", createUserId);
        newVerifyJson.put("isAudit", -1);
        newVerifyJson.put("reason", "");
        newVerifyJson.put("tableName", tableName);
        newVerifyJson.put("verifyUserId", verifyUserId);
        iCommonService.saveOneRecord("MainVerifyProcess", newVerifyJson);
    }

    /**
     * 根据审核级别从流程记录JSON中获取对应的审核角色ID
     */
    private Integer getVerifyRoleIdByLevel(JSONObject relJson, Integer verifyLevel) {
        if (relJson == null || verifyLevel == null) {
            return null;
        }
        switch (verifyLevel) {
            case 2:
                return relJson.getInteger("verifyFirstRoleId");
            case 3:
                return relJson.getInteger("verifySecondRoleId");
            case 4:
                return relJson.getInteger("verifyThirdRoleId");
            case 5:
                return relJson.getInteger("verifyFourthRoleId");
            case 6:
                return relJson.getInteger("verifyFifthRoleId");
            default:
                return null;
        }
    }

    @Override
    public Object deleteNewInternship(List<Integer> internshipIds) {
        if (internshipIds == null || internshipIds.isEmpty()) {
            throw BaseResponse.parameterInvalid.error("实习项目ID列表不能为空");
        }

        // // (1) 检查所有id是否在审核流程中，这个不检查了，因为前端已经检查过了。
        // for (Integer internshipId : internshipIds) {
        //     if (internshipId == null) {
        //         continue;
        //     }
        //     JSONObject searchKeys = new JSONObject();
        //     searchKeys.put("internshipId", internshipId);
        //     // 查询时需要考虑 isDeleted，所以使用默认的 getSomeRecords（会自动过滤 isDeleted=false）
        //     @SuppressWarnings("unchecked")
        //     Page<Object> verifyProcessPage = (Page<Object>) iCommonService.getSomeRecords(
        //             "ViewVerifyProcessInternship", searchKeys, null, Sort.unsorted());
        //     List<Object> verifyProcessList = verifyProcessPage.getContent();

        //     // 如果存在审核流程记录，返回错误信息
        //     if (verifyProcessList != null && !verifyProcessList.isEmpty()) {
        //         throw BaseResponse.parameterInvalid.error("当前项目已经进入审核流程，无法删除");
        //     }
        // }
        // (2) 批量删除 MainInternship 中对应的记录（伪删除）
        // 过滤掉 null 值
        List<Integer> validIds = internshipIds.stream()
                .filter(id -> id != null)
                .collect(java.util.stream.Collectors.toList());
        if (!validIds.isEmpty()) {
            iCommonService.deleteSomeRecords("MainInternship", validIds);
        }

        // (3) 批量删除 RelProcessInternship 中所有 internshipId 是对应 Id 的记录（伪删除）
        JSONObject searchKeys = new JSONObject();
        searchKeys.put("internshipId", String.join(",", validIds.stream()
                .map(String::valueOf)
                .collect(Collectors.toList())));
        Map<String, String> regMap = new HashMap<>();
        regMap.put("internshipId", Constant.IN); // internshipId IN (列表)
        iCommonService.deleteSomeRecords("RelProcessInternship", searchKeys, regMap, null);

        // (4) 批量删除 MainVerifyProcess 中 relationId 在 validIds 集合且 tableName 是 "MainInternship" 的所有记录（伪删除）
        JSONObject verifySearchKeys = new JSONObject();
        verifySearchKeys.put("relationId", String.join(",", validIds.stream()
                .map(String::valueOf)
                .collect(Collectors.toList())));
        verifySearchKeys.put("tableName", "MainInternship");
        Map<String, String> verifyRegMap = new HashMap<>();
        verifyRegMap.put("relationId", Constant.IN); // relationId IN (列表)
        iCommonService.deleteSomeRecords("MainVerifyProcess", verifySearchKeys, verifyRegMap, null);

        return "删除成功";
    }

    // ==================== 老师申报题目（保存/提交、需审核与无需审核） ====================

    private static final String TABLE_REL_TEACHER_STUDENT = "RelTeacherStudent";
    private static final String TABLE_REL_TITLE_TEACHER = "RelTitleTeacher";
    private static final String TABLE_REL_TITLE_STUDENT = "RelTitleStudent";
    private static final String TABLE_MAIN_VERIFY_PROCESS = "MainVerifyProcess";

    @Override
    public void createFirstVerifyProcessForRelTeacherStudent(Integer relationId, Integer internshipId, Integer createUserId, String tableName) {
        if (relationId == null || internshipId == null || createUserId == null) {
            logger.warn("createFirstVerifyProcessForRelTeacherStudent 参数不完整: relationId={}, internshipId={}, createUserId={}", relationId, internshipId, createUserId);
            return;
        }
        String targetTableName = TABLE_REL_TEACHER_STUDENT;
        if (TABLE_REL_TITLE_TEACHER.equals(tableName)) {
            targetTableName = TABLE_REL_TITLE_TEACHER;
        } else if (TABLE_REL_TITLE_STUDENT.equals(tableName)) {
            targetTableName = TABLE_REL_TITLE_STUDENT;
        } else if (tableName != null && !TABLE_REL_TEACHER_STUDENT.equals(tableName)) {
            logger.warn("createFirstVerifyProcessForRelTeacherStudent 未识别 tableName={}, 使用默认 {}", tableName, TABLE_REL_TEACHER_STUDENT);
        }
        String processTypeCode = Constant.PROCESS_TYPE.INTERNAL_TEACHER_DECLARE_TOPIC;
        if (TABLE_REL_TITLE_STUDENT.equals(targetTableName)) {
            processTypeCode = Constant.PROCESS_TYPE.INTERNAL_STUDENT_TEACHER_MATCH;
        }
        // 1. 获取流程配置（ViewRelProcessInternship 的 id 即 RelProcessInternship.id）
        Object processObj = iVerifyProcessService.GetInternshipProcess(internshipId, processTypeCode);
        if (processObj == null) {
            logger.warn("未找到实习流程配置, internshipId={}, processTypeCode={}", internshipId, processTypeCode);
            return;
        }
        JSONObject processJson = FastJsonUtil.toJson(processObj);
        Integer processId = processJson.getInteger("id");
        if (processId == null) {
            return;
        }
        Integer verifyTypeId = processJson.getInteger("verifyTypeId");
        boolean needsVerify = verifyTypeId != null && verifyTypeId >= Constant.VERIFY_LEVEL.ONE_VERIFY;
        boolean titleStudentCandidate = TABLE_REL_TITLE_STUDENT.equals(targetTableName);

        // Keep the business row aligned with the process definition so the first approval
        // advances from the real starting level instead of from NO_VERIFY.
        JSONObject levelUpdate = new JSONObject();
        levelUpdate.put("id", relationId);
        levelUpdate.put("currentVerifyTypeId",
                needsVerify ? Constant.VERIFY_LEVEL.ONE_VERIFY : Constant.VERIFY_LEVEL.NO_VERIFY);
        iCommonService.saveOneRecord(targetTableName, levelUpdate);

        String verifyUserId;
        int isAudit;
        if (needsVerify) {
            Integer verifyFirstRoleId = processJson.getInteger("verifyFirstRoleId");
            verifyUserId = iVerifyProcessService.GetVerifyUserId(verifyFirstRoleId, createUserId, internshipId);
            isAudit = Constant.AUDIT_STATUS.SAVE;
        } else if (titleStudentCandidate) {
            verifyUserId = "";
            isAudit = Constant.AUDIT_STATUS.SAVE;
        } else {
            verifyUserId = Constant.SYSTEM_AUDIT_NOTE.AUTO_PASS;
            isAudit = Constant.AUDIT_STATUS.PASS;
        }

        JSONObject verifyJson = new JSONObject();
        verifyJson.put("relationId", relationId);
        verifyJson.put("processId", processId);
        verifyJson.put("createUserId", createUserId);
        verifyJson.put("verifyUserId", verifyUserId);
        verifyJson.put("isAudit", isAudit);
        verifyJson.put("reason", "");
        verifyJson.put("tableName", targetTableName);
        iCommonService.saveOneRecord(TABLE_MAIN_VERIFY_PROCESS, verifyJson);

    }

    @Override
    public void deleteVerifyProcessByRelationIdAndTableName(Integer relationId, String tableName) {
        if (relationId == null || tableName == null || tableName.isEmpty()) {
            return;
        }
        JSONObject searchKeys = new JSONObject();
        searchKeys.put("relationId", relationId);
        searchKeys.put("tableName", tableName);
        @SuppressWarnings("unchecked")
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                TABLE_MAIN_VERIFY_PROCESS, searchKeys, null, Sort.unsorted(), 1, 100);
        List<Object> content = page.getContent();
        if (content != null) {
            for (Object obj : content) {
                JSONObject verifyProcessJson = FastJsonUtil.toJson(obj);
                Integer id = verifyProcessJson.getInteger("id");
                if (id != null) {
                    iCommonService.deleteRecordByDelflag(TABLE_MAIN_VERIFY_PROCESS, id);
                }
            }
        }
    }
}

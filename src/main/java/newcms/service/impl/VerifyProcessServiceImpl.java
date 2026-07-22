package newcms.service.impl;

import jakarta.annotation.Resource;
import newcms.base.Base;
import newcms.base.BaseResponse;
import newcms.base.Constant;
import newcms.entity.db.MainInternshipPost;
import newcms.entity.db.MainVerifyProcess;
import newcms.entity.db.BaseEnterpriseVerifyConfig;
import newcms.entity.db.RelStuInternshipPost;
import newcms.entity.db.RelTeacherStudent;
import newcms.entity.db.RelTitleStudent;
import newcms.entity.db.RelTitleTeacher;
import newcms.repository.db.MainInternshipPostDao;
import newcms.repository.db.BaseEnterpriseVerifyConfigDao;
import newcms.repository.db.RelStuInternshipPostDao;
import newcms.repository.db.RelTeacherStudentDao;
import newcms.repository.db.RelTitleStudentDao;
import newcms.repository.db.RelTitleTeacherDao;
import newcms.service.ICommonService;
import newcms.service.IVerifyProcessService;
import newcms.utils.FastJsonUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 审核流程服务实现类
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class VerifyProcessServiceImpl extends Base implements IVerifyProcessService {
    private static final String TABLE_REL_TITLE_STUDENT = "RelTitleStudent";
    private static final String TABLE_REL_TEACHER_STUDENT = "RelTeacherStudent";
    private static final String TABLE_MAIN_VERIFY_PROCESS = "MainVerifyProcess";
    private static final String TITLE_SOURCE_STUDENT_CANDIDATE = "STUDENT_CANDIDATE";
    private static final ConcurrentHashMap<String, Object> TITLE_CONFIRM_LOCKS = new ConcurrentHashMap<>();

    @Resource
    private ICommonService iCommonService;

    @Resource
    private MainInternshipPostDao mainInternshipPostDao;

    @Resource
    private RelStuInternshipPostDao relStuInternshipPostDao;

    @Resource
    private RelTeacherStudentDao relTeacherStudentDao;

    @Resource
    private RelTitleStudentDao relTitleStudentDao;

    @Resource
    private RelTitleTeacherDao relTitleTeacherDao;

    @Resource
    private BaseEnterpriseVerifyConfigDao baseEnterpriseVerifyConfigDao;

    @Override
    @SuppressWarnings("unchecked")
    public Object GetInternshipProcess(Integer internshipId, String processTypeCode) {
        if (internshipId == null) {
            throw BaseResponse.parameterInvalid.error("实习项目ID不能为空");
        }

        // 如果 processTypeCode 为空，则使用默认值
        if (processTypeCode == null || processTypeCode.trim().isEmpty()) {
            processTypeCode = Constant.PROCESS_TYPE.INTERNSHIP_PLAN_MAKE;
        }

        // 查找流程关联记录（取第一条）
        JSONObject searchKeys = new JSONObject();
        searchKeys.put("internshipId", internshipId);
        searchKeys.put("processTypeCode", processTypeCode);
        Page<Object> relPage = (Page<Object>) iCommonService.getSomeRecords(
                "ViewRelProcessInternship", searchKeys, null,
                Sort.by(Sort.Direction.ASC, "theOrder"), 1, 1);
        List<Object> relList = relPage.getContent();
        if (relList == null || relList.isEmpty()) {
            throw BaseResponse.moreInfoError.error("未找到实习项目的流程配置，请先创建流程模板");
        }
        return relList.get(0);
    }

    @Override
    public String GetVerifyUserId(Integer verifyFirstRoleId, Integer createUserId) {
        return GetVerifyUserId(verifyFirstRoleId, createUserId, null, null);
    }

    @Override
    public String GetVerifyUserId(Integer verifyFirstRoleId, Integer createUserId, Integer internshipId) {
        return GetVerifyUserId(verifyFirstRoleId, createUserId, internshipId, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public String GetVerifyUserId(Integer verifyFirstRoleId, Integer createUserId, Integer internshipId,
                                   Integer hostSchoolScopeId) {
        if (verifyFirstRoleId == null || verifyFirstRoleId == 0) {
            return "";
        }
        if (createUserId == null) {
            throw BaseResponse.parameterInvalid.error("创建用户ID不能为空");
        }

        // (1) 获取提交人的 schoolId
        Object currentUserObj = iCommonService.getOneRecordById("ViewBaseUser", createUserId);
        if (currentUserObj == null) {
            throw BaseResponse.moreInfoError.error("未找到当前用户信息");
        }
        Integer userSchoolId = FastJsonUtil.toJson(currentUserObj).getInteger("schoolId");

        // (1.1) 收集需要搜索的 schoolId 集合
        //       若调用方传入 hostSchoolScopeId（如企业申报单上的合作学校根部门 id），则优先只在该校范围内找人，
        //       避免企业管理员 view_base_user.schoolId 落在企业组织节点上导致与学校侧审核人无法求交。
        Set<Integer> schoolIds = new java.util.LinkedHashSet<>();
        if (hostSchoolScopeId != null) {
            schoolIds.add(hostSchoolScopeId);
        } else if (userSchoolId != null) {
            schoolIds.add(userSchoolId);
        }
        if (internshipId != null) {
            Object internshipObj = iCommonService.getOneRecordById("MainInternship", internshipId);
            if (internshipObj != null) {
                Integer creatorId = FastJsonUtil.toJson(internshipObj).getInteger("creatorId");
                if (creatorId != null) {
                    Object creatorObj = iCommonService.getOneRecordById("ViewBaseUser", creatorId);
                    if (creatorObj != null) {
                        Integer creatorSchoolId = FastJsonUtil.toJson(creatorObj).getInteger("schoolId");
                        if (creatorSchoolId != null) {
                            schoolIds.add(creatorSchoolId);
                        }
                    }
                }
            }

            // (1.2) 收集该实习项目下所有岗位所属企业的 schoolId
            //       学生报名岗位时，审核人可能是企业管理员，其 schoolId 与学生/学校不同
            JSONObject postSearchKeys = new JSONObject();
            postSearchKeys.put("internshipId", internshipId);
            Page<Object> postPage = (Page<Object>) iCommonService.getSomeRecords(
                    "MainInternshipPost", postSearchKeys, null, Sort.unsorted(), 1, 10000);
            Set<Integer> postTypeIds = postPage.getContent().stream()
                    .map(FastJsonUtil::toJson)
                    .map(json -> json.getInteger("postTypeId"))
                    .filter(id -> id != null)
                    .collect(Collectors.toSet());
            for (Integer postTypeId : postTypeIds) {
                Object postTypeObj = iCommonService.getOneRecordById("ViewBasePostType", postTypeId);
                if (postTypeObj != null) {
                    Integer companyId = FastJsonUtil.toJson(postTypeObj).getInteger("companyId");
                    if (companyId != null) {
                        Object deptObj = iCommonService.getOneRecordById("ViewBaseDepartment", companyId);
                        if (deptObj != null) {
                            Integer companySchoolId = FastJsonUtil.toJson(deptObj).getInteger("schoolId");
                            if (companySchoolId != null) {
                                schoolIds.add(companySchoolId);
                            }
                        }
                    }
                }
            }
        }

        logger.info("GetVerifyUserId: verifyRoleId={}, createUserId={}, internshipId={}, hostSchoolScopeId={}, 搜索schoolIds={}",
                verifyFirstRoleId, createUserId, internshipId, hostSchoolScopeId, schoolIds);

        if (schoolIds.isEmpty()) {
            logger.warn("GetVerifyUserId: createUserId={} 无法确定任何 schoolId", createUserId);
            return "";
        }

        Set<Integer> candidateUserIds = buildCandidateUserIdsForSchools(schoolIds);
        List<Integer> verifyUserIds = intersectVerifyRole(verifyFirstRoleId, candidateUserIds);

        if (verifyUserIds.isEmpty() && hostSchoolScopeId != null) {
            BaseEnterpriseVerifyConfig cfg = baseEnterpriseVerifyConfigDao.findTopByIsDeletedFalseOrderByIdDesc();
            Integer cfgSchool = cfg != null ? cfg.getSchoolId() : null;
            if (cfgSchool != null && cfgSchool > 0 && !schoolIds.contains(cfgSchool)) {
                schoolIds.add(cfgSchool);
                logger.info(
                        "GetVerifyUserId: hostSchool 范围下无匹配审核人，追加企业审核全局配置 schoolId={}，搜索schoolIds={}",
                        cfgSchool, schoolIds);
                candidateUserIds = buildCandidateUserIdsForSchools(schoolIds);
                verifyUserIds = intersectVerifyRole(verifyFirstRoleId, candidateUserIds);
            }
        }

        logger.info("GetVerifyUserId: roleId={}, 候选用户数={}, 匹配审核人={}",
                verifyFirstRoleId, candidateUserIds.size(), verifyUserIds);

        if (verifyUserIds.isEmpty()) {
            return "";
        }

        return verifyUserIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining("|"));
    }

    @SuppressWarnings("unchecked")
    private Set<Integer> buildCandidateUserIdsForSchools(Set<Integer> schoolIds) {
        Set<Integer> candidateUserIds = new HashSet<>();
        for (Integer sid : schoolIds) {
            JSONObject schoolSearchKeys = new JSONObject();
            schoolSearchKeys.put("schoolId", sid);
            Page<Object> schoolUserPage = (Page<Object>) iCommonService.getSomeRecords(
                    "ViewBaseUser", schoolSearchKeys, null, Sort.unsorted(), 1, 10000);
            schoolUserPage.getContent().stream()
                    .map(user -> FastJsonUtil.toJson(user).getInteger("id"))
                    .filter(id -> id != null)
                    .forEach(candidateUserIds::add);
        }
        return candidateUserIds;
    }

    @SuppressWarnings("unchecked")
    private List<Integer> intersectVerifyRole(int verifyFirstRoleId, Set<Integer> candidateUserIds) {
        if (candidateUserIds.isEmpty()) {
            return Collections.emptyList();
        }
        JSONObject roleSearchKeys = new JSONObject();
        roleSearchKeys.put("roleId", verifyFirstRoleId);
        Page<Object> userRolePage = (Page<Object>) iCommonService.getSomeRecords(
                "RelUserRole", roleSearchKeys, null, Sort.unsorted(), 1, 10000);
        List<Object> userRoleList = userRolePage.getContent();
        return userRoleList.stream()
                .map(role -> FastJsonUtil.toJson(role).getInteger("userId"))
                .filter(userId -> userId != null && candidateUserIds.contains(userId))
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public int refreshPendingVerifyUsersByUser(Integer userId) {
        if (userId == null) {
            return 0;
        }

        // 1. 获取用户的 schoolId
        Object userObj = iCommonService.getOneRecordById("ViewBaseUser", userId);
        if (userObj == null) {
            return 0;
        }
        JSONObject userJson = FastJsonUtil.toJson(userObj);
        Integer schoolId = userJson.getInteger("schoolId");
        if (schoolId == null) {
            return 0;
        }

        // 2. 批量查出同校所有用户ID
        JSONObject schoolSearchKeys = new JSONObject();
        schoolSearchKeys.put("schoolId", schoolId);
        Page<Object> schoolUserPage = (Page<Object>) iCommonService.getSomeRecords(
                "ViewBaseUser", schoolSearchKeys, null, Sort.unsorted(), 1, 10000);
        Set<Integer> schoolUserIds = schoolUserPage.getContent().stream()
                .map(u -> FastJsonUtil.toJson(u).getInteger("id"))
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (schoolUserIds.isEmpty()) {
            return 0;
        }

        // 3. 分页加载所有 MainVerifyProcess 记录，避免硬编码上限截断大规模数据
        List<MainVerifyProcess> allRecords = new ArrayList<>();
        int pageNum = 1;
        final int PAGE_SIZE = 1000;
        while (true) {
            Page<MainVerifyProcess> page = (Page<MainVerifyProcess>) iCommonService.getSomeRecords(
                    "MainVerifyProcess", new JSONObject(), null,
                    Sort.by(Sort.Direction.ASC, "id"), pageNum, PAGE_SIZE);
            List<MainVerifyProcess> pageContent = page.getContent();
            allRecords.addAll(pageContent);
            if (pageContent.size() < PAGE_SIZE) break;
            pageNum++;
        }

        // 4. 只处理创建人在同校的记录
        List<MainVerifyProcess> relevantRecords = allRecords.stream()
                .filter(vp -> schoolUserIds.contains(vp.getCreateUserId()))
                .collect(Collectors.toList());

        // 5. 按 processId 分组
        Map<Integer, List<MainVerifyProcess>> groupedByProcess = relevantRecords.stream()
                .filter(vp -> vp.getProcessId() != null)
                .collect(Collectors.groupingBy(MainVerifyProcess::getProcessId));

        // 6. 缓存流程配置和 verifyUserId 计算结果，避免重复查询
        Map<Integer, JSONObject> processConfigCache = new HashMap<>();
        Map<String, String> verifyUserIdCache = new HashMap<>();

        int updatedCount = 0;
        for (Map.Entry<Integer, List<MainVerifyProcess>> entry : groupedByProcess.entrySet()) {
            Integer processId = entry.getKey();
            List<MainVerifyProcess> records = entry.getValue();

            // 按 id 升序排列
            records.sort(Comparator.comparingInt(MainVerifyProcess::getId));

            // 加载流程配置（RelProcessInternship）
            JSONObject relJson = processConfigCache.get(processId);
            if (relJson == null) {
                Object relObj = iCommonService.getOneRecordById("RelProcessInternship", processId);
                if (relObj == null) continue;
                relJson = FastJsonUtil.toJson(relObj);
                processConfigCache.put(processId, relJson);
            }

            // 行走算法推断每条记录的审核级别并刷新 verifyUserId
            List<JSONObject> jsonRecords = records.stream()
                    .map(FastJsonUtil::toJson)
                    .collect(Collectors.toList());
            updatedCount += applyWalkingAlgorithm(jsonRecords, relJson, verifyUserIdCache);
        }

        logger.info("刷新用户 {} 相关的审核记录完成，共更新 {} 条记录", userId, updatedCount);
        return updatedCount;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public Object activateProcess(JSONObject node) {
        if (node == null) return null;
        Integer relationId  = node.getInteger("relationId");
        Integer processId   = node.getInteger("processId");
        Integer createUserId = node.getInteger("createUserId");
        String  tableName   = node.getString("tableName");
        if (relationId == null || createUserId == null) {
            logger.warn("activateProcess 参数不完整: {}", node);
            return null;
        }
        String finalTableName = (tableName != null) ? tableName : "RelProcessInternship";

        // 加载流程配置（使用 relationId，它是 RelProcessInternship 表的主键ID）
        Object relObj = iCommonService.getOneRecordById("RelProcessInternship", relationId);
        if (relObj == null) {
            logger.warn("activateProcess：未找到流程配置 {}", relationId);
            return null;
        }
        JSONObject relJson = FastJsonUtil.toJson(relObj);
        Integer verifyTypeId = relJson.getInteger("verifyTypeId");
        boolean needsVerify  = verifyTypeId != null && verifyTypeId >= 2;
        // 更新 currentVerifyTypeId
        // Integer currentVerifyTypeId = needsVerify ? 2 : 1;
        // relJson.put("currentVerifyTypeId", currentVerifyTypeId);
        // iCommonService.saveOneRecord("RelProcessInternship", relJson);
        // 计算审核人
        Integer verifyRoleId = needsVerify ? getVerifyRoleIdByLevel(relJson, 2) : null;
        Integer internshipId = relJson.getInteger("internshipId");
        String  verifyUserId = needsVerify ? GetVerifyUserId(verifyRoleId, createUserId, internshipId) : Constant.SYSTEM_AUDIT_NOTE.AUTO_PASS;
        // 创建审核记录
        Object saved = iCommonService.saveOneRecord("MainVerifyProcess",
                buildVerifyProcessJson(relationId, processId, createUserId, verifyUserId,
                        needsVerify ? -1 : 1, finalTableName));
        // logger.info("流程 {} 激活成功，isAudit: {}, currentVerifyTypeId: {}",
        //         relationId, saved.getIsAudit(), currentVerifyTypeId);
        return saved;
    }


    /**
     * 审核通过后的回调处理
     * <p>
     * 当审核记录被标记为通过（isAudit = 1）时调用此方法，用于推进多级审核流程。
     * </p>
     * <p>
     * 功能说明：
     * <ul>
     *   <li>获取审核通过的记录和对应的流程配置</li>
     *   <li>判断是否还有下一级审核（通过比较 currentVerifyTypeId 和 verifyTypeId）</li>
     *   <li>如果还有下一级审核：
     *     <ul>
     *       <li>更新 RelProcessInternship 的 currentVerifyTypeId 为下一级</li>
     *       <li>根据下一级审核级别获取对应的审核角色ID</li>
     *       <li>计算下一级的审核用户ID列表</li>
     *       <li>创建新的 MainVerifyProcess 记录，状态为待审核（isAudit = 0）</li>
     *     </ul>
     *   </li>
     *   <li>如果审核全部完成：
     *     <ul>
     *       <li>更新 RelProcessInternship 的 currentVerifyTypeId 为 verifyTypeId + 1</li>
     *       <li>便于后续通过 currentVerifyTypeId > verifyTypeId 判断审核是否结束</li>
     *     </ul>
     *   </li>
     * </ul>
     * </p>
     * 
     * @param Id 审核通过的 MainVerifyProcess 记录ID
     */
    @Override
    public void onVerifyProcessApproved(Integer Id) {
        if (Id == null) {
            return;
        }
        // 获取审核通过的记录
        Object verifyProcessObj = iCommonService.getOneRecordById("MainVerifyProcess", Id);
        if (verifyProcessObj == null) {
            logger.warn("未找到审核记录 {}", Id);
            return;
        }
        MainVerifyProcess verifyProcess = (MainVerifyProcess) verifyProcessObj;
        Integer processId = verifyProcess.getProcessId();
        Integer relationId = verifyProcess.getRelationId();
        String tableName = verifyProcess.getTableName();
        Integer createUserId = verifyProcess.getCreateUserId();
        // processId 为 null 时（如日志审核），直接从业务实体读审核配置；否则走 RelProcessInternship
        JSONObject relJson;
        if (processId != null) {
            Object relObj = iCommonService.getOneRecordById("RelProcessInternship", processId);
            if (relObj == null) {
                logger.warn("未找到流程关联记录 {}", processId);
                return;
            }
            relJson = FastJsonUtil.toJson(relObj);
        } else {
            Object entityObj = iCommonService.getOneRecordById(tableName, relationId);
            if (entityObj == null) {
                logger.warn("未找到业务实体配置 {} id={}", tableName, relationId);
                return;
            }
            relJson = FastJsonUtil.toJson(entityObj);
        }
        Integer verifyTypeId = relJson.getInteger("verifyTypeId");

        // 从对应的业务实体表获取 currentVerifyTypeId（每个审核条目独立跟踪审核级别）
        Object entityObj = iCommonService.getOneRecordById(tableName, relationId);
        if (entityObj == null) {
            logger.warn("未找到业务实体记录 {} id={}", tableName, relationId);
            return;
        }
        JSONObject entityJson = FastJsonUtil.toJson(entityObj);
        Integer currentVerifyTypeId = entityJson.getInteger("currentVerifyTypeId");

        if (currentVerifyTypeId == null) {
            currentVerifyTypeId = 2; // 默认一级审核
        }
        if (verifyTypeId == null) {
            verifyTypeId = 1; // 默认无需审核
        }

        if (verifyTypeId >= Constant.VERIFY_LEVEL.ONE_VERIFY
                && currentVerifyTypeId < Constant.VERIFY_LEVEL.ONE_VERIFY) {
            currentVerifyTypeId = Constant.VERIFY_LEVEL.ONE_VERIFY;
        }
        if (verifyTypeId < Constant.VERIFY_LEVEL.ONE_VERIFY
                && currentVerifyTypeId > Constant.VERIFY_LEVEL.NO_VERIFY) {
            currentVerifyTypeId = Constant.VERIFY_LEVEL.NO_VERIFY;
        }

        int nextLevel = currentVerifyTypeId + 1;

        // 更新业务实体表的 currentVerifyTypeId
        JSONObject updateEntityJson = new JSONObject();
        updateEntityJson.put("id", relationId);
        updateEntityJson.put("currentVerifyTypeId", nextLevel);
        iCommonService.saveOneRecord(tableName, updateEntityJson);

        if (nextLevel <= verifyTypeId) {
            // 还有下一级审核：创建新审核记录
            Integer nextVerifyRoleId = getVerifyRoleIdByLevel(relJson, nextLevel);
            Integer internshipIdForVerify = relJson.getInteger("internshipId");
            String nextVerifyUserId = GetVerifyUserId(nextVerifyRoleId, createUserId, internshipIdForVerify);
            // 创建下一级审核记录
            Object savedNextVerify = iCommonService.saveOneRecord("MainVerifyProcess",
                    buildVerifyProcessJson(relationId, processId, createUserId, nextVerifyUserId, 0, tableName));
            JSONObject savedNextVerifyJson = FastJsonUtil.toJson(savedNextVerify);
            Integer nextVerifyId = savedNextVerifyJson.getInteger("id");

            logger.info("审核记录 {} 通过，流程 {} 进入下一级审核 {}，创建新审核记录 {}",
                    Id, relationId, nextLevel, nextVerifyId);
        } else {
            // 审核全部完成（currentVerifyTypeId > verifyTypeId）
            logger.info("审核记录 {} 通过，流程 {} 审核全部完成（currentVerifyTypeId 更新为 {}，verifyTypeId {}）",
                    Id, relationId, nextLevel, verifyTypeId);

            // 学生选岗：审核全部通过 → 递增岗位人数，若已满清理该岗位其余报名，再清理学生其余报名
            if ("RelStuInternshipPost".equals(tableName)) {
                Integer studentId = entityJson.getInteger("studentId");
                Integer internshipPostId = entityJson.getInteger("internshipPostId");
                if (studentId != null && internshipPostId != null) {
                    MainInternshipPost post = mainInternshipPostDao.getByIdAndIsDeletedFalse(internshipPostId);
                    boolean isSelfInternship = post != null && "SELF_INTERNSHIP".equals(post.getCode());

                    if (!isSelfInternship) {
                        // 企业岗位 PASS：标准流程 —— 递增人数、满员级联、跨企业岗位级联 + 删自主
                        mainInternshipPostDao.incrementNowPersonNum(internshipPostId);
                        cancelPendingApplicationsIfPostFull(internshipPostId, relationId);
                        if (post != null && post.getInternshipId() != null) {
                            cancelOtherStuPostsOnApproval(relationId, studentId, post.getInternshipId());
                            // 企业岗位 PASS 即永久删除该学生该项目下的自主实习记录
                            // （applySelfInternship 会再预检拒绝，实现"企业岗位通过即自动退出自主"）
                            cancelSelfInternshipOnEnterpriseApproval(studentId, post.getInternshipId());
                        }
                    } else {
                        // 自主 PASS：allPersonNum=-1 不需计人头；但"同项目一岗位"对称互斥 ——
                        // 必须级联删除该学生在该项目下的所有企业岗位报名（含 SUBMIT/SAVE/BACK/NOTPASS）。
                        // cancelOtherStuPostsOnApproval 内部已跳过自主记录，传本 relationId 进去
                        // 则恰好只删企业岗位报名；stuSelPost 侧再预检拦截新的企业岗位申请。
                        if (post != null && post.getInternshipId() != null) {
                            cancelOtherStuPostsOnApproval(relationId, studentId, post.getInternshipId());
                        }
                    }
                    // 导师分配 ensure 对两种岗位都执行（自主 PASS 后也要生成导师分配占位）
                    if (post != null && post.getInternshipId() != null) {
                        ensureSeparateTutorAssignmentsAfterStuPostApproved(
                                post.getInternshipId(), relationId, studentId);
                    }
                }
            } else if (TABLE_REL_TITLE_STUDENT.equals(tableName)) {
                confirmTitleSelection(relationId);
            }
        }
    }

    /**
     * 学生选岗审核全部通过后：从 ViewRelProcessInternship 取该实习项目下未删除的流程配置；<br>
     * 若存在 EXTERNAL_ASSIGN_INTERNAL_TUTOR / EXTERNAL_ENTERPRISE_ASSIGN_TUTOR（各至多一条），则分别为其<br>
     * 新建一条 RelTeacherStudent（不填 teacherId）及一条 MainVerifyProcess（verifyUserId 空、isAudit=SAVE）。
     */
    private void ensureSeparateTutorAssignmentsAfterStuPostApproved(Integer internshipId,
            Integer relStuInternshipPostId, Integer studentId) {
        if (internshipId == null || relStuInternshipPostId == null || studentId == null) {
            return;
        }
        List<JSONObject> processRows = fetchAllViewRelProcessInternshipRows(internshipId);
        Integer internalProcId = findProcessIdByTypeCode(processRows,
                Constant.PROCESS_TYPE.EXTERNAL_ASSIGN_INTERNAL_TUTOR);
        Integer enterpriseProcId = findProcessIdByTypeCode(processRows,
                Constant.PROCESS_TYPE.EXTERNAL_ENTERPRISE_ASSIGN_TUTOR);

        if (internalProcId != null) {
            createRelTeacherStudentAndTutorSaveVerifyIfAbsent(studentId, internshipId, relStuInternshipPostId,
                    internalProcId, Constant.PROCESS_TYPE.EXTERNAL_ASSIGN_INTERNAL_TUTOR);
        }
        if (enterpriseProcId != null) {
            createRelTeacherStudentAndTutorSaveVerifyIfAbsent(studentId, internshipId, relStuInternshipPostId,
                    enterpriseProcId, Constant.PROCESS_TYPE.EXTERNAL_ENTERPRISE_ASSIGN_TUTOR);
        }
    }

    @SuppressWarnings("unchecked")
    private List<JSONObject> fetchAllViewRelProcessInternshipRows(Integer internshipId) {
        List<JSONObject> out = new ArrayList<>();
        if (internshipId == null) {
            return out;
        }
        JSONObject sk = new JSONObject();
        sk.put("internshipId", internshipId);
        sk.put("isDeleted", 0);
        int pageNum = 1;
        final int pageSize = 500;
        while (true) {
            Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                    "ViewRelProcessInternship", sk, null,
                    Sort.by(Sort.Direction.ASC, "theOrder"), pageNum, pageSize);
            List<Object> content = page != null ? page.getContent() : null;
            if (content == null || content.isEmpty()) {
                break;
            }
            for (Object obj : content) {
                out.add(FastJsonUtil.toJson(obj));
            }
            if (content.size() < pageSize) {
                break;
            }
            pageNum++;
        }
        return out;
    }

    private Integer findProcessIdByTypeCode(List<JSONObject> rows, String processTypeCode) {
        if (rows == null || processTypeCode == null) {
            return null;
        }
        for (JSONObject row : rows) {
            if (processTypeCode.equals(row.getString("processTypeCode"))) {
                return row.getInteger("id");
            }
        }
        return null;
    }

    /**
     * 同一选岗下同一流程若已有对应 MainVerifyProcess（师生关联匹配）则跳过，避免重复回调重复插入。
     */
    @SuppressWarnings("unchecked")
    private boolean hasTutorAssignmentVerifyForPost(Integer stuId, Integer internshipId,
            Integer relStuInternshipPostId, Integer processId) {
        if (stuId == null || internshipId == null || relStuInternshipPostId == null || processId == null) {
            return false;
        }
        JSONObject sk = new JSONObject();
        sk.put("processId", processId);
        sk.put("tableName", TABLE_REL_TEACHER_STUDENT);
        sk.put("createUserId", stuId);
        int pageNum = 1;
        final int pageSize = 200;
        while (true) {
            Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                    TABLE_MAIN_VERIFY_PROCESS, sk, null, Sort.by(Sort.Direction.ASC, "id"), pageNum, pageSize);
            List<Object> content = page != null ? page.getContent() : null;
            if (content == null || content.isEmpty()) {
                break;
            }
            for (Object obj : content) {
                JSONObject vp = FastJsonUtil.toJson(obj);
                Integer relationId = vp.getInteger("relationId");
                if (relationId == null) {
                    continue;
                }
                Object rtsObj = iCommonService.getOneRecordById(TABLE_REL_TEACHER_STUDENT, relationId);
                if (rtsObj == null) {
                    continue;
                }
                JSONObject rts = FastJsonUtil.toJson(rtsObj);
                if (stuId.equals(rts.getInteger("stuId"))
                        && internshipId.equals(rts.getInteger("internshipId"))
                        && relStuInternshipPostId.equals(rts.getInteger("relInternshipId"))) {
                    return true;
                }
            }
            if (content.size() < pageSize) {
                break;
            }
            pageNum++;
        }
        return false;
    }

    private void createRelTeacherStudentAndTutorSaveVerifyIfAbsent(Integer stuId, Integer internshipId,
            Integer relStuInternshipPostId, Integer processId, String kindLog) {
        if (processId == null) {
            return;
        }
        if (hasTutorAssignmentVerifyForPost(stuId, internshipId, relStuInternshipPostId, processId)) {
            return;
        }
        JSONObject rtsJson = new JSONObject();
        rtsJson.put("stuId", stuId);
        rtsJson.put("currentVerifyTypeId", 1);
        rtsJson.put("relInternshipId", relStuInternshipPostId);
        rtsJson.put("internshipId", internshipId);
        Object savedRts = iCommonService.saveOneRecord(TABLE_REL_TEACHER_STUDENT, rtsJson);
        Integer rtsId = FastJsonUtil.toJson(savedRts).getInteger("id");
        if (rtsId == null) {
            logger.warn("选岗通过后补建 RelTeacherStudent 失败 internshipId={} relStuPostId={} processId={}",
                    internshipId, relStuInternshipPostId, processId);
            return;
        }
        JSONObject vpJson = new JSONObject();
        vpJson.put("relationId", rtsId);
        vpJson.put("processId", processId);
        vpJson.put("createUserId", stuId);
        vpJson.put("verifyUserId", "");
        vpJson.put("isAudit", Constant.AUDIT_STATUS.SAVE);
        vpJson.put("reason", "");
        vpJson.put("tableName", TABLE_REL_TEACHER_STUDENT);
        iCommonService.saveOneRecord(TABLE_MAIN_VERIFY_PROCESS, vpJson);
        logger.info("选岗通过已补建导师分配：RelTeacherStudent={} processId={} kind={} internshipId={}",
                rtsId, processId, kindLog, internshipId);
    }

    /**
     * 根据审核级别从流程记录JSON中获取对应的审核角色ID
     *
     * @param relJson 流程关联记录JSON
     * @param verifyLevel 审核级别（2-6）
     * @return 对应级别的审核角色ID
     */
    private void confirmTitleSelection(Integer relationId) {
        if (relationId == null) {
            return;
        }
        RelTitleStudent current = relTitleStudentDao.getByIdAndIsDeletedFalse(relationId);
        if (current == null) {
            return;
        }
        Integer internshipId = resolveTitleSelectionInternshipId(current);
        if (internshipId == null) {
            throw BaseResponse.moreInfoError.error("title selection has no internshipId");
        }

        Object lock = titleConfirmLock(internshipId);
        synchronized (lock) {
            current = relTitleStudentDao.getByIdAndIsDeletedFalse(relationId);
            if (current == null) {
                return;
            }
            internshipId = resolveTitleSelectionInternshipId(current);
            validateNoFinalTitleConflict(current.getStuId(), internshipId, current.getTitleId(), relationId);

            JSONObject update = new JSONObject();
            update.put("id", relationId);
            update.put("internshipId", internshipId);
            update.put("isFinal", 1);
            if (current.getSourceType() == null || current.getSourceType().isBlank()) {
                update.put("sourceType", TITLE_SOURCE_STUDENT_CANDIDATE);
            }
            update.put("confirmedBy", resolveCurrentUserId());
            update.put("confirmedTime", new Date());
            iCommonService.saveOneRecord(TABLE_REL_TITLE_STUDENT, update);

            releaseOtherCandidatesOfStudent(current.getStuId(), internshipId, relationId);
            releaseOtherCandidatesOfTitle(current.getTitleId(), relationId);
        }
    }

    private Integer resolveTitleSelectionInternshipId(RelTitleStudent row) {
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

    private Object titleConfirmLock(Integer internshipId) {
        String key = "title-confirm:" + (internshipId == null ? "none" : internshipId);
        return TITLE_CONFIRM_LOCKS.computeIfAbsent(key, k -> new Object());
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

    @SuppressWarnings("unchecked")
    private void deleteVerifyProcessByRelationIdAndTableName(Integer relationId, String tableName) {
        if (relationId == null || tableName == null || tableName.isBlank()) {
            return;
        }
        JSONObject searchKeys = new JSONObject();
        searchKeys.put("relationId", relationId);
        searchKeys.put("tableName", tableName);
        Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
                TABLE_MAIN_VERIFY_PROCESS, searchKeys, null, Sort.unsorted(), 1, 100);
        if (page == null || page.getContent() == null) {
            return;
        }
        for (Object obj : page.getContent()) {
            Integer id = FastJsonUtil.toJson(obj).getInteger("id");
            if (id != null) {
                iCommonService.deleteRecordByDelflag(TABLE_MAIN_VERIFY_PROCESS, id);
            }
        }
    }

    @Override
    public Integer getVerifyRoleIdByLevel(JSONObject relJson, Integer verifyLevel) {
        if (relJson == null || verifyLevel == null) {
            return null;
        }
        switch (verifyLevel) {
            case 2: return relJson.getInteger("verifyFirstRoleId");
            case 3: return relJson.getInteger("verifySecondRoleId");
            case 4: return relJson.getInteger("verifyThirdRoleId");
            case 5: return relJson.getInteger("verifyFourthRoleId");
            case 6: return relJson.getInteger("verifyFifthRoleId");
            default: return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public int refreshPendingVerifyUsersByProcess(Integer processId) {
        if (processId == null) {
            return 0;
        }

        // 1. 加载流程配置
        Object relObj = iCommonService.getOneRecordById("RelProcessInternship", processId);
        if (relObj == null) {
            return 0;
        }
        JSONObject relJson = FastJsonUtil.toJson(relObj);

        // 2. 分页查询该流程下所有 MainVerifyProcess 记录（PERF-05: 避免 10000 硬编码截断）
        JSONObject searchKeys = new JSONObject();
        searchKeys.put("processId", processId);
        List<Object> allRecords = new ArrayList<>();
        int pageNum = 1;
        final int PAGE_SIZE = 1000;
        while (true) {
            Page<Object> pageResult = (Page<Object>) iCommonService.getSomeRecords(
                    "MainVerifyProcess", searchKeys, null,
                    Sort.by(Sort.Direction.ASC, "id"), pageNum, PAGE_SIZE);
            List<Object> pageContent = pageResult.getContent();
            allRecords.addAll(pageContent);
            if (pageContent.size() < PAGE_SIZE) break;
            pageNum++;
        }

        // 行走算法推断每条记录的审核级别并刷新 verifyUserId
        List<JSONObject> jsonRecords = allRecords.stream()
                .map(FastJsonUtil::toJson)
                .collect(Collectors.toList());
        int updatedCount = applyWalkingAlgorithm(jsonRecords, relJson, new HashMap<>());

        logger.info("刷新流程 {} 的审核记录完成，共更新 {} 条记录", processId, updatedCount);
        return updatedCount;
    }

    private JSONObject buildVerifyProcessJson(Integer relationId, Integer processId, Integer createUserId,
                                               String verifyUserId, int isAudit, String tableName) {
        JSONObject json = new JSONObject();
        json.put("relationId", relationId);
        json.put("processId", processId);
        json.put("createUserId", createUserId);
        json.put("verifyUserId", verifyUserId);
        json.put("isAudit", isAudit);
        json.put("reason", "");
        json.put("tableName", tableName);
        return json;
    }

    private int applyWalkingAlgorithm(List<JSONObject> records, JSONObject relJson,
                                       Map<String, String> verifyUserIdCache) {
        int updatedCount = 0;
        int currentLevel = 2;
        for (JSONObject recordJson : records) {
            Integer isAudit = recordJson.getInteger("isAudit");
            Integer createUserId = recordJson.getInteger("createUserId");
            if (isAudit != null && (isAudit == -1 || isAudit == 0)) {
                Integer verifyRoleId = getVerifyRoleIdByLevel(relJson, currentLevel);
                if (verifyRoleId != null && verifyRoleId != 0) {
                    Integer internshipId = relJson.getInteger("internshipId");
                    String cacheKey = verifyRoleId + ":" + createUserId + ":" + internshipId;
                    String newVerifyUserId = verifyUserIdCache.computeIfAbsent(cacheKey,
                            k -> GetVerifyUserId(verifyRoleId, createUserId, internshipId));
                    String oldVerifyUserId = recordJson.getString("verifyUserId");
                    if (!newVerifyUserId.equals(oldVerifyUserId != null ? oldVerifyUserId : "")) {
                        JSONObject updateJson = new JSONObject();
                        updateJson.put("id", recordJson.getInteger("id"));
                        updateJson.put("verifyUserId", newVerifyUserId);
                        iCommonService.saveOneRecord("MainVerifyProcess", updateJson);
                        updatedCount++;
                    }
                }
            }
            if (isAudit != null) {
                if (isAudit == 1) {
                    currentLevel++;
                } else if (isAudit == 2 || isAudit == 3) {
                    currentLevel = Math.max(2, currentLevel - 1);
                }
            }
        }
        return updatedCount;
    }

    /**
     * 学生某一岗位报名审核全部通过后，将同实习项目下其余**企业岗位**报名记录标记为系统自动作废
     * （isAudit=NOTPASS + 系统说明），<b>保留</b>关系记录与附件可见。
     * <p>对每条"其他报名"：清掉旧 MainVerifyProcess → 追加 NOTPASS 标记；若曾 PASS（NO_VERIFY 自动通过）
     * 则原子性扣减岗位 nowPersonNum。RelStuInternshipPost 与 SysOssFile 不软删，学生可见审核历史。</p>
     * <p>自主实习岗位（code='SELF_INTERNSHIP'）下的记录不在此处理 —— 由
     * {@link #cancelSelfInternshipOnEnterpriseApproval} 负责。</p>
     * <p>调用场景：</p>
     * <ul>
     *   <li>企业岗位 PASS：传 approvedRelStuPostId=企业记录 id，跳过自己 + 跳过自主 → 作废其他企业报名。</li>
     *   <li>自主 PASS：传 approvedRelStuPostId=自主记录 id，跳过自己（同时命中"跳过自主"）→ 作废所有企业报名。</li>
     * </ul>
     */
    @Override
    public void cancelOtherStuPostsOnApproval(Integer approvedRelStuPostId,
                                               Integer studentId,
                                               Integer internshipId) {
        if (approvedRelStuPostId == null || studentId == null || internshipId == null) {
            return;
        }

        // 取该实习项目下所有岗位（保留 code 以便过滤自主岗位）
        List<MainInternshipPost> posts = mainInternshipPostDao.findByInternshipIdAndIsDeletedFalse(internshipId);
        if (posts == null || posts.isEmpty()) {
            return;
        }
        Set<Integer> selfInternshipPostIds = posts.stream()
                .filter(p -> "SELF_INTERNSHIP".equals(p.getCode()))
                .map(MainInternshipPost::getId)
                .collect(Collectors.toSet());
        List<Integer> postIds = posts.stream()
                .map(MainInternshipPost::getId)
                .collect(Collectors.toList());

        // 推断 reason：approved 是自主则提示自主，否则提示企业
        boolean approvedIsSelf = false;
        for (RelStuInternshipPost p : relStuInternshipPostDao
                .findByStudentIdAndInternshipPostIdInAndIsDeletedFalse(studentId, postIds)) {
            if (approvedRelStuPostId.equals(p.getId())
                    && selfInternshipPostIds.contains(p.getInternshipPostId())) {
                approvedIsSelf = true;
                break;
            }
        }
        String reason = approvedIsSelf
                ? "您已通过自主实习申请，本企业岗位报名自动作废"
                : "您已选定其他企业岗位，本报名自动作废";

        // 取学生在这些岗位中的全部有效报名记录
        List<RelStuInternshipPost> others = relStuInternshipPostDao
                .findByStudentIdAndInternshipPostIdInAndIsDeletedFalse(studentId, postIds);

        for (RelStuInternshipPost other : others) {
            if (approvedRelStuPostId.equals(other.getId())) {
                continue; // 跳过已通过的那条
            }
            // 跳过自主实习：自主由 cancelSelfInternshipOnEnterpriseApproval 处理
            if (selfInternshipPostIds.contains(other.getInternshipPostId())) {
                continue;
            }
            Integer otherId = other.getId();

            boolean wasApproved = markRelationCancelled(otherId, "RelStuInternshipPost", studentId, reason);
            if (wasApproved && other.getInternshipPostId() != null) {
                int updated = mainInternshipPostDao.decrementNowPersonNum(other.getInternshipPostId());
                logger.info("作废已通过报名 id={}，岗位 {} nowPersonNum-1（影响 {} 行）",
                        otherId, other.getInternshipPostId(), updated);
            }
            logger.info("学生 {} 岗位 {} 通过，标记其他企业岗位报名为作废 id={}", studentId, approvedRelStuPostId, otherId);
        }
    }

    /**
     * 企业岗位 PASS 后，永久删除该学生在本实习项目下的自主实习记录（所有状态）。
     * <p>级联软删：{@link RelStuInternshipPost} → 其 {@code MainVerifyProcess} → 其 {@code SysOssFile}
     * → 由自主触发生成的 {@code RelTeacherStudent}（relInternshipId 指向该自主 id）及其 {@code MainVerifyProcess}。</p>
     * <p>语义：学生选定一份企业岗位后，不再允许自主实习与之并存；
     * {@code applySelfInternship} 会再通过 {@code countApprovedCompanyPostForStudentInInternship} 预检拒绝重新申请。</p>
     */
    private void cancelSelfInternshipOnEnterpriseApproval(Integer studentId, Integer internshipId) {
        if (studentId == null || internshipId == null) {
            return;
        }
        List<Integer> selfRelIds = relStuInternshipPostDao
                .findAllActiveSelfInternshipRelIds(studentId, internshipId);
        if (selfRelIds == null || selfRelIds.isEmpty()) {
            return;
        }
        String reason = "该项目已有企业岗位审核通过，自主实习申请自动作废";
        for (Integer selfRelId : selfRelIds) {
            // 1. 标记 RelStuInternshipPost 自动作废（清旧 MVP + 追加 NOTPASS 标记）；自主无 nowPersonNum 概念，wasApproved 不用。
            markRelationCancelled(selfRelId, "RelStuInternshipPost", studentId, reason);

            // 2. 附件保留（SysOssFile 不动），让学生可看曾提交的材料。

            // 3. 软删由自主 PASS 触发生成的师生分配记录（relInternshipId=selfRelId）及其审核 ——
            //    源记录已作废，导师占位无意义。
            List<RelTeacherStudent> tutorAssigns = relTeacherStudentDao
                    .findByRelInternshipIdAndIsDeletedFalse(selfRelId);
            if (tutorAssigns != null) {
                for (RelTeacherStudent rts : tutorAssigns) {
                    Integer rtsId = rts.getId();
                    if (rtsId == null) continue;
                    softDeleteVerifyProcessByRelation(rtsId, TABLE_REL_TEACHER_STUDENT);
                    iCommonService.deleteRecordByDelflag("RelTeacherStudent", rtsId);
                }
            }

            logger.info("学生 {} 项目 {} 企业岗位通过，自主实习记录 id={} 标记为作废（保留可见）",
                    studentId, internshipId, selfRelId);
        }
    }

    @SuppressWarnings("unchecked")
    private void softDeleteVerifyProcessByRelation(Integer relationId, String tableName) {
        if (relationId == null || tableName == null) return;
        JSONObject sk = new JSONObject();
        sk.put("relationId", relationId);
        sk.put("tableName", tableName);
        Page<Object> vpPage = (Page<Object>) iCommonService.getSomeRecords(
                "MainVerifyProcess", sk, null, Sort.unsorted(), 1, 100);
        if (vpPage == null || vpPage.getContent() == null) return;
        for (Object vp : vpPage.getContent()) {
            Integer vpId = FastJsonUtil.toJson(vp).getInteger("id");
            if (vpId != null) {
                iCommonService.deleteRecordByDelflag("MainVerifyProcess", vpId);
            }
        }
    }

    /**
     * 标记某条业务记录被系统自动作废：清掉旧 MainVerifyProcess（避免残留 PASS 影响 pre-check），
     * 追加一条 isAudit=NOTPASS 的标记记录，保留业务记录本身可见。
     * <p>返回旧 MVP 中是否曾出现过 PASS 状态，调用方据此决定是否扣回 nowPersonNum。</p>
     */
    @SuppressWarnings("unchecked")
    private boolean markRelationCancelled(Integer relationId, String tableName,
                                          Integer createUserId, String reason) {
        if (relationId == null || tableName == null) return false;
        JSONObject sk = new JSONObject();
        sk.put("relationId", relationId);
        sk.put("tableName", tableName);
        Page<Object> vpPage = (Page<Object>) iCommonService.getSomeRecords(
                "MainVerifyProcess", sk, null, Sort.unsorted(), 1, 100);
        boolean wasApproved = false;
        Integer processId = null;
        if (vpPage != null && vpPage.getContent() != null) {
            for (Object vp : vpPage.getContent()) {
                JSONObject vpJson = FastJsonUtil.toJson(vp);
                Integer vpIsAudit = vpJson.getInteger("isAudit");
                if (vpIsAudit != null && vpIsAudit == Constant.AUDIT_STATUS.PASS) {
                    wasApproved = true;
                }
                if (processId == null) {
                    processId = vpJson.getInteger("processId");
                }
                Integer vpId = vpJson.getInteger("id");
                if (vpId != null) {
                    iCommonService.deleteRecordByDelflag("MainVerifyProcess", vpId);
                }
            }
        }
        // 追加 NOTPASS 标记
        JSONObject vp = new JSONObject();
        vp.put("relationId", relationId);
        if (processId != null) vp.put("processId", processId);
        vp.put("createUserId", createUserId);
        vp.put("verifyUserId", Constant.SYSTEM_AUDIT_NOTE.AUTO_CANCEL);
        vp.put("isAudit", Constant.AUDIT_STATUS.NOTPASS);
        vp.put("reason", reason);
        vp.put("tableName", tableName);
        iCommonService.saveOneRecord("MainVerifyProcess", vp);
        return wasApproved;
    }

    /**
     * 若某岗位审核通过后已招满，将该岗位上仍处于待审/退回的报名标记为系统自动作废（保留可见，附 NOTPASS 标记）。
     * <p><b>不会</b>作废已审核通过（isAudit=PASS）的报名——免审/系统分配场景下同岗多人依次 PASS 挤满时，
     * 先前已通过的记录必须保留，否则会出现「满员后只剩最后 1 条 PASS、其余被误标 NOTPASS」。</p>
     */
    @Override
    public void cancelPendingApplicationsIfPostFull(Integer postId, Integer approvedRelStuPostId) {
        if (postId == null) return;
        MainInternshipPost post = mainInternshipPostDao.getByIdAndIsDeletedFalse(postId);
        if (post == null || post.getNowPersonNum() == null || post.getAllPersonNum() == null
                || post.getNowPersonNum() < post.getAllPersonNum()) {
            return; // 未满，无需处理
        }
        String reason = "该岗位已招满，本报名自动作废";
        List<RelStuInternshipPost> records = relStuInternshipPostDao.findByInternshipPostIdAndIsDeletedFalse(postId);
        for (RelStuInternshipPost record : records) {
            if (approvedRelStuPostId != null && approvedRelStuPostId.equals(record.getId())) {
                continue; // 跳过刚通过的记录
            }
            Integer recordId = record.getId();
            if (!isPendingStuPostApplication(recordId)) {
                // 已 PASS、已作废/不通过，或不存在待审记录 → 不作废
                continue;
            }
            markRelationCancelled(recordId, "RelStuInternshipPost", record.getStudentId(), reason);
            logger.info("岗位 {} 已满（{}/{}），标记待审报名记录 id={} 为作废（保留可见）",
                    postId, post.getNowPersonNum(), post.getAllPersonNum(), recordId);
        }
    }

    /**
     * 选岗报名是否仍处于可被「岗位满员」作废的状态：存在 SAVE/SUBMIT/BACK，且从未 PASS。
     */
    @SuppressWarnings("unchecked")
    private boolean isPendingStuPostApplication(Integer relationId) {
        if (relationId == null) {
            return false;
        }
        JSONObject sk = new JSONObject();
        sk.put("relationId", relationId);
        sk.put("tableName", "RelStuInternshipPost");
        Page<Object> vpPage = (Page<Object>) iCommonService.getSomeRecords(
                "MainVerifyProcess", sk, null, Sort.unsorted(), 1, 100);
        if (vpPage == null || vpPage.getContent() == null || vpPage.getContent().isEmpty()) {
            return false;
        }
        boolean hasPending = false;
        for (Object vp : vpPage.getContent()) {
            Integer isAudit = FastJsonUtil.toJson(vp).getInteger("isAudit");
            if (isAudit == null) {
                continue;
            }
            if (isAudit == Constant.AUDIT_STATUS.PASS) {
                return false;
            }
            if (isAudit == Constant.AUDIT_STATUS.SAVE
                    || isAudit == Constant.AUDIT_STATUS.SUBMIT
                    || isAudit == Constant.AUDIT_STATUS.BACK) {
                hasPending = true;
            }
        }
        return hasPending;
    }

}

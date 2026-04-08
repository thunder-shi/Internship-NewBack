package newcms.service.impl;

import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import newcms.base.Base;
import newcms.base.BaseResponse;
import newcms.base.Constant;
import newcms.entity.db.*;
import newcms.repository.db.*;
import newcms.service.ICommonService;
import newcms.service.IDiaryService;
import newcms.utils.FastJsonUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class DiaryServiceImpl extends Base implements IDiaryService {

    @Resource
    private ICommonService iCommonService;

    @Resource
    private MainDiaryDao mainDiaryDao;

    @Resource
    private MainDiaryPeriodDao mainDiaryPeriodDao;

    @Resource
    private MainVerifyProcessDao mainVerifyProcessDao;

    @Resource
    private MainInternshipPostDao mainInternshipPostDao;

    @Resource
    private RelStuInternshipPostDao relStuInternshipPostDao;

    @Resource
    private RelIntershipUserDao relIntershipUserDao;

    @Resource
    private RelTeacherStudentDao relTeacherStudentDao;

    @Resource
    private ViewRelTitleTeacherStudentDao viewRelTitleTeacherStudentDao;

    @Resource
    private ViewVerifyMainDiaryMergeDao viewVerifyMainDiaryMergeDao;

    @Resource
    private ViewRelStuInternshipPostDao viewRelStuInternshipPostDao;

    @Resource
    private ViewVerifyProcessRelStuInternshipPostMergeDao viewVerifyProcessRelStuInternshipPostMergeDao;

    @Resource
    private ViewBaseUserDao viewBaseUserDao;

    @Resource
    private newcms.service.IVerifyProcessService iVerifyProcessService;

    // ==================== 提交/保存日志 ====================

    @Override
    public Integer submitDiary(Integer relationId, String tableName, Integer periodId,
                               String content, Boolean submit, Integer currentUserId) {
        boolean isSubmit = Boolean.TRUE.equals(submit);

        // 查找已有日志（同一 relationId+tableName+periodId 只存一条）
        Optional<MainDiary> existingOpt =
                mainDiaryDao.findByRelationIdAndTableNameAndPeriodIdAndIsDeletedFalse(relationId, tableName, periodId);

        if (existingOpt.isPresent()) {
            MainDiary existing = existingOpt.get();
            Integer diaryId = existing.getId();
            boolean wasDraft = !Boolean.TRUE.equals(existing.getSubmit());

            // 更新内容、提交状态、审核进度
            JSONObject updateDiary = new JSONObject();
            updateDiary.put("id", diaryId);
            updateDiary.put("content", content);
            updateDiary.put("submit", isSubmit);
            updateDiary.put("currentVerifyTypeId", isSubmit ? Constant.VERIFY_LEVEL.ONE_VERIFY : Constant.VERIFY_LEVEL.NO_VERIFY);
            iCommonService.saveOneRecord("MainDiary", updateDiary);

            // 从草稿变为提交时，创建审核记录（若无 SUBMIT 状态的记录）
            if (isSubmit && wasDraft) {
                ensureDiaryVerifyProcess(diaryId, relationId, tableName, currentUserId);
            }
            return diaryId;
        }

        // 首次创建
        JSONObject diaryJson = new JSONObject();
        diaryJson.put("relationId", relationId);
        diaryJson.put("tableName", tableName);
        diaryJson.put("periodId", periodId);
        diaryJson.put("content", content);
        diaryJson.put("submit", isSubmit);
        diaryJson.put("verifyTypeId", Constant.VERIFY_LEVEL.ONE_VERIFY);
        diaryJson.put("currentVerifyTypeId", isSubmit ? Constant.VERIFY_LEVEL.ONE_VERIFY : Constant.VERIFY_LEVEL.NO_VERIFY);
        Object savedDiary = iCommonService.saveOneRecord("MainDiary", diaryJson);
        Integer diaryId = FastJsonUtil.toJson(savedDiary).getInteger("id");

        if (isSubmit) {
            ensureDiaryVerifyProcess(diaryId, relationId, tableName, currentUserId);
        }
        return diaryId;
    }

    /**
     * 若没有 SUBMIT(0) 状态的审核记录，则新建第一级审核记录。
     * 优先使用 MainDiary.verifyFirstRoleId 做角色匹配；角色未配置（0）时回落到具体分配的校内导师。
     */
    private void ensureDiaryVerifyProcess(Integer diaryId, Integer relationId,
                                          String tableName, Integer currentUserId) {
        List<MainVerifyProcess> existing =
                mainVerifyProcessDao.findByRelationIdAndTableNameAndIsDeletedFalse(diaryId, "MainDiary");
        boolean hasPending = existing.stream()
                .anyMatch(p -> p.getIsAudit() != null && p.getIsAudit() == Constant.AUDIT_STATUS.SUBMIT);
        if (hasPending) return;

        // 读取日志的第一级审核角色
        JSONObject diaryJson = FastJsonUtil.toJson(iCommonService.getOneRecordById("MainDiary", diaryId));
        Integer verifyFirstRoleId = diaryJson.getInteger("verifyFirstRoleId");

        String verifyUserId;
        if (verifyFirstRoleId != null && verifyFirstRoleId > 0) {
            // 角色配置存在：按角色+学校范围查找所有可审核人
            verifyUserId = iVerifyProcessService.GetVerifyUserId(verifyFirstRoleId, currentUserId);
        } else {
            // 角色未配置：回落到具体分配的校内导师
            Integer teacherId = findSchoolTeacherId(relationId, tableName, currentUserId);
            verifyUserId = String.valueOf(teacherId);
        }

        JSONObject verifyJson = new JSONObject();
        verifyJson.put("relationId", diaryId);
        verifyJson.put("createUserId", currentUserId);
        verifyJson.put("verifyUserId", verifyUserId);
        verifyJson.put("isAudit", Constant.AUDIT_STATUS.SUBMIT);
        verifyJson.put("reason", "");
        verifyJson.put("tableName", "MainDiary");
        iCommonService.saveOneRecord("MainVerifyProcess", verifyJson);
    }

    /**
     * 根据 relationId+tableName 查找负责审批该日志的校内导师 ID。
     */
    private Integer findSchoolTeacherId(Integer relationId, String tableName, Integer currentUserId) {
        if ("RelStuInternshipPost".equals(tableName)) {
            Object relStuPostObj = iCommonService.getOneRecordById("RelStuInternshipPost", relationId);
            if (relStuPostObj == null) throw BaseResponse.parameterInvalid.error("学生实习岗位记录不存在");
            JSONObject relStuPostJson = FastJsonUtil.toJson(relStuPostObj);
            Integer studentId = relStuPostJson.getInteger("studentId");
            Integer internshipPostId = relStuPostJson.getInteger("internshipPostId");

            Object postObj = iCommonService.getOneRecordById("MainInternshipPost", internshipPostId);
            if (postObj == null) throw BaseResponse.parameterInvalid.error("实习岗位不存在");
            Integer internshipId = FastJsonUtil.toJson(postObj).getInteger("internshipId");

            List<RelIntershipUser> relIntershipUsers =
                    relIntershipUserDao.findByUserIdAndInternshipIdAndIsDeletedFalse(studentId, internshipId);
            if (relIntershipUsers.isEmpty()) throw BaseResponse.parameterInvalid.error("该学生尚未纳入实习项目");
            Integer relIntershipUserId = relIntershipUsers.get(0).getId();

            List<RelTeacherStudent> teacherStudents =
                    relTeacherStudentDao.findByRelInternshipIdAndIsDeletedFalse(relIntershipUserId);
            Set<Integer> candidateIds = teacherStudents.stream()
                    .map(RelTeacherStudent::getTeacherId).filter(Objects::nonNull).collect(Collectors.toSet());
            return viewBaseUserDao.getByIdInAndIsDeletedFalse(candidateIds).stream()
                    .filter(u -> Constant.USER_JOB_CODE.SCHOOL_TEACHER.equals(u.getJobCode()))
                    .map(ViewBaseUser::getId)
                    .findFirst()
                    .orElseThrow(() -> BaseResponse.parameterInvalid.error("该学生尚未分配校内导师"));
        } else {
            // RelTitleStudent（校内）
            List<ViewRelTitleTeacherStudent> records =
                    viewRelTitleTeacherStudentDao.findByRelTitleStudentIdAndIsDeletedFalse(relationId);
            if (records.isEmpty()) throw BaseResponse.parameterInvalid.error("学生题目关联记录不存在");
            return records.get(0).getTeacherId();
        }
    }

    // ==================== 期次列表（学生视角） ====================

    @Override
    public Object getDiaryPeriods(Integer relationId, String tableName) {
        Integer internshipId = getInternshipIdFromRelation(relationId, tableName);

        List<MainDiaryPeriod> periods =
                mainDiaryPeriodDao.findByInternshipIdAndIsDeletedFalseOrderByPeriodIndexAsc(internshipId);
        if (periods.isEmpty()) return Collections.emptyList();

        List<MainDiary> diaries =
                mainDiaryDao.findByRelationIdAndTableNameAndIsDeletedFalse(relationId, tableName);
        Map<Integer, Integer> periodIdToDiaryId = new HashMap<>();
        for (MainDiary d : diaries) {
            if (d.getPeriodId() != null) periodIdToDiaryId.put(d.getPeriodId(), d.getId());
        }

        Map<Integer, ViewVerifyMainDiaryMerge> mergeMap =
                fetchMergeByDiaryIds(new ArrayList<>(periodIdToDiaryId.values()));

        List<JSONObject> result = new ArrayList<>();
        for (MainDiaryPeriod period : periods) {
            JSONObject item = new JSONObject();
            item.put("periodId", period.getId());
            item.put("periodIndex", period.getPeriodIndex());
            item.put("beginTime", period.getBeginTime());
            item.put("endTime", period.getEndTime());
            Integer diaryId = periodIdToDiaryId.get(period.getId());
            ViewVerifyMainDiaryMerge merge = diaryId != null ? mergeMap.get(diaryId) : null;
            item.put("diary", merge != null ? FastJsonUtil.toJson(merge) : null);
            result.add(item);
        }
        return result;
    }

    // ==================== 期次定义（老师端） ====================

    @Override
    public List<MainDiaryPeriod> getInternshipPeriods(Integer internshipId) {
        return mainDiaryPeriodDao.findByInternshipIdAndIsDeletedFalseOrderByPeriodIndexAsc(internshipId);
    }

    // ==================== 学生列表（老师视角） ====================

    @Override
    public Object getPeriodStudents(Integer internshipId, Integer periodId, Integer userId) {
        List<JSONObject> result = new ArrayList<>();

        // 校外路径：有岗位 → 校外项目
        List<MainInternshipPost> posts = mainInternshipPostDao.findByInternshipIdAndIsDeletedFalse(internshipId);
        if (!posts.isEmpty()) {
            // 建立 relIntershipUser id ↔ studentId 的双向映射
            List<RelIntershipUser> allRelIntershipUsers =
                    relIntershipUserDao.findByInternshipIdAndIsDeletedFalse(internshipId);
            Map<Integer, Integer> relIntershipUserIdToStudentId = new HashMap<>();
            Map<Integer, Integer> studentIdToRelIntershipUserId = new HashMap<>();
            for (RelIntershipUser riu : allRelIntershipUsers) {
                relIntershipUserIdToStudentId.put(riu.getId(), riu.getUserId());
                studentIdToRelIntershipUserId.put(riu.getUserId(), riu.getId());
            }

            // 筛出校内导师（jobCode = SCHOOL_TEACHER）
            List<RelTeacherStudent> allTeacherStudents =
                    relTeacherStudentDao.findByInternshipIdAndIsDeletedFalse(internshipId);
            Set<Integer> candidateTeacherIds = allTeacherStudents.stream()
                    .map(RelTeacherStudent::getTeacherId).filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Integer, ViewBaseUser> teacherIdToUser = new HashMap<>();
            for (ViewBaseUser u : viewBaseUserDao.getByIdInAndIsDeletedFalse(candidateTeacherIds))
                teacherIdToUser.put(u.getId(), u);
            Set<Integer> schoolTeacherIds = teacherIdToUser.values().stream()
                    .filter(u -> Constant.USER_JOB_CODE.SCHOOL_TEACHER.equals(u.getJobCode()))
                    .map(ViewBaseUser::getId).collect(Collectors.toSet());

            // studentId → 校内导师 ID；校内导师 ID → 所管 studentId 集合
            Map<Integer, Integer> studentIdToSchoolTeacherId = new HashMap<>();
            Map<Integer, Set<Integer>> schoolTeacherIdToStudentIds = new HashMap<>();
            for (RelTeacherStudent rts : allTeacherStudents) {
                if (rts.getTeacherId() == null || !schoolTeacherIds.contains(rts.getTeacherId())
                        || rts.getRelInternshipId() == null) continue;
                Integer studentId = relIntershipUserIdToStudentId.get(rts.getRelInternshipId());
                if (studentId == null) continue;
                studentIdToSchoolTeacherId.put(studentId, rts.getTeacherId());
                schoolTeacherIdToStudentIds.computeIfAbsent(rts.getTeacherId(), k -> new HashSet<>()).add(studentId);
            }

            // userId 过滤：只返回该校内导师名下的学生
            Set<Integer> allowedStuIds = userId != null
                    ? schoolTeacherIdToStudentIds.getOrDefault(userId, Collections.emptySet())
                    : null;

            List<Integer> postIds = posts.stream().map(MainInternshipPost::getId).collect(Collectors.toList());
            List<RelStuInternshipPost> relStuPosts =
                    relStuInternshipPostDao.findByInternshipPostIdInAndIsDeletedFalse(postIds);

            if (allowedStuIds != null) {
                final Set<Integer> filter = allowedStuIds;
                relStuPosts = relStuPosts.stream()
                        .filter(r -> filter.contains(r.getStudentId()))
                        .collect(Collectors.toList());
            }

            // 只保留岗位选择已审核通过（isAllVerified=true）的学生
            if (!relStuPosts.isEmpty()) {
                List<Integer> candidatePostIds = relStuPosts.stream()
                        .map(RelStuInternshipPost::getId).collect(Collectors.toList());
                Set<Integer> approvedPostIds = viewVerifyProcessRelStuInternshipPostMergeDao
                        .findByRelationIdInAndIsDeletedFalse(candidatePostIds).stream()
                        .filter(m -> Boolean.TRUE.equals(m.getIsAllVerified()))
                        .map(m -> m.getRelationId())
                        .collect(Collectors.toSet());
                relStuPosts = relStuPosts.stream()
                        .filter(r -> approvedPostIds.contains(r.getId()))
                        .collect(Collectors.toList());
            }

            if (!relStuPosts.isEmpty()) {
                List<Integer> stuPostIds = relStuPosts.stream()
                        .map(RelStuInternshipPost::getId).collect(Collectors.toList());
                Map<Integer, ViewRelStuInternshipPost> idToView = new HashMap<>();
                for (ViewRelStuInternshipPost v : viewRelStuInternshipPostDao.findAllById(stuPostIds))
                    idToView.put(v.getId(), v);

                // 按 relationId（=RelStuInternshipPost.id）+ tableName + periodId 查日志
                List<MainDiary> diaries = mainDiaryDao
                        .findByRelationIdInAndTableNameAndPeriodIdAndIsDeletedFalse(
                                stuPostIds, "RelStuInternshipPost", periodId);
                Map<Integer, Integer> stuPostIdToDiaryId = new HashMap<>();
                for (MainDiary d : diaries) {
                    if (d.getRelationId() != null) stuPostIdToDiaryId.put(d.getRelationId(), d.getId());
                }

                Map<Integer, ViewVerifyMainDiaryMerge> mergeMap =
                        fetchMergeByDiaryIds(new ArrayList<>(stuPostIdToDiaryId.values()));

                Set<Integer> studentIdsWithoutDiary = relStuPosts.stream()
                        .filter(r -> !stuPostIdToDiaryId.containsKey(r.getId()))
                        .map(RelStuInternshipPost::getStudentId)
                        .collect(Collectors.toSet());
                Map<Integer, ViewBaseUser> studentIdToUser = batchLoadUsers(studentIdsWithoutDiary);

                for (RelStuInternshipPost rsp : relStuPosts) {
                    JSONObject item = new JSONObject();
                    item.put("stuRelationId", rsp.getId());
                    item.put("studentId", rsp.getStudentId());
                    ViewRelStuInternshipPost v = idToView.get(rsp.getId());
                    if (v != null) {
                        item.put("studentName", v.getStudentName());
                        item.put("internshipPostName", v.getInternshipPostName());
                        item.put("companyName", v.getCompanyName());
                        item.put("postDescription", v.getInternshipPostRemarks());
                        item.put("className", v.getDepartmentName());
                    }
                    Integer assignedTeacherId = studentIdToSchoolTeacherId.get(rsp.getStudentId());
                    ViewBaseUser teacher = assignedTeacherId != null ? teacherIdToUser.get(assignedTeacherId) : null;
                    item.put("teacherName", teacher != null ? teacher.getNickName() : null);
                    item.put("titleDescription", null);

                    Integer diaryId = stuPostIdToDiaryId.get(rsp.getId());
                    ViewVerifyMainDiaryMerge merge = diaryId != null ? mergeMap.get(diaryId) : null;
                    if (merge != null) {
                        item.put("studentNo", merge.getStudentAccount());
                        item.put("majorName", merge.getStudentMajorName());
                        if (item.getString("className") == null)
                            item.put("className", merge.getStudentDepartmentName());
                    } else {
                        ViewBaseUser bu = studentIdToUser.get(rsp.getStudentId());
                        item.put("studentNo", bu != null ? bu.getAccount() : null);
                        item.put("majorName", null);
                    }
                    item.put("diary", merge != null ? FastJsonUtil.toJson(merge) : null);
                    result.add(item);
                }
            }
            return result; // 校外项目直接返回，不走校内路径
        }

        // 校内路径：题目-学生关联（userId 过滤 + 只保留题目已审核通过的学生）
        List<ViewRelTitleTeacherStudent> titleStudents =
                viewRelTitleTeacherStudentDao.findByInternshipIdAndIsDeletedFalse(internshipId).stream()
                        .filter(t -> Integer.valueOf(1).equals(t.getIsAudit()))
                        .filter(t -> userId == null || userId.equals(t.getTeacherId()))
                        .collect(Collectors.toList());
        if (titleStudents.isEmpty()) return result;

        List<Integer> relTitleStudentIds = titleStudents.stream()
                .map(ViewRelTitleTeacherStudent::getRelTitleStudentId).collect(Collectors.toList());

        // 按 relationId（=RelTitleStudent.id）+ tableName + periodId 查日志
        List<MainDiary> diaries = mainDiaryDao
                .findByRelationIdInAndTableNameAndPeriodIdAndIsDeletedFalse(
                        relTitleStudentIds, "RelTitleStudent", periodId);
        Map<Integer, Integer> relTitleStudentIdToDiaryId = new HashMap<>();
        for (MainDiary d : diaries) {
            if (d.getRelationId() != null) relTitleStudentIdToDiaryId.put(d.getRelationId(), d.getId());
        }

        Map<Integer, ViewVerifyMainDiaryMerge> mergeMap =
                fetchMergeByDiaryIds(new ArrayList<>(relTitleStudentIdToDiaryId.values()));

        Set<Integer> stuIdsWithoutDiary = titleStudents.stream()
                .filter(t -> !relTitleStudentIdToDiaryId.containsKey(t.getRelTitleStudentId()))
                .map(ViewRelTitleTeacherStudent::getStuId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Integer, ViewBaseUser> studentIdToUser = batchLoadUsers(stuIdsWithoutDiary);

        for (ViewRelTitleTeacherStudent rts : titleStudents) {
            JSONObject item = new JSONObject();
            item.put("titleRelationId", rts.getRelTitleStudentId());
            item.put("studentId", rts.getStuId());
            item.put("studentName", rts.getStudentName());
            item.put("titleName", rts.getName());
            item.put("titleDescription", rts.getRemarks());
            item.put("teacherName", rts.getTeacherName());
            item.put("companyName", null);

            Integer diaryId = relTitleStudentIdToDiaryId.get(rts.getRelTitleStudentId());
            ViewVerifyMainDiaryMerge merge = diaryId != null ? mergeMap.get(diaryId) : null;
            if (merge != null) {
                item.put("studentNo", merge.getStudentAccount());
                item.put("majorName", merge.getStudentMajorName());
                item.put("className", merge.getStudentDepartmentName());
            } else {
                ViewBaseUser bu = studentIdToUser.get(rts.getStuId());
                item.put("studentNo", bu != null ? bu.getAccount() : null);
                item.put("majorName", null);
                item.put("className", bu != null ? bu.getDepartmentName() : null);
            }
            item.put("postDescription", null);
            item.put("diary", merge != null ? FastJsonUtil.toJson(merge) : null);
            result.add(item);
        }
        return result;
    }

    // ==================== 工具方法 ====================

    private Integer getInternshipIdFromRelation(Integer relationId, String tableName) {
        if ("RelStuInternshipPost".equals(tableName)) {
            Object relStuPostObj = iCommonService.getOneRecordById("RelStuInternshipPost", relationId);
            if (relStuPostObj == null) throw BaseResponse.parameterInvalid.error("学生实习岗位记录不存在");
            Integer internshipPostId = FastJsonUtil.toJson(relStuPostObj).getInteger("internshipPostId");
            Object postObj = iCommonService.getOneRecordById("MainInternshipPost", internshipPostId);
            if (postObj == null) throw BaseResponse.parameterInvalid.error("实习岗位不存在");
            return FastJsonUtil.toJson(postObj).getInteger("internshipId");
        } else {
            List<ViewRelTitleTeacherStudent> records =
                    viewRelTitleTeacherStudentDao.findByRelTitleStudentIdAndIsDeletedFalse(relationId);
            if (records.isEmpty()) throw BaseResponse.parameterInvalid.error("学生题目关联记录不存在");
            return records.get(0).getInternshipId();
        }
    }

    private Map<Integer, ViewBaseUser> batchLoadUsers(Set<Integer> studentIds) {
        if (studentIds.isEmpty()) return Collections.emptyMap();
        Map<Integer, ViewBaseUser> map = new HashMap<>();
        for (ViewBaseUser u : viewBaseUserDao.getByIdInAndIsDeletedFalse(studentIds))
            map.put(u.getId(), u);
        return map;
    }

    private Map<Integer, ViewVerifyMainDiaryMerge> fetchMergeByDiaryIds(List<Integer> diaryIds) {
        Map<Integer, ViewVerifyMainDiaryMerge> result = new HashMap<>();
        if (diaryIds.isEmpty()) return result;
        List<ViewVerifyMainDiaryMerge> merges = viewVerifyMainDiaryMergeDao.findByRelationIdIn(diaryIds);
        for (ViewVerifyMainDiaryMerge m : merges) result.put(m.getRelationId(), m);
        return result;
    }
}

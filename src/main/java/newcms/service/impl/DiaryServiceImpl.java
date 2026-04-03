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

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
    private MainVerifyProcessDao mainVerifyProcessDao;

    @Resource
    private RelProcessInternshipDao relProcessInternshipDao;

    @Resource
    private MainInternshipPostDao mainInternshipPostDao;

    @Resource
    private RelStuInternshipPostDao relStuInternshipPostDao;

    @Resource
    private ViewRelTitleTeacherStudentDao viewRelTitleTeacherStudentDao;

    @Resource
    private ViewVerifyMainDiaryMergeDao viewVerifyMainDiaryMergeDao;

    @Resource
    private ViewRelStuInternshipPostDao viewRelStuInternshipPostDao;

    // ==================== 提交日志 ====================

    @Override
    public Integer submitDiary(Integer stuInternshipPostId, Integer relTitleStudentId,
                               Integer periodIndex, String content, Integer currentUserId) {
        boolean isExternal = stuInternshipPostId != null;
        boolean isInternal = relTitleStudentId != null;
        if (!isExternal && !isInternal) {
            throw BaseResponse.parameterInvalid.error("stuInternshipPostId 和 relTitleStudentId 不能同时为空");
        }

        Integer teacherId = null;
        Integer internshipId = null;

        // 查找该期已有的 diary（如果有就是重新提交）
        Optional<MainDiary> existingOpt = isExternal
                ? mainDiaryDao.findByStuInternshipPostIdAndPeriodIndexAndIsDeletedFalse(stuInternshipPostId, periodIndex)
                : mainDiaryDao.findByRelTitleStudentIdAndPeriodIndexAndIsDeletedFalse(relTitleStudentId, periodIndex);

        if (existingOpt.isPresent()) {
            // 重新提交：更新 content，将 isAudit=-1 的记录改为 isAudit=0
            MainDiary existing = existingOpt.get();
            Integer diaryId = existing.getId();

            JSONObject updateDiary = new JSONObject();
            updateDiary.put("id", diaryId);
            updateDiary.put("content", content);
            iCommonService.saveOneRecord("MainDiary", updateDiary);

            // 找到待提交（isAudit=-1）的记录并改为 isAudit=0
            List<MainVerifyProcess> pendingList = mainVerifyProcessDao
                    .findByRelationIdAndTableNameAndIsDeletedFalse(diaryId, "MainDiary");
            pendingList.stream()
                    .filter(p -> p.getIsAudit() != null && p.getIsAudit() == Constant.AUDIT_STATUS.SAVE)
                    .max(Comparator.comparing(MainVerifyProcess::getId))
                    .ifPresent(p -> {
                        JSONObject updateProcess = new JSONObject();
                        updateProcess.put("id", p.getId());
                        updateProcess.put("isAudit", Constant.AUDIT_STATUS.SUBMIT);
                        iCommonService.saveOneRecord("MainVerifyProcess", updateProcess);
                    });

            return diaryId;
        }

        // 首次提交：需要获取指导老师
        if (isExternal) {
            Object relStuPostObj = iCommonService.getOneRecordById("RelStuInternshipPost", stuInternshipPostId);
            if (relStuPostObj == null) throw BaseResponse.parameterInvalid.error("学生实习岗位记录不存在");
            JSONObject relStuPostJson = FastJsonUtil.toJson(relStuPostObj);
            Integer studentId = relStuPostJson.getInteger("studentId");
            Integer internshipPostId = relStuPostJson.getInteger("internshipPostId");

            Object postObj = iCommonService.getOneRecordById("MainInternshipPost", internshipPostId);
            if (postObj == null) throw BaseResponse.parameterInvalid.error("实习岗位不存在");
            internshipId = FastJsonUtil.toJson(postObj).getInteger("internshipId");

            List<ViewRelTitleTeacherStudent> teachers =
                    viewRelTitleTeacherStudentDao.findByStuIdAndInternshipIdAndIsDeletedFalse(studentId, internshipId);
            if (teachers.isEmpty()) throw BaseResponse.parameterInvalid.error("该学生尚未分配指导老师");
            teacherId = teachers.get(0).getTeacherId();
        } else {
            List<ViewRelTitleTeacherStudent> records =
                    viewRelTitleTeacherStudentDao.findByRelTitleStudentIdAndIsDeletedFalse(relTitleStudentId);
            if (records.isEmpty()) throw BaseResponse.parameterInvalid.error("学生题目关联记录不存在");
            ViewRelTitleTeacherStudent record = records.get(0);
            teacherId = record.getTeacherId();
            internshipId = record.getInternshipId();
        }

        // 新建 MainDiary
        JSONObject diaryJson = new JSONObject();
        diaryJson.put("stuInternshipPostId", stuInternshipPostId);
        diaryJson.put("relTitleStudentId", relTitleStudentId);
        diaryJson.put("periodIndex", periodIndex);
        diaryJson.put("content", content);
        Object savedDiary = iCommonService.saveOneRecord("MainDiary", diaryJson);
        Integer diaryId = FastJsonUtil.toJson(savedDiary).getInteger("id");

        // 创建 MainVerifyProcess（processId 用 0 占位，diary 无流程配置）
        JSONObject verifyJson = new JSONObject();
        verifyJson.put("relationId", diaryId);
        verifyJson.put("processId", 0);
        verifyJson.put("createUserId", currentUserId);
        verifyJson.put("verifyUserId", String.valueOf(teacherId));
        verifyJson.put("isAudit", Constant.AUDIT_STATUS.SUBMIT);
        verifyJson.put("reason", "");
        verifyJson.put("tableName", "MainDiary");
        iCommonService.saveOneRecord("MainVerifyProcess", verifyJson);

        return diaryId;
    }

    // ==================== 期数列表（学生视角） ====================

    @Override
    public Object getDiaryPeriods(Integer stuInternshipPostId, Integer relTitleStudentId) {
        boolean isExternal = stuInternshipPostId != null;

        Integer internshipId;
        String cron;

        if (isExternal) {
            Object relStuPostObj = iCommonService.getOneRecordById("RelStuInternshipPost", stuInternshipPostId);
            if (relStuPostObj == null) throw BaseResponse.parameterInvalid.error("学生实习岗位记录不存在");
            Integer internshipPostId = FastJsonUtil.toJson(relStuPostObj).getInteger("internshipPostId");
            Object postObj = iCommonService.getOneRecordById("MainInternshipPost", internshipPostId);
            if (postObj == null) throw BaseResponse.parameterInvalid.error("实习岗位不存在");
            internshipId = FastJsonUtil.toJson(postObj).getInteger("internshipId");
        } else {
            List<ViewRelTitleTeacherStudent> records =
                    viewRelTitleTeacherStudentDao.findByRelTitleStudentIdAndIsDeletedFalse(relTitleStudentId);
            if (records.isEmpty()) throw BaseResponse.parameterInvalid.error("学生题目关联记录不存在");
            internshipId = records.get(0).getInternshipId();
        }

        Object internshipObj = iCommonService.getOneRecordById("MainInternship", internshipId);
        if (internshipObj == null) throw BaseResponse.parameterInvalid.error("实习项目不存在");
        cron = FastJsonUtil.toJson(internshipObj).getString("cron");

        // 取最早流程 startTime 作为周期起点
        List<RelProcessInternship> processes = relProcessInternshipDao.findByInternshipIdAndIsDeletedFalse(internshipId);
        if (processes.isEmpty()) return Collections.emptyList();
        LocalDateTime startTime = processes.stream()
                .map(RelProcessInternship::getStartTime)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
        if (startTime == null) return Collections.emptyList();

        int totalPeriods = calcTotalPeriods(startTime, cron);
        if (totalPeriods <= 0) return Collections.emptyList();

        // 查询已提交日志
        List<MainDiary> diaries = isExternal
                ? mainDiaryDao.findByStuInternshipPostIdAndIsDeletedFalse(stuInternshipPostId)
                : mainDiaryDao.findByRelTitleStudentIdAndIsDeletedFalse(relTitleStudentId);

        Map<Integer, Integer> periodToDiaryId = new HashMap<>();
        for (MainDiary d : diaries) {
            if (d.getPeriodIndex() != null) {
                periodToDiaryId.put(d.getPeriodIndex(), d.getId());
            }
        }

        Map<Integer, ViewVerifyMainDiaryMerge> diaryIdToMerge =
                fetchMergeByDiaryIds(new ArrayList<>(periodToDiaryId.values()));

        List<JSONObject> result = new ArrayList<>();
        for (int i = 1; i <= totalPeriods; i++) {
            JSONObject item = new JSONObject();
            item.put("periodIndex", i);
            Integer diaryId = periodToDiaryId.get(i);
            ViewVerifyMainDiaryMerge merge = diaryId != null ? diaryIdToMerge.get(diaryId) : null;
            item.put("diary", merge != null ? FastJsonUtil.toJson(merge) : null);
            result.add(item);
        }
        return result;
    }

    // ==================== 学生列表（老师视角） ====================

    @Override
    public Object getPeriodStudents(Integer internshipId, Integer periodIndex) {
        List<JSONObject> result = new ArrayList<>();

        // 校外路径：通过岗位 → 学生报名记录
        List<MainInternshipPost> posts = mainInternshipPostDao.findByInternshipIdAndIsDeletedFalse(internshipId);
        if (!posts.isEmpty()) {
            List<Integer> postIds = posts.stream().map(MainInternshipPost::getId).collect(Collectors.toList());
            List<RelStuInternshipPost> relStuPosts =
                    relStuInternshipPostDao.findByInternshipPostIdInAndIsDeletedFalse(postIds);

            if (!relStuPosts.isEmpty()) {
                List<Integer> stuPostIds = relStuPosts.stream()
                        .map(RelStuInternshipPost::getId).collect(Collectors.toList());
                List<ViewRelStuInternshipPost> viewStuPosts =
                        viewRelStuInternshipPostDao.findAllById(stuPostIds);
                Map<Integer, ViewRelStuInternshipPost> idToView = new HashMap<>();
                for (ViewRelStuInternshipPost v : viewStuPosts) idToView.put(v.getId(), v);

                List<MainDiary> diaries = mainDiaryDao
                        .findByStuInternshipPostIdInAndPeriodIndexAndIsDeletedFalse(stuPostIds, periodIndex);
                Map<Integer, Integer> stuPostIdToDiaryId = new HashMap<>();
                for (MainDiary d : diaries) stuPostIdToDiaryId.put(d.getStuInternshipPostId(), d.getId());

                Map<Integer, ViewVerifyMainDiaryMerge> mergeMap =
                        fetchMergeByDiaryIds(new ArrayList<>(stuPostIdToDiaryId.values()));

                for (RelStuInternshipPost rsp : relStuPosts) {
                    JSONObject item = new JSONObject();
                    item.put("stuInternshipPostId", rsp.getId());
                    item.put("studentId", rsp.getStudentId());
                    ViewRelStuInternshipPost v = idToView.get(rsp.getId());
                    if (v != null) {
                        item.put("studentName", v.getStudentName());
                        item.put("internshipPostName", v.getInternshipPostName());
                    }
                    Integer diaryId = stuPostIdToDiaryId.get(rsp.getId());
                    ViewVerifyMainDiaryMerge merge = diaryId != null ? mergeMap.get(diaryId) : null;
                    item.put("diary", merge != null ? FastJsonUtil.toJson(merge) : null);
                    result.add(item);
                }
                return result;
            }
        }

        // 校内路径：通过题目-学生关联
        List<ViewRelTitleTeacherStudent> titleStudents =
                viewRelTitleTeacherStudentDao.findByInternshipIdAndIsDeletedFalse(internshipId);
        if (titleStudents.isEmpty()) return result;

        List<Integer> relTitleStudentIds = titleStudents.stream()
                .map(ViewRelTitleTeacherStudent::getRelTitleStudentId).collect(Collectors.toList());

        List<MainDiary> diaries = mainDiaryDao
                .findByRelTitleStudentIdInAndPeriodIndexAndIsDeletedFalse(relTitleStudentIds, periodIndex);
        Map<Integer, Integer> relTitleStudentIdToDiaryId = new HashMap<>();
        for (MainDiary d : diaries) relTitleStudentIdToDiaryId.put(d.getRelTitleStudentId(), d.getId());

        Map<Integer, ViewVerifyMainDiaryMerge> mergeMap =
                fetchMergeByDiaryIds(new ArrayList<>(relTitleStudentIdToDiaryId.values()));

        for (ViewRelTitleTeacherStudent rts : titleStudents) {
            JSONObject item = new JSONObject();
            item.put("relTitleStudentId", rts.getRelTitleStudentId());
            item.put("studentId", rts.getStuId());
            item.put("studentName", rts.getStudentName());
            item.put("titleName", rts.getName());
            item.put("teacherName", rts.getTeacherName());
            Integer diaryId = relTitleStudentIdToDiaryId.get(rts.getRelTitleStudentId());
            ViewVerifyMainDiaryMerge merge = diaryId != null ? mergeMap.get(diaryId) : null;
            item.put("diary", merge != null ? FastJsonUtil.toJson(merge) : null);
            result.add(item);
        }
        return result;
    }

    // ==================== 工具方法 ====================

    private Map<Integer, ViewVerifyMainDiaryMerge> fetchMergeByDiaryIds(List<Integer> diaryIds) {
        Map<Integer, ViewVerifyMainDiaryMerge> result = new HashMap<>();
        if (diaryIds.isEmpty()) return result;
        List<ViewVerifyMainDiaryMerge> merges = viewVerifyMainDiaryMergeDao.findByRelationIdIn(diaryIds);
        for (ViewVerifyMainDiaryMerge m : merges) result.put(m.getRelationId(), m);
        return result;
    }

    private int calcTotalPeriods(LocalDateTime startTime, String cron) {
        LocalDateTime now = LocalDateTime.now();
        if (startTime.isAfter(now)) return 0;
        if (cron == null) return 1;
        return switch (cron.toUpperCase()) {
            case "DAILY"   -> (int) ChronoUnit.DAYS.between(startTime, now) + 1;
            case "WEEKLY"  -> (int) ChronoUnit.WEEKS.between(startTime, now) + 1;
            case "MONTHLY" -> (int) ChronoUnit.MONTHS.between(startTime, now) + 1;
            default        -> 1;
        };
    }
}

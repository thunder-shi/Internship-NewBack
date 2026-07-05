package newcms.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import newcms.base.Base;
import newcms.base.BaseException;
import newcms.base.BaseResponse;
import newcms.base.Constant;
import newcms.config.CozeProperties;
import newcms.entity.db.*;
import newcms.repository.db.*;
import newcms.service.ICommonService;
import newcms.service.IDiaryService;
import newcms.service.IInternshipTerminationService;
import newcms.service.CozeWorkflowService;
import newcms.utils.FastJsonUtil;
import newcms.utils.MinIOUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class DiaryServiceImpl extends Base implements IDiaryService {

    /** 按 diaryId 分段的应用层锁，防止并发提交同一期日志时重复创建审核行（CONC-06） */
    private static final ConcurrentHashMap<Integer, Object> DIARY_LOCKS = new ConcurrentHashMap<>();

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
  private RelTeacherStudentDao relTeacherStudentDao;

  @Resource
  private RelTitleStudentDao relTitleStudentDao;

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
  private IInternshipTerminationService internshipTerminationService;

  @Resource
  private MinIOUtils minIOUtils;

  @Resource
  private CozeWorkflowService cozeWorkflowService;

  @Resource
  private CozeProperties cozeProperties;

  private static final Set<String> REVIEWABLE_SUFFIXES = Set.of("pdf", "doc", "docx");
  private static final List<String> DIARY_OSS_TABLE_NAMES = List.of("MainDiary", "main_diary");

  @Override
  public Integer submitDiary(Integer relationId, String tableName, Integer periodId,
      String title, String content, Boolean submit, Integer currentUserId) {
    boolean isSubmit = Boolean.TRUE.equals(submit);
    internshipTerminationService.assertNotTerminated(tableName, relationId);

    // 查找已有日志（同一 relationId+tableName+periodId 只存一条）
    Optional<MainDiary> existingOpt = mainDiaryDao.findByRelationIdAndTableNameAndPeriodIdAndIsDeletedFalse(relationId,
        tableName, periodId);

    if (existingOpt.isPresent()) {
      MainDiary existing = existingOpt.get();
      Integer diaryId = existing.getId();
      boolean wasDraft = !Boolean.TRUE.equals(existing.getSubmit());
      if (!isSubmit && !wasDraft) {
        throw BaseResponse.parameterInvalid.error("日志已提交，不能保存为草稿");
      }

      // 更新标题、内容、提交状态、审核进度
      JSONObject updateDiary = new JSONObject();
      updateDiary.put("id", diaryId);
      updateDiary.put("title", title);
      updateDiary.put("content", content);
      updateDiary.put("submit", isSubmit);
      updateDiary.put("currentVerifyTypeId",
          isSubmit ? Constant.VERIFY_LEVEL.ONE_VERIFY : Constant.VERIFY_LEVEL.NO_VERIFY);
      iCommonService.saveOneRecord("MainDiary", updateDiary);

      if (isSubmit) {
        // 提交时复用草稿审核占位，并按当前师生关系修正具体校内导师。
        activateDiaryVerifyProcess(diaryId, relationId, tableName, currentUserId, wasDraft);
      } else if (wasDraft) {
        ensureDraftDiaryVerifyProcess(diaryId, relationId, tableName);
      }
      return diaryId;
    }

    // 首次创建
    JSONObject diaryJson = new JSONObject();
    diaryJson.put("relationId", relationId);
    diaryJson.put("tableName", tableName);
    diaryJson.put("periodId", periodId);
    diaryJson.put("title", title);
    diaryJson.put("content", content);
    diaryJson.put("submit", isSubmit);
    diaryJson.put("verifyTypeId", Constant.VERIFY_LEVEL.ONE_VERIFY);
    diaryJson.put("currentVerifyTypeId", isSubmit ? Constant.VERIFY_LEVEL.ONE_VERIFY : Constant.VERIFY_LEVEL.NO_VERIFY);
    Object savedDiary = iCommonService.saveOneRecord("MainDiary", diaryJson);
    Integer diaryId = FastJsonUtil.toJson(savedDiary).getInteger("id");

    if (isSubmit) {
      activateDiaryVerifyProcess(diaryId, relationId, tableName, currentUserId, true);
    } else {
      ensureDraftDiaryVerifyProcess(diaryId, relationId, tableName);
    }
    return diaryId;
  }

  /**
   * 草稿阶段维护一条 SAVE(-1) 审核占位，审核人固定为该学生当前校内导师。
   */
  private void ensureDraftDiaryVerifyProcess(Integer diaryId, Integer relationId, String tableName) {
    Integer teacherId = findSchoolTeacherId(relationId, tableName);
    Integer studentId = resolveStudentId(relationId, tableName);
    upsertDiaryVerifyProcess(diaryId, studentId, String.valueOf(teacherId), Constant.AUDIT_STATUS.SAVE);
  }

  /**
   * 提交阶段复用 SAVE(-1) 占位审核行；若旧数据没有占位，则创建 SUBMIT(0) 审核行。
   */
  private void activateDiaryVerifyProcess(Integer diaryId, Integer relationId,
      String tableName, Integer currentUserId, boolean createIfMissing) {
    Integer teacherId = findSchoolTeacherId(relationId, tableName);
    Integer createUserId = currentUserId != null ? currentUserId : resolveStudentId(relationId, tableName);
    upsertDiaryVerifyProcess(diaryId, createUserId, String.valueOf(teacherId), Constant.AUDIT_STATUS.SUBMIT,
        createIfMissing);
  }

  private void upsertDiaryVerifyProcess(Integer diaryId, Integer createUserId, String verifyUserId, Integer isAudit) {
    upsertDiaryVerifyProcess(diaryId, createUserId, verifyUserId, isAudit, true);
  }

  private void upsertDiaryVerifyProcess(Integer diaryId, Integer createUserId, String verifyUserId, Integer isAudit,
      boolean createIfMissing) {
    Object lock = DIARY_LOCKS.computeIfAbsent(diaryId, k -> new Object());
    synchronized (lock) {
      List<MainVerifyProcess> existing = mainVerifyProcessDao.findByRelationIdAndTableNameAndIsDeletedFalse(diaryId,
          "MainDiary");

      MainVerifyProcess target;
      if (Objects.equals(isAudit, Constant.AUDIT_STATUS.SAVE)) {
        if (latestVerifyProcess(existing, Set.of(Constant.AUDIT_STATUS.SUBMIT)) != null)
          return;
        target = latestVerifyProcess(existing, Set.of(Constant.AUDIT_STATUS.SAVE));
      } else {
        MainVerifyProcess pending = latestVerifyProcess(existing, Set.of(Constant.AUDIT_STATUS.SUBMIT));
        target = pending != null ? pending : latestVerifyProcess(existing, Set.of(Constant.AUDIT_STATUS.SAVE));
      }
      if (target == null && !createIfMissing)
        return;

      JSONObject verifyJson = new JSONObject();
      if (target != null)
        verifyJson.put("id", target.getId());
      verifyJson.put("relationId", diaryId);
      verifyJson.put("createUserId", createUserId);
      verifyJson.put("verifyUserId", verifyUserId);
      verifyJson.put("isAudit", isAudit);
      verifyJson.put("reason", target != null ? target.getReason() : "");
      verifyJson.put("tableName", "MainDiary");
      iCommonService.saveOneRecord("MainVerifyProcess", verifyJson);
    }
  }

  private MainVerifyProcess latestVerifyProcess(List<MainVerifyProcess> records, Set<Integer> statuses) {
    return records.stream()
        .filter(r -> r.getIsAudit() != null && statuses.contains(r.getIsAudit()))
        .max(Comparator.comparing(MainVerifyProcess::getId))
        .orElse(null);
  }

  /**
   * 根据 relationId+tableName 查找负责审批该日志的校内导师 ID。
   */
  private Integer findSchoolTeacherId(Integer relationId, String tableName) {
    return resolveSchoolTeacherId(relationId, tableName, true);
  }

  private Integer resolveSchoolTeacherId(Integer relationId, String tableName, boolean required) {
    if ("RelStuInternshipPost".equals(tableName)) {
      // RelTeacherStudent.relInternshipId 存的是 RelStuInternshipPost.id，即 relationId 本身
      List<RelTeacherStudent> teacherStudents = relTeacherStudentDao
          .findByRelInternshipIdAndIsDeletedFalse(relationId);
      Set<Integer> candidateIds = teacherStudents.stream()
          .map(RelTeacherStudent::getTeacherId).filter(Objects::nonNull).collect(Collectors.toSet());
      if (candidateIds.isEmpty()) {
        if (required)
          throw BaseResponse.parameterInvalid.error("该学生尚未分配校内导师");
        return null;
      }
      return viewBaseUserDao.getByIdInAndIsDeletedFalse(candidateIds).stream()
          .filter(u -> Constant.USER_JOB_CODE.SCHOOL_TEACHER.equals(u.getJobCode()))
          .map(ViewBaseUser::getId)
          .findFirst()
          .orElseGet(() -> {
            if (required)
              throw BaseResponse.parameterInvalid.error("该学生尚未分配校内导师");
            return null;
          });
    } else {
      // RelTitleStudent（校内）
      List<ViewRelTitleTeacherStudent> records = viewRelTitleTeacherStudentDao
          .findByRelTitleStudentIdAndIsDeletedFalse(relationId);
      if (records.isEmpty()) {
        if (required)
          throw BaseResponse.parameterInvalid.error("学生题目关联记录不存在");
        return null;
      }
      Integer teacherId = records.get(0).getTeacherId();
      if (teacherId == null && required)
        throw BaseResponse.parameterInvalid.error("该学生尚未分配校内导师");
      return teacherId;
    }
  }

  private Integer resolveStudentId(Integer relationId, String tableName) {
    if ("RelStuInternshipPost".equals(tableName)) {
      RelStuInternshipPost rel = relStuInternshipPostDao.getByIdAndIsDeletedFalse(relationId);
      if (rel == null)
        throw BaseResponse.parameterInvalid.error("学生实习岗位记录不存在");
      return rel.getStudentId();
    }
    RelTitleStudent rel = relTitleStudentDao.getByIdAndIsDeletedFalse(relationId);
    if (rel == null)
      throw BaseResponse.parameterInvalid.error("学生题目关联记录不存在");
    return rel.getStuId();
  }

  // ==================== 期次列表（学生视角） ====================

  @Override
  public Object getDiaryPeriods(Integer relationId, String tableName) {
    Integer internshipId = getInternshipIdFromRelation(relationId, tableName);

    List<MainDiaryPeriod> periods = mainDiaryPeriodDao
        .findByInternshipIdAndIsDeletedFalseOrderByPeriodIndexAsc(internshipId);
    if (periods.isEmpty())
      return Collections.emptyList();

    List<MainDiary> diaries = mainDiaryDao.findByRelationIdAndTableNameAndIsDeletedFalse(relationId, tableName);
    Map<Integer, MainDiary> periodIdToDiary = new HashMap<>();
    for (MainDiary d : diaries) {
      if (d.getPeriodId() != null)
        periodIdToDiary.put(d.getPeriodId(), d);
    }

    List<Integer> diaryIds = periodIdToDiary.values().stream()
        .map(MainDiary::getId).collect(Collectors.toList());
    Map<Integer, ViewVerifyMainDiaryMerge> mergeMap = fetchMergeByDiaryIds(diaryIds);

    List<JSONObject> result = new ArrayList<>();
    for (MainDiaryPeriod period : periods) {
      JSONObject item = new JSONObject();
      item.put("periodId", period.getId());
      item.put("periodIndex", period.getPeriodIndex());
      item.put("beginTime", period.getBeginTime());
      item.put("endTime", period.getEndTime());

      MainDiary diary = periodIdToDiary.get(period.getId());
      if (diary == null) {
        item.put("diary", null);
      } else {
        ViewVerifyMainDiaryMerge merge = mergeMap.get(diary.getId());
        item.put("diary", buildDiaryJson(diary, merge));
      }
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
      // 先拿全量选岗记录，用于后续教师映射（relInternshipId 存的是 RelStuInternshipPost.id）
      List<Integer> postIds = posts.stream().map(MainInternshipPost::getId).collect(Collectors.toList());
      List<RelStuInternshipPost> relStuPosts = relStuInternshipPostDao
          .findByInternshipPostIdInAndIsDeletedFalse(postIds);
      // RelStuInternshipPost.id → studentId
      Map<Integer, Integer> relStuPostIdToStudentId = new HashMap<>();
      for (RelStuInternshipPost rsp : relStuPosts)
        relStuPostIdToStudentId.put(rsp.getId(), rsp.getStudentId());

      // 筛出校内导师（jobCode = SCHOOL_TEACHER）
      List<RelTeacherStudent> allTeacherStudents = relTeacherStudentDao
          .findByInternshipIdAndIsDeletedFalse(internshipId);
      Set<Integer> candidateTeacherIds = allTeacherStudents.stream()
          .map(RelTeacherStudent::getTeacherId).filter(Objects::nonNull).collect(Collectors.toSet());
      Map<Integer, ViewBaseUser> teacherIdToUser = new HashMap<>();
      for (ViewBaseUser u : viewBaseUserDao.getByIdInAndIsDeletedFalse(candidateTeacherIds))
        teacherIdToUser.put(u.getId(), u);
      Set<Integer> schoolTeacherIds = teacherIdToUser.values().stream()
          .filter(u -> Constant.USER_JOB_CODE.SCHOOL_TEACHER.equals(u.getJobCode()))
          .map(ViewBaseUser::getId).collect(Collectors.toSet());

      // studentId → 校内导师 ID；校内导师 ID → 所管 studentId 集合
      // RelTeacherStudent.relInternshipId 存的是 RelStuInternshipPost.id，用此查 studentId
      Map<Integer, Integer> studentIdToSchoolTeacherId = new HashMap<>();
      Map<Integer, Set<Integer>> schoolTeacherIdToStudentIds = new HashMap<>();
      for (RelTeacherStudent rts : allTeacherStudents) {
        if (rts.getTeacherId() == null || !schoolTeacherIds.contains(rts.getTeacherId())
            || rts.getRelInternshipId() == null)
          continue;
        Integer studentId = relStuPostIdToStudentId.get(rts.getRelInternshipId());
        if (studentId == null)
          continue;
        studentIdToSchoolTeacherId.put(studentId, rts.getTeacherId());
        schoolTeacherIdToStudentIds.computeIfAbsent(rts.getTeacherId(), k -> new HashSet<>()).add(studentId);
      }

      // userId 过滤：只返回该校内导师名下的学生
      Set<Integer> allowedStuIds = userId != null
          ? schoolTeacherIdToStudentIds.getOrDefault(userId, Collections.emptySet())
          : null;

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
        Map<Integer, MainDiary> stuPostIdToDiary = new HashMap<>();
        for (MainDiary d : diaries) {
          if (d.getRelationId() != null)
            stuPostIdToDiary.put(d.getRelationId(), d);
        }

        Map<Integer, ViewVerifyMainDiaryMerge> mergeMap = fetchMergeByDiaryIds(
            stuPostIdToDiary.values().stream().map(MainDiary::getId).collect(Collectors.toList()));

        for (RelStuInternshipPost rsp : relStuPosts) {
          JSONObject item = new JSONObject();
          item.put("stuRelationId", rsp.getId());
          item.put("studentId", rsp.getStudentId());
          ViewRelStuInternshipPost v = idToView.get(rsp.getId());
          if (v != null) {
            item.put("studentName", v.getStudentName());
            item.put("studentAccount", v.getStudentAccount());
            item.put("internshipPostName", v.getInternshipPostName());
            item.put("companyName", v.getCompanyName());
            item.put("postDescription", v.getInternshipPostRemarks());
            item.put("className", v.getDepartmentName());
          }
          Integer assignedTeacherId = studentIdToSchoolTeacherId.get(rsp.getStudentId());
          ViewBaseUser teacher = assignedTeacherId != null ? teacherIdToUser.get(assignedTeacherId) : null;
          item.put("teacherId", assignedTeacherId);
          item.put("teacherName", teacher != null ? teacher.getNickName() : null);
          item.put("titleDescription", null);

          MainDiary diary = stuPostIdToDiary.get(rsp.getId());
          Integer diaryId = diary != null ? diary.getId() : null;
          ViewVerifyMainDiaryMerge merge = diaryId != null ? mergeMap.get(diaryId) : null;
          if (merge != null) {
            item.put("majorName", merge.getStudentMajorName());
            if (item.getString("className") == null)
              item.put("className", merge.getStudentDepartmentName());
          } else {
            item.put("majorName", null);
          }
          item.put("diary", buildDiaryJson(diary, merge));
          result.add(item);
        }
      }
      return result; // 校外项目直接返回，不走校内路径
    }

    // 校内路径：题目-学生关联（userId 过滤 + 只保留题目已审核通过的学生）
    List<ViewRelTitleTeacherStudent> titleStudents = viewRelTitleTeacherStudentDao
        .findByInternshipIdAndIsDeletedFalse(internshipId).stream()
        .filter(t -> Integer.valueOf(1).equals(t.getIsAudit()))
        .filter(t -> userId == null || userId.equals(t.getTeacherId()))
        .collect(Collectors.toList());
    if (titleStudents.isEmpty())
      return result;

    List<Integer> relTitleStudentIds = titleStudents.stream()
        .map(ViewRelTitleTeacherStudent::getRelTitleStudentId).collect(Collectors.toList());

    // 按 relationId（=RelTitleStudent.id）+ tableName + periodId 查日志
    List<MainDiary> diaries = mainDiaryDao
        .findByRelationIdInAndTableNameAndPeriodIdAndIsDeletedFalse(
            relTitleStudentIds, "RelTitleStudent", periodId);
    Map<Integer, MainDiary> relTitleStudentIdToDiary = new HashMap<>();
    for (MainDiary d : diaries) {
      if (d.getRelationId() != null)
        relTitleStudentIdToDiary.put(d.getRelationId(), d);
    }

    Map<Integer, ViewVerifyMainDiaryMerge> mergeMap = fetchMergeByDiaryIds(
        relTitleStudentIdToDiary.values().stream().map(MainDiary::getId).collect(Collectors.toList()));

    Set<Integer> stuIdsWithoutDiary = titleStudents.stream()
        .filter(t -> !relTitleStudentIdToDiary.containsKey(t.getRelTitleStudentId()))
        .map(ViewRelTitleTeacherStudent::getStuId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    Map<Integer, ViewBaseUser> studentIdToUser = batchLoadUsers(stuIdsWithoutDiary);

    for (ViewRelTitleTeacherStudent rts : titleStudents) {
      JSONObject item = new JSONObject();
      item.put("titleRelationId", rts.getRelTitleStudentId());
      item.put("studentId", rts.getStuId());
      item.put("studentName", rts.getStudentName());
      item.put("studentAccount", rts.getStudentAccount());
      item.put("titleName", rts.getName());
      item.put("titleDescription", rts.getRemarks());
      item.put("teacherId", rts.getTeacherId());
      item.put("teacherName", rts.getTeacherName());
      item.put("companyName", null);

      MainDiary diary = relTitleStudentIdToDiary.get(rts.getRelTitleStudentId());
      Integer diaryId = diary != null ? diary.getId() : null;
      ViewVerifyMainDiaryMerge merge = diaryId != null ? mergeMap.get(diaryId) : null;
      if (merge != null) {
        item.put("majorName", merge.getStudentMajorName());
        item.put("className", merge.getStudentDepartmentName());
      } else {
        ViewBaseUser bu = studentIdToUser.get(rts.getStuId());
        item.put("majorName", null);
        item.put("className", bu != null ? bu.getDepartmentName() : null);
      }
      item.put("postDescription", null);
      item.put("diary", buildDiaryJson(diary, merge));
      result.add(item);
    }
    return result;
  }

  @Override
  public JSONObject getReviewOptions(Integer currentUserId) {
    Page<Object> page = queryReviewableDiaryPage(currentUserId, null, 1, LARGE_PAGE_SIZE,
        Sort.by(Sort.Direction.ASC, "periodId"));
    List<JSONObject> rows = page.getContent().stream()
        .map(FastJsonUtil::toJson)
        .collect(Collectors.toList());

    Set<Integer> periodIds = rows.stream()
        .map(j -> j.getInteger("periodId"))
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    Map<Integer, MainDiaryPeriod> periodMap = loadPeriodMap(periodIds);

    Map<Integer, JSONObject> internshipMap = new LinkedHashMap<>();
    Map<Integer, Set<Integer>> internshipPeriodIds = new HashMap<>();
    for (JSONObject row : rows) {
      Integer periodId = row.getInteger("periodId");
      MainDiaryPeriod period = periodId != null ? periodMap.get(periodId) : null;
      if (period == null || period.getInternshipId() == null)
        continue;
      Integer internshipId = period.getInternshipId();
      JSONObject internship = internshipMap.computeIfAbsent(internshipId, id -> {
        JSONObject obj = new JSONObject();
        obj.put("internshipId", id);
        obj.put("internshipName", row.getString("internshipName"));
        obj.put("periods", new JSONArray());
        return obj;
      });
      Set<Integer> seenPeriods = internshipPeriodIds.computeIfAbsent(internshipId, id -> new HashSet<>());
      if (seenPeriods.add(period.getId())) {
        JSONObject periodJson = new JSONObject();
        periodJson.put("periodId", period.getId());
        periodJson.put("periodIndex", period.getPeriodIndex());
        periodJson.put("beginTime", period.getBeginTime());
        periodJson.put("endTime", period.getEndTime());
        internship.getJSONArray("periods").add(periodJson);
      }
    }

    JSONArray internships = new JSONArray();
    internshipMap.values().forEach(internships::add);
    JSONObject result = new JSONObject();
    result.put("internships", internships);
    return result;
  }

  @Override
  public JSONObject getReviewStudents(Integer internshipId, Integer periodId, Integer page, Integer size,
      Integer currentUserId) {
    MainDiaryPeriod period = mainDiaryPeriodDao.getByIdAndIsDeletedFalse(periodId);
    if (period == null)
      throw BaseResponse.parameterInvalid.error("期次不存在");
    if (!Objects.equals(period.getInternshipId(), internshipId))
      throw BaseResponse.parameterInvalid.error("periodId 不属于指定 internshipId");

    int pageNum = page == null || page < 1 ? 1 : page;
    int pageSize = size == null || size < 1 ? 20 : Math.min(size, 200);
    Page<Object> diaryPage = queryReviewableDiaryPage(currentUserId, periodId, pageNum, pageSize,
        Sort.by(Sort.Direction.ASC, "studentAccount"));
    JSONObject summary = buildReviewSummary(internshipId, periodId, currentUserId, diaryPage.getTotalElements());

    JSONArray students = new JSONArray();
    for (Object obj : diaryPage.getContent()) {
      JSONObject row = FastJsonUtil.toJson(obj);
      JSONObject item = new JSONObject();
      item.put("studentId", row.getInteger("studentId"));
      item.put("studentName", row.getString("studentName"));
      item.put("studentAccount", row.getString("studentAccount"));
      item.put("internshipId", internshipId);
      item.put("periodId", periodId);
      item.put("internshipPostName", row.getString("internshipPostName"));
      item.put("titleName", row.getString("titleName"));
      item.put("diary", normalizeDiaryJson(row));
      students.add(item);
    }

    JSONObject result = new JSONObject();
    result.put("total", diaryPage.getTotalElements());
    result.putAll(summary);
    result.put("students", students);
    return result;
  }

  // ==================== 工具方法 ====================

  @SuppressWarnings("unchecked")
  private Page<Object> queryReviewableDiaryPage(Integer currentUserId, Integer periodId, Integer page, Integer size,
      Sort sort) {
    JSONObject searchKeys = new JSONObject();
    searchKeys.put("verifyUserId", String.valueOf(currentUserId));
    searchKeys.put("isAudit", Constant.AUDIT_STATUS.SUBMIT);
    searchKeys.put("submit", Boolean.TRUE);
    if (periodId != null)
      searchKeys.put("periodId", periodId);
    Map<String, String> regMap = new HashMap<>();
    regMap.put("verifyUserId", Constant.FIND_IN);
    return (Page<Object>) iCommonService.getSomeRecords(
        "ViewVerifyMainDiaryMerge", searchKeys, regMap, sort, page, size);
  }

  private Map<Integer, MainDiaryPeriod> loadPeriodMap(Set<Integer> periodIds) {
    if (periodIds.isEmpty())
      return Collections.emptyMap();
    Map<Integer, MainDiaryPeriod> result = new HashMap<>();
    for (MainDiaryPeriod period : mainDiaryPeriodDao.findByIdInAndIsDeletedFalse(periodIds))
      result.put(period.getId(), period);
    return result;
  }

  @SuppressWarnings("unchecked")
  private JSONObject buildReviewSummary(Integer internshipId, Integer periodId, Integer currentUserId,
      long pendingReviewCount) {
    List<JSONObject> rows = (List<JSONObject>) getPeriodStudents(internshipId, periodId, currentUserId);
    long submittedCount = 0;
    long notSubmittedCount = 0;
    for (JSONObject row : rows) {
      JSONObject diary = row.getJSONObject("diary");
      if (diary != null && Boolean.TRUE.equals(diary.getBoolean("submit"))) {
        submittedCount++;
      } else {
        notSubmittedCount++;
      }
    }

    JSONObject summary = new JSONObject();
    summary.put("submittedCount", submittedCount);
    summary.put("notSubmittedCount", notSubmittedCount);
    summary.put("pendingReviewCount", pendingReviewCount);
    return summary;
  }

  private Integer getInternshipIdFromRelation(Integer relationId, String tableName) {
    if ("RelStuInternshipPost".equals(tableName)) {
      Object relStuPostObj = iCommonService.getOneRecordById("RelStuInternshipPost", relationId);
      if (relStuPostObj == null)
        throw BaseResponse.parameterInvalid.error("学生实习岗位记录不存在");
      Integer internshipPostId = FastJsonUtil.toJson(relStuPostObj).getInteger("internshipPostId");
      Object postObj = iCommonService.getOneRecordById("MainInternshipPost", internshipPostId);
      if (postObj == null)
        throw BaseResponse.parameterInvalid.error("实习岗位不存在");
      return FastJsonUtil.toJson(postObj).getInteger("internshipId");
    } else {
      List<ViewRelTitleTeacherStudent> records = viewRelTitleTeacherStudentDao
          .findByRelTitleStudentIdAndIsDeletedFalse(relationId);
      if (records.isEmpty())
        throw BaseResponse.parameterInvalid.error("学生题目关联记录不存在");
      return records.get(0).getInternshipId();
    }
  }

  private Map<Integer, ViewBaseUser> batchLoadUsers(Set<Integer> studentIds) {
    if (studentIds.isEmpty())
      return Collections.emptyMap();
    Map<Integer, ViewBaseUser> map = new HashMap<>();
    for (ViewBaseUser u : viewBaseUserDao.getByIdInAndIsDeletedFalse(studentIds))
      map.put(u.getId(), u);
    return map;
  }

  private Map<Integer, ViewVerifyMainDiaryMerge> fetchMergeByDiaryIds(List<Integer> diaryIds) {
    Map<Integer, ViewVerifyMainDiaryMerge> result = new HashMap<>();
    if (diaryIds.isEmpty())
      return result;
    List<ViewVerifyMainDiaryMerge> merges = viewVerifyMainDiaryMergeDao.findByRelationIdIn(diaryIds);
    for (ViewVerifyMainDiaryMerge m : merges)
      result.put(m.getRelationId(), m);
    return result;
  }

  private JSONObject buildDiaryJson(MainDiary diary, ViewVerifyMainDiaryMerge merge) {
    if (merge != null)
      return normalizeDiaryJson(FastJsonUtil.toJson(merge));
    if (diary == null)
      return null;
    JSONObject stub = new JSONObject();
    stub.put("id", diary.getId());
    stub.put("diaryId", diary.getId());
    stub.put("verifyProcessId", null);
    stub.put("relationId", diary.getId());
    stub.put("diaryRelationId", diary.getRelationId());
    stub.put("diaryTableName", diary.getTableName());
    stub.put("periodId", diary.getPeriodId());
    stub.put("submit", diary.getSubmit());
    stub.put("title", diary.getTitle());
    stub.put("content", diary.getContent());
    stub.put("verifyTypeId", diary.getVerifyTypeId());
    stub.put("currentVerifyTypeId", diary.getCurrentVerifyTypeId());
    stub.put("isAudit", null);
    stub.put("isAllVerified", false);
    return stub;
  }

  private JSONObject normalizeDiaryJson(JSONObject diary) {
    if (diary == null)
      return null;
    Integer diaryId = diary.getInteger("relationId");
    diary.put("diaryId", diaryId);
    diary.put("verifyProcessId", diary.getInteger("id"));
    diary.put("relationId", diaryId);
    return diary;
  }

  // ==================== 期次生成 ====================

  private static final int LARGE_PAGE_SIZE = 100000;

  @Override
  public void generatePeriods(Integer internshipId, LocalDateTime reportStartTime, LocalDateTime reportEndTime,
      String cron, Integer periodNum) {
    if (internshipId == null)
      throw BaseResponse.parameterInvalid.error("internshipId 不能为空");
    if (reportStartTime == null || reportEndTime == null)
      throw BaseResponse.parameterInvalid.error("reportStartTime 和 reportEndTime 不能为空");
    if (!reportEndTime.isAfter(reportStartTime))
      throw BaseResponse.parameterInvalid.error("reportEndTime 必须晚于 reportStartTime");
    boolean hasCron = cron != null && !cron.isBlank();
    boolean hasPeriodNum = periodNum != null && periodNum > 0;
    if (!hasCron && !hasPeriodNum)
      throw BaseResponse.parameterInvalid.error("cron 或 periodNum（>0）至少提供一个");

    // 1. 安全检查：若已有 submit=true 的日志则拒绝重新生成
    List<MainDiaryPeriod> existingPeriods = mainDiaryPeriodDao
        .findByInternshipIdAndIsDeletedFalseOrderByPeriodIndexAsc(internshipId);
    if (!existingPeriods.isEmpty()) {
      List<Integer> existingPeriodIds = existingPeriods.stream()
          .map(MainDiaryPeriod::getId).collect(Collectors.toList());
      if (mainDiaryDao.existsByPeriodIdInAndSubmitTrueAndIsDeletedFalse(existingPeriodIds)) {
        throw BaseResponse.parameterInvalid.error("已有学生提交日志，不允许重新生成期次");
      }
      // 2. 删除旧的 submit=false 日志桩
      List<MainDiary> stubs = mainDiaryDao.findByPeriodIdInAndSubmitFalseAndIsDeletedFalse(existingPeriodIds);
      if (!stubs.isEmpty()) {
        List<Integer> stubIds = stubs.stream().map(MainDiary::getId).collect(Collectors.toList());
        iCommonService.deleteSomeRecords("MainDiary", stubIds);
      }
      // 3. 删除旧期次
      iCommonService.deleteSomeRecords("MainDiaryPeriod", existingPeriodIds);
    }

    // 4. 计算新期次时间区间
    List<LocalDateTime[]> timePairs;
    String freqType;
    if (hasCron) {
      freqType = detectCronFrequency(cron);
      timePairs = switch (freqType) {
        case "DAILY" -> generateDailyPeriods(reportStartTime, reportEndTime);
        case "WEEKLY" -> generateWeeklyPeriods(reportStartTime, reportEndTime);
        case "MONTHLY" -> generateMonthlyPeriods(reportStartTime, reportEndTime);
        default -> throw BaseResponse.parameterInvalid.error("无法识别的 cron 频率：" + cron);
      };
    } else {
      freqType = "EQUAL";
      timePairs = generateEqualPeriods(reportStartTime, reportEndTime, periodNum);
    }
    if (timePairs.isEmpty())
      throw BaseResponse.parameterInvalid.error("时间范围内无法生成有效期次，请检查参数");

    // 5. 保存新期次
    for (int i = 0; i < timePairs.size(); i++) {
      LocalDateTime[] pair = timePairs.get(i);
      JSONObject periodJson = new JSONObject();
      periodJson.put("internshipId", internshipId);
      periodJson.put("periodIndex", i + 1);
      periodJson.put("beginTime", pair[0]);
      periodJson.put("endTime", pair[1]);
      periodJson.put("name", buildPeriodName(i + 1, pair[0], pair[1], freqType));
      iCommonService.saveOneRecord("MainDiaryPeriod", periodJson);
    }

    // 6. 追溯为已审核通过的学生创建日志桩
    retroactivelyCreateDiaryStubs(internshipId);
  }

  @Override
  public void createDiaryEntriesForStudent(Integer relationId, String tableName) {
    if (!isStudentApproved(relationId, tableName))
      return;
    Integer internshipId = getInternshipIdFromRelation(relationId, tableName);
    createDiaryStubs(relationId, tableName, internshipId);
  }

  // ==================== 期次生成辅助方法 ====================

  /**
   * 识别 Spring 6-field cron（second minute hour dayOfMonth month dayOfWeek）的周期类型
   */
  private String detectCronFrequency(String cron) {
    String[] parts = cron.trim().split("\\s+");
    if (parts.length != 6)
      throw BaseResponse.parameterInvalid.error("无效的 cron 表达式，需要 6 个字段（Spring格式）");
    String dayOfMonth = parts[3];
    String dayOfWeek = parts[5];
    if ("?".equals(dayOfWeek) && "*".equals(dayOfMonth))
      return "DAILY";
    if (!"?".equals(dayOfWeek) && "?".equals(dayOfMonth))
      return "WEEKLY";
    if ("?".equals(dayOfWeek) && !"*".equals(dayOfMonth))
      return "MONTHLY";
    throw BaseResponse.parameterInvalid.error(
        "无法识别 cron 频率，示例：DAILY='0 0 0 * * ?'，WEEKLY='0 0 0 ? * MON'，MONTHLY='0 0 0 1 * ?'");
  }

  /** 按日历天切割，首/末期截断至 reportStart/reportEnd */
  private List<LocalDateTime[]> generateDailyPeriods(LocalDateTime start, LocalDateTime end) {
    List<LocalDateTime[]> result = new ArrayList<>();
    LocalDateTime cursor = start.toLocalDate().atStartOfDay();
    LocalDateTime endDay = end.toLocalDate().atStartOfDay();
    while (!cursor.isAfter(endDay)) {
      LocalDateTime periodStart = cursor.isBefore(start) ? start : cursor;
      LocalDateTime periodEnd = cursor.toLocalDate().atTime(23, 59, 59);
      if (periodEnd.isAfter(end))
        periodEnd = end;
      result.add(new LocalDateTime[] { periodStart, periodEnd });
      cursor = cursor.plusDays(1);
    }
    return result;
  }

  /** 按周（周一 00:00 ~ 周日 23:59:59）切割，首/末期截断 */
  private List<LocalDateTime[]> generateWeeklyPeriods(LocalDateTime start, LocalDateTime end) {
    List<LocalDateTime[]> result = new ArrayList<>();
    LocalDateTime monday = start.toLocalDate()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .atStartOfDay();
    while (!monday.isAfter(end)) {
      LocalDateTime sunday = monday.toLocalDate().with(DayOfWeek.SUNDAY).atTime(23, 59, 59);
      LocalDateTime periodStart = monday.isBefore(start) ? start : monday;
      LocalDateTime periodEnd = sunday.isAfter(end) ? end : sunday;
      result.add(new LocalDateTime[] { periodStart, periodEnd });
      monday = monday.plusWeeks(1);
    }
    return result;
  }

  /** 按月（1日 00:00 ~ 月末 23:59:59）切割，首/末期截断 */
  private List<LocalDateTime[]> generateMonthlyPeriods(LocalDateTime start, LocalDateTime end) {
    List<LocalDateTime[]> result = new ArrayList<>();
    LocalDateTime firstOfMonth = start.toLocalDate().withDayOfMonth(1).atStartOfDay();
    while (!firstOfMonth.isAfter(end)) {
      LocalDateTime lastOfMonth = firstOfMonth.toLocalDate()
          .with(TemporalAdjusters.lastDayOfMonth()).atTime(23, 59, 59);
      LocalDateTime periodStart = firstOfMonth.isBefore(start) ? start : firstOfMonth;
      LocalDateTime periodEnd = lastOfMonth.isAfter(end) ? end : lastOfMonth;
      result.add(new LocalDateTime[] { periodStart, periodEnd });
      firstOfMonth = firstOfMonth.plusMonths(1);
    }
    return result;
  }

  /** 等分切割（精确到天）：前 N-1 期等长，末期末端对齐 reportEndTime 所在日 23:59:59 */
  private List<LocalDateTime[]> generateEqualPeriods(LocalDateTime start, LocalDateTime end, int n) {
    List<LocalDateTime[]> result = new ArrayList<>();
    java.time.LocalDate startDate = start.toLocalDate();
    java.time.LocalDate endDate = end.toLocalDate();
    long totalDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
    long daysPerPeriod = Math.max(1, totalDays / n);
    for (int i = 0; i < n - 1; i++) {
      LocalDateTime periodStart = startDate.plusDays((long) i * daysPerPeriod).atStartOfDay();
      LocalDateTime periodEnd = startDate.plusDays((long) (i + 1) * daysPerPeriod - 1).atTime(23, 59, 59);
      result.add(new LocalDateTime[] { periodStart, periodEnd });
    }
    LocalDateTime lastStart = startDate.plusDays((long) (n - 1) * daysPerPeriod).atStartOfDay();
    result.add(new LocalDateTime[] { lastStart, endDate.atTime(23, 59, 59) });
    return result;
  }

  private String buildPeriodName(int index, LocalDateTime begin, LocalDateTime end, String freqType) {
    DateTimeFormatter mmdd = DateTimeFormatter.ofPattern("MM/dd");
    return switch (freqType) {
      case "DAILY" -> "第" + index + "期 (" + begin.toLocalDate() + ")";
      case "WEEKLY" -> "第" + index + "期 (" + begin.format(mmdd) + "-" + end.format(mmdd) + ")";
      case "MONTHLY" -> "第" + index + "期 (" + begin.getYear() + "年" + begin.getMonthValue() + "月)";
      default -> "第" + index + "期";
    };
  }

  /** 查询某学生选岗/选题是否已全部通过审核（isAllVerified=true） */
  @SuppressWarnings("unchecked")
  private boolean isStudentApproved(Integer relationId, String tableName) {
    JSONObject sk = new JSONObject();
    sk.put("isAllVerified", Boolean.TRUE);
    if ("RelStuInternshipPost".equals(tableName)) {
      sk.put("relationId", relationId);
      Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
          "ViewVerifyProcessRelStuInternshipPostMerge", sk, null, Sort.unsorted(), 1, 1);
      return !page.isEmpty();
    } else {
      // RelTitleStudent：merge 视图通过 relTitleStudentId 对应
      sk.put("relTitleStudentId", relationId);
      Page<Object> page = (Page<Object>) iCommonService.getSomeRecords(
          "ViewVerifyProcessRelTitleStudentMerge", sk, null, Sort.unsorted(), 1, 1);
      return !page.isEmpty();
    }
  }

  /** 为指定学生关联的所有期次幂等创建 submit=false 的日志桩 */
  private void createDiaryStubs(Integer relationId, String tableName, Integer internshipId) {
    Integer schoolTeacherId = resolveSchoolTeacherId(relationId, tableName, false);
    if (schoolTeacherId == null) {
      logger.warn("日志占位创建跳过：relationId={}, tableName={} 尚未分配校内导师", relationId, tableName);
      return;
    }
    Integer studentId = resolveStudentId(relationId, tableName);
    List<MainDiaryPeriod> periods = mainDiaryPeriodDao
        .findByInternshipIdAndIsDeletedFalseOrderByPeriodIndexAsc(internshipId);
    if (periods.isEmpty())
      return;
    for (MainDiaryPeriod period : periods) {
      Optional<MainDiary> existing = mainDiaryDao
          .findByRelationIdAndTableNameAndPeriodIdAndIsDeletedFalse(
              relationId, tableName, period.getId());
      if (existing.isPresent()) {
        MainDiary diary = existing.get();
        if (!Boolean.TRUE.equals(diary.getSubmit())) {
          upsertDiaryVerifyProcess(diary.getId(), studentId, String.valueOf(schoolTeacherId),
              Constant.AUDIT_STATUS.SAVE);
        }
        continue;
      }
      JSONObject diaryJson = new JSONObject();
      diaryJson.put("relationId", relationId);
      diaryJson.put("tableName", tableName);
      diaryJson.put("periodId", period.getId());
      diaryJson.put("content", "");
      diaryJson.put("submit", false);
      diaryJson.put("verifyTypeId", Constant.VERIFY_LEVEL.ONE_VERIFY);
      diaryJson.put("currentVerifyTypeId", Constant.VERIFY_LEVEL.NO_VERIFY);
      Object savedDiary = iCommonService.saveOneRecord("MainDiary", diaryJson);
      Integer diaryId = FastJsonUtil.toJson(savedDiary).getInteger("id");
      upsertDiaryVerifyProcess(diaryId, studentId, String.valueOf(schoolTeacherId), Constant.AUDIT_STATUS.SAVE);
    }
  }

  // ==================== 期次 CRUD ====================

  @Override
  public void savePeriod(Integer id, Integer internshipId, LocalDateTime beginTime, LocalDateTime endTime) {
    if (beginTime == null || endTime == null)
      throw BaseResponse.parameterInvalid.error("beginTime 和 endTime 不能为空");
    if (!endTime.isAfter(beginTime))
      throw BaseResponse.parameterInvalid.error("endTime 必须晚于 beginTime");

    if (id == null) {
      // 新增
      if (internshipId == null)
        throw BaseResponse.parameterInvalid.error("新增时 internshipId 不能为空");
      MainDiaryPeriod period = new MainDiaryPeriod();
      period.setInternshipId(internshipId);
      period.setBeginTime(beginTime);
      period.setEndTime(endTime);
      period.setPeriodIndex(0); // 由 rebuildPeriodIndex 修正
      mainDiaryPeriodDao.save(period);
    } else {
      // 编辑
      MainDiaryPeriod period = mainDiaryPeriodDao.getByIdAndIsDeletedFalse(id);
      if (period == null)
        throw BaseResponse.parameterInvalid.error("期次不存在");
      period.setBeginTime(beginTime);
      period.setEndTime(endTime);
      mainDiaryPeriodDao.save(period);
      internshipId = period.getInternshipId();
    }
    rebuildPeriodIndex(internshipId);
  }

  @Override
  public void deletePeriods(List<Integer> ids) {
    if (ids == null || ids.isEmpty())
      throw BaseResponse.parameterInvalid.error("ids 不能为空");

    // 检查是否存在已提交日志
    if (mainDiaryDao.existsByPeriodIdInAndSubmitTrueAndIsDeletedFalse(ids))
      throw BaseResponse.parameterInvalid.error("该期次已有学生提交日志，无法删除");

    // 收集 internshipId 用于后续重建
    List<MainDiaryPeriod> periods = mainDiaryPeriodDao.findByIdInAndIsDeletedFalse(ids);
    Set<Integer> internshipIds = periods.stream()
        .map(MainDiaryPeriod::getInternshipId).filter(Objects::nonNull).collect(Collectors.toSet());

    // 删除关联的草稿桩
    List<MainDiary> stubs = mainDiaryDao.findByPeriodIdInAndSubmitFalseAndIsDeletedFalse(ids);
    if (!stubs.isEmpty()) {
      List<Integer> stubIds = stubs.stream().map(MainDiary::getId).collect(Collectors.toList());
      iCommonService.deleteSomeRecords("MainDiary", stubIds);
    }

    // 删除期次
    iCommonService.deleteSomeRecords("MainDiaryPeriod", ids);

    // 重建各项目的 periodIndex
    for (Integer iid : internshipIds) {
      rebuildPeriodIndex(iid);
    }
  }

  /** 按 beginTime 重建同实习项目下所有期次的 periodIndex（从 1 开始连续） */
  private void rebuildPeriodIndex(Integer internshipId) {
    List<MainDiaryPeriod> periods = mainDiaryPeriodDao
        .findByInternshipIdAndIsDeletedFalseOrderByBeginTimeAsc(internshipId);
    for (int i = 0; i < periods.size(); i++) {
      periods.get(i).setPeriodIndex(i + 1);
    }
    mainDiaryPeriodDao.saveAll(periods);
  }

  @Override
  public void initDiaryByInternship(Integer internshipId) {
    if (internshipId == null)
      throw BaseResponse.parameterInvalid.error("internshipId 不能为空");

    List<MainDiaryPeriod> periods = mainDiaryPeriodDao
        .findByInternshipIdAndIsDeletedFalseOrderByPeriodIndexAsc(internshipId);
    if (periods.isEmpty())
      return; // 期次尚未生成，静默跳过

    // 校外路径：RelStuInternshipPost
    List<MainInternshipPost> posts = mainInternshipPostDao.findByInternshipIdAndIsDeletedFalse(internshipId);
    if (!posts.isEmpty()) {
      List<Integer> postIds = posts.stream().map(MainInternshipPost::getId).collect(Collectors.toList());
      relStuInternshipPostDao.findByInternshipPostIdInAndIsDeletedFalse(postIds)
          .forEach(rsp -> createDiaryStubs(rsp.getId(), "RelStuInternshipPost", internshipId));
    }

    // 校内路径：RelTitleStudent（通过 ViewRelTitleTeacherStudent 关联到 internshipId）
    viewRelTitleTeacherStudentDao.findByInternshipIdAndIsDeletedFalse(internshipId).stream()
        .map(ViewRelTitleTeacherStudent::getRelTitleStudentId)
        .filter(Objects::nonNull)
        .distinct()
        .forEach(relId -> createDiaryStubs(relId, "RelTitleStudent", internshipId));
  }

  /** 追溯为当前实习项目所有已通过审核的学生创建日志桩 */
  @SuppressWarnings("unchecked")
  private void retroactivelyCreateDiaryStubs(Integer internshipId) {
    // 校外路径：RelStuInternshipPost
    JSONObject stuSk = new JSONObject();
    stuSk.put("internshipId", internshipId);
    stuSk.put("isAllVerified", Boolean.TRUE);
    Page<Object> stuPage = (Page<Object>) iCommonService.getSomeRecords(
        "ViewVerifyProcessRelStuInternshipPostMerge", stuSk, null, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
    stuPage.getContent().stream()
        .map(FastJsonUtil::toJson)
        .map(j -> j.getInteger("relationId"))
        .filter(Objects::nonNull)
        .distinct()
        .forEach(relId -> createDiaryStubs(relId, "RelStuInternshipPost", internshipId));

    // 校内路径：RelTitleStudent（internshipId 来自 rel_title_teacher）
    JSONObject titleSk = new JSONObject();
    titleSk.put("internshipId", internshipId);
    titleSk.put("isAllVerified", Boolean.TRUE);
    Page<Object> titlePage = (Page<Object>) iCommonService.getSomeRecords(
        "ViewVerifyProcessRelTitleStudentMerge", titleSk, null, Sort.unsorted(), 1, LARGE_PAGE_SIZE);
    titlePage.getContent().stream()
        .map(FastJsonUtil::toJson)
        .map(j -> j.getInteger("relTitleStudentId"))
        .filter(Objects::nonNull)
        .distinct()
        .forEach(relId -> createDiaryStubs(relId, "RelTitleStudent", internshipId));
  }

  // ==================== 批量提交日志 ====================

  @Override
  public JSONObject submitDiaryBatch(List<JSONObject> nodes, Integer currentUserId) {
    if (nodes == null || nodes.isEmpty()) {
      throw BaseResponse.parameterInvalid.error("nodes 不能为空");
    }

    int successCount = 0;
    List<JSONObject> results = new ArrayList<>();

    for (JSONObject node : nodes) {
      Integer relationId = node.getInteger("relationId");
      String tableName = node.getString("tableName");
      Integer periodId = node.getInteger("periodId");
      String title = node.getString("title");
      String content = node.getString("content");
      Boolean submit = node.getBoolean("submit");

      JSONObject item = new JSONObject();
      item.put("periodId", periodId);

      try {
        if (relationId == null) throw BaseResponse.parameterInvalid.error("relationId 不能为空");
        if (tableName == null || tableName.isBlank()) throw BaseResponse.parameterInvalid.error("tableName 不能为空");
        if (periodId == null) throw BaseResponse.parameterInvalid.error("periodId 不能为空");

        Integer diaryId = submitDiary(relationId, tableName, periodId, title, content, submit, currentUserId);
        item.put("diaryId", diaryId);
        item.put("message", "操作成功");
        successCount++;
      } catch (Exception e) {
        item.put("message", e.getMessage());
      }

      results.add(item);
    }

    JSONObject response = new JSONObject();
    response.put("successCount", successCount);
    response.put("results", results);
    return response;
  }

  // ==================== AI 批改（Coze） ====================

  @Override
  public JSONObject aiReviewDiary(Integer diaryId) {
    MainDiary diary = mainDiaryDao.findById(diaryId)
        .filter(d -> !Boolean.TRUE.equals(d.getIsDeleted()))
        .orElseThrow(() -> BaseResponse.parameterInvalid.error("日志不存在"));
    if (!Boolean.TRUE.equals(diary.getSubmit())) {
      throw BaseResponse.parameterInvalid.error("仅已提交的日志可进行 AI 批改");
    }

    markAiReviewStatus(diaryId, "RUNNING", null, null);

    try {
      SysOssFile attachment = findReviewableAttachment(diaryId)
          .orElseThrow(() -> BaseResponse.parameterInvalid.error("未找到 pdf/docx 格式的日志附件"));

      JSONObject cozeResult;
      if (cozeProperties.useFileIdInput()) {
        byte[] fileBytes = minIOUtils.readBytes(attachment.getBucketName(), attachment.getOssPath());
        cozeResult = cozeWorkflowService.runDiaryReview(fileBytes, attachment.getFileName());
      } else {
        String fileUrl = minIOUtils.presignedPreviewUrl(
            attachment.getBucketName(),
            attachment.getOssPath(),
            cozeProperties.getFileUrlExpireSeconds());
        cozeResult = cozeWorkflowService.runDiaryReviewByUrl(fileUrl, attachment.getFileName());
      }
      String output = cozeResult.getString("output");
      BigDecimal score = cozeResult.getBigDecimal("score");
      String rawData = cozeResult.getString("rawData");

      JSONObject update = new JSONObject();
      update.put("id", diaryId);
      update.put("aiReviewComment", output);
      update.put("aiReviewScore", score);
      update.put("aiReviewStatus", "SUCCESS");
      update.put("aiReviewTime", new Date());
      update.put("aiReviewRaw", rawData);
      iCommonService.saveOneRecord("MainDiary", update);

      JSONObject response = new JSONObject();
      response.put("diaryId", diaryId);
      response.put("aiReviewStatus", "SUCCESS");
      response.put("output", output);
      response.put("score", score);
      response.put("aiReviewComment", output);
      response.put("aiReviewScore", score);
      response.put("aiReviewTime", update.get("aiReviewTime"));
      response.put("debugUrl", cozeResult.getString("debugUrl"));
      return response;
    } catch (Exception e) {
      markAiReviewStatus(diaryId, "FAILED", null, e.getMessage());
      if (e instanceof BaseException) {
        throw e;
      }
      throw BaseResponse.moreInfoError.error("AI 批改失败: " + e.getMessage());
    }
  }

  private void markAiReviewStatus(Integer diaryId, String status, String comment, String raw) {
    JSONObject update = new JSONObject();
    update.put("id", diaryId);
    update.put("aiReviewStatus", status);
    if (comment != null) {
      update.put("aiReviewComment", comment);
    }
    if (raw != null) {
      update.put("aiReviewRaw", raw);
    }
    if ("SUCCESS".equals(status)) {
      update.put("aiReviewTime", new Date());
    }
    iCommonService.saveOneRecord("MainDiary", update);
  }

  private Optional<SysOssFile> findReviewableAttachment(Integer diaryId) {
    for (String tableName : DIARY_OSS_TABLE_NAMES) {
      List<SysOssFile> files = sysOssFileDao
          .findByRelationIdsAndTableNameAndIsDeletedFalse(diaryId, tableName);
      Optional<SysOssFile> match = files.stream()
          .filter(this::isReviewableFile)
          .findFirst();
      if (match.isPresent()) {
        return match;
      }
    }
    return Optional.empty();
  }

  private boolean isReviewableFile(SysOssFile file) {
    if (file.getSuffix() == null) {
      return false;
    }
    return REVIEWABLE_SUFFIXES.contains(file.getSuffix().toLowerCase(Locale.ROOT));
  }
}

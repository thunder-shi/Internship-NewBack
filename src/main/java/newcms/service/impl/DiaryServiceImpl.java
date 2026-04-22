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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
      String title, String content, Boolean submit, Integer currentUserId) {
    boolean isSubmit = Boolean.TRUE.equals(submit);

    // 查找已有日志（同一 relationId+tableName+periodId 只存一条）
    Optional<MainDiary> existingOpt = mainDiaryDao.findByRelationIdAndTableNameAndPeriodIdAndIsDeletedFalse(relationId,
        tableName, periodId);

    if (existingOpt.isPresent()) {
      MainDiary existing = existingOpt.get();
      Integer diaryId = existing.getId();
      boolean wasDraft = !Boolean.TRUE.equals(existing.getSubmit());

      // 更新标题、内容、提交状态、审核进度
      JSONObject updateDiary = new JSONObject();
      updateDiary.put("id", diaryId);
      updateDiary.put("title", title);
      updateDiary.put("content", content);
      updateDiary.put("submit", isSubmit);
      updateDiary.put("currentVerifyTypeId",
          isSubmit ? Constant.VERIFY_LEVEL.ONE_VERIFY : Constant.VERIFY_LEVEL.NO_VERIFY);
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
    diaryJson.put("title", title);
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
    // 按 diaryId 加锁，防止并发提交同一期日志时重复创建审核行（CONC-06）
    Object lock = DIARY_LOCKS.computeIfAbsent(diaryId, k -> new Object());
    synchronized (lock) {

    List<MainVerifyProcess> existing = mainVerifyProcessDao.findByRelationIdAndTableNameAndIsDeletedFalse(diaryId,
        "MainDiary");
    boolean hasPending = existing.stream()
        .anyMatch(p -> p.getIsAudit() != null && p.getIsAudit() == Constant.AUDIT_STATUS.SUBMIT);
    if (hasPending)
      return;

    // 读取日志的第一级审核角色
    JSONObject diaryJson = FastJsonUtil.toJson(iCommonService.getOneRecordById("MainDiary", diaryId));
    Integer verifyFirstRoleId = diaryJson.getInteger("verifyFirstRoleId");

    String verifyUserId;
    if (verifyFirstRoleId != null && verifyFirstRoleId > 0) {
      // 角色配置存在：按角色+学校范围查找所有可审核人
      verifyUserId = iVerifyProcessService.GetVerifyUserId(verifyFirstRoleId, currentUserId);
    } else {
      // 角色未配置：回落到具体分配的校内导师
      Integer teacherId = findSchoolTeacherId(relationId, tableName);
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

    } // end synchronized
  }

  /**
   * 根据 relationId+tableName 查找负责审批该日志的校内导师 ID。
   */
  private Integer findSchoolTeacherId(Integer relationId, String tableName) {
    if ("RelStuInternshipPost".equals(tableName)) {
      // RelTeacherStudent.relInternshipId 存的是 RelStuInternshipPost.id，即 relationId 本身
      List<RelTeacherStudent> teacherStudents = relTeacherStudentDao
          .findByRelInternshipIdAndIsDeletedFalse(relationId);
      Set<Integer> candidateIds = teacherStudents.stream()
          .map(RelTeacherStudent::getTeacherId).filter(Objects::nonNull).collect(Collectors.toSet());
      return viewBaseUserDao.getByIdInAndIsDeletedFalse(candidateIds).stream()
          .filter(u -> Constant.USER_JOB_CODE.SCHOOL_TEACHER.equals(u.getJobCode()))
          .map(ViewBaseUser::getId)
          .findFirst()
          .orElseThrow(() -> BaseResponse.parameterInvalid.error("该学生尚未分配校内导师"));
    } else {
      // RelTitleStudent（校内）
      List<ViewRelTitleTeacherStudent> records = viewRelTitleTeacherStudentDao
          .findByRelTitleStudentIdAndIsDeletedFalse(relationId);
      if (records.isEmpty())
        throw BaseResponse.parameterInvalid.error("学生题目关联记录不存在");
      return records.get(0).getTeacherId();
    }
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
        if (merge != null) {
          // 已提交，有审核记录，返回完整 merge 视图
          item.put("diary", FastJsonUtil.toJson(merge));
        } else {
          // 草稿桩（submit=false），尚无审核记录，返回基础字段
          JSONObject stub = new JSONObject();
          stub.put("id", diary.getId());
          stub.put("submit", diary.getSubmit());
          stub.put("content", diary.getContent());
          item.put("diary", stub);
        }
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
        Map<Integer, Integer> stuPostIdToDiaryId = new HashMap<>();
        for (MainDiary d : diaries) {
          if (d.getRelationId() != null)
            stuPostIdToDiaryId.put(d.getRelationId(), d.getId());
        }

        Map<Integer, ViewVerifyMainDiaryMerge> mergeMap = fetchMergeByDiaryIds(
            new ArrayList<>(stuPostIdToDiaryId.values()));

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

          Integer diaryId = stuPostIdToDiaryId.get(rsp.getId());
          ViewVerifyMainDiaryMerge merge = diaryId != null ? mergeMap.get(diaryId) : null;
          if (merge != null) {
            item.put("majorName", merge.getStudentMajorName());
            if (item.getString("className") == null)
              item.put("className", merge.getStudentDepartmentName());
          } else {
            item.put("majorName", null);
          }
          item.put("diary", merge != null ? FastJsonUtil.toJson(merge) : null);
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
    Map<Integer, Integer> relTitleStudentIdToDiaryId = new HashMap<>();
    for (MainDiary d : diaries) {
      if (d.getRelationId() != null)
        relTitleStudentIdToDiaryId.put(d.getRelationId(), d.getId());
    }

    Map<Integer, ViewVerifyMainDiaryMerge> mergeMap = fetchMergeByDiaryIds(
        new ArrayList<>(relTitleStudentIdToDiaryId.values()));

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
      item.put("studentAccount", rts.getStudentAccount());
      item.put("titleName", rts.getName());
      item.put("titleDescription", rts.getRemarks());
      item.put("teacherId", rts.getTeacherId());
      item.put("teacherName", rts.getTeacherName());
      item.put("companyName", null);

      Integer diaryId = relTitleStudentIdToDiaryId.get(rts.getRelTitleStudentId());
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
      item.put("diary", merge != null ? FastJsonUtil.toJson(merge) : null);
      result.add(item);
    }
    return result;
  }

  // ==================== 工具方法 ====================

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
    List<MainDiaryPeriod> periods = mainDiaryPeriodDao
        .findByInternshipIdAndIsDeletedFalseOrderByPeriodIndexAsc(internshipId);
    if (periods.isEmpty())
      return;
    for (MainDiaryPeriod period : periods) {
      Optional<MainDiary> existing = mainDiaryDao
          .findByRelationIdAndTableNameAndPeriodIdAndIsDeletedFalse(
              relationId, tableName, period.getId());
      if (existing.isPresent())
        continue;
      JSONObject diaryJson = new JSONObject();
      diaryJson.put("relationId", relationId);
      diaryJson.put("tableName", tableName);
      diaryJson.put("periodId", period.getId());
      diaryJson.put("content", "");
      diaryJson.put("submit", false);
      diaryJson.put("verifyTypeId", Constant.VERIFY_LEVEL.ONE_VERIFY);
      diaryJson.put("currentVerifyTypeId", Constant.VERIFY_LEVEL.NO_VERIFY);
      iCommonService.saveOneRecord("MainDiary", diaryJson);
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
}

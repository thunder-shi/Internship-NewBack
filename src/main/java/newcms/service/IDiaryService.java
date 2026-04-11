package newcms.service;

import newcms.entity.db.MainDiaryPeriod;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public interface IDiaryService {

    /**
     * 学生提交/保存实习日志。
     * 同一 relationId+tableName+periodId 只存一条，重复调用时就地更新。
     * submit=false 时仅保存草稿，不创建审核记录；submit=true 时提交审核，若无待审核记录则新建。
     *
     * @param relationId    关联 ID（RelStuInternshipPost.id 或 RelTitleStudent.id）
     * @param tableName     关联表名（"RelStuInternshipPost" 或 "RelTitleStudent"）
     * @param periodId      期次 ID（MainDiaryPeriod.id）
     * @param content       日志文字内容
     * @param submit        false=保存草稿，true=提交审核
     * @param currentUserId 当前登录用户 ID（学生）
     * @return 日志记录的 id（用于后续文件上传）
     */
    Integer submitDiary(Integer relationId, String tableName, Integer periodId,
                        String content, Boolean submit, Integer currentUserId);

    /**
     * 获取学生某个岗位/题目的所有期次及日志审核状态。
     *
     * @param relationId 关联 ID
     * @param tableName  关联表名
     * @return 各期次列表，每项包含 period 信息和 diary（ViewVerifyMainDiaryMerge，未提交时为 null）
     */
    Object getDiaryPeriods(Integer relationId, String tableName);

    /**
     * 获取某实习项目的所有期次定义（老师端期次选择器使用）。
     *
     * @param internshipId 实习项目 ID
     * @return MainDiaryPeriod 列表，按 periodIndex 升序
     */
    List<MainDiaryPeriod> getInternshipPeriods(Integer internshipId);

    /**
     * 老师查看某实习项目某期所有学生的日志提交及审核状态。
     * userId 不为 null 时只返回该老师名下的学生；为 null 时返回全部（超管视角）。
     *
     * @param internshipId 实习项目 ID
     * @param periodId     期次 ID（MainDiaryPeriod.id）
     * @param userId       当前老师的用户 ID（可为 null）
     */
    Object getPeriodStudents(Integer internshipId, Integer periodId, Integer userId);

    /**
     * 生成实习项目的日志期次（MainDiaryPeriod），并为已审核通过的学生追溯创建 submit=false 的日志桩。
     * <p>
     * 若已有任意 submit=true 的日志记录，则拒绝重新生成（防止覆盖学生已提交的内容）。
     * 否则删除旧的 submit=false 桩和旧期次，重新计算并保存。
     * <p>
     * 期次生成规则：
     * <ul>
     *   <li>cron != null：解析 Spring 6-field cron，识别 DAILY / WEEKLY / MONTHLY 频率，按日历边界切割</li>
     *   <li>cron == null：按 periodNum 等分 [reportStartTime, reportEndTime]，前 N-1 期等长，末期对齐 reportEndTime</li>
     * </ul>
     *
     * @param internshipId    实习项目 ID
     * @param reportStartTime 报告周期起始时间
     * @param reportEndTime   报告周期结束时间
     * @param cron            Spring 6-field cron 表达式（与 periodNum 二选一）
     * @param periodNum       期数（与 cron 二选一，必须 > 0）
     */
    void generatePeriods(Integer internshipId, LocalDateTime reportStartTime, LocalDateTime reportEndTime,
                         String cron, Integer periodNum);

    /**
     * 学生选岗/选题全部审核通过后，为该学生的所有期次创建 submit=false 的日志桩（幂等）。
     * 若期次尚未生成，则静默跳过。
     * 若该学生尚未全部通过审核，则静默跳过（不报错）。
     *
     * @param relationId RelStuInternshipPost.id 或 RelTitleStudent.id
     * @param tableName  "RelStuInternshipPost" 或 "RelTitleStudent"
     */
    void createDiaryEntriesForStudent(Integer relationId, String tableName);

    /**
     * 新增或编辑单条期次记录，保存后按 beginTime 重建同项目所有期次的 periodIndex。
     * id=null 时新增，id 有值时更新（只允许修改 beginTime / endTime）。
     *
     * @param id           期次 ID（null=新增，非null=编辑）
     * @param internshipId 实习项目 ID（新增时必填）
     * @param beginTime    期次开始时间
     * @param endTime      期次结束时间
     */
    void savePeriod(Integer id, Integer internshipId, LocalDateTime beginTime, LocalDateTime endTime);

    /**
     * 删除期次（批量）。
     * 若任意期次存在 submit=true 的日志则拒绝删除；否则先删关联的草稿桩，再删期次，最后重建 periodIndex。
     *
     * @param ids 要删除的期次 ID 列表
     */
    void deletePeriods(List<Integer> ids);
}

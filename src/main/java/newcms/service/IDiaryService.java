package newcms.service;

import newcms.entity.db.MainDiaryPeriod;
import org.springframework.stereotype.Service;

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
}

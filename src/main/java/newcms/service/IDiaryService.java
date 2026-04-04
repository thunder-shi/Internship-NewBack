package newcms.service;

import org.springframework.stereotype.Service;

@Service
public interface IDiaryService {

    /**
     * 学生提交实习日志（重复提交时旧日志及审核记录软删除，新建一条）。
     * 校外实习传 stuInternshipPostId，校内实习传 relTitleStudentId，二者不能同时为空。
     *
     * @param stuInternshipPostId 校外：学生实习岗位关联ID
     * @param relTitleStudentId   校内：学生题目关联ID
     * @param periodIndex         期数（1-based）
     * @param content             日志文字内容
     * @param currentUserId       当前登录用户ID（学生）
     * @return 新建的 MainDiary id（用于后续文件上传）
     */
    Integer submitDiary(Integer stuInternshipPostId, Integer relTitleStudentId,
                        Integer periodIndex, String content, Integer currentUserId);

    /**
     * 获取学生某个岗位/题目的所有期数及日志审核状态。
     * 校外传 stuInternshipPostId，校内传 relTitleStudentId。
     *
     * @param stuInternshipPostId 校外：学生实习岗位关联ID
     * @param relTitleStudentId   校内：学生题目关联ID
     * @return 各期数列表，每项包含 periodIndex 和 diary（ViewMainDiaryMerge，未提交时为 null）
     */
    Object getDiaryPeriods(Integer stuInternshipPostId, Integer relTitleStudentId);

    /**
     * 获取某实习项目的总期数（老师端期数选择器使用）。
     * 计算规则与学生端 getDiaryPeriods 一致：从流程最早 startTime 到当前时间按 cron 推算。
     *
     * @param internshipId 实习项目ID
     * @return 总期数（int），0 表示尚未开始或无流程配置
     */
    int getInternshipPeriodCount(Integer internshipId);

    /**
     * 老师查看某实习项目某期所有学生的日志提交及审核状态。
     * userId 不为 null 时只返回该老师名下的学生；为 null 时返回全部（超管视角）。
     *
     * @param internshipId 实习项目ID
     * @param periodIndex  期数
     * @param userId       当前老师的用户ID（可为 null）
     */
    Object getPeriodStudents(Integer internshipId, Integer periodIndex, Integer userId);
}

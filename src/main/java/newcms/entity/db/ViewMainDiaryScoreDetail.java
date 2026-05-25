package newcms.entity.db;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 实习日志评分明细视图
 * 合并 main_diary_score_detail + view_main_diary（含学生信息）+ main_diary_period（含 internshipId）
 */
@Getter
@Setter
@Entity(name = "view_main_diary_score_detail")
public class ViewMainDiaryScoreDetail extends BaseInfo {

    // ===== 来自 main_diary_score_detail =====
    private Integer diaryId;
    private Integer levelOrder;
    private BigDecimal weight;
    private BigDecimal maxScore;
    private BigDecimal score;
    private String verifyUserId;
    private String verifyUserName;

    // ===== 来自 view_main_diary =====
    private String title;
    private Integer relationId;
    private String tableName;
    private Integer periodId;
    private Integer periodIndex;
    private LocalDateTime beginTime;
    private LocalDateTime endTime;
    private Integer studentId;
    private String studentName;
    private String studentAccount;

    // ===== 来自 main_diary_period =====
    private Integer internshipId;
}

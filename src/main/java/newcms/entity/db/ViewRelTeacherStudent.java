package newcms.entity.db;

import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import newcms.entity.base.NameRemarkInfo;

/**
 * 教师与学生实习关联视图
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
public class ViewRelTeacherStudent extends NameRemarkInfo {
    /**
     * 外键，关联表1（教师）
     */
    private Integer teacherId;
    
    /**
     * 外键，关联表16（实习）
     */
    private Integer internshipId;
    
    /**
     * 外键，关联表11（实习批次），默认0
     */
    // private Integer relInternshipId;
    
    /**
     * 教师名称（来自BaseUser）
     */
    private String teacherName;
    
    /**
     * 实习项目名称（来自MainInternship）
     */
    private String internshipName;

    /**
     * 审核状态：-1 待提交、0 待审核、1 审核通过、2 审核不通过、3 审核退回。
     * 来自 MainVerifyProcess，若无审核记录则可为 -1。
     */
    private Integer isAudit;
    
    /**
    * 对应的审核记录主键ID（MainVerifyProcess.id），用于提交/查看进度。
    */
    private Integer verifyProcessId;
}


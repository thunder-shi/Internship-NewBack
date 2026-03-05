package newcms.entity.db;

import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import newcms.entity.base.NameRemarkInfo;

/**
 * 实习与用户关联视图
 * 包含实习项目、教师职务等信息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
public class ViewRelIntershipUser extends NameRemarkInfo {

    /**
     * 外键，关联表16（实习）
     */
    private Integer internshipId;

    /**
     * 教师职务名称
     */
    private String jobName;

    /**
     * 教师姓名
     */
    private String userName;

    /**
     * 实习项目名称
     */
    private String internshipName;
}


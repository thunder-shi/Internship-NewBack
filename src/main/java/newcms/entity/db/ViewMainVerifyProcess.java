package newcms.entity.db;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

/**
 * 审核流程视图
 */
@Getter
@Setter
@Entity
public class ViewMainVerifyProcess extends BaseInfo {
    /**
     * 外键，关联表 11、15、20、21、22、23
     */
    private Integer relationId;

    /**
     * 外键，关联表 1，当前流程创建人
     */
    private Integer createUserId;

    /**
     * 审核用户id，格式：12|14|17
     */
    private String verifyUserId;

    /**
     * 是否审核：-1-保存未提交，0-未审核，1-审核通过，2-审核退回
     */
    private Integer isAudit;

    /**
     * 审核理由
     */
    private String reason;

    /**
     * 当前审核操作表名（例如 RelProcessInternship）
     */
    private String tableName;

    /**
     * 实习项目ID（来自 view_rel_process_internship）
     */
    private Integer internshipId;

    /**
     * 流程类型ID（来自 view_rel_process_internship）
     */
    private Integer processTypeId;

    /**
     * 审核类型ID（来自 view_rel_process_internship）
     */
    private Integer verifyTypeId;
}

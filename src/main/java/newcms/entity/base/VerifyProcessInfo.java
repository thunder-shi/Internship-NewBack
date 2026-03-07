package newcms.entity.base;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * 审核流程基类
 * 在审核配置基础上增加当前审核级别（运行时状态）
 */
@MappedSuperclass
@Getter
@Setter
public class VerifyProcessInfo extends VerifyConfigInfo {
    @Column(columnDefinition = "integer unsigned default '1' comment '流程当前处在的审核级别id，外键，关联表BaseVerifyType'")
    private Integer currentVerifyTypeId;
}

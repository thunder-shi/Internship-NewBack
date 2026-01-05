package newcms.entity.db;

import newcms.entity.base.BaseInfo;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;

/**
 * 流程实习类型关联表
 */
@Getter
@Setter
@Entity
public class RelProcessInternshipType extends BaseInfo {
    @Column(columnDefinition = "integer unsigned comment '实习类型id，外键，关联表BaseInternshipType'")
    private Integer internshipTypeId;
    
    @Column(columnDefinition = "integer unsigned comment '流程类型id，外键，关联表BaseProcessType'")
    private Integer processTypeId;
    
    @Column(columnDefinition = "integer unsigned comment '验证类型id，外键，关联表BaseVerifyType'")
    private Integer verifyTypeId;
}


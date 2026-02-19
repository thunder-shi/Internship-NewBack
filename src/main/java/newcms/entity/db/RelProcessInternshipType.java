package newcms.entity.db;

import newcms.entity.base.BaseInfo;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import newcms.entity.base.NameRemarkOrderInfo;


/**
 * 流程实习类型关联表
 */
@Getter
@Setter
@Entity
public class RelProcessInternshipType extends NameRemarkOrderInfo {
    @Column(columnDefinition = "integer unsigned comment '实习类型id，外键，关联表BaseInternshipType'")
    private Integer internshipTypeId;
    
    @Column(columnDefinition = "integer unsigned comment '流程类型id，外键，关联表BaseProcessType'")
    private Integer processTypeId;
    
    @Column(columnDefinition = "integer unsigned comment '验证类型id，外键，关联表BaseVerifyType'")
    private Integer verifyTypeId;

    @Column(columnDefinition = "integer unsigned default '0' comment '第一轮审核的人物角色id，外键，关联表SysRole'")
    private Integer verifyFirstRoleId = 0;
    @Column(columnDefinition = "integer unsigned default '0' comment '第二轮审核的人物角色id，外键，关联表SysRole'")
    private Integer verifySecondRoleId = 0;
    @Column(columnDefinition = "integer unsigned default '0' comment '第三轮审核的人物角色id，外键，关联表SysRole'")
    private Integer verifyThirdRoleId = 0;
    @Column(columnDefinition = "integer unsigned default '0' comment '第四轮审核的人物角色id，外键，关联表SysRole'")
    private Integer verifyFourthRoleId = 0;
    @Column(columnDefinition = "integer unsigned default '0' comment '第五轮审核的人物角色id，外键，关联表SysRole'")
    private Integer verifyFifthRoleId = 0;
}


package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

import java.time.LocalDateTime;

/**
 * 实习项目流程关联表
 */

@Getter
@Setter
@Entity
public class RelProcessInternship extends BaseInfo {
    @Column(columnDefinition = "integer unsigned comment '实习项目id，外键，关联表BaseInternship'")
    private Integer internshipId;

    @Column(columnDefinition = "integer unsigned comment '流程类型id，外键，关联表BaseProcessType'")
    private Integer processTypeId;

    @Column(columnDefinition = "integer unsigned comment '验证类型id，外键，关联表BaseVerifyType'")
    private Integer verifyTypeId;
    @Column(columnDefinition = "integer unsigned default '0' comment '第一轮审核的人物角色id，外键，关联表SysRole'")
    private Integer verifyFirstRoleId;
    @Column(columnDefinition = "integer unsigned default '0' comment '第二轮审核的人物角色id，外键，关联表SysRole'")
    private Integer verifySecondRoleId;
    @Column(columnDefinition = "integer unsigned default '0' comment '第三轮审核的人物角色id，外键，关联表SysRole'")
    private Integer verifyThirdRoleId;
    @Column(columnDefinition = "integer unsigned default '0' comment '第四轮审核的人物角色id，外键，关联表SysRole'")
    private Integer verifyFourthRoleId;
    @Column(columnDefinition = "integer unsigned default '0' comment '第五轮审核的人物角色id，外键，关联表SysRole'")
    private Integer verifyFifthRoleId;

    @Column(columnDefinition = "datetime comment '流程开始时间'")
    private LocalDateTime startTime;
    @Column(columnDefinition = "datetime comment '流程结束时间'")
    private LocalDateTime endTime;
}

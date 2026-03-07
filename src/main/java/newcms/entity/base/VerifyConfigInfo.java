package newcms.entity.base;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * 审核配置基类
 * 包含审核级别和各级审核角色ID
 */
@MappedSuperclass
@Getter
@Setter
public class VerifyConfigInfo extends BaseInfo {
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

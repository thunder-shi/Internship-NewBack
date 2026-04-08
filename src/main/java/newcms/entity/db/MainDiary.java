package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.NameRemarkInfo;

/**
 * 实习日志表
 * 公共字段：BaseInfo(组一) + NameRemarkInfo(组二) + VerifyConfigInfo(组五) + VerifyProcessInfo(组六)
 */
@Getter
@Setter
@Entity
public class MainDiary extends NameRemarkInfo {

    // ===== 业务字段 =====

    @Column(columnDefinition = "int unsigned not null comment '关联记录ID，外键，RelStuInternshipPost(校外)或RelTitleStudent(校内)'")
    private Integer relationId;

    @Column(columnDefinition = "varchar(50) not null comment '关联表名：RelStuInternshipPost（校外）或 RelTitleStudent（校内）'")
    private String tableName;

    @Column(columnDefinition = "int unsigned not null comment '外键，关联表34（MainDiaryPeriod）'")
    private Integer periodId;

    @Column(columnDefinition = "text comment '日志文字内容'")
    private String content;

    @Column(columnDefinition = "bit(1) not null default b'0' comment '是否已提交：1=已提交，0=未提交'")
    private Boolean submit = false;

    // ===== 组五：VerifyConfigInfo =====

    @Column(columnDefinition = "integer unsigned comment '验证类型id，外键，关联表BaseVerifyType'")
    private Integer verifyTypeId;

    @Column(columnDefinition = "integer unsigned default '0' comment '第一轮审核角色id，外键，关联表SysRole'")
    private Integer verifyFirstRoleId = 0;

    @Column(columnDefinition = "integer unsigned default '0' comment '第二轮审核角色id，外键，关联表SysRole'")
    private Integer verifySecondRoleId = 0;

    @Column(columnDefinition = "integer unsigned default '0' comment '第三轮审核角色id，外键，关联表SysRole'")
    private Integer verifyThirdRoleId = 0;

    @Column(columnDefinition = "integer unsigned default '0' comment '第四轮审核角色id，外键，关联表SysRole'")
    private Integer verifyFourthRoleId = 0;

    @Column(columnDefinition = "integer unsigned default '0' comment '第五轮审核角色id，外键，关联表SysRole'")
    private Integer verifyFifthRoleId = 0;

    // ===== 组六：VerifyProcessInfo =====

    @Column(columnDefinition = "integer default '1' comment '流程当前处在的审核级别id'")
    private Integer currentVerifyTypeId = 1;
}

package newcms.entity.db;

import jakarta.persistence.Column;
import newcms.entity.base.BaseInfo;

public class RelProcessInternship extends BaseInfo {
    @Column(columnDefinition = "integer unsigned comment '实习项目id，外键，关联表BaseInternship'")
    private Integer internshipId;

    @Column(columnDefinition = "integer unsigned comment '流程类型id，外键，关联表BaseProcessType'")
    private Integer processTypeId;

    @Column(columnDefinition = "integer unsigned comment '验证类型id，外键，关联表BaseVerifyType'")
    private Integer verifyTypeId;
}

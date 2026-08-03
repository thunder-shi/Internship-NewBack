package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

import java.util.Date;

/**
 * 实习通知接收人表（学生侧已读状态）
 */
@Getter
@Setter
@Entity
public class RelNoticeReceiver extends BaseInfo {

    @Column(nullable = false, columnDefinition = "int unsigned not null comment '外键，关联 main_notice'")
    private Integer noticeId;

    @Column(nullable = false, columnDefinition = "int unsigned not null comment '外键，关联 base_user（接收学生）'")
    private Integer studentId;

    @Column(columnDefinition = "bit(1) default b'0' comment '是否已读：0未读 1已读'")
    private Boolean isRead = false;

    @Column(columnDefinition = "datetime comment '已读时间'")
    private Date readTime;
}

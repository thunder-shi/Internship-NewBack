package newcms.entity.db;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;
import org.hibernate.annotations.Immutable;

import java.util.Date;

/**
 * 学生收件箱视图（接收信息 + 通知摘要）
 */
@Getter
@Setter
@Entity
@Table(name = "view_rel_notice_receiver")
@Immutable
public class ViewRelNoticeReceiver extends BaseInfo {

    private Integer noticeId;

    private Integer studentId;

    private Boolean isRead;

    private Date readTime;

    private String title;

    private String content;

    private Integer internshipId;

    private Integer publisherId;

    private Date publishTime;

    private String publisherName;

    private String internshipName;
}
package newcms.entity.db;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.NameRemarkInfo;
import org.hibernate.annotations.Immutable;

import java.util.Date;

/**
 * 老师已发通知列表视图（含接收数、已读数）
 */
@Getter
@Setter
@Entity
@Table(name = "view_main_notice")
@Immutable
public class ViewMainNotice extends NameRemarkInfo {

    private String title;

    private String content;

    private Integer internshipId;

    private Integer publisherId;

    private Date publishTime;

    private String publisherName;

    private String internshipName;

    private Long receiverCount;

    private Long readCount;
}
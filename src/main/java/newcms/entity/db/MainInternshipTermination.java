package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.VerifyConfigInfo;

import java.util.Date;

@Getter
@Setter
@Entity
public class MainInternshipTermination extends VerifyConfigInfo {

    @Column(nullable = false, columnDefinition = "int unsigned comment 'internship id'")
    private Integer internshipId;

    @Column(nullable = false, columnDefinition = "int unsigned comment 'student user id'")
    private Integer studentId;

    @Column(nullable = false, columnDefinition = "varchar(50) comment 'RelStuInternshipPost or RelTitleStudent'")
    private String relationTable;

    @Column(nullable = false, columnDefinition = "int unsigned comment 'student internship relation id'")
    private Integer relationId;

    @Column(columnDefinition = "datetime comment 'effective termination date'")
    private Date terminateDate;

    @Column(columnDefinition = "varchar(50) comment 'termination reason type'")
    private String reasonType;

    @Column(columnDefinition = "varchar(1000) comment 'termination reason'")
    private String reason;

    @Column(columnDefinition = "varchar(255) comment 'optional attachment ids, comma separated'")
    private String attachmentIds;

    @Column(nullable = false, columnDefinition = "int unsigned comment 'applicant user id'")
    private Integer applyUserId;

    @Column(columnDefinition = "tinyint not null default 0 comment '0 pending, 1 approved, 2 rejected, 3 returned, 4 cancelled'")
    private Integer status = 0;

    @Column(columnDefinition = "integer default '1' comment 'current verify level'")
    private Integer currentVerifyTypeId = 1;

    @Column(columnDefinition = "datetime comment 'approved time'")
    private Date approvedTime;

    @Column(columnDefinition = "int unsigned comment 'final approver user id'")
    private Integer approvedBy;

    @Version
    @Column(columnDefinition = "int default 0 comment 'optimistic lock version'")
    private Integer version;
}

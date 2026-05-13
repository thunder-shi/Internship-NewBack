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
public class MainEnterpriseInfo extends VerifyConfigInfo {

    @Column(nullable = false, columnDefinition = "int unsigned comment 'company department id'")
    private Integer companyId;

    @Column(nullable = false, columnDefinition = "int unsigned comment 'school root department id'")
    private Integer schoolId;

    @Column(nullable = false, columnDefinition = "int unsigned comment 'company admin user id'")
    private Integer adminUserId;

    @Column(columnDefinition = "varchar(50) comment 'unified social credit code, synced to base_department.code when approved'")
    private String code;

    @Column(columnDefinition = "varchar(200) comment 'company name snapshot'")
    private String name;

    @Column(columnDefinition = "varchar(100) comment 'contact person'")
    private String contactName;

    @Column(columnDefinition = "varchar(50) comment 'contact phone'")
    private String contactPhone;

    @Column(columnDefinition = "varchar(100) comment 'contact email'")
    private String contactEmail;

    @Column(columnDefinition = "varchar(255) comment 'company address'")
    private String address;

    @Column(columnDefinition = "varchar(100) comment 'legal person'")
    private String legalPerson;

    @Column(columnDefinition = "varchar(100) comment 'industry'")
    private String industry;

    @Column(columnDefinition = "varchar(100) comment 'company scale'")
    private String companyScale;

    @Column(columnDefinition = "varchar(1000) comment 'business scope'")
    private String businessScope;

    @Column(columnDefinition = "varchar(2000) comment 'company introduction'")
    private String introduction;

    @Column(columnDefinition = "varchar(1000) comment 'remarks or audit note'")
    private String remarks;

    @Column(columnDefinition = "tinyint not null default -1 comment '-1 draft, 0 pending, 1 approved, 2 rejected, 3 returned'")
    private Integer auditStatus = -1;

    @Column(columnDefinition = "integer default '1' comment 'current verify level'")
    private Integer currentVerifyTypeId = 1;

    @Column(columnDefinition = "bit(1) not null default b'0' comment 'whether this version is current approved version'")
    private Boolean isCurrent = false;

    @Column(columnDefinition = "int unsigned not null default 1 comment 'version number inside same company'")
    private Integer versionNo = 1;

    @Column(columnDefinition = "datetime comment 'approved time'")
    private Date approvedTime;

    @Column(columnDefinition = "int unsigned comment 'final approver user id'")
    private Integer approvedBy;

    @Version
    @Column(columnDefinition = "int default 0 comment 'optimistic lock version'")
    private Integer version;
}

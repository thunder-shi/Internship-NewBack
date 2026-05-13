package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.VerifyConfigInfo;

@Getter
@Setter
@Entity
public class BaseEnterpriseVerifyConfig extends VerifyConfigInfo {

    @Column(nullable = false, columnDefinition = "int unsigned comment 'school root department id'")
    private Integer schoolId;

    @Column(columnDefinition = "varchar(1000) comment 'config remarks'")
    private String remarks;

    @Version
    @Column(columnDefinition = "int default 0 comment 'optimistic lock version'")
    private Integer version;
}

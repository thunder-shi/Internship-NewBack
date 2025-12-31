package newcms.entity.db;

import newcms.entity.base.NameRemarkInfo;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;

/**
 * 角色信息表
 */
@Getter
@Setter
@Entity
public class BaseInternshipType extends NameRemarkInfo {
    @Column(columnDefinition = "integer unsigned not null")
    private Integer universityId;
    @Column(columnDefinition = "integer unsigned not null")
    private Integer intTypeId;
}
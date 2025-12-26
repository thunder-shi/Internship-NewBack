package newcms.entity.base;

import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
@Getter
@Setter
public class NameRemarkInfo extends BaseInfo {
    @Column(columnDefinition = "varchar(50) comment '编码'")
    private String code;
    @Column(columnDefinition = "varchar(50) comment '名称'")
    private String name;
    @Column(columnDefinition = "varchar(1000) comment '备注'")
    private String remarks;
}

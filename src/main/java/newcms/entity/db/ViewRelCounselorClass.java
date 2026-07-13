package newcms.entity.db;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import newcms.entity.base.BaseInfo;

@Getter
@Setter
@Entity
public class ViewRelCounselorClass extends BaseInfo {
    private Integer counselorId;
    private String counselorName;
    private Integer classId;
    private String className;
}

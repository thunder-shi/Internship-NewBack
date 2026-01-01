package newcms.entity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import newcms.entity.base.AuditInfo;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
public class ViewMainInternship extends AuditInfo {
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer internshipTypeId;
    private Integer creatorId;
    private String cron;
    private LocalDate reportStartDate;
    private LocalDate reportEndDate;
}

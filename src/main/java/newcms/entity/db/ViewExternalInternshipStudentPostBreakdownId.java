package newcms.entity.db;

import java.io.Serializable;
import java.util.Objects;

/**
 * {@link ViewExternalInternshipStudentPostBreakdown} 复合主键：internshipId + userId。
 */
public class ViewExternalInternshipStudentPostBreakdownId implements Serializable {

    private Integer internshipId;
    private Integer userId;

    public ViewExternalInternshipStudentPostBreakdownId() {
    }

    public ViewExternalInternshipStudentPostBreakdownId(Integer internshipId, Integer userId) {
        this.internshipId = internshipId;
        this.userId = userId;
    }

    public Integer getInternshipId() {
        return internshipId;
    }

    public void setInternshipId(Integer internshipId) {
        this.internshipId = internshipId;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ViewExternalInternshipStudentPostBreakdownId that)) {
            return false;
        }
        return Objects.equals(internshipId, that.internshipId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(internshipId, userId);
    }
}

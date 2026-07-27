package newcms.service.support;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Excel 导入手动师生分配的内存任务进度。
 */
public class ImportManualAssignTask {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    public static class TeacherGroup {
        public final Integer teacherId;
        public final String teacherWorkId;
        public final List<Integer> studentIds;

        public TeacherGroup(Integer teacherId, String teacherWorkId, List<Integer> studentIds) {
            this.teacherId = teacherId;
            this.teacherWorkId = teacherWorkId;
            this.studentIds = studentIds == null ? List.of() : new ArrayList<>(studentIds);
        }
    }

    private final String taskId;
    private final Integer internshipId;
    private final Integer processId;
    private final Integer createUserId;
    private final String verifyUserId;
    private final int currentVerifyTypeId;
    private final int totalExcelRowCount;
    private final int resolvedPairCount;
    private final List<TeacherGroup> teacherGroups;
    private final JSONArray failures = new JSONArray();

    private volatile String status = STATUS_PENDING;
    private volatile String message = "";
    private volatile int total;
    private final AtomicInteger processed = new AtomicInteger(0);
    private final AtomicInteger assignedTeacherGroupCount = new AtomicInteger(0);
    private final AtomicInteger createdRelTeacherStudentCount = new AtomicInteger(0);
    private final AtomicInteger createdVerifyProcessCount = new AtomicInteger(0);
    private final AtomicInteger updatedRelTeacherStudentCount = new AtomicInteger(0);
    private final AtomicInteger skippedSubmittedCount = new AtomicInteger(0);
    private final long createdAt = System.currentTimeMillis();
    private volatile long finishedAt;

    public ImportManualAssignTask(String taskId, Integer internshipId, Integer processId, Integer createUserId,
                                  String verifyUserId, int currentVerifyTypeId, int totalExcelRowCount,
                                  int resolvedPairCount, List<TeacherGroup> teacherGroups, JSONArray validationFailures) {
        this.taskId = taskId;
        this.internshipId = internshipId;
        this.processId = processId;
        this.createUserId = createUserId;
        this.verifyUserId = verifyUserId == null ? "" : verifyUserId;
        this.currentVerifyTypeId = currentVerifyTypeId;
        this.totalExcelRowCount = totalExcelRowCount;
        this.resolvedPairCount = resolvedPairCount;
        this.teacherGroups = teacherGroups == null ? List.of() : new ArrayList<>(teacherGroups);
        this.total = this.teacherGroups.size();
        if (validationFailures != null) {
            synchronized (failures) {
                failures.addAll(validationFailures);
            }
        }
    }

    public String getTaskId() {
        return taskId;
    }

    public Integer getInternshipId() {
        return internshipId;
    }

    public Integer getProcessId() {
        return processId;
    }

    public Integer getCreateUserId() {
        return createUserId;
    }

    public String getVerifyUserId() {
        return verifyUserId;
    }

    public int getCurrentVerifyTypeId() {
        return currentVerifyTypeId;
    }

    public List<TeacherGroup> getTeacherGroups() {
        return teacherGroups;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message == null ? "" : message;
    }

    public void setTotal(int total) {
        this.total = Math.max(total, 0);
    }

    public int getTotal() {
        return total;
    }

    public void incrementProcessed() {
        processed.incrementAndGet();
    }

    public void incrementAssignedGroup() {
        assignedTeacherGroupCount.incrementAndGet();
    }

    public void addCreatedRel(int n) {
        if (n > 0) {
            createdRelTeacherStudentCount.addAndGet(n);
        }
    }

    public void addCreatedVerify(int n) {
        if (n > 0) {
            createdVerifyProcessCount.addAndGet(n);
        }
    }

    public void addUpdatedRel(int n) {
        if (n > 0) {
            updatedRelTeacherStudentCount.addAndGet(n);
        }
    }

    public void addSkippedSubmitted(int n) {
        if (n > 0) {
            skippedSubmittedCount.addAndGet(n);
        }
    }

    public void addFailure(JSONObject failure) {
        if (failure != null) {
            synchronized (failures) {
                failures.add(failure);
            }
        }
    }

    public void markFinished() {
        this.finishedAt = System.currentTimeMillis();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getFinishedAt() {
        return finishedAt;
    }

    public int getPercent() {
        if (total <= 0) {
            return STATUS_SUCCESS.equals(status) || STATUS_FAILED.equals(status) ? 100 : 0;
        }
        int p = (int) Math.min(100, Math.round(processed.get() * 100.0 / total));
        if ((STATUS_SUCCESS.equals(status) || STATUS_FAILED.equals(status)) && p < 100) {
            return 100;
        }
        return p;
    }

    public boolean isFinished() {
        return STATUS_SUCCESS.equals(status) || STATUS_FAILED.equals(status);
    }

    public JSONObject toJson(boolean includeFailures) {
        JSONObject out = new JSONObject();
        out.put("taskId", taskId);
        out.put("internshipId", internshipId);
        out.put("processId", processId);
        out.put("status", status);
        out.put("message", message);
        out.put("verifyUserId", verifyUserId);
        out.put("total", total);
        out.put("processed", processed.get());
        out.put("percent", getPercent());
        out.put("totalExcelRowCount", totalExcelRowCount);
        out.put("resolvedPairCount", resolvedPairCount);
        out.put("assignedTeacherGroupCount", assignedTeacherGroupCount.get());
        out.put("createdRelTeacherStudentCount", createdRelTeacherStudentCount.get());
        out.put("createdVerifyProcessCount", createdVerifyProcessCount.get());
        out.put("updatedRelTeacherStudentCount", updatedRelTeacherStudentCount.get());
        out.put("skippedSubmittedCount", skippedSubmittedCount.get());
        out.put("createdAt", createdAt);
        if (finishedAt > 0) {
            out.put("finishedAt", finishedAt);
        }
        synchronized (failures) {
            out.put("failedCount", failures.size());
            if (includeFailures) {
                out.put("failures", failures.clone());
            }
        }
        return out;
    }
}

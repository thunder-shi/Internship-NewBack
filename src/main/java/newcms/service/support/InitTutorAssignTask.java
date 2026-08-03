package newcms.service.support;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 系统分配校内导师的内存任务进度。
 */
public class InitTutorAssignTask {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    private final String taskId;
    private final Integer internshipId;
    private final Integer processId;
    private final Integer createUserId;
    private final String verifyUserId;
    private final int currentVerifyTypeId;

    private volatile String status = STATUS_PENDING;
    private volatile String message = "";
    private volatile int total;
    private final AtomicInteger processed = new AtomicInteger(0);
    private final AtomicInteger assignedCount = new AtomicInteger(0);
    private final AtomicInteger verifyUpdatedCount = new AtomicInteger(0);
    private final AtomicInteger skippedCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);
    private final JSONArray details = new JSONArray();
    private final long createdAt = System.currentTimeMillis();
    private volatile long finishedAt;

    public InitTutorAssignTask(String taskId, Integer internshipId, Integer processId,
                               Integer createUserId, String verifyUserId, int currentVerifyTypeId) {
        this.taskId = taskId;
        this.internshipId = internshipId;
        this.processId = processId;
        this.createUserId = createUserId;
        this.verifyUserId = verifyUserId;
        this.currentVerifyTypeId = currentVerifyTypeId;
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

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = Math.max(total, 0);
    }

    public int getProcessed() {
        return processed.get();
    }

    public void incrementProcessed() {
        processed.incrementAndGet();
    }

    public int getAssignedCount() {
        return assignedCount.get();
    }

    public void incrementAssigned() {
        assignedCount.incrementAndGet();
    }

    public int getVerifyUpdatedCount() {
        return verifyUpdatedCount.get();
    }

    public void addVerifyUpdated(int n) {
        if (n > 0) {
            verifyUpdatedCount.addAndGet(n);
        }
    }

    public int getSkippedCount() {
        return skippedCount.get();
    }

    public void incrementSkipped() {
        skippedCount.incrementAndGet();
    }

    public int getFailedCount() {
        return failedCount.get();
    }

    public void incrementFailed() {
        failedCount.incrementAndGet();
    }

    public void addDetail(JSONObject item) {
        if (item != null) {
            synchronized (details) {
                details.add(item);
            }
        }
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getFinishedAt() {
        return finishedAt;
    }

    public void markFinished() {
        this.finishedAt = System.currentTimeMillis();
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

    public JSONObject toJson(boolean includeDetails) {
        JSONObject out = new JSONObject();
        out.put("taskId", taskId);
        out.put("internshipId", internshipId);
        out.put("processId", processId);
        out.put("status", status);
        out.put("message", message);
        out.put("total", total);
        out.put("processed", processed.get());
        out.put("percent", getPercent());
        out.put("assignedCount", assignedCount.get());
        out.put("createdRelTeacherStudentCount", assignedCount.get());
        out.put("verifyUpdatedCount", verifyUpdatedCount.get());
        out.put("createdVerifyProcessCount", verifyUpdatedCount.get());
        out.put("skippedCount", skippedCount.get());
        out.put("failedCount", failedCount.get());
        out.put("createdAt", createdAt);
        if (finishedAt > 0) {
            out.put("finishedAt", finishedAt);
        }
        if (includeDetails) {
            synchronized (details) {
                out.put("details", details.clone());
            }
        }
        return out;
    }
}

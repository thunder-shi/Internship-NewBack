package newcms.service.support;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 未选岗学生随机分配岗位的内存任务进度。
 */
public class RandomAssignPostTask {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    private final String taskId;
    private final Integer internshipId;
    private volatile String status = STATUS_PENDING;
    private volatile String message = "";
    private volatile int total;
    private volatile int candidatePostCount;
    private final AtomicInteger processed = new AtomicInteger(0);
    private final AtomicInteger assignedCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);
    private final AtomicInteger unassignedCount = new AtomicInteger(0);
    private final JSONArray details = new JSONArray();
    private final long createdAt = System.currentTimeMillis();
    private volatile long finishedAt;

    public RandomAssignPostTask(String taskId, Integer internshipId) {
        this.taskId = taskId;
        this.internshipId = internshipId;
    }

    public String getTaskId() {
        return taskId;
    }

    public Integer getInternshipId() {
        return internshipId;
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

    public int getCandidatePostCount() {
        return candidatePostCount;
    }

    public void setCandidatePostCount(int candidatePostCount) {
        this.candidatePostCount = Math.max(candidatePostCount, 0);
    }

    public int getProcessed() {
        return processed.get();
    }

    public int incrementProcessed() {
        return processed.incrementAndGet();
    }

    public int getAssignedCount() {
        return assignedCount.get();
    }

    public void incrementAssigned() {
        assignedCount.incrementAndGet();
    }

    public int getFailedCount() {
        return failedCount.get();
    }

    public void incrementFailed() {
        failedCount.incrementAndGet();
    }

    public int getUnassignedCount() {
        return unassignedCount.get();
    }

    public void incrementUnassigned() {
        unassignedCount.incrementAndGet();
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
        out.put("status", status);
        out.put("message", message);
        out.put("total", total);
        out.put("candidateStudentCount", total);
        out.put("candidatePostCount", candidatePostCount);
        out.put("processed", processed.get());
        out.put("percent", getPercent());
        out.put("assignedCount", assignedCount.get());
        out.put("failedCount", failedCount.get());
        out.put("unassignedCount", unassignedCount.get());
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

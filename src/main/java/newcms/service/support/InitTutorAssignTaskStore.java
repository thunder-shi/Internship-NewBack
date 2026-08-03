package newcms.service.support;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统分配校内导师任务的进程内存储。
 */
@Component
public class InitTutorAssignTaskStore {

    private static final long TTL_MS = 2L * 60 * 60 * 1000;

    private final ConcurrentHashMap<String, InitTutorAssignTask> tasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> runningByInternship = new ConcurrentHashMap<>();

    public InitTutorAssignTask create(Integer internshipId, Integer processId, Integer createUserId,
                                      String verifyUserId, int currentVerifyTypeId) {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        InitTutorAssignTask task = new InitTutorAssignTask(
                taskId, internshipId, processId, createUserId, verifyUserId, currentVerifyTypeId);
        tasks.put(taskId, task);
        return task;
    }

    public InitTutorAssignTask get(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        return tasks.get(taskId);
    }

    public String tryMarkRunning(Integer internshipId, String taskId) {
        if (internshipId == null || taskId == null) {
            return "invalid";
        }
        return runningByInternship.putIfAbsent(internshipId, taskId);
    }

    public void clearRunning(Integer internshipId, String taskId) {
        if (internshipId == null || taskId == null) {
            return;
        }
        runningByInternship.computeIfPresent(internshipId, (k, v) -> taskId.equals(v) ? null : v);
    }

    @Scheduled(fixedDelay = 30 * 60 * 1000L)
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, InitTutorAssignTask>> it = tasks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, InitTutorAssignTask> e = it.next();
            InitTutorAssignTask t = e.getValue();
            long anchor = t.getFinishedAt() > 0 ? t.getFinishedAt() : t.getCreatedAt();
            if (now - anchor > TTL_MS) {
                it.remove();
                clearRunning(t.getInternshipId(), t.getTaskId());
            }
        }
    }
}

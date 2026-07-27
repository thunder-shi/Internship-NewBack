package newcms.service.support;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 随机分配岗位任务的进程内存储（Redis 当前未启用）。
 */
@Component
public class RandomAssignPostTaskStore {

    private static final long TTL_MS = 2L * 60 * 60 * 1000;

    private final ConcurrentHashMap<String, RandomAssignPostTask> tasks = new ConcurrentHashMap<>();
    /** internshipId -> 进行中的 taskId，避免同项目重复开跑 */
    private final ConcurrentHashMap<Integer, String> runningByInternship = new ConcurrentHashMap<>();

    public RandomAssignPostTask create(Integer internshipId) {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        RandomAssignPostTask task = new RandomAssignPostTask(taskId, internshipId);
        tasks.put(taskId, task);
        return task;
    }

    public RandomAssignPostTask get(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        return tasks.get(taskId);
    }

    /**
     * @return 已占用则返回已有 taskId；成功占用返回 null
     */
    public String tryMarkRunning(Integer internshipId, String taskId) {
        if (internshipId == null || taskId == null) {
            return "invalid";
        }
        String existing = runningByInternship.putIfAbsent(internshipId, taskId);
        return existing;
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
        Iterator<Map.Entry<String, RandomAssignPostTask>> it = tasks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, RandomAssignPostTask> e = it.next();
            RandomAssignPostTask t = e.getValue();
            long anchor = t.getFinishedAt() > 0 ? t.getFinishedAt() : t.getCreatedAt();
            if (now - anchor > TTL_MS) {
                it.remove();
                clearRunning(t.getInternshipId(), t.getTaskId());
            }
        }
    }
}

package newcms.service.support;

import com.alibaba.fastjson.JSONArray;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ImportManualAssignTaskStore {

    private static final long TTL_MS = 2L * 60 * 60 * 1000;

    private final ConcurrentHashMap<String, ImportManualAssignTask> tasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> runningByInternship = new ConcurrentHashMap<>();

    public ImportManualAssignTask create(Integer internshipId, Integer processId, Integer createUserId,
                                         String verifyUserId, int currentVerifyTypeId, int totalExcelRowCount,
                                         int resolvedPairCount, List<ImportManualAssignTask.TeacherGroup> groups,
                                         JSONArray validationFailures) {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        ImportManualAssignTask task = new ImportManualAssignTask(
                taskId, internshipId, processId, createUserId, verifyUserId, currentVerifyTypeId,
                totalExcelRowCount, resolvedPairCount, groups, validationFailures);
        tasks.put(taskId, task);
        return task;
    }

    public ImportManualAssignTask get(String taskId) {
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
        Iterator<Map.Entry<String, ImportManualAssignTask>> it = tasks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ImportManualAssignTask> e = it.next();
            ImportManualAssignTask t = e.getValue();
            long anchor = t.getFinishedAt() > 0 ? t.getFinishedAt() : t.getCreatedAt();
            if (now - anchor > TTL_MS) {
                it.remove();
                clearRunning(t.getInternshipId(), t.getTaskId());
            }
        }
    }
}

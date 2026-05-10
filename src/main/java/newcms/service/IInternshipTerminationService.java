package newcms.service;

import com.alibaba.fastjson.JSONObject;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public interface IInternshipTerminationService {
    Object listCandidates(JSONObject searchKeys, Map<String, String> regMap, Sort sort, Integer page, Integer size);

    Object listAudits(JSONObject searchKeys, Map<String, String> regMap, Sort sort, Integer page, Integer size);

    Object create(JSONObject node, Integer currentUserId);

    Object detail(Integer terminationId);

    Object cancel(Integer terminationId, Integer currentUserId);

    Object resubmit(JSONObject node, Integer currentUserId);

    void afterAuditPassed(Integer terminationId, Integer auditUserId);

    void afterAuditRejectedOrReturned(Integer terminationId, Integer isAudit);

    void assertNotTerminated(String relationTable, Integer relationId);
}

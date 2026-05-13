package newcms.service;

import com.alibaba.fastjson.JSONObject;
import org.springframework.data.domain.Sort;

public interface IEnterpriseInfoService {
    Object getMine(Integer currentUserId);

    Object listMyHistory(JSONObject searchKeys, Integer currentUserId, Sort sort, Integer page, Integer size);

    Object detail(Integer enterpriseInfoId, Integer currentUserId);

    Object saveDraft(JSONObject node, Integer currentUserId);

    Object submit(JSONObject node, Integer currentUserId);

    Object resubmit(JSONObject node, Integer currentUserId);

    Object listAudits(JSONObject searchKeys, Integer currentUserId, Sort sort, Integer page, Integer size);

    Object auditDetail(Integer enterpriseInfoId, Integer currentUserId);

    Object audit(JSONObject node, Integer currentUserId);

    Object getVerifyConfig(Integer schoolId, Integer currentUserId);

    Object saveVerifyConfig(JSONObject node, Integer currentUserId);

    boolean canAccessAttachment(Integer currentUserId, Integer enterpriseInfoId);

    void assertCurrentUserCanDeclareExternal();
}

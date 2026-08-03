package newcms.controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import newcms.annotation.PathRestController;
import newcms.base.Base;
import newcms.base.BaseResponse;
import newcms.service.INoticeService;
import newcms.utils.LogUtil;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Tag(name = "实习通知")
@PathRestController("notice")
public class NoticeController {

    @Resource
    private INoticeService iNoticeService;

    @Operation(summary = "当前老师可发通知的实习项目列表")
    @PostMapping(value = "/my-internships", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object myInternships(@RequestBody(required = false) JSONObject requestJson) {
        LogUtil.loggerRecord("notice.myInternships", requestJson);
        return BaseResponse.ok(iNoticeService.listMyInternships(Base.getLoginUserId()));
    }

    @Operation(summary = "当前老师在指定项目下绑定的学生（勾选弹窗）")
    @PostMapping(value = "/bound-students", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object boundStudents(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("notice.boundStudents", requestJson);
        JSONObject node = requireNode(requestJson);
        Integer internshipId = requirePositiveInteger(node, "internshipId");
        return BaseResponse.ok(iNoticeService.listBoundStudents(internshipId, Base.getLoginUserId()));
    }

    @Operation(summary = "发布通知",
            description = "title/content 必填；studentIds 为空则发给当前老师在该项目下全部绑定学生。"
                    + "返回 noticeId，可用于 /common/minio/upload（tableName=MainNotice）。")
    @PostMapping(value = "/publish", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object publish(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("notice.publish", requestJson);
        JSONObject node = requireNode(requestJson);
        Integer internshipId = requirePositiveInteger(node, "internshipId");
        String title = node.getString("title");
        String content = node.getString("content");
        List<Integer> studentIds = parseIntegerList(node.get("studentIds"));
        return BaseResponse.ok(
                iNoticeService.publish(internshipId, title, content, studentIds, Base.getLoginUserId()));
    }

    @Operation(summary = "老师已发通知列表（分页）")
    @PostMapping(value = "/my-published", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object myPublished(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("notice.myPublished", requestJson);
        JSONObject node = requestJson == null ? new JSONObject() : requestJson.getJSONObject("node");
        if (node == null) {
            node = requestJson != null ? requestJson : new JSONObject();
        }
        Integer internshipId = getOptionalPositiveInteger(node, "internshipId");
        Integer page = getOptionalPositiveInteger(node, "page");
        Integer size = getOptionalPositiveInteger(node, "size");
        if (page == null) {
            page = getOptionalPositiveInteger(requestJson, "page");
        }
        if (size == null) {
            size = getOptionalPositiveInteger(requestJson, "size");
        }
        return BaseResponse.ok(
                iNoticeService.myPublished(Base.getLoginUserId(), internshipId, page, size));
    }

    @Operation(summary = "学生收件箱（分页）")
    @PostMapping(value = "/my-inbox", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object myInbox(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("notice.myInbox", requestJson);
        JSONObject node = requestJson == null ? new JSONObject() : requestJson.getJSONObject("node");
        if (node == null) {
            node = requestJson != null ? requestJson : new JSONObject();
        }
        Integer internshipId = getOptionalPositiveInteger(node, "internshipId");
        Boolean isRead = node.getBoolean("isRead");
        Integer page = getOptionalPositiveInteger(node, "page");
        Integer size = getOptionalPositiveInteger(node, "size");
        if (page == null) {
            page = getOptionalPositiveInteger(requestJson, "page");
        }
        if (size == null) {
            size = getOptionalPositiveInteger(requestJson, "size");
        }
        return BaseResponse.ok(
                iNoticeService.myInbox(Base.getLoginUserId(), internshipId, isRead, page, size));
    }

    @Operation(summary = "标记已读")
    @PostMapping(value = "/mark-read", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object markRead(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("notice.markRead", requestJson);
        JSONObject node = requireNode(requestJson);
        Integer noticeId = getOptionalPositiveInteger(node, "noticeId");
        List<Integer> noticeIds = parseIntegerList(node.get("noticeIds"));
        iNoticeService.markRead(Base.getLoginUserId(), noticeId, noticeIds);
        return BaseResponse.ok(null);
    }

    @Operation(summary = "未读消息数")
    @PostMapping(value = "/unread-count", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object unreadCount(@RequestBody(required = false) JSONObject requestJson) {
        LogUtil.loggerRecord("notice.unreadCount", requestJson);
        JSONObject node = requestJson == null ? new JSONObject() : requestJson.getJSONObject("node");
        if (node == null) {
            node = requestJson != null ? requestJson : new JSONObject();
        }
        Integer internshipId = getOptionalPositiveInteger(node, "internshipId");
        return BaseResponse.ok(iNoticeService.unreadCount(Base.getLoginUserId(), internshipId));
    }

    @Operation(summary = "通知详情")
    @PostMapping(value = "/detail", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object detail(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("notice.detail", requestJson);
        JSONObject node = requireNode(requestJson);
        Integer noticeId = requirePositiveInteger(node, "noticeId");
        return BaseResponse.ok(iNoticeService.detail(noticeId, Base.getLoginUserId()));
    }

    @Operation(summary = "软删通知（仅发布人）")
    @PostMapping(value = "/delete", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object delete(@RequestBody JSONObject requestJson) {
        LogUtil.loggerRecord("notice.delete", requestJson);
        JSONObject node = requireNode(requestJson);
        Integer noticeId = requirePositiveInteger(node, "noticeId");
        iNoticeService.delete(noticeId, Base.getLoginUserId());
        return BaseResponse.ok(null);
    }

    private JSONObject requireNode(JSONObject requestJson) {
        if (requestJson == null) {
            throw BaseResponse.parameterInvalid.error("请求体不能为空");
        }
        JSONObject node = requestJson.getJSONObject("node");
        if (node == null) {
            throw BaseResponse.parameterInvalid.error("node 不能为空");
        }
        return node;
    }

    private Integer requirePositiveInteger(JSONObject node, String key) {
        Integer value = getOptionalPositiveInteger(node, key);
        if (value == null) {
            throw BaseResponse.parameterInvalid.error(key + " 不能为空");
        }
        return value;
    }

    private Integer getOptionalPositiveInteger(JSONObject json, String key) {
        if (json == null || !json.containsKey(key)) {
            return null;
        }
        Object raw = json.get(key);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number) {
            int value = ((Number) raw).intValue();
            return value > 0 ? value : null;
        }
        String value = String.valueOf(raw).trim();
        if (value.isEmpty() || "-".equals(value)) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            throw BaseResponse.parameterInvalid.error(key + " must be a positive integer");
        }
    }

    private List<Integer> parseIntegerList(Object raw) {
        if (raw == null) {
            return Collections.emptyList();
        }
        if (raw instanceof JSONArray array) {
            return array.toJavaList(Integer.class);
        }
        if (raw instanceof List<?> list) {
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(item -> {
                        if (item instanceof Number number) {
                            return number.intValue();
                        }
                        return Integer.valueOf(String.valueOf(item).trim());
                    })
                    .toList();
        }
        return Collections.emptyList();
    }
}

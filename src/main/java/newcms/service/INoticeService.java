package newcms.service;

import com.alibaba.fastjson.JSONObject;

import java.util.List;

/**
 * 实习通知（老师发布 / 学生查收）
 */
public interface INoticeService {

    /**
     * 当前老师在指定项目下绑定的学生列表（勾选弹窗数据源）。
     */
    List<JSONObject> listBoundStudents(Integer internshipId, Integer teacherId);

    /**
     * 当前老师可发通知的实习项目列表。
     */
    List<JSONObject> listMyInternships(Integer teacherId);

    /**
     * 发布通知。studentIds 为空则发给全部绑定学生。
     *
     * @return 含 noticeId 的结果
     */
    JSONObject publish(Integer internshipId, String title, String content,
                       List<Integer> studentIds, Integer publisherId);

    /**
     * 老师已发列表（分页）。
     */
    JSONObject myPublished(Integer publisherId, Integer internshipId, Integer page, Integer size);

    /**
     * 学生收件箱（分页）。
     */
    JSONObject myInbox(Integer studentId, Integer internshipId, Boolean isRead, Integer page, Integer size);

    /**
     * 标记已读（仅本人接收行）。
     */
    void markRead(Integer studentId, Integer noticeId, List<Integer> noticeIds);

    /**
     * 未读数。
     */
    JSONObject unreadCount(Integer studentId, Integer internshipId);

    /**
     * 详情：老师看本人发布；学生看本人接收。
     */
    JSONObject detail(Integer noticeId, Integer currentUserId);

    /**
     * 软删：仅发布人；主表 + 接收人一并软删。
     */
    void delete(Integer noticeId, Integer publisherId);
}

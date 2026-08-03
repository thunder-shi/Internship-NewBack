package newcms.service.impl;

import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import newcms.base.BaseResponse;
import newcms.entity.db.MainInternship;
import newcms.entity.db.MainInternshipPost;
import newcms.entity.db.MainNotice;
import newcms.entity.db.RelNoticeReceiver;
import newcms.entity.db.RelTeacherStudent;
import newcms.entity.db.ViewBaseUser;
import newcms.entity.db.ViewMainNotice;
import newcms.entity.db.ViewRelNoticeReceiver;
import newcms.entity.db.ViewRelTitleTeacherStudent;
import newcms.repository.db.MainInternshipDao;
import newcms.repository.db.MainInternshipPostDao;
import newcms.repository.db.MainNoticeDao;
import newcms.repository.db.RelNoticeReceiverDao;
import newcms.repository.db.RelTeacherStudentDao;
import newcms.repository.db.ViewBaseUserDao;
import newcms.repository.db.ViewMainNoticeDao;
import newcms.repository.db.ViewRelNoticeReceiverDao;
import newcms.repository.db.ViewRelTitleTeacherStudentDao;
import newcms.service.INoticeService;
import newcms.utils.FastJsonUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class NoticeServiceImpl implements INoticeService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 200;

    @Resource
    private MainNoticeDao mainNoticeDao;
    @Resource
    private RelNoticeReceiverDao relNoticeReceiverDao;
    @Resource
    private ViewMainNoticeDao viewMainNoticeDao;
    @Resource
    private ViewRelNoticeReceiverDao viewRelNoticeReceiverDao;
    @Resource
    private MainInternshipPostDao mainInternshipPostDao;
    @Resource
    private RelTeacherStudentDao relTeacherStudentDao;
    @Resource
    private ViewRelTitleTeacherStudentDao viewRelTitleTeacherStudentDao;
    @Resource
    private ViewBaseUserDao viewBaseUserDao;
    @Resource
    private MainInternshipDao mainInternshipDao;

    @Override
    public List<JSONObject> listBoundStudents(Integer internshipId, Integer teacherId) {
        requirePositive(internshipId, "internshipId");
        requirePositive(teacherId, "teacherId");
        BoundAudience audience = resolveBoundAudience(teacherId, internshipId);
        if (audience.studentIds.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Integer, ViewBaseUser> userMap = loadUsers(audience.studentIds);
        List<JSONObject> result = new ArrayList<>();
        for (Integer studentId : audience.studentIds) {
            JSONObject item = new JSONObject();
            item.put("studentId", studentId);
            ViewBaseUser user = userMap.get(studentId);
            if (user != null) {
                item.put("studentName", user.getName());
                item.put("studentAccount", user.getAccount());
                item.put("studentDepartmentName", user.getDepartmentName());
            } else {
                BoundStudentInfo info = audience.infoByStudentId.get(studentId);
                if (info != null) {
                    item.put("studentName", info.studentName);
                    item.put("studentAccount", info.studentAccount);
                    item.put("studentDepartmentName", info.studentDepartmentName);
                }
            }
            result.add(item);
        }
        return result;
    }

    @Override
    public List<JSONObject> listMyInternships(Integer teacherId) {
        requirePositive(teacherId, "teacherId");
        Map<Integer, JSONObject> map = new LinkedHashMap<>();
        collectExternalInternships(teacherId, map);
        collectInternalInternships(teacherId, map);
        return new ArrayList<>(map.values());
    }

    private void collectExternalInternships(Integer teacherId, Map<Integer, JSONObject> map) {
        // Scan via all RTS for this teacher — RelTeacherStudentDao has no findByTeacherId,
        // use internship posts presence: query RTS through common pattern
        List<RelTeacherStudent> all = findRelTeacherStudentByTeacherId(teacherId);
        Set<Integer> internshipIds = all.stream()
                .map(RelTeacherStudent::getInternshipId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (Integer internshipId : internshipIds) {
            MainInternship internship = mainInternshipDao.getByIdAndIsDeletedFalse(internshipId);
            if (internship == null) {
                continue;
            }
            JSONObject item = map.computeIfAbsent(internshipId, id -> {
                JSONObject obj = new JSONObject();
                obj.put("internshipId", id);
                obj.put("internshipName", internship.getName());
                return obj;
            });
            item.put("internshipName", internship.getName());
        }
    }

    private void collectInternalInternships(Integer teacherId, Map<Integer, JSONObject> map) {
        // ViewRelTitleTeacherStudent has no findByTeacherId — filter all by internship is heavy;
        // iterate known internships from title view via soft scan on teacher's records
        List<ViewRelTitleTeacherStudent> titleRows = findTitleRowsByTeacherId(teacherId);
        for (ViewRelTitleTeacherStudent row : titleRows) {
            if (row.getInternshipId() == null) {
                continue;
            }
            map.computeIfAbsent(row.getInternshipId(), id -> {
                JSONObject obj = new JSONObject();
                obj.put("internshipId", id);
                MainInternship internship = mainInternshipDao.getByIdAndIsDeletedFalse(id);
                obj.put("internshipName", internship != null ? internship.getName() : null);
                return obj;
            });
        }
    }

    private List<RelTeacherStudent> findRelTeacherStudentByTeacherId(Integer teacherId) {
        return relTeacherStudentDao.findByTeacherIdAndIsDeletedFalse(teacherId);
    }

    private List<ViewRelTitleTeacherStudent> findTitleRowsByTeacherId(Integer teacherId) {
        return viewRelTitleTeacherStudentDao.findByTeacherIdAndIsDeletedFalse(teacherId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JSONObject publish(Integer internshipId, String title, String content,
                              List<Integer> studentIds, Integer publisherId) {
        requirePositive(internshipId, "internshipId");
        requirePositive(publisherId, "publisherId");
        if (title == null || title.isBlank()) {
            throw BaseResponse.parameterInvalid.error("title 不能为空");
        }
        if (content == null || content.isBlank()) {
            throw BaseResponse.parameterInvalid.error("content 不能为空");
        }
        String trimmedTitle = title.trim();
        if (trimmedTitle.length() > 200) {
            throw BaseResponse.parameterInvalid.error("title 不能超过 200 字");
        }

        BoundAudience audience = resolveBoundAudience(publisherId, internshipId);
        if (audience.studentIds.isEmpty()) {
            throw BaseResponse.parameterInvalid.error("当前项目下暂无绑定学生");
        }

        Set<Integer> targetIds;
        if (studentIds == null || studentIds.isEmpty()) {
            targetIds = audience.studentIds;
        } else {
            targetIds = new LinkedHashSet<>();
            for (Integer sid : studentIds) {
                if (sid == null) {
                    continue;
                }
                if (!audience.studentIds.contains(sid)) {
                    throw BaseResponse.parameterInvalid.error("学生 " + sid + " 不在您的绑定范围内");
                }
                targetIds.add(sid);
            }
            if (targetIds.isEmpty()) {
                throw BaseResponse.parameterInvalid.error("请至少选择一名学生");
            }
        }

        Date now = new Date();
        MainNotice notice = new MainNotice();
        notice.setTitle(trimmedTitle);
        notice.setContent(content.trim());
        notice.setName(trimmedTitle.length() > 50 ? trimmedTitle.substring(0, 50) : trimmedTitle);
        notice.setInternshipId(internshipId);
        notice.setPublisherId(publisherId);
        notice.setPublishTime(now);
        notice.setIsDeleted(false);
        MainNotice saved = mainNoticeDao.save(notice);

        List<RelNoticeReceiver> receivers = new ArrayList<>();
        for (Integer studentId : targetIds) {
            RelNoticeReceiver receiver = new RelNoticeReceiver();
            receiver.setNoticeId(saved.getId());
            receiver.setStudentId(studentId);
            receiver.setIsRead(false);
            receiver.setIsDeleted(false);
            receivers.add(receiver);
        }
        relNoticeReceiverDao.saveAll(receivers);

        JSONObject result = new JSONObject();
        result.put("noticeId", saved.getId());
        result.put("receiverCount", receivers.size());
        return result;
    }

    @Override
    public JSONObject myPublished(Integer publisherId, Integer internshipId, Integer page, Integer size) {
        requirePositive(publisherId, "publisherId");
        PageRequest pageable = buildPageable(page, size, Sort.by(Sort.Direction.DESC, "publishTime", "id"));
        Page<ViewMainNotice> resultPage;
        if (internshipId != null && internshipId > 0) {
            resultPage = viewMainNoticeDao.findByPublisherIdAndInternshipIdAndIsDeletedFalse(
                    publisherId, internshipId, pageable);
        } else {
            resultPage = viewMainNoticeDao.findByPublisherIdAndIsDeletedFalse(publisherId, pageable);
        }
        return toPageJson(resultPage);
    }

    @Override
    public JSONObject myInbox(Integer studentId, Integer internshipId, Boolean isRead, Integer page, Integer size) {
        requirePositive(studentId, "studentId");
        PageRequest pageable = buildPageable(page, size, Sort.by(Sort.Direction.DESC, "publishTime", "id"));
        Page<ViewRelNoticeReceiver> resultPage;
        boolean hasInternship = internshipId != null && internshipId > 0;
        if (hasInternship && isRead != null) {
            resultPage = viewRelNoticeReceiverDao.findByStudentIdAndInternshipIdAndIsReadAndIsDeletedFalse(
                    studentId, internshipId, isRead, pageable);
        } else if (hasInternship) {
            resultPage = viewRelNoticeReceiverDao.findByStudentIdAndInternshipIdAndIsDeletedFalse(
                    studentId, internshipId, pageable);
        } else if (isRead != null) {
            resultPage = viewRelNoticeReceiverDao.findByStudentIdAndIsReadAndIsDeletedFalse(
                    studentId, isRead, pageable);
        } else {
            resultPage = viewRelNoticeReceiverDao.findByStudentIdAndIsDeletedFalse(studentId, pageable);
        }
        return toPageJson(resultPage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Integer studentId, Integer noticeId, List<Integer> noticeIds) {
        requirePositive(studentId, "studentId");
        Set<Integer> ids = new LinkedHashSet<>();
        if (noticeId != null && noticeId > 0) {
            ids.add(noticeId);
        }
        if (noticeIds != null) {
            for (Integer id : noticeIds) {
                if (id != null && id > 0) {
                    ids.add(id);
                }
            }
        }
        if (ids.isEmpty()) {
            throw BaseResponse.parameterInvalid.error("noticeId 或 noticeIds 不能为空");
        }
        Date now = new Date();
        for (Integer nid : ids) {
            List<RelNoticeReceiver> rows = relNoticeReceiverDao
                    .findByNoticeIdAndStudentIdAndIsDeletedFalse(nid, studentId);
            for (RelNoticeReceiver row : rows) {
                if (Boolean.TRUE.equals(row.getIsRead())) {
                    continue;
                }
                row.setIsRead(true);
                row.setReadTime(now);
                relNoticeReceiverDao.save(row);
            }
        }
    }

    @Override
    public JSONObject unreadCount(Integer studentId, Integer internshipId) {
        requirePositive(studentId, "studentId");
        Integer filterInternshipId = (internshipId != null && internshipId > 0) ? internshipId : null;
        long count = relNoticeReceiverDao.countUnreadByStudentId(studentId, filterInternshipId);
        JSONObject result = new JSONObject();
        result.put("count", count);
        return result;
    }

    @Override
    public JSONObject detail(Integer noticeId, Integer currentUserId) {
        requirePositive(noticeId, "noticeId");
        requirePositive(currentUserId, "currentUserId");
        MainNotice notice = mainNoticeDao.getByIdAndIsDeletedFalse(noticeId);
        if (notice == null) {
            throw BaseResponse.parameterInvalid.error("通知不存在或已删除");
        }

        boolean isPublisher = Objects.equals(notice.getPublisherId(), currentUserId);
        RelNoticeReceiver myReceiver = null;
        if (!isPublisher) {
            List<RelNoticeReceiver> rows = relNoticeReceiverDao
                    .findByNoticeIdAndStudentIdAndIsDeletedFalse(noticeId, currentUserId);
            if (rows.isEmpty()) {
                throw BaseResponse.lackPermissions.error("无权查看该通知");
            }
            myReceiver = rows.get(0);
        }

        JSONObject result = FastJsonUtil.toJson(notice);
        ViewBaseUser publisher = viewBaseUserDao.getByIdAndIsDeletedFalse(notice.getPublisherId());
        if (publisher != null) {
            result.put("publisherName", publisher.getName());
        }
        MainInternship internship = mainInternshipDao.getByIdAndIsDeletedFalse(notice.getInternshipId());
        if (internship != null) {
            result.put("internshipName", internship.getName());
        }
        if (isPublisher) {
            List<RelNoticeReceiver> receivers = relNoticeReceiverDao.findByNoticeIdAndIsDeletedFalse(noticeId);
            long readCount = receivers.stream().filter(r -> Boolean.TRUE.equals(r.getIsRead())).count();
            result.put("receiverCount", receivers.size());
            result.put("readCount", readCount);
            result.put("role", "publisher");
        } else {
            result.put("isRead", myReceiver.getIsRead());
            result.put("readTime", myReceiver.getReadTime());
            result.put("receiverId", myReceiver.getId());
            result.put("role", "receiver");
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer noticeId, Integer publisherId) {
        requirePositive(noticeId, "noticeId");
        requirePositive(publisherId, "publisherId");
        MainNotice notice = mainNoticeDao.getByIdAndIsDeletedFalse(noticeId);
        if (notice == null) {
            throw BaseResponse.parameterInvalid.error("通知不存在或已删除");
        }
        if (!Objects.equals(notice.getPublisherId(), publisherId)) {
            throw BaseResponse.lackPermissions.error("只能删除自己发布的通知");
        }
        notice.setIsDeleted(true);
        mainNoticeDao.save(notice);
        List<RelNoticeReceiver> receivers = relNoticeReceiverDao.findByNoticeIdAndIsDeletedFalse(noticeId);
        for (RelNoticeReceiver receiver : receivers) {
            receiver.setIsDeleted(true);
            relNoticeReceiverDao.save(receiver);
        }
    }

    /**
     * 解析老师在某项目下绑定的学生。校外走 RelTeacherStudent；校内走 ViewRelTitleTeacherStudent（优先 isFinal=1）。
     */
    private BoundAudience resolveBoundAudience(Integer teacherId, Integer internshipId) {
        List<MainInternshipPost> posts = mainInternshipPostDao.findByInternshipIdAndIsDeletedFalse(internshipId);
        BoundAudience audience = new BoundAudience();
        if (posts != null && !posts.isEmpty()) {
            resolveExternalAudience(teacherId, internshipId, audience);
        } else {
            resolveInternalAudience(teacherId, internshipId, audience);
        }
        return audience;
    }

    private void resolveExternalAudience(Integer teacherId, Integer internshipId, BoundAudience audience) {
        List<RelTeacherStudent> rows = relTeacherStudentDao.findByInternshipIdAndIsDeletedFalse(internshipId);
        Set<Integer> stuIdsFromRts = new LinkedHashSet<>();
        for (RelTeacherStudent rts : rows) {
            if (rts.getTeacherId() == null || !Objects.equals(rts.getTeacherId(), teacherId)) {
                continue;
            }
            if (rts.getStuId() != null) {
                stuIdsFromRts.add(rts.getStuId());
            }
        }
        audience.studentIds.addAll(stuIdsFromRts);
        if (!stuIdsFromRts.isEmpty()) {
            Map<Integer, ViewBaseUser> users = loadUsers(stuIdsFromRts);
            for (Integer sid : stuIdsFromRts) {
                ViewBaseUser u = users.get(sid);
                BoundStudentInfo info = new BoundStudentInfo();
                info.studentId = sid;
                if (u != null) {
                    info.studentName = u.getName();
                    info.studentAccount = u.getAccount();
                    info.studentDepartmentName = u.getDepartmentName();
                }
                audience.infoByStudentId.put(sid, info);
            }
        }
    }

    private void resolveInternalAudience(Integer teacherId, Integer internshipId, BoundAudience audience) {
        List<ViewRelTitleTeacherStudent> rows = viewRelTitleTeacherStudentDao
                .findByInternshipIdAndIsDeletedFalse(internshipId);
        List<ViewRelTitleTeacherStudent> mine = rows.stream()
                .filter(r -> Objects.equals(r.getTeacherId(), teacherId) && r.getStuId() != null)
                .collect(Collectors.toList());
        List<ViewRelTitleTeacherStudent> preferred = mine.stream()
                .filter(r -> Integer.valueOf(1).equals(r.getIsFinal()))
                .collect(Collectors.toList());
        List<ViewRelTitleTeacherStudent> effective = preferred.isEmpty() ? mine : preferred;
        for (ViewRelTitleTeacherStudent row : effective) {
            Integer sid = row.getStuId();
            if (sid == null || audience.studentIds.contains(sid)) {
                continue;
            }
            audience.studentIds.add(sid);
            BoundStudentInfo info = new BoundStudentInfo();
            info.studentId = sid;
            info.studentName = row.getStudentName();
            info.studentAccount = row.getStudentAccount();
            audience.infoByStudentId.put(sid, info);
        }
    }

    private Map<Integer, ViewBaseUser> loadUsers(Set<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, ViewBaseUser> map = new LinkedHashMap<>();
        for (ViewBaseUser u : viewBaseUserDao.getByIdInAndIsDeletedFalse(ids)) {
            map.put(u.getId(), u);
        }
        return map;
    }

    private PageRequest buildPageable(Integer page, Integer size, Sort sort) {
        int pageNum = (page == null || page < 1) ? DEFAULT_PAGE : page;
        int pageSize = (size == null || size < 1) ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return PageRequest.of(pageNum - 1, pageSize, sort);
    }

    private JSONObject toPageJson(Page<?> page) {
        List<JSONObject> content = page.getContent().stream()
                .map(FastJsonUtil::toJson)
                .collect(Collectors.toList());
        JSONObject data = new JSONObject();
        data.put("content", content);
        data.put("totalElements", page.getTotalElements());
        JSONObject pageMeta = new JSONObject();
        pageMeta.put("totalElements", page.getTotalElements());
        pageMeta.put("totalPages", page.getTotalPages());
        pageMeta.put("number", page.getNumber());
        pageMeta.put("size", page.getSize());
        data.put("page", pageMeta);
        return data;
    }

    private void requirePositive(Integer value, String field) {
        if (value == null || value <= 0) {
            throw BaseResponse.parameterInvalid.error(field + " 不能为空");
        }
    }

    private static class BoundAudience {
        private final Set<Integer> studentIds = new LinkedHashSet<>();
        private final Map<Integer, BoundStudentInfo> infoByStudentId = new LinkedHashMap<>();
    }

    private static class BoundStudentInfo {
        private Integer studentId;
        private String studentName;
        private String studentAccount;
        private String studentDepartmentName;
    }
}

package newcms.service;

import org.springframework.stereotype.Service;

@Service
public interface IInternshipPostService {

    /**
     * 学生选择岗位
     * @param studentId 学生ID
     * @param oldPostId 原岗位ID
     * @param newPostId 新岗位ID（0表示取消选择）
     * @return 操作结果
     */
    Object stuSelPost(Integer studentId, Integer oldPostId, Integer newPostId);
}

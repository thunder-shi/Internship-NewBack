package newcms.service;

import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Service;

import java.util.List;

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

    /**
     * 学生批量报名岗位（全部作为首次选择，oldPostId=0）。
     * 逐条调用 stuSelPost，单条失败不阻断其余。
     *
     * @param studentId       学生ID
     * @param internshipPostIds 岗位ID列表
     * @return { successCount, results: [{ internshipPostId, isAudit, message }] }
     */
    JSONObject stuSelPostBatch(Integer studentId, List<Integer> internshipPostIds);
}

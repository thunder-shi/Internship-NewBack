package newcms.service;

import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface IInternshipService {
    /**
     * 新增实习项目
     * @param node 实习项目数据
     * @return 保存后的实习项目实体
     */
    Object addNewInternship(JSONObject node);

    /**
     * 提交新增实习项目（保存并创建审核记录）
     * @param requestJson 前端传入的 JSON，包含 node（MainInternship 数据）和 createUserId
     * @return 保存后的实习项目实体
     */
    Object submitNewInternship(JSONObject requestJson);

    /**
     * 删除实习项目
     * @param internshipIds 实习项目ID列表
     * @return 删除结果
     */
    Object deleteNewInternship(List<Integer> internshipIds);
}

package newcms.service;

import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Service;

@Service
public interface IInternshipService {
    /**
     * 新增实习项目
     * @param node 实习项目数据
     * @return 保存后的实习项目实体
     */
    Object addNewInternship(JSONObject node);
}

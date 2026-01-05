package newcms.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public interface IImportAndExportService {

    Object exportInfo(String key, JSONArray nodes, JSONArray allTableColumns, JSONObject searchWords);
    void downTemplate(String keyWords);
    Object importBaseMajor(File file);
    Object importBaseUser(File file);
}

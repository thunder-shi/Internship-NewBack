package newcms.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "coze")
public class CozeProperties {

    /** 是否启用 Coze AI 批改 */
    private boolean enabled = false;

    private String apiBase = "https://api.coze.cn";

    private String workflowId = "";

    private String pat = "";

    /** 工作流开始节点的文件参数名（本工作流为 file） */
    private String fileUrlParam = "file";

    /** 文件入参模式：file-id=先上传 Coze 再传 file_id；url=传 MinIO presigned URL */
    private String fileInputMode = "file-id";

    /** 传给 Coze 的 MinIO presigned URL 有效期（秒），file-input-mode=url 时使用 */
    private int fileUrlExpireSeconds = 1800;

    public boolean useFileIdInput() {
        return !"url".equalsIgnoreCase(fileInputMode);
    }

    public boolean isConfigured() {
        return enabled
                && workflowId != null && !workflowId.isBlank()
                && pat != null && !pat.isBlank();
    }
}

package online.yudream.base.infra.platform.agent.dataobj;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Data
@Document("platformAgentWorkflowCheckpoint")
public class AgentWorkflowCheckpointDO {
    @Id private String id;
    private Long userId;
    private String status;
    /** 显式私有业务状态 JSON，禁止装入运行对象、AI 配置或权限快照。 */
    private String stateJson;
    private Instant updatedAt;
}

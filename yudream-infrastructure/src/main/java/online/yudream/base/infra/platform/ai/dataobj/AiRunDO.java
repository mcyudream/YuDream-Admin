package online.yudream.base.infra.platform.ai.dataobj;

import lombok.Data;
import online.yudream.base.domain.platform.ai.enumerate.AiRunStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Data
@Document("platformAiRun")
@CompoundIndex(name = "userSessionTime", def = "{'userId':1,'sessionId':1,'createdAt':-1}")
public class AiRunDO {
    @Id private String id;
    @org.springframework.data.mongodb.core.index.Indexed(unique = true, sparse = true)
    private String activeKey;
    private Long userId;
    private String sessionId;
    private String module;
    private String ownerInstance;
    private boolean cancelRequested;
    private online.yudream.base.domain.platform.ai.valobj.HarnessRunReference nativeReference;
    private boolean nativeResumeSupported;
    private online.yudream.base.domain.platform.ai.valobj.HarnessResumeProfile nativeProfile;
    private online.yudream.base.domain.platform.ai.valobj.HarnessSessionSnapshot nativeSnapshot;
    private java.util.Map<String, Object> frontendContext;
    private online.yudream.base.domain.platform.ai.valobj.AiInvocationContextSnapshot invocationContext;
    private AiRunStatus status;
    private long cursor;
    private String content;
    private String reasoning;
    private Object result;
    private String error;
    private Instant createdAt;
    private Instant updatedAt;
}

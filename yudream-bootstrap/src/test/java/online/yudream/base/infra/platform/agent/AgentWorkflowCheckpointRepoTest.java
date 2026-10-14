package online.yudream.base.infra.platform.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.result.UpdateResult;
import online.yudream.base.infra.platform.agent.dataobj.AgentWorkflowCheckpointDO;
import online.yudream.base.infra.platform.agent.impl.AgentWorkflowCheckpointRepoImpl;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentWorkflowCheckpointRepoTest {
    @Test void terminalCancellationCannotBeOverwrittenByLateWorkflowCompletion() {
        var status = new AtomicReference<>("WAITING");
        var repo = repository(status);
        repo.cancel("run-id");
        repo.complete("run-id");
        assertEquals("CANCELLED", status.get());
    }

    @Test void terminalCompletionCannotBeRewrittenAsCancelledByLateRequest() {
        var status = new AtomicReference<>("RUNNING");
        var repo = repository(status);
        repo.complete("run-id");
        repo.cancel("run-id");
        assertEquals("COMPLETED", status.get());
    }

    private AgentWorkflowCheckpointRepoImpl repository(AtomicReference<String> status) {
        var mongo = mock(MongoTemplate.class);
        when(mongo.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(AgentWorkflowCheckpointDO.class)))
                .thenAnswer(invocation -> {
                    Query query = invocation.getArgument(0);
                    UpdateDefinition update = invocation.getArgument(1);
                    Object criterion = query.getQueryObject().get("status");
                    boolean allowed = criterion == null || criterion instanceof org.bson.Document document
                            && document.get("$in") instanceof List<?> values && values.contains(status.get());
                    if (allowed) status.set(update.getUpdateObject().get("$set", org.bson.Document.class).getString("status"));
                    return UpdateResult.acknowledged(allowed ? 1 : 0, allowed ? 1L : 0L, null);
                });
        return new AgentWorkflowCheckpointRepoImpl(mongo, new ObjectMapper());
    }
}

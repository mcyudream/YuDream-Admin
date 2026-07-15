package online.yudream.base.application.platform.agent.workflow.handler;

import online.yudream.base.application.platform.agent.workflow.AgentWorkflowContext;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowNode;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowNodeHandler;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowNodeResult;
import online.yudream.base.application.platform.agent.workflow.support.AgentWorkflowValueResolver;
import online.yudream.base.application.platform.agent.workflow.support.AgentWorkflowRunState;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.document.service.DocumentTextExtractor;
import online.yudream.base.domain.platform.document.valobj.DocumentSource;

import java.util.Map;

public final class AgentDocumentNodeHandler implements AgentWorkflowNodeHandler {
    private final AgentWorkflowValueResolver values;
    private final DocumentTextExtractor extractor;
    private final AgentWorkflowRunState state;

    public AgentDocumentNodeHandler(AgentWorkflowValueResolver values, DocumentTextExtractor extractor) {
        this(values, extractor, null);
    }

    public AgentDocumentNodeHandler(
            AgentWorkflowValueResolver values,
            DocumentTextExtractor extractor,
            AgentWorkflowRunState state
    ) {
        this.values = values;
        this.extractor = extractor;
        this.state = state;
    }

    @Override
    public String kind() {
        return "document";
    }

    @Override
    public AgentWorkflowNodeResult execute(AgentWorkflowNode node, AgentWorkflowContext context) {
        String configuredInput = values.text(node, "documentInput");
        Object input = "attachment".equals(configuredInput) ? attachment() : null;
        if (input == null) {
            input = configuredInput.isBlank() || "attachment".equals(configuredInput)
                    ? values.input(node, context)
                    : values.resolve(configuredInput, context);
        }
        return values.result(node, extractor.extract(source(input)));
    }

    private Object attachment() {
        if (state == null || state.command().getAttachments() == null || state.command().getAttachments().isEmpty()) {
            return null;
        }
        var attachment = state.command().getAttachments().getFirst();
        return Map.of(
                "dataUrl", attachment.dataUrl() == null ? "" : attachment.dataUrl(),
                "contentType", attachment.contentType() == null ? "" : attachment.contentType(),
                "fileName", attachment.name() == null ? "" : attachment.name()
        );
    }

    private DocumentSource source(Object input) {
        if (input instanceof String content && !content.isBlank()) {
            return DocumentSource.dataUrl(content);
        }
        if (input instanceof Map<?, ?> map) {
            String content = text(map.get("dataUrl"));
            if (content.isBlank()) {
                content = text(map.get("content"));
            }
            if (!content.isBlank()) {
                return DocumentSource.base64(content, text(map.get("contentType")), text(map.get("fileName")));
            }
        }
        throw new BizException("文档节点未获得可解析的附件内容");
    }

    private String text(Object value) {
        return value == null ? "" : value.toString();
    }
}

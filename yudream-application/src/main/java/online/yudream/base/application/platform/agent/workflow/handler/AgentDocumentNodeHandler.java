package online.yudream.base.application.platform.agent.workflow.handler;

import online.yudream.base.application.platform.agent.workflow.AgentWorkflowContext;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowNode;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowNodeHandler;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowNodeResult;
import online.yudream.base.application.platform.agent.workflow.support.AgentWorkflowValueResolver;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.document.service.DocumentTextExtractor;
import online.yudream.base.domain.platform.document.valobj.DocumentSource;

import java.util.Map;

public final class AgentDocumentNodeHandler implements AgentWorkflowNodeHandler {
    private final AgentWorkflowValueResolver values;
    private final DocumentTextExtractor extractor;

    public AgentDocumentNodeHandler(AgentWorkflowValueResolver values, DocumentTextExtractor extractor) {
        this.values = values;
        this.extractor = extractor;
    }

    @Override
    public String kind() {
        return "document";
    }

    @Override
    public AgentWorkflowNodeResult execute(AgentWorkflowNode node, AgentWorkflowContext context) {
        String configuredInput = values.text(node, "documentInput");
        Object input = attachment(configuredInput, context);
        if (input == null) {
            input = configuredInput.isBlank() || "attachment".equals(configuredInput)
                    ? values.input(node, context)
                    : values.resolve(configuredInput, context);
        }
        return values.result(node, extractor.extract(source(input)));
    }

    private Object attachment(String selector, AgentWorkflowContext context) {
        if (!selector.isBlank() && !"attachment".equals(selector) && !selector.startsWith("attachment:")) {
            return null;
        }
        String fileName = selector.startsWith("attachment:") ? selector.substring("attachment:".length()).trim() : "";
        Object all = context.variable("attachments");
        if (!(all instanceof java.util.List<?> attachments) || attachments.isEmpty()) {
            return null;
        }
        if (fileName.isBlank()) {
            return attachments.getFirst();
        }
        return attachments.stream()
                .filter(item -> item instanceof Map<?, ?> map && fileName.equals(String.valueOf(map.get("name"))))
                .findFirst()
                .orElseThrow(() -> new BizException("未找到文档附件：" + fileName));
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

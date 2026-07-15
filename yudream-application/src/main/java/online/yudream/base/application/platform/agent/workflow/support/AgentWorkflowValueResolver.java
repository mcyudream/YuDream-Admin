package online.yudream.base.application.platform.agent.workflow.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowContext;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowNode;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowNodeResult;
import online.yudream.base.domain.common.exception.BizException;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AgentWorkflowValueResolver {
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TemplateParserContext TEMPLATE_CONTEXT = new TemplateParserContext("{{", "}}");

    private final ObjectMapper objectMapper;
    private final SpelExpressionParser expressionParser = new SpelExpressionParser();

    public AgentWorkflowValueResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Object input(AgentWorkflowNode node, AgentWorkflowContext context) {
        String variable = text(node, "inputVariable");
        if (StringUtils.hasText(variable)) {
            return resolve(variable, context);
        }
        Object latest = null;
        for (Object output : context.nodeOutputs().values()) {
            latest = output;
        }
        return latest == null ? context.input() : latest;
    }

    public Object resolve(String path, AgentWorkflowContext context) {
        if (!StringUtils.hasText(path)) {
            return inputValue(context);
        }
        try {
            return expressionParser.parseExpression(path).getValue(evaluationContext(context));
        } catch (RuntimeException exception) {
            throw new BizException("无法解析工作流变量：" + path);
        }
    }

    public String render(String template, AgentWorkflowContext context) {
        if (template == null) {
            return "";
        }
        try {
            return expressionParser.parseExpression(template, TEMPLATE_CONTEXT)
                    .getValue(evaluationContext(context), String.class);
        } catch (RuntimeException exception) {
            throw new BizException("模板表达式无效：" + exception.getMessage());
        }
    }

    public boolean condition(String expression, AgentWorkflowContext context) {
        if (!StringUtils.hasText(expression)) {
            throw new BizException("条件节点必须配置判断表达式");
        }
        try {
            Boolean matched = expressionParser.parseExpression(expression)
                    .getValue(evaluationContext(context), Boolean.class);
            return Boolean.TRUE.equals(matched);
        } catch (RuntimeException exception) {
            throw new BizException("条件表达式无效：" + exception.getMessage());
        }
    }

    public AgentWorkflowNodeResult result(AgentWorkflowNode node, Object output) {
        AgentWorkflowNodeResult result = AgentWorkflowNodeResult.output(output);
        String variable = text(node, "outputVariable");
        return StringUtils.hasText(variable)
                ? result.withVariables(Map.of(variable, output == null ? "" : output))
                : result;
    }

    public String text(AgentWorkflowNode node, String field) {
        return node.data().path(field).asText("").trim();
    }

    public int integer(AgentWorkflowNode node, String field, int defaultValue) {
        return node.data().path(field).canConvertToInt() ? node.data().path(field).asInt() : defaultValue;
    }

    public boolean bool(AgentWorkflowNode node, String field, boolean defaultValue) {
        return node.data().has(field) ? node.data().path(field).asBoolean(defaultValue) : defaultValue;
    }

    private Object inputValue(AgentWorkflowContext context) {
        Object latest = null;
        for (Object value : context.nodeOutputs().values()) {
            latest = value;
        }
        return latest == null ? context.input() : latest;
    }

    private EvaluationContext evaluationContext(AgentWorkflowContext context) {
        Map<String, Object> values = objectMapper.convertValue(context.variables(), MAP_TYPE);
        values.put("nodes", objectMapper.convertValue(context.nodeOutputs(), MAP_TYPE));
        return SimpleEvaluationContext.forPropertyAccessors(new MapAccessor())
                .withAssignmentDisabled()
                .withRootObject(values)
                .build();
    }
}

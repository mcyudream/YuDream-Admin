package online.yudream.base.application.platform.integration.assembler;

import online.yudream.base.application.platform.integration.dto.HttpConnectorDTO;
import online.yudream.base.application.platform.integration.dto.HttpInvocationLogDTO;
import online.yudream.base.application.platform.integration.dto.RuntimeExecutionLogDTO;
import online.yudream.base.application.platform.integration.dto.RuntimeScriptDTO;
import online.yudream.base.domain.platform.integration.aggregate.HttpConnector;
import online.yudream.base.domain.platform.integration.aggregate.HttpInvocationLog;
import online.yudream.base.domain.platform.integration.aggregate.RuntimeExecutionLog;
import online.yudream.base.domain.platform.integration.aggregate.RuntimeScript;

public class IntegrationAssembler {

    private IntegrationAssembler() {
    }

    public static HttpConnectorDTO toDTO(HttpConnector connector) {
        return HttpConnectorDTO.builder()
                .id(connector.getId())
                .name(connector.getName())
                .code(connector.getCode())
                .url(connector.getUrl())
                .method(connector.getMethod())
                .headers(connector.getHeaders())
                .queryParams(connector.getQueryParams())
                .bodyTemplate(connector.getBodyTemplate())
                .timeoutMillis(connector.getTimeoutMillis())
                .retryTimes(connector.getRetryTimes())
                .status(connector.getStatus())
                .createTime(connector.getCreateTime())
                .updateTime(connector.getUpdateTime())
                .build();
    }

    public static HttpInvocationLogDTO toDTO(HttpInvocationLog log) {
        return HttpInvocationLogDTO.builder()
                .id(log.getId())
                .connectorId(log.getConnectorId())
                .connectorCode(log.getConnectorCode())
                .url(log.getUrl())
                .method(log.getMethod())
                .requestHeaders(log.getRequestHeaders())
                .requestBody(log.getRequestBody())
                .responseStatus(log.getResponseStatus())
                .responseBody(log.getResponseBody())
                .durationMillis(log.getDurationMillis())
                .status(log.getStatus())
                .errorMessage(log.getErrorMessage())
                .invokedAt(log.getInvokedAt())
                .build();
    }

    public static RuntimeScriptDTO toDTO(RuntimeScript script) {
        return RuntimeScriptDTO.builder()
                .id(script.getId())
                .name(script.getName())
                .code(script.getCode())
                .language(script.getLanguage())
                .scriptContent(script.getScriptContent())
                .timeoutMillis(script.getTimeoutMillis())
                .env(script.getEnv())
                .status(script.getStatus())
                .createTime(script.getCreateTime())
                .updateTime(script.getUpdateTime())
                .build();
    }

    public static RuntimeExecutionLogDTO toDTO(RuntimeExecutionLog log) {
        return RuntimeExecutionLogDTO.builder()
                .id(log.getId())
                .scriptId(log.getScriptId())
                .scriptCode(log.getScriptCode())
                .language(log.getLanguage())
                .stdin(log.getStdin())
                .stdout(log.getStdout())
                .stderr(log.getStderr())
                .exitCode(log.getExitCode())
                .durationMillis(log.getDurationMillis())
                .status(log.getStatus())
                .errorMessage(log.getErrorMessage())
                .executedAt(log.getExecutedAt())
                .build();
    }
}

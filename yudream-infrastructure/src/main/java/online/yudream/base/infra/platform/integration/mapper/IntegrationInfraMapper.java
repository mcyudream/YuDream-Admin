package online.yudream.base.infra.platform.integration.mapper;

import online.yudream.base.domain.platform.integration.aggregate.HttpConnector;
import online.yudream.base.domain.platform.integration.aggregate.HttpInvocationLog;
import online.yudream.base.domain.platform.integration.aggregate.RuntimeExecutionLog;
import online.yudream.base.domain.platform.integration.aggregate.RuntimeScript;
import online.yudream.base.infra.platform.integration.dataobj.HttpConnectorDO;
import online.yudream.base.infra.platform.integration.dataobj.HttpInvocationLogDO;
import online.yudream.base.infra.platform.integration.dataobj.RuntimeExecutionLogDO;
import online.yudream.base.infra.platform.integration.dataobj.RuntimeScriptDO;

public class IntegrationInfraMapper {

    private IntegrationInfraMapper() {
    }

    public static HttpConnectorDO toDataObj(HttpConnector domain) {
        if (domain == null) {
            return null;
        }
        HttpConnectorDO dataObj = new HttpConnectorDO();
        dataObj.setId(domain.getId());
        dataObj.setName(domain.getName());
        dataObj.setCode(domain.getCode());
        dataObj.setUrl(domain.getUrl());
        dataObj.setMethod(domain.getMethod());
        dataObj.setHeaders(domain.getHeaders());
        dataObj.setQueryParams(domain.getQueryParams());
        dataObj.setBodyTemplate(domain.getBodyTemplate());
        dataObj.setTimeoutMillis(domain.getTimeoutMillis());
        dataObj.setRetryTimes(domain.getRetryTimes());
        dataObj.setStatus(domain.getStatus());
        dataObj.setVersion(domain.getVersion());
        dataObj.setCreateTime(domain.getCreateTime());
        dataObj.setUpdateTime(domain.getUpdateTime());
        return dataObj;
    }

    public static HttpConnector toDomain(HttpConnectorDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        return HttpConnector.builder()
                .id(dataObj.getId())
                .name(dataObj.getName())
                .code(dataObj.getCode())
                .url(dataObj.getUrl())
                .method(dataObj.getMethod())
                .headers(dataObj.getHeaders())
                .queryParams(dataObj.getQueryParams())
                .bodyTemplate(dataObj.getBodyTemplate())
                .timeoutMillis(dataObj.getTimeoutMillis())
                .retryTimes(dataObj.getRetryTimes())
                .status(dataObj.getStatus())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .build();
    }

    public static HttpInvocationLogDO toDataObj(HttpInvocationLog domain) {
        if (domain == null) {
            return null;
        }
        HttpInvocationLogDO dataObj = new HttpInvocationLogDO();
        dataObj.setId(domain.getId());
        dataObj.setConnectorId(domain.getConnectorId());
        dataObj.setConnectorCode(domain.getConnectorCode());
        dataObj.setUrl(domain.getUrl());
        dataObj.setMethod(domain.getMethod());
        dataObj.setRequestHeaders(domain.getRequestHeaders());
        dataObj.setRequestBody(domain.getRequestBody());
        dataObj.setResponseStatus(domain.getResponseStatus());
        dataObj.setResponseBody(domain.getResponseBody());
        dataObj.setDurationMillis(domain.getDurationMillis());
        dataObj.setStatus(domain.getStatus());
        dataObj.setErrorMessage(domain.getErrorMessage());
        dataObj.setInvokedAt(domain.getInvokedAt());
        dataObj.setVersion(domain.getVersion());
        dataObj.setCreateTime(domain.getCreateTime());
        dataObj.setUpdateTime(domain.getUpdateTime());
        return dataObj;
    }

    public static HttpInvocationLog toDomain(HttpInvocationLogDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        return HttpInvocationLog.builder()
                .id(dataObj.getId())
                .connectorId(dataObj.getConnectorId())
                .connectorCode(dataObj.getConnectorCode())
                .url(dataObj.getUrl())
                .method(dataObj.getMethod())
                .requestHeaders(dataObj.getRequestHeaders())
                .requestBody(dataObj.getRequestBody())
                .responseStatus(dataObj.getResponseStatus())
                .responseBody(dataObj.getResponseBody())
                .durationMillis(dataObj.getDurationMillis())
                .status(dataObj.getStatus())
                .errorMessage(dataObj.getErrorMessage())
                .invokedAt(dataObj.getInvokedAt())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .build();
    }

    public static RuntimeScriptDO toDataObj(RuntimeScript domain) {
        if (domain == null) {
            return null;
        }
        RuntimeScriptDO dataObj = new RuntimeScriptDO();
        dataObj.setId(domain.getId());
        dataObj.setName(domain.getName());
        dataObj.setCode(domain.getCode());
        dataObj.setLanguage(domain.getLanguage());
        dataObj.setScriptContent(domain.getScriptContent());
        dataObj.setTimeoutMillis(domain.getTimeoutMillis());
        dataObj.setEnv(domain.getEnv());
        dataObj.setStatus(domain.getStatus());
        dataObj.setVersion(domain.getVersion());
        dataObj.setCreateTime(domain.getCreateTime());
        dataObj.setUpdateTime(domain.getUpdateTime());
        return dataObj;
    }

    public static RuntimeScript toDomain(RuntimeScriptDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        return RuntimeScript.builder()
                .id(dataObj.getId())
                .name(dataObj.getName())
                .code(dataObj.getCode())
                .language(dataObj.getLanguage())
                .scriptContent(dataObj.getScriptContent())
                .timeoutMillis(dataObj.getTimeoutMillis())
                .env(dataObj.getEnv())
                .status(dataObj.getStatus())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .build();
    }

    public static RuntimeExecutionLogDO toDataObj(RuntimeExecutionLog domain) {
        if (domain == null) {
            return null;
        }
        RuntimeExecutionLogDO dataObj = new RuntimeExecutionLogDO();
        dataObj.setId(domain.getId());
        dataObj.setScriptId(domain.getScriptId());
        dataObj.setScriptCode(domain.getScriptCode());
        dataObj.setLanguage(domain.getLanguage());
        dataObj.setStdin(domain.getStdin());
        dataObj.setStdout(domain.getStdout());
        dataObj.setStderr(domain.getStderr());
        dataObj.setExitCode(domain.getExitCode());
        dataObj.setDurationMillis(domain.getDurationMillis());
        dataObj.setStatus(domain.getStatus());
        dataObj.setErrorMessage(domain.getErrorMessage());
        dataObj.setExecutedAt(domain.getExecutedAt());
        dataObj.setVersion(domain.getVersion());
        dataObj.setCreateTime(domain.getCreateTime());
        dataObj.setUpdateTime(domain.getUpdateTime());
        return dataObj;
    }

    public static RuntimeExecutionLog toDomain(RuntimeExecutionLogDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        return RuntimeExecutionLog.builder()
                .id(dataObj.getId())
                .scriptId(dataObj.getScriptId())
                .scriptCode(dataObj.getScriptCode())
                .language(dataObj.getLanguage())
                .stdin(dataObj.getStdin())
                .stdout(dataObj.getStdout())
                .stderr(dataObj.getStderr())
                .exitCode(dataObj.getExitCode())
                .durationMillis(dataObj.getDurationMillis())
                .status(dataObj.getStatus())
                .errorMessage(dataObj.getErrorMessage())
                .executedAt(dataObj.getExecutedAt())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .build();
    }
}

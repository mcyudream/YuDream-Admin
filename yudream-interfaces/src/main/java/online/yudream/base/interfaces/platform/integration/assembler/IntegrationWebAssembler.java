package online.yudream.base.interfaces.platform.integration.assembler;

import online.yudream.base.application.platform.integration.cmd.HttpConnectorSaveCmd;
import online.yudream.base.application.platform.integration.cmd.HttpInvokeCmd;
import online.yudream.base.application.platform.integration.cmd.RuntimeExecuteCmd;
import online.yudream.base.application.platform.integration.cmd.RuntimeScriptSaveCmd;
import online.yudream.base.application.platform.integration.dto.HttpConnectorDTO;
import online.yudream.base.application.platform.integration.dto.HttpInvocationLogDTO;
import online.yudream.base.application.platform.integration.dto.RuntimeExecutionLogDTO;
import online.yudream.base.application.platform.integration.dto.RuntimeScriptDTO;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.interfaces.platform.integration.request.HttpConnectorSaveRequest;
import online.yudream.base.interfaces.platform.integration.request.HttpInvokeRequest;
import online.yudream.base.interfaces.platform.integration.request.RuntimeExecuteRequest;
import online.yudream.base.interfaces.platform.integration.request.RuntimeScriptSaveRequest;
import online.yudream.base.interfaces.platform.integration.res.HttpConnectorRes;
import online.yudream.base.interfaces.platform.integration.res.HttpInvocationLogRes;
import online.yudream.base.interfaces.platform.integration.res.RuntimeExecutionLogRes;
import online.yudream.base.interfaces.platform.integration.res.RuntimeScriptRes;

public class IntegrationWebAssembler {

    private IntegrationWebAssembler() {
    }

    public static HttpConnectorSaveCmd toCmd(HttpConnectorSaveRequest request) {
        return toCmd(null, request);
    }

    public static HttpConnectorSaveCmd toCmd(Long id, HttpConnectorSaveRequest request) {
        HttpConnectorSaveCmd cmd = new HttpConnectorSaveCmd();
        cmd.setId(id);
        cmd.setName(request.getName());
        cmd.setCode(request.getCode());
        cmd.setUrl(request.getUrl());
        cmd.setMethod(request.getMethod());
        cmd.setHeaders(request.getHeaders());
        cmd.setQueryParams(request.getQueryParams());
        cmd.setBodyTemplate(request.getBodyTemplate());
        cmd.setTimeoutMillis(request.getTimeoutMillis());
        cmd.setRetryTimes(request.getRetryTimes());
        cmd.setStatus(request.getStatus());
        return cmd;
    }

    public static HttpInvokeCmd toCmd(Long id, HttpInvokeRequest request) {
        HttpInvokeCmd cmd = new HttpInvokeCmd();
        cmd.setConnectorId(id);
        cmd.setHeaders(request.getHeaders());
        cmd.setQueryParams(request.getQueryParams());
        cmd.setBody(request.getBody());
        return cmd;
    }

    public static RuntimeScriptSaveCmd toCmd(RuntimeScriptSaveRequest request) {
        return toCmd(null, request);
    }

    public static RuntimeScriptSaveCmd toCmd(Long id, RuntimeScriptSaveRequest request) {
        RuntimeScriptSaveCmd cmd = new RuntimeScriptSaveCmd();
        cmd.setId(id);
        cmd.setName(request.getName());
        cmd.setCode(request.getCode());
        cmd.setLanguage(request.getLanguage());
        cmd.setScriptContent(request.getScriptContent());
        cmd.setTimeoutMillis(request.getTimeoutMillis());
        cmd.setEnv(request.getEnv());
        cmd.setStatus(request.getStatus());
        return cmd;
    }

    public static RuntimeExecuteCmd toCmd(Long id, RuntimeExecuteRequest request) {
        RuntimeExecuteCmd cmd = new RuntimeExecuteCmd();
        cmd.setScriptId(id);
        cmd.setStdin(request.getStdin());
        return cmd;
    }

    public static PageResult<HttpConnectorRes> toConnectorPage(PageResult<HttpConnectorDTO> page) {
        return new PageResult<>(page.getRecords().stream().map(IntegrationWebAssembler::toRes).toList(), page.getTotal(), page.getPage(), page.getSize());
    }

    public static PageResult<HttpInvocationLogRes> toHttpLogPage(PageResult<HttpInvocationLogDTO> page) {
        return new PageResult<>(page.getRecords().stream().map(IntegrationWebAssembler::toRes).toList(), page.getTotal(), page.getPage(), page.getSize());
    }

    public static PageResult<RuntimeScriptRes> toScriptPage(PageResult<RuntimeScriptDTO> page) {
        return new PageResult<>(page.getRecords().stream().map(IntegrationWebAssembler::toRes).toList(), page.getTotal(), page.getPage(), page.getSize());
    }

    public static PageResult<RuntimeExecutionLogRes> toExecutionLogPage(PageResult<RuntimeExecutionLogDTO> page) {
        return new PageResult<>(page.getRecords().stream().map(IntegrationWebAssembler::toRes).toList(), page.getTotal(), page.getPage(), page.getSize());
    }

    public static HttpConnectorRes toRes(HttpConnectorDTO dto) {
        return HttpConnectorRes.builder()
                .id(dto.getId())
                .name(dto.getName())
                .code(dto.getCode())
                .url(dto.getUrl())
                .method(dto.getMethod())
                .headers(dto.getHeaders())
                .queryParams(dto.getQueryParams())
                .bodyTemplate(dto.getBodyTemplate())
                .timeoutMillis(dto.getTimeoutMillis())
                .retryTimes(dto.getRetryTimes())
                .status(dto.getStatus())
                .createTime(dto.getCreateTime())
                .updateTime(dto.getUpdateTime())
                .build();
    }

    public static HttpInvocationLogRes toRes(HttpInvocationLogDTO dto) {
        return HttpInvocationLogRes.builder()
                .id(dto.getId())
                .connectorId(dto.getConnectorId())
                .connectorCode(dto.getConnectorCode())
                .url(dto.getUrl())
                .method(dto.getMethod())
                .requestHeaders(dto.getRequestHeaders())
                .requestBody(dto.getRequestBody())
                .responseStatus(dto.getResponseStatus())
                .responseBody(dto.getResponseBody())
                .durationMillis(dto.getDurationMillis())
                .status(dto.getStatus())
                .errorMessage(dto.getErrorMessage())
                .invokedAt(dto.getInvokedAt())
                .build();
    }

    public static RuntimeScriptRes toRes(RuntimeScriptDTO dto) {
        return RuntimeScriptRes.builder()
                .id(dto.getId())
                .name(dto.getName())
                .code(dto.getCode())
                .language(dto.getLanguage())
                .scriptContent(dto.getScriptContent())
                .timeoutMillis(dto.getTimeoutMillis())
                .env(dto.getEnv())
                .status(dto.getStatus())
                .createTime(dto.getCreateTime())
                .updateTime(dto.getUpdateTime())
                .build();
    }

    public static RuntimeExecutionLogRes toRes(RuntimeExecutionLogDTO dto) {
        return RuntimeExecutionLogRes.builder()
                .id(dto.getId())
                .scriptId(dto.getScriptId())
                .scriptCode(dto.getScriptCode())
                .language(dto.getLanguage())
                .stdin(dto.getStdin())
                .stdout(dto.getStdout())
                .stderr(dto.getStderr())
                .exitCode(dto.getExitCode())
                .durationMillis(dto.getDurationMillis())
                .status(dto.getStatus())
                .errorMessage(dto.getErrorMessage())
                .executedAt(dto.getExecutedAt())
                .build();
    }
}

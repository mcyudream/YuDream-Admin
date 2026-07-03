package online.yudream.base.application.platform.integration.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.integration.assembler.IntegrationAssembler;
import online.yudream.base.application.platform.integration.cmd.HttpConnectorSaveCmd;
import online.yudream.base.application.platform.integration.cmd.HttpInvokeCmd;
import online.yudream.base.application.platform.integration.cmd.RuntimeExecuteCmd;
import online.yudream.base.application.platform.integration.cmd.RuntimeScriptSaveCmd;
import online.yudream.base.application.platform.integration.dto.HttpConnectorDTO;
import online.yudream.base.application.platform.integration.dto.HttpInvocationLogDTO;
import online.yudream.base.application.platform.integration.dto.RuntimeExecutionLogDTO;
import online.yudream.base.application.platform.integration.dto.RuntimeScriptDTO;
import online.yudream.base.application.platform.integration.query.IntegrationPageQuery;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.integration.aggregate.HttpConnector;
import online.yudream.base.domain.platform.integration.aggregate.HttpInvocationLog;
import online.yudream.base.domain.platform.integration.aggregate.RuntimeExecutionLog;
import online.yudream.base.domain.platform.integration.aggregate.RuntimeScript;
import online.yudream.base.domain.platform.integration.enumerate.ConnectorStatus;
import online.yudream.base.domain.platform.integration.repo.HttpConnectorRepo;
import online.yudream.base.domain.platform.integration.repo.HttpInvocationLogRepo;
import online.yudream.base.domain.platform.integration.repo.RuntimeExecutionLogRepo;
import online.yudream.base.domain.platform.integration.repo.RuntimeScriptRepo;
import online.yudream.base.domain.platform.integration.service.HttpInvocationGateway;
import online.yudream.base.domain.platform.integration.service.RuntimeExecutor;
import online.yudream.base.domain.platform.integration.valobj.HttpInvocationResult;
import online.yudream.base.domain.platform.integration.valobj.RuntimeExecutionResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class IntegrationAppService {

    private static final String INTEGRATION_CAPABILITY_CODE = "integration";

    private final CapabilityModuleRepo capabilityModuleRepo;
    private final HttpConnectorRepo httpConnectorRepo;
    private final HttpInvocationLogRepo httpInvocationLogRepo;
    private final RuntimeScriptRepo runtimeScriptRepo;
    private final RuntimeExecutionLogRepo runtimeExecutionLogRepo;
    private final HttpInvocationGateway httpInvocationGateway;
    private final RuntimeExecutor runtimeExecutor;

    @Transactional(readOnly = true)
    public PageResult<HttpConnectorDTO> pageConnectors(IntegrationPageQuery query) {
        ensureIntegrationEnabled();
        PageResult<HttpConnector> page = httpConnectorRepo.page(query.getKeyword(), query.getPage(), query.getSize());
        return new PageResult<>(page.getRecords().stream().map(IntegrationAssembler::toDTO).toList(), page.getTotal(), page.getPage(), page.getSize());
    }

    @Transactional
    public HttpConnectorDTO saveConnector(HttpConnectorSaveCmd cmd) {
        ensureIntegrationEnabled();
        HttpConnector connector = cmd.getId() == null ? createConnector(cmd) : connector(cmd.getId());
        connector.update(cmd.getName(), cmd.getUrl(), cmd.getMethod(), cmd.getHeaders(), cmd.getQueryParams(),
                cmd.getBodyTemplate(), cmd.getTimeoutMillis(), cmd.getRetryTimes(), cmd.getStatus());
        return IntegrationAssembler.toDTO(httpConnectorRepo.save(connector));
    }

    @Transactional
    public void disableConnector(Long id) {
        ensureIntegrationEnabled();
        HttpConnector connector = connector(id);
        connector.disable();
        httpConnectorRepo.save(connector);
    }

    @Transactional
    public HttpInvocationLogDTO invoke(HttpInvokeCmd cmd) {
        ensureIntegrationEnabled();
        HttpConnector connector = connector(cmd.getConnectorId());
        if (connector.getStatus() != ConnectorStatus.ACTIVE) {
            throw new BizException("HTTP 连接器已停用");
        }
        Map<String, String> headers = merge(connector.getHeaders(), cmd.getHeaders());
        Map<String, String> queryParams = merge(connector.getQueryParams(), cmd.getQueryParams());
        String body = cmd.getBody() == null ? connector.getBodyTemplate() : cmd.getBody();
        HttpInvocationResult result = httpInvocationGateway.invoke(connector, headers, queryParams, body);
        HttpInvocationLog log = HttpInvocationLog.builder()
                .connectorId(connector.getId())
                .connectorCode(connector.getCode())
                .url(connector.getUrl())
                .method(connector.getMethod())
                .requestHeaders(redact(headers))
                .requestBody(body)
                .responseStatus(result.statusCode())
                .responseBody(result.body())
                .durationMillis(result.durationMillis())
                .status(result.status())
                .errorMessage(result.errorMessage())
                .invokedAt(LocalDateTime.now())
                .build();
        return IntegrationAssembler.toDTO(httpInvocationLogRepo.save(log));
    }

    @Transactional(readOnly = true)
    public PageResult<HttpInvocationLogDTO> pageHttpLogs(IntegrationPageQuery query) {
        ensureIntegrationEnabled();
        PageResult<HttpInvocationLog> page = httpInvocationLogRepo.page(query.getKeyword(), query.getPage(), query.getSize());
        return new PageResult<>(page.getRecords().stream().map(IntegrationAssembler::toDTO).toList(), page.getTotal(), page.getPage(), page.getSize());
    }

    @Transactional(readOnly = true)
    public PageResult<RuntimeScriptDTO> pageScripts(IntegrationPageQuery query) {
        ensureIntegrationEnabled();
        PageResult<RuntimeScript> page = runtimeScriptRepo.page(query.getKeyword(), query.getPage(), query.getSize());
        return new PageResult<>(page.getRecords().stream().map(IntegrationAssembler::toDTO).toList(), page.getTotal(), page.getPage(), page.getSize());
    }

    @Transactional
    public RuntimeScriptDTO saveScript(RuntimeScriptSaveCmd cmd) {
        ensureIntegrationEnabled();
        RuntimeScript script = cmd.getId() == null ? createScript(cmd) : script(cmd.getId());
        script.update(cmd.getName(), cmd.getLanguage(), cmd.getScriptContent(), cmd.getTimeoutMillis(), cmd.getEnv(), cmd.getStatus());
        return IntegrationAssembler.toDTO(runtimeScriptRepo.save(script));
    }

    @Transactional
    public void disableScript(Long id) {
        ensureIntegrationEnabled();
        RuntimeScript script = script(id);
        script.disable();
        runtimeScriptRepo.save(script);
    }

    @Transactional
    public RuntimeExecutionLogDTO execute(RuntimeExecuteCmd cmd) {
        ensureIntegrationEnabled();
        RuntimeScript script = script(cmd.getScriptId());
        if (script.getStatus() != ConnectorStatus.ACTIVE) {
            throw new BizException("运行脚本已停用");
        }
        RuntimeExecutionResult result = runtimeExecutor.execute(script, cmd.getStdin());
        RuntimeExecutionLog log = RuntimeExecutionLog.builder()
                .scriptId(script.getId())
                .scriptCode(script.getCode())
                .language(script.getLanguage())
                .stdin(cmd.getStdin())
                .stdout(result.stdout())
                .stderr(result.stderr())
                .exitCode(result.exitCode())
                .durationMillis(result.durationMillis())
                .status(result.status())
                .errorMessage(result.errorMessage())
                .executedAt(LocalDateTime.now())
                .build();
        return IntegrationAssembler.toDTO(runtimeExecutionLogRepo.save(log));
    }

    @Transactional(readOnly = true)
    public PageResult<RuntimeExecutionLogDTO> pageExecutionLogs(IntegrationPageQuery query) {
        ensureIntegrationEnabled();
        PageResult<RuntimeExecutionLog> page = runtimeExecutionLogRepo.page(query.getKeyword(), query.getPage(), query.getSize());
        return new PageResult<>(page.getRecords().stream().map(IntegrationAssembler::toDTO).toList(), page.getTotal(), page.getPage(), page.getSize());
    }

    private HttpConnector createConnector(HttpConnectorSaveCmd cmd) {
        if (httpConnectorRepo.findByCode(cmd.getCode()).isPresent()) {
            throw new BizException("连接器编码已存在");
        }
        return HttpConnector.create(cmd.getName(), cmd.getCode(), cmd.getUrl(), cmd.getMethod());
    }

    private RuntimeScript createScript(RuntimeScriptSaveCmd cmd) {
        if (runtimeScriptRepo.findByCode(cmd.getCode()).isPresent()) {
            throw new BizException("脚本编码已存在");
        }
        return RuntimeScript.create(cmd.getName(), cmd.getCode(), cmd.getLanguage(), cmd.getScriptContent());
    }

    private HttpConnector connector(Long id) {
        return httpConnectorRepo.findById(id).orElseThrow(() -> new BizException("HTTP 连接器不存在"));
    }

    private RuntimeScript script(Long id) {
        return runtimeScriptRepo.findById(id).orElseThrow(() -> new BizException("运行脚本不存在"));
    }

    private void ensureIntegrationEnabled() {
        boolean enabled = capabilityModuleRepo.findByCode(INTEGRATION_CAPABILITY_CODE)
                .map(module -> Boolean.TRUE.equals(module.getEnabled()))
                .orElse(false);
        if (!enabled) {
            throw new BizException("集成调用能力未启用");
        }
    }

    private Map<String, String> merge(Map<String, String> defaults, Map<String, String> overrides) {
        Map<String, String> result = new HashMap<>(defaults == null ? Map.of() : defaults);
        if (overrides != null) {
            result.putAll(overrides);
        }
        return result;
    }

    private Map<String, String> redact(Map<String, String> values) {
        Map<String, String> result = new HashMap<>();
        if (values == null) {
            return result;
        }
        values.forEach((key, value) -> result.put(key, shouldRedact(key) ? "******" : value));
        return result;
    }

    private boolean shouldRedact(String key) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.contains("token") || lower.contains("secret") || lower.contains("key") || lower.contains("password") || lower.contains("authorization");
    }
}

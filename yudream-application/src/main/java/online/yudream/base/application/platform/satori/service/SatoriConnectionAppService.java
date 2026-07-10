package online.yudream.base.application.platform.satori.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.satori.assembler.SatoriConnectionAssembler;
import online.yudream.base.application.platform.satori.cmd.SatoriConnectionCreateCmd;
import online.yudream.base.application.platform.satori.cmd.SatoriConnectionPageQuery;
import online.yudream.base.application.platform.satori.cmd.SatoriConnectionUpdateCmd;
import online.yudream.base.application.platform.satori.dto.SatoriConnectionDTO;
import online.yudream.base.application.platform.satori.dto.SatoriConnectionTestDTO;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.satori.aggregate.SatoriConnection;
import online.yudream.base.domain.platform.satori.model.SatoriApiModels.SatoriApiContext;
import online.yudream.base.domain.platform.satori.repo.SatoriConnectionRepo;
import online.yudream.base.domain.platform.satori.service.SatoriApiGateway;
import online.yudream.base.domain.platform.satori.service.SatoriInternalGateway;
import online.yudream.base.domain.platform.satori.model.SatoriApiModels.InternalRequest;
import online.yudream.base.application.platform.satori.cmd.SatoriInternalInvokeCmd;
import online.yudream.base.domain.platform.satori.service.SatoriEventGateway;
import online.yudream.base.domain.platform.satori.service.SatoriOperationLogger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.satori", name = "enabled", havingValue = "true")
public class SatoriConnectionAppService {
    public static final String CAPABILITY_CODE = "satori";
    private static final String CAPABILITY_NAME = "Satori 平台";

    private final SatoriConnectionRepo connectionRepo;
    private final SatoriApiGateway apiGateway;
    private final SatoriInternalGateway internalGateway;
    private final SatoriEventGateway eventGateway;
    private final CapabilityAppService capabilityAppService;
    private final SatoriOperationLogger operationLogger;

    @Transactional
    public SatoriConnectionDTO create(SatoriConnectionCreateCmd cmd) {
        ensureEnabled();
        SatoriConnection saved = connectionRepo.save(SatoriConnection.create(cmd.getName(), cmd.getBaseUrl(),
                cmd.getPlatform(), cmd.getUserId(), cmd.getToken()));
        operationLogger.info(saved.getId(), "CONNECTION", "created", "连接已创建");
        return SatoriConnectionAssembler.toDTO(saved);
    }

    @Transactional
    public SatoriConnectionDTO update(SatoriConnectionUpdateCmd cmd) {
        ensureEnabled();
        SatoriConnection connection = connection(cmd.getId());
        connection.update(cmd.getName(), cmd.getBaseUrl(), cmd.getPlatform(), cmd.getUserId(), cmd.getToken());
        SatoriConnection saved = connectionRepo.save(connection);
        operationLogger.info(saved.getId(), "CONNECTION", "updated", "连接配置已更新");
        return SatoriConnectionAssembler.toDTO(saved);
    }

    @Transactional(readOnly = true)
    public PageResult<SatoriConnectionDTO> page(SatoriConnectionPageQuery query) {
        ensureEnabled();
        int size = Math.min(Math.max(query.getSize(), 1), 100);
        return SatoriConnectionAssembler.toDTO(connectionRepo.page(query.getKeyword(), Math.max(query.getPage(), 1), size));
    }

    @Transactional
    public SatoriConnectionDTO enable(Long id) {
        ensureEnabled();
        SatoriConnection connection = connection(id);
        connection.enable();
        SatoriConnection saved = connectionRepo.save(connection);
        eventGateway.connect(saved.getId());
        operationLogger.info(saved.getId(), "CONNECTION", "enabled", "连接已启用，正在建立 WebSocket 会话");
        return SatoriConnectionAssembler.toDTO(saved);
    }

    @Transactional
    public SatoriConnectionDTO disable(Long id) {
        ensureEnabled();
        SatoriConnection connection = connection(id);
        connection.disable();
        SatoriConnection saved = connectionRepo.save(connection);
        eventGateway.close(saved.getId());
        operationLogger.info(saved.getId(), "CONNECTION", "disabled", "连接已停用，WebSocket 会话已关闭");
        return SatoriConnectionAssembler.toDTO(saved);
    }

    @Transactional(readOnly = true)
    public SatoriConnectionTestDTO test(Long id) {
        ensureEnabled();
        SatoriConnection connection = connection(id);
        if (!connection.enabled()) {
            throw new BizException("Satori 连接未启用");
        }
        try {
            var result = SatoriConnectionAssembler.toTestDTO(connection, apiGateway.loginGet(contextOf(connection)));
            operationLogger.info(connection.getId(), "HTTP", "login.get", "连接测试成功");
            return result;
        } catch (RuntimeException exception) {
            operationLogger.error(connection.getId(), "HTTP", "login.get", exception.getMessage());
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public Object invokeInternal(SatoriInternalInvokeCmd cmd) {
        ensureEnabled();
        SatoriConnection connection = connection(cmd.getConnectionId());
        if (!connection.enabled()) {
            throw new BizException("Satori 连接未启用");
        }
        if (cmd.getMethod() == null || cmd.getMethod().isBlank()) {
            throw new BizException("Satori 原生方法不能为空");
        }
        String platform = cmd.getPlatform() == null || cmd.getPlatform().isBlank() ? connection.getPlatform() : cmd.getPlatform();
        String userId = cmd.getUserId() == null || cmd.getUserId().isBlank() ? connection.getUserId() : cmd.getUserId();
        return internalGateway.invoke(new SatoriApiContext(connection.getBaseUrl(), connection.getToken(), platform, userId),
                new InternalRequest(cmd.getMethod(), cmd.getPayload()));
    }

    private SatoriConnection connection(Long id) {
        if (id == null) {
            throw new BizException("Satori 连接 ID 不能为空");
        }
        return connectionRepo.findById(id).orElseThrow(() -> new BizException("Satori 连接不存在"));
    }

    private SatoriApiContext contextOf(SatoriConnection connection) {
        return new SatoriApiContext(connection.getBaseUrl(), connection.getToken(), connection.getPlatform(), connection.getUserId());
    }

    private void ensureEnabled() {
        capabilityAppService.ensureEnabled(CAPABILITY_CODE, CAPABILITY_NAME);
    }
}

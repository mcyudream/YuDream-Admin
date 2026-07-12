package online.yudream.base.application.platform.milky.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.milky.assembler.MilkyConnectionAssembler;
import online.yudream.base.application.platform.milky.cmd.MilkyConnectionCreateCmd;
import online.yudream.base.application.platform.milky.cmd.MilkyConnectionUpdateCmd;
import online.yudream.base.application.platform.milky.dto.MilkyConnectionDTO;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.milky.aggregate.MilkyConnection;
import online.yudream.base.domain.platform.milky.model.MilkyModels;
import online.yudream.base.domain.platform.milky.repo.MilkyConnectionRepo;
import online.yudream.base.domain.platform.milky.service.MilkyApiGateway;
import online.yudream.base.domain.platform.milky.service.MilkyEventGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class MilkyConnectionAppService {
    private final MilkyConnectionRepo connectionRepo;
    private final CapabilityAppService capabilityAppService;
    private final MilkyEventGateway eventGateway;
    private final MilkyApiGateway apiGateway;

    @Transactional
    public MilkyConnectionDTO create(MilkyConnectionCreateCmd cmd) {
        ready();
        return MilkyConnectionAssembler.toDTO(connectionRepo.save(MilkyConnection.create(cmd.getName(), cmd.getBaseUrl(), cmd.getToken(), cmd.getCommandMenuImageMode(), cmd.getCommandMenuPublicBaseUrl())));
    }

    @Transactional
    public MilkyConnectionDTO update(MilkyConnectionUpdateCmd cmd) {
        ready();
        MilkyConnection connection = item(cmd.getId());
        connection.update(cmd.getName(), cmd.getBaseUrl(), cmd.getToken(), cmd.getCommandMenuImageMode(), cmd.getCommandMenuPublicBaseUrl());
        return MilkyConnectionAssembler.toDTO(connectionRepo.save(connection));
    }

    @Transactional(readOnly = true)
    public PageResult<MilkyConnectionDTO> page(String keyword, int page, int size) {
        ready();
        return MilkyConnectionAssembler.toDTO(connectionRepo.page(keyword, page, size));
    }

    @Transactional
    public MilkyConnectionDTO enable(Long id) {
        ready();
        MilkyConnection connection = item(id);
        connection.setEnabled(true);
        MilkyConnection saved = connectionRepo.save(connection);
        eventGateway.connect(saved.getId());
        return MilkyConnectionAssembler.toDTO(saved);
    }

    @Transactional
    public MilkyConnectionDTO disable(Long id) {
        ready();
        MilkyConnection connection = item(id);
        connection.setEnabled(false);
        MilkyConnection saved = connectionRepo.save(connection);
        eventGateway.close(saved.getId());
        return MilkyConnectionAssembler.toDTO(saved);
    }

    @Transactional(readOnly = true)
    public Object test(Long id) {
        ready();
        MilkyConnection connection = item(id);
        return apiGateway.invoke(new MilkyModels.Context(connection.getBaseUrl(), connection.getToken(), null), "get_login_info", Map.of());
    }

    private MilkyConnection item(Long id) {
        return connectionRepo.findById(id).orElseThrow(() -> new BizException("Milky 连接不存在"));
    }

    private void ready() {
        capabilityAppService.ensureEnabled("milky", "Milky 消息平台");
    }
}

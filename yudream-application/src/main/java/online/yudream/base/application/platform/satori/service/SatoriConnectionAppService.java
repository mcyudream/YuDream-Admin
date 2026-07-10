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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SatoriConnectionAppService {
    public static final String CAPABILITY_CODE = "satori";
    private static final String CAPABILITY_NAME = "Satori 平台";

    private final SatoriConnectionRepo connectionRepo;
    private final SatoriApiGateway apiGateway;
    private final CapabilityAppService capabilityAppService;

    @Transactional
    public SatoriConnectionDTO create(SatoriConnectionCreateCmd cmd) {
        ensureEnabled();
        return SatoriConnectionAssembler.toDTO(connectionRepo.save(SatoriConnection.create(cmd.getName(), cmd.getBaseUrl(), cmd.getToken())));
    }

    @Transactional
    public SatoriConnectionDTO update(SatoriConnectionUpdateCmd cmd) {
        ensureEnabled();
        SatoriConnection connection = connection(cmd.getId());
        connection.update(cmd.getName(), cmd.getBaseUrl(), cmd.getToken());
        return SatoriConnectionAssembler.toDTO(connectionRepo.save(connection));
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
        return SatoriConnectionAssembler.toDTO(connectionRepo.save(connection));
    }

    @Transactional
    public SatoriConnectionDTO disable(Long id) {
        ensureEnabled();
        SatoriConnection connection = connection(id);
        connection.disable();
        return SatoriConnectionAssembler.toDTO(connectionRepo.save(connection));
    }

    @Transactional(readOnly = true)
    public SatoriConnectionTestDTO test(Long id) {
        ensureEnabled();
        SatoriConnection connection = connection(id);
        if (!connection.enabled()) {
            throw new BizException("Satori 连接未启用");
        }
        return SatoriConnectionAssembler.toTestDTO(apiGateway.meta(contextOf(connection)));
    }

    private SatoriConnection connection(Long id) {
        if (id == null) {
            throw new BizException("Satori 连接 ID 不能为空");
        }
        return connectionRepo.findById(id).orElseThrow(() -> new BizException("Satori 连接不存在"));
    }

    private SatoriApiContext contextOf(SatoriConnection connection) {
        return new SatoriApiContext(connection.getBaseUrl(), connection.getToken(), null, null);
    }

    private void ensureEnabled() {
        capabilityAppService.ensureEnabled(CAPABILITY_CODE, CAPABILITY_NAME);
    }
}

package online.yudream.base.application.platform.satori.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.satori.assembler.SatoriConnectionAssembler;
import online.yudream.base.application.platform.satori.dto.SatoriOperationLogDTO;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.satori.repo.SatoriConnectionRepo;
import online.yudream.base.domain.platform.satori.repo.SatoriOperationLogRepo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.satori", name = "enabled", havingValue = "true")
public class SatoriOperationLogAppService {
    private final CapabilityAppService capabilityAppService;
    private final SatoriConnectionRepo connectionRepo;
    private final SatoriOperationLogRepo logRepo;

    @Transactional(readOnly = true)
    public PageResult<SatoriOperationLogDTO> page(Long connectionId, int page, int size) {
        capabilityAppService.ensureEnabled(SatoriConnectionAppService.CAPABILITY_CODE, "Satori 平台");
        if (connectionId == null || connectionRepo.findById(connectionId).isEmpty()) throw new BizException("Satori 连接不存在");
        return SatoriConnectionAssembler.toLogDTO(logRepo.page(connectionId, Math.max(page, 1), Math.min(Math.max(size, 1), 100)));
    }
}

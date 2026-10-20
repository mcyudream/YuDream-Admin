package online.yudream.base.application.platform.cms.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.cms.assembler.StudioBlockTemplateAssembler;
import online.yudream.base.application.platform.cms.cmd.StudioBlockTemplateSaveCmd;
import online.yudream.base.application.platform.cms.dto.StudioBlockTemplateDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.cms.aggregate.StudioBlockTemplate;
import online.yudream.base.domain.platform.cms.repo.StudioBlockTemplateRepo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * 用户区块模板：站点内共享，插入器“模板”与区块搜索共用。内容按站点文档同一套规则校验，
 * 插入时由前端重建全部节点 ID，模板与已插入内容之间没有联动。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.cms", name = "enabled", havingValue = "true")
public class StudioBlockTemplateAppService {
    static final int MAX_TEMPLATES = 500;

    private final CapabilityModuleRepo capabilityModuleRepo;
    private final StudioBlockTemplateRepo repo;
    private final StudioDocumentValidator validator;

    public List<StudioBlockTemplateDTO> list() {
        ensureEnabled();
        return repo.findAll().stream()
                .sorted(Comparator.comparing(StudioBlockTemplate::updatedAt).reversed())
                .map(StudioBlockTemplateAssembler::toDTO)
                .toList();
    }

    public StudioBlockTemplateDTO create(StudioBlockTemplateSaveCmd cmd, Long userId) {
        ensureEnabled();
        if (repo.count() >= MAX_TEMPLATES) throw new BizException("区块模板最多 " + MAX_TEMPLATES + " 个，请先清理不再使用的模板");
        validator.validateNodes(cmd.nodesJson());
        StudioBlockTemplate template = StudioBlockTemplate.create(cmd.name(), cmd.category(), cmd.description(), cmd.keywords(),
                cmd.nodesJson(), userId, Instant.now());
        repo.insert(template);
        return StudioBlockTemplateAssembler.toDTO(template);
    }

    public StudioBlockTemplateDTO update(String id, StudioBlockTemplateSaveCmd cmd) {
        ensureEnabled();
        StudioBlockTemplate current = repo.findById(id).orElseThrow(() -> new BizException(404, "区块模板不存在"));
        long expected = parseRevision(cmd.expectedRevision());
        if (cmd.nodesJson() != null) validator.validateNodes(cmd.nodesJson());
        StudioBlockTemplate next = current.update(expected, cmd.name(), cmd.category(), cmd.description(), cmd.keywords(),
                cmd.nodesJson(), Instant.now());
        if (!repo.compareAndSet(expected, next)) throw new BizException(409, "区块模板已被他人修改，请刷新后重试");
        return StudioBlockTemplateAssembler.toDTO(next);
    }

    public void delete(String id) {
        ensureEnabled();
        if (!repo.delete(id)) throw new BizException(404, "区块模板不存在");
    }

    private static long parseRevision(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) throw new BizException("缺少有效的区块模板版本");
        return Long.parseLong(value);
    }

    private void ensureEnabled() {
        if (!capabilityModuleRepo.findByCode("cms").map(module -> Boolean.TRUE.equals(module.getEnabled())).orElse(false)) {
            throw new BizException("内容定制能力未启用");
        }
    }
}

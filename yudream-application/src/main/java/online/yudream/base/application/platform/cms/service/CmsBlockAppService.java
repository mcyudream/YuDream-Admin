package online.yudream.base.application.platform.cms.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.cms.assembler.CmsAssembler;
import online.yudream.base.application.platform.cms.cmd.CmsBlockSaveCmd;
import online.yudream.base.application.platform.cms.dto.CmsBlockDTO;
import online.yudream.base.application.platform.cms.query.CmsBlockQuery;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.cms.aggregate.CmsBlock;
import online.yudream.base.domain.platform.cms.enumerate.CmsBlockKind;
import online.yudream.base.domain.platform.cms.repo.CmsBlockRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CmsBlockAppService {

    private static final String CAPABILITY_CODE = "cms";

    private final CapabilityModuleRepo capabilityModuleRepo;
    private final CmsBlockRepo cmsBlockRepo;

    @Transactional(readOnly = true)
    public PageResult<CmsBlockDTO> page(CmsBlockQuery query) {
        ensureEnabled();
        PageResult<CmsBlock> result = cmsBlockRepo.page(query.getKeyword(), query.getCategory(), query.getKind(), query.getPage(), query.getSize());
        return new PageResult<>(result.getRecords().stream().map(CmsAssembler::toDTO).toList(), result.getTotal(), result.getPage(), result.getSize());
    }

    @Transactional(readOnly = true)
    public CmsBlockDTO getById(Long id) {
        ensureEnabled();
        return CmsAssembler.toDTO(block(id));
    }

    @Transactional
    public CmsBlockDTO create(CmsBlockSaveCmd cmd) {
        ensureEnabled();
        CmsBlock block = createBlock(cmd);
        applyUpdate(block, cmd);
        return CmsAssembler.toDTO(cmsBlockRepo.save(block));
    }

    @Transactional
    public CmsBlockDTO update(Long id, CmsBlockSaveCmd cmd) {
        ensureEnabled();
        CmsBlock block = block(id);
        String normalizedCode = normalizeCode(cmd.getCode());
        ensureCodeAvailable(normalizedCode, block.getId());
        block.setCode(normalizedCode);
        if (cmd.getKind() != null) {
            block.setKind(cmd.getKind());
        }
        applyUpdate(block, cmd);
        return CmsAssembler.toDTO(cmsBlockRepo.save(block));
    }

    @Transactional
    public void delete(Long id) {
        ensureEnabled();
        block(id);
        cmsBlockRepo.deleteById(id);
    }

    @Transactional
    public void enable(Long id) {
        ensureEnabled();
        CmsBlock block = block(id);
        block.enable();
        cmsBlockRepo.save(block);
    }

    @Transactional
    public void disable(Long id) {
        ensureEnabled();
        CmsBlock block = block(id);
        block.disable();
        cmsBlockRepo.save(block);
    }

    @Transactional(readOnly = true)
    public List<String> listCategories() {
        ensureEnabled();
        return cmsBlockRepo.findAllEnabled().stream()
                .map(CmsBlock::getCategory)
                .filter(category -> category != null && !category.trim().isEmpty())
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();
    }

    private CmsBlock createBlock(CmsBlockSaveCmd cmd) {
        String normalizedCode = normalizeCode(cmd.getCode());
        ensureCodeAvailable(normalizedCode, null);
        return CmsBlock.create(normalizedCode, cmd.getName(), cmd.getKind() == null ? CmsBlockKind.ATOMIC : cmd.getKind());
    }

    private void applyUpdate(CmsBlock block, CmsBlockSaveCmd cmd) {
        block.update(cmd.getName(), cmd.getDescription(), cmd.getCategory(), cmd.getIcon(),
                cmd.getPreviewImageUrl(), cmd.getHtmlContent(), cmd.getCssContent(), cmd.getJsContent(),
                cmd.getBuilderProjectJson(), cmd.getTags(), cmd.getEnabled(), cmd.getSort());
    }

    private CmsBlock block(Long id) {
        return cmsBlockRepo.findById(id).orElseThrow(() -> new BizException("区块不存在"));
    }

    private void ensureEnabled() {
        boolean enabled = capabilityModuleRepo.findByCode(CAPABILITY_CODE)
                .map(module -> Boolean.TRUE.equals(module.getEnabled()))
                .orElse(false);
        if (!enabled) {
            throw new BizException("内容定制能力未启用");
        }
    }

    private String normalizeCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new BizException("区块编码不能为空");
        }
        return code.trim().toLowerCase();
    }

    private void ensureCodeAvailable(String code, Long currentBlockId) {
        cmsBlockRepo.findByCode(code).ifPresent(existing -> {
            if (currentBlockId == null || !currentBlockId.equals(existing.getId())) {
                throw new BizException("区块编码已存在");
            }
        });
    }
}

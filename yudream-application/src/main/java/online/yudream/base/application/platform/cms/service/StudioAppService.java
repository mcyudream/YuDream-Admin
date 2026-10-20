package online.yudream.base.application.platform.cms.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.cms.assembler.StudioAssembler;
import online.yudream.base.application.platform.cms.cmd.StudioDraftSaveCmd;
import online.yudream.base.application.platform.cms.cmd.StudioRevisionCmd;
import online.yudream.base.application.platform.cms.dto.*;
import online.yudream.base.application.platform.theme.service.SiteThemeQueryService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.cms.aggregate.StudioWorkspace;
import online.yudream.base.domain.platform.cms.repo.StudioWorkspaceRepo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.cms", name = "enabled", havingValue = "true")
public class StudioAppService {
    private final CapabilityModuleRepo capabilityModuleRepo;
    private final StudioWorkspaceRepo workspaceRepo;
    private final SiteThemeQueryService siteThemeQueryService;
    private final StudioDocumentValidator validator;

    public StudioStateDTO state(String themeCode) {
        ensureEnabled();
        return StudioAssembler.toDTO(workspace(themeCode));
    }
    public StudioStateDTO saveDraft(String themeCode, StudioDraftSaveCmd cmd) {
        ensureEnabled();
        StudioWorkspace current = workspace(themeCode);
        current.checkRevision(cmd.expectedRevision());
        validator.validate(cmd.documentJson());
        return persist(current, current.saveDraft(cmd.expectedRevision(), cmd.documentJson(), Instant.now()));
    }
    public StudioStateDTO publish(String themeCode, StudioRevisionCmd cmd) {
        ensureEnabled();
        StudioWorkspace current = workspace(themeCode);
        current.checkRevision(cmd.expectedRevision());
        validator.validate(current.documentJson());
        return persist(current, current.publish(cmd.expectedRevision(), UUID.randomUUID().toString(), Instant.now()));
    }
    public StudioStateDTO unpublish(String themeCode, StudioRevisionCmd cmd) {
        ensureEnabled();
        StudioWorkspace current = workspace(themeCode);
        return persist(current, current.unpublish(cmd.expectedRevision(), Instant.now()));
    }
    public StudioStateDTO restore(String themeCode, String releaseId, StudioRevisionCmd cmd) {
        ensureEnabled();
        StudioWorkspace current = workspace(themeCode);
        StudioWorkspace restored = current.restore(cmd.expectedRevision(), releaseId, Instant.now());
        validator.validate(restored.documentJson());
        return persist(current, restored);
    }
    public PageResult<StudioReleaseDTO> releases(String themeCode, int page, int size) {
        ensureEnabled();
        if (page < 1 || size < 1 || size > 100) throw new BizException("分页参数不合法，size范围为1至100");
        var releases = workspace(themeCode).releases();
        long offset = ((long) page - 1) * size;
        var list = releases.stream().skip(offset).limit(size).map(StudioAssembler::toDTO).toList();
        return new PageResult<>(list, releases.size(), page, size);
    }
    public StudioPublicDocumentDTO publicDocument() {
        ensureEnabled();
        String themeCode = siteThemeQueryService.activeSiteThemeCode();
        return StudioAssembler.toPublicDTO(workspace(themeCode));
    }
    private StudioWorkspace workspace(String themeCode) {
        StudioWorkspace empty = StudioWorkspace.empty(themeCode);
        return workspaceRepo.findByThemeCode(themeCode).orElse(empty);
    }
    private StudioStateDTO persist(StudioWorkspace current, StudioWorkspace next) {
        if (!workspaceRepo.compareAndSet(current.revision(), next)) {
            throw new BizException(409, "站点草稿版本冲突，请刷新后重新确认修改");
        }
        return StudioAssembler.toDTO(next);
    }
    private void ensureEnabled() {
        if (!capabilityModuleRepo.findByCode("cms").map(module -> Boolean.TRUE.equals(module.getEnabled())).orElse(false)) {
            throw new BizException("内容定制能力未启用");
        }
    }
}

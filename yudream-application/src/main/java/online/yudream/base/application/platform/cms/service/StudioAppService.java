package online.yudream.base.application.platform.cms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.cms", name = "enabled", havingValue = "true")
public class StudioAppService {
    private final CapabilityModuleRepo capabilityModuleRepo;
    private final StudioWorkspaceRepo workspaceRepo;
    private final SiteThemeQueryService siteThemeQueryService;
    private final StudioDocumentValidator validator;
    private final ObjectMapper objectMapper;

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
    /**
     * 主题插件自带站点（首页方案 JSON 的 studioPackage，格式同工作台便携包 yudream-site-package v1）：
     * 该主题尚无站点工作区时写入草稿并发布；已有工作区（管理员已编辑或此前已导入）时不覆盖。
     * 返回是否导入。能力未启用、包不合法时抛出，由调用方记录且不阻塞主题激活。
     */
    public boolean seedThemePackage(String themeCode, JsonNode sitePackage) {
        ensureEnabled();
        if (workspaceRepo.findByThemeCode(themeCode).isPresent()) {
            log.info("主题已有站点工作区，保留管理员内容不导入自带站点：theme={}", themeCode);
            return false;
        }
        if (sitePackage == null || !sitePackage.isObject() || !"yudream-site-package".equals(sitePackage.path("type").asText())
                || sitePackage.path("version").asInt() != 1 || !"site".equals(sitePackage.path("kind").asText()) || !sitePackage.path("document").isObject()) {
            throw new BizException("主题自带站点须为 yudream-site-package v1 的 site 包");
        }
        String documentJson;
        try {
            documentJson = objectMapper.writeValueAsString(sitePackage.get("document"));
        } catch (Exception ex) {
            throw new BizException("主题自带站点无法序列化");
        }
        validator.validate(documentJson);
        StudioWorkspace empty = StudioWorkspace.empty(themeCode);
        Instant now = Instant.now();
        StudioWorkspace drafted = empty.saveDraft("0", documentJson, now);
        StudioWorkspace published = drafted.publish(Long.toString(drafted.revision()), UUID.randomUUID().toString(), now);
        persist(empty, published);
        log.info("已导入并发布主题自带站点：theme={}", themeCode);
        return true;
    }
    /** 框架内置主题编码与其自带的常规站点（浅色、系统字体、内容模型可编辑），主题个性化由主题插件提供 */
    public static final String BUILTIN_THEME_CODE = "default";
    private static final String BUILTIN_SITE_PACKAGE = "cms/default-site-package.json";

    /** 内置主题尚无站点工作区时导入并发布框架自带的常规站点；已有工作区不覆盖。返回是否导入。 */
    public boolean seedBuiltinSite() {
        if (workspaceRepo.findByThemeCode(BUILTIN_THEME_CODE).isPresent()) {
            return false;
        }
        try (var input = getClass().getClassLoader().getResourceAsStream(BUILTIN_SITE_PACKAGE)) {
            if (input == null) {
                throw new BizException("框架内置站点资源缺失");
            }
            return seedThemePackage(BUILTIN_THEME_CODE, objectMapper.readTree(input));
        } catch (java.io.IOException ex) {
            throw new BizException("框架内置站点读取失败");
        }
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

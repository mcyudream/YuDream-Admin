package online.yudream.base.application.platform.wiki.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.wiki.assembler.WikiAssembler;
import online.yudream.base.application.platform.wiki.cmd.WikiSpaceCoverCmd;
import online.yudream.base.application.platform.wiki.dto.WikiPublicSpaceProfileDTO;
import online.yudream.base.application.platform.wiki.dto.WikiSpaceDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceAclAction;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceCoverType;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import online.yudream.base.domain.platform.wiki.valobj.WikiActor;
import online.yudream.base.domain.platform.wiki.valobj.WikiCover;
import online.yudream.base.domain.system.file.aggregate.FileObject;
import online.yudream.base.domain.system.file.repo.FileObjectRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * 空间封面用例：随机（稳定 seed 确定性呈现）、上传引用与清除。
 * <p>
 * 上传封面必须是当前用户归属、未删除、公开可读的图片文件对象；
 * 移除封面只清空引用，不删除可能被其他内容共享的文件。
 */
@Service
@RequiredArgsConstructor
public class WikiSpaceCoverAppService {

    /** 封面图片大小上限（5MB）。 */
    private static final long MAX_COVER_SIZE = 5L * 1024 * 1024;
    /** 公开端封面访问地址：显式公开端点，非公开文件对匿名请求 fail closed。 */
    private static final String PUBLIC_CONTENT_PREFIX = "/api/files/public/";
    private static final String PUBLIC_CONTENT_SUFFIX = "/content";

    private final WikiSpaceRepo spaceRepo;
    private final FileObjectRepo fileObjectRepo;
    private final WikiSpaceAclAppService accessAppService;

    /** 更新封面：SEEDED 需稳定 seed，UPLOAD 需归属当前用户且公开可读的图片，NONE 清除。 */
    @Transactional
    public WikiSpaceDTO update(WikiSpaceCoverCmd cmd) {
        WikiAccessGrant grant = accessAppService.requireSpace(cmd.getSpaceId(), WikiSpaceAclAction.MANAGE,
                WikiAccessPermissions.COVER_EDIT);
        WikiSpaceCoverType type = WikiSpaceCoverType.of(cmd.getCoverType());
        WikiCover cover = switch (type) {
            case NONE -> WikiCover.NONE;
            case SEEDED -> WikiCover.seeded(cmd.getCoverSeed(), cmd.getCoverAlt());
            case UPLOAD -> {
                FileObject file = requireOwnedPublicImage(cmd.getCoverFileObjectId(), grant.actor());
                yield WikiCover.upload(file.getId(), cmd.getCoverAlt());
            }
        };
        WikiSpace space = grant.space();
        space.applyCover(cover);
        return WikiAssembler.space(spaceRepo.save(space));
    }

    /** 随机封面：seed 由服务端生成，客户端只能提交替代文本。 */
    @Transactional
    public WikiSpaceDTO random(Long spaceId, String coverAlt) {
        WikiSpace space = accessAppService.requireSpace(spaceId, WikiSpaceAclAction.MANAGE,
                WikiAccessPermissions.COVER_EDIT).space();
        space.applyCover(WikiCover.random(coverAlt));
        return WikiAssembler.space(spaceRepo.save(space));
    }

    /** 移除封面：只清空引用，不删除文件对象。 */
    @Transactional
    public WikiSpaceDTO clear(Long spaceId) {
        WikiSpace space = accessAppService.requireSpace(spaceId, WikiSpaceAclAction.MANAGE,
                WikiAccessPermissions.COVER_DELETE).space();
        space.clearCover();
        return WikiAssembler.space(spaceRepo.save(space));
    }

    /** 公开门户：只暴露公开空间的安全封面字段。 */
    @Transactional(readOnly = true)
    public List<WikiPublicSpaceProfileDTO> profiles() {
        accessAppService.ensureEnabled();
        return spaceRepo.findAll().stream()
                .filter(WikiSpace::isPublicReadEnabled)
                .map(this::profile)
                .toList();
    }

    /** 空间资料：公开空间才返回，未公开一律拒绝。 */
    @Transactional(readOnly = true)
    public WikiPublicSpaceProfileDTO profile(String spaceSlug) {
        accessAppService.ensureEnabled();
        WikiSpace space = spaceRepo.findBySlug(spaceSlug).orElseThrow(() -> new BizException("知识库不存在"));
        if (!space.isPublicReadEnabled()) {
            throw new BizException("该知识库未开放公开阅读");
        }
        return profile(space);
    }

    private WikiPublicSpaceProfileDTO profile(WikiSpace space) {
        WikiCover cover = space.cover();
        String coverUrl = cover.type() == WikiSpaceCoverType.UPLOAD
                ? PUBLIC_CONTENT_PREFIX + cover.fileObjectId() + PUBLIC_CONTENT_SUFFIX : null;
        return new WikiPublicSpaceProfileDTO(space.getName(), space.getSlug(), space.getDescription(),
                cover.type().name(), cover.seed(), coverUrl, cover.alt(), space.isCommentEnabled());
    }

    /** 上传封面校验：存在、未删除、图片 MIME、大小受限、归属当前用户、公开可读。 */
    private FileObject requireOwnedPublicImage(Long fileObjectId, WikiActor actor) {
        if (fileObjectId == null) {
            throw new BizException("上传封面必须提供文件对象");
        }
        FileObject file = fileObjectRepo.findById(fileObjectId).orElseThrow(() -> new BizException("封面文件不存在"));
        if (file.isDeleted()) {
            throw new BizException("封面文件已删除");
        }
        if (!StringUtils.hasText(file.getContentType())
                || !file.getContentType().toLowerCase().startsWith("image/")) {
            throw new BizException("封面只支持图片文件");
        }
        if (file.getSize() == null || file.getSize() <= 0 || file.getSize() > MAX_COVER_SIZE) {
            throw new BizException("封面图片不能超过 5MB");
        }
        if (!Objects.equals(file.getUploaderId(), actor.userId())) {
            throw new BizException("只能使用本人上传的封面图片");
        }
        if (!Boolean.TRUE.equals(file.getPublicAccess())) {
            throw new BizException("封面图片必须是公开可读的文件，避免私有图片经封面泄露");
        }
        return file;
    }

}
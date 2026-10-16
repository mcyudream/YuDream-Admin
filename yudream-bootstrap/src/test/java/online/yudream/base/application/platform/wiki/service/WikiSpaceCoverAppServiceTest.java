package online.yudream.base.application.platform.wiki.service;

import online.yudream.base.application.platform.wiki.cmd.WikiSpaceCoverCmd;
import online.yudream.base.application.platform.wiki.dto.WikiPublicSpaceProfileDTO;
import online.yudream.base.application.platform.wiki.dto.WikiSpaceDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceCoverType;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import online.yudream.base.domain.platform.wiki.valobj.WikiActor;
import online.yudream.base.domain.system.file.aggregate.FileObject;
import online.yudream.base.domain.system.file.repo.FileObjectRepo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 封面用例：上传文件归属/公开性/类型/大小校验、随机 seed 稳定落库、清除不删文件、公开资料只暴露安全字段。
 */
class WikiSpaceCoverAppServiceTest {

    private static final WikiActor OPERATOR = WikiActor.of(100L, 7L, "操作者", false);

    private final WikiSpaceRepo spaces = mock(WikiSpaceRepo.class);
    private final FileObjectRepo files = mock(FileObjectRepo.class);
    private final WikiSpaceAclAppService access = mock(WikiSpaceAclAppService.class);
    private final WikiSpaceCoverAppService service = new WikiSpaceCoverAppService(spaces, files, access);

    @Test
    void uploadCoverRejectsNonImageAndOversizedAndForeignAndPrivateFile() {
        WikiSpace space = space(10L);
        when(spaces.findById(10L)).thenReturn(Optional.of(space));
        when(access.requireSpace(any(), any(), any())).thenReturn(new WikiAccessGrant(space, OPERATOR));

        when(files.findById(5L)).thenReturn(Optional.of(file(5L, 100L, "application/pdf", 1024L, true)));
        assertThatThrownBy(() -> service.update(upload(10L, 5L))).isInstanceOf(BizException.class)
                .hasMessageContaining("封面只支持图片文件");

        when(files.findById(5L)).thenReturn(Optional.of(file(5L, 100L, "image/png", 6L * 1024 * 1024, true)));
        assertThatThrownBy(() -> service.update(upload(10L, 5L))).isInstanceOf(BizException.class)
                .hasMessageContaining("不能超过 5MB");

        when(files.findById(5L)).thenReturn(Optional.of(file(5L, 200L, "image/png", 1024L, true)));
        assertThatThrownBy(() -> service.update(upload(10L, 5L))).isInstanceOf(BizException.class)
                .hasMessageContaining("本人上传");

        when(files.findById(5L)).thenReturn(Optional.of(file(5L, 100L, "image/png", 1024L, false)));
        assertThatThrownBy(() -> service.update(upload(10L, 5L))).isInstanceOf(BizException.class)
                .hasMessageContaining("公开可读");

        when(files.findById(5L)).thenReturn(Optional.of(deletedFile(5L, 100L, "image/png", 1024L)));
        assertThatThrownBy(() -> service.update(upload(10L, 5L))).isInstanceOf(BizException.class)
                .hasMessageContaining("已删除");
    }

    @Test
    void uploadCoverStoresOwnedPublicImageReference() {
        WikiSpace space = space(10L);
        when(spaces.findById(10L)).thenReturn(Optional.of(space));
        when(access.requireSpace(any(), any(), any())).thenReturn(new WikiAccessGrant(space, OPERATOR));
        when(files.findById(5L)).thenReturn(Optional.of(file(5L, 100L, "image/webp", 2048L, true)));
        when(spaces.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WikiSpaceDTO dto = service.update(upload(10L, 5L));

        assertThat(dto.getCoverType()).isEqualTo(WikiSpaceCoverType.UPLOAD.name());
        assertThat(dto.getCoverFileObjectId()).isEqualTo("5");
        assertThat(dto.getCoverSeed()).isEmpty();
    }

    @Test
    void randomCoverStoresServerGeneratedStableSeed() {
        WikiSpace space = space(10L);
        when(spaces.findById(10L)).thenReturn(Optional.of(space));
        when(access.requireSpace(any(), any(), any())).thenReturn(new WikiAccessGrant(space, OPERATOR));
        when(spaces.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WikiSpaceDTO first = service.random(10L, "封面");
        String seed = first.getCoverSeed();
        WikiSpaceDTO second = service.random(10L, "封面");

        assertThat(first.getCoverType()).isEqualTo(WikiSpaceCoverType.SEEDED.name());
        assertThat(seed).hasSize(12).matches("[a-z0-9]{12}");
        assertThat(first.getCoverAlt()).isEqualTo("封面");
        assertThat(second.getCoverSeed()).hasSize(12);
    }

    @Test
    void clearCoverDropsReferenceOnlyAndKeepsFile() {
        WikiSpace space = space(10L);
        space.applyCover(online.yudream.base.domain.platform.wiki.valobj.WikiCover.upload(5L, "封面"));
        when(spaces.findById(10L)).thenReturn(Optional.of(space));
        when(access.requireSpace(any(), any(), any())).thenReturn(new WikiAccessGrant(space, OPERATOR));
        when(spaces.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WikiSpaceDTO dto = service.clear(10L);

        assertThat(dto.getCoverType()).isEqualTo(WikiSpaceCoverType.NONE.name());
        assertThat(dto.getCoverFileObjectId()).isNull();
        assertThat(dto.getCoverSeed()).isEmpty();
        assertThat(dto.getCoverAlt()).isEmpty();
        // 只清引用：不触碰文件对象（不删除可能被共享的文件）
        verifyNoInteractions(files);
    }

    @Test
    void publicProfileExposesOnlySafeCoverFields() {
        WikiSpace space = space(10L);
        space.setPublicReadEnabled(true);
        space.applyCommentEnabled(true);
        space.applyCover(online.yudream.base.domain.platform.wiki.valobj.WikiCover.upload(5L, "封面"));
        when(spaces.findBySlug("demo")).thenReturn(Optional.of(space));

        WikiPublicSpaceProfileDTO profile = service.profile("demo");

        assertThat(profile.coverType()).isEqualTo(WikiSpaceCoverType.UPLOAD.name());
        assertThat(profile.coverUrl()).isEqualTo("/api/files/public/5/content");
        assertThat(profile.coverAlt()).isEqualTo("封面");
        assertThat(profile.commentEnabled()).isTrue();
    }

    @Test
    void publicProfileFailsClosedForNonPublicOrMissingSpace() {
        WikiSpace space = space(10L);
        when(spaces.findBySlug("demo")).thenReturn(Optional.of(space));
        assertThatThrownBy(() -> service.profile("demo")).isInstanceOf(BizException.class)
                .hasMessageContaining("未开放公开阅读");

        when(spaces.findBySlug("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.profile("missing")).isInstanceOf(BizException.class)
                .hasMessageContaining("知识库不存在");
    }

    @Test
    void portalOnlyListsPublicSpaces() {
        WikiSpace privateSpace = space(10L);
        WikiSpace publicSpace = space(11L);
        publicSpace.setPublicReadEnabled(true);
        when(spaces.findAll()).thenReturn(List.of(privateSpace, publicSpace));

        List<WikiPublicSpaceProfileDTO> profiles = service.profiles();

        assertThat(profiles).extracting(WikiPublicSpaceProfileDTO::slug).containsExactly("space-11");
        assertThat(profiles.getFirst().coverType()).isEqualTo(WikiSpaceCoverType.NONE.name());
    }

    private static WikiSpaceCoverCmd upload(Long spaceId, Long fileObjectId) {
        WikiSpaceCoverCmd cmd = new WikiSpaceCoverCmd();
        cmd.setSpaceId(spaceId);
        cmd.setCoverType(WikiSpaceCoverType.UPLOAD);
        cmd.setCoverFileObjectId(fileObjectId);
        cmd.setCoverAlt("封面");
        return cmd;
    }

    private static WikiSpace space(Long id) {
        WikiSpace space = WikiSpace.create("库", "space-" + id);
        space.setId(id);
        return space;
    }

    private static FileObject file(Long id, Long uploaderId, String contentType, Long size, boolean publicAccess) {
        FileObject file = FileObject.builder()
                .bucket("bucket")
                .objectKey("key")
                .originalName("cover.png")
                .contentType(contentType)
                .size(size)
                .module("wiki")
                .uploaderId(uploaderId)
                .publicAccess(publicAccess)
                .deleted(false)
                .build();
        file.setId(id);
        return file;
    }

    private static FileObject deletedFile(Long id, Long uploaderId, String contentType, Long size) {
        FileObject file = file(id, uploaderId, contentType, size, true);
        file.markDeleted();
        return file;
    }
}

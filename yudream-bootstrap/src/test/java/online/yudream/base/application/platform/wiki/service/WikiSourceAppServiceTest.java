package online.yudream.base.application.platform.wiki.service;

import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.wiki.dto.WikiSourceDTO;
import online.yudream.base.application.system.file.dto.FileContentDTO;
import online.yudream.base.application.system.file.dto.FileObjectDTO;
import online.yudream.base.application.system.file.service.FileAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.wiki.aggregate.WikiNode;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSource;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.enumerate.WikiCaptionStatus;
import online.yudream.base.domain.platform.wiki.enumerate.WikiIngestStatus;
import online.yudream.base.domain.platform.wiki.enumerate.WikiPageType;
import online.yudream.base.domain.platform.wiki.repo.WikiNodeRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiPageVersionRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSourceRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import online.yudream.base.domain.platform.wiki.service.WikiRemoteImageFetcher;
import online.yudream.base.domain.platform.wiki.service.WikiWebPageFetcher;
import online.yudream.base.domain.platform.wiki.valobj.WikiRemoteImage;
import online.yudream.base.domain.platform.wiki.valobj.WikiSourceImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 资料图片生命周期：站内 ID 越权跳过、远程图片继承空间可见性、caption 变化触发重新摄入。
 */
class WikiSourceAppServiceTest {

    private final CapabilityAppService capabilities = mock(CapabilityAppService.class);
    private final CapabilityModuleRepo capabilityModuleRepo = mock(CapabilityModuleRepo.class);
    private final WikiSpaceRepo spaceRepo = mock(WikiSpaceRepo.class);
    private final WikiSourceRepo sourceRepo = mock(WikiSourceRepo.class);
    private final WikiNodeRepo nodeRepo = mock(WikiNodeRepo.class);
    private final WikiPageVersionRepo versionRepo = mock(WikiPageVersionRepo.class);
    private final FileAppService files = mock(FileAppService.class);
    private final WikiWebPageFetcher webPageFetcher = mock(WikiWebPageFetcher.class);
    private final WikiRemoteImageFetcher remoteImageFetcher = mock(WikiRemoteImageFetcher.class);
    private final WikiImageCaptionService imageCaptions = mock(WikiImageCaptionService.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<online.yudream.base.application.platform.ai.service.AiRunAppService> runs =
            mock(ObjectProvider.class);
    private final WikiIngestAppService ingestAppService = mock(WikiIngestAppService.class);

    private final WikiSourceAppService service = new WikiSourceAppService(capabilities, capabilityModuleRepo, spaceRepo,
            sourceRepo, nodeRepo, versionRepo, files, webPageFetcher, remoteImageFetcher, imageCaptions, runs,
            ingestAppService);

    private final WikiSpace space = space();

    @BeforeEach
    void setUp() {
        when(spaceRepo.findById(100L)).thenReturn(Optional.of(space));
        when(sourceRepo.save(any())).thenAnswer(invocation -> {
            WikiSource saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(5L);
            }
            return saved;
        });
        CapabilityModule ai = new CapabilityModule();
        ai.setCode("ai");
        ai.setEnabled(true);
        ai.setConfig(Map.of());
        when(capabilityModuleRepo.findByCode("ai")).thenReturn(Optional.of(ai));
    }

    @Test
    void textImportSkipsStoredImagesTheCallerCannotRead() {
        stubReadableImage(111L, 7L);

        WikiSourceDTO dto = service.createText(100L, "/", "资料", "![a](/api/files/111/content)\n\n![b](/api/files/999/content)", 7L);

        // 越权站内 ID 不登记为图片证据，也不会进入 caption/生成上下文
        assertThat(dto.images()).singleElement()
                .satisfies(image -> assertThat(image.fileObjectId()).isEqualTo("111"));
        verify(ingestAppService).enqueueIngest(100L, 5L);
    }

    @Test
    void downloadedRemoteImagesRemainPrivateEvenForPublicSpace() {
        space.setPublicReadEnabled(true);
        when(remoteImageFetcher.fetch("https://example.com/a.png"))
                .thenReturn(new WikiRemoteImage(new byte[]{1}, "image/png", "a.png"));
        when(files.upload(any(), eq("a.png"), eq("image/png"), eq(1L), eq("wiki-image"), eq(7L), eq(false)))
                .thenReturn(FileObjectDTO.builder().id(222L).build());

        WikiSourceDTO dto = service.createText(100L, "/", "资料", "![远程图](https://example.com/a.png)", 7L);

        assertThat(dto.extractedText()).contains("/api/files/222/content");
        assertThat(dto.images()).singleElement().satisfies(image -> {
            assertThat(image.fileObjectId()).isEqualTo("222");
            assertThat(image.captionStatus()).isEqualTo(WikiCaptionStatus.SKIPPED.name());
        });
    }

    @Test
    void captionRunMarksSourcePendingAndReenqueuesIngest() {
        space.setVisionProviderCode("openai");
        space.setVisionModelCode("gpt-4o-mini");
        WikiSource source = WikiSource.text(100L, "/", "资料", "正文", "hash");
        source.setId(5L);
        when(sourceRepo.findById(5L)).thenReturn(Optional.of(source));
        when(imageCaptions.caption(eq(source), eq(space), eq(7L), any())).thenAnswer(invocation -> {
            source.setImages(List.of(new WikiSourceImage(111L, 1, 0, "按钮截图",
                    WikiCaptionStatus.CAPTIONED, "openai", "gpt-4o-mini", 10, 10, "image/png")));
            return 1;
        });

        service.captionImages(5L, 7L);

        assertThat(source.getIngestStatus()).isEqualTo(WikiIngestStatus.PENDING);
        assertThat(source.getImages()).singleElement()
                .satisfies(image -> assertThat(image.caption()).isEqualTo("按钮截图"));
        verify(ingestAppService).enqueueIngest(100L, 5L);
    }

    @Test
    void deletingSourceReleasesUnreferencedImagesAndKeepsReferencedOnes() {
        WikiSource source = WikiSource.text(100L, "/", "资料", "正文", "hash");
        source.setId(5L);
        source.markExtracted("正文", List.of(
                new WikiSourceImage(111L, 1, 0, "被页面引用", WikiCaptionStatus.CAPTIONED, null, null, 0, 0, "image/png"),
                new WikiSourceImage(222L, 1, 1, "未被引用", WikiCaptionStatus.CAPTIONED, null, null, 0, 0, "image/png")));
        when(sourceRepo.findById(5L)).thenReturn(Optional.of(source));
        WikiNode page = WikiNode.page(100L, null, "页面", "page", 0);
        page.setId(9L);
        page.applyGeneratedMarkdown("---\ntitle: 页面\ntype: concept\n---\n![图](/api/files/111/content)");
        when(nodeRepo.findBySpaceId(100L)).thenReturn(List.of(page));
        when(sourceRepo.findBySpaceId(100L)).thenReturn(List.of(source));

        service.delete(5L);

        verify(files).delete(222L);
        verify(files, never()).delete(111L);
        verify(ingestAppService).enqueueCleanup(100L, 5L, "资料", source.displayPath());
        verify(sourceRepo).deleteById(5L);
    }

    private void stubReadableImage(Long fileObjectId, Long callerId) {        when(files.content(fileObjectId, callerId, false)).thenReturn(FileContentDTO.builder()
                .contentType("image/png")
                .inputStream(new ByteArrayInputStream(new byte[]{1}))
                .build());
        when(files.content(999L, callerId, false)).thenThrow(new BizException("无权访问该文件"));
    }

    private static WikiSpace space() {
        WikiSpace space = WikiSpace.create("Demo", "demo");
        space.setId(100L);
        return space;
    }
}

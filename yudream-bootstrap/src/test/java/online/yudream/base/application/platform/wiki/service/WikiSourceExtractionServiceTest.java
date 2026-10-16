package online.yudream.base.application.platform.wiki.service;

import online.yudream.base.application.system.file.dto.FileContentDTO;
import online.yudream.base.application.system.file.dto.FileObjectDTO;
import online.yudream.base.application.system.file.service.FileAppService;
import online.yudream.base.domain.platform.document.service.DocumentTextExtractor;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSource;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.enumerate.WikiCaptionStatus;
import online.yudream.base.domain.platform.wiki.service.WikiDocumentImageExtractor;
import online.yudream.base.domain.platform.wiki.valobj.WikiExtractedImage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 抽取图片的访问策略：继承空间可见性，私有空间不得匿名公开。
 */
class WikiSourceExtractionServiceTest {

    private final FileAppService files = mock(FileAppService.class);
    private final DocumentTextExtractor textExtractor = mock(DocumentTextExtractor.class);
    private final WikiDocumentImageExtractor imageExtractor = mock(WikiDocumentImageExtractor.class);
    private final WikiSourceExtractionService service = new WikiSourceExtractionService(files, textExtractor, imageExtractor);

    @Test
    void privateSpaceImagesAreUploadedWithoutPublicAccess() {
        WikiSpace space = space(false);
        WikiSource source = fileSource();

        service.extract(source, space, 7L, Map.of());

        ArgumentCaptor<Boolean> publicAccess = ArgumentCaptor.forClass(Boolean.class);
        verify(files).upload(any(), eq("page-2-0.png"), eq("image/png"), eq(1L), eq("wiki-image"), eq(7L),
                publicAccess.capture());
        assertThat(publicAccess.getValue()).isFalse();
        assertThat(source.getImages()).singleElement().satisfies(image -> {
            assertThat(image.fileObjectId()).isEqualTo(555L);
            assertThat(image.pageNumber()).isEqualTo(2);
            assertThat(image.sequence()).isZero();
            // 未配置视觉模型：登记为 SKIPPED，不进入 caption 队列
            assertThat(image.captionStatus()).isEqualTo(WikiCaptionStatus.SKIPPED);
        });
    }

    /** Space visibility never makes extracted draft assets anonymously readable. */
    @Test
    void publicSpaceImagesRemainPrivateUntilPublishedProxyAllowsThem() {
        WikiSpace space = space(true);
        space.setVisionProviderCode("openai");
        space.setVisionModelCode("gpt-4o-mini");
        WikiSource source = fileSource();

        service.extract(source, space, 7L, Map.of());

        ArgumentCaptor<Boolean> publicAccess = ArgumentCaptor.forClass(Boolean.class);
        verify(files).upload(any(), eq("page-2-0.png"), eq("image/png"), eq(1L), eq("wiki-image"), eq(7L),
                publicAccess.capture());
        assertThat(publicAccess.getValue()).isFalse();
        assertThat(source.getImages()).singleElement()
                .satisfies(image -> assertThat(image.captionStatus()).isEqualTo(WikiCaptionStatus.PENDING));
    }

    private WikiSource fileSource() {
        WikiSource source = WikiSource.file(100L, "/", "doc.pdf", "doc.pdf", "application/pdf", 1L, "hash");
        source.setId(5L);
        when(files.content(1L)).thenReturn(FileContentDTO.builder()
                .contentType("application/pdf")
                .inputStream(new ByteArrayInputStream(new byte[]{1, 2, 3}))
                .build());
        when(textExtractor.extract(any())).thenReturn("资料正文");
        when(imageExtractor.extractImages(any())).thenReturn(List.of(
                new WikiExtractedImage(2, "image/png", "page-2-0.png", new byte[]{9}, 10, 20)));
        when(files.upload(any(), any(), any(), anyLong(), any(), any(), anyBoolean()))
                .thenReturn(FileObjectDTO.builder().id(555L).build());
        return source;
    }

    private static WikiSpace space(boolean publicRead) {
        WikiSpace space = WikiSpace.create("Demo", "demo");
        space.setId(100L);
        space.setPublicReadEnabled(publicRead);
        return space;
    }
}

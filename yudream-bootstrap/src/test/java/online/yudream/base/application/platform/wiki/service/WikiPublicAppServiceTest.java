package online.yudream.base.application.platform.wiki.service;

import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.wiki.dto.WikiPublicDocumentDetailDTO;
import online.yudream.base.application.system.file.service.FileAppService;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSource;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.repo.WikiPageVersionRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSourceRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import online.yudream.base.domain.platform.wiki.valobj.WikiSourceImage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 公开原文档 DTO 的图片必须匿名可读，私有图片不得因 URL 出现在公开接口。
 */
class WikiPublicAppServiceTest {

    private final WikiSpaceRepo spaces = mock(WikiSpaceRepo.class);
    private final WikiPageVersionRepo versions = mock(WikiPageVersionRepo.class);
    private final WikiSourceRepo sources = mock(WikiSourceRepo.class);
    private final WikiAppService wikiAppService = mock(WikiAppService.class);
    private final WikiSearchAppService searches = mock(WikiSearchAppService.class);
    private final FileAppService files = mock(FileAppService.class);
    private final CapabilityAppService capabilities = mock(CapabilityAppService.class);

    private final WikiPublicAppService service = new WikiPublicAppService(spaces, versions, sources, wikiAppService,
            searches, files, capabilities);

    @Test
    void publicDocumentOmitsImagesThatAreNotPubliclyReadable() {
        WikiSpace space = WikiSpace.create("Demo", "demo");
        space.setId(100L);
        space.setPublicReadEnabled(true);
        when(spaces.findBySlug("demo")).thenReturn(Optional.of(space));

        WikiSource source = WikiSource.text(100L, "/", "资料", "原始正文", "hash");
        source.setId(5L);
        source.markExtracted("原始正文", List.of(
                new WikiSourceImage(111L, 1, 0, "公开截图", null, null, null, 10, 10, "image/png"),
                new WikiSourceImage(222L, 1, 1, "私有截图", null, null, null, 10, 10, "image/png")));
        source.markIngested("hash");
        when(sources.findById(5L)).thenReturn(Optional.of(source));
        when(files.isPubliclyReadable(111L)).thenReturn(true);
        when(files.isPubliclyReadable(222L)).thenReturn(false);

        WikiPublicDocumentDetailDTO detail = service.document("demo", 5L);

        assertThat(detail.images()).singleElement().satisfies(image -> {
            assertThat(image.url()).isEqualTo("/api/files/111/content");
            assertThat(image.caption()).isEqualTo("公开截图");
        });
    }
}

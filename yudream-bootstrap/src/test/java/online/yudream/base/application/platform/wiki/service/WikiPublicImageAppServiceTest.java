package online.yudream.base.application.platform.wiki.service;

import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.system.file.dto.FileContentDTO;
import online.yudream.base.application.system.file.dto.FileObjectDTO;
import online.yudream.base.application.system.file.service.FileAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.aggregate.WikiNode;
import online.yudream.base.domain.platform.wiki.aggregate.WikiPageVersion;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSource;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.enumerate.WikiCaptionStatus;
import online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType;
import online.yudream.base.domain.platform.wiki.enumerate.WikiPageType;
import online.yudream.base.domain.platform.wiki.repo.WikiNodeRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiPageVersionRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSourceRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import online.yudream.base.domain.platform.wiki.valobj.WikiSourceImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 公开图片代理：只有“空间公开 + 节点已发布 + 发布正文引用 + 本空间 Wiki 图片资产”的图片可被匿名读取。
 * 仅凭正文引用不足以防跨空间/跨模块读取猜测到的私有文件 ID。
 */
class WikiPublicImageAppServiceTest {

    private static final String PUBLISHED_MARKDOWN =
            "---\ntitle: 部署教程\ntype: concept\n---\n![截图](/api/files/321/content)";

    private final CapabilityAppService capabilities = mock(CapabilityAppService.class);
    private final WikiSpaceRepo spaces = mock(WikiSpaceRepo.class);
    private final WikiNodeRepo nodes = mock(WikiNodeRepo.class);
    private final WikiPageVersionRepo versions = mock(WikiPageVersionRepo.class);
    private final WikiSourceRepo sources = mock(WikiSourceRepo.class);
    private final FileAppService files = mock(FileAppService.class);

    private final WikiPublicImageAppService service = new WikiPublicImageAppService(capabilities, spaces, nodes,
            versions, sources, files);

    private final WikiSpace space = space();

    @BeforeEach
    void setUp() {
        when(spaces.findBySlug("demo")).thenReturn(Optional.of(space));
        when(files.get(anyLong())).thenReturn(fileObject("wiki-image"));
    }

    @Test
    void streamsOwnSpaceWikiImageReferencedByPublishedMarkdown() {
        WikiNode node = stubPublishedNode();
        when(sources.findByImageFileObjectIds(100L, List.of(321L)))
                .thenReturn(List.of(sourceWithImage(100L, 321L)));
        FileContentDTO content = FileContentDTO.builder()
                .contentType("image/png")
                .contentLength(5L)
                .inputStream(new ByteArrayInputStream(new byte[]{1, 2, 3, 4, 5}))
                .build();
        when(files.content(321L)).thenReturn(content);

        FileContentDTO result = service.content("demo", 7L, 321L);

        assertThat(result.getContentType()).isEqualTo("image/png");
        assertThat(result.getInputStream()).isNotNull();
        assertThat(node.getPublishedVersionId()).isEqualTo(1000L);
    }

    @Test
    void rejectsFileFromForeignModuleEvenWhenRegisteredInSource() {
        stubPublishedNode();
        // 同空间资料来源里登记过，但模块不是 wiki 图片通道（例如用户私有头像/其他模块文件）
        when(sources.findByImageFileObjectIds(100L, List.of(321L)))
                .thenReturn(List.of(sourceWithImage(100L, 321L)));
        when(files.get(321L)).thenReturn(fileObject("common"));

        assertThatThrownBy(() -> service.content("demo", 7L, 321L))
                .isInstanceOf(BizException.class)
                .hasMessage("图片不可公开访问");
    }

    @Test
    void rejectsFileRegisteredInAnotherSpace() {
        stubPublishedNode();
        // 该文件是同 ID 的 wiki 图片，但登记在另一个知识库的资料里；本空间仓储查询为空
        WikiSource foreign = sourceWithImage(200L, 321L);
        when(sources.findByImageFileObjectIds(200L, List.of(321L))).thenReturn(List.of(foreign));
        when(sources.findByImageFileObjectIds(100L, List.of(321L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.content("demo", 7L, 321L))
                .isInstanceOf(BizException.class)
                .hasMessage("图片不可公开访问");
    }

    @Test
    void rejectsUnregisteredArbitraryFileId() {
        stubPublishedNode();
        when(sources.findByImageFileObjectIds(100L, List.of(321L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.content("demo", 7L, 321L))
                .isInstanceOf(BizException.class)
                .hasMessage("图片不可公开访问");
    }

    @Test
    void rejectsDeletedOrMissingFileMetadata() {
        stubPublishedNode();
        when(sources.findByImageFileObjectIds(100L, List.of(321L)))
                .thenReturn(List.of(sourceWithImage(100L, 321L)));
        when(files.get(321L)).thenThrow(new BizException("文件不存在"));

        assertThatThrownBy(() -> service.content("demo", 7L, 321L))
                .isInstanceOf(BizException.class)
                .hasMessage("图片不可公开访问");
    }

    @Test
    void rejectsPrivateSpace() {
        space.setPublicReadEnabled(false);

        assertThatThrownBy(() -> service.content("demo", 7L, 321L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未开放公开阅读");
    }

    @Test
    void rejectsUnpublishedNode() {
        WikiNode node = WikiNode.page(100L, null, "部署教程", "deploy", 0);
        node.setId(7L);
        node.setNodeType(WikiNodeType.PAGE);
        when(nodes.findById(7L)).thenReturn(Optional.of(node));

        assertThatThrownBy(() -> service.content("demo", 7L, 321L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未发布");
    }

    @Test
    void rejectsFileNotReferencedByPublishedMarkdown() {
        stubPublishedNode();

        assertThatThrownBy(() -> service.content("demo", 7L, 999L))
                .isInstanceOf(BizException.class)
                .hasMessage("图片不可公开访问");
    }

    @Test
    void rejectsNonImageContentOfRegisteredWikiAsset() {
        stubPublishedNode();
        when(sources.findByImageFileObjectIds(100L, List.of(321L)))
                .thenReturn(List.of(sourceWithImage(100L, 321L)));
        when(files.content(321L)).thenReturn(FileContentDTO.builder()
                .contentType("application/pdf")
                .inputStream(new ByteArrayInputStream(new byte[]{1}))
                .build());

        assertThatThrownBy(() -> service.content("demo", 7L, 321L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不是图片");
    }

    @Test
    void rejectsNodeFromAnotherSpace() {
        WikiNode node = publishedNode();
        node.setSpaceId(200L);
        when(nodes.findById(7L)).thenReturn(Optional.of(node));

        assertThatThrownBy(() -> service.content("demo", 7L, 321L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未发布");
    }

    private WikiNode stubPublishedNode() {
        WikiNode node = publishedNode();
        when(nodes.findById(7L)).thenReturn(Optional.of(node));
        when(versions.findById(1000L)).thenReturn(Optional.of(publishedVersion(node)));
        return node;
    }

    private WikiNode publishedNode() {
        WikiNode node = WikiNode.page(100L, null, "部署教程", "deploy", 0);
        node.setId(7L);
        node.setNodeType(WikiNodeType.PAGE);
        node.setPageType(WikiPageType.CONCEPT);
        node.setPublishedVersionId(1000L);
        return node;
    }

    private WikiPageVersion publishedVersion(WikiNode node) {
        WikiPageVersion version = WikiPageVersion.builder()
                .id(1000L)
                .nodeId(node.getId())
                .title("部署教程")
                .markdown(PUBLISHED_MARKDOWN)
                .build();
        version.setSpaceId(100L);
        return version;
    }

    private static WikiSource sourceWithImage(Long spaceId, Long fileObjectId) {
        WikiSource source = WikiSource.text(spaceId, "/", "资料", "正文", "hash-" + spaceId);
        source.setId(spaceId);
        source.setImages(List.of(new WikiSourceImage(fileObjectId, 1, 0, "截图",
                WikiCaptionStatus.CAPTIONED, null, null, 0, 0, "image/png")));
        return source;
    }

    private static FileObjectDTO fileObject(String module) {
        return FileObjectDTO.builder().id(321L).module(module).contentType("image/png").build();
    }

    private static WikiSpace space() {
        WikiSpace space = WikiSpace.create("Demo", "demo");
        space.setId(100L);
        space.setPublicReadEnabled(true);
        return space;
    }
}

package online.yudream.base.application.platform.chat.support;

import online.yudream.base.application.platform.chat.dto.ChatContextRefDTO;
import online.yudream.base.application.platform.wiki.dto.WikiSearchHitDTO;
import online.yudream.base.application.platform.wiki.service.WikiSearchAppService;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChatWikiContextResolverTest {

    private final WikiSearchAppService searchService = mock(WikiSearchAppService.class);
    private final WikiSpaceRepo spaceRepo = mock(WikiSpaceRepo.class);
    private final ChatWikiContextResolver resolver = new ChatWikiContextResolver(searchService, spaceRepo);

    @Test
    void returnsNoPromptWhenWikiIsNotSelected() {
        ChatWikiContextResolver.ResolvedContext result = resolver.resolve(List.of(), null, "如何部署", List.of());

        assertThat(result.prompt()).isEmpty();
        assertThat(result.citations()).isEmpty();
    }

    @Test
    void fallsBackToSelectedSpaceCatalogWhenSearchHasNoHits() {
        WikiSpace space = space(1L, "产品文档", "product");
        when(spaceRepo.findBySlug("product")).thenReturn(Optional.of(space));
        when(searchService.searchForAdmin("product", "如何部署", 3, null, true)).thenReturn(List.of());
        when(searchService.catalogForChat("product", "如何部署", 2))
                .thenReturn(List.of(hit("部署指南", "guide/deploy", "部署步骤正文")));

        ChatWikiContextResolver.ResolvedContext result = resolver.resolve(
                List.of(new ChatContextRefDTO("wiki", "product", "产品文档")), null, "如何部署", wikiView());

        assertThat(result.prompt()).contains("已选择的知识库目录摘要", "部署指南", "部署步骤正文");
        assertThat(result.citations()).singleElement().satisfies(citation -> {
            assertThat(citation.spaceSlug()).isEqualTo("product");
            assertThat(citation.title()).isEqualTo("部署指南");
        });
    }

    @Test
    void distinguishesSelectedWikiWithoutContentFromNoSelection() {
        WikiSpace space = space(1L, "空知识库", "empty");
        when(spaceRepo.findBySlug("empty")).thenReturn(Optional.of(space));
        when(searchService.searchForAdmin("empty", "问题", 3, null, true)).thenReturn(List.of());
        when(searchService.catalogForChat("empty", "问题", 2)).thenReturn(List.of());

        ChatWikiContextResolver.ResolvedContext result = resolver.resolve(
                List.of(new ChatContextRefDTO("wiki", "empty", "空知识库")), null, "问题", wikiView());

        assertThat(result.prompt()).contains("已选择知识库", "未找到可用的目录或页面内容");
        assertThat(result.citations()).isEmpty();
    }

    @Test
    void aggregatesAllSpacesAndIsolatesOneFailedSpace() {
        WikiSpace failed = space(1L, "故障库", "failed");
        WikiSpace available = space(2L, "可用库", "available");
        when(spaceRepo.findAll()).thenReturn(List.of(failed, available));
        when(searchService.searchForAdmin("failed", "问题", 3, null, true)).thenThrow(new IllegalStateException("index unavailable"));
        when(searchService.catalogForChat("failed", "问题", 2)).thenReturn(List.of(hit("故障库目录", "catalog", "目录摘要")));
        when(searchService.searchForAdmin("available", "问题", 3, null, true)).thenReturn(List.of(hit("命中页面", "answer", "有效内容")));

        ChatWikiContextResolver.ResolvedContext result = resolver.resolve(
                List.of(new ChatContextRefDTO("wiki", "all", "全部知识库")), null, "问题", wikiView());

        assertThat(result.prompt()).contains("知识库检索结果", "命中页面", "有效内容", "故障库目录", "目录摘要");
        assertThat(result.citations()).extracting(citation -> citation.spaceSlug())
                .containsExactlyInAnyOrder("failed", "available");
    }

    @Test
    void reportsSelectedAllSpacesWhenEverySpaceFails() {
        WikiSpace first = space(1L, "一号库", "first");
        WikiSpace second = space(2L, "二号库", "second");
        when(spaceRepo.findAll()).thenReturn(List.of(first, second));
        when(searchService.searchForAdmin("first", "问题", 3, null, true)).thenThrow(new IllegalStateException("first search"));
        when(searchService.searchForAdmin("second", "问题", 3, null, true)).thenThrow(new IllegalStateException("second search"));
        when(searchService.catalogForChat("first", "问题", 2)).thenThrow(new IllegalStateException("first catalog"));
        when(searchService.catalogForChat("second", "问题", 2)).thenThrow(new IllegalStateException("second catalog"));

        ChatWikiContextResolver.ResolvedContext result = resolver.resolve(
                List.of(new ChatContextRefDTO("wiki", "all", "全部知识库")), null, "问题", wikiView());

        assertThat(result.prompt()).contains("已选择全部知识库", "上下文加载失败");
        assertThat(result.citations()).isEmpty();
    }

    @Test
    void forgedSlugWithOnlyChatPermissionDoesNotReadWiki() {
        ChatWikiContextResolver.ResolvedContext result = resolver.resolve(
                List.of(new ChatContextRefDTO("wiki", "secret", "机密库")), null, "机密内容", List.of("platform:chat:view"));

        assertThat(result.prompt()).contains("无权读取");
        assertThat(result.citations()).isEmpty();
        verifyNoInteractions(spaceRepo, searchService);
    }

    @Test
    void allWithoutWikiViewPermissionDoesNotEnumerateSpaces() {
        ChatWikiContextResolver.ResolvedContext result = resolver.resolve(
                List.of(new ChatContextRefDTO("wiki", "all", "全部知识库")), null, "问题", List.of("platform:chat:view"));

        assertThat(result.prompt()).contains("无权读取");
        verify(spaceRepo, never()).findAll();
        verifyNoInteractions(searchService);
    }

    @Test
    void wildcardPermissionCanReadSelectedSpace() {
        WikiSpace space = space(1L, "产品文档", "product");
        when(spaceRepo.findBySlug("product")).thenReturn(Optional.of(space));
        when(searchService.searchForAdmin("product", "如何部署", 3, null, true))
                .thenReturn(List.of(hit("部署指南", "guide/deploy", "部署步骤正文")));

        ChatWikiContextResolver.ResolvedContext result = resolver.resolve(
                List.of(new ChatContextRefDTO("wiki", "product", "产品文档")), null, "如何部署", List.of("*"));

        assertThat(result.prompt()).contains("部署指南", "部署步骤正文");
        verify(spaceRepo).findBySlug("product");
    }

    @Test
    void wildcardPermissionCanEnumerateAllSpaces() {
        WikiSpace space = space(1L, "产品文档", "product");
        when(spaceRepo.findAll()).thenReturn(List.of(space));
        when(searchService.searchForAdmin("product", "如何部署", 3, null, true))
                .thenReturn(List.of(hit("部署指南", "guide/deploy", "部署步骤正文")));

        ChatWikiContextResolver.ResolvedContext result = resolver.resolve(
                List.of(new ChatContextRefDTO("wiki", "all", "全部知识库")), null, "如何部署", List.of("*"));

        assertThat(result.prompt()).contains("部署指南", "部署步骤正文");
        verify(spaceRepo).findAll();
    }

    @Test
    void unrelatedCatalogFallbackContainsOnlyTitlesAndNoHitNotice() {
        WikiSpace space = space(1L, "产品文档", "product");
        when(spaceRepo.findBySlug("product")).thenReturn(Optional.of(space));
        when(searchService.searchForAdmin("product", "Kubernetes", 3, null, true)).thenReturn(List.of());
        when(searchService.catalogForChat("product", "Kubernetes", 2))
                .thenReturn(List.of(hit("账号说明", "account", "")));

        ChatWikiContextResolver.ResolvedContext result = resolver.resolve(
                List.of(new ChatContextRefDTO("wiki", "product", "产品文档")), null, "Kubernetes", wikiView());

        assertThat(result.prompt()).contains("没有检索命中", "账号说明");
        assertThat(result.prompt()).doesNotContain("随机正文");
    }

    @Test
    void searchEmptyAndCatalogFailureReportsLoadFailure() {
        WikiSpace space = space(1L, "故障库", "failed");
        when(spaceRepo.findBySlug("failed")).thenReturn(Optional.of(space));
        when(searchService.searchForAdmin("failed", "问题", 3, null, true)).thenReturn(List.of());
        when(searchService.catalogForChat("failed", "问题", 2)).thenThrow(new IllegalStateException("catalog unavailable"));

        ChatWikiContextResolver.ResolvedContext result = resolver.resolve(
                List.of(new ChatContextRefDTO("wiki", "failed", "故障库")), null, "问题", wikiView());

        assertThat(result.prompt()).contains("上下文加载失败");
        assertThat(result.prompt()).doesNotContain("未找到可用的目录或页面内容");
    }

    @Test
    void selectedSpaceRequestsGraphExpansion() {
        WikiSpace space = space(1L, "产品文档", "product");
        when(spaceRepo.findBySlug("product")).thenReturn(Optional.of(space));
        when(searchService.searchForAdmin("product", "部署问题", 3, null, true))
                .thenReturn(List.of(hit("部署指南", "guide/deploy", "部署步骤正文")));

        resolver.resolve(List.of(new ChatContextRefDTO("wiki", "product", "产品文档")),
                null, "部署问题", wikiView());

        verify(searchService).searchForAdmin("product", "部署问题", 3, null, true);
    }

    @Test
    void allSpacesRequestGraphExpansionForEachSpace() {
        WikiSpace first = space(1L, "一号库", "first");
        WikiSpace second = space(2L, "二号库", "second");
        when(spaceRepo.findAll()).thenReturn(List.of(first, second));
        when(searchService.searchForAdmin("first", "问题", 3, null, true)).thenReturn(List.of());
        when(searchService.searchForAdmin("second", "问题", 3, null, true)).thenReturn(List.of());
        when(searchService.catalogForChat("first", "问题", 2)).thenReturn(List.of());
        when(searchService.catalogForChat("second", "问题", 2)).thenReturn(List.of());

        resolver.resolve(List.of(new ChatContextRefDTO("wiki", "all", "全部知识库")),
                null, "问题", wikiView());

        verify(searchService).searchForAdmin("first", "问题", 3, null, true);
        verify(searchService).searchForAdmin("second", "问题", 3, null, true);
    }

    private static List<String> wikiView() {
        return List.of("platform:wiki:view");
    }

    private static WikiSpace space(Long id, String name, String slug) {
        WikiSpace space = WikiSpace.create(name, slug);
        space.setId(id);
        return space;
    }

    private static WikiSearchHitDTO hit(String title, String path, String content) {
        return WikiSearchHitDTO.builder()
                .score(0.8)
                .nodeId("100")
                .kind("PAGE")
                .title(title)
                .path(path)
                .content(content)
                .build();
    }
}

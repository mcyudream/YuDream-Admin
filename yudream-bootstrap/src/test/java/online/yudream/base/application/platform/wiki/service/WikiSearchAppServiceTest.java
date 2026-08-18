package online.yudream.base.application.platform.wiki.service;

import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.wiki.dto.WikiSearchHitDTO;
import online.yudream.base.domain.platform.wiki.aggregate.WikiNode;
import online.yudream.base.domain.platform.wiki.aggregate.WikiPageVersion;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType;
import online.yudream.base.domain.platform.wiki.enumerate.WikiPageType;
import online.yudream.base.domain.platform.wiki.repo.WikiNodeRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiPageVersionRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSourceRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import online.yudream.base.domain.platform.wiki.service.WikiIndexGateway;
import online.yudream.base.domain.platform.wiki.service.WikiQueryExpansionGateway;
import online.yudream.base.domain.platform.wiki.service.WikiRerankGateway;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WikiSearchAppServiceTest {

    private final CapabilityAppService capabilities = mock(CapabilityAppService.class);
    private final WikiSpaceRepo spaces = mock(WikiSpaceRepo.class);
    private final WikiNodeRepo nodes = mock(WikiNodeRepo.class);
    private final WikiPageVersionRepo versions = mock(WikiPageVersionRepo.class);
    private final WikiSourceRepo sources = mock(WikiSourceRepo.class);
    private final WikiIndexGateway indexes = mock(WikiIndexGateway.class);
    private final WikiQueryExpansionGateway expansions = mock(WikiQueryExpansionGateway.class);
    private final WikiRerankGateway reranks = mock(WikiRerankGateway.class);
    private final Map<Long, WikiPageVersion> publishedVersions = new LinkedHashMap<>();

    private final WikiSearchAppService service = new WikiSearchAppService(
            capabilities, spaces, nodes, versions, sources, indexes, expansions, reranks);

    @Test
    void publicSearchUsesRootPageSlugPathAndUrl() {
        WikiNode root = page(1L, null, "overview", "概览");
        publish(root, "概览", "欢迎进入概览页");
        setupPublicSpace(List.of(root));

        var result = service.searchForPublicSite("demo", "概览", 5, null, false);

        assertThat(result).singleElement().satisfies(hit -> {
            assertThat(hit.getPath()).isEqualTo("overview");
            assertThat(hit.getSourceUrl()).isEqualTo("/wiki/demo/overview");
        });
    }

    @Test
    void publicSearchUsesNestedDirectoryPageSlugPathAndUrl() {
        WikiNode dir = directory(1L, null, "guide", "指南");
        WikiNode page = page(2L, 1L, "target", "目标页");
        publish(page, "目标页", "目标页发布正文");
        setupPublicSpace(List.of(dir, page));

        var result = service.searchForPublicSite("demo", "目标页", 5, null, false);

        assertThat(result).singleElement().satisfies(hit -> {
            assertThat(hit.getPath()).isEqualTo("guide/target");
            assertThat(hit.getSourceUrl()).isEqualTo("/wiki/demo/guide/target");
        });
    }

    @Test
    void publicSearchPathPrefixMatchesSlugPath() {
        WikiNode guideDir = directory(1L, null, "guide", "指南");
        WikiNode target = page(2L, 1L, "target", "目标页");
        WikiNode otherDir = directory(3L, null, "other", "其他");
        WikiNode otherPage = page(4L, 3L, "page", "无关页");
        publish(target, "目标页", "目标页共享正文");
        publish(otherPage, "无关页", "无关页共享正文");
        setupPublicSpace(List.of(guideDir, target, otherDir, otherPage));

        var result = service.searchForPublicSite("demo", "共享正文", 5, "guide", false);

        assertThat(result).singleElement().satisfies(hit -> assertThat(hit.getNodeId()).isEqualTo("2"));
    }

    @Test
    void publicSearchDraftOnlyKeywordYieldsNoResult() {
        WikiNode node = page(1L, null, "page", "草稿标题");
        node.setMarkdownDraft("---\ntitle: 草稿标题\ntype: concept\n---\n草稿正文包含草稿关键词");
        publish(node, "发布标题", "发布正文不包含");
        setupPublicSpace(List.of(node));

        var result = service.searchForPublicSite("demo", "草稿关键词", 5, null, false);

        assertThat(result).isEmpty();
    }

    @Test
    void publicSearchPublishedOnlyKeywordYieldsResult() {
        WikiNode node = page(1L, null, "page", "发布标题");
        publish(node, "发布标题", "发布正文包含发布关键词");
        setupPublicSpace(List.of(node));

        var result = service.searchForPublicSite("demo", "发布关键词", 5, null, false);

        assertThat(result).singleElement().satisfies(hit -> assertThat(hit.getNodeId()).isEqualTo("1"));
    }

    @Test
    void publicSearchReturnsPublishedTitleWhenDraftTitleDiffers() {
        WikiNode node = page(1L, null, "page", "草稿标题");
        node.setMarkdownDraft("---\ntitle: 草稿标题\ntype: concept\n---\n草稿正文");
        publish(node, "发布标题", "发布正文包含发布关键词");
        setupPublicSpace(List.of(node));

        var result = service.searchForPublicSite("demo", "发布关键词", 5, null, false);

        assertThat(result).singleElement().satisfies(hit -> assertThat(hit.getTitle()).isEqualTo("发布标题"));
    }

    @Test
    void publicSearchDoesNotProbeDraftKeywordRepo() {
        WikiNode node = page(1L, null, "page", "发布标题");
        publish(node, "发布标题", "发布正文包含发布关键词");
        setupPublicSpace(List.of(node));

        service.searchForPublicSite("demo", "发布关键词", 5, null, false);

        verify(nodes, never()).searchByKeyword(anyLong(), anyString(), anyInt());
    }

    @Test
    void publicSearchExcludesUnpublishedPages() {
        WikiNode published = page(1L, null, "published", "已发布");
        publish(published, "已发布", "页面内容");
        WikiNode unpublished = page(2L, null, "unpublished", "未发布");
        setupPublicSpace(List.of(published, unpublished));

        var result = service.searchForPublicSite("demo", "页面", 5, null, false);

        assertThat(result).singleElement().satisfies(hit -> assertThat(hit.getNodeId()).isEqualTo("1"));
    }

    @Test
    void publicSearchIgnoresSourceGroundedAndNeverReadsSources() {
        WikiNode node = page(1L, null, "page", "已发布");
        publish(node, "已发布", "页面内容");
        setupPublicSpace(List.of(node));

        var result = service.searchForPublicSite("demo", "页面", 5, null, false, true);

        assertThat(result).singleElement().satisfies(hit -> assertThat(hit.getKind()).isEqualTo("PAGE"));
        verify(sources, never()).searchByKeyword(anyLong(), anyString(), anyInt());
    }

    @Test
    void chatCatalogUsesOnlyPublishedVersionAndNeverDraft() {
        WikiNode node = page(1L, null, "deploy", "草稿标题");
        node.setSummary("草稿摘要机密");
        node.setMarkdownDraft("---\ntitle: 草稿标题\nsummary: 草稿摘要机密\ntype: concept\n---\n草稿正文机密");
        publish(node, "发布标题", "发布正文公开");
        setupAdminSpace(List.of(node));

        var result = service.catalogForChat("demo", "发布", 2);

        assertThat(result).singleElement().satisfies(hit -> {
            assertThat(hit.getTitle()).isEqualTo("发布标题");
            assertThat(hit.getContent()).contains("发布正文公开");
            assertThat(hit.getContent()).doesNotContain("草稿摘要机密", "草稿正文机密");
        });
    }

    @Test
    void chatCatalogPrefersQueryRelatedPublishedSummary() {
        WikiNode related = page(1L, null, "deploy", "部署指南");
        publishMarkdown(related, "部署指南", "---\ntitle: 部署指南\nsummary: Kubernetes 部署步骤\ntype: concept\n---\n正文");
        WikiNode unrelated = page(2L, null, "other", "其他说明");
        publishMarkdown(unrelated, "其他说明", "---\ntitle: 其他说明\nsummary: 账号注册说明\ntype: concept\n---\n正文");
        setupAdminSpace(List.of(unrelated, related));

        var result = service.catalogForChat("demo", "Kubernetes 部署", 2);

        assertThat(result).isNotEmpty();
        assertThat(result.get(0).getTitle()).isEqualTo("部署指南");
        assertThat(result.get(0).getContent()).contains("Kubernetes 部署步骤");
    }

    @Test
    void chatCatalogWithoutRelevantSummaryReturnsTitlesWithoutBody() {
        WikiNode node = page(1L, null, "account", "账号说明");
        publishMarkdown(node, "账号说明", "---\ntitle: 账号说明\nsummary: 注册流程\ntype: concept\n---\n这里有不相关的随机正文");
        setupAdminSpace(List.of(node));

        var result = service.catalogForChat("demo", "Kubernetes 部署", 2);

        assertThat(result).singleElement().satisfies(hit -> assertThat(hit.getContent()).isEmpty());
    }

    @Test
    void publicSearchTokenizesMultiWordQueryAndScoresAllTermsHigher() {
        WikiNode both = page(1L, null, "both", "多词页");
        publish(both, "多词页", "正文 Java 虚拟线程");
        WikiNode onlyJava = page(2L, null, "only", "单词页");
        publish(onlyJava, "单词页", "正文 Java 平台");
        setupPublicSpace(List.of(both, onlyJava));

        var result = service.searchForPublicSite("demo", "Java 虚拟线程", 5, null, false);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getNodeId()).isEqualTo("1");
    }

    @Test
    void publicSearchMatchesChineseQuestionAgainstRelatedTitle() {
        WikiNode tutorial = page(1L, null, "join", "服务器进入教程");
        publish(tutorial, "服务器进入教程", "获取服务器 IP 后在多人游戏里添加服务器。");
        WikiNode other = page(2L, null, "account", "角色配置");
        publish(other, "角色配置", "创建游戏角色并设置外观。");
        setupPublicSpace(List.of(tutorial, other));

        // “服务器怎么进入”是无空格中文问句，不能再要求整串精确命中
        var result = service.searchForPublicSite("demo", "服务器怎么进入", 5, null, false);

        assertThat(result).isNotEmpty();
        assertThat(result.get(0).getNodeId()).isEqualTo("1");
    }

    @Test
    void adminSearchPassesGraphExpansionWhenSpaceGraphIsEnabled() {
        WikiSpace space = setupGraphSearchSpace(true);
        when(indexes.search(space, "部署", 10, null, true)).thenReturn(List.of());
        when(nodes.searchByKeyword(100L, "部署", 10)).thenReturn(List.of());

        service.searchForAdmin("demo", "部署", 5, null, true);

        verify(indexes).search(space, "部署", 10, null, true);
    }

    @Test
    void adminSearchDisablesGraphExpansionWhenSpaceGraphIsDisabled() {
        WikiSpace space = setupGraphSearchSpace(false);
        when(indexes.search(space, "部署", 10, null, false)).thenReturn(List.of());
        when(nodes.searchByKeyword(100L, "部署", 10)).thenReturn(List.of());

        service.searchForAdmin("demo", "部署", 5, null, true);

        verify(indexes).search(space, "部署", 10, null, false);
    }

    @Test
    void adminSearchFallsBackToKeywordHitsWhenGraphGatewayFails() {
        WikiSpace space = setupGraphSearchSpace(true);
        WikiNode keywordPage = page(1L, null, "deploy", "部署指南");
        keywordPage.setMarkdownDraft("---\ntitle: 部署指南\ntype: concept\n---\n部署步骤正文");
        when(indexes.search(space, "部署", 10, null, true))
                .thenThrow(new IllegalStateException("neo4j unavailable"));
        when(nodes.searchByKeyword(100L, "部署", 10)).thenReturn(List.of(keywordPage));

        List<WikiSearchHitDTO> result = service.searchForAdmin("demo", "部署", 5, null, true);

        assertThat(result).singleElement().satisfies(hit -> {
            assertThat(hit.getNodeId()).isEqualTo("1");
            assertThat(hit.getTitle()).isEqualTo("部署指南");
            assertThat(hit.getContent()).contains("部署步骤正文");
        });
    }

    private WikiSpace setupGraphSearchSpace(boolean graphEnabled) {
        WikiSpace space = WikiSpace.create("Demo", "demo");
        space.setId(100L);
        space.update("Demo", "demo", "", false, false,
                "embedding-provider", "embedding-model", graphEnabled, "graph-provider", "graph-model",
                1200, 160, 8);
        when(spaces.findBySlug("demo")).thenReturn(Optional.of(space));
        return space;
    }

    private void setupPublicSpace(List<WikiNode> all) {
        setupAdminSpace(all);
        spaces.findBySlug("demo").orElseThrow().setPublicReadEnabled(true);
    }

    private void setupAdminSpace(List<WikiNode> all) {
        var space = WikiSpace.create("Demo", "demo");
        space.setId(100L);
        when(spaces.findBySlug("demo")).thenReturn(Optional.of(space));
        when(nodes.findBySpaceId(anyLong())).thenReturn(all);
        when(versions.findByIds(org.mockito.ArgumentMatchers.anyCollection()))
                .thenAnswer(invocation -> ((java.util.Collection<Long>) invocation.getArgument(0)).stream()
                        .map(publishedVersions::get)
                        .filter(java.util.Objects::nonNull)
                        .toList());
    }

    private void publish(WikiNode node, String publishedTitle, String body) {
        publishMarkdown(node, publishedTitle,
                "---\ntitle: " + publishedTitle + "\ntype: concept\n---\n" + body);
    }

    private void publishMarkdown(WikiNode node, String publishedTitle, String markdown) {
        Long versionId = 1000L + node.getId();
        node.setPublishedVersionId(versionId);
        WikiPageVersion version = WikiPageVersion.builder()
                .id(versionId)
                .nodeId(node.getId())
                .title(publishedTitle)
                .markdown(markdown)
                .build();
        version.setSpaceId(node.getSpaceId());
        publishedVersions.put(versionId, version);
        when(versions.findById(versionId)).thenReturn(Optional.of(version));
    }

    private static WikiNode directory(Long id, Long parentId, String slug, String title) {
        WikiNode node = WikiNode.directory(100L, parentId, title, slug, 0);
        node.setId(id);
        return node;
    }

    private static WikiNode page(Long id, Long parentId, String slug, String title) {
        WikiNode node = WikiNode.page(100L, parentId, title, slug, 0);
        node.setId(id);
        node.setNodeType(WikiNodeType.PAGE);
        node.setPageType(WikiPageType.CONCEPT);
        node.setMarkdownDraft("---\ntitle: " + title + "\ntype: concept\n---\n正文");
        return node;
    }

    private static WikiPageVersion publishedVersion(Long id, Long nodeId, String title, String body) {
        return WikiPageVersion.builder()
                .id(id)
                .nodeId(nodeId)
                .title(title)
                .markdown("---\ntitle: " + title + "\ntype: concept\n---\n" + body)
                .build();
    }
}

package online.yudream.base.application.platform.wiki.service;

import online.yudream.base.application.platform.ai.service.AiRunAppService;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.service.AiGenerationGateway;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationResult;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.wiki.aggregate.WikiIngestTask;
import online.yudream.base.domain.platform.wiki.aggregate.WikiNode;
import online.yudream.base.domain.platform.wiki.aggregate.WikiPageVersion;
import online.yudream.base.domain.platform.wiki.aggregate.WikiReviewItem;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSource;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.enumerate.WikiCaptionStatus;
import online.yudream.base.domain.platform.wiki.enumerate.WikiIngestTaskType;
import online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType;
import online.yudream.base.domain.platform.wiki.enumerate.WikiPageType;
import online.yudream.base.domain.platform.wiki.enumerate.WikiReviewItemType;
import online.yudream.base.domain.platform.wiki.repo.WikiIngestTaskRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiNodeRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiPageVersionRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiReviewItemRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSourceRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import online.yudream.base.domain.platform.wiki.service.WikiIngestCancellationRegistry;
import online.yudream.base.domain.platform.wiki.service.WikiWebSearchGateway;
import online.yudream.base.domain.platform.wiki.valobj.WikiFrontmatter;
import online.yudream.base.domain.platform.wiki.valobj.WikiIngestProgress;
import online.yudream.base.domain.platform.wiki.valobj.WikiSourceImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 摄入生成目录（llm-gen）、覆盖保护与图片证据进入生成/原文档的行为测试。
 */
class WikiIngestAppServiceTest {

    private static final String GENERATED_SLUG = "llm-gen";

    private final CapabilityAppService capabilities = mock(CapabilityAppService.class);
    private final CapabilityModuleRepo capabilityModuleRepo = mock(CapabilityModuleRepo.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<AiGenerationGateway> aiGeneration = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<AiRunAppService> runs = mock(ObjectProvider.class);
    private final WikiSpaceRepo spaceRepo = mock(WikiSpaceRepo.class);
    private final WikiNodeRepo nodeRepo = mock(WikiNodeRepo.class);
    private final WikiPageVersionRepo versionRepo = mock(WikiPageVersionRepo.class);
    private final WikiSourceRepo sourceRepo = mock(WikiSourceRepo.class);
    private final WikiIngestTaskRepo taskRepo = mock(WikiIngestTaskRepo.class);
    private final WikiReviewItemRepo reviewItemRepo = mock(WikiReviewItemRepo.class);
    private final WikiSourceExtractionService extractionService = mock(WikiSourceExtractionService.class);
    private final WikiImageCaptionService imageCaptions = mock(WikiImageCaptionService.class);
    private final WikiPublicationAppService publicationAppService = mock(WikiPublicationAppService.class);
    private final WikiWebSearchGateway webSearchGateway = mock(WikiWebSearchGateway.class);
    private final WikiIngestCancellationRegistry cancellationRegistry = mock(WikiIngestCancellationRegistry.class);

    private final WikiIngestAppService service = new WikiIngestAppService(capabilities, capabilityModuleRepo, aiGeneration,
            runs, spaceRepo, nodeRepo, versionRepo, sourceRepo, taskRepo, reviewItemRepo, extractionService,
            imageCaptions, publicationAppService, webSearchGateway, cancellationRegistry);

    private final List<AiGenerationRequest> requests = new ArrayList<>();
    private final List<WikiReviewItem> reviewItems = new ArrayList<>();
    private final List<WikiIngestProgress> progressEvents = new ArrayList<>();
    private final Map<Long, WikiNode> nodesById = new LinkedHashMap<>();
    private final Map<Long, WikiPageVersion> versionsById = new LinkedHashMap<>();
    private long nextNodeId = 1000L;

    private final WikiSpace space = space();
    private final WikiSource source = source();

    @BeforeEach
    void setUp() {
        AiGenerationGateway gateway = mock(AiGenerationGateway.class);
        when(gateway.generateStream(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            requests.add(invocation.getArgument(0));
            String json = requests.size() == 1
                    ? "{\"entities\":[],\"reviewItems\":[]}"
                    : "{\"pages\":[{\"title\":\"Deploy Guide\",\"type\":\"concept\",\"summary\":\"部署摘要\","
                    + "\"sources\":[\"/原始资料\"],\"related\":[],\"tags\":[\"source\"],\"body\":\"LLM 生成正文\"}],"
                    + "\"reviewItems\":[]}";
            return AiGenerationResult.of(json, List.of(), null);
        });
        when(aiGeneration.getObject()).thenReturn(gateway);

        AiRunAppService runService = mock(AiRunAppService.class);
        AiRunAppService.RunEmitter emitter = mock(AiRunAppService.RunEmitter.class);
        doAnswer(invocation -> {
            Function<AiRunAppService.RunEmitter, Object> action = invocation.getArgument(3);
            return action.apply(emitter);
        }).when(runService).execute(anyLong(), anyString(), anyString(), any(), any());
        when(runs.getObject()).thenReturn(runService);

        CapabilityModule ai = new CapabilityModule();
        ai.setCode("ai");
        ai.setEnabled(true);
        ai.setConfig(new HashMap<>());
        when(capabilityModuleRepo.findByCode("ai")).thenReturn(Optional.of(ai));

        when(spaceRepo.findById(space.getId())).thenReturn(Optional.of(space));
        when(sourceRepo.findById(source.getId())).thenReturn(Optional.of(source));
        when(sourceRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(nodeRepo.findBySpaceId(anyLong())).thenAnswer(invocation -> new ArrayList<>(nodesById.values()));
        when(nodeRepo.findBySlug(anyLong(), anyString())).thenAnswer(invocation -> nodesById.values().stream()
                .filter(node -> invocation.getArgument(1).equals(node.getSlug())).findFirst());
        when(nodeRepo.save(any())).thenAnswer(invocation -> {
            WikiNode node = invocation.getArgument(0);
            if (node.getId() == null) {
                node.setId(nextNodeId++);
            }
            nodesById.put(node.getId(), node);
            return node;
        });
        when(versionRepo.findById(anyLong()))
                .thenAnswer(invocation -> Optional.ofNullable(versionsById.get(invocation.getArgument(0))));
        when(reviewItemRepo.findBySpaceIdAndStatus(anyLong(), any())).thenReturn(List.of());
        when(reviewItemRepo.save(any())).thenAnswer(invocation -> {
            reviewItems.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        when(taskRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void enqueueIngestRejectsSourceFromAnotherSpace() {
        WikiSource foreignSource = WikiSource.text(200L, "/", "跨库资料", "私密正文", "foreign-hash");
        foreignSource.setId(6L);
        when(sourceRepo.findById(6L)).thenReturn(Optional.of(foreignSource));

        assertThatThrownBy(() -> service.enqueueIngest(space.getId(), 6L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不属于指定知识库");
        verify(taskRepo, never()).save(any());
    }
    @Test
    void generatedPagesAreWrittenIntoReservedDirectoryAndRootSameSlugNodeIsUntouched() {
        WikiNode manualRoot = manualNode(null, "deploy-guide", "人工整理的正文");

        runIngest();

        assertThat(manualRoot.getMarkdownDraft()).contains("人工整理的正文");
        assertThat(manualRoot.getMarkdownDraft()).doesNotContain("LLM 生成正文");
        WikiNode directory = nodesById.values().stream()
                .filter(node -> GENERATED_SLUG.equals(node.getSlug())).findFirst().orElseThrow();
        assertThat(directory.getNodeType()).isEqualTo(WikiNodeType.DIRECTORY);
        assertThat(directory.getParentId()).isNull();
        WikiNode generated = nodesById.values().stream()
                .filter(node -> "deploy-guide".equals(node.getSlug()) && directory.getId().equals(node.getParentId()))
                .findFirst().orElseThrow();
        assertThat(generated.getMarkdownDraft()).contains("LLM 生成正文");
        assertThat(generated.frontmatter().llmGenerated()).isTrue();
        assertThat(reviewItems).isEmpty();
        verify(imageCaptions).caption(source, space, 7L, Map.of());
    }

    @Test
    void handEditedPageInsideDirectoryIsNotOverwritten() {
        WikiNode handEdited = manualNode(directory().getId(), "deploy-guide", "人工整理的正文");

        runIngest();

        assertThat(handEdited.getMarkdownDraft()).contains("人工整理的正文");
        assertThat(handEdited.getMarkdownDraft()).doesNotContain("LLM 生成正文");
        assertThat(reviewItems).singleElement().satisfies(item -> {
            assertThat(item.getItemType()).isEqualTo(WikiReviewItemType.FLAG);
            assertThat(item.getTitle()).isEqualTo("Deploy Guide");
            assertThat(item.getDescription()).contains("未写入");
        });
    }

    @Test
    void generatedPageEditedAfterPublishIsNotOverwrittenInsideDirectory() {
        WikiNode edited = existingNode(directory().getId(), "deploy-guide", "生成后又被人工改动",
                WikiFrontmatter.ORIGIN_LLM, "已发布正文");

        runIngest();

        assertThat(edited.getMarkdownDraft()).contains("生成后又被人工改动");
        assertThat(reviewItems).hasSize(1);
    }

    @Test
    void untouchedGeneratedPageInsideDirectoryIsRegenerated() {
        WikiNode generated = existingNode(directory().getId(), "deploy-guide", "已发布正文",
                WikiFrontmatter.ORIGIN_LLM, "已发布正文");

        runIngest();

        assertThat(generated.getMarkdownDraft()).contains("LLM 生成正文");
        assertThat(reviewItems).isEmpty();
    }

    @Test
    void llmGenChildNamedLlmGenDoesNotPoisonReservedDirectoryLookup() {
        WikiNode directory = directory();
        WikiNode child = WikiNode.page(100L, directory.getId(), "LLM gen", "llm-gen", 0);
        child.setId(nextNodeId++);
        child.applyGeneratedMarkdown(WikiFrontmatter.of("LLM gen", WikiPageType.CONCEPT, List.of(), List.of(),
                List.of(), "child page", "child body").withOrigin(WikiFrontmatter.ORIGIN_LLM).fullMarkdown());
        nodesById.put(child.getId(), child);

        runIngest();

        assertThat(nodesById.get(directory.getId()).getNodeType()).isEqualTo(WikiNodeType.DIRECTORY);
        assertThat(child.getParentId()).isEqualTo(directory.getId());
        assertThat(nodesById.values()).anySatisfy(node ->
                assertThat(node.getParentId()).isEqualTo(directory.getId()));
    }
    @Test
    void ingestRejectsReservedDirectoryHijackedByNonDirectoryNode() {
        manualNode(null, GENERATED_SLUG, "被手工页面占用的保留 slug");

        assertThatThrownBy(this::runIngest)
                .isInstanceOf(BizException.class)
                .hasMessageContaining(GENERATED_SLUG);
    }


    @Test
    void llmOutputCannotCreateSystemPageTypeUnderGeneratedDirectory() {
        AiGenerationGateway gateway = mock(AiGenerationGateway.class);
        when(gateway.generateStream(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            requests.add(invocation.getArgument(0));
            String json = requests.size() == 1
                    ? "{\"entities\":[],\"reviewItems\":[]}"
                    : "{\"pages\":[{\"title\":\"Bad Index\",\"type\":\"index\",\"summary\":\"x\",\"body\":\"x\"}],\"reviewItems\":[]}";
            return AiGenerationResult.of(json, List.of(), null);
        });
        when(aiGeneration.getObject()).thenReturn(gateway);

        runIngest();

        assertThat(nodesById.values()).noneMatch(node -> "bad-index".equals(node.getSlug()));
        assertThat(reviewItems).anySatisfy(item -> assertThat(item.getDescription()).contains("不允许由生成流程创建"));
    }
    @Test
    void legacyRootGeneratedPagesAreCountedAndLeftUnchanged() {
        WikiNode legacy = manualNode(null, "legacy-generated", "旧版根层级生成正文");
        legacy.setPageType(WikiPageType.CONCEPT);
        String before = legacy.getMarkdownDraft();

        runIngest();

        assertThat(legacy.getMarkdownDraft()).isEqualTo(before);
        assertThat(progressEvents).anySatisfy(event ->
                assertThat(event.message()).contains("未迁移"));
        WikiNode directory = nodesById.values().stream()
                .filter(node -> GENERATED_SLUG.equals(node.getSlug())).findFirst().orElseThrow();
        assertThat(nodesById.values().stream()
                .anyMatch(node -> directory.getId().equals(node.getParentId())
                        && "deploy-guide".equals(node.getSlug()))).isTrue();
    }

    @Test
    void manuallyEditedSourceDocumentPageIsNotOverwritten() {
        WikiNode sourceDocument = manualNode(null, "source-5", "人工补充的原文档说明");
        String before = sourceDocument.getMarkdownDraft();

        runIngest();

        assertThat(sourceDocument.getMarkdownDraft()).isEqualTo(before);
        assertThat(reviewItems).anySatisfy(item ->
                assertThat(item.getDescription()).contains("原文档页已被人工编辑"));
    }

    @Test
    void sourceDocumentAndGenerationContextContainImageCaptionEvidence() {
        runIngest();

        WikiNode document = nodesById.values().stream()
                .filter(node -> "source-5".equals(node.getSlug())).findFirst().orElseThrow();
        assertThat(document.getParentId()).isNull();
        assertThat(document.getMarkdownDraft())
                .contains("## 图片证据")
                .contains("第 2 页 图 1：架构图示例")
                .contains("资料正文");
        assertThat(requests.get(1).userPrompt()).contains("架构图示例");
    }

    private void runIngest() {
        WikiIngestTask task = WikiIngestTask.create(space.getId(), source.getId(), WikiIngestTaskType.INGEST, null, 1L);
        task.setId(9L);
        task.setRequestedByUserId(7L);
        service.run(task, progressEvents::add, () -> false);
    }

    /** 预置 llm-gen 保留目录（幂等：已存在则复用）。 */
    private WikiNode directory() {
        return nodesById.values().stream()
                .filter(node -> GENERATED_SLUG.equals(node.getSlug()))
                .findFirst()
                .orElseGet(() -> {
                    WikiNode directory = WikiNode.directory(100L, null, "LLM 生成", GENERATED_SLUG, 1);
                    directory.setId(nextNodeId++);
                    nodesById.put(directory.getId(), directory);
                    return directory;
                });
    }

    /** 人工页面：无生成来源标记。 */
    private WikiNode manualNode(Long parentId, String slug, String body) {
        WikiNode node = WikiNode.page(100L, parentId, titleFor(slug), slug, 0);
        node.setId(nextNodeId++);
        node.applyGeneratedMarkdown(WikiFrontmatter.of(titleFor(slug), WikiPageType.CONCEPT, List.of(), List.of(),
                List.of(), "人工摘要", body).fullMarkdown());
        nodesById.put(node.getId(), node);
        return node;
    }

    /** 已有页面：草稿按标记构建；publishedBody 与草稿正文一致时发布版本等于草稿，否则视为发布后有改动。 */
    private WikiNode existingNode(Long parentId, String slug, String body, String origin, String publishedBody) {
        WikiNode node = WikiNode.page(100L, parentId, titleFor(slug), slug, 0);
        node.setId(nextNodeId++);
        String draft = WikiFrontmatter.of(titleFor(slug), WikiPageType.CONCEPT, List.of(), List.of(), List.of(),
                        "摘要", body)
                .withOrigin(origin)
                .fullMarkdown();
        node.applyGeneratedMarkdown(draft);
        String published = publishedBody != null && publishedBody.equals(body) ? draft : publishedBody;
        WikiPageVersion version = WikiPageVersion.builder()
                .id(nextNodeId++)
                .nodeId(node.getId())
                .title(titleFor(slug))
                .markdown(published)
                .build();
        version.setSpaceId(100L);
        versionsById.put(version.getId(), version);
        node.setPublishedVersionId(version.getId());
        nodesById.put(node.getId(), node);
        return node;
    }

    private static String titleFor(String slug) {
        return "deploy-guide".equals(slug) ? "Deploy Guide" : slug;
    }

    private static WikiSpace space() {
        WikiSpace space = WikiSpace.create("Demo", "demo");
        space.setId(100L);
        space.setIngestProviderCode("openai");
        space.setIngestModelCode("gpt-4o-mini");
        return space;
    }

    private static WikiSource source() {
        WikiSource source = WikiSource.text(100L, "/", "原始资料", "资料正文", "hash-1");
        source.setId(5L);
        source.markExtracted("资料正文", List.of(new WikiSourceImage(555L, 2, 0, "架构图示例",
                WikiCaptionStatus.CAPTIONED, null, null, 640, 480, "image/png")));
        return source;
    }
}

package online.yudream.base.application.platform.wiki.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.wiki.assembler.WikiKnowledgeAssembler;
import online.yudream.base.application.platform.wiki.dto.WikiReviewItemDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.aggregate.WikiReviewItem;
import online.yudream.base.domain.platform.wiki.enumerate.WikiReviewStatus;
import online.yudream.base.domain.platform.wiki.repo.WikiReviewItemRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * 异步审阅：LLM 标记、人工判断、预定义操作（完成/忽略/触发深度研究）。
 */
@Service
@RequiredArgsConstructor
public class WikiReviewAppService {

    private final CapabilityAppService capabilities;
    private final WikiReviewItemRepo reviewItemRepo;
    private final WikiIngestAppService ingestAppService;

    @Transactional(readOnly = true)
    public List<WikiReviewItemDTO> list(Long spaceId) {
        enabled();
        return reviewItemRepo.findBySpaceId(spaceId).stream().map(WikiKnowledgeAssembler::reviewItem).toList();
    }

    @Transactional(readOnly = true)
    public List<WikiReviewItemDTO> pending(Long spaceId) {
        enabled();
        return reviewItemRepo.findBySpaceIdAndStatus(spaceId, WikiReviewStatus.PENDING).stream()
                .map(WikiKnowledgeAssembler::reviewItem).toList();
    }

    @Transactional
    public void resolve(Long id) {
        WikiReviewItem item = reviewItem(id);
        item.resolve();
        reviewItemRepo.save(item);
    }

    @Transactional
    public void dismiss(Long id) {
        WikiReviewItem item = reviewItem(id);
        item.dismiss();
        reviewItemRepo.save(item);
    }

    @Transactional
    public void execute(Long id, String action) {
        WikiReviewItem item = reviewItem(id);
        String normalized = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "deep_research", "research" -> {
                ingestAppService.enqueueResearch(item.getSpaceId(), item.getTitle(), item.getSearchQueries());
                item.resolve();
                reviewItemRepo.save(item);
            }
            case "dismiss", "skip" -> {
                item.dismiss();
                reviewItemRepo.save(item);
            }
            default -> {
                item.resolve();
                reviewItemRepo.save(item);
            }
        }
    }

    private WikiReviewItem reviewItem(Long id) {
        return reviewItemRepo.findById(id).orElseThrow(() -> new BizException("审阅项不存在"));
    }

    private void enabled() {
        capabilities.ensureEnabled("wiki", "Wiki 知识库");
    }
}

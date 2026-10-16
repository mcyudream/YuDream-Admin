package online.yudream.base.application.platform.feishu.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.feishu.assembler.FeishuAssembler;
import online.yudream.base.application.platform.feishu.cmd.FeishuBlockAppendCmd;
import online.yudream.base.application.platform.feishu.cmd.FeishuBlockDeleteCmd;
import online.yudream.base.application.platform.feishu.cmd.FeishuElementUpdateCmd;
import online.yudream.base.application.platform.feishu.dto.FeishuBlockWriteResultDTO;
import online.yudream.base.application.platform.feishu.dto.FeishuCloudDocPageDTO;
import online.yudream.base.application.platform.feishu.dto.FeishuDocumentBlockPageDTO;
import online.yudream.base.application.platform.feishu.dto.FeishuDocumentContentDTO;
import online.yudream.base.application.platform.feishu.dto.FeishuDocumentMetaDTO;
import online.yudream.base.application.platform.feishu.dto.FeishuWikiNodePageDTO;
import online.yudream.base.application.platform.feishu.dto.FeishuWikiSpacePageDTO;
import online.yudream.base.application.platform.feishu.query.FeishuDocumentBlockQuery;
import online.yudream.base.application.platform.feishu.query.FeishuDocumentQuery;
import online.yudream.base.application.platform.feishu.query.FeishuDriveFileQuery;
import online.yudream.base.application.platform.feishu.query.FeishuWikiNodeQuery;
import online.yudream.base.application.platform.feishu.query.FeishuWikiSpaceQuery;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.feishu.enumerate.FeishuQuotaScope;
import online.yudream.base.domain.platform.feishu.exception.FeishuApiException;
import online.yudream.base.domain.platform.feishu.repo.FeishuCallQuotaRepo;
import online.yudream.base.domain.platform.feishu.service.FeishuActorGateway;
import online.yudream.base.domain.platform.feishu.service.FeishuCloudDocGateway;
import online.yudream.base.domain.platform.feishu.valobj.FeishuQuotaPolicy;
import online.yudream.base.domain.platform.feishu.valobj.FeishuQuotaWindowKeys;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 飞书云文档用例编排。
 * <p>
 * 每个用例：能力应用闸门 → 会话身份解析 → 调用前原子预占额度 → 调用网关 → 失败按约定退还。
 * 额度按「每次云文档 API 请求」计数：一个用例对应一次飞书请求，分页由前端携带游标再次调用并再次计数，
 * 不存在绕过计数的隐藏子请求（tenant_access_token 获取属于认证，不占用云文档额度）。
 * <p>
 * 归属只取会话身份，命令对象里没有用户标识字段，前端无法伪造归属。
 */
@Service
@RequiredArgsConstructor
public class FeishuAppService {

    private final FeishuQuotaAppService quotas;
    private final FeishuActorGateway actors;
    private final FeishuCloudDocGateway cloudDocs;
    private final FeishuCallQuotaRepo quotaRepo;

    public FeishuCloudDocPageDTO driveFiles(FeishuDriveFileQuery query) {
        return consumed(userId -> FeishuAssembler.cloudDocPage(
                cloudDocs.listDriveFiles(query.folderToken(), query.pageToken(), query.pageSize())));
    }

    public FeishuWikiSpacePageDTO wikiSpaces(FeishuWikiSpaceQuery query) {
        return consumed(userId -> FeishuAssembler.wikiSpacePage(
                cloudDocs.listWikiSpaces(query.pageToken(), query.pageSize())));
    }

    public FeishuWikiNodePageDTO wikiNodes(FeishuWikiNodeQuery query) {
        return consumed(userId -> FeishuAssembler.wikiNodePage(query.spaceId(),
                cloudDocs.listWikiNodes(query.spaceId(), query.parentNodeToken(), query.pageToken(), query.pageSize())));
    }

    public FeishuDocumentMetaDTO documentMeta(FeishuDocumentQuery query) {
        return consumed(userId -> FeishuAssembler.documentMeta(cloudDocs.documentMeta(query.documentId())));
    }

    public FeishuDocumentContentDTO documentContent(FeishuDocumentQuery query) {
        return consumed(userId -> FeishuAssembler.documentContent(
                query.documentId(), cloudDocs.documentRawContent(query.documentId(), query.lang())));
    }

    public FeishuDocumentBlockPageDTO documentBlocks(FeishuDocumentBlockQuery query) {
        return consumed(userId -> FeishuAssembler.blockPage(query.documentId(),
                cloudDocs.documentBlocks(query.documentId(), query.pageToken(), query.pageSize())));
    }

    public FeishuBlockWriteResultDTO appendBlocks(FeishuBlockAppendCmd cmd) {
        return consumed(userId -> FeishuAssembler.writeResult(cloudDocs.appendDocumentBlocks(
                cmd.getDocumentId(), cmd.getParentBlockId(), cmd.getChildren(), cmd.getIndex())));
    }

    public void deleteBlocks(FeishuBlockDeleteCmd cmd) {
        consumed(userId -> {
            cloudDocs.deleteDocumentBlocks(cmd.getDocumentId(), cmd.getParentBlockId(), cmd.getStartIndex(), cmd.getEndIndex());
            return null;
        });
    }

    public FeishuBlockWriteResultDTO updateBlockElements(FeishuElementUpdateCmd cmd) {
        return consumed(userId -> FeishuAssembler.writeResult(cloudDocs.updateBlockElements(
                cmd.getDocumentId(), cmd.getBlockId(), cmd.getElements(), cmd.getDocumentRevisionId())));
    }

    /**
     * 预占 → 调用 → 失败按约定退还。
     * <p>
     * 退还约定：飞书限频（HTTP 429 / code 99991400）与鉴权、权限拒绝（401/403）视为未执行，退还额度；
     * 其他业务错误与网络异常无法证明未执行，保守计数不退还。
     */
    private <T> T consumed(java.util.function.Function<Long, T> call) {
        quotas.ensureEnabled();
        Long userId = actors.currentUserId();
        if (userId == null) {
            throw new BizException("当前用户未登录或账号不可用，无法使用飞书云文档");
        }
        FeishuQuotaPolicy policy = quotas.policy();
        FeishuQuotaWindowKeys windows = quotaRepo.reserve(userId, FeishuQuotaScope.CLOUD_DOC, Instant.now(), policy)
                .orElseThrow(() -> new BizException("飞书云文档调用额度已用完（每分钟 " + policy.perMinuteLimit()
                        + " 次、每日 " + policy.dailyLimit() + " 次），请稍后再试或联系管理员调整额度"));
        try {
            return call.apply(userId);
        }
        catch (FeishuApiException exception) {
            if (exception.refundable()) {
                quotaRepo.refund(userId, FeishuQuotaScope.CLOUD_DOC, windows);
            }
            throw exception;
        }
    }
}

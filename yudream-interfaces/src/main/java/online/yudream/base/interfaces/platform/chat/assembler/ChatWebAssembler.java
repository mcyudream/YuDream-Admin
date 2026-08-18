package online.yudream.base.interfaces.platform.chat.assembler;

import online.yudream.base.application.platform.chat.cmd.ChatQuotaConfigCmd;
import online.yudream.base.application.platform.chat.cmd.ChatSendCmd;
import online.yudream.base.application.platform.chat.cmd.ChatSessionSaveCmd;
import online.yudream.base.application.platform.chat.dto.ChatAttachmentDTO;
import online.yudream.base.application.platform.chat.dto.ChatMessageDTO;
import online.yudream.base.application.platform.chat.dto.ChatQuotaDTO;
import online.yudream.base.application.platform.chat.dto.ChatSendResultDTO;
import online.yudream.base.application.platform.chat.dto.ChatSessionDTO;
import online.yudream.base.domain.platform.ai.valobj.AiChatMessage;
import online.yudream.base.interfaces.platform.chat.request.ChatAttachmentRequest;
import online.yudream.base.interfaces.platform.chat.request.ChatQuotaConfigRequest;
import online.yudream.base.interfaces.platform.chat.request.ChatSendRequest;
import online.yudream.base.interfaces.platform.chat.request.ChatSessionSaveRequest;
import online.yudream.base.interfaces.platform.chat.res.ChatAttachmentRes;
import online.yudream.base.interfaces.platform.chat.res.ChatMessageRes;
import online.yudream.base.interfaces.platform.chat.res.ChatQuotaConfigRes;
import online.yudream.base.interfaces.platform.chat.res.ChatQuotaRes;
import online.yudream.base.interfaces.platform.chat.res.ChatSendResultRes;
import online.yudream.base.interfaces.platform.chat.res.ChatSessionRes;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport.SecurityPrincipal;

import java.util.List;

public final class ChatWebAssembler {

    private ChatWebAssembler() {
    }

    public static ChatSessionSaveCmd session(Long id, ChatSessionSaveRequest request) {
        ChatSessionSaveCmd cmd = new ChatSessionSaveCmd();
        cmd.setId(id);
        cmd.setTitle(request.getTitle());
        cmd.setScopeType(request.getScopeType());
        cmd.setAgentCode(request.getAgentCode());
        cmd.setSpaceSlug(request.getSpaceSlug());
        cmd.setProviderCode(request.getProviderCode());
        cmd.setModelCode(request.getModelCode());
        cmd.setPinned(request.getPinned());
        return cmd;
    }

    public static ChatSendCmd send(ChatSendRequest request) {
        return send(request, null);
    }

    public static ChatSendCmd send(ChatSendRequest request, SecurityPrincipal principal) {
        ChatSendCmd cmd = new ChatSendCmd();
        cmd.setSessionId(request.getSessionId());
        cmd.setScopeType(request.getScopeType());
        cmd.setAgentCode(request.getAgentCode());
        cmd.setSpaceSlug(request.getSpaceSlug());
        cmd.setProviderCode(request.getProviderCode());
        cmd.setModelCode(request.getModelCode());
        cmd.setContextRefs(request.getContextRefs() == null ? List.of() : request.getContextRefs().stream()
                                                                          .map(ref -> new online.yudream.base.application.platform.chat.dto.ChatContextRefDTO(
                                                                                  ref.getType(), ref.getTarget(), ref.getLabel()))
                                                                          .toList());
        cmd.setQuestion(request.getQuestion());
        cmd.setAttachments(request.getAttachments() == null ? List.of() : request.getAttachments().stream()
                                                                          .map(ChatWebAssembler::attachment)
                                                                          .toList());
        cmd.setHistory(request.getHistory() == null ? List.of() : request.getHistory().stream()
                                                                  .map(turn -> new AiChatMessage(turn.getRole(), turn.getContent()))
                                                                  .toList());
        if (principal != null) {
            cmd.setPermissionCodes(principal.permissions() == null
                    ? List.of()
                    : List.copyOf(principal.permissions()));
        }
        return cmd;
    }

    public static ChatQuotaConfigCmd quotaConfig(ChatQuotaConfigRequest request) {
        ChatQuotaConfigCmd cmd = new ChatQuotaConfigCmd();
        cmd.setDailyTokenLimit(request.getDailyTokenLimit());
        return cmd;
    }

    public static ChatSessionRes session(ChatSessionDTO dto) {
        if (dto == null) {
            return null;
        }
        return ChatSessionRes.builder()
                .id(dto.id())
                .userId(dto.userId())
                .title(dto.title())
                .scopeType(dto.scopeType())
                .agentCode(dto.agentCode())
                .spaceSlug(dto.spaceSlug())
                .providerCode(dto.providerCode())
                .modelCode(dto.modelCode())
                .messageCount(dto.messageCount())
                .pinned(dto.pinned())
                .lastMessageAt(dto.lastMessageAt())
                .createTime(dto.createTime())
                .build();
    }

    public static ChatMessageRes message(ChatMessageDTO dto) {
        if (dto == null) {
            return null;
        }
        return ChatMessageRes.builder()
                .id(dto.id())
                .sessionId(dto.sessionId())
                .userId(dto.userId())
                .role(dto.role())
                .content(dto.content())
                .reasoning(dto.reasoning())
                .citations(dto.citations())
                .tools(dto.tools())
                .activities(dto.activities())
                .attachments(dto.attachments())
                .usage(dto.usage())
                .status(dto.status())
                .errorMessage(dto.errorMessage())
                .createTime(dto.createTime())
                .build();
    }

    public static ChatQuotaRes quota(ChatQuotaDTO dto) {
        if (dto == null) {
            return null;
        }
        return ChatQuotaRes.builder()
                .userId(dto.userId())
                .usageDate(dto.usageDate())
                .usedTokens(dto.usedTokens())
                .limitTokens(dto.limitTokens())
                .remainingTokens(dto.remainingTokens())
                .build();
    }

    public static ChatQuotaConfigRes quotaConfig(long dailyTokenLimit) {
        return ChatQuotaConfigRes.builder().dailyTokenLimit(dailyTokenLimit).build();
    }

    public static ChatSendResultRes sendResult(ChatSendResultDTO dto) {
        if (dto == null) {
            return null;
        }
        return ChatSendResultRes.builder()
                .sessionId(dto.sessionId())
                .userMessageId(dto.userMessageId())
                .assistantMessageId(dto.assistantMessageId())
                .content(dto.content())
                .citations(dto.citations())
                .tools(dto.tools())
                .activities(dto.activities())
                .usage(dto.usage())
                .usedTokens(dto.usedTokens())
                .limitTokens(dto.limitTokens())
                .remainingTokens(dto.remainingTokens())
                .build();
    }

    public static ChatAttachmentRes attachment(ChatAttachmentDTO dto) {
        if (dto == null) {
            return null;
        }
        return ChatAttachmentRes.builder()
                .fileId(dto.fileId())
                .fileName(dto.fileName())
                .contentType(dto.contentType())
                .size(dto.size())
                .kind(dto.kind())
                .url(dto.url())
                .extractedText(dto.extractedText())
                .dataUrl(dto.dataUrl())
                .build();
    }

    private static ChatAttachmentDTO attachment(ChatAttachmentRequest request) {
        return new ChatAttachmentDTO(
                request.getFileId(),
                request.getFileName(),
                request.getContentType(),
                request.getSize(),
                request.getKind(),
                request.getUrl(),
                request.getExtractedText(),
                request.getDataUrl());
    }
}

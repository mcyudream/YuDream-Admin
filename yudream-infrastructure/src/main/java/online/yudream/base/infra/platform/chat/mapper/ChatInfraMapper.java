package online.yudream.base.infra.platform.chat.mapper;

import online.yudream.base.domain.platform.chat.aggregate.ChatMessage;
import online.yudream.base.domain.platform.chat.aggregate.ChatSession;
import online.yudream.base.domain.platform.chat.aggregate.UserChatQuota;
import online.yudream.base.infra.common.baseobj.BaseDO;
import online.yudream.base.infra.platform.chat.dataobj.ChatMessageDO;
import online.yudream.base.infra.platform.chat.dataobj.ChatSessionDO;
import online.yudream.base.infra.platform.chat.dataobj.UserChatQuotaDO;

import java.util.ArrayList;

public final class ChatInfraMapper {

    private ChatInfraMapper() {
    }

    public static ChatSession session(ChatSessionDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        return ChatSession.builder()
                .id(dataObj.getId())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .userId(dataObj.getUserId())
                .title(dataObj.getTitle())
                .scopeType(dataObj.getScopeType())
                .agentCode(dataObj.getAgentCode())
                .spaceSlug(dataObj.getSpaceSlug())
                .providerCode(dataObj.getProviderCode())
                .modelCode(dataObj.getModelCode())
                .messageCount(dataObj.getMessageCount())
                .pinned(dataObj.isPinned())
                .lastMessageAt(dataObj.getLastMessageAt())
                .build();
    }

    public static ChatSessionDO session(ChatSession domain) {
        if (domain == null) {
            return null;
        }
        ChatSessionDO dataObj = new ChatSessionDO();
        copyBase(domain, dataObj);
        dataObj.setUserId(domain.getUserId());
        dataObj.setTitle(domain.getTitle());
        dataObj.setScopeType(domain.getScopeType());
        dataObj.setAgentCode(domain.getAgentCode());
        dataObj.setSpaceSlug(domain.getSpaceSlug());
        dataObj.setProviderCode(domain.getProviderCode());
        dataObj.setModelCode(domain.getModelCode());
        dataObj.setMessageCount(domain.getMessageCount());
        dataObj.setPinned(domain.isPinned());
        dataObj.setLastMessageAt(domain.getLastMessageAt());
        return dataObj;
    }

    public static ChatMessage message(ChatMessageDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        return ChatMessage.builder()
                .id(dataObj.getId())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .sessionId(dataObj.getSessionId())
                .userId(dataObj.getUserId())
                .role(dataObj.getRole())
                .content(dataObj.getContent())
                .reasoning(dataObj.getReasoning())
                .citations(dataObj.getCitations() == null ? new ArrayList<>() : dataObj.getCitations())
                .tools(dataObj.getTools() == null ? new ArrayList<>() : dataObj.getTools())
                .activities(dataObj.getActivities() == null ? new ArrayList<>() : dataObj.getActivities())
                .attachments(dataObj.getAttachments() == null ? new ArrayList<>() : dataObj.getAttachments())
                .usage(dataObj.getUsage())
                .status(dataObj.getStatus())
                .errorMessage(dataObj.getErrorMessage())
                .build();
    }

    public static ChatMessageDO message(ChatMessage domain) {
        if (domain == null) {
            return null;
        }
        ChatMessageDO dataObj = new ChatMessageDO();
        copyBase(domain, dataObj);
        dataObj.setSessionId(domain.getSessionId());
        dataObj.setUserId(domain.getUserId());
        dataObj.setRole(domain.getRole());
        dataObj.setContent(domain.getContent());
        dataObj.setReasoning(domain.getReasoning());
        dataObj.setCitations(domain.getCitations() == null ? new ArrayList<>() : new ArrayList<>(domain.getCitations()));
        dataObj.setTools(domain.getTools() == null ? new ArrayList<>() : new ArrayList<>(domain.getTools()));
        dataObj.setActivities(domain.getActivities() == null ? new ArrayList<>() : new ArrayList<>(domain.getActivities()));
        dataObj.setAttachments(domain.getAttachments() == null ? new ArrayList<>() : new ArrayList<>(domain.getAttachments()));
        dataObj.setUsage(domain.getUsage());
        dataObj.setStatus(domain.getStatus());
        dataObj.setErrorMessage(domain.getErrorMessage());
        return dataObj;
    }

    public static UserChatQuota quota(UserChatQuotaDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        return UserChatQuota.builder()
                .id(dataObj.getId())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .userId(dataObj.getUserId())
                .usageDate(dataObj.getUsageDate())
                .usedTokens(dataObj.getUsedTokens())
                .limitTokens(dataObj.getLimitTokens())
                .build();
    }

    public static UserChatQuotaDO quota(UserChatQuota domain) {
        if (domain == null) {
            return null;
        }
        UserChatQuotaDO dataObj = new UserChatQuotaDO();
        copyBase(domain, dataObj);
        dataObj.setUserId(domain.getUserId());
        dataObj.setUsageDate(domain.getUsageDate());
        dataObj.setUsedTokens(domain.getUsedTokens());
        dataObj.setLimitTokens(domain.getLimitTokens());
        return dataObj;
    }

    private static void copyBase(online.yudream.base.domain.common.base.BaseDomain source, BaseDO target) {
        target.setId(source.getId());
        target.setVersion(source.getVersion());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
    }
}

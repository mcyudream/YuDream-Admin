package online.yudream.base.application.platform.milky.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.milky.assembler.MilkyConnectionAssembler;
import online.yudream.base.application.platform.milky.cmd.MilkyConnectionCreateCmd;
import online.yudream.base.application.platform.milky.cmd.MilkyConnectionUpdateCmd;
import online.yudream.base.application.platform.milky.dto.MilkyConnectionDTO;
import online.yudream.base.application.system.user.dto.MessagingBindingCodeDTO;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.milky.aggregate.MilkyConnection;
import online.yudream.base.domain.platform.milky.repo.MilkyConnectionRepo;
import online.yudream.base.domain.platform.milky.service.MilkyApiGateway;
import online.yudream.base.domain.platform.milky.service.MilkyEventGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class MilkyConnectionAppService {
    private final MilkyConnectionRepo connectionRepo;
    private final CapabilityAppService capabilityAppService;
    private final MilkyEventGateway eventGateway;
    private final MilkyApiGateway apiGateway;
    private final OfficialQqBotCommandMenuAppService officialCommandMenuAppService;
    private final SecureRandom mentionBindingRandom = new SecureRandom();
    private final Map<String, MentionBindingEntry> mentionBindingCodes = new ConcurrentHashMap<>();

    /**
     * 生成机器人提及回填码：管理员在目标群 @机器人 发送「/绑定机器人 码」，
     * 宿主从该消息内容的提及标签回填机器人在群内的 openid。码短时一次性，本身即授权。
     */
    @Transactional(readOnly = true)
    public MessagingBindingCodeDTO issueMentionBindingCode(Long id) {
        ready();
        MilkyConnection connection = item(id);
        if (!connection.official()) {
            throw new BizException("仅官方 QQ 机器人连接需要回填提及身份");
        }
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(15));
        String code;
        do {
            code = String.format("%06d", mentionBindingRandom.nextInt(1_000_000));
        } while (mentionBindingCodes.putIfAbsent(code, new MentionBindingEntry(id, expiresAt)) != null);
        mentionBindingCodes.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(Instant.now()));
        return MessagingBindingCodeDTO.builder()
                .code(code)
                .expiresAt(expiresAt)
                .connectionId(String.valueOf(id))
                .connectionName(connection.getName())
                .protocol(connection.protocolCode())
                .build();
    }

    /** 校验并消费回填码；码与连接一一对应，防止拿到码去其他连接回填。 */
    public void consumeMentionBindingCode(String code, Long connectionId) {
        if (code == null || !code.trim().matches("\\d{6}")) {
            throw new BizException("回填码无效");
        }
        MentionBindingEntry entry = mentionBindingCodes.remove(code.trim());
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            throw new BizException("回填码已过期或已使用");
        }
        if (!entry.connectionId().equals(connectionId)) {
            throw new BizException("回填码与当前连接不匹配");
        }
    }

    private record MentionBindingEntry(Long connectionId, Instant expiresAt) { }

    @Transactional
    public MilkyConnectionDTO create(MilkyConnectionCreateCmd cmd) {
        ready();
        return MilkyConnectionAssembler.toDTO(connectionRepo.save(MilkyConnection.create(
                cmd.getName(), cmd.getProtocol(), cmd.getBaseUrl(), cmd.getToken(),
                cmd.getAppId(), cmd.getAppSecret(), cmd.getSandbox(), cmd.getIntents(),
                cmd.getCommandMenuImageMode(), cmd.getCommandMenuPublicBaseUrl(), cmd.getMentionOpenIds())));
    }

    @Transactional
    public MilkyConnectionDTO update(MilkyConnectionUpdateCmd cmd) {
        ready();
        MilkyConnection connection = item(cmd.getId());
        connection.update(cmd.getName(), cmd.getProtocol(), cmd.getBaseUrl(), cmd.getToken(),
                cmd.getAppId(), cmd.getAppSecret(), cmd.getSandbox(), cmd.getIntents(),
                cmd.getCommandMenuImageMode(), cmd.getCommandMenuPublicBaseUrl(), cmd.getMentionOpenIds());
        return MilkyConnectionAssembler.toDTO(connectionRepo.save(connection));
    }

    @Transactional(readOnly = true)
    public PageResult<MilkyConnectionDTO> page(String keyword, int page, int size) {
        ready();
        return MilkyConnectionAssembler.toDTO(connectionRepo.page(keyword, page, size));
    }

    @Transactional
    public MilkyConnectionDTO enable(Long id) {
        ready();
        MilkyConnection connection = item(id);
        connection.setEnabled(true);
        MilkyConnection saved = connectionRepo.save(connection);
        eventGateway.connect(saved.getId());
        if (saved.official()) {
            officialCommandMenuAppService.prefetchRemoteSnapshots();
            officialCommandMenuAppService.requestSync();
        }
        return MilkyConnectionAssembler.toDTO(saved);
    }

    @Transactional
    public MilkyConnectionDTO disable(Long id) {
        ready();
        MilkyConnection connection = item(id);
        connection.setEnabled(false);
        MilkyConnection saved = connectionRepo.save(connection);
        eventGateway.close(saved.getId());
        return MilkyConnectionAssembler.toDTO(saved);
    }

    @Transactional(readOnly = true)
    public Object test(Long id) {
        ready();
        MilkyConnection connection = item(id);
        return apiGateway.invoke(connection.toApiContext(), "get_login_info", Map.of());
    }

    private MilkyConnection item(Long id) {
        return connectionRepo.findById(id).orElseThrow(() -> new BizException("Milky 连接不存在"));
    }

    private void ready() {
        capabilityAppService.ensureEnabled("milky", "QQ 消息平台");
    }
}

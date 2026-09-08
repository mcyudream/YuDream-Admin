package online.yudream.base.application.system.user.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.system.user.assembler.UserAssembler;
import online.yudream.base.application.system.user.dto.MessagingBindingCodeDTO;
import online.yudream.base.application.system.user.dto.MessagingBindingTargetDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.milky.aggregate.MilkyConnection;
import online.yudream.base.domain.platform.milky.enumerate.MilkyConnectionProtocol;
import online.yudream.base.domain.platform.milky.repo.MilkyConnectionRepo;
import online.yudream.base.domain.platform.milky.service.MessagingBotNameLookup;
import online.yudream.base.domain.system.user.aggregate.MessagingIdentity;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.enumerate.MessagingIdentityType;
import online.yudream.base.domain.system.user.repo.MessagingIdentityRepo;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.domain.system.user.service.MessagingIdentityClassifier;
import online.yudream.base.domain.valobj.QQ;
import online.yudream.base.plugin.spi.system.user.PluginMessagingIdentity;
import online.yudream.base.plugin.spi.system.user.PluginQqBindingCode;
import online.yudream.base.plugin.spi.system.user.PluginQqBindingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessagingIdentityAppService {

    private final MessagingIdentityRepo identityRepo;
    private final UserRepo userRepo;
    private final MilkyConnectionRepo connectionRepo;
    private final CapabilityAppService capabilityAppService;
    private final PluginQqBindingService pluginQqBindingService;
    private final MessagingBotNameLookup botNameLookup;

    @Transactional(readOnly = true)
    public Optional<User> findUser(MessagingIdentityClassifier.Classification classification) {
        return findIdentity(classification).flatMap(identity -> userRepo.findById(identity.getUserId()));
    }

    @Transactional(readOnly = true)
    public Optional<User> findUserByEvent(MilkyConnection connection, String scene, String identity, String groupOpenid) {
        if (!StringUtils.hasText(identity)) {
            return Optional.empty();
        }
        MessagingIdentityClassifier.Classification classification = classifyEvent(connection, scene, identity, groupOpenid);
        Optional<User> matched = findUser(classification);
        if (matched.isPresent()) {
            return matched;
        }
        if (classification.identityType().milkyQq()) {
            return userRepo.findByQQ(classification.identity());
        }
        return findOfficialIdentityOnConnection(connection, classification.identity())
                .flatMap(item -> userRepo.findById(item.getUserId()));
    }

    @Transactional(readOnly = true)
    public Optional<User> findUserByRawIdentity(String identity) {
        return findUserByRawIdentity(identity, null);
    }

    @Transactional(readOnly = true)
    public Optional<User> findUserByRawIdentity(String identity, String appId) {
        if (!StringUtils.hasText(identity)) {
            return Optional.empty();
        }
        List<MessagingIdentity> identities = identityRepo.findByIdentity(identity.trim());
        if (identities.isEmpty()) {
            return userRepo.findByQQ(identity.trim());
        }
        if (StringUtils.hasText(appId)) {
            List<MessagingIdentity> scoped = identities.stream()
                    .filter(item -> !StringUtils.hasText(item.getAppId()) || appId.trim().equals(item.getAppId()))
                    .toList();
            if (!scoped.isEmpty()) {
                identities = scoped;
            }
        }
        Long userId = identities.getFirst().getUserId();
        boolean uniqueUser = identities.stream().allMatch(item -> userId.equals(item.getUserId()));
        if (!uniqueUser) {
            return Optional.empty();
        }
        return userRepo.findById(userId);
    }

    @Transactional(readOnly = true)
    public Optional<MessagingIdentity> findIdentity(MessagingIdentityClassifier.Classification classification) {
        if (classification == null || !StringUtils.hasText(classification.identity())) {
            return Optional.empty();
        }
        Optional<MessagingIdentity> exact = identityRepo.findExact(classification.protocol(), classification.identityType(),
                classification.appId(), classification.groupOpenid(), classification.identity());
        if (exact.isPresent()) {
            return exact;
        }
        if (!classification.identityType().milkyQq() && StringUtils.hasText(classification.appId())) {
            return identityRepo.findExact(classification.protocol(), classification.identityType(),
                    null, classification.groupOpenid(), classification.identity());
        }
        return Optional.empty();
    }

    @Transactional
    public List<MessagingIdentity> listByUser(Long userId) {
        if (userId == null) {
            return List.of();
        }
        compactOfficialIdentities(userId);
        return identityRepo.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Map<Long, List<MessagingIdentity>> listByUsers(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return identityRepo.findByUserIds(userIds).stream()
                .collect(Collectors.groupingBy(MessagingIdentity::getUserId));
    }

    @Transactional(readOnly = true)
    public Optional<String> privatePeerId(Long userId, MilkyConnection connection) {
        if (userId == null || connection == null) {
            return Optional.empty();
        }
        if (connection.official()) {
            return identityRepo.findByUserId(userId).stream()
                    .filter(identity -> identity.getIdentityType() != null && !identity.getIdentityType().milkyQq())
                    .filter(identity -> sameApp(identity.getAppId(), connection.getAppId())
                            || (connection.getId() != null && connection.getId().equals(identity.getConnectionId())))
                    .sorted(this::preferPrivateOfficial)
                    .map(MessagingIdentity::getIdentity)
                    .findFirst();
        }
        return identityRepo.findByUserId(userId).stream()
                .filter(identity -> identity.getIdentityType() == MessagingIdentityType.QQ)
                .map(MessagingIdentity::getIdentity)
                .findFirst()
                .or(() -> userRepo.findById(userId)
                        .map(User::getQq)
                        .map(QQ::getValue)
                        .filter(StringUtils::hasText));
    }

    @Transactional
    public MessagingIdentity bindOnce(Long userId, MessagingIdentityClassifier.Classification classification, Long connectionId) {
        if (userId == null || classification == null || !StringUtils.hasText(classification.identity())) {
            throw new BizException("用户和消息身份不能为空");
        }
        User user = userRepo.findById(userId).orElseThrow(() -> new BizException("用户不存在"));
        Optional<MessagingIdentity> existing = findIdentity(classification);
        if (existing.isPresent()) {
            MessagingIdentity current = existing.get();
            if (!current.ownedBy(userId)) {
                throw new BizException("该消息身份已被其他用户绑定");
            }
            boolean dirty = false;
            if (connectionId != null && current.getConnectionId() == null) {
                current.setConnectionId(connectionId);
                dirty = true;
            }
            if (StringUtils.hasText(classification.appId()) && !StringUtils.hasText(current.getAppId())) {
                current.setAppId(classification.appId());
                dirty = true;
            }
            MessagingIdentity saved = dirty ? identityRepo.save(current) : current;
            if (!classification.identityType().milkyQq()) {
                compactOfficialIdentities(userId, saved.getId(),
                        connectionId != null ? connectionId : saved.getConnectionId(),
                        firstText(classification.appId(), saved.getAppId()));
            }
            return saved;
        }
        if (classification.identityType().milkyQq()) {
            String qq = classification.identity();
            if (user.getQq() != null && StringUtils.hasText(user.getQq().getValue())
                    && !qq.equals(user.getQq().getValue())) {
                throw new BizException("系统 QQ 已绑定，不能重复绑定");
            }
            if (userRepo.existsByQQExcludeId(qq, userId)) {
                throw new BizException("QQ 已被其他用户绑定");
            }
            if (user.getQq() == null || !StringUtils.hasText(user.getQq().getValue())) {
                user.updateProfile(user.getNickname(), user.getEmail(), user.getPhone(), QQ.of(qq), null);
                userRepo.save(user);
            }
        }
        MessagingIdentity created = MessagingIdentity.bind(userId, classification.protocol(), connectionId,
                classification.appId(), classification.identityType(), classification.identity(), classification.groupOpenid());
        MessagingIdentity saved = identityRepo.save(created);
        if (!classification.identityType().milkyQq()) {
            compactOfficialIdentities(userId, saved.getId(), connectionId, classification.appId());
        }
        return saved;
    }

    @Transactional
    public MessagingIdentity bindFromScopeOrLegacy(Long userId, String rawIdentity) {
        MessagingIdentityBindScope.Context context = MessagingIdentityBindScope.current();
        if (context != null) {
            MessagingIdentityClassifier.Classification classification = MessagingIdentityClassifier.classifyEvent(
                    context.protocol(), context.scene(),
                    StringUtils.hasText(rawIdentity) ? rawIdentity : context.identity(),
                    context.groupOpenid(), context.appId());
            return bindOnce(userId, classification, context.connectionId());
        }
        MessagingIdentityClassifier.Classification classification = MessagingIdentityClassifier.classifyStoredQq(rawIdentity);
        return bindOnce(userId, classification, null);
    }

    @Transactional
    public MessagingIdentity bindSpiIdentity(Long userId, PluginMessagingIdentity identity) {
        if (identity == null || !StringUtils.hasText(identity.identity())) {
            throw new BizException("用户和消息身份不能为空");
        }
        MessagingIdentityBindScope.Context context = MessagingIdentityBindScope.current();
        MilkyConnectionProtocol protocol = identity.protocol() == null || identity.protocol().isBlank()
                ? (context == null ? MilkyConnectionProtocol.MILKY : context.protocol())
                : MilkyConnectionProtocol.from(identity.protocol());
        String scene = identity.identityType() == null || identity.identityType().isBlank()
                ? (context == null ? null : context.scene())
                : sceneHint(identity.identityType());
        String appId = firstText(identity.appId(), context == null ? null : context.appId());
        String groupOpenid = firstText(identity.groupOpenid(), context == null ? null : context.groupOpenid());
        Long connectionId = parseConnectionId(identity.connectionId(), context == null ? null : context.connectionId());
        MessagingIdentityClassifier.Classification classification = MessagingIdentityClassifier.classifyEvent(
                protocol, scene, identity.identity(), groupOpenid, appId);
        return bindOnce(userId, classification, connectionId);
    }

    @Transactional
    public void syncMilkyQq(Long userId, String qq) {
        if (userId == null) {
            return;
        }
        identityRepo.deleteMilkyQqByUserId(userId);
        if (!MessagingIdentityClassifier.milkyQqNumber(qq)) {
            return;
        }
        MessagingIdentityClassifier.Classification classification = MessagingIdentityClassifier.classifyStoredQq(qq);
        if (findIdentity(classification).filter(identity -> identity.ownedBy(userId)).isPresent()) {
            return;
        }
        identityRepo.save(MessagingIdentity.bind(userId, classification.protocol(), null, null,
                classification.identityType(), classification.identity(), null));
    }

    @Transactional(readOnly = true)
    public List<MessagingBindingTargetDTO> listBindingTargets(Long userId) {
        if (!capabilityAppService.enabled("milky")) {
            return List.of();
        }
        List<MessagingIdentity> identities = listByUser(userId);
        return connectionRepo.findEnabled().stream()
                .map(connection -> UserAssembler.toBindingTargetDTO(
                        connection,
                        botNameLookup.botName(connection.getId()).orElse(null),
                        boundTo(identities, connection)))
                .toList();
    }

    @Transactional(readOnly = true)
    public MessagingBindingCodeDTO issueBindingCode(Long userId, String connectionId) {
        userRepo.findById(userId).orElseThrow(() -> new BizException("用户不存在"));
        MessagingBindingTargetDTO target = requireEnabledTarget(connectionId);
        PluginQqBindingCode issued = pluginQqBindingService.issue(userId);
        return UserAssembler.toBindingCodeDTO(issued.code(), issued.expiresAt(), target);
    }

    public MessagingIdentityClassifier.Classification classifyEvent(MilkyConnection connection, String scene,
                                                                     String identity, String groupOpenid) {
        MilkyConnectionProtocol protocol = connection == null ? MilkyConnectionProtocol.MILKY : connection.protocolOrDefault();
        String appId = connection == null || !connection.official() ? null : connection.getAppId();
        return MessagingIdentityClassifier.classifyEvent(protocol, scene, identity, groupOpenid, appId);
    }

    public Optional<MilkyConnection> findConnection(Long connectionId) {
        if (connectionId == null) {
            return Optional.empty();
        }
        return connectionRepo.findById(connectionId);
    }

    public PluginMessagingIdentity toPlugin(MessagingIdentity identity) {
        if (identity == null) {
            return null;
        }
        return new PluginMessagingIdentity(
                identity.getProtocol() == null ? null : identity.getProtocol().code(),
                identity.getIdentityType() == null ? null : identity.getIdentityType().code(),
                identity.getIdentity(),
                identity.getAppId(),
                identity.getGroupOpenid(),
                identity.getConnectionId() == null ? null : String.valueOf(identity.getConnectionId())
        );
    }

    private MessagingBindingTargetDTO requireEnabledTarget(String connectionId) {
        if (!capabilityAppService.enabled("milky")) {
            throw new BizException("QQ 消息平台未启用");
        }
        Long id = parseConnectionId(connectionId, null);
        if (id == null) {
            throw new BizException("请选择要绑定的机器人连接");
        }
        MilkyConnection connection = connectionRepo.findById(id)
                .orElseThrow(() -> new BizException("消息连接不存在"));
        if (!connection.isEnabled()) {
            throw new BizException("该机器人连接未启用");
        }
        return UserAssembler.toBindingTargetDTO(connection, botNameLookup.botName(connection.getId()).orElse(null), false);
    }

    private static boolean boundTo(List<MessagingIdentity> identities, MilkyConnection connection) {
        if (identities == null || identities.isEmpty() || connection == null || connection.getId() == null) {
            return false;
        }
        Long connectionId = connection.getId();
        if (identities.stream().anyMatch(identity -> connectionId.equals(identity.getConnectionId()))) {
            return true;
        }
        if (connection.official()) {
            String appId = connection.getAppId();
            return identities.stream().anyMatch(identity ->
                    identity.getIdentityType() != null
                            && !identity.getIdentityType().milkyQq()
                            && sameApp(identity.getAppId(), appId));
        }
        return identities.stream().anyMatch(identity ->
                identity.getIdentityType() != null && identity.getIdentityType().milkyQq());
    }

    private void compactOfficialIdentities(Long userId) {
        compactOfficialIdentities(userId, null, null, null);
    }

    private void compactOfficialIdentities(Long userId, Long keepId, Long connectionId, String appId) {
        if (userId == null) {
            return;
        }
        List<MessagingIdentity> identities = identityRepo.findByUserId(userId).stream()
                .filter(identity -> identity.getIdentityType() != null && !identity.getIdentityType().milkyQq())
                .toList();
        if (identities.isEmpty()) {
            return;
        }
        Map<String, List<MessagingIdentity>> grouped = identities.stream()
                .collect(Collectors.groupingBy(this::officialSlotKey));
        for (List<MessagingIdentity> group : grouped.values()) {
            compactOfficialSlot(group, keepId, connectionId, appId);
        }
    }

    private void compactOfficialSlot(List<MessagingIdentity> group, Long keepId, Long connectionId, String appId) {
        if (group.size() <= 1) {
            return;
        }
        MessagingIdentity keeper = selectOfficialKeeper(group, keepId, connectionId, appId);
        for (MessagingIdentity identity : group) {
            if (!identity.getId().equals(keeper.getId())) {
                identityRepo.deleteById(identity.getId());
            }
        }
    }

    private MessagingIdentity selectOfficialKeeper(List<MessagingIdentity> group, Long keepId,
                                                   Long connectionId, String appId) {
        return group.stream()
                .min((left, right) -> {
                    int keepRank = rankKeep(right, keepId) - rankKeep(left, keepId);
                    if (keepRank != 0) {
                        return keepRank;
                    }
                    int connectionRank = rankConnection(right, connectionId) - rankConnection(left, connectionId);
                    if (connectionRank != 0) {
                        return connectionRank;
                    }
                    int appRank = rankApp(right, appId) - rankApp(left, appId);
                    if (appRank != 0) {
                        return appRank;
                    }
                    return preferPrivateOfficial(left, right);
                })
                .orElse(group.getFirst());
    }

    private Optional<MessagingIdentity> findOfficialIdentityOnConnection(MilkyConnection connection, String identity) {
        if (connection == null || !connection.official() || !StringUtils.hasText(identity)) {
            return Optional.empty();
        }
        String openid = identity.trim();
        return identityRepo.findByIdentity(openid).stream()
                .filter(item -> item.getIdentityType() != null && !item.getIdentityType().milkyQq())
                .filter(item -> (connection.getId() != null && connection.getId().equals(item.getConnectionId()))
                        || sameApp(item.getAppId(), connection.getAppId()))
                .findFirst();
    }

    private String officialSlotKey(MessagingIdentity identity) {
        if (identity.getConnectionId() != null) {
            return "c:" + identity.getConnectionId();
        }
        if (StringUtils.hasText(identity.getAppId())) {
            return "a:" + identity.getAppId().trim();
        }
        return "u:" + identity.getUserId();
    }

    private int preferPrivateOfficial(MessagingIdentity left, MessagingIdentity right) {
        return Integer.compare(officialPriority(left), officialPriority(right));
    }

    private static int officialPriority(MessagingIdentity identity) {
        if (identity.getIdentityType() == MessagingIdentityType.USER_OPENID) {
            return 0;
        }
        return 1;
    }

    private static int rankKeep(MessagingIdentity identity, Long keepId) {
        return keepId != null && keepId.equals(identity.getId()) ? 1 : 0;
    }

    private static int rankConnection(MessagingIdentity identity, Long connectionId) {
        return connectionId != null && connectionId.equals(identity.getConnectionId()) ? 1 : 0;
    }

    private static int rankApp(MessagingIdentity identity, String appId) {
        return StringUtils.hasText(appId) && sameApp(identity.getAppId(), appId) ? 1 : 0;
    }

    private static boolean sameApp(String left, String right) {
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return !StringUtils.hasText(left) && !StringUtils.hasText(right);
        }
        return left.trim().equals(right.trim());
    }

    private static String sceneHint(String identityType) {
        try {
            return MessagingIdentityType.from(identityType).officialMemberOpenid() ? "group" : "friend";
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String firstText(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary.trim() : (StringUtils.hasText(fallback) ? fallback.trim() : null);
    }

    private static Long parseConnectionId(String value, Long fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}

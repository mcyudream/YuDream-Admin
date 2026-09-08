package online.yudream.base.infra.platform.milky.official;

import online.yudream.base.domain.platform.milky.service.MessagingBotNameLookup;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 官方机器人会话侧缓存：被动回复所需的最近 msg_id、群列表、同 msg_id 递增的 msg_seq，
 * 以及事件流落地的本地历史（官方 OpenAPI 不提供历史拉取）。
 */
@Component
public class OfficialQqBotSessionStore implements MessagingBotNameLookup {
    static final int MAX_HISTORY = 200;

    private final Map<Long, Map<String, Group>> groups = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, Friend>> friends = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, Guild>> guilds = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, Map<String, Member>>> members = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, LastInbound>> lastInbound = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, AtomicInteger>> sequences = new ConcurrentHashMap<>();
    private final Map<Long, String> selfIds = new ConcurrentHashMap<>();
    private final Map<Long, String> selfNames = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, Deque<Map<String, Object>>>> histories = new ConcurrentHashMap<>();

    public void rememberSelf(Long connectionId, String selfId) {
        rememberSelf(connectionId, selfId, null);
    }

    public void rememberSelf(Long connectionId, String selfId, String selfName) {
        if (connectionId != null && selfId != null && !selfId.isBlank()) {
            selfIds.put(connectionId, selfId);
        }
        if (connectionId != null && selfName != null && !selfName.isBlank()) {
            selfNames.put(connectionId, selfName.trim());
        }
    }

    @Override
    public Optional<String> botName(Long connectionId) {
        String name = selfName(connectionId);
        return name == null || name.isBlank() ? Optional.empty() : Optional.of(name);
    }

    public String selfId(Long connectionId) {
        return connectionId == null ? null : selfIds.get(connectionId);
    }

    public String selfName(Long connectionId) {
        return connectionId == null ? null : selfNames.get(connectionId);
    }

    public void rememberGroup(Long connectionId, String groupOpenId, String groupName) {
        if (connectionId == null || blank(groupOpenId)) {
            return;
        }
        groups.computeIfAbsent(connectionId, ignored -> new ConcurrentHashMap<>())
                .compute(groupOpenId, (id, existing) -> {
                    String resolved = blank(groupName) ? null : groupName;
                    if (resolved == null && existing != null && !blank(existing.groupName()) && !id.equals(existing.groupName())) {
                        resolved = existing.groupName();
                    }
                    if (resolved == null) {
                        resolved = id;
                    }
                    return new Group(id, resolved);
                });
    }

    public boolean needsGroupName(Long connectionId, String groupOpenId) {
        if (connectionId == null || blank(groupOpenId)) {
            return false;
        }
        Map<String, Group> stored = groups.get(connectionId);
        if (stored == null) {
            return true;
        }
        Group group = stored.get(groupOpenId);
        return group == null || blank(group.groupName()) || groupOpenId.equals(group.groupName());
    }

    public void forgetGroup(Long connectionId, String groupOpenId) {
        if (connectionId == null || blank(groupOpenId)) {
            return;
        }
        Map<String, Group> stored = groups.get(connectionId);
        if (stored != null) {
            stored.remove(groupOpenId);
        }
    }

    public void rememberFriend(Long connectionId, String userOpenId, String nickname) {
        if (connectionId == null || blank(userOpenId)) {
            return;
        }
        friends.computeIfAbsent(connectionId, ignored -> new ConcurrentHashMap<>())
                .put(userOpenId, new Friend(userOpenId, blank(nickname) ? userOpenId : nickname));
    }

    public void rememberGuild(Long connectionId, String guildId, String guildName) {
        if (connectionId == null || blank(guildId)) {
            return;
        }
        guilds.computeIfAbsent(connectionId, ignored -> new ConcurrentHashMap<>())
                .compute(guildId, (id, existing) -> {
                    String resolved = blank(guildName) ? null : guildName;
                    if (resolved == null && existing != null && !blank(existing.guildName()) && !id.equals(existing.guildName())) {
                        resolved = existing.guildName();
                    }
                    if (resolved == null) {
                        resolved = id;
                    }
                    return new Guild(id, resolved);
                });
    }

    public List<Map<String, Object>> guildList(Long connectionId) {
        Map<String, Guild> stored = connectionId == null ? null : guilds.get(connectionId);
        if (stored == null || stored.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Guild guild : stored.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("guild_id", guild.guildId());
            row.put("guild_name", guild.guildName());
            rows.add(row);
        }
        return List.copyOf(rows);
    }

    public void rememberGroupMember(Long connectionId, String groupOpenId, String memberOpenId, String nickname) {
        if (connectionId == null || blank(groupOpenId) || blank(memberOpenId)) {
            return;
        }
        members.computeIfAbsent(connectionId, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(groupOpenId, ignored -> new ConcurrentHashMap<>())
                .compute(memberOpenId, (id, existing) -> {
                    String resolved = blank(nickname) ? null : nickname;
                    if (resolved == null && existing != null && !blank(existing.nickname()) && !id.equals(existing.nickname())) {
                        resolved = existing.nickname();
                    }
                    if (resolved == null) {
                        resolved = id;
                    }
                    return new Member(id, resolved);
                });
    }

    public List<Map<String, Object>> groupMembers(Long connectionId, String groupOpenId) {
        if (connectionId == null || blank(groupOpenId)) {
            return List.of();
        }
        Map<String, Map<String, Member>> stored = members.get(connectionId);
        Map<String, Member> groupMembers = stored == null ? null : stored.get(groupOpenId);
        if (groupMembers == null || groupMembers.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Member member : groupMembers.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("user_id", member.userId());
            row.put("member_openid", member.userId());
            row.put("nickname", member.nickname());
            rows.add(row);
        }
        return List.copyOf(rows);
    }

    public void forgetFriend(Long connectionId, String userOpenId) {
        if (connectionId == null || blank(userOpenId)) {
            return;
        }
        Map<String, Friend> stored = friends.get(connectionId);
        if (stored != null) {
            stored.remove(userOpenId);
        }
    }

    public void rememberInbound(Long connectionId, String peerId, String msgId, String eventId) {
        if (connectionId == null || blank(peerId) || (blank(msgId) && blank(eventId))) {
            return;
        }
        lastInbound.computeIfAbsent(connectionId, ignored -> new ConcurrentHashMap<>())
                .put(peerId, new LastInbound(msgId, eventId));
    }

    public LastInbound lastInbound(Long connectionId, String peerId) {
        if (connectionId == null || blank(peerId)) {
            return null;
        }
        Map<String, LastInbound> stored = lastInbound.get(connectionId);
        return stored == null ? null : stored.get(peerId);
    }

    public int nextMessageSeq(Long connectionId, String peerId) {
        if (connectionId == null) {
            return 1;
        }
        String key = blank(peerId) ? "_" : peerId;
        return sequences.computeIfAbsent(connectionId, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(key, ignored -> new AtomicInteger())
                .incrementAndGet();
    }

    public void rememberMessage(Long connectionId, String scene, String peerId, Map<String, Object> message) {
        if (connectionId == null || blank(peerId) || message == null || message.isEmpty()) {
            return;
        }
        Deque<Map<String, Object>> deque = histories.computeIfAbsent(connectionId, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(historyKey(scene, peerId), ignored -> new ArrayDeque<>());
        synchronized (deque) {
            String seq = stringValue(message.get("message_seq"));
            if (!blank(seq)) {
                deque.removeIf(item -> seq.equals(stringValue(item.get("message_seq"))));
            }
            deque.addLast(copyMessage(message));
            while (deque.size() > MAX_HISTORY) {
                deque.removeFirst();
            }
        }
    }

    public List<Map<String, Object>> history(Long connectionId, String scene, String peerId, String start, int limit) {
        if (connectionId == null || blank(peerId)) {
            return List.of();
        }
        Map<String, Deque<Map<String, Object>>> stored = histories.get(connectionId);
        if (stored == null) {
            return List.of();
        }
        Deque<Map<String, Object>> deque = stored.get(historyKey(scene, peerId));
        if (deque == null || deque.isEmpty()) {
            return List.of();
        }
        int size = Math.min(MAX_HISTORY, Math.max(1, limit));
        synchronized (deque) {
            List<Map<String, Object>> snapshot = new ArrayList<>(deque);
            int from = 0;
            if (!blank(start)) {
                int index = -1;
                for (int i = 0; i < snapshot.size(); i++) {
                    if (start.equals(stringValue(snapshot.get(i).get("message_seq")))) {
                        index = i;
                        break;
                    }
                }
                from = index < 0 ? 0 : Math.min(snapshot.size(), index + 1);
            }
            int to = Math.min(snapshot.size(), from + size);
            if (from >= to) {
                return List.of();
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> item : snapshot.subList(from, to)) {
                rows.add(copyMessage(item));
            }
            return List.copyOf(rows);
        }
    }

    public Map<String, Object> message(Long connectionId, String messageSeq) {
        if (connectionId == null || blank(messageSeq)) {
            return null;
        }
        Map<String, Deque<Map<String, Object>>> stored = histories.get(connectionId);
        if (stored == null) {
            return null;
        }
        for (Deque<Map<String, Object>> deque : stored.values()) {
            synchronized (deque) {
                for (Map<String, Object> item : deque) {
                    if (messageSeq.equals(stringValue(item.get("message_seq")))
                            || messageSeq.equals(stringValue(item.get("message_id")))) {
                        return copyMessage(item);
                    }
                }
            }
        }
        return null;
    }

    public List<Map<String, Object>> groupList(Long connectionId) {
        Map<String, Group> stored = connectionId == null ? null : groups.get(connectionId);
        if (stored == null || stored.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Group group : stored.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("group_id", group.groupId());
            row.put("group_name", group.groupName());
            rows.add(row);
        }
        return List.copyOf(rows);
    }

    public List<Map<String, Object>> friendList(Long connectionId) {
        Map<String, Friend> stored = connectionId == null ? null : friends.get(connectionId);
        if (stored == null || stored.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Friend friend : stored.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("user_id", friend.userId());
            row.put("nickname", friend.nickname());
            rows.add(row);
        }
        return List.copyOf(rows);
    }

    public void forget(Long connectionId) {
        if (connectionId == null) {
            return;
        }
        groups.remove(connectionId);
        friends.remove(connectionId);
        guilds.remove(connectionId);
        members.remove(connectionId);
        lastInbound.remove(connectionId);
        sequences.remove(connectionId);
        selfIds.remove(connectionId);
        selfNames.remove(connectionId);
        histories.remove(connectionId);
    }

    private static String historyKey(String scene, String peerId) {
        String normalized = blank(scene) ? "_" : scene.trim().toLowerCase();
        if ("private".equals(normalized)) {
            normalized = "friend";
        }
        return normalized + ":" + peerId;
    }

    private static Map<String, Object> copyMessage(Map<String, Object> message) {
        return new LinkedHashMap<>(message);
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record Group(String groupId, String groupName) { }
    public record Friend(String userId, String nickname) { }
    public record Guild(String guildId, String guildName) { }
    public record Member(String userId, String nickname) { }
    public record LastInbound(String msgId, String eventId) { }
}

package online.yudream.base.infra.platform.milky.official;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 官方机器人会话侧缓存：被动回复所需的最近 msg_id、群列表，以及同 msg_id 递增的 msg_seq。
 */
@Component
public class OfficialQqBotSessionStore {
    private final Map<Long, Map<String, Group>> groups = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, Friend>> friends = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, LastInbound>> lastInbound = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, AtomicInteger>> sequences = new ConcurrentHashMap<>();
    private final Map<Long, String> selfIds = new ConcurrentHashMap<>();

    public void rememberSelf(Long connectionId, String selfId) {
        if (connectionId != null && selfId != null && !selfId.isBlank()) {
            selfIds.put(connectionId, selfId);
        }
    }

    public String selfId(Long connectionId) {
        return connectionId == null ? null : selfIds.get(connectionId);
    }

    public void rememberGroup(Long connectionId, String groupOpenId, String groupName) {
        if (connectionId == null || blank(groupOpenId)) {
            return;
        }
        groups.computeIfAbsent(connectionId, ignored -> new ConcurrentHashMap<>())
                .put(groupOpenId, new Group(groupOpenId, blank(groupName) ? groupOpenId : groupName));
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
        lastInbound.remove(connectionId);
        sequences.remove(connectionId);
        selfIds.remove(connectionId);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record Group(String groupId, String groupName) { }
    public record Friend(String userId, String nickname) { }
    public record LastInbound(String msgId, String eventId) { }
}

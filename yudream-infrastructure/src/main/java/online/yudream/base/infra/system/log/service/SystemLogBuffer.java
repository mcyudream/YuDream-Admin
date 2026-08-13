package online.yudream.base.infra.system.log.service;

import online.yudream.base.domain.system.log.model.SystemLogEntry;
import online.yudream.base.domain.system.log.model.SystemLogLevel;
import online.yudream.base.domain.system.log.model.SystemLogQuery;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 系统运行日志内存环形缓冲（进程内单例）。logback appender 写入，系统日志页面查询与订阅读取。
 * 通过最大条数上限实现自动清理，避免长期运行导致内存膨胀。
 */
public final class SystemLogBuffer {

    private static final SystemLogBuffer INSTANCE = new SystemLogBuffer();

    private final Object monitor = new Object();
    private final ArrayDeque<SystemLogEntry> entries = new ArrayDeque<>();
    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong(1);
    private final AtomicLong dropped = new AtomicLong();

    private volatile int maxEntries = 10_000;
    private volatile int maxMessageLength = 8_000;
    private volatile int maxThrowableLength = 4_000;

    public static SystemLogBuffer instance() {
        return INSTANCE;
    }

    public void append(long timestamp, SystemLogLevel level, String logger, String module, String thread,
                       String traceId, String message, String throwable) {
        SystemLogEntry entry = new SystemLogEntry(
                sequence.getAndIncrement(), timestamp, level, logger, module, thread, traceId,
                truncate(message, maxMessageLength), truncate(throwable, maxThrowableLength));
        List<Subscription> snapshot;
        synchronized (monitor) {
            entries.addLast(entry);
            while (entries.size() > maxEntries) {
                entries.removeFirst();
                dropped.incrementAndGet();
            }
            snapshot = List.copyOf(subscriptions);
        }
        for (Subscription subscription : snapshot) {
            subscription.offer(entry);
        }
    }

    public List<SystemLogEntry> recent(SystemLogQuery query) {
        synchronized (monitor) {
            List<SystemLogEntry> result = new ArrayList<>(Math.min(query.limit(), entries.size()));
            Iterator<SystemLogEntry> iterator = entries.descendingIterator();
            while (iterator.hasNext() && result.size() < query.limit()) {
                SystemLogEntry entry = iterator.next();
                if (query.matches(entry)) {
                    result.add(entry);
                }
            }
            return result;
        }
    }

    public List<String> observedModules() {
        synchronized (monitor) {
            return entries.stream().map(SystemLogEntry::module).filter(Objects::nonNull).distinct().sorted().toList();
        }
    }

    public AutoCloseable subscribe(SystemLogQuery query, Consumer<SystemLogEntry> consumer) {
        Subscription subscription = new Subscription(query, consumer);
        subscriptions.add(subscription);
        return () -> subscriptions.remove(subscription);
    }

    public void clear() {
        synchronized (monitor) {
            entries.clear();
        }
    }

    public long size() {
        synchronized (monitor) {
            return entries.size();
        }
    }

    public long droppedCount() {
        return dropped.get();
    }

    public void setMaxEntries(int maxEntries) {
        this.maxEntries = Math.max(100, maxEntries);
    }

    public int maxEntries() {
        return maxEntries;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...(truncated)";
    }

    private record Subscription(SystemLogQuery query, Consumer<SystemLogEntry> consumer) {
        void offer(SystemLogEntry entry) {
            try {
                if (query.matches(entry)) {
                    consumer.accept(entry);
                }
            } catch (Exception ignored) {
                // 慢速或已关闭的订阅者不能影响日志采集。
            }
        }
    }
}

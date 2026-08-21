package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.platform.milky.sandbox.QqSandboxSession;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class QqSandboxExecutionScope implements AutoCloseable {
    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();
    private static final java.util.concurrent.ConcurrentMap<String, CopyOnWriteArrayList<CompletableFuture<?>>> PENDING =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final State previous;
    private final State installed;
    private boolean closed;

    private QqSandboxExecutionScope(State state) {
        previous = CURRENT.get();
        installed = state;
        CURRENT.set(state);
    }

    public static QqSandboxExecutionScope open(QqSandboxSession session) {
        PENDING.computeIfAbsent(session.id(), ignored -> new CopyOnWriteArrayList<>());
        return new QqSandboxExecutionScope(new State(session));
    }

    public static QqSandboxExecutionScope openIfAbsent(QqSandboxSession session) {
        return current() == null ? open(session) : new QqSandboxExecutionScope(CURRENT.get());
    }

    private static QqSandboxExecutionScope open(State state) {
        return new QqSandboxExecutionScope(state);
    }

    public static QqSandboxSession current() {
        State state = CURRENT.get();
        return state == null ? null : state.session;
    }

    public static QqSandboxSession requireActive() {
        QqSandboxSession session = current();
        if (session != null && !session.acceptsCaptures()) {
            throw new online.yudream.base.domain.common.exception.BizException("QQ 沙箱会话已关闭");
        }
        return session;
    }

    static boolean accepts(String pluginCode) {
        QqSandboxSession session = current();
        // 会话未限定插件（空串）时广播给全部插件，与真实 QQ 群的触发语义一致
        return session == null || session.pluginCode().isEmpty() || session.pluginCode().equals(pluginCode);
    }

    public static Runnable wrap(Runnable task) {
        State captured = CURRENT.get();
        if (captured == null) return task;
        return () -> {
            try (QqSandboxExecutionScope ignored = open(captured)) {
                task.run();
            }
        };
    }

    public static <T> Supplier<T> wrap(Supplier<T> task) {
        State captured = CURRENT.get();
        if (captured == null) return task;
        return () -> {
            try (QqSandboxExecutionScope ignored = open(captured)) {
                return task.get();
            }
        };
    }

    public static <T, R> Function<T, R> wrap(Function<T, R> task) {
        State captured = CURRENT.get();
        if (captured == null) return task;
        return value -> {
            try (QqSandboxExecutionScope ignored = open(captured)) {
                return task.apply(value);
            }
        };
    }

    public static <T, U> BiConsumer<T, U> wrap(BiConsumer<T, U> task) {
        State captured = CURRENT.get();
        if (captured == null) return task;
        return (first, second) -> {
            try (QqSandboxExecutionScope ignored = open(captured)) {
                task.accept(first, second);
            }
        };
    }

    public static <T> Consumer<T> wrap(Consumer<T> task) {
        State captured = CURRENT.get();
        if (captured == null) return task;
        return value -> {
            try (QqSandboxExecutionScope ignored = open(captured)) {
                task.accept(value);
            }
        };
    }

    public static <T> CompletionStage<T> wrapCompletion(QqSandboxSession session, CompletionStage<T> stage) {
        if (session == null || stage == null) return stage;
        try (QqSandboxExecutionScope ignored = open(session)) {
            return track(stage.whenComplete(wrap((value, error) -> requireActive())));
        }
    }

    public static void cancelPending(QqSandboxSession session) {
        if (session == null) return;
        PENDING.getOrDefault(session.id(), new CopyOnWriteArrayList<>()).forEach(future -> future.cancel(true));
        PENDING.remove(session.id());
        session.close();
    }

    static <T> CompletionStage<T> track(CompletionStage<T> stage) {
        State state = CURRENT.get();
        if (state != null && stage != null) {
            state.pending.add(stage.toCompletableFuture());
            PENDING.computeIfAbsent(state.session.id(), ignored -> new CopyOnWriteArrayList<>())
                    .add(stage.toCompletableFuture());
        }
        return stage;
    }

    static CompletionStage<Void> awaitTracked() {
        State state = CURRENT.get();
        if (state == null) return CompletableFuture.completedFuture(null);
        CompletableFuture<Void> result = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            try {
                int seen = -1;
                while (!result.isCancelled()) {
                    List<CompletableFuture<?>> snapshot = List.copyOf(state.pending);
                    CompletableFuture.allOf(snapshot.toArray(CompletableFuture[]::new)).join();
                    if (snapshot.size() == seen && snapshot.stream().allMatch(CompletableFuture::isDone)
                            && !state.session.hasActiveOperations()) {
                        result.complete(null);
                        PENDING.remove(state.session.id());
                        return;
                    }
                    seen = snapshot.size();
                    Thread.sleep(10L);
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                result.completeExceptionally(error);
            } catch (RuntimeException error) {
                result.completeExceptionally(error);
            }
        });
        result.whenComplete((value, error) -> {
            if (result.isCancelled()) cancelPending(state.session);
        });
        return result;
    }

    static Map<String, Map<String, Map<String, Object>>> documents() {
        QqSandboxSession session = requireActive();
        return session == null ? null : session.documentOverlay();
    }

    static Map<String, Object> semanticWrites() {
        QqSandboxSession session = requireActive();
        return session == null ? null : session.semanticOverlay();
    }

    @Override
    public void close() {
        if (closed) return;
        if (CURRENT.get() != installed) throw new IllegalStateException("QQ 沙箱执行作用域必须按后进先出顺序关闭");
        closed = true;
        if (previous == null) CURRENT.remove();
        else CURRENT.set(previous);
    }

    private static final class State {
        private final QqSandboxSession session;
        private final CopyOnWriteArrayList<CompletableFuture<?>> pending = new CopyOnWriteArrayList<>();

        private State(QqSandboxSession session) {
            this.session = session;
        }
    }
}

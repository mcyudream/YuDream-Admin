package online.yudream.base.infra.platform.plugin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.agent.service.AgentRuntimeApplicationRegistry;
import online.yudream.base.domain.platform.plugin.aggregate.PluginModule;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpDispatchResult;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpStreamingBody;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpStreamingDispatchRequest;
import online.yudream.base.domain.platform.plugin.valobj.PluginHttpStreamingPart;
import online.yudream.base.infra.platform.plugin.devmode.DevModeEnvironment;
import online.yudream.base.infra.platform.plugin.devmode.PluginDevDirectoryBrowser;
import online.yudream.base.infra.platform.plugin.devmode.PluginDevProjectCatalog;
import online.yudream.base.infra.platform.plugin.devmode.PluginScaffoldGenerator;
import online.yudream.base.plugin.spi.annotation.PluginStreamingHttpEndpoint;
import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.core.YuDreamPlugin;
import online.yudream.base.plugin.spi.http.PluginHttpBodyStream;
import online.yudream.base.plugin.spi.http.PluginHttpResponseBody;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;
import online.yudream.base.plugin.spi.http.PluginStreamingHttpRequest;
import online.yudream.base.plugin.spi.system.FrameworkServices;
import online.yudream.base.plugin.spi.system.memory.PluginSemanticMemoryService;
import online.yudream.base.plugin.spi.system.security.PluginPrincipal;
import online.yudream.base.plugin.spi.system.security.PluginSecurityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流式 HTTP 分发验证：桥接单元语义（鉴权先于物化/限长、唯一流、资源清理）
 * 与真 JAR 插件端到端（注册、注解、禁用主动取消活动流、跨模式重复注册）。
 */
class PluginStreamingHttpDispatchTest {

    private static final String PLUGIN_CODE = "stream-plugin";
    private static final long SMALL_LIMIT = 8;

    @TempDir
    Path pluginDir;

    // ---------- 桥接单元语义 ----------

    @Test
    void echoBodyStreamsToHandlerWithoutBufferingAndClosesHostSideStream() {
        byte[] payload = "hello-stream".getBytes(StandardCharsets.UTF_8);
        FakeBody body = new FakeBody(payload, payload.length);
        AtomicReference<PluginHttpBodyStream> seenBody = new AtomicReference<>();

        PluginHttpDispatchResult result = bridge().dispatch(
                bridge().newRegistry(),
                registration(request -> {
                    seenBody.set(request.body());
                    try (InputStream in = request.body().stream()) {
                        byte[] bytes = in.readAllBytes();
                        return PluginHttpResponse.rawJson(200, Map.of("bytes", bytes.length,
                                "text", new String(bytes, StandardCharsets.UTF_8)));
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                }),
                "POST", "/stream/echo",
                Map.of("Content-Length", List.of(String.valueOf(payload.length))),
                new PluginPrincipal(1L, List.of()),
                1024,
                () -> {
                },
                streamingRequest(body, Map.of())
        );

        assertEquals(200, result.status());
        assertFalse(result.wrapped());
        Map<String, Object> responseBody = asMap(result.body());
        assertEquals(payload.length, ((Number) responseBody.get("bytes")).intValue());
        assertEquals("hello-stream", responseBody.get("text"));
        // 唯一流实例 + 宿主兜底关闭
        assertNotNull(seenBody.get());
        assertTrue(body.closed.get(), "宿主必须在分发结束后关闭请求体流");
        assertEquals(1, body.opened.get());
    }

    @Test
    void declaredLengthAboveLimitRejectedBeforeAnyMaterialization() {
        FakeBody body = new FakeBody(new byte[1024], 1024);
        AtomicInteger handlerInvoked = new AtomicInteger();
        AtomicBoolean queryMaterialized = new AtomicBoolean();

        PluginHttpDispatchResult result = bridge().dispatch(
                bridge().newRegistry(),
                registration(request -> {
                    handlerInvoked.incrementAndGet();
                    return PluginHttpResponse.ok(Map.of());
                }),
                "POST", "/stream/echo", Map.of("Content-Length", List.of("1024")),
                new PluginPrincipal(1L, List.of()),
                SMALL_LIMIT,
                () -> {
                },
                new PluginHttpStreamingDispatchRequest(
                        PLUGIN_CODE, "POST", "/stream/echo",
                        Map.of("Content-Length", List.of("1024")), 1L, List.of(),
                        () -> {
                            queryMaterialized.set(true);
                            return Map.of();
                        },
                        () -> body,
                        Map::of)
        );

        assertEquals(413, result.status());
        assertEquals(0, handlerInvoked.get(), "前置限长不得进入处理器");
        assertFalse(queryMaterialized.get(), "前置限长不得物化 query（可能触发容器解析）");
        assertFalse(body.opened.get() > 0, "前置限长不得打开请求体流");
        assertFalse(body.closed.get());
        assertNotNull(asMap(result.body()).get("message"));
    }

    @Test
    void permissionCheckRunsBeforeSizeCheckAndMaterialization() {
        FakeBody body = new FakeBody(new byte[1024], 1024);
        List<String> order = new ArrayList<>();

        // 无权限：鉴权异常优先于 413，且不物化任何请求体
        BizException authFailure = assertThrows(BizException.class, () -> bridge().dispatch(
                bridge().newRegistry(),
                registration(request -> PluginHttpResponse.ok(Map.of())),
                "POST", "/stream/echo", Map.of("Content-Length", List.of("1024")),
                new PluginPrincipal(1L, List.of()),
                SMALL_LIMIT,
                () -> {
                    order.add("auth");
                    throw new BizException("无插件访问权限");
                },
                new PluginHttpStreamingDispatchRequest(
                        PLUGIN_CODE, "POST", "/stream/echo",
                        Map.of("Content-Length", List.of("1024")), 1L, List.of(),
                        () -> {
                            order.add("query");
                            return Map.of();
                        },
                        () -> {
                            order.add("body");
                            return body;
                        },
                        () -> {
                            order.add("parts");
                            return Map.of();
                        })
        ));
        assertEquals("无插件访问权限", authFailure.getMessage());
        assertEquals(List.of("auth"), order);

        // 有权限：鉴权先于前置限长
        List<String> authorizedOrder = new ArrayList<>();
        PluginHttpDispatchResult result = bridge().dispatch(
                bridge().newRegistry(),
                registration(request -> PluginHttpResponse.ok(Map.of())),
                "POST", "/stream/echo", Map.of("Content-Length", List.of("1024")),
                new PluginPrincipal(1L, List.of()),
                SMALL_LIMIT,
                () -> authorizedOrder.add("auth"),
                new PluginHttpStreamingDispatchRequest(
                        PLUGIN_CODE, "POST", "/stream/echo",
                        Map.of("Content-Length", List.of("1024")), 1L, List.of(),
                        () -> {
                            authorizedOrder.add("query");
                            return Map.of();
                        },
                        () -> body,
                        Map::of)
        );
        assertEquals(413, result.status());
        assertEquals(List.of("auth"), authorizedOrder, "限长前只允许执行鉴权");
    }

    @Test
    void midStreamLimitExceededRejectsWith413AndStillClosesBody() {
        // 声明长度撒谎（8），实际 64 字节：边读边计数拦截
        FakeBody body = new FakeBody(new byte[64], 8);
        PluginHttpDispatchResult result = bridge().dispatch(
                bridge().newRegistry(),
                registration(request -> {
                    try {
                        request.body().stream().readAllBytes();
                        return PluginHttpResponse.ok(Map.of());
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                }),
                "POST", "/stream/echo", Map.of(),
                new PluginPrincipal(1L, List.of()),
                SMALL_LIMIT,
                () -> {
                },
                streamingRequest(body, Map.of())
        );
        assertEquals(413, result.status());
        assertTrue(body.closed.get(), "限长中断后宿主仍必须关闭请求体流");
    }

    @Test
    void openedPartStreamsClosedAndPartsDisposedEvenWhenPluginLeavesThemOpen() {
        FakePart file = new FakePart("file", "a.bin", new byte[10]);
        FakePart field = new FakePart("field", null, new byte[2]);
        AtomicInteger handlerSawParts = new AtomicInteger();

        PluginHttpDispatchResult result = bridge().dispatch(
                bridge().newRegistry(),
                registration(request -> {
                    handlerSawParts.set(request.parts().size());
                    try {
                        // 只打开 file 的流且不关闭；field 从不打开
                        request.parts().get("file").stream().readAllBytes();
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                    return PluginHttpResponse.ok(Map.of());
                }),
                "POST", "/stream/upload", Map.of(),
                new PluginPrincipal(1L, List.of()),
                1024,
                () -> {
                },
                streamingRequest(null, Map.of("file", file, "field", field))
        );

        assertEquals(200, result.status());
        assertEquals(2, handlerSawParts.get());
        assertEquals(1, file.openedStreams.size());
        assertTrue(file.openedStreams.getFirst().closed.get(), "插件未关闭的 part 流必须由宿主兜底关闭");
        assertTrue(file.disposed.get(), "part 必须被 dispose（清理容器临时文件）");
        assertTrue(field.disposed.get());
        assertEquals(0, field.openedStreams.size(), "未打开的 part 不应产生流");
    }

    @Test
    void partsMaterializationFailureStillClosesMaterializedBody() {
        // parts supplier 抛错（如容器解析失败）时，已物化的请求体必须经 finally 关闭
        FakeBody body = new FakeBody(new byte[]{1}, 1);
        assertThrows(BizException.class, () -> bridge().dispatch(
                bridge().newRegistry(),
                registration(request -> PluginHttpResponse.ok(Map.of())),
                "POST", "/stream/upload", Map.of(),
                new PluginPrincipal(1L, List.of()),
                1024,
                () -> {
                },
                new PluginHttpStreamingDispatchRequest(
                        PLUGIN_CODE, "POST", "/stream/upload", Map.of(), 1L, List.of(),
                        () -> Map.of(), () -> body,
                        () -> {
                            throw new BizException("解析 multipart 请求失败：测试注入");
                        })
        ));
        assertTrue(body.closed.get(), "parts 物化失败时已物化的请求体必须被关闭");
    }

    @Test
    void partTotalSizeAboveLimitRejectedBeforeHandler() {
        FakePart big = new FakePart("file", "a.bin", new byte[100]);
        AtomicInteger handlerInvoked = new AtomicInteger();
        PluginHttpDispatchResult result = bridge().dispatch(
                bridge().newRegistry(),
                registration(request -> {
                    handlerInvoked.incrementAndGet();
                    return PluginHttpResponse.ok(Map.of());
                }),
                "POST", "/stream/upload", Map.of(),
                new PluginPrincipal(1L, List.of()),
                SMALL_LIMIT,
                () -> {
                },
                streamingRequest(null, Map.of("file", big))
        );
        assertEquals(413, result.status());
        assertEquals(0, handlerInvoked.get());
        assertTrue(big.disposed.get(), "限长拒绝后仍需 dispose parts");
    }

    @Test
    void handlerFailureStillClosesBodyAndRethrows() {
        FakeBody body = new FakeBody(new byte[]{1, 2, 3}, 3);
        assertThrows(IllegalStateException.class, () -> bridge().dispatch(
                bridge().newRegistry(),
                registration(request -> {
                    throw new IllegalStateException("处理器崩溃");
                }),
                "POST", "/stream/echo", Map.of(),
                new PluginPrincipal(1L, List.of()),
                SMALL_LIMIT,
                () -> {
                },
                streamingRequest(body, Map.of())
        ));
        assertTrue(body.closed.get(), "处理器抛错后宿主仍必须关闭请求体流");
    }

    @Test
    void handlerMappingIllegalArgumentExceptionTo400LikeBufferedDispatch() {
        PluginHttpDispatchResult result = bridge().dispatch(
                bridge().newRegistry(),
                registration(request -> {
                    throw new IllegalArgumentException("参数不合法");
                }),
                "POST", "/stream/echo", Map.of(),
                new PluginPrincipal(1L, List.of()),
                1024,
                () -> {
                },
                streamingRequest(null, Map.of())
        );
        assertEquals(400, result.status());
        assertEquals("参数不合法", asMap(result.body()).get("message"));
    }

    @Test
    void disabledLimitAllowsOversizedStreams() {
        FakeBody body = new FakeBody(new byte[1024], -1);
        PluginHttpDispatchResult result = bridge().dispatch(
                bridge().newRegistry(),
                registration(request -> {
                    try {
                        return PluginHttpResponse.rawJson(200,
                                Map.of("bytes", request.body().stream().readAllBytes().length));
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                }),
                "POST", "/stream/echo", Map.of(),
                new PluginPrincipal(1L, List.of()),
                0, // 运维显式关闭限制
                () -> {
                },
                streamingRequest(body, Map.of())
        );
        assertEquals(200, result.status());
        assertTrue(body.closed.get());
    }

    @Test
    @Timeout(60)
    void cancelActiveClosesInFlightBodyStream() {
        FakeBody body = new FakeBody(new byte[16], 16);
        CountDownLatch bodyOpened = new CountDownLatch(1);
        CountDownLatch releaseRead = new CountDownLatch(1);
        // 模拟插件正在挂起读取：读阻塞直到被释放，同时可通过 close 唤醒感知取消
        PluginHttpStreamingBody blockingBody = new PluginHttpStreamingBody() {
            @Override
            public InputStream stream() {
                bodyOpened.countDown();
                return new InputStream() {
                    private volatile boolean hostClosed;

                    @Override
                    public int read() throws IOException {
                        // 宿主取消保证 close -> countDown 唤醒；不设硬超时，
                        // 若取消未触达本流，测试将以挂起而非假 EOF 的方式显式失败
                        try {
                            releaseRead.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IOException("中断", e);
                        }
                        if (hostClosed) {
                            // 宿主取消关闭流后，挂起读取必须以 IOException 感知（而非正常 EOF）
                            throw new IOException("流已被宿主取消");
                        }
                        return -1;
                    }

                    @Override
                    public void close() {
                        // 宿主取消时唤醒挂起读取并以 IOException 告知（模拟 socket 关闭语义）
                        hostClosed = true;
                        releaseRead.countDown();
                    }
                };
            }

            @Override
            public long declaredContentLength() {
                return 16;
            }

            @Override
            public String contentType() {
                return "application/octet-stream";
            }

            @Override
            public void close() {
                body.closed.set(true);
            }
        };
        PluginStreamingHttpHandlerEcho handler = request -> {
            try {
                request.body().stream().read();
                return PluginHttpResponse.ok(Map.of());
            } catch (IOException e) {
                return PluginHttpResponse.rawJson(499, Map.of("message", "读取被取消"));
            }
        };
        PluginStreamingHttpBridge.PluginActiveDispatches registry = bridge().newRegistry();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<PluginHttpDispatchResult> dispatching = executor.submit(() -> bridge().dispatch(
                    registry,
                    registration(handler::handle),
                    "POST", "/stream/echo", Map.of(),
                    new PluginPrincipal(1L, List.of()),
                    1024,
                    () -> {
                    },
                    new PluginHttpStreamingDispatchRequest(
                            PLUGIN_CODE, "POST", "/stream/echo", Map.of(), 1L, List.of(),
                            () -> Map.of(), () -> blockingBody, Map::of)
            ));
            assertTrue(bodyOpened.await(5, TimeUnit.SECONDS), "处理器应已打开请求体流");
            assertEquals(1, bridge().cancel(registry), "取消应作用于进行中的分发");
            PluginHttpDispatchResult result = dispatching.get(5, TimeUnit.SECONDS);
            assertEquals(499, result.status(), "宿主主动取消应以 IOException 唤醒挂起读取");
            assertEquals(0, bridge().cancel(registry), "取消是幂等的一次性动作");
            assertTrue(body.closed.get());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            releaseRead.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void streamingResponseBodyIsPassedThroughUntouched() {
        byte[] payload = "chunked-file-content".getBytes(StandardCharsets.UTF_8);
        AtomicBoolean responseClosed = new AtomicBoolean();
        PluginHttpResponseBody responseBody = new PluginHttpResponseBody() {
            @Override
            public InputStream stream() {
                return new ByteArrayInputStream(payload);
            }

            @Override
            public long contentLength() {
                return payload.length;
            }

            @Override
            public void close() {
                responseClosed.set(true);
            }
        };
        PluginHttpDispatchResult result = bridge().dispatch(
                bridge().newRegistry(),
                registration(request -> new PluginHttpResponse(200, Map.of("X-Plugin", "stream"),
                        "application/octet-stream", responseBody, false)),
                "GET", "/stream/download", Map.of(),
                new PluginPrincipal(1L, List.of()),
                1024,
                () -> {
                },
                streamingRequest(null, Map.of())
        );
        assertEquals(200, result.status());
        assertEquals("application/octet-stream", result.contentType());
        assertEquals("stream", result.headers().get("X-Plugin"));
        assertFalse(result.wrapped());
        assertInstanceOf(PluginHttpResponseBody.class, result.body());
        // 宿主负责关闭响应流（由 interfaces 写出层执行；网关层不提前关闭）
        assertFalse(responseClosed.get(), "响应流的关闭归属 Web 写出层，不由桥接代劳");
    }

    @Test
    void requestBackedStreamingResponseStaysReadableUntilResponseClosed() {
        // 回显端点：响应内容惰性来自请求体流。桥接必须保证请求体在响应写出期间保持可用
        byte[] payload = "request-to-response".getBytes(StandardCharsets.UTF_8);
        FakeBody body = new FakeBody(payload, payload.length);

        PluginStreamingHttpBridge.PluginActiveDispatches registry = bridge().newRegistry();
        PluginHttpDispatchResult result = bridge().dispatch(
                registry,
                registration(request -> {
                    PluginHttpBodyStream bodyStream = request.body();
                    return new PluginHttpResponse(200, Map.of(), "application/octet-stream",
                            new PluginHttpResponseBody() {
                                @Override
                                public InputStream stream() {
                                    // 分发返回后才被 Web 写出层消费
                                    return bodyStream.stream();
                                }

                                @Override
                                public long contentLength() {
                                    return payload.length;
                                }

                                @Override
                                public void close() {
                                    bodyStream.close();
                                }
                            }, false);
                }),
                "POST", "/stream/echo", Map.of("Content-Length", List.of(String.valueOf(payload.length))),
                new PluginPrincipal(1L, List.of()),
                1024,
                () -> {
                },
                streamingRequest(body, Map.of())
        );

        PluginHttpResponseBody responseBody = assertInstanceOf(PluginHttpResponseBody.class, result.body());
        assertFalse(body.closed.get(), "分发返回后请求体不得提前关闭：响应写出期间仍需可读");
        try (InputStream in = responseBody.stream()) {
            assertArrayEquals(payload, in.readAllBytes(), "响应写出阶段必须能读到请求体内容");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        // 模拟 Web 写出层收尾：关闭响应体必须连带释放请求侧资源
        responseBody.close();
        assertTrue(body.closed.get(), "响应体关闭必须同步释放请求体流");
        assertEquals(0, bridge().cancel(registry), "清理后不得残留活动分发");
    }

    @Test
    void partBackedStreamingResponseStaysReadableUntilResponseClosed() {
        FakePart file = new FakePart("file", "a.bin", new byte[]{5, 6, 7});
        AtomicBoolean delegateResponseClosed = new AtomicBoolean();

        PluginHttpDispatchResult result = bridge().dispatch(
                bridge().newRegistry(),
                registration(request -> new PluginHttpResponse(200, Map.of(), "application/octet-stream",
                        new PluginHttpResponseBody() {
                            @Override
                            public InputStream stream() {
                                try {
                                    return request.parts().get("file").stream();
                                } catch (IOException e) {
                                    throw new IllegalStateException(e);
                                }
                            }

                            @Override
                            public long contentLength() {
                                return 3;
                            }

                            @Override
                            public void close() {
                                delegateResponseClosed.set(true);
                            }
                        }, false)),
                "POST", "/stream/upload", Map.of(),
                new PluginPrincipal(1L, List.of()),
                1024,
                () -> {
                },
                streamingRequest(null, Map.of("file", file))
        );

        PluginHttpResponseBody responseBody = assertInstanceOf(PluginHttpResponseBody.class, result.body());
        // part 回传：写出阶段可重新打开流读取，spool 临时文件必须保留到响应写出结束
        try (InputStream in = responseBody.stream()) {
            assertArrayEquals(new byte[]{5, 6, 7}, in.readAllBytes(), "响应写出阶段必须能读取 part 内容");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        assertFalse(file.disposed.get(), "part 不得在响应写出前被 dispose（临时文件已删除则写出失败）");
        responseBody.close();
        assertTrue(delegateResponseClosed.get(), "包装不得吞掉插件响应体的关闭动作");
        assertTrue(file.disposed.get(), "响应体关闭必须连带 dispose part（清理容器临时文件）");
    }

    @Test
    void cancelActiveClosesInFlightStreamingResponse() throws Exception {
        AtomicBoolean responseClosed = new AtomicBoolean();
        PluginStreamingHttpBridge.PluginActiveDispatches registry = bridge().newRegistry();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            // 处理器返回流式响应后、Web 层尚未写出/关闭（模拟慢速长流）：禁用/卸载必须能主动关闭
            Future<PluginHttpDispatchResult> dispatching = executor.submit(() -> bridge().dispatch(
                    registry,
                    registration(request -> new PluginHttpResponse(200, Map.of(), "application/octet-stream",
                            new PluginHttpResponseBody() {
                                @Override
                                public InputStream stream() {
                                    return new ByteArrayInputStream(new byte[0]);
                                }

                                @Override
                                public long contentLength() {
                                    return 0;
                                }

                                @Override
                                public void close() {
                                    responseClosed.set(true);
                                }
                            }, false)),
                    "GET", "/stream/echo", Map.of(),
                    new PluginPrincipal(1L, List.of()),
                    1024,
                    () -> {
                    },
                    new PluginHttpStreamingDispatchRequest(
                            PLUGIN_CODE, "GET", "/stream/echo", Map.of(), 1L, List.of(),
                            () -> Map.of(), () -> null, Map::of)
            ));
            PluginHttpDispatchResult result = dispatching.get(5, TimeUnit.SECONDS);
            assertInstanceOf(PluginHttpResponseBody.class, result.body());
            assertFalse(responseClosed.get(), "写出未结束时响应流保持打开");

            assertEquals(1, bridge().cancel(registry), "取消必须覆盖在途流式响应体");
            assertTrue(responseClosed.get(), "禁用/卸载应主动关闭在途响应流");
            assertEquals(0, bridge().cancel(registry), "取消是幂等的一次性动作");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Timeout(60)
    void cancelBeforeResponseAttachClosesResponseImmediately() throws Exception {
        // 禁用先于处理器返回：cancel 之后 handler 才返回并挂接响应体，
        // 响应体必须被立即关闭（不得逃逸取消），且包装拒绝再读取
        CountDownLatch handlerEntered = new CountDownLatch(1);
        CountDownLatch handlerCanReturn = new CountDownLatch(1);
        AtomicBoolean responseClosed = new AtomicBoolean();
        PluginStreamingHttpBridge.PluginActiveDispatches registry = bridge().newRegistry();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<PluginHttpDispatchResult> dispatching = executor.submit(() -> bridge().dispatch(
                    registry,
                    registration(request -> {
                        // 处理器入口即 countDown：保证主线程 cancel 前活动分发已登记
                        handlerEntered.countDown();
                        try {
                            handlerCanReturn.await(5, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return new PluginHttpResponse(200, Map.of(), "application/octet-stream",
                                new PluginHttpResponseBody() {
                                    @Override
                                    public InputStream stream() {
                                        return new ByteArrayInputStream(new byte[0]);
                                    }

                                    @Override
                                    public long contentLength() {
                                        return 0;
                                    }

                                    @Override
                                    public void close() {
                                        responseClosed.set(true);
                                    }
                                }, false);
                    }),
                    "GET", "/stream/echo", Map.of(),
                    new PluginPrincipal(1L, List.of()),
                    1024,
                    () -> {
                    },
                    new PluginHttpStreamingDispatchRequest(
                            PLUGIN_CODE, "GET", "/stream/echo", Map.of(), 1L, List.of(),
                            () -> Map.of(), () -> null, Map::of)
            ));
            // 等待处理器已进入（活动分发已登记）后再取消，避免与注册竞态导致合法的取消计数 0
            assertTrue(handlerEntered.await(5, TimeUnit.SECONDS), "处理器应已进入并登记活动分发");
            assertEquals(1, bridge().cancel(registry), "处理器未返回时的取消仍须生效");
            handlerCanReturn.countDown();
            PluginHttpDispatchResult result = dispatching.get(5, TimeUnit.SECONDS);
            assertTrue(responseClosed.get(), "晚于取消挂接的响应体必须被立即关闭");
            PluginHttpResponseBody responseBody = assertInstanceOf(PluginHttpResponseBody.class, result.body());
            assertThrows(IllegalStateException.class, responseBody::stream, "已取消的流式响应体必须拒绝再读取");
        } finally {
            executor.shutdownNow();
        }
    }

    // ---------- 真插件 JAR 端到端 ----------

    @Test
    void gatewayDispatchesToRegisteredStreamingHandlerEndToEnd() throws IOException {
        Path jar = writePluginJar("stream-plugin.jar", PLUGIN_CODE, StreamingPlugin.class.getName());
        JarPluginRuntimeGateway gateway = newGateway(new PluginProperties());
        gateway.enable(module(PLUGIN_CODE, jar));

        assertTrue(gateway.hasStreamingHttpHandler(PLUGIN_CODE, "POST", "/stream/echo"));
        assertFalse(gateway.hasStreamingHttpHandler(PLUGIN_CODE, "POST", "/stream/other"));
        assertFalse(gateway.hasStreamingHttpHandler("missing-plugin", "POST", "/stream/echo"));

        StreamingPlugin.reset();
        PluginHttpDispatchResult result = gateway.dispatchStreaming(streamingRequest(
                new FakeBody("端到端流".getBytes(StandardCharsets.UTF_8), -1), Map.of()));

        assertEquals(200, result.status());
        assertEquals("端到端流", StreamingPlugin.lastText.get());
        assertTrue(StreamingPlugin.lastBodyClosed.get());
        gateway.unload(PLUGIN_CODE);
        assertFalse(gateway.hasStreamingHttpHandler(PLUGIN_CODE, "POST", "/stream/echo"));
    }

    @Test
    void annotatedStreamingEndpointReceivesStreamingRequest() throws IOException {
        Path jar = writePluginJar("annotated-stream.jar", "annotated-stream", AnnotatedStreamingPlugin.class.getName());
        JarPluginRuntimeGateway gateway = newGateway(new PluginProperties());
        gateway.enable(module("annotated-stream", jar));

        PluginHttpDispatchResult result = gateway.dispatchStreaming(new PluginHttpStreamingDispatchRequest(
                "annotated-stream", "POST", "/stream/annotated", Map.of("Content-Length", List.of("3")),
                1L, List.of(), () -> Map.of(),
                () -> new FakeBody("abc".getBytes(StandardCharsets.UTF_8), 3), Map::of));
        assertEquals(200, result.status());
        assertEquals(3, ((Number) asMap(result.body()).get("bytes")).intValue());
        gateway.unload("annotated-stream");
    }

    @Test
    void gatewayEnforcesRegisteredPermissionBeforeBridge() throws IOException {
        Path jar = writePluginJar("secured-stream.jar", "secured-stream", SecuredStreamingPlugin.class.getName());
        PluginProperties properties = new PluginProperties();
        properties.setHttpStreamingMaxBodyBytes(4);
        AtomicBoolean requirePermissionCalled = new AtomicBoolean();
        FrameworkServices frameworkServices = (FrameworkServices) Proxy.newProxyInstance(
                FrameworkServices.class.getClassLoader(), new Class<?>[]{FrameworkServices.class},
                (proxy, method, args) -> {
                    if ("security".equals(method.getName())) {
                        return (PluginSecurityService) new PluginSecurityService() {
                            @Override
                            public boolean hasPermission(PluginPrincipal principal, String permission) {
                                return principal.hasPermission(permission);
                            }

                            @Override
                            public void requirePermission(PluginPrincipal principal, String permission) {
                                requirePermissionCalled.set(true);
                                if (!principal.hasPermission(permission)) {
                                    throw new BizException("缺少插件权限：" + permission);
                                }
                            }
                        };
                    }
                    return null;
                });
        JarPluginRuntimeGateway gateway = newGateway(properties, frameworkServices);
        gateway.enable(module("secured-stream", jar));

        // 无权限：鉴权失败先于 413（声明长度超限），且未打开请求体
        BizException failure = assertThrows(BizException.class, () -> gateway.dispatchStreaming(new PluginHttpStreamingDispatchRequest(
                "secured-stream", "POST", "/stream/secured", Map.of("Content-Length", List.of("64")),
                1L, List.of(), () -> Map.of(), () -> new FakeBody(new byte[64], 64), Map::of)));
        assertTrue(failure.getMessage().contains("缺少插件权限"));
        assertTrue(requirePermissionCalled.get());
        assertFalse(SecuredStreamingPlugin.bodyOpened.get(), "鉴权失败不得打开请求体");

        // 有权限：前置限长 413
        SecuredStreamingPlugin.reset();
        PluginPrincipal allowed = new PluginPrincipal(1L, List.of("plugin:secured-stream:upload"));
        PluginHttpDispatchResult denied = gateway.dispatchStreaming(new PluginHttpStreamingDispatchRequest(
                "secured-stream", "POST", "/stream/secured", Map.of("Content-Length", List.of("64")),
                1L, allowed.permissions(), () -> Map.of(), () -> new FakeBody(new byte[64], 64), Map::of));
        assertEquals(413, denied.status());
        gateway.unload("secured-stream");
    }

    @Test
    void disableDuringActiveDispatchActivelyCancelsHangingRead() throws Exception {
        Path jar = writePluginJar("slow-stream.jar", "slow-stream", SlowStreamingPlugin.class.getName());
        JarPluginRuntimeGateway gateway = newGateway(new PluginProperties());
        gateway.enable(module("slow-stream", jar));

        CountDownLatch handlerStarted = new CountDownLatch(1);
        SlowStreamingPlugin.reset(handlerStarted);
        FakeBody body = new FakeBody(new byte[0], -1) {
            @Override
            public InputStream stream() {
                opened.incrementAndGet();
                // 模拟客户端停传导致的挂起读取：只有流被宿主 close 时才唤醒（抛 IOException）
                return new InputStream() {
                    @Override
                    public int read() throws IOException {
                        handlerStarted.countDown();
                        // 不阻塞在锁上：轮询关闭标记，宿主 close 后以 IOException 唤醒
                        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                        while (!closed.get()) {
                            if (System.nanoTime() > deadline) {
                                throw new IOException("等待宿主取消超时");
                            }
                            try {
                                Thread.sleep(10);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new IOException("中断", e);
                            }
                        }
                        throw new IOException("流已被宿主取消");
                    }
                };
            }
        };

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<PluginHttpDispatchResult> dispatching = executor.submit(() -> gateway.dispatchStreaming(
                    new PluginHttpStreamingDispatchRequest(
                            "slow-stream", "POST", "/stream/slow",
                            Map.of(), 1L, List.of(),
                            () -> Map.of(), () -> body, Map::of)));
            assertTrue(handlerStarted.await(5, TimeUnit.SECONDS), "处理器应在限时内启动");

            long disableStart = System.nanoTime();
            gateway.disable("slow-stream");
            long disableMillis = (System.nanoTime() - disableStart) / 1_000_000;
            assertTrue(disableMillis < 5000, "禁用不得等待活动流式分发完成");
            assertFalse(gateway.enabled("slow-stream"));

            // 宿主禁用主动关闭请求体流：挂起读取以 IOException 唤醒、插件自行收尾
            PluginHttpDispatchResult result = dispatching.get(10, TimeUnit.SECONDS);
            assertEquals(499, result.status(), "禁用必须主动取消挂起读取而非放任阻塞");
            assertTrue(body.closed.get(), "取消路径同样必须关闭请求体流");
            assertTrue(SlowStreamingPlugin.aborted.get());
        } finally {
            executor.shutdownNow();
            gateway.unload("slow-stream");
        }
    }

    @Test
    void duplicateRegistrationAcrossBufferedAndStreamingModesRejected() throws IOException {
        Path jar = writePluginJar("conflict-stream.jar", "conflict-stream", ConflictingStreamingPlugin.class.getName());
        JarPluginRuntimeGateway gateway = newGateway(new PluginProperties());
        // onEnable 中先注册缓冲端点再注册同键流式端点，必须在注册期报错并回滚
        assertThrows(BizException.class, () -> gateway.enable(module("conflict-stream", jar)));
        assertFalse(gateway.enabled("conflict-stream"));
        gateway.unload("conflict-stream");
    }

    // ---------- 测试插件（经父委派与测试类共享静态状态） ----------

    public static class StreamingPlugin implements YuDreamPlugin {
        static final AtomicReference<String> lastText = new AtomicReference<>();
        static final AtomicBoolean lastBodyClosed = new AtomicBoolean();

        static void reset() {
            lastText.set(null);
            lastBodyClosed.set(false);
        }

        @Override
        public void onEnable(PluginContext context) {
            context.registerStreamingHttpHandler("POST", "/stream/echo", request -> {
                try (InputStream in = request.body().stream()) {
                    byte[] bytes = in.readAllBytes();
                    String text = new String(bytes, StandardCharsets.UTF_8);
                    lastText.set(text);
                    return PluginHttpResponse.rawJson(200, Map.of("bytes", bytes.length));
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                } finally {
                    lastBodyClosed.set(true);
                }
            });
        }
    }

    public static class AnnotatedStreamingPlugin implements YuDreamPlugin {
        @PluginStreamingHttpEndpoint(method = "POST", path = "/stream/annotated")
        public PluginHttpResponse echo(PluginStreamingHttpRequest request) {
            try {
                int bytes = request.body().stream().readAllBytes().length;
                return PluginHttpResponse.rawJson(200, Map.of("bytes", bytes));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    public static class SecuredStreamingPlugin implements YuDreamPlugin {
        static final AtomicBoolean bodyOpened = new AtomicBoolean();

        static void reset() {
            bodyOpened.set(false);
        }

        @PluginStreamingHttpEndpoint(method = "POST", path = "/stream/secured",
                permission = "plugin:secured-stream:upload")
        public PluginHttpResponse handle(PluginStreamingHttpRequest request) {
            bodyOpened.set(true);
            return PluginHttpResponse.ok(Map.of());
        }
    }

    public static class SlowStreamingPlugin implements YuDreamPlugin {
        static CountDownLatch handlerStarted = new CountDownLatch(1);
        static final AtomicBoolean aborted = new AtomicBoolean();

        static void reset(CountDownLatch startedLatch) {
            handlerStarted = startedLatch;
            aborted.set(false);
        }

        @Override
        public void onEnable(PluginContext context) {
            context.registerStreamingHttpHandler("POST", "/stream/slow", request -> {
                try {
                    while (request.body().stream().read() != -1) {
                        // 持续消费直到流结束或被宿主取消
                    }
                    return PluginHttpResponse.rawJson(200, Map.of());
                } catch (IOException e) {
                    // 挂起读取被宿主取消（IOException）：插件自行收尾并终止
                    aborted.set(true);
                    return PluginHttpResponse.rawJson(499, Map.of("message", "分发已被宿主取消"));
                }
            });
        }
    }

    public static class ConflictingStreamingPlugin implements YuDreamPlugin {
        @Override
        public void onEnable(PluginContext context) {
            context.registerHttpHandler("POST", "/stream/dup", request -> PluginHttpResponse.ok(Map.of()));
            context.registerStreamingHttpHandler("POST", "/stream/dup", request -> PluginHttpResponse.ok(Map.of()));
        }
    }

    // ---------- 构造与工具 ----------

    /** 共享桥接实例：活动分发注册表按桥接实例隔离，dispatch 与 cancelActive 必须同源。 */
    private final PluginStreamingHttpBridge sharedBridge = new PluginStreamingHttpBridge();

    private PluginStreamingHttpBridge bridge() {
        return sharedBridge;
    }

    private PluginContextImpl.StreamingHttpRegistration registration(PluginStreamingHttpHandlerEcho handler) {
        return new PluginContextImpl.StreamingHttpRegistration(handler::handle, "", true);
    }

    @FunctionalInterface
    private interface PluginStreamingHttpHandlerEcho {
        PluginHttpResponse handle(PluginStreamingHttpRequest request);
    }

    private PluginHttpStreamingDispatchRequest streamingRequest(FakeBody body, Map<String, PluginHttpStreamingPart> parts) {
        return new PluginHttpStreamingDispatchRequest(
                PLUGIN_CODE, "POST", "/stream/echo",
                body == null ? Map.of() : Map.of("Content-Length", List.of(String.valueOf(body.declaredLength))),
                body == null ? null : 1L, List.of(),
                () -> Map.of(),
                body == null ? () -> null : (Supplier<PluginHttpStreamingBody>) () -> body,
                () -> parts
        );
    }

    /** 可观测的流式请求体：记录流打开次数与关闭状态。 */
    static class FakeBody implements PluginHttpStreamingBody {
        private final byte[] payload;
        private final long declaredLength;
        final AtomicInteger opened = new AtomicInteger();
        final AtomicBoolean closed = new AtomicBoolean();

        FakeBody(byte[] payload, long declaredLength) {
            this.payload = payload;
            this.declaredLength = declaredLength;
        }

        @Override
        public InputStream stream() {
            opened.incrementAndGet();
            return new ByteArrayInputStream(payload);
        }

        @Override
        public long declaredContentLength() {
            return declaredLength;
        }

        @Override
        public String contentType() {
            return "application/octet-stream";
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }

    /** 可观测的流式 part：跟踪每次打开的流与 dispose。 */
    static final class FakePart implements PluginHttpStreamingPart {
        private final String name;
        private final String filename;
        private final byte[] payload;
        final List<CloseTrackingInputStream> openedStreams = new ArrayList<>();
        final AtomicBoolean disposed = new AtomicBoolean();

        FakePart(String name, String filename, byte[] payload) {
            this.name = name;
            this.filename = filename;
            this.payload = payload;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String filename() {
            return filename;
        }

        @Override
        public String contentType() {
            return "application/octet-stream";
        }

        @Override
        public long size() {
            return payload.length;
        }

        @Override
        public InputStream stream() {
            CloseTrackingInputStream stream = new CloseTrackingInputStream(payload);
            openedStreams.add(stream);
            return stream;
        }

        @Override
        public void dispose() {
            disposed.set(true);
        }
    }

    static final class CloseTrackingInputStream extends InputStream {
        private final ByteArrayInputStream delegate;
        final AtomicBoolean closed = new AtomicBoolean();

        CloseTrackingInputStream(byte[] payload) {
            this.delegate = new ByteArrayInputStream(payload);
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            return delegate.read(buffer, offset, length);
        }

        @Override
        public void close() throws IOException {
            closed.set(true);
            delegate.close();
        }
    }

    private static Map<String, Object> asMap(Object body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) body;
        return map;
    }

    private JarPluginRuntimeGateway newGateway(PluginProperties properties) {
        return newGateway(properties, nullReturningProxy(FrameworkServices.class));
    }

    private JarPluginRuntimeGateway newGateway(PluginProperties properties, FrameworkServices frameworkServices) {
        properties.setDirectories(List.of(pluginDir.toString()));
        return new JarPluginRuntimeGateway(
                properties,
                frameworkServices,
                new PluginServiceRegistry(),
                new PluginAiToolRegistry(),
                new PluginExtensionRegistry(),
                new PluginGraphFrameworkService(null, null, null),
                nullReturningProxy(PluginSemanticMemoryService.class),
                new AgentRuntimeApplicationRegistry() {
                    @Override
                    public AutoCloseable register(String ownerCode, AgentApplication application) {
                        return () -> {
                        };
                    }

                    @Override
                    public Optional<AgentApplication> findByCode(String code) {
                        return Optional.empty();
                    }

                    @Override
                    public List<AgentApplication> applications() {
                        return List.of();
                    }
                },
                event -> {
                },
                null,
                new PluginDevModeProperties(),
                new PluginDevProjectCatalog(new PluginDevModeProperties(), new ObjectMapper()),
                new PluginDevDirectoryBrowser(),
                new PluginScaffoldGenerator(),
                new DevModeEnvironment() {
                    @Override
                    public boolean runningFromSource() {
                        return false;
                    }
                }
        );
    }

    @SuppressWarnings("unchecked")
    private static <T> T nullReturningProxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            Class<?> returnType = method.getReturnType();
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            if (returnType == double.class) {
                return 0d;
            }
            return null;
        });
    }

    private PluginModule module(String code, Path jar) {
        return PluginModule.builder().code(code).jarPath(jar.toString()).build();
    }

    private Path writePluginJar(String fileName, String pluginCode, String mainClass) throws IOException {
        Path jar = pluginDir.resolve(fileName);
        try (java.util.jar.JarOutputStream output = new java.util.jar.JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new java.util.jar.JarEntry("plugin.yml"));
            String yaml = "name: " + pluginCode + "\nversion: 1.0.0\nmain: " + mainClass + "\n";
            output.write(yaml.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return jar;
    }
}

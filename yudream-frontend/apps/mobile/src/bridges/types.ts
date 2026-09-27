/**
 * 原生能力桥的契约层。接口命名与形参一律平台中立：
 * Android 特有物（SAF、APK 安装等）将来以独立能力位加入，不得渗入这些通用接口。
 * iOS 实现缺席时按"能力位缺失"处理，而不是让接口出现半死不活的方法。
 */

/** 安全存储：dual token 等敏感物。Android = Keystore，iOS = Keychain。 */
export interface SecureStorageBridge {
  get(key: string): Promise<string | null>;
  set(key: string, value: string): Promise<void>;
  remove(key: string): Promise<void>;
}

export interface SseSubscription {
  close(): void;
}

export interface SseHandlers {
  /** 事件名 -> 载荷文本；服务端信封见 ai.message/ai.error 等约定 */
  onEvent(event: string, data: string): void;
  onOpen?(): void;
  /** 401/403 必须停止重连（由实现方保证一次性） */
  onError?(error: Error, status: number | null): void;
}

/** SSE：RN fetch 不支持流式读取，由桥内 polyfill（react-native-sse / 原生实现）。 */
export interface SseBridge {
  subscribe(url: string, headers: Record<string, string>, handlers: SseHandlers): SseSubscription;
}

export interface DownloadTask {
  cancel(): void;
  readonly promise: Promise<{ path: string }>;
}

/** 后台/断点续传下载；返回沙盒内最终落盘路径。 */
export interface DownloadBridge {
  download(url: string, destPath: string, headers?: Record<string, string>): DownloadTask;
}

export interface DeeplinkBridge {
  open(url: string): Promise<void>;
  canOpen(url: string): Promise<boolean>;
}

export interface NativeBridges {
  secureStorage: SecureStorageBridge;
  sse: SseBridge;
  download: DownloadBridge;
  deeplink: DeeplinkBridge;
}

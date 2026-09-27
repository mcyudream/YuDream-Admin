/**
 * 桥的第一代实现：全部基于跨平台库，双端同构。
 * 未来 iOS/Android 分化（如 SAF、厂商推送）在此处按能力位替换实现，
 * 接口（types.ts）保持不变。
 */
import * as Keychain from 'react-native-keychain';
import RNFS from 'react-native-fs';
import { Linking } from 'react-native';
import EventSource from 'react-native-sse';
import type {
  DeeplinkBridge,
  DownloadBridge,
  DownloadTask,
  NativeBridges,
  SecureStorageBridge,
  SseBridge,
  SseHandlers,
  SseSubscription,
  ScannerBridge,
} from './types';

const secureStorage: SecureStorageBridge = {
  async get(key) {
    const cred = await Keychain.getGenericPassword({ service: key });
    return cred ? cred.password : null;
  },
  async set(key, value) {
    await Keychain.setGenericPassword('yudream', value, { service: key });
  },
  async remove(key) {
    await Keychain.resetGenericPassword({ service: key });
  },
};

const sse: SseBridge = {
  subscribe(url, headers, handlers: SseHandlers): SseSubscription {
    const es = new EventSource(url, { headers, method: 'GET' });
    // 服务端信封自带 event/action 字段（见 AGENTS.md SSE 约定），
    // 统一走 message 通道由上层拆包，避免为每个事件名注册监听器。
    es.addEventListener('message', (event) => {
      if (event.data != null) {
        handlers.onEvent('message', event.data);
      }
    });
    es.addEventListener('open', () => handlers.onOpen?.());
    es.addEventListener('error', (event) => {
      // react-native-sse 不向上暴露 HTTP 状态；401/403 停流语义由上层在
      // 首次 onError 后依鉴权状态自行判断，桥保证不重连（一次性订阅）。
      const detail =
        event.type === 'error'
          ? event.message
          : event.type === 'exception'
            ? String(event.error)
            : `连接超时（${event.type}）`;
      handlers.onError?.(new Error(detail ?? 'SSE 连接失败'), null);
    });
    return {
      close() {
        es.removeAllEventListeners();
        es.close();
      },
    };
  },
};

const download: DownloadBridge = {
  download(url, destPath, headers = {}): DownloadTask {
    const job = RNFS.downloadFile({ fromUrl: url, toFile: destPath, headers });
    return {
      promise: job.promise.then(() => ({ path: destPath })),
      cancel() {
        RNFS.stopDownload(job.jobId);
      },
    };
  },
};

const deeplink: DeeplinkBridge = {
  open(url) {
    return Linking.openURL(url);
  },
  canOpen(url) {
    return Linking.canOpenURL(url);
  },
};

// v1 相机扫描未接入（模拟器亦无摄像头）；接口占位，UI 按 available() 引导手动输入/粘贴。
const scanner: ScannerBridge = {
  async scan() {
    return null;
  },
  async available() {
    return false;
  },
};

export const bridges: NativeBridges = { secureStorage, sse, download, deeplink, scanner };

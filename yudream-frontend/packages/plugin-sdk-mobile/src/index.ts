/**
 * @yudream/plugin-sdk-mobile —— 插件移动端契约（纯类型包）。
 *
 * 插件以 Module Federation remote 形态发布：
 *   exposes: { './module': './src/entry' }
 *   默认导出 React 组件（页面）。
 *
 * 本包与 react / react-native 由宿主作为 shared singleton 注入，
 * 插件构建时必须声明 shared 且不得自行打包。
 */
import type { ComponentType } from 'react';

/** 宿主注入的主题 token 结构（与宿主 T0 token 同构；插件只读消费）。 */
export interface PluginThemeTokens {
  scheme: 'light' | 'dark';
  colors: Record<string, string>;
  spacing: Record<'xs' | 'sm' | 'md' | 'lg' | 'xl', number>;
  radii: Record<'sm' | 'md' | 'lg' | 'full', number>;
  typography: Record<string, number>;
}

export type PluginPlatform = 'android' | 'ios';

export interface PluginMobileSdk {
  /** 当前主题 token；颜色一律经此消费，禁止插件写死色值。 */
  readonly theme: PluginThemeTokens;
  readonly platform: PluginPlatform;
  /** 宿主版本，供插件做 minHostVersion 运行期自查。 */
  readonly hostVersion: string;
  /** 激活域 origin；站内相对资产路径（头像/图片）用它拼接。 */
  readonly baseUrl: string;
  /** 宿主顶部导航栏能力位：插件可改标题/显隐/接管返回键，也可完全自绘头部。 */
  navigation: {
    /** 设置导航栏标题（插件内路由切换时同步调用）。 */
    setTitle(title: string): void;
    /** 显示/隐藏导航栏；隐藏时插件自绘头部并自行处理返回。 */
    setHidden(hidden: boolean): void;
    /** 接管头部返回键为应用内返回（子页→上一视图）；null 恢复默认退出应用。旧宿主为空实现。 */
    setBackAction?(action: (() => void) | null): void;
  };
  api: {
    /** 已携带鉴权与 401 刷新重试的站内请求。 */
    request<T>(path: string, options?: { method?: string; body?: unknown }): Promise<T>;
    /** 鉴权下载站内文件到本地并返回本地路径（旧宿主无此能力位时为 undefined，插件需自行回退）。 */
    download?(path: string, toFile: string): Promise<string>;
  };
  storage: {
    /** 按插件 code 命名空间隔离的键值存储。 */
    get(key: string): Promise<string | null>;
    set(key: string, value: string): Promise<void>;
    remove(key: string): Promise<void>;
  };
  sse: {
    subscribe(
      path: string,
      handlers: { onEvent(data: string): void; onError?(error: Error): void },
    ): { close(): void };
  };
  deeplink: {
    open(url: string): Promise<void>;
  };
}

/** 插件远程模块的导出形状。 */
export interface MobilePluginModule {
  default: ComponentType<Record<string, unknown>>;
}

/** plugin.yml mobile 块的镜像，供构建期与 manifest 展示。 */
export interface MobilePluginDeclaration {
  platforms: PluginPlatform[];
  minHostVersion: string;
  requiredNativeCapabilities: string[];
}

export const __SDK_VERSION__ = '0.1.0';

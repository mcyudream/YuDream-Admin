/**
 * 系统栏自适应（导航栏部分）：把主题底色与深浅模式同步到 Android 底部导航栏。
 * 状态栏颜色由 react-native-screens 的 Screen trait（RootNavigator screenOptions
 * statusBarColor）负责——RNS 会在每次屏幕挂载后强制回落其捕获的默认色，宿主侧
 * 再直接写 window.statusBarColor 会被覆盖；导航栏 RNS 不管理，经此桥设置。
 */
import { NativeModules, Platform } from 'react-native';

interface SystemBarsModule {
  applyNavColor(color: string, lightIcons: boolean): void;
}

export function applySystemBars(scheme: 'light' | 'dark', bgColor: string): void {
  if (Platform.OS !== 'android') {
    return;
  }
  // 浅色底配深色图标，深色底配浅色图标
  (NativeModules.SystemBars as SystemBarsModule | null | undefined)?.applyNavColor(
    bgColor,
    scheme === 'light',
  );
}

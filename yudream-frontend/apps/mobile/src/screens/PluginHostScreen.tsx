import React, { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, Text, View } from 'react-native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import type { ComponentType } from 'react';
import type { PluginMobileSdk } from '@yudream/plugin-sdk-mobile';
import { YdButton, YdScreen, YdText } from '@/components';
import { useTheme } from '@/core/theme/ThemeProvider';
import { loadPluginModule } from '@/core/plugins/pluginLoader';
import { buildAppSdk } from '@/core/plugins/appSdk';
import { rollback } from '@/core/plugins/bundleCache';
import { getActiveDomain } from '@/core/domains/store';
import { getPlugins } from '@/core/plugins/registry';
import type { ManifestPluginEntry } from '@/core/manifest/types';
import type { RootStackParamList } from '@/navigation/types';

type Props = NativeStackScreenProps<RootStackParamList, 'PluginHost'>;

type LoadState =
  | { kind: 'loading' }
  | { kind: 'ready'; Component: AppModuleComponent }
  | { kind: 'error'; message: string };

/** 宿主传给应用模块的入参：sdk 注入 + route + manifest 条目（含权限过滤后的卡片）。 */
export interface AppModuleProps {
  sdk: PluginMobileSdk;
  route?: string;
  /** 应用在 manifest 中的条目（adminCards 已按当前用户权限过滤）；供应用做能力显隐。 */
  entry?: ManifestPluginEntry;
}

type AppModuleComponent = ComponentType<AppModuleProps>;

/**
 * 应用容器：经 MF 运行时加载远程模块并渲染其默认导出。
 * 顶部导航栏为宿主自带样式（页底色、无投影、粗标题，与设计稿同构）；
 * 插件经 sdk.navigation 改标题/显隐（能力位），自绘头部时隐藏宿主栏。
 * 失败路径已含 last-known-good 回滚（pluginLoader 内）；
 * 仍失败则给出手动回滚入口。
 */
export function PluginHostScreen({ route, navigation }: Props) {
  const t = useTheme();
  const { code, route: appRoute } = route.params;
  const entry = useMemo(() => getPlugins().find((p) => p.code === code), [code]);
  const [state, setState] = useState<LoadState>({ kind: 'loading' });
  const [retryToken, setRetryToken] = useState(0);

  // 设计稿同款导航栏：页底色 + 无投影 + 主文本色粗标题
  useLayoutEffect(() => {
    navigation.setOptions({
      headerStyle: { backgroundColor: t.colors.bgPage },
      headerShadowVisible: false,
      headerTintColor: t.colors.textPrimary,
      headerTitleStyle: { fontWeight: t.typography.weightBold, fontSize: t.typography.sizeLg },
    });
  }, [navigation, t]);

  // 应用内子页返回接管：插件经 sdk.navigation.setBackAction 换掉头部返回键
  const backActionRef = useRef<(() => void) | null>(null);
  const applyHeaderBack = React.useCallback(() => {
    const action = backActionRef.current;
    navigation.setOptions({
      headerLeft: action
        ? () => (
            <Pressable hitSlop={12} onPress={action}>
              <Text style={{ color: t.colors.textPrimary, fontSize: 26, lineHeight: 32, paddingHorizontal: 4 }}>{'‹'}</Text>
            </Pressable>
          )
        : undefined,
    });
  }, [navigation, t]);

  const sdk = useMemo(
    () =>
      buildAppSdk(code, t, {
        setTitle: (title) => navigation.setOptions({ title }),
        setHidden: (hidden) => navigation.setOptions({ headerShown: !hidden }),
        setBackAction: (action) => {
          backActionRef.current = action;
          applyHeaderBack();
        },
      }),
    [code, t, navigation, applyHeaderBack],
  );

  // 卸载/切换应用时清掉接管，避免下一个应用继承返回行为
  useEffect(() => () => {
    backActionRef.current = null;
  }, [code]);

  useEffect(() => {
    let cancelled = false;
    setState({ kind: 'loading' });
    loadPluginModule(code)
      .then(
        (Component) =>
          !cancelled &&
          setState({ kind: 'ready', Component: Component as unknown as AppModuleComponent }),
      )
      .catch((e) =>
        !cancelled &&
        setState({
          kind: 'error',
          message: e instanceof Error ? e.message : '应用加载失败',
        }),
      );
    return () => {
      cancelled = true;
    };
  }, [code, retryToken]);

  if (state.kind === 'loading') {
    return (
      <YdScreen>
        <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', gap: t.spacing.md }}>
          <ActivityIndicator color={t.colors.accent} />
          <YdText variant="secondary">正在加载应用…</YdText>
        </View>
      </YdScreen>
    );
  }

  if (state.kind === 'error') {
    return (
      <YdScreen>
        <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', gap: t.spacing.md }}>
          <YdText style={{ color: t.colors.danger }}>{state.message}</YdText>
          <YdButton
            title="回滚到上一版本并重试"
            variant="secondary"
            onPress={() => {
              const domain = getActiveDomain();
              void (domain ? rollback(domain.id, code) : Promise.resolve(false)).then(() =>
                setRetryToken((n) => n + 1),
              );
            }}
          />
          <YdButton title="重试" variant="ghost" onPress={() => setRetryToken((n) => n + 1)} />
        </View>
      </YdScreen>
    );
  }

  const { Component } = state;
  return (
    <View style={{ flex: 1, backgroundColor: t.colors.bgPage }}>
      <Component sdk={sdk} route={appRoute} entry={entry} />
    </View>
  );
}

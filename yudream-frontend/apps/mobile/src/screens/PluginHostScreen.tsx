import React, { useEffect, useState } from 'react';
import { ActivityIndicator, View } from 'react-native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import type { ComponentType } from 'react';
import { YdButton, YdScreen, YdText } from '@/components';
import { useTheme } from '@/core/theme/ThemeProvider';
import { loadPluginModule } from '@/core/plugins/pluginLoader';
import { rollback } from '@/core/plugins/bundleCache';
import type { RootStackParamList } from '@/navigation/types';

type Props = NativeStackScreenProps<RootStackParamList, 'PluginHost'>;

type LoadState =
  | { kind: 'loading' }
  | { kind: 'ready'; Component: ComponentType<Record<string, unknown>> }
  | { kind: 'error'; message: string };

/**
 * 插件容器：经 MF 运行时加载远程模块并渲染其默认导出。
 * 失败路径已含 last-known-good 回滚（pluginLoader 内）；
 * 仍失败则给出手动回滚入口。
 */
export function PluginHostScreen({ route }: Props) {
  const t = useTheme();
  const { code } = route.params;
  const [state, setState] = useState<LoadState>({ kind: 'loading' });
  const [retryToken, setRetryToken] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setState({ kind: 'loading' });
    loadPluginModule(code)
      .then((Component) => !cancelled && setState({ kind: 'ready', Component }))
      .catch((e) =>
        !cancelled &&
        setState({
          kind: 'error',
          message: e instanceof Error ? e.message : '插件加载失败',
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
          <YdText variant="secondary">正在加载插件…</YdText>
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
            onPress={() => void rollback(code).then(() => setRetryToken((n) => n + 1))}
          />
          <YdButton title="重试" variant="ghost" onPress={() => setRetryToken((n) => n + 1)} />
        </View>
      </YdScreen>
    );
  }

  const { Component } = state;
  return (
    <View style={{ flex: 1, backgroundColor: t.colors.bgPage }}>
      <Component />
    </View>
  );
}

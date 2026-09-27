import React, { useCallback, useEffect, useState } from 'react';
import { FlatList, RefreshControl, View } from 'react-native';
import type { CompositeScreenProps } from '@react-navigation/native';
import type { BottomTabScreenProps } from '@react-navigation/bottom-tabs';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import Icon from 'react-native-vector-icons/Ionicons';
import { YdCard, YdScreen, YdText } from '@/components';
import { useTheme } from '@/core/theme/ThemeProvider';
import { onPluginsChanged, getPlugins } from '@/core/plugins/registry';
import { syncPlugins } from '@/core/plugins/pluginLoader';
import { getInstalled } from '@/core/plugins/bundleCache';
import { getActiveDomain } from '@/core/domains/store';
import type { ManifestPluginEntry } from '@/core/manifest/types';
import type { MainTabParamList, RootStackParamList } from '@/navigation/types';

type Props = CompositeScreenProps<
  BottomTabScreenProps<MainTabParamList>,
  NativeStackScreenProps<RootStackParamList>
>;

/**
 * 插件页：当前域的全部插件，下拉刷新检查更新（需登录态）。
 */
export function PluginsTabScreen({ navigation }: Props) {
  const t = useTheme();
  const [plugins, setPlugins] = useState<ManifestPluginEntry[]>(getPlugins());
  const [refreshing, setRefreshing] = useState(false);
  const [lastSync, setLastSync] = useState<string | null>(null);
  const [localVersions, setLocalVersions] = useState<Record<string, string>>({});

  useEffect(() => onPluginsChanged(setPlugins), []);

  const refreshLocal = useCallback(async () => {
    const domain = getActiveDomain();
    if (!domain) {
      return;
    }
    const entries = getPlugins();
    const map: Record<string, string> = {};
    for (const p of entries) {
      const installed = await getInstalled(domain.id, p.code);
      if (installed) {
        map[p.code] = installed.version;
      }
    }
    setLocalVersions(map);
  }, []);

  useEffect(() => {
    void refreshLocal();
  }, [refreshLocal, plugins.length]);

  const onRefresh = useCallback(async () => {
    setRefreshing(true);
    try {
      await syncPlugins();
      setLastSync(new Date().toLocaleTimeString());
      await refreshLocal();
    } catch (e) {
      console.warn('[plugins] 同步失败', e);
    } finally {
      setRefreshing(false);
    }
  }, [refreshLocal]);

  return (
    <YdScreen>
      <FlatList
        data={plugins}
        keyExtractor={(item) => item.code}
        contentContainerStyle={{ gap: t.spacing.md, paddingVertical: t.spacing.md }}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={t.colors.accent} />
        }
        ListHeaderComponent={
          <View style={{ gap: 4, paddingHorizontal: 4 }}>
            <YdText variant="title">插件</YdText>
            <YdText variant="caption">
              {lastSync ? `上次检查更新 ${lastSync} · 下拉刷新` : '下拉检查更新'}
            </YdText>
          </View>
        }
        renderItem={({ item }) => {
          const local = localVersions[item.code];
          const pendingUpdate = local && local !== item.version;
          return (
            <YdCard
              onPress={() => navigation.navigate('PluginHost', { code: item.code, title: item.code })}
            >
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: t.spacing.md }}>
                <View
                  style={{
                    width: 40,
                    height: 40,
                    borderRadius: 12,
                    backgroundColor: t.colors.fillHover,
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  <Icon name="extension-outline" size={20} color={t.colors.accent} />
                </View>
                <View style={{ flex: 1, gap: 2 }}>
                  <YdText style={{ fontWeight: t.typography.weightMedium }} numberOfLines={1}>
                    {item.code}
                  </YdText>
                  <YdText variant="caption">
                    {local ? `本地 v${local}` : '未下载'} · 最新 v{item.version}
                  </YdText>
                </View>
                {pendingUpdate ? (
                  <YdText variant="caption" style={{ color: t.colors.accent }}>
                    可更新
                  </YdText>
                ) : null}
                <Icon name="chevron-forward" size={18} color={t.colors.textTertiary} />
              </View>
            </YdCard>
          );
        }}
        ListEmptyComponent={
          <YdText variant="secondary" style={{ textAlign: 'center', marginTop: t.spacing.xl }}>
            暂无可用插件
          </YdText>
        }
      />
    </YdScreen>
  );
}

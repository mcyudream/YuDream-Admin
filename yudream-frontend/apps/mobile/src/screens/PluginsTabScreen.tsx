import React, { useCallback, useEffect, useState } from 'react';
import { FlatList, RefreshControl, View } from 'react-native';
import type { CompositeScreenProps } from '@react-navigation/native';
import type { BottomTabScreenProps } from '@react-navigation/bottom-tabs';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import Icon from 'react-native-vector-icons/Ionicons';
import { Pressable } from 'react-native';
import { YdScreen, YdText } from '@/components';
import { useTheme } from '@/core/theme/ThemeProvider';
import { onPluginsChanged, getPlugins } from '@/core/plugins/registry';
import { syncPlugins } from '@/core/plugins/pluginLoader';
import { getInstalled } from '@/core/plugins/bundleCache';
import { getActiveDomain } from '@/core/domains/store';
import { appDisplayName, type ManifestPluginEntry } from '@/core/manifest/types';
import type { MainTabParamList, RootStackParamList } from '@/navigation/types';

type Props = CompositeScreenProps<
  BottomTabScreenProps<MainTabParamList>,
  NativeStackScreenProps<RootStackParamList>
>;

/**
 * 应用页：当前域全部应用，双列卡片；下拉刷新检查更新（需登录态）。
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
      console.warn('[apps] 同步失败', e);
    } finally {
      setRefreshing(false);
    }
  }, [refreshLocal]);

  return (
    <YdScreen>
      <FlatList
        data={plugins}
        keyExtractor={(item) => item.code}
        numColumns={2}
        columnWrapperStyle={{ gap: t.spacing.md, paddingHorizontal: t.spacing.lg }}
        contentContainerStyle={{ gap: t.spacing.md, paddingVertical: t.spacing.md }}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={t.colors.accent} />
        }
        ListHeaderComponent={
          <View style={{ gap: 4, paddingHorizontal: t.spacing.lg }}>
            <YdText variant="title">应用</YdText>
            <YdText variant="caption">
              {lastSync
                ? `上次检查更新 ${lastSync} · 下拉刷新`
                : `${plugins.length} 个应用 · 下拉检查更新`}
            </YdText>
          </View>
        }
        renderItem={({ item }) => {
          const local = localVersions[item.code];
          const pendingUpdate = local && local !== item.version;
          return (
            <Pressable
              onPress={() =>
                navigation.navigate('PluginHost', {
                  code: item.code,
                  title: appDisplayName(item),
                  route: item.homeCards?.[0]?.route,
                })
              }
              android_ripple={{ color: t.colors.fillHover }}
              style={{
                flex: 1,
                borderRadius: t.radii.lg,
                borderWidth: 1,
                borderColor: t.colors.borderSubtle,
                backgroundColor: t.colors.bgSurface,
                padding: t.spacing.md,
                gap: 10,
              }}
            >
              <View
                style={{
                  width: 44,
                  height: 44,
                  borderRadius: 13,
                  backgroundColor: t.colors.fillHover,
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <Icon name={item.icon ?? 'cube-outline'} size={22} color={t.colors.accent} />
              </View>
              <View style={{ gap: 3, minHeight: 54 }}>
                <YdText numberOfLines={1} style={{ fontWeight: t.typography.weightMedium }}>
                  {appDisplayName(item)}
                </YdText>
                {item.description ? (
                  <YdText variant="caption" numberOfLines={2}>
                    {item.description}
                  </YdText>
                ) : null}
              </View>
              <YdText variant="caption" style={{ color: pendingUpdate ? t.colors.accent : t.colors.textTertiary }}>
                {pendingUpdate ? '可更新' : local ? `已安装 v${local}` : `v${item.version}`}
              </YdText>
            </Pressable>
          );
        }}
        ListEmptyComponent={
          <YdText variant="secondary" style={{ textAlign: 'center', marginTop: t.spacing.xl }}>
            暂无可用应用
          </YdText>
        }
      />
    </YdScreen>
  );
}

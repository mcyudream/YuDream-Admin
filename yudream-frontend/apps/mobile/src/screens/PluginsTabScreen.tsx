import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { FlatList, Pressable, RefreshControl, TextInput, View } from 'react-native';
import type { CompositeScreenProps } from '@react-navigation/native';
import type { BottomTabScreenProps } from '@react-navigation/bottom-tabs';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import Icon from 'react-native-vector-icons/Ionicons';
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
 * 应用页（设计稿 apps）：搜索 + 当前域全部应用双列卡片；下拉刷新检查更新（需登录态）。
 * 应用清单由域 manifest 下发，客户端不写死任何业务入口。
 */
export function PluginsTabScreen({ navigation }: Props) {
  const t = useTheme();
  const [plugins, setPlugins] = useState<ManifestPluginEntry[]>(getPlugins());
  const [refreshing, setRefreshing] = useState(false);
  const [lastSync, setLastSync] = useState<string | null>(null);
  const [localVersions, setLocalVersions] = useState<Record<string, string>>({});
  const [keyword, setKeyword] = useState('');

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

  const filtered = useMemo(() => {
    // 管理功能不进应用列表：仅管理卡片的应用走「我的 → 管理应用」；
    // 无动态源也无用户主页卡片的应用（当前用户视角无任何可用入口）不展示
    const usable = plugins.filter(
      (p) => p.homeFeed?.endpoint || (p.homeCards?.length ?? 0) > 0,
    );
    const kw = keyword.trim().toLowerCase();
    if (!kw) {
      return usable;
    }
    return usable.filter(
      (p) =>
        appDisplayName(p).toLowerCase().includes(kw) ||
        (p.description ?? '').toLowerCase().includes(kw) ||
        p.code.toLowerCase().includes(kw),
    );
  }, [plugins, keyword]);

  return (
    <YdScreen padded={false}>
      <FlatList
        data={filtered}
        keyExtractor={(item) => item.code}
        numColumns={2}
        columnWrapperStyle={{ gap: t.spacing.md, paddingHorizontal: t.spacing.lg }}
        contentContainerStyle={{ gap: t.spacing.md, paddingVertical: t.spacing.md }}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={t.colors.accent} />
        }
        ListHeaderComponent={
          <View style={{ gap: t.spacing.md, paddingHorizontal: t.spacing.lg }}>
            <YdText variant="title">应用</YdText>
            {/* 搜索：本地过滤应用名/描述/标识 */}
            <View
              style={{
                flexDirection: 'row',
                alignItems: 'center',
                gap: t.spacing.sm,
                height: 46,
                paddingHorizontal: t.spacing.md,
                borderRadius: t.radii.md,
                backgroundColor: t.colors.bgSurface,
                borderWidth: 1,
                borderColor: t.colors.borderSubtle,
              }}
            >
              <TextInput
                value={keyword}
                onChangeText={setKeyword}
                placeholder="搜索应用"
                placeholderTextColor={t.colors.textTertiary}
                returnKeyType="search"
                style={{ flex: 1, color: t.colors.textPrimary, fontSize: t.typography.sizeMd, padding: 0 }}
              />
              {keyword ? (
                <Pressable onPress={() => setKeyword('')} hitSlop={8}>
                  <Icon name="close-circle" size={16} color={t.colors.textTertiary} />
                </Pressable>
              ) : (
                <Icon name="search" size={17} color={t.colors.textTertiary} />
              )}
            </View>
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
                gap: t.spacing.sm,
              }}
            >
              <View
                style={{
                  width: 44,
                  height: 44,
                  borderRadius: t.radii.md,
                  backgroundColor: t.colors.fillHover,
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <Icon name={item.icon ?? 'cube-outline'} size={22} color={t.colors.textPrimary} />
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
            {keyword ? '没有匹配的应用' : '暂无可用应用'}
          </YdText>
        }
      />
    </YdScreen>
  );
}

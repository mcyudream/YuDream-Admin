import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { FlatList, Pressable, RefreshControl, View } from 'react-native';
import type { CompositeScreenProps } from '@react-navigation/native';
import type { BottomTabScreenProps } from '@react-navigation/bottom-tabs';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import Icon from 'react-native-vector-icons/Ionicons';
import { BannerCarousel, FeedItem, YdScreen, YdText } from '@/components';
import { useTheme } from '@/core/theme/ThemeProvider';
import {
  getActiveDomain,
  type DomainAccount,
  type DomainBanner,
} from '@/core/domains/store';
import { applyAppPrefs, getAppPrefs, loadAppPrefs, subscribeAppPrefs } from '@/core/domains/appPrefs';
import { onPluginsChanged, getPlugins } from '@/core/plugins/registry';
import {
  appDisplayName,
  type ManifestPluginEntry,
  type MobileFeedItem,
} from '@/core/manifest/types';
import { fetchAppFeed } from '@/core/manifest/homeFeed';
import type { MainTabParamList, RootStackParamList } from '@/navigation/types';

type Props = CompositeScreenProps<
  BottomTabScreenProps<MainTabParamList>,
  NativeStackScreenProps<RootStackParamList>
>;

type FeedEntry = { key: string; app: ManifestPluginEntry; item: MobileFeedItem };

interface FeedSourceState {
  items: MobileFeedItem[];
  page: number;
  hasMore: boolean;
}

const PAGE_SIZE = 20;

/**
 * 首页（设计稿 home）：问候顶栏 + 轮播图 + 纯动态时间线。
 * 动态 = 全部内容源（论坛帖子 / 最新活动 / MC 新闻…）合并后按时间倒序，
 * 不混入任何应用入口；下拉刷新重置回第一页，触底自动为仍有余量的源加载下一页。
 */
export function HomeTabScreen({ navigation }: Props) {
  const t = useTheme();
  const domain = getActiveDomain();
  const [plugins, setPlugins] = useState<ManifestPluginEntry[]>(getPlugins());
  const [account, setAccount] = useState<DomainAccount | null>(
    getActiveDomain()?.account ?? null,
  );
  const [prefsVersion, setPrefsVersion] = useState(0);

  const [sources, setSources] = useState<Record<string, FeedSourceState>>({});
  const [refreshing, setRefreshing] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const sourcesRef = useRef<Record<string, FeedSourceState>>({});
  const feedAppsRef = useRef<ManifestPluginEntry[]>([]);

  useEffect(() => onPluginsChanged(setPlugins), []);
  useEffect(() => setAccount(getActiveDomain()?.account ?? null), []);
  useEffect(() => subscribeAppPrefs(() => setPrefsVersion((v) => v + 1)), []);
  useEffect(() => setAccount(getActiveDomain()?.account ?? null), [domain?.id]);

  const visibleApps = useMemo(() => {
    void prefsVersion;
    void domain?.id;
    if (!domain) {
      return [];
    }
    return applyAppPrefs(
      plugins.map((p) => p.code),
      getAppPrefs(domain.id),
    )
      .map((code) => plugins.find((p) => p.code === code))
      .filter((p): p is ManifestPluginEntry => Boolean(p));
  }, [plugins, domain?.id, prefsVersion]);

  const feedApps = useMemo(
    () => visibleApps.filter((a) => a.homeFeed?.endpoint),
    [visibleApps],
  );
  void visibleApps;

  const reloadAll = useCallback(async () => {
    const apps = feedAppsRef.current;
    const results = await Promise.allSettled(apps.map((app) => fetchAppFeed(app, 1)));
    const next: Record<string, FeedSourceState> = {};
    apps.forEach((app, i) => {
      const result = results[i];
      next[app.code] =
        result && result.status === 'fulfilled'
          ? { items: result.value.items, page: 1, hasMore: result.value.hasMore }
          : { items: [], page: 1, hasMore: false };
    });
    sourcesRef.current = next;
    setSources(next);
  }, []);

  // 内容源应用集合变化（切域/装新应用/偏好调整）→ 重置回第一页
  const feedKey = feedApps.map((a) => a.code).join(',');
  useEffect(() => {
    feedAppsRef.current = feedApps;
    void reloadAll();
  }, [feedKey, reloadAll]);

  const banners: DomainBanner[] = domain?.branding?.homeBanners ?? [];

  const openBanner = (banner: DomainBanner) => {
    if (!banner.route) {
      return;
    }
    const hit = visibleApps.find((app) =>
      (app.homeCards ?? []).some((c) => c.route === banner.route),
    );
    if (hit) {
      navigation.navigate('PluginHost', {
        code: hit.code,
        title: appDisplayName(hit),
        route: banner.route,
      });
    }
  };

  const openContent = (app: ManifestPluginEntry, route: string) =>
    navigation.navigate('PluginHost', { code: app.code, title: appDisplayName(app), route });

  // 动态时间线：全部内容源合并后按时间倒序（论坛帖子 / 最新活动 / MC 新闻…）
  const merged: FeedEntry[] = feedApps
    .flatMap((app) =>
      (sources[app.code]?.items ?? []).map(
        (item): FeedEntry => ({ key: `${app.code}:${item.id}`, app, item }),
      ),
    )
    .sort((a, b) => Number(b.item.createTime ?? 0) - Number(a.item.createTime ?? 0));

  const onRefresh = useCallback(async () => {
    setRefreshing(true);
    try {
      await reloadAll();
    } finally {
      setRefreshing(false);
    }
  }, [reloadAll]);

  const loadMore = useCallback(async () => {
    if (loadingMore) {
      return;
    }
    const current = sourcesRef.current;
    const pending = feedAppsRef.current.filter((a) => current[a.code]?.hasMore);
    if (pending.length === 0) {
      return;
    }
    setLoadingMore(true);
    try {
      const results = await Promise.allSettled(
        pending.map((app) => fetchAppFeed(app, (current[app.code]?.page ?? 1) + 1)),
      );
      const next: Record<string, FeedSourceState> = { ...sourcesRef.current };
      pending.forEach((app, i) => {
        const result = results[i];
        const prev: FeedSourceState =
          current[app.code] ?? { items: [], page: 1, hasMore: false };
        if (result && result.status === 'fulfilled') {
          const known = new Set(prev.items.map((existing) => existing.id));
          const fresh = result.value.items.filter((incoming) => !known.has(incoming.id));
          next[app.code] = {
            items: [...prev.items, ...fresh],
            page: prev.page + 1,
            hasMore: result.value.hasMore,
          };
        }
      });
      sourcesRef.current = next;
      setSources(next);
    } finally {
      setLoadingMore(false);
    }
  }, [loadingMore]);

  if (!domain) {
    return (
      <YdScreen>
        <YdText variant="title" style={{ marginTop: t.spacing.xl }}>
          未接入域
        </YdText>
      </YdScreen>
    );
  }

  const hour = new Date().getHours();
  const greeting = hour < 6 ? '夜深了' : hour < 12 ? '早上好' : hour < 18 ? '下午好' : '晚上好';

  return (
    <YdScreen padded={false}>
      <FlatList
        data={merged}
        keyExtractor={(entry) => entry.key}
        contentContainerStyle={{
          paddingHorizontal: t.spacing.lg,
          paddingBottom: 96,
          gap: t.spacing.md,
        }}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={t.colors.accent} />
        }
        onEndReachedThreshold={0.3}
        onEndReached={() => void loadMore()}
        ListHeaderComponent={
          <View style={{ gap: t.spacing.md, paddingTop: t.spacing.sm, paddingBottom: 2 }}>
            {/* 顶栏：问候 + 域标识 + 圆形图标按钮（应用 / 刷新） */}
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: t.spacing.sm }}>
              <View style={{ flex: 1, gap: 2 }}>
                <YdText style={{ fontSize: t.typography.sizeLg, fontWeight: t.typography.weightBold }} numberOfLines={1}>
                  {greeting}
                  {account ? `，${account.nickname}` : ''}
                </YdText>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 3 }}>
                  <YdText variant="caption" numberOfLines={1}>
                    {domain.name}
                  </YdText>
                  <Icon name="chevron-down" size={13} color={t.colors.textTertiary} />
                </View>
              </View>
              <Pressable
                accessibilityRole="button"
                onPress={() => navigation.navigate('应用')}
                hitSlop={6}
                style={{
                  width: 36,
                  height: 36,
                  borderRadius: 18,
                  backgroundColor: t.colors.bgSurface,
                  borderWidth: 1,
                  borderColor: t.colors.borderSubtle,
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <Icon name="grid-outline" size={18} color={t.colors.textPrimary} />
              </Pressable>
              <Pressable
                accessibilityRole="button"
                onPress={() => void onRefresh()}
                hitSlop={6}
                style={{
                  width: 36,
                  height: 36,
                  borderRadius: 18,
                  backgroundColor: t.colors.bgSurface,
                  borderWidth: 1,
                  borderColor: t.colors.borderSubtle,
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <Icon name="refresh" size={17} color={t.colors.textSecondary} />
              </Pressable>
            </View>
            {/* 轮播图 */}
            <BannerCarousel banners={banners} onPress={openBanner} />
            {/* 动态分节标题 */}
            <View style={{ flexDirection: 'row', alignItems: 'center' }}>
              <YdText style={{ fontSize: t.typography.sizeLg, fontWeight: t.typography.weightBold }}>
                动态
              </YdText>
              <View style={{ flex: 1 }} />
              <YdText variant="caption">论坛 · 活动 · 新闻</YdText>
            </View>
          </View>
        }
        renderItem={({ item }) => (
          <FeedItem item={item.item} onPress={() => openContent(item.app, item.item.route)} />
        )}
        ItemSeparatorComponent={() => <View style={{ height: 0 }} />}
        ListEmptyComponent={
          <YdText variant="secondary" style={{ textAlign: 'center', marginTop: t.spacing.xl }}>
            暂无内容，下拉刷新试试
          </YdText>
        }
        ListFooterComponent={
          loadingMore ? (
            <YdText variant="caption" style={{ textAlign: 'center', paddingVertical: t.spacing.md }}>
              正在加载更多…
            </YdText>
          ) : null
        }
      />
    </YdScreen>
  );
}

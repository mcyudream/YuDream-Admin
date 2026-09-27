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
  hostOf,
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

type FeedEntry =
  | { kind: 'content'; key: string; app: ManifestPluginEntry; item: MobileFeedItem }
  | { kind: 'card'; key: string; app: ManifestPluginEntry; card: MobileFeedCard };

type MobileFeedCard = NonNullable<ManifestPluginEntry['homeCards']>[number];

interface FeedSourceState {
  items: MobileFeedItem[];
  page: number;
  hasMore: boolean;
}

const PAGE_SIZE = 20;

/**
 * 首页 = 轮播图 + 应用注册的内容源信息流（真实内容条目，仅内容）。
 * 下拉刷新重置回第一页；触底自动为仍有余量的应用加载下一页。
 * 无内容源的应用其主页卡片作为快捷入口排在列表末尾。
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
  const [cardEntries, setCardEntries] = useState<FeedEntry[]>([]);
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
  const cardOnlyApps = useMemo(
    () => visibleApps.filter((a) => !a.homeFeed?.endpoint && (a.homeCards?.length ?? 0) > 0),
    [visibleApps],
  );

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

  // 无内容源的应用：主页卡片作为快捷条目
  useEffect(() => {
    const entries: FeedEntry[] = cardOnlyApps.flatMap((app) =>
      (app.homeCards ?? []).map(
        (card): FeedEntry => ({ kind: 'card', key: `${app.code}:${card.id}`, app, card }),
      ),
    );
    setCardEntries(entries);
  }, [cardOnlyApps]);

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

  const merged: FeedEntry[] = [
    ...feedApps.flatMap((app) =>
      (sources[app.code]?.items ?? []).map(
        (item): FeedEntry => ({ kind: 'content', key: `${app.code}:${item.id}`, app, item }),
      ),
    ),
    ...cardEntries,
  ];

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
    <YdScreen>
      <FlatList
        data={merged}
        keyExtractor={(entry) => `${entry.kind}:${entry.key}`}
        contentContainerStyle={{ paddingBottom: t.spacing.xl }}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={t.colors.accent} />
        }
        onEndReachedThreshold={0.3}
        onEndReached={() => void loadMore()}
        ListHeaderComponent={
          <View style={{ gap: t.spacing.lg, paddingTop: t.spacing.md, paddingBottom: t.spacing.sm }}>
            {/* 顶栏：站点身份 + 轻量应用入口 */}
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: t.spacing.md }}>
              <View style={{ flex: 1 }}>
                <YdText variant="title" numberOfLines={1}>
                  {greeting}
                  {account ? `，${account.nickname}` : ''}
                </YdText>
                <YdText variant="caption" numberOfLines={1}>
                  {domain.name} · {hostOf(domain.serverUrl)}
                </YdText>
              </View>
              <Icon
                name="apps-outline"
                size={22}
                color={t.colors.textSecondary}
                onPress={() => navigation.navigate('应用')}
                hitSlop={10}
              />
              <Icon
                name="swap-horizontal"
                size={22}
                color={t.colors.textSecondary}
                onPress={() => navigation.navigate('Welcome')}
                hitSlop={10}
              />
            </View>
            {/* 轮播图 */}
            <BannerCarousel banners={banners} onPress={openBanner} />
            {/* 轻量应用入口条：常用应用 + 全部 */}
            {visibleApps.length > 0 ? (
              <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                {visibleApps.slice(0, 4).map((app) => (
                  <Pressable
                    key={app.code}
                    onPress={() =>
                      navigation.navigate('PluginHost', {
                        code: app.code,
                        title: appDisplayName(app),
                        route: app.homeCards?.[0]?.route,
                      })
                    }
                    android_ripple={{ color: t.colors.fillHover, radius: 40 }}
                    style={{ alignItems: 'center', marginRight: t.spacing.md, paddingHorizontal: t.spacing.xs }}
                  >
                    <View
                      style={{
                        width: 46,
                        height: 46,
                        borderRadius: 14,
                        backgroundColor: t.colors.bgSurface,
                        borderWidth: 1,
                        borderColor: t.colors.borderSubtle,
                        alignItems: 'center',
                        justifyContent: 'center',
                      }}
                    >
                      <Icon name={app.icon ?? 'cube-outline'} size={24} color={t.colors.accent} />
                    </View>
                    <YdText variant="caption" numberOfLines={1} style={{ marginTop: 4, maxWidth: 64 }}>
                      {appDisplayName(app)}
                    </YdText>
                  </Pressable>
                ))}
                <Pressable
                  onPress={() => navigation.navigate('应用')}
                  android_ripple={{ color: t.colors.fillHover, radius: 40 }}
                  style={{ alignItems: 'center', paddingHorizontal: t.spacing.xs }}
                >
                  <View
                    style={{
                      width: 46,
                      height: 46,
                      borderRadius: 14,
                      backgroundColor: t.colors.fillHover,
                      alignItems: 'center',
                      justifyContent: 'center',
                    }}
                  >
                    <Icon name="grid-outline" size={22} color={t.colors.textSecondary} />
                  </View>
                  <YdText variant="caption" style={{ marginTop: 4 }}>
                    全部
                  </YdText>
                </Pressable>
              </View>
            ) : null}
          </View>
        }
        renderItem={({ item }) => {
          if (item.kind === 'content') {
            return (
              <FeedItem
                item={item.item}
                onPress={() => openContent(item.app, item.item.route)}
              />
            );
          }
          return (
            <View style={{ paddingHorizontal: t.spacing.lg, paddingVertical: t.spacing.md }}>
              <YdText numberOfLines={1} style={{ fontWeight: t.typography.weightMedium }}>
                {item.card.title}
              </YdText>
              {item.card.description ? (
                <YdText variant="caption" numberOfLines={1}>
                  {item.card.description}
                </YdText>
              ) : null}
            </View>
          );
        }}
        ItemSeparatorComponent={() => (
          <View style={{ height: 1, backgroundColor: t.colors.borderSubtle }} />
        )}
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

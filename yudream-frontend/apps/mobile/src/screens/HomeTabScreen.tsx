import React, { useEffect, useMemo, useState } from 'react';
import { Pressable, ScrollView, View } from 'react-native';
import type { CompositeScreenProps } from '@react-navigation/native';
import type { BottomTabScreenProps } from '@react-navigation/bottom-tabs';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import Icon from 'react-native-vector-icons/Ionicons';
import { BannerCarousel, YdScreen, YdText } from '@/components';
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
  type MobileHomeCard,
} from '@/core/manifest/types';
import type { MainTabParamList, RootStackParamList } from '@/navigation/types';

type Props = CompositeScreenProps<
  BottomTabScreenProps<MainTabParamList>,
  NativeStackScreenProps<RootStackParamList>
>;

/** 首页内容流条目：某应用注册的一张卡片。 */
interface HomeFeedItem {
  app: ManifestPluginEntry;
  card: MobileHomeCard;
}

/**
 * 首页 = 轮播图 + 应用注册的内容条目流，仅内容。
 * 应用排布与展示切换收在「域管理」（管理员定默认：服务端应用启用与顺序；
 * 用户覆盖：本地隐藏/排序）；头部仅保留一个轻量应用入口。
 */
export function HomeTabScreen({ navigation }: Props) {
  const t = useTheme();
  const domain = getActiveDomain();
  const [plugins, setPlugins] = useState<ManifestPluginEntry[]>(getPlugins());
  const [account, setAccount] = useState<DomainAccount | null>(
    getActiveDomain()?.account ?? null,
  );
  const [prefsVersion, setPrefsVersion] = useState(0);

  useEffect(() => onPluginsChanged(setPlugins), []);
  useEffect(() => setAccount(getActiveDomain()?.account ?? null), []);
  useEffect(() => subscribeAppPrefs(() => setPrefsVersion((v) => v + 1)), []);
  // 域变化时重载偏好（切域）
  useEffect(() => {
    const d = getActiveDomain();
    if (d) {
      void loadAppPrefs(d.id);
    }
    setAccount(d?.account ?? null);
  }, [domain?.id]);

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

  const feed = useMemo<HomeFeedItem[]>(
    () =>
      visibleApps.flatMap((app) =>
        (app.homeCards ?? []).map((card) => ({ app, card })),
      ),
    [visibleApps],
  );

  const banners = domain?.branding?.homeBanners ?? [];

  const openBanner = (banner: DomainBanner) => {
    if (!banner.route) {
      return;
    }
    // 路由归属：优先命中注册了该路由的应用
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
      <ScrollView contentContainerStyle={{ gap: t.spacing.lg, paddingBottom: t.spacing.xl }}>
        {/* 顶栏：站点身份 + 轻量应用入口 */}
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: t.spacing.md, paddingTop: t.spacing.md }}>
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

        {/* 内容条目流：仅内容 */}
        {feed.length > 0 ? (
          <View>
            {feed.map(({ app, card }, i) => (
              <View key={`${app.code}:${card.id}`}>
                <FeedItemRow
                  icon={card.icon}
                  title={card.title}
                  description={card.description}
                  source={appDisplayName(app)}
                  onPress={() =>
                    navigation.navigate('PluginHost', {
                      code: app.code,
                      title: appDisplayName(app),
                      route: card.route,
                    })
                  }
                />
                {i < feed.length - 1 ? (
                  <View style={{ height: 1, backgroundColor: t.colors.borderSubtle, marginHorizontal: 4 }} />
                ) : null}
              </View>
            ))}
          </View>
        ) : (
          <YdText variant="secondary" style={{ textAlign: 'center', marginTop: t.spacing.lg }}>
            暂无内容，安装的应用会在这里展示动态
          </YdText>
        )}
      </ScrollView>
    </YdScreen>
  );
}

interface FeedItemRowProps {
  icon: string;
  title: string;
  description: string;
  source: string;
  onPress: () => void;
}

function FeedItemRow({ icon, title, description, source, onPress }: FeedItemRowProps) {
  const t = useTheme();
  return (
    <Pressable
      onPress={onPress}
      android_ripple={{ color: t.colors.fillHover }}
      style={({ pressed }) => ({
        flexDirection: 'row',
        alignItems: 'center',
        gap: t.spacing.md,
        paddingVertical: t.spacing.md,
        paddingHorizontal: 4,
        backgroundColor: pressed ? t.colors.fillHover : 'transparent',
      })}
    >
      <View
        style={{
          width: 46,
          height: 46,
          borderRadius: 12,
          backgroundColor: t.colors.fillHover,
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <Icon name={icon} size={24} color={t.colors.accent} />
      </View>
      <View style={{ flex: 1, gap: 2 }}>
        <YdText numberOfLines={1} style={{ fontSize: 16, fontWeight: t.typography.weightMedium }}>
          {title}
        </YdText>
        {description ? (
          <YdText variant="secondary" numberOfLines={2}>
            {description}
          </YdText>
        ) : null}
        <YdText variant="caption" style={{ color: t.colors.textTertiary }}>
          {source}
        </YdText>
      </View>
      <Icon name="chevron-forward" size={18} color={t.colors.textTertiary} />
    </Pressable>
  );
}

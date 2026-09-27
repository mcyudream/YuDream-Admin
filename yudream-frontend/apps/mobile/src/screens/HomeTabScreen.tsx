import React, { useEffect, useMemo, useState } from 'react';
import { ScrollView, View } from 'react-native';
import type { CompositeScreenProps } from '@react-navigation/native';
import type { BottomTabScreenProps } from '@react-navigation/bottom-tabs';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import Icon from 'react-native-vector-icons/Ionicons';
import { YdCard, YdMark, YdScreen, YdText } from '@/components';
import { useTheme } from '@/core/theme/ThemeProvider';
import { getActiveDomain, hostOf, type DomainAccount } from '@/core/domains/store';
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

/** 主页信息流条目：某应用注册的一张卡片。 */
interface HomeFeedItem {
  app: ManifestPluginEntry;
  card: MobileHomeCard;
}

/**
 * 首页（域内容）：应用注册的主页卡片信息流 + 我的应用网格。
 * 应用经 plugin.yml mobile.home.cards 向主页注册内容；顺序/可见性由域管理维护。
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

  const openApp = (app: ManifestPluginEntry, route?: string) =>
    navigation.navigate('PluginHost', {
      code: app.code,
      title: appDisplayName(app),
      route,
    });

  return (
    <YdScreen>
      <ScrollView contentContainerStyle={{ gap: t.spacing.lg, paddingBottom: t.spacing.xl }}>
        {/* 域头：标识 + 问候 */}
        <View style={{ gap: t.spacing.md, paddingTop: t.spacing.md }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: t.spacing.md }}>
            <YdMark name={domain.name} size={52} />
            <View style={{ flex: 1, gap: 2 }}>
              <YdText variant="title" numberOfLines={1}>
                {greeting}
                {account ? `，${account.nickname}` : ''}
              </YdText>
              <YdText variant="caption" numberOfLines={1}>
                {domain.name} · {hostOf(domain.serverUrl)}
              </YdText>
            </View>
            <Icon
              name="swap-horizontal"
              size={22}
              color={t.colors.textSecondary}
              onPress={() => navigation.navigate('Welcome')}
              hitSlop={10}
            />
          </View>
          {!domain.mobileEnabled ? (
            <YdText variant="caption" style={{ color: t.colors.warning }}>
              该站点未启用移动能力，仅提供基础功能
            </YdText>
          ) : null}
        </View>

        {/* 主页内容：应用注册的卡片信息流 */}
        {feed.length > 0 ? (
          <View style={{ gap: t.spacing.md }}>
            <YdText variant="secondary" style={{ fontWeight: t.typography.weightMedium }}>
              主页内容
            </YdText>
            {feed.map(({ app, card }) => (
              <YdCard key={`${app.code}:${card.id}`} onPress={() => openApp(app, card.route)}>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: t.spacing.md }}>
                  <View
                    style={{
                      width: 44,
                      height: 44,
                      borderRadius: 12,
                      backgroundColor: t.colors.fillHover,
                      alignItems: 'center',
                      justifyContent: 'center',
                    }}
                  >
                    <Icon name={card.icon} size={22} color={t.colors.accent} />
                  </View>
                  <View style={{ flex: 1, gap: 2 }}>
                    <YdText style={{ fontWeight: t.typography.weightMedium }} numberOfLines={1}>
                      {card.title}
                    </YdText>
                    {card.description ? (
                      <YdText variant="caption" numberOfLines={2}>
                        {card.description}
                      </YdText>
                    ) : null}
                    <YdText variant="caption" style={{ color: t.colors.textTertiary }}>
                      来自 {appDisplayName(app)}
                    </YdText>
                  </View>
                  <Icon name="chevron-forward" size={18} color={t.colors.textTertiary} />
                </View>
              </YdCard>
            ))}
          </View>
        ) : null}

        {/* 我的应用：图标网格 */}
        <View style={{ gap: t.spacing.md }}>
          <YdText variant="secondary" style={{ fontWeight: t.typography.weightMedium }}>
            我的应用
          </YdText>
          {visibleApps.length > 0 ? (
            <YdCard>
              <View style={{ flexDirection: 'row', flexWrap: 'wrap' }}>
                {visibleApps.map((app) => (
                  <View
                    key={app.code}
                    style={{ width: '25%', alignItems: 'center', paddingVertical: t.spacing.md }}
                  >
                    <Icon
                      name={app.icon ?? 'cube-outline'}
                      size={30}
                      color={t.colors.accent}
                      onPress={() => openApp(app, app.homeCards?.[0]?.route)}
                    />
                    <YdText
                      variant="caption"
                      numberOfLines={1}
                      style={{ marginTop: 6, maxWidth: '88%', textAlign: 'center' }}
                    >
                      {appDisplayName(app)}
                    </YdText>
                  </View>
                ))}
              </View>
            </YdCard>
          ) : (
            <YdText variant="secondary" style={{ textAlign: 'center', marginTop: t.spacing.sm }}>
              暂无可用应用，联网后将自动同步
            </YdText>
          )}
        </View>
      </ScrollView>
    </YdScreen>
  );
}

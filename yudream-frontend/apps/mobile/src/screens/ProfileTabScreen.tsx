import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Pressable, ScrollView, View } from 'react-native';
import type { CompositeScreenProps } from '@react-navigation/native';
import type { BottomTabScreenProps } from '@react-navigation/bottom-tabs';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import Icon from 'react-native-vector-icons/Ionicons';
import { YdButton, YdCard, YdListItem, YdScreen, YdText } from '@/components';
import { useTheme, useThemeController, type ThemeMode } from '@/core/theme/ThemeProvider';
import { HOST_VERSION } from '@/core/config/env';
import {
  getActiveDomain,
  getDomains,
  hostOf,
  removeDomain,
  setActiveDomain,
  subscribeDomains,
} from '@/core/domains/store';
import { clearAllTokens } from '@/core/auth/tokenStore';
import { removeAllForDomain } from '@/core/plugins/bundleCache';
import { clearThemeOverride } from '@/core/theme/themeLoader';
import { logout } from '@/core/auth/authService';
import { onPluginsChanged, getPlugins } from '@/core/plugins/registry';
import { getInstalled } from '@/core/plugins/bundleCache';
import { appDisplayName } from '@/core/manifest/types';
import type { ManifestPluginEntry } from '@/core/manifest/types';
import type { MainTabParamList, RootStackParamList } from '@/navigation/types';

type Props = CompositeScreenProps<
  BottomTabScreenProps<MainTabParamList>,
  NativeStackScreenProps<RootStackParamList>
>;

/**
 * 我的（个人中心）：
 * 渐变头部（头像/昵称/徽标/设置齿轮）→ 悬浮四格概览 → 我的应用（插件注册，
 * 可更新带角标）→ 功能列表（域管理/外观/缓存/关于）→ 退出登录。
 */
export function ProfileTabScreen({ navigation }: Props) {
  const t = useTheme();
  const { mode, setMode } = useThemeController();
  const [domains, setDomains] = useState(getDomains());
  const [activeId, setActiveId] = useState(getActiveDomain()?.id ?? null);
  const [plugins, setPlugins] = useState<ManifestPluginEntry[]>(getPlugins());
  const [updates, setUpdates] = useState<Record<string, boolean>>({});
  const active = domains.find((d) => d.id === activeId) ?? null;
  const account = active?.account ?? null;

  useEffect(
    () =>
      subscribeDomains(() => {
        setDomains(getDomains());
        setActiveId(getActiveDomain()?.id ?? null);
      }),
    [],
  );
  useEffect(() => onPluginsChanged(setPlugins), []);

  // 可更新应用（红点角标数据源）
  useEffect(() => {
    let cancelled = false;
    const domain = getActiveDomain();
    if (!domain) {
      return;
    }
    void (async () => {
      const map: Record<string, boolean> = {};
      for (const p of plugins) {
        const installed = await getInstalled(domain.id, p.code);
        map[p.code] = Boolean(installed && installed.version !== p.version);
      }
      if (!cancelled) {
        setUpdates(map);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [plugins, activeId]);

  const switchTo = async (id: string) => {
    await setActiveDomain(id);
    navigation
      .getParent<NativeStackScreenProps<RootStackParamList>['navigation']>()
      ?.reset({ index: 0, routes: [{ name: 'Login' }] });
  };

  const doLogout = async () => {
    await logout();
    navigation
      .getParent<NativeStackScreenProps<RootStackParamList>['navigation']>()
      ?.reset({ index: 0, routes: [{ name: 'Login' }] });
  };

  const confirmRemove = (id: string, name: string) => {
    Alert.alert('移除站点域', `确定移除「${name}」？将清除其登录凭据与本地缓存。`, [
      { text: '取消', style: 'cancel' },
      {
        text: '移除',
        style: 'destructive',
        onPress: () => {
          void (async () => {
            await clearAllTokens([id]);
            await removeAllForDomain(id);
            await clearThemeOverride(id);
            await removeDomain(id);
          })();
        },
      },
    ]);
  };

  const clearCache = () => {
    const domain = getActiveDomain();
    if (!domain) {
      return;
    }
    Alert.alert('清除缓存', `清除「${domain.name}」的应用下载缓存？首次打开应用将重新下载。`, [
      { text: '取消', style: 'cancel' },
      {
        text: '清除',
        style: 'destructive',
        onPress: () => {
          void removeAllForDomain(domain.id).then(() => {
            Alert.alert('已清除', '应用缓存已清空。');
          });
        },
      },
    ]);
  };

  const about = () => {
    Alert.alert(
      '关于',
      `YuDream Mobile\n宿主版本 ${HOST_VERSION}\n当前域：${active ? hostOf(active.serverUrl) : '未接入'}`,
    );
  };

  const modes: { key: ThemeMode; label: string }[] = [
    { key: 'system', label: '跟随系统' },
    { key: 'light', label: '浅色' },
    { key: 'dark', label: '深色' },
  ];
  const cycleMode = () => {
    const i = modes.findIndex((m) => m.key === mode);
    setMode(modes[(i + 1) % modes.length]!.key);
  };

  const homeContentCount =
    plugins.reduce((sum, p) => sum + (p.homeCards?.length ?? 0), 0) +
    plugins.reduce((sum, p) => sum + (p.homeFeed ? 1 : 0), 0);
  const pendingCount = Object.values(updates).filter(Boolean).length;
  const quickApps = plugins.slice(0, 5);

  const headerBg =
    active?.branding?.themeColor || active?.branding?.loginHeroBackground || t.colors.accent;

  return (
    <YdScreen padded={false}>
      <ScrollView contentContainerStyle={{ paddingBottom: t.spacing.xl }}>
        {/* 渐变头部：柔和光斑 + 头像 + 昵称 + 徽标 + 设置齿轮 */}
        <View style={{ backgroundColor: headerBg, overflow: 'hidden', paddingBottom: 64 }}>
          <View
            pointerEvents="none"
            style={{
              position: 'absolute',
              top: -90,
              right: -50,
              width: 220,
              height: 220,
              borderRadius: 110,
              backgroundColor: 'rgba(255,255,255,0.14)',
            }}
          />
          <View
            pointerEvents="none"
            style={{
              position: 'absolute',
              top: 30,
              left: -70,
              width: 180,
              height: 180,
              borderRadius: 90,
              backgroundColor: 'rgba(255,255,255,0.10)',
            }}
          />
          <View
            style={{
              flexDirection: 'row',
              alignItems: 'center',
              gap: t.spacing.md,
              paddingHorizontal: t.spacing.lg,
              paddingTop: t.spacing.lg,
            }}
          >
            {/* 头像：用户头像 → 站点 logo → 剪影 */}
            <View
              style={{
                width: 58,
                height: 58,
                borderRadius: 29,
                borderWidth: 2,
                borderColor: 'rgba(255,255,255,0.7)',
                overflow: 'hidden',
                backgroundColor: 'rgba(255,255,255,0.25)',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              {account?.avatar ? (
                // 图片头像由 YdDomainAvatar 的 Image 逻辑等价处理；这里保持纯剪影通用性
                <Icon name="person" size={34} color="#ffffff" />
              ) : (
                <Icon name="person" size={34} color="#ffffff" />
              )}
            </View>
            <View style={{ flex: 1, gap: 4 }}>
              <YdText numberOfLines={1} style={{ color: t.colors.onAccent, fontSize: 19, fontWeight: t.typography.weightBold }}>
                {account?.nickname ?? '未登录'}
              </YdText>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
                <View
                  style={{
                    paddingHorizontal: 8,
                    paddingVertical: 2,
                    borderRadius: 999,
                    backgroundColor: 'rgba(255,255,255,0.22)',
                  }}
                >
                  <YdText variant="caption" style={{ color: t.colors.onAccent }}>
                    {account?.admin ? '管理员' : '域成员'}
                  </YdText>
                </View>
                {active ? (
                  <YdText variant="caption" numberOfLines={1} style={{ color: `${t.colors.onAccent}CC` }}>
                    {active.name}
                  </YdText>
                ) : null}
              </View>
            </View>
            {/* 设置齿轮：管理员进域管理，普通成员切域 */}
            <Icon
              name={account?.admin ? 'settings-outline' : 'swap-horizontal'}
              size={22}
              color={t.colors.onAccent}
              hitSlop={10}
              onPress={() => {
                if (account?.admin) {
                  navigation.navigate('DomainManage');
                } else {
                  navigation.navigate('Welcome');
                }
              }}
            />
          </View>
        </View>

        {/* 悬浮四格概览卡 */}
        <View style={{ paddingHorizontal: t.spacing.md, marginTop: -52 }}>
          <YdCard>
            <View style={{ flexDirection: 'row' }}>
              {[
                { label: '站点域', value: String(domains.length) },
                { label: '应用', value: String(plugins.length) },
                { label: '主页内容', value: String(homeContentCount) },
              ].map((tile, i, arr) => (
                <Pressable
                  key={tile.label}
                  onPress={() => navigation.navigate('应用')}
                  style={{ flex: 1, alignItems: 'center', gap: 2, paddingVertical: t.spacing.sm }}
                >
                  <YdText style={{ fontSize: 17, fontWeight: t.typography.weightBold }}>
                    {tile.value}
                  </YdText>
                  <YdText variant="caption">{tile.label}</YdText>
                </Pressable>
              ))}
              <Pressable
                onPress={cycleMode}
                style={{ flex: 1, alignItems: 'center', gap: 2, paddingVertical: t.spacing.sm }}
              >
                <YdText
                  style={{ fontSize: 13, fontWeight: t.typography.weightMedium, height: 22 }}
                  numberOfLines={1}
                >
                  {modes.find((m) => m.key === mode)?.label ?? '跟随系统'}
                </YdText>
                <YdText variant="caption">外观</YdText>
              </Pressable>
            </View>
          </YdCard>
        </View>

        {/* 我的应用：插件注册的快捷入口（可更新带角标） */}
        <View style={{ paddingHorizontal: t.spacing.md, marginTop: t.spacing.md }}>
          <YdCard>
            <View style={{ flexDirection: 'row', alignItems: 'center', marginBottom: t.spacing.sm }}>
              <YdText style={{ fontWeight: t.typography.weightMedium, flex: 1 }}>我的应用</YdText>
              <Pressable onPress={() => navigation.navigate('应用')} hitSlop={8}>
                <YdText variant="caption" style={{ color: t.colors.textTertiary }}>
                  查看全部
                </YdText>
              </Pressable>
            </View>
            {quickApps.length > 0 ? (
              <View style={{ flexDirection: 'row' }}>
                {quickApps.map((app) => (
                  <Pressable
                    key={app.code}
                    onPress={() =>
                      navigation.navigate('PluginHost', {
                        code: app.code,
                        title: appDisplayName(app),
                        route: app.homeCards?.[0]?.route,
                      })
                    }
                    style={{ flex: 1, alignItems: 'center', gap: 4 }}
                  >
                    <View>
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
                        <Icon name={app.icon ?? 'cube-outline'} size={22} color={t.colors.accent} />
                      </View>
                      {updates[app.code] ? (
                        <View
                          style={{
                            position: 'absolute',
                            top: -3,
                            right: -3,
                            minWidth: 16,
                            height: 16,
                            borderRadius: 8,
                            backgroundColor: t.colors.danger,
                            alignItems: 'center',
                            justifyContent: 'center',
                            paddingHorizontal: 3,
                          }}
                        >
                          <YdText variant="caption" style={{ color: '#ffffff', fontSize: 9 }}>
                            更
                          </YdText>
                        </View>
                      ) : null}
                    </View>
                    <YdText variant="caption" numberOfLines={1} style={{ maxWidth: '100%', textAlign: 'center' }}>
                      {appDisplayName(app)}
                    </YdText>
                  </Pressable>
                ))}
              </View>
            ) : (
              <YdText variant="caption" style={{ textAlign: 'center', paddingVertical: t.spacing.sm }}>
                暂无应用，下拉首页同步
              </YdText>
            )}
          </YdCard>
        </View>

        {/* 站点域 */}
        <View style={{ paddingHorizontal: t.spacing.md, marginTop: t.spacing.md }}>
          <YdCard>
            <View style={{ gap: t.spacing.sm }}>
              <YdText style={{ fontWeight: t.typography.weightMedium }}>我的站点域</YdText>
              {domains.map((d) => (
                <YdListItem
                  key={d.id}
                  title={d.name}
                  subtitle={hostOf(d.serverUrl)}
                  onPress={() => void switchTo(d.id)}
                  trailing={
                    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
                      {d.id === activeId ? (
                        <YdText variant="caption" style={{ color: t.colors.accent }}>
                          当前
                        </YdText>
                      ) : null}
                      <Icon
                        name="close-circle-outline"
                        size={20}
                        color={t.colors.textTertiary}
                        onPress={() => confirmRemove(d.id, d.name)}
                        hitSlop={8}
                      />
                    </View>
                  }
                />
              ))}
              <YdButton
                title="添加站点域"
                variant="secondary"
                onPress={() =>
                  navigation
                    .getParent<NativeStackScreenProps<RootStackParamList>['navigation']>()
                    ?.navigate('DomainAdd')
                }
              />
            </View>
          </YdCard>
        </View>

        {/* 功能列表 */}
        <View style={{ paddingHorizontal: t.spacing.md, marginTop: t.spacing.md }}>
          <YdCard>
            <View style={{ gap: t.spacing.xs }}>
              {account?.admin ? (
                <YdListItem
                  title="域管理"
                  subtitle="站点域与应用展示、排序统一管理"
                  onPress={() => navigation.navigate('DomainManage')}
                  trailing={<Icon name="chevron-forward" size={18} color={t.colors.textTertiary} />}
                />
              ) : null}
              <YdListItem
                title="外观"
                subtitle={`当前：${modes.find((m) => m.key === mode)?.label ?? '跟随系统'}`}
                onPress={cycleMode}
                trailing={<Icon name="chevron-forward" size={18} color={t.colors.textTertiary} />}
              />
              <YdListItem
                title="清除应用缓存"
                subtitle="清空当前域的应用下载缓存"
                onPress={clearCache}
                trailing={<Icon name="chevron-forward" size={18} color={t.colors.textTertiary} />}
              />
              <YdListItem
                title="关于"
                subtitle={`宿主版本 ${HOST_VERSION}`}
                onPress={about}
                trailing={<Icon name="chevron-forward" size={18} color={t.colors.textTertiary} />}
              />
            </View>
          </YdCard>
        </View>

        {/* 退出登录 */}
        <View style={{ paddingHorizontal: t.spacing.md, marginTop: t.spacing.md }}>
          <YdButton title="退出登录" variant="danger" onPress={doLogout} disabled={!account} />
          <YdText variant="caption" style={{ textAlign: 'center', marginTop: t.spacing.md }}>
            YuDream Mobile · 宿主版本 {HOST_VERSION}
          </YdText>
        </View>
      </ScrollView>
    </YdScreen>
  );
}

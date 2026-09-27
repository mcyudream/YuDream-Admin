import React, { useState } from 'react';
import { Alert, ScrollView, View } from 'react-native';
import type { CompositeScreenProps } from '@react-navigation/native';
import type { BottomTabScreenProps } from '@react-navigation/bottom-tabs';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import Icon from 'react-native-vector-icons/Ionicons';
import { YdButton, YdCard, YdDomainAvatar, YdListItem, YdScreen, YdText } from '@/components';
import { useTheme, useThemeController, type ThemeMode } from '@/core/theme/ThemeProvider';
import { HOST_VERSION } from '@/core/config/env';
import { resolveAssetUrl } from '@/core/domains/assetUrl';
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
import type { MainTabParamList, RootStackParamList } from '@/navigation/types';

type Props = CompositeScreenProps<
  BottomTabScreenProps<MainTabParamList>,
  NativeStackScreenProps<RootStackParamList>
>;

/**
 * 我的：账户、域管理、外观、关于。
 */
export function ProfileTabScreen({ navigation }: Props) {
  const t = useTheme();
  const { mode, setMode } = useThemeController();
  const [domains, setDomains] = useState(getDomains());
  const [activeId, setActiveId] = useState(getActiveDomain()?.id ?? null);
  const active = domains.find((d) => d.id === activeId) ?? null;

  // 域变化时刷新（含切域、添加、移除）
  React.useEffect(
    () =>
      subscribeDomains(() => {
        setDomains(getDomains());
        setActiveId(getActiveDomain()?.id ?? null);
      }),
    [],
  );

  const switchTo = async (id: string) => {
    await setActiveDomain(id);
    navigation.getParent<NativeStackScreenProps<RootStackParamList>['navigation']>()?.reset({ index: 0, routes: [{ name: 'Login' }] });
  };

  const doLogout = async () => {
    await logout();
    navigation.getParent<NativeStackScreenProps<RootStackParamList>['navigation']>()?.reset({ index: 0, routes: [{ name: 'Login' }] });
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

  const modes: { key: ThemeMode; label: string }[] = [
    { key: 'system', label: '跟随系统' },
    { key: 'light', label: '浅色' },
    { key: 'dark', label: '深色' },
  ];

  return (
    <YdScreen>
      <ScrollView contentContainerStyle={{ gap: t.spacing.lg, paddingVertical: t.spacing.md }}>
        {/* 账户卡 */}
        <YdCard>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: t.spacing.md }}>
            <YdDomainAvatar
              name={active?.account?.nickname ?? active?.name ?? 'Y'}
              logoUrl={resolveAssetUrl(active?.serverUrl ?? '', active?.branding?.logo)}
              size={52}
            />
            <View style={{ flex: 1, gap: 2 }}>
              <YdText style={{ fontWeight: t.typography.weightMedium }}>
                {active?.account?.nickname ?? '未登录'}
              </YdText>
              <YdText variant="caption" numberOfLines={1}>
                {active ? hostOf(active.serverUrl) : ''}
              </YdText>
            </View>
          </View>
          {!active?.account ? (
            <View style={{ marginTop: t.spacing.md }}>
              <YdButton
                title="去登录"
                onPress={() =>
                  navigation
                    .getParent<NativeStackScreenProps<RootStackParamList>['navigation']>()
                    ?.reset({ index: 0, routes: [{ name: 'Login' }] })
                }
              />
            </View>
          ) : null}
        </YdCard>

        {/* 域管理 */}
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
              onPress={() => navigation.getParent<NativeStackScreenProps<RootStackParamList>['navigation']>()?.navigate('DomainAdd')}
            />
          </View>
        </YdCard>

        {/* 外观 */}
        <YdCard>
          <YdText style={{ fontWeight: t.typography.weightMedium }}>外观</YdText>
          {modes.map((m) => (
            <YdListItem
              key={m.key}
              title={m.label}
              onPress={() => setMode(m.key)}
              trailing={mode === m.key ? <YdText variant="link">当前</YdText> : null}
            />
          ))}
        </YdCard>

        {/* 会话 */}
        <YdButton title="退出登录" variant="danger" onPress={doLogout} disabled={!active?.account} />
        <YdText variant="caption" style={{ textAlign: 'center' }}>
          YuDream Mobile · 宿主版本 {HOST_VERSION}
        </YdText>
      </ScrollView>
    </YdScreen>
  );
}

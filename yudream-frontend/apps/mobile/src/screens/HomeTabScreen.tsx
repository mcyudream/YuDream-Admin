import React, { useEffect, useState } from 'react';
import { FlatList, View } from 'react-native';
import type { CompositeScreenProps } from '@react-navigation/native';
import type { BottomTabScreenProps } from '@react-navigation/bottom-tabs';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import Icon from 'react-native-vector-icons/Ionicons';
import { YdCard, YdMark, YdScreen, YdText } from '@/components';
import { useTheme } from '@/core/theme/ThemeProvider';
import { getActiveDomain, getDomains, hostOf, type DomainAccount } from '@/core/domains/store';
import { onPluginsChanged, getPlugins } from '@/core/plugins/registry';
import type { ManifestPluginEntry } from '@/core/manifest/types';
import type { MainTabParamList, RootStackParamList } from '@/navigation/types';

type Props = CompositeScreenProps<
  BottomTabScreenProps<MainTabParamList>,
  NativeStackScreenProps<RootStackParamList>
>;

/**
 * 首页（域内容）：激活域概览 + 账户卡 + 插件快捷入口。
 */
export function HomeTabScreen({ navigation }: Props) {
  const t = useTheme();
  const domain = getActiveDomain();
  const [plugins, setPlugins] = useState<ManifestPluginEntry[]>(getPlugins());
  const [account, setAccount] = useState<DomainAccount | null>(
    getActiveDomain()?.account ?? null,
  );

  useEffect(() => onPluginsChanged(setPlugins), []);
  useEffect(() => setAccount(getActiveDomain()?.account ?? null), []);
  // 激活域变化（切域）时同步
  useEffect(() => {
    const timer = setInterval(() => setAccount(getActiveDomain()?.account ?? null), 3000);
    return () => clearInterval(timer);
  }, []);

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
        data={plugins}
        keyExtractor={(item) => item.code}
        contentContainerStyle={{ gap: t.spacing.md, paddingBottom: t.spacing.xl }}
        ListHeaderComponent={
          <View style={{ gap: t.spacing.md, paddingTop: t.spacing.md, paddingBottom: 4 }}>
            {/* 域概览 */}
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: t.spacing.md }}>
              <YdMark name={domain.name} size={52} />
              <View style={{ flex: 1, gap: 2 }}>
                <YdText variant="title" numberOfLines={1}>
                  {domain.name}
                </YdText>
                <YdText variant="caption" numberOfLines={1}>
                  {hostOf(domain.serverUrl)}
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

            {/* 问候 + 账户 */}
            <YdCard>
              <View style={{ gap: 4 }}>
                <YdText>
                  {greeting}
                  {account ? `，${account.nickname}` : ''}
                </YdText>
                <YdText variant="secondary">
                  {account ? `@${account.username}` : '未登录'}
                </YdText>
                {!domain.mobileEnabled ? (
                  <YdText variant="caption" style={{ color: t.colors.warning }}>
                    该站点未启用移动能力，仅提供基础功能
                  </YdText>
                ) : null}
              </View>
            </YdCard>

            {plugins.length > 0 ? (
              <YdText variant="secondary" style={{ fontWeight: t.typography.weightMedium }}>
                插件快捷入口
              </YdText>
            ) : null}
          </View>
        }
        renderItem={({ item }) => (
          <YdCard onPress={() => navigation.navigate('PluginHost', { code: item.code, title: item.code })}>
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
                <YdText variant="caption">v{item.version}</YdText>
              </View>
              <Icon name="chevron-forward" size={18} color={t.colors.textTertiary} />
            </View>
          </YdCard>
        )}
        ListEmptyComponent={
          <YdText variant="secondary" style={{ textAlign: 'center', marginTop: t.spacing.lg }}>
            暂无可用插件，联网后将自动同步
          </YdText>
        }
      />
    </YdScreen>
  );
}

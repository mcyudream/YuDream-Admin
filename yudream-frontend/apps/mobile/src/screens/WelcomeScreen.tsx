import React, { useEffect, useState } from 'react';
import { resolveAssetUrl } from '@/core/domains/assetUrl';
import { FlatList, View } from 'react-native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import Icon from 'react-native-vector-icons/Ionicons';
import { YdButton, YdCard, YdDomainAvatar, YdScreen, YdText } from '@/components';
import { useTheme } from '@/core/theme/ThemeProvider';
import {
  getActiveDomain,
  getDomains,
  hostOf,
  removeDomain,
  setActiveDomain,
  subscribeDomains,
  type Domain,
} from '@/core/domains/store';
import { isAuthenticated } from '@/core/auth/authService';
import type { RootStackParamList } from '@/navigation/types';

type Props = NativeStackScreenProps<RootStackParamList, 'Welcome'>;

/**
 * 域列表（欢迎页 / 切换域）：
 * - 无域：品牌欢迎态 + 添加入口
 * - 有域：点击切换激活域；已登录的域直接进主界面，未登录进登录页
 */
export function WelcomeScreen({ navigation }: Props) {
  const t = useTheme();
  const [domains, setDomains] = useState<Domain[]>(getDomains());
  const [activeId, setActiveId] = useState<string | null>(getActiveDomain()?.id ?? null);

  useEffect(
    () =>
      subscribeDomains(() => {
        setDomains(getDomains());
        setActiveId(getActiveDomain()?.id ?? null);
      }),
    [],
  );

  const pick = async (domain: Domain) => {
    await setActiveDomain(domain.id);
    if (await isAuthenticated()) {
      navigation.reset({ index: 0, routes: [{ name: 'Main' }] });
    } else {
      navigation.reset({ index: 0, routes: [{ name: 'Login' }] });
    }
  };

  const remove = (domain: Domain) => {
    void removeDomain(domain.id);
  };

  return (
    <YdScreen>
      <View style={{ flex: 1, gap: t.spacing.lg, paddingTop: t.spacing.xl }}>
        <View style={{ gap: 6, paddingHorizontal: 4 }}>
          <YdText variant="display">{domains.length > 0 ? '选择站点' : 'YDAM'}</YdText>
          <YdText variant="secondary">
            {domains.length > 0
              ? '点击切换域，随时添加或移除站点'
              : '接入你的站点域，管理服务器与社区内容'}
          </YdText>
        </View>

        <FlatList
          data={domains}
          keyExtractor={(item) => item.id}
          contentContainerStyle={{ gap: t.spacing.md, paddingBottom: 8 }}
          renderItem={({ item }) => {
            const active = item.id === activeId;
            return (
              <YdCard onPress={() => void pick(item)}>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: t.spacing.md }}>
                  <YdDomainAvatar name={item.name} logoUrl={resolveAssetUrl(item.serverUrl, item.branding?.logo)} size={46} />
                  <View style={{ flex: 1, gap: 2 }}>
                    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
                      <YdText style={{ fontWeight: t.typography.weightMedium }} numberOfLines={1}>
                        {item.name}
                      </YdText>
                      {active ? (
                        <YdText variant="caption" style={{ color: t.colors.accent }}>
                          当前
                        </YdText>
                      ) : null}
                    </View>
                    <YdText variant="caption" numberOfLines={1}>
                      {hostOf(item.serverUrl)}
                    </YdText>
                    {item.account ? (
                      <YdText variant="caption" numberOfLines={1}>
                        {item.account.nickname}
                      </YdText>
                    ) : null}
                  </View>
                  <View style={{ flexDirection: 'row', alignItems: 'center', gap: 4 }}>
                    {!item.mobileEnabled ? (
                      <Icon name="information-circle-outline" size={18} color={t.colors.textTertiary} />
                    ) : null}
                    <Icon
                      name="trash-outline"
                      size={18}
                      color={t.colors.textTertiary}
                      onPress={() => remove(item)}
                      hitSlop={10}
                    />
                    <Icon name="chevron-forward" size={18} color={t.colors.textTertiary} />
                  </View>
                </View>
              </YdCard>
            );
          }}
          ListEmptyComponent={
            <View style={{ alignItems: 'center', gap: t.spacing.md, paddingVertical: t.spacing.xl }}>
              <View
                style={{
                  width: 96,
                  height: 96,
                  borderRadius: 28,
                  backgroundColor: t.colors.bgSurface,
                  alignItems: 'center',
                  justifyContent: 'center',
                  borderWidth: 1,
                  borderColor: t.colors.borderSubtle,
                }}
              >
                <Icon name="server-outline" size={44} color={t.colors.accent} />
              </View>
              <YdText variant="secondary" style={{ textAlign: 'center' }}>
                还没有接入任何站点
              </YdText>
            </View>
          }
        />

        <YdButton title="添加站点域" onPress={() => navigation.navigate('DomainAdd')} />
      </View>
    </YdScreen>
  );
}

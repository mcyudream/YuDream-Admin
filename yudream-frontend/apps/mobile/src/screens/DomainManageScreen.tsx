import React, { useEffect, useState } from 'react';
import { ScrollView, View } from 'react-native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import type { NavigationProp } from '@react-navigation/native';
import { Switch } from 'react-native-paper';
import Icon from 'react-native-vector-icons/Ionicons';
import { YdButton, YdCard, YdListItem, YdScreen, YdText } from '@/components';
import { useTheme } from '@/core/theme/ThemeProvider';
import {
  getActiveDomain,
  getDomains,
  hostOf,
  setActiveDomain,
  subscribeDomains,
  type Domain,
} from '@/core/domains/store';
import {
  applyAppPrefs,
  getAppPrefs,
  loadAppPrefs,
  moveApp,
  setAppHidden,
  subscribeAppPrefs,
} from '@/core/domains/appPrefs';
import { onPluginsChanged, getPlugins } from '@/core/plugins/registry';
import { appDisplayName, type ManifestPluginEntry } from '@/core/manifest/types';
import type { RootStackParamList } from '@/navigation/types';

type Props = NativeStackScreenProps<RootStackParamList, 'DomainManage'>;

/**
 * 域管理（管理员）：域列表（切换/移除）+ 当前域的应用注册管理
 * （可见性开关与排序）。所有应用向主页注册的内容在此统一管控。
 */
export function DomainManageScreen({ navigation }: Props) {
  const t = useTheme();
  const [domains, setDomains] = useState(getDomains());
  const [activeId, setActiveId] = useState(getActiveDomain()?.id ?? null);
  const [plugins, setPlugins] = useState<ManifestPluginEntry[]>(getPlugins());
  const [prefsVersion, setPrefsVersion] = useState(0);

  useEffect(
    () =>
      subscribeDomains(() => {
        setDomains(getDomains());
        setActiveId(getActiveDomain()?.id ?? null);
      }),
    [],
  );
  useEffect(() => onPluginsChanged(setPlugins), []);
  useEffect(() => subscribeAppPrefs(() => setPrefsVersion((v) => v + 1)), []);
  useEffect(() => {
    const d = getActiveDomain();
    if (d) {
      void loadAppPrefs(d.id);
    }
  }, [activeId]);

  const active = domains.find((d) => d.id === activeId) ?? null;

  const orderedApps = (() => {
    void prefsVersion;
    if (!active) {
      return [] as Array<{ app: ManifestPluginEntry; visible: boolean }>;
    }
    const prefs = getAppPrefs(active.id);
    const visible = new Set(applyAppPrefs(plugins.map((p) => p.code), prefs));
    const ordered = [
      ...prefs.order.filter((c) => plugins.some((p) => p.code === c)),
      ...plugins.filter((p) => !prefs.order.includes(p.code)).map((p) => p.code),
    ];
    return ordered
      .map((code) => plugins.find((p) => p.code === code))
      .filter((p): p is ManifestPluginEntry => Boolean(p))
      .map((app) => ({ app, visible: visible.has(app.code) }));
  })();

  const switchTo = async (d: Domain) => {
    await setActiveDomain(d.id);
  };

  return (
    <YdScreen>
      <ScrollView contentContainerStyle={{ gap: t.spacing.lg, paddingVertical: t.spacing.md }}>
        {/* 域列表 */}
        <YdCard>
          <View style={{ gap: t.spacing.sm }}>
            <YdText style={{ fontWeight: t.typography.weightMedium }}>站点域</YdText>
            {domains.map((d) => (
              <YdListItem
                key={d.id}
                title={d.name}
                subtitle={hostOf(d.serverUrl)}
                onPress={() => void switchTo(d)}
                trailing={
                  <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
                    {d.id === activeId ? (
                      <YdText variant="caption" style={{ color: t.colors.accent }}>
                        当前
                      </YdText>
                    ) : null}
                    <Icon
                      name="chevron-forward"
                      size={18}
                      color={t.colors.textTertiary}
                      onPress={() => void switchTo(d)}
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
                navigation.getParent<NavigationProp<RootStackParamList>>()?.navigate('DomainAdd')
              }
            />
          </View>
        </YdCard>

        {/* 应用注册管理（当前域） */}
        <View style={{ gap: t.spacing.sm }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8, paddingHorizontal: 4 }}>
            <YdText style={{ fontWeight: t.typography.weightMedium }}>应用管理</YdText>
            <YdText variant="caption">{active ? active.name : '无激活域'}</YdText>
          </View>
          <YdText variant="caption" style={{ paddingHorizontal: 4 }}>
            控制各应用在主页注册的内容与展示顺序；隐藏后主页信息流与「我的应用」不再展示。
          </YdText>
          {orderedApps.length > 0 ? (
            <YdCard>
              <View style={{ gap: t.spacing.xs }}>
                {orderedApps.map(({ app, visible }, index) => (
                  <View key={app.code}>
                    <View
                      style={{
                        flexDirection: 'row',
                        alignItems: 'center',
                        gap: t.spacing.md,
                        paddingVertical: 8,
                      }}
                    >
                      <Icon name={app.icon ?? 'cube-outline'} size={22} color={t.colors.accent} />
                      <View style={{ flex: 1, gap: 2 }}>
                        <YdText numberOfLines={1} style={{ fontWeight: t.typography.weightMedium }}>
                          {appDisplayName(app)}
                        </YdText>
                        <YdText variant="caption" numberOfLines={1}>
                          {app.homeCards?.length
                            ? `注册 ${app.homeCards.length} 张主页卡片`
                            : '未注册主页内容'}
                        </YdText>
                      </View>
                      <Icon
                        name="chevron-up"
                        size={20}
                        color={index === 0 ? t.colors.textTertiary : t.colors.textSecondary}
                        onPress={
                          index === 0 || !active ? undefined : () => void moveApp(active.id, app.code, -1)
                        }
                        hitSlop={6}
                      />
                      <Icon
                        name="chevron-down"
                        size={20}
                        color={
                          index === orderedApps.length - 1
                            ? t.colors.textTertiary
                            : t.colors.textSecondary
                        }
                        onPress={
                          index === orderedApps.length - 1 || !active
                            ? undefined
                            : () => void moveApp(active.id, app.code, 1)
                        }
                        hitSlop={6}
                      />
                      <Switch
                        value={visible}
                        onValueChange={(v) => {
                          if (active) {
                            void setAppHidden(active.id, app.code, !v);
                          }
                        }}
                      />
                    </View>
                    {index < orderedApps.length - 1 ? (
                      <View style={{ height: 1, backgroundColor: t.colors.borderSubtle }} />
                    ) : null}
                  </View>
                ))}
              </View>
            </YdCard>
          ) : (
            <YdText variant="secondary" style={{ textAlign: 'center' }}>
              当前域暂无应用
            </YdText>
          )}
        </View>
      </ScrollView>
    </YdScreen>
  );
}

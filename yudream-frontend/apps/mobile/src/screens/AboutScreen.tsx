/**
 * 关于软件：应用 logo/名称/版本 + 手动检查更新 + 完整更新日志（app-release 插件公开端点）。
 */
import React, { useCallback, useEffect, useState } from 'react';
import { FlatList, Image, Pressable, View } from 'react-native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import Icon from 'react-native-vector-icons/Ionicons';
import { YdCard, YdListItem, YdScreen, YdText, ydAlert } from '@/components';
import { useTheme } from '@/core/theme/ThemeProvider';
import { APP_LOGO, APP_NAME } from '@/core/branding';
import { HOST_VERSION } from '@/core/config/env';
import { getActiveDomain, hostOf } from '@/core/domains/store';
import {
  checkAppUpdate,
  fetchChangelogs,
  formatUpdateSize,
  type AppUpdateInfo,
  type ChangelogEntry,
} from '@/core/update/updateService';
import type { RootStackParamList } from '@/navigation/types';

type Props = NativeStackScreenProps<RootStackParamList, 'About'>;

export function AboutScreen(_: Props) {
  const t = useTheme();
  const domain = getActiveDomain();
  const [logs, setLogs] = useState<ChangelogEntry[]>([]);
  const [checking, setChecking] = useState(false);
  const [update, setUpdate] = useState<AppUpdateInfo | null>(null);

  useEffect(() => {
    let alive = true;
    if (!domain) {
      return;
    }
    void fetchChangelogs(domain.serverUrl).then((entries) => {
      if (alive) {
        setLogs(entries);
      }
    });
    return () => {
      alive = false;
    };
  }, [domain?.id]);

  const runCheck = useCallback(async () => {
    if (!domain || checking) {
      return;
    }
    setChecking(true);
    try {
      const info = await checkAppUpdate(domain.serverUrl);
      setUpdate(info);
      if (!info?.updateAvailable) {
        // 无更新时轻提示；有更新走弹窗
        ydAlert('已是最新版本', `${APP_NAME} ${HOST_VERSION} 已是当前站点最新版本。`);
      }
    } finally {
      setChecking(false);
    }
  }, [domain?.id, checking]);

  return (
    <YdScreen>
      <View style={{ alignItems: 'center', gap: 6, paddingVertical: t.spacing.xl }}>
        <View
          style={{
            width: 88,
            height: 88,
            borderRadius: 26,
            overflow: 'hidden',
            alignItems: 'center',
            justifyContent: 'center',
            backgroundColor: t.colors.bgSurface,
            borderWidth: 1,
            borderColor: t.colors.borderSubtle,
          }}
        >
          <Image source={APP_LOGO} style={{ width: 88, height: 88 }} resizeMode="contain" />
        </View>
        <YdText style={{ fontSize: t.typography.sizeLg, fontWeight: t.typography.weightBold }}>{APP_NAME}</YdText>
        <YdText variant="caption">YuDream Admin Mobile · 版本 {HOST_VERSION}</YdText>
        {domain ? (
          <YdText variant="caption" numberOfLines={1}>
            当前站点：{domain.name}（{hostOf(domain.serverUrl)}）
          </YdText>
        ) : null}
      </View>

      <YdCard>
        <YdListItem
          title="检查更新"
          subtitle={update?.updateAvailable ? `发现新版本 ${update.latest?.versionName}` : `当前版本 ${HOST_VERSION}`}
          onPress={() => void runCheck()}
          trailing={
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
              {update?.updateAvailable ? (
                <View
                  style={{
                    paddingHorizontal: 8,
                    paddingVertical: 3,
                    borderRadius: 999,
                    backgroundColor: t.colors.accent,
                  }}
                >
                  <YdText variant="caption" style={{ color: t.colors.onAccent, fontSize: 10 }}>
                    可更新
                  </YdText>
                </View>
              ) : null}
              <Icon
                name={checking ? 'sync' : 'chevron-forward'}
                size={17}
                color={t.colors.textTertiary}
              />
            </View>
          }
        />
        <Pressable onPress={() => void runCheck()} style={{ paddingHorizontal: t.spacing.lg, paddingBottom: t.spacing.sm }}>
          <YdText variant="caption" style={{ color: t.colors.textTertiary }}>
            {checking ? '正在检查更新…' : '更新包由站点「更新发布」通道分发'}
          </YdText>
        </Pressable>
      </YdCard>

      <YdCard>
        <View style={{ gap: t.spacing.sm }}>
          <YdText style={{ fontWeight: t.typography.weightMedium }}>完整更新日志</YdText>
          {logs.length === 0 ? (
            <YdText variant="caption" style={{ color: t.colors.textTertiary }}>
              暂无更新日志（站点尚未发布过版本）
            </YdText>
          ) : (
            <FlatList
              data={logs}
              scrollEnabled={false}
              keyExtractor={(item) => item.id}
              ItemSeparatorComponent={() => <View style={{ height: t.spacing.md }} />}
              renderItem={({ item }) => (
                <View style={{ gap: 3 }}>
                  <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
                    <YdText style={{ fontWeight: t.typography.weightMedium, color: t.colors.textPrimary }}>
                      v{item.versionName}
                    </YdText>
                    <YdText variant="caption">
                      {item.publishedAt ? new Date(Number(item.publishedAt)).toLocaleDateString() : ''}
                      {item.fileSize ? ` · ${formatUpdateSize(item.fileSize)}` : ''}
                    </YdText>
                  </View>
                  <YdText variant="secondary" style={{ lineHeight: 20 }}>
                    {item.changelog?.trim() || '稳定性修复与体验优化。'}
                  </YdText>
                </View>
              )}
            />
          )}
        </View>
      </YdCard>

      <YdText variant="caption" style={{ textAlign: 'center', marginTop: t.spacing.md }}>
        {APP_NAME} · YuDream Admin 的移动客户端
      </YdText>
    </YdScreen>
  );
}

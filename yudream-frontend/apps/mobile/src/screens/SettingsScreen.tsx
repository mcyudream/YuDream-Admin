import React, { useState } from 'react';
import { Alert, TextInput, View } from 'react-native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { YdButton, YdCard, YdListItem, YdScreen, YdText } from '@/components';
import { useTheme, useThemeController, type ThemeMode } from '@/core/theme/ThemeProvider';
import { getServerUrl, setServerUrl, HOST_VERSION } from '@/core/config/env';
import { syncPlugins } from '@/core/plugins/pluginLoader';
import { logout } from '@/core/auth/authService';
import type { RootStackParamList } from '@/navigation/types';

type Props = NativeStackScreenProps<RootStackParamList, 'Settings'>;

export function SettingsScreen({ navigation }: Props) {
  const t = useTheme();
  const { mode, setMode } = useThemeController();
  const [serverUrl, setServerUrlState] = useState(getServerUrl());
  const [syncing, setSyncing] = useState(false);

  const applyServer = async () => {
    await setServerUrl(serverUrl);
    Alert.alert('已保存', '服务器地址将在下次请求时生效');
  };

  const resync = async () => {
    setSyncing(true);
    try {
      const result = await syncPlugins();
      const failed = Object.entries(result.failed);
      const summary =
        failed.length > 0
          ? `更新 ${result.updated.length} 个，失败 ${failed.length} 个：${failed.map(([c]) => c).join('、')}`
          : `已是最新${result.updated.length > 0 ? `，更新 ${result.updated.length} 个` : ''}`;
      Alert.alert('插件同步完成', summary);
    } catch (e) {
      Alert.alert('插件同步失败', e instanceof Error ? e.message : String(e));
    } finally {
      setSyncing(false);
    }
  };

  const doLogout = async () => {
    await logout();
    navigation.reset({ index: 0, routes: [{ name: 'Login' }] });
  };

  const modes: { key: ThemeMode; label: string }[] = [
    { key: 'system', label: '跟随系统' },
    { key: 'light', label: '浅色' },
    { key: 'dark', label: '深色' },
  ];

  return (
    <YdScreen>
      <View style={{ gap: t.spacing.lg, paddingVertical: t.spacing.md }}>
        <YdCard>
          <View style={{ gap: t.spacing.sm }}>
            <YdText style={{ fontWeight: t.typography.weightMedium }}>服务器地址</YdText>
            <TextInput
              value={serverUrl}
              onChangeText={setServerUrlState}
              autoCapitalize="none"
              autoCorrect={false}
              keyboardType="url"
              placeholderTextColor={t.colors.textTertiary}
              style={{
                minHeight: 44,
                borderWidth: 1,
                borderColor: t.colors.borderSubtle,
                borderRadius: t.radii.md,
                paddingHorizontal: t.spacing.md,
                color: t.colors.textPrimary,
              }}
            />
            <YdButton title="保存" variant="secondary" onPress={applyServer} />
          </View>
        </YdCard>

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

        <YdCard>
          <View style={{ gap: t.spacing.sm }}>
            <YdText style={{ fontWeight: t.typography.weightMedium }}>插件</YdText>
            <YdButton title="检查插件更新" variant="secondary" onPress={resync} loading={syncing} />
          </View>
        </YdCard>

        <YdButton title="退出登录" variant="danger" onPress={doLogout} />
        <YdText variant="caption" style={{ textAlign: 'center' }}>
          宿主版本 {HOST_VERSION}
        </YdText>
      </View>
    </YdScreen>
  );
}

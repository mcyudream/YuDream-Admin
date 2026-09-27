import React, { useState } from 'react';
import { KeyboardAvoidingView, Platform, View } from 'react-native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { YdButton, YdCard, YdField, YdMark, YdScreen, YdText } from '@/components';
import { useTheme } from '@/core/theme/ThemeProvider';
import { login } from '@/core/auth/authService';
import { getActiveDomain, hostOf } from '@/core/domains/store';
import { startBackgroundSync } from '@/app/bootstrap';
import type { RootStackParamList } from '@/navigation/types';

type Props = NativeStackScreenProps<RootStackParamList, 'Login'>;

/**
 * 域登录：顶部显示目标站点（可返回换域），账户密码登录后进入主界面。
 */
export function LoginScreen({ navigation }: Props) {
  const t = useTheme();
  const domain = getActiveDomain();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const submit = async () => {
    if (!username || !password) {
      setError('请输入用户名和密码');
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await login(username.trim(), password);
      startBackgroundSync();
      navigation.reset({ index: 0, routes: [{ name: 'Main' }] });
    } catch (e) {
      setError(e instanceof Error ? e.message : '登录失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <YdScreen>
      <KeyboardAvoidingView
        style={{ flex: 1 }}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <View style={{ flex: 1, gap: t.spacing.lg, paddingTop: t.spacing.lg }}>
          {domain ? (
            <YdCard onPress={() => navigation.navigate('Welcome')}>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: t.spacing.md }}>
                <YdMark name={domain.name} size={44} />
                <View style={{ flex: 1, gap: 2 }}>
                  <YdText style={{ fontWeight: t.typography.weightMedium }} numberOfLines={1}>
                    {domain.name}
                  </YdText>
                  <YdText variant="caption" numberOfLines={1}>
                    {hostOf(domain.serverUrl)} · 点击更换域
                  </YdText>
                </View>
              </View>
            </YdCard>
          ) : null}

          <View style={{ gap: 6 }}>
            <YdText variant="display">欢迎回来</YdText>
            <YdText variant="secondary">登录以同步该域的插件与内容</YdText>
          </View>

          <YdCard>
            <View style={{ gap: t.spacing.md }}>
              <YdField
                label="用户名"
                value={username}
                onChangeText={(text) => {
                  setUsername(text);
                  setError(null);
                }}
                placeholder="用户名 / 邮箱"
                autoFocus
              />
              <YdField
                label="密码"
                value={password}
                onChangeText={(text) => {
                  setPassword(text);
                  setError(null);
                }}
                placeholder="密码"
                secure
                onSubmitEditing={submit}
              />
              {error ? (
                <YdText variant="secondary" style={{ color: t.colors.danger }}>
                  {error}
                </YdText>
              ) : null}
              <YdButton title="登录" onPress={submit} loading={loading} />
            </View>
          </YdCard>
        </View>
      </KeyboardAvoidingView>
    </YdScreen>
  );
}

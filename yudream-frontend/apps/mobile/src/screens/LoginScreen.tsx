import React, { useState } from 'react';
import { TextInput, View } from 'react-native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { YdButton, YdCard, YdScreen, YdText } from '@/components';
import { useTheme } from '@/core/theme/ThemeProvider';
import { login } from '@/core/auth/authService';
import type { RootStackParamList } from '@/navigation/types';

type Props = NativeStackScreenProps<RootStackParamList, 'Login'>;

export function LoginScreen({ navigation }: Props) {
  const t = useTheme();
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
      navigation.reset({ index: 0, routes: [{ name: 'Home' }] });
    } catch (e) {
      setError(e instanceof Error ? e.message : '登录失败');
    } finally {
      setLoading(false);
    }
  };

  const inputStyle = {
    minHeight: 44,
    borderWidth: 1,
    borderColor: t.colors.borderSubtle,
    borderRadius: t.radii.md,
    paddingHorizontal: t.spacing.md,
    color: t.colors.textPrimary,
    fontSize: t.typography.sizeMd,
  } as const;

  return (
    <YdScreen>
      <View style={{ flex: 1, justifyContent: 'center', gap: t.spacing.lg }}>
        <YdText variant="display">登录</YdText>
        <YdCard>
          <View style={{ gap: t.spacing.md }}>
            <TextInput
              placeholder="用户名"
              placeholderTextColor={t.colors.textTertiary}
              autoCapitalize="none"
              value={username}
              onChangeText={setUsername}
              style={inputStyle}
            />
            <TextInput
              placeholder="密码"
              placeholderTextColor={t.colors.textTertiary}
              secureTextEntry
              value={password}
              onChangeText={setPassword}
              onSubmitEditing={submit}
              style={inputStyle}
            />
            {error ? <YdText style={{ color: t.colors.danger }}>{error}</YdText> : null}
            <YdButton title="登录" onPress={submit} loading={loading} />
          </View>
        </YdCard>
      </View>
    </YdScreen>
  );
}

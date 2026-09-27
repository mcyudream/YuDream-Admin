import React, { useState } from 'react';
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, TextInput, View } from 'react-native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { YdButton, YdText } from '@/components';
import { useTheme } from '@/core/theme/ThemeProvider';
import { login } from '@/core/auth/authService';
import { getActiveDomain, hostOf } from '@/core/domains/store';
import { startBackgroundSync } from '@/app/bootstrap';
import type { RootStackParamList } from '@/navigation/types';

type Props = NativeStackScreenProps<RootStackParamList, 'Login'>;

/**
 * 域登录（设计稿风格）：
 * 上半为品牌 hero 区——装饰弧线 + 居中域标识 + 域名大字（点击换域）；
 * 下半白底承载账号表单与胶囊主按钮。
 */
export function LoginScreen({ navigation }: Props) {
  const t = useTheme();
  const domain = getActiveDomain();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
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

  const underline = (focused: boolean) => ({
    borderBottomWidth: 1.5,
    borderBottomColor: focused ? t.colors.accent : t.colors.borderSubtle,
  });

  const fieldText = { color: t.colors.textPrimary, fontSize: t.typography.sizeMd, paddingVertical: 12 };

  return (
    <View style={{ flex: 1, backgroundColor: t.colors.bgPage }}>
      {/* 品牌 hero 区 */}
      <View style={{ height: '44%', backgroundColor: t.colors.accent, overflow: 'hidden' }}>
        {/* 装饰弧线（纯 View，无 SVG 依赖） */}
        <View
          pointerEvents="none"
          style={{
            position: 'absolute',
            top: -140,
            left: -60,
            width: 300,
            height: 220,
            borderRadius: 160,
            borderWidth: 26,
            borderColor: 'rgba(255,255,255,0.22)',
          }}
        />
        <View
          pointerEvents="none"
          style={{
            position: 'absolute',
            top: -40,
            right: -80,
            width: 260,
            height: 260,
            borderRadius: 130,
            borderWidth: 20,
            borderColor: 'rgba(255,255,255,0.14)',
          }}
        />

        {/* 居中域标识 + 域名（点击换域） */}
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="点击更换域"
          onPress={() => navigation.navigate('Welcome')}
          style={{ flex: 1, alignItems: 'center', justifyContent: 'center', gap: 14 }}
        >
          <View
            style={{
              width: 96,
              height: 96,
              borderRadius: 48,
              backgroundColor: t.colors.bgSurface,
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <YdText
              style={{
                color: t.colors.accent,
                fontSize: 42,
                fontWeight: t.typography.weightBold,
              }}
            >
              {(domain?.name ?? 'Y').trim()[0]?.toUpperCase() ?? 'Y'}
            </YdText>
          </View>
          <YdText
            numberOfLines={1}
            style={{
              color: t.colors.onAccent,
              fontSize: 26,
              fontWeight: t.typography.weightBold,
              paddingHorizontal: t.spacing.xl,
            }}
          >
            {domain?.name ?? 'YuDream'}
          </YdText>
          {domain ? (
            <YdText
              numberOfLines={1}
              style={{
                color: `${t.colors.onAccent}B3`,
                fontSize: t.typography.sizeSm,
                paddingHorizontal: t.spacing.xl,
              }}
            >
              {hostOf(domain.serverUrl)} · 点击更换域
            </YdText>
          ) : null}
        </Pressable>
      </View>

      {/* 表单区 */}
      <KeyboardAvoidingView
        style={{ flex: 1 }}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <ScrollView contentContainerStyle={{ flexGrow: 1, paddingHorizontal: t.spacing.xl, paddingTop: t.spacing.xl }} keyboardShouldPersistTaps="handled">
          <View style={{ gap: t.spacing.lg }}>
            {/* 用户名：底线式输入 */}
            <View style={underline(username.length > 0)}>
              <TextInput
                value={username}
                onChangeText={(text) => {
                  setUsername(text);
                  setError(null);
                }}
                placeholder="用户名 / 邮箱"
                placeholderTextColor={t.colors.textTertiary}
                autoCapitalize="none"
                autoCorrect={false}
                style={fieldText}
              />
            </View>
            {/* 密码：底线式输入 + 可见切换 */}
            <View style={{ ...underline(password.length > 0), flexDirection: 'row', alignItems: 'center' }}>
              <TextInput
                value={password}
                onChangeText={(text) => {
                  setPassword(text);
                  setError(null);
                }}
                placeholder="密码"
                placeholderTextColor={t.colors.textTertiary}
                secureTextEntry={!showPassword}
                autoCapitalize="none"
                autoCorrect={false}
                onSubmitEditing={submit}
                style={[fieldText, { flex: 1 }]}
              />
              <Pressable
                onPress={() => setShowPassword((v) => !v)}
                hitSlop={8}
                accessibilityLabel={showPassword ? '隐藏密码' : '显示密码'}
              >
                <YdText variant="caption">{showPassword ? '隐藏' : '显示'}</YdText>
              </Pressable>
            </View>

            {error ? (
              <YdText variant="secondary" style={{ color: t.colors.danger }}>
                {error}
              </YdText>
            ) : null}

            <YdButton title="登录" variant="hero" onPress={submit} loading={loading} />

            <YdText
              variant="caption"
              style={{ textAlign: 'center', color: t.colors.textTertiary }}
            >
              登录即同步该域的插件与内容
            </YdText>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </View>
  );
}

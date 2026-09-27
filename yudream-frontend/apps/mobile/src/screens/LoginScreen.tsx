import React, { useState } from 'react';
import {
  Image,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  View,
} from 'react-native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { YdButton, YdDomainAvatar, YdField, YdText } from '@/components';
import { useTheme } from '@/core/theme/ThemeProvider';
import { login } from '@/core/auth/authService';
import { getActiveDomain, hostOf } from '@/core/domains/store';
import { resolveAssetUrl } from '@/core/domains/assetUrl';
import { startBackgroundSync } from '@/app/bootstrap';
import type { RootStackParamList } from '@/navigation/types';

type Props = NativeStackScreenProps<RootStackParamList, 'Login'>;

/**
 * 域登录（设计稿风格）：
 * 上半为品牌 hero 区——优先使用能力配置的登录页背景图与底色，
 * 回退主题 accent + 装饰弧线；居中为站点 logo（回退域标识字母）与域名大字。
 */
export function LoginScreen({ navigation }: Props) {
  const t = useTheme();
  const domain = getActiveDomain();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const branding = domain?.branding ?? {};
  const heroImage = resolveAssetUrl(domain?.serverUrl ?? '', branding.loginHeroImage);
  const logoUrl = resolveAssetUrl(domain?.serverUrl ?? '', branding.logo);
  const heroBackground = branding.loginHeroBackground || t.colors.accent;
  const heroLetter = (domain?.name ?? 'Y').trim()[0]?.toUpperCase() ?? 'Y';

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
    <View style={{ flex: 1, backgroundColor: t.colors.bgPage }}>
      {/* 品牌 hero 区：能力配置背景图 > 能力配置底色 > 主题 accent + 弧线 */}
      <View style={{ height: '44%', backgroundColor: heroBackground, overflow: 'hidden' }}>
        {heroImage ? (
          <Image
            source={{ uri: heroImage }}
            style={{ position: 'absolute', width: '100%', height: '100%' }}
            resizeMode="cover"
          />
        ) : (
          <>
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
          </>
        )}
        {/* 背景图上压轻微暗化，保证域名文字可读 */}
        {heroImage ? (
          <View
            pointerEvents="none"
            style={{ position: 'absolute', width: '100%', height: '100%', backgroundColor: 'rgba(0,0,0,0.18)' }}
          />
        ) : null}

        {/* 居中域标识 + 域名（点击换域） */}
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="点击更换域"
          onPress={() => navigation.navigate('Welcome')}
          style={{ flex: 1, alignItems: 'center', justifyContent: 'center', gap: 14 }}
        >
          <YdDomainAvatar name={domain?.name ?? 'YuDream'} logoUrl={logoUrl} size={96} />
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
        <ScrollView
          contentContainerStyle={{ flexGrow: 1, paddingHorizontal: t.spacing.xl, paddingTop: t.spacing.xl }}
          keyboardShouldPersistTaps="handled"
        >
          <View style={{ gap: t.spacing.lg }}>
            <YdField
              label="用户名"
              value={username}
              onChangeText={(text) => {
                setUsername(text);
                setError(null);
              }}
              placeholder="用户名 / 邮箱"
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

            <YdButton title="登录" variant="hero" onPress={submit} loading={loading} />

            <YdText
              variant="caption"
              style={{ textAlign: 'center', color: t.colors.textTertiary }}
            >
              登录即同步该域的应用与内容
            </YdText>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </View>
  );
}

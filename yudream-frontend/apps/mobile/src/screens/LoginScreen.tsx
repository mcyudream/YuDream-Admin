import React, { useEffect, useState } from 'react';
import {
  Image,
  KeyboardAvoidingView,
  Linking,
  Platform,
  Pressable,
  ScrollView,
  View,
} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import Icon from 'react-native-vector-icons/Ionicons';
import { YdButton, YdDomainAvatar, YdField, YdScreen, YdText } from '@/components';
import { useTheme } from '@/core/theme/ThemeProvider';
import { login } from '@/core/auth/authService';
import { getActiveDomain, hostOf } from '@/core/domains/store';
import { resolveAssetUrl } from '@/core/domains/assetUrl';
import { startBackgroundSync } from '@/app/bootstrap';
import type { RootStackParamList } from '@/navigation/types';

type Props = NativeStackScreenProps<RootStackParamList, 'Login'>;

/**
 * 域登录（设计稿 login）：
 * 默认版式为简洁表单——顶部品牌行（站点 logo + 域名 + 切换域），标题 + 账号表单 +
 * 主色登录按钮。站点经能力配置下发 loginHeroImage/loginHeroBackground 时切换为
 * 品牌 hero 版式（上半屏背景图/底色 + 居中域标识），配置驱动、不写死。
 */
const REMEMBER_KEY = 'login.rememberUsername';

export function LoginScreen({ navigation }: Props) {
  const t = useTheme();
  const domain = getActiveDomain();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [remember, setRemember] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  // 记住我：上次成功登录勾选时记下的用户名自动预填
  useEffect(() => {
    void AsyncStorage.getItem(REMEMBER_KEY).then((saved) => {
      if (saved) {
        setUsername(saved);
      } else {
        setRemember(false);
      }
    });
  }, []);

  const branding = domain?.branding ?? {};
  const heroImage = resolveAssetUrl(domain?.serverUrl ?? '', branding.loginHeroImage);
  const logoUrl = resolveAssetUrl(domain?.serverUrl ?? '', branding.logo);
  const heroBackground = branding.loginHeroBackground || t.colors.accent;
  const heroMode = Boolean(branding.loginHeroImage || branding.loginHeroBackground);

  const submit = async () => {
    if (!username || !password) {
      setError('请输入用户名和密码');
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await login(username.trim(), password);
      if (remember) {
        void AsyncStorage.setItem(REMEMBER_KEY, username.trim());
      } else {
        void AsyncStorage.removeItem(REMEMBER_KEY);
      }
      startBackgroundSync();
      navigation.reset({ index: 0, routes: [{ name: 'Main' }] });
    } catch (e) {
      setError(e instanceof Error ? e.message : '登录失败');
    } finally {
      setLoading(false);
    }
  };

  const switchDomain = () => navigation.navigate('Welcome');
  const guest = () => navigation.reset({ index: 0, routes: [{ name: 'Main' }] });

  const form = (
    <View style={{ gap: t.spacing.sm }}>
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

      {/* 记住我 + 忘记密码（设计稿 login 同排） */}
      <View style={{ flexDirection: 'row', alignItems: 'center', marginTop: 2 }}>
        <Pressable
          accessibilityRole="checkbox"
          accessibilityState={{ checked: remember }}
          onPress={() => setRemember((v) => !v)}
          style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}
          hitSlop={6}
        >
          <View
            style={{
              width: 16,
              height: 16,
              borderRadius: 4,
              borderWidth: 1.5,
              borderColor: remember ? t.colors.accent : t.colors.borderSubtle,
              backgroundColor: remember ? t.colors.accent : t.colors.bgSurface,
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            {remember ? (
              <YdText style={{ color: t.colors.onAccent, fontSize: 11, lineHeight: 13 }}>✓</YdText>
            ) : null}
          </View>
          <YdText variant="secondary">记住我</YdText>
        </Pressable>
        <Pressable
          onPress={() => domain && Linking.openURL(domain.serverUrl)}
          style={{ flex: 1, alignItems: 'flex-end' }}
          hitSlop={6}
        >
          <YdText variant="secondary">忘记密码?</YdText>
        </Pressable>
      </View>

      {error ? (
        <YdText variant="secondary" style={{ color: t.colors.danger }}>
          {error}
        </YdText>
      ) : null}

      <YdButton
        title={loading ? '正在登录…' : '登 录'}
        variant="primary"
        onPress={submit}
        loading={loading}
        style={{ height: 48, marginTop: t.spacing.sm }}
      />

      {/* 或 · 游客模式（设计稿 login） */}
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12, marginTop: t.spacing.md }}>
        <View style={{ flex: 1, height: 1, backgroundColor: t.colors.borderSubtle }} />
        <YdText variant="caption">或</YdText>
        <View style={{ flex: 1, height: 1, backgroundColor: t.colors.borderSubtle }} />
      </View>
      <Pressable onPress={guest} hitSlop={6}>
        <YdText variant="secondary" style={{ textAlign: 'center' }}>
          先逛逛 · 游客模式
        </YdText>
      </Pressable>

      <YdText
        variant="caption"
        style={{ textAlign: 'center', marginTop: t.spacing.lg }}
      >
        登录即代表同意《用户协议》与《隐私政策》
      </YdText>
    </View>
  );

  if (heroMode) {
    // 品牌 hero 版式：能力配置了登录页背景图/底色
    return (
      <View style={{ flex: 1, backgroundColor: t.colors.bgPage }}>
        <View style={{ height: '44%', backgroundColor: heroBackground, overflow: 'hidden' }}>
          {heroImage ? (
            <>
              <Image
                source={{ uri: heroImage }}
                style={{ position: 'absolute', width: '100%', height: '100%' }}
                resizeMode="cover"
              />
              <View
                pointerEvents="none"
                style={{ position: 'absolute', width: '100%', height: '100%', backgroundColor: 'rgba(0,0,0,0.18)' }}
              />
            </>
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

          <Pressable
            accessibilityRole="button"
            accessibilityLabel="点击更换域"
            onPress={switchDomain}
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

        <KeyboardAvoidingView
          style={{ flex: 1 }}
          behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        >
          <ScrollView
            contentContainerStyle={{ flexGrow: 1, paddingHorizontal: t.spacing.lg, paddingTop: t.spacing.xl }}
            keyboardShouldPersistTaps="handled"
          >
            {form}
          </ScrollView>
        </KeyboardAvoidingView>
      </View>
    );
  }

  // 默认版式（设计稿 login）：品牌行 + 欢迎标题 + 表单
  return (
    <YdScreen padded={false}>
      <KeyboardAvoidingView
        style={{ flex: 1 }}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <ScrollView
          contentContainerStyle={{
            flexGrow: 1,
            paddingHorizontal: t.spacing.lg,
            paddingTop: t.spacing.md,
            paddingBottom: t.spacing.lg,
          }}
          keyboardShouldPersistTaps="handled"
        >
          {/* 品牌行：站点 logo + 域名 + 切换域 */}
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10, paddingBottom: t.spacing.xl }}>
            <YdDomainAvatar name={domain?.name ?? 'YuDream'} logoUrl={logoUrl} size={44} />
            <View style={{ flex: 1, gap: 1 }}>
              <YdText numberOfLines={1} style={{ fontSize: t.typography.sizeLg, fontWeight: t.typography.weightBold }}>
                {domain?.name ?? 'YuDream Admin'}
              </YdText>
              <YdText variant="caption" numberOfLines={1}>
                {domain ? hostOf(domain.serverUrl) : '未接入站点'}
              </YdText>
            </View>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="切换域"
              onPress={switchDomain}
              style={{ flexDirection: 'row', alignItems: 'center', gap: 2 }}
              hitSlop={8}
            >
              <YdText variant="secondary">切换</YdText>
              <Icon name="chevron-forward" size={14} color={t.colors.textTertiary} />
            </Pressable>
          </View>

          <YdText style={{ fontSize: 22, fontWeight: t.typography.weightBold }}>欢迎回来</YdText>
          <YdText variant="secondary" style={{ marginTop: 4, marginBottom: t.spacing.lg }}>
            使用站点账号登录当前域
          </YdText>

          {form}
        </ScrollView>
      </KeyboardAvoidingView>
    </YdScreen>
  );
}

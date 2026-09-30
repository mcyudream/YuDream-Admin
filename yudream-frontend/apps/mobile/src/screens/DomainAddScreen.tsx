import React, { useState } from 'react';
import { ActivityIndicator, KeyboardAvoidingView, Platform, Pressable, ScrollView, View } from 'react-native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import Clipboard from '@react-native-clipboard/clipboard';
import Icon from 'react-native-vector-icons/Ionicons';
import { YdButton, YdCard, YdField, YdMark, YdScreen, YdText } from '@/components';
import { useTheme } from '@/core/theme/ThemeProvider';
import { discoverDomain } from '@/core/domains/discover';
import { addDomain, normalizeServerUrl, setActiveDomain } from '@/core/domains/store';
import { bridges } from '@/bridges';
import type { RootStackParamList } from '@/navigation/types';

type Props = NativeStackScreenProps<RootStackParamList, 'DomainAdd'>;

/**
 * 接入站点（设计稿 domainAdd）：扫码卡片（能力位，不可用时引导手动输入）
 * / 粘贴 / 手动输入端点地址 -> 测试连接发现站点 -> 确认接入并进入登录。
 */
export function DomainAddScreen({ navigation }: Props) {
  const t = useTheme();
  const [rawUrl, setRawUrl] = useState('');
  const [checking, setChecking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [preview, setPreview] = useState<{
    serverUrl: string;
    name: string;
    mobileEnabled: boolean;
    branding: { logo?: string; loginHeroImage?: string; loginHeroBackground?: string };
  } | null>(null);

  const tryScan = async () => {
    if (!(await bridges.scanner.available())) {
      setError('相机扫码将在后续版本提供，可手动输入或粘贴地址');
      return;
    }
    const text = await bridges.scanner.scan();
    if (text) {
      setRawUrl(normalizeServerUrl(text));
      setError(null);
    }
  };

  const paste = () => {
    Clipboard.getString()
      .then((text) => {
        if (text?.trim()) {
          setRawUrl(normalizeServerUrl(text.trim()));
          setError(null);
        }
      })
      .catch(() => undefined);
  };

  const verify = async () => {
    const serverUrl = normalizeServerUrl(rawUrl);
    if (!/^https?:\/\/.+/.test(serverUrl)) {
      setError('请输入有效的站点地址，例如 https://mc.example.com');
      return;
    }
    setChecking(true);
    setError(null);
    setPreview(null);
    try {
      const outcome = await discoverDomain(serverUrl);
      if (outcome.kind === 'unreachable') {
        setError(outcome.message);
        return;
      }
      setPreview({
        serverUrl,
        name: outcome.kind === 'ok' ? outcome.info.siteName : serverUrl.replace(/^https?:\/\//, ''),
        mobileEnabled: outcome.kind === 'ok' ? outcome.info.mobileEnabled : false,
        branding:
          outcome.kind === 'ok'
            ? {
                logo: outcome.info.logo,
                loginHeroImage: outcome.info.loginHeroImage,
                loginHeroBackground: outcome.info.loginHeroBackground,
              }
            : {},
      });
    } finally {
      setChecking(false);
    }
  };

  const confirm = async () => {
    if (!preview) {
      return;
    }
    const domain = await addDomain(preview);
    await setActiveDomain(domain.id);
    navigation.reset({ index: 0, routes: [{ name: 'Login' }] });
  };

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
            paddingTop: t.spacing.sm,
            paddingBottom: t.spacing.lg,
            gap: t.spacing.md,
          }}
          keyboardShouldPersistTaps="handled"
        >
          {/* 导航行 */}
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: t.spacing.xs }}>
            <Pressable
              accessibilityRole="button"
              onPress={() => navigation.goBack()}
              hitSlop={10}
              style={{
                width: 40,
                height: 40,
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <Icon name="chevron-back" size={24} color={t.colors.textPrimary} />
            </Pressable>
            <YdText style={{ fontSize: t.typography.sizeLg, fontWeight: t.typography.weightBold }}>
              接入站点
            </YdText>
          </View>

          {/* 扫码添加：主色卡片（相机能力不可用时降级提示） */}
          <Pressable
            accessibilityRole="button"
            onPress={() => void tryScan()}
            android_ripple={{ color: t.colors.fillPressed }}
            style={({ pressed }) => ({
              flexDirection: 'row',
              alignItems: 'center',
              gap: 12,
              borderRadius: t.radii.lg,
              padding: 16,
              backgroundColor: pressed ? t.colors.accentPressed : t.colors.accent,
            })}
          >
            <View
              style={{
                width: 40,
                height: 40,
                borderRadius: t.radii.md,
                backgroundColor: `${t.colors.onAccent}26`,
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <Icon name="qr-code-outline" size={22} color={t.colors.onAccent} />
            </View>
            <View style={{ flex: 1, gap: 2 }}>
              <YdText style={{ color: t.colors.onAccent, fontWeight: t.typography.weightBold }}>
                扫码添加
              </YdText>
              <YdText
                numberOfLines={2}
                style={{ color: `${t.colors.onAccent}B3`, fontSize: t.typography.sizeSm, lineHeight: 18 }}
              >
                使用相机扫描站点二维码，自动填入并连接
              </YdText>
            </View>
            <Icon name="chevron-forward" size={16} color={`${t.colors.onAccent}99`} />
          </Pressable>

          {/* 或手动输入 */}
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: t.spacing.md, paddingVertical: 2 }}>
            <View style={{ flex: 1, height: 1, backgroundColor: t.colors.borderSubtle }} />
            <YdText variant="caption">或手动输入</YdText>
            <View style={{ flex: 1, height: 1, backgroundColor: t.colors.borderSubtle }} />
          </View>

          {/* 端点地址 */}
          <YdField
            label="端点地址"
            value={rawUrl}
            onChangeText={(text) => {
              setRawUrl(text);
              setError(null);
              setPreview(null);
            }}
            placeholder="https://mc.example.com"
            keyboardType="url"
            error={error}
          />
          <YdText variant="caption" style={{ marginTop: -t.spacing.xs }}>
            支持 http(s) 站点地址，首次连接将自动获取站点清单与应用目录。
          </YdText>

          <View style={{ flexDirection: 'row', gap: t.spacing.sm }}>
            <YdButton title="粘贴" variant="secondary" onPress={paste} style={{ width: 96 }} />
            <YdButton
              title={checking ? '正在探测…' : '测试连接'}
              variant="secondary"
              onPress={verify}
              loading={checking}
              style={{ flex: 1 }}
            />
          </View>

          {preview ? (
            <YdCard>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: t.spacing.md }}>
                <YdMark name={preview.name} size={46} />
                <View style={{ flex: 1, gap: 2 }}>
                  <YdText style={{ fontWeight: t.typography.weightMedium }} numberOfLines={1}>
                    {preview.name}
                  </YdText>
                  <YdText variant="caption" numberOfLines={1}>
                    {preview.serverUrl}
                  </YdText>
                  <YdText
                    variant="caption"
                    style={{ color: preview.mobileEnabled ? t.colors.success : t.colors.warning }}
                  >
                    {preview.mobileEnabled ? '已启用移动能力' : '未启用移动能力，仅基础功能'}
                  </YdText>
                </View>
              </View>
              <View style={{ marginTop: t.spacing.md }}>
                <YdButton title="确认接入" onPress={confirm} />
              </View>
            </YdCard>
          ) : null}

          <View style={{ flex: 1 }} />
          {checking ? <ActivityIndicator color={t.colors.accent} /> : null}
        </ScrollView>
      </KeyboardAvoidingView>
    </YdScreen>
  );
}

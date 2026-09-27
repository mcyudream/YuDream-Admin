import React, { useState } from 'react';
import { ActivityIndicator, KeyboardAvoidingView, Platform, View } from 'react-native';
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
 * 接入域：扫码（能力位，v1 引导手动输入）/ 粘贴 / 手动输入端点地址
 * -> 发现站点 -> 确认添加并进入登录。
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
    <YdScreen>
      <KeyboardAvoidingView
        style={{ flex: 1 }}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <View style={{ flex: 1, gap: t.spacing.lg, paddingTop: t.spacing.lg }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: t.spacing.sm }}>
            <Icon
              name="chevron-back"
              size={24}
              color={t.colors.textPrimary}
              onPress={() => navigation.goBack()}
              hitSlop={12}
            />
            <YdText variant="title">接入站点</YdText>
          </View>

          <YdText variant="secondary">输入站点端点地址，或扫描站点提供的接入二维码</YdText>

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

          <View style={{ flexDirection: 'row', gap: t.spacing.sm }}>
            <YdButton title="扫码" variant="secondary" onPress={tryScan} style={{ flex: 1 }} />
            <YdButton title="粘贴" variant="secondary" onPress={paste} style={{ flex: 1 }} />
          </View>

          <YdButton title={checking ? '正在探测…' : '验证并继续'} onPress={verify} loading={checking} />

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
        </View>
      </KeyboardAvoidingView>
    </YdScreen>
  );
}

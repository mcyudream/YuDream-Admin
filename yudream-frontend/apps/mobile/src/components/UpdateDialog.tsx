/**
 * 更新公告弹窗：发现新版本时展示更新日志与「立即更新」；
 * 强制更新时不可关闭、不提供「稍后」，不更新不允许使用软件。
 */
import React, { useEffect, useRef } from 'react';
import { Animated, Easing, Image, Linking, Modal, Pressable, ScrollView, View } from 'react-native';
import Icon from 'react-native-vector-icons/Ionicons';
import { YdButton, YdText } from '@/components';
import { useTheme } from '@/core/theme/ThemeProvider';
import { APP_LOGO, APP_NAME } from '@/core/branding';
import { formatUpdateSize, type AppUpdateInfo } from '@/core/update/updateService';

interface Props {
  info: AppUpdateInfo | null;
  onDismiss: () => void;
}

export function UpdateDialog({ info, onDismiss }: Props) {
  const t = useTheme();
  const enter = useRef(new Animated.Value(0)).current;

  // 公告弹窗弹簧入场：轻微上浮 + 缩放，替代生硬的直切
  useEffect(() => {
    if (info?.latest) {
      enter.setValue(0);
      Animated.parallel([
        Animated.spring(enter, { toValue: 1, useNativeDriver: true, friction: 7, tension: 160 }),
        Animated.timing(enter, { toValue: 1, duration: 180, easing: Easing.out(Easing.quad), useNativeDriver: true }),
      ]).start();
    }
  }, [info?.latest?.id, enter]);

  if (!info?.latest) {
    return null;
  }
  const forced = info.forced;
  const size = formatUpdateSize(info.latest.fileSize);

  return (
    <Modal visible transparent animationType="fade" statusBarTranslucent onRequestClose={() => !forced && onDismiss()}>
      <Pressable
        disabled={forced}
        onPress={() => !forced && onDismiss()}
        style={{ flex: 1, backgroundColor: 'rgba(0,0,0,0.45)', alignItems: 'center', justifyContent: 'center', padding: 28 }}
      >
        <Pressable onPress={(e) => e.stopPropagation()} style={{ width: '100%', maxWidth: 420 }}>
        <Animated.View
          style={{
            opacity: enter,
            transform: [
              { translateY: enter.interpolate({ inputRange: [0, 1], outputRange: [24, 0] }) },
              { scale: enter.interpolate({ inputRange: [0, 1], outputRange: [0.92, 1] }) },
            ],
            borderRadius: t.radii.lg,
            backgroundColor: t.colors.bgSurface,
            borderWidth: 1,
            borderColor: t.colors.borderSubtle,
            paddingBottom: t.spacing.lg,
            overflow: 'hidden',
          }}
        >
          {/* 头部：logo + 新版本号 + 强制角标 */}
          <View style={{ alignItems: 'center', gap: 6, paddingTop: t.spacing.xl, paddingBottom: t.spacing.md }}>
            <View style={{ position: 'relative' }}>
              <View style={{ width: 68, height: 68, borderRadius: 20, overflow: 'hidden', alignItems: 'center', justifyContent: 'center' }}>
                <Image source={APP_LOGO} style={{ width: 68, height: 68 }} resizeMode="contain" />
              </View>
              {forced ? (
                <View
                  style={{
                    position: 'absolute',
                    top: -6,
                    right: -34,
                    paddingHorizontal: 8,
                    paddingVertical: 3,
                    borderRadius: 999,
                    backgroundColor: t.colors.danger,
                  }}
                >
                  <YdText variant="caption" style={{ color: '#ffffff', fontSize: 10 }}>
                    强制更新
                  </YdText>
                </View>
              ) : null}
            </View>
            <YdText style={{ fontSize: t.typography.sizeLg, fontWeight: t.typography.weightBold }}>
              发现新版本 {info.latest.versionName}
            </YdText>
            <YdText variant="caption">
              {[size ? `安装包 ${size}` : '', info.latest.publishedAt ? new Date(Number(info.latest.publishedAt)).toLocaleDateString() : '']
                .filter(Boolean)
                .join(' · ')}
            </YdText>
          </View>

          {/* 更新日志 */}
          <ScrollView style={{ maxHeight: 280 }} contentContainerStyle={{ paddingHorizontal: t.spacing.xl, gap: 8, paddingBottom: t.spacing.md }}>
            <YdText variant="secondary" style={{ fontWeight: t.typography.weightMedium }}>
              更新内容
            </YdText>
            <YdText style={{ lineHeight: 22 }}>
              {info.latest.changelog?.trim() || '本次更新包含稳定性修复与体验优化。'}
            </YdText>
            {forced ? (
              <View
                style={{
                  marginTop: 4,
                  flexDirection: 'row',
                  alignItems: 'center',
                  gap: 6,
                  padding: 10,
                  borderRadius: t.radii.md,
                  backgroundColor: t.colors.fillHover,
                }}
              >
                <Icon name="lock-closed-outline" size={15} color={t.colors.danger} />
                <YdText variant="caption" style={{ flex: 1 }}>
                  当前版本过旧，更新后才能继续使用{APP_NAME}。
                </YdText>
              </View>
            ) : null}
          </ScrollView>

          <View style={{ paddingHorizontal: t.spacing.xl, paddingTop: t.spacing.md, gap: t.spacing.sm }}>
            <YdButton
              title="立即更新"
              onPress={() => {
                // 匿名下载端点：交系统浏览器下载 APK
                void Linking.openURL(info.downloadUrl).catch(() => undefined);
                if (!forced) {
                  onDismiss();
                }
              }}
            />
            {!forced ? (
              <YdButton title="稍后再说" variant="secondary" onPress={onDismiss} />
            ) : (
              <YdText variant="caption" style={{ textAlign: 'center' }}>
                下载完成后请在浏览器中安装，再重新打开应用
              </YdText>
            )}
          </View>
        </Animated.View>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

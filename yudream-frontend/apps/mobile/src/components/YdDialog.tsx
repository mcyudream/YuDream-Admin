/**
 * 宿主统一主题弹窗：替代 RN 原生 Alert.alert（参考 plugin-mobile-ui 同款实现）。
 * YdDialogHost 在 App 根部挂载一次；调用方使用与 Alert.alert 同构的 ydAlert。
 */
import React, { useEffect, useRef, useState } from 'react';
import { Animated, Easing, Modal, Pressable, Text, View } from 'react-native';
import { useTheme } from '@/core/theme/ThemeProvider';

export interface YdDialogButton {
  text: string;
  onPress?: () => void;
  style?: 'default' | 'cancel' | 'destructive';
}

interface YdDialogRequest {
  title: string;
  message: string;
  buttons: YdDialogButton[];
}

let emitDialog: ((req: YdDialogRequest) => void) | null = null;

/** 主题弹窗（替代 Alert.alert）：按钮缺省时自动补「确定」。 */
export function ydAlert(title: string, message?: string, buttons?: YdDialogButton[]): void {
  const req: YdDialogRequest = {
    title,
    message: message ?? '',
    buttons: buttons && buttons.length > 0 ? buttons : [{ text: '确定' }],
  };
  if (emitDialog) {
    emitDialog(req);
  } else {
    const [primary] = req.buttons.slice(-1);
    primary?.onPress?.();
  }
}

/** 全局弹窗宿主：App 根部挂载一次。 */
export function YdDialogHost() {
  const t = useTheme();
  const [req, setReq] = useState<YdDialogRequest | null>(null);
  const anim = useRef(new Animated.Value(0)).current;
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    emitDialog = (r: YdDialogRequest) => {
      setReq(r);
      setVisible(true);
      anim.setValue(0);
      Animated.parallel([
        Animated.timing(anim, { toValue: 1, duration: 160, easing: Easing.out(Easing.quad), useNativeDriver: true }),
        Animated.spring(anim, { toValue: 1, friction: 7, tension: 180, useNativeDriver: true }),
      ]).start();
    };
    return () => {
      emitDialog = null;
    };
  }, [anim]);

  const dismiss = (button?: YdDialogButton) => {
    setVisible(false);
    setReq(null);
    button?.onPress?.();
  };

  const single = req && req.buttons.length === 1;

  return (
    <Modal transparent visible={visible} statusBarTranslucent onRequestClose={() => dismiss()}>
      {req ? (
        <Animated.View
          style={{
            flex: 1,
            backgroundColor: 'rgba(0,0,0,0.45)',
            opacity: anim,
            alignItems: 'center',
            justifyContent: 'center',
            paddingHorizontal: 32,
          }}
        >
          <Pressable
            onPress={() => dismiss()}
            style={{ position: 'absolute', left: 0, right: 0, top: 0, bottom: 0 }}
          />
          <Animated.View
            style={{
              width: '100%',
              maxWidth: 340,
              borderRadius: 18,
              backgroundColor: t.colors.bgSurface,
              paddingTop: 20,
              paddingBottom: 6,
              paddingHorizontal: 18,
              opacity: anim,
              transform: [
                { translateY: anim.interpolate({ inputRange: [0, 1], outputRange: [22, 0] }) },
                { scale: anim.interpolate({ inputRange: [0, 1], outputRange: [0.94, 1] }) },
              ],
            }}
          >
            <Text
              numberOfLines={2}
              style={{ color: t.colors.textPrimary, fontSize: t.typography.sizeMd + 1, fontWeight: '700', lineHeight: 24 }}
            >
              {req.title}
            </Text>
            {req.message ? (
              <Text
                style={{
                  color: t.colors.textSecondary,
                  fontSize: t.typography.sizeSm,
                  lineHeight: 20,
                  marginTop: 8,
                  marginBottom: 4,
                }}
              >
                {req.message}
              </Text>
            ) : (
              <View style={{ height: 8 }} />
            )}
            <View style={{ flexDirection: 'row', marginTop: 14, gap: single ? 0 : 8 }}>
              {req.buttons.map((b, i) => {
                const destructive = b.style === 'destructive';
                const cancel = b.style === 'cancel';
                const color = destructive ? t.colors.danger : cancel ? t.colors.textSecondary : t.colors.accent;
                return (
                  <Pressable
                    key={`${b.text}-${i}`}
                    onPress={() => dismiss(b)}
                    android_ripple={{ color: t.colors.fillHover, radius: 60 }}
                    style={({ pressed }) => ({
                      flex: single ? undefined : 1,
                      alignSelf: single ? 'flex-end' : 'auto',
                      paddingVertical: 10,
                      paddingHorizontal: single ? 14 : 6,
                      borderRadius: 12,
                      backgroundColor: pressed ? t.colors.fillHover : 'transparent',
                      alignItems: 'center',
                    })}
                  >
                    <Text style={{ color, fontSize: t.typography.sizeSm + 1, fontWeight: '600' }}>
                      {b.text}
                    </Text>
                  </Pressable>
                );
              })}
            </View>
          </Animated.View>
        </Animated.View>
      ) : null}
    </Modal>
  );
}

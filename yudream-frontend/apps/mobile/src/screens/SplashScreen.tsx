import React, { useEffect, useState } from 'react';
import { ActivityIndicator, View } from 'react-native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { YdScreen, YdText } from '@/components';
import { useTheme, useThemeController } from '@/core/theme/ThemeProvider';
import { bootstrap } from '@/app/bootstrap';
import type { RootStackParamList } from '@/navigation/types';

type Props = NativeStackScreenProps<RootStackParamList, 'Splash'>;

/**
 * 启动页：缓存先绘（主题覆盖层即刻生效）-> bootstrap -> 路由分发。
 */
export function SplashScreen({ navigation }: Props) {
  const t = useTheme();
  const { applyRemoteOverride } = useThemeController();
  const [status, setStatus] = useState('正在初始化…');

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        setStatus('正在恢复会话…');
        const result = await bootstrap();
        if (cancelled) {
          return;
        }
        if (result.themeOverride) {
          applyRemoteOverride(result.themeOverride);
        }
        navigation.reset({
          index: 0,
          routes: [{ name: result.authenticated ? 'Home' : 'Login' }],
        });
      } catch (e) {
        console.error('[splash] bootstrap 失败', e);
        if (!cancelled) {
          setStatus('初始化失败，请检查服务器地址后重启应用');
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [navigation, applyRemoteOverride]);

  return (
    <YdScreen>
      <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', gap: 16 }}>
        <YdText variant="display">YuDream</YdText>
        <ActivityIndicator color={t.colors.accent} />
        <YdText variant="secondary">{status}</YdText>
      </View>
    </YdScreen>
  );
}

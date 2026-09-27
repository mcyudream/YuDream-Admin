import React, { useEffect, useState } from 'react';
import { ActivityIndicator, View } from 'react-native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { YdMark, YdScreen, YdText } from '@/components';
import { useTheme, useThemeController } from '@/core/theme/ThemeProvider';
import { bootstrap } from '@/app/bootstrap';
import type { RootStackParamList } from '@/navigation/types';

type Props = NativeStackScreenProps<RootStackParamList, 'Splash'>;

/**
 * 启动页：缓存先绘（主题覆盖层即刻生效）-> bootstrap -> 路由分发：
 * 无域 -> Welcome（域列表/接入）；有域未登录 -> Login；已登录 -> Main。
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
        const next = !result.hasDomain ? 'Welcome' : result.authenticated ? 'Main' : 'Login';
        navigation.reset({ index: 0, routes: [{ name: next }] });
      } catch (e) {
        console.error('[splash] bootstrap 失败', e);
        if (!cancelled) {
          setStatus('初始化失败，请重启应用');
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
        <YdMark name="YuDream" size={72} />
        <ActivityIndicator color={t.colors.accent} style={{ marginTop: 8 }} />
        <YdText variant="secondary">{status}</YdText>
      </View>
    </YdScreen>
  );
}

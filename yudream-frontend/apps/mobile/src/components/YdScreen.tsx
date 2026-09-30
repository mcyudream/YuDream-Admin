import React from 'react';
import { SafeAreaView } from 'react-native-safe-area-context';
import { StyleSheet, View, type ViewStyle } from 'react-native';
import { useTheme } from '@/core/theme/ThemeProvider';

/**
 * 页面容器：统一安全区、底色。所有宿主/插件页面以此起步。
 * edge-to-edge 下顶/底 inset 由本容器预留：状态栏区域永远透出页面底色。
 * 注意：不要在此渲染 RN <StatusBar>——其 legacy systemUiVisibility 会破坏
 * edge-to-edge（API 30+ 窗口被拉回非沉浸），状态栏图标风格由 RNS statusBarStyle
 * trait（RootNavigator screenOptions / 各页 setOptions）驱动。
 */
export function YdScreen({
  children,
  style,
  padded = true,
}: {
  children: React.ReactNode;
  style?: ViewStyle;
  padded?: boolean;
}) {
  const t = useTheme();
  return (
    <SafeAreaView
      style={[styles.root, { backgroundColor: t.colors.bgPage }]}
      edges={['top', 'left', 'right', 'bottom']}
    >
      <View style={[styles.body, padded && { paddingHorizontal: t.spacing.lg }, style]}>
        {children}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  body: { flex: 1 },
});

import React from 'react';
import { SafeAreaView } from 'react-native-safe-area-context';
import { StatusBar, StyleSheet, View, type ViewStyle } from 'react-native';
import { useTheme } from '@/core/theme/ThemeProvider';

/**
 * 页面容器：统一安全区、底色与状态栏风格。所有宿主/插件页面以此起步。
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
    <SafeAreaView style={[styles.root, { backgroundColor: t.colors.bgPage }]} edges={['top', 'left', 'right']}>
      <StatusBar barStyle={t.scheme === 'dark' ? 'light-content' : 'dark-content'} />
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

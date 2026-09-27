import React from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { useTheme } from '@/core/theme/ThemeProvider';
import { YdText } from './YdText';

/**
 * 列表行：标题 + 副标题 + 右侧槽位；最小触控 44pt。
 */
export function YdListItem({
  title,
  subtitle,
  onPress,
  trailing,
}: {
  title: string;
  subtitle?: string;
  onPress?: () => void;
  trailing?: React.ReactNode;
}) {
  const t = useTheme();
  return (
    <Pressable
      accessibilityRole={onPress ? 'button' : undefined}
      onPress={onPress}
      disabled={!onPress}
      style={({ pressed }) => [
        styles.row,
        {
          minHeight: 44,
          paddingVertical: t.spacing.sm,
          borderBottomColor: t.colors.borderSubtle,
        },
        pressed && { backgroundColor: t.colors.fillHover },
      ]}
    >
      <View style={styles.texts}>
        <YdText>{title}</YdText>
        {subtitle ? <YdText variant="secondary">{subtitle}</YdText> : null}
      </View>
      {trailing ? <View style={styles.trailing}>{trailing}</View> : null}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', borderBottomWidth: StyleSheet.hairlineWidth },
  texts: { flex: 1, gap: 2 },
  trailing: { marginLeft: 12 },
});

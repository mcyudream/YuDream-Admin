import React from 'react';
import { View } from 'react-native';
import { List as PaperList } from 'react-native-paper';
import { useTheme } from '@/core/theme/ThemeProvider';

/**
 * 列表行（react-native-paper List.Item 封装）：标题 + 副标题 + 右侧槽位。
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
    <PaperList.Item
      title={title}
      description={subtitle}
      onPress={onPress}
      titleStyle={{ color: t.colors.textPrimary, fontSize: t.typography.sizeMd }}
      descriptionStyle={{ color: t.colors.textTertiary, fontSize: t.typography.sizeSm }}
      descriptionNumberOfLines={1}
      right={trailing ? () => <View style={{ flexDirection: 'row', alignItems: 'center' }}>{trailing}</View> : undefined}
      style={{ paddingHorizontal: 0, minHeight: 44, paddingVertical: 0 }}
    />
  );
}

import React, { useState } from 'react';
import { TextInput, View, Pressable } from 'react-native';
import Icon from 'react-native-vector-icons/Ionicons';
import { useTheme } from '@/core/theme/ThemeProvider';
import { YdText } from './YdText';

/**
 * 带标签的输入框：登录/接入表单统一用它，保证聚焦态、错误态一致。
 */
export function YdField({
  label,
  value,
  onChangeText,
  placeholder,
  secure = false,
  error,
  autoCapitalize = 'none',
  keyboardType = 'default',
  onSubmitEditing,
  autoFocus,
}: {
  label: string;
  value: string;
  onChangeText: (text: string) => void;
  placeholder?: string;
  secure?: boolean;
  error?: string | null;
  autoCapitalize?: 'none' | 'sentences' | 'words';
  keyboardType?: 'default' | 'url' | 'email-address';
  onSubmitEditing?: () => void;
  autoFocus?: boolean;
}) {
  const t = useTheme();
  const [focused, setFocused] = useState(false);
  const [visible, setVisible] = useState(!secure);
  const borderColor = error
    ? t.colors.danger
    : focused
      ? t.colors.accent
      : t.colors.borderSubtle;

  return (
    <View style={{ gap: 6 }}>
      <YdText variant="secondary" style={{ fontWeight: t.typography.weightMedium }}>
        {label}
      </YdText>
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          minHeight: 48,
          borderWidth: 1.5,
          borderColor,
          borderRadius: t.radii.md,
          backgroundColor: t.colors.bgSurface,
          paddingHorizontal: t.spacing.md,
        }}
      >
        <TextInput
          value={value}
          onChangeText={onChangeText}
          placeholder={placeholder}
          placeholderTextColor={t.colors.textTertiary}
          secureTextEntry={!visible}
          autoCapitalize={autoCapitalize}
          autoCorrect={false}
          keyboardType={keyboardType}
          onSubmitEditing={onSubmitEditing}
          autoFocus={autoFocus}
          onFocus={() => setFocused(true)}
          onBlur={() => setFocused(false)}
          style={{
            flex: 1,
            color: t.colors.textPrimary,
            fontSize: t.typography.sizeMd,
            paddingVertical: 12,
          }}
        />
        {secure ? (
          <Pressable
            onPress={() => setVisible((v) => !v)}
            hitSlop={8}
            accessibilityLabel={visible ? '隐藏密码' : '显示密码'}
          >
            <Icon
              name={visible ? 'eye-off-outline' : 'eye-outline'}
              size={20}
              color={t.colors.textTertiary}
            />
          </Pressable>
        ) : null}
      </View>
      {error ? (
        <YdText variant="caption" style={{ color: t.colors.danger }}>
          {error}
        </YdText>
      ) : null}
    </View>
  );
}

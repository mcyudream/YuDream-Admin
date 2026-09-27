import React, { useState } from 'react';
import { View } from 'react-native';
import { TextInput as PaperInput } from 'react-native-paper';
import { useTheme } from '@/core/theme/ThemeProvider';
import { YdText } from './YdText';

/**
 * 统一输入框（react-native-paper TextInput 封装）：
 * 灰底填充式 + 浮动标签 + 聚焦 accent 描边 + 密码可见切换 + 错误态。
 * 登录/接入等全部表单都用它，保证观感一致。
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
  const [hidden, setHidden] = useState(secure);

  return (
    <View>
      <PaperInput
        mode="flat"
        label={label}
        value={value}
        onChangeText={onChangeText}
        placeholder={placeholder}
        secureTextEntry={hidden}
        autoCapitalize={autoCapitalize}
        autoCorrect={false}
        keyboardType={keyboardType}
        onSubmitEditing={onSubmitEditing}
        autoFocus={autoFocus}
        error={!!error}
        underlineColor="transparent"
        activeUnderlineColor="transparent"
        theme={{
          colors: {
            onSurfaceVariant: t.colors.textTertiary,
            primary: t.colors.accent,
            onSurface: t.colors.textPrimary,
            error: t.colors.danger,
          },
          roundness: t.radii.md,
        }}
        style={{
          backgroundColor: t.colors.fillHover,
          borderTopLeftRadius: t.radii.md,
          borderTopRightRadius: t.radii.md,
        }}
        contentStyle={{ color: t.colors.textPrimary, fontSize: t.typography.sizeMd }}
        right={
          secure ? (
            <PaperInput.Icon
              icon={hidden ? 'eye' : 'eye-off'}
              color={t.colors.textTertiary}
              onPress={() => setHidden((v) => !v)}
            />
          ) : undefined
        }
      />
      {error ? (
        <YdText variant="caption" style={{ color: t.colors.danger, marginTop: 4 }}>
          {error}
        </YdText>
      ) : null}
    </View>
  );
}

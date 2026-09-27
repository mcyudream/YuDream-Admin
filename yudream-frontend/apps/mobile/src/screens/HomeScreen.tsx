import React, { useEffect, useState } from 'react';
import { FlatList, View } from 'react-native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { YdButton, YdCard, YdScreen, YdText } from '@/components';
import { useTheme } from '@/core/theme/ThemeProvider';
import { onPluginsChanged, getPlugins } from '@/core/plugins/registry';
import type { ManifestPluginEntry } from '@/core/manifest/types';
import type { RootStackParamList } from '@/navigation/types';

type Props = NativeStackScreenProps<RootStackParamList, 'Home'>;

/**
 * 首页：已下发插件的入口卡片。数据层只做展示——
 * 平台/能力过滤已在服务端完成，离线时这里渲染的是快照内容。
 */
export function HomeScreen({ navigation }: Props) {
  const t = useTheme();
  const [plugins, setPlugins] = useState<ManifestPluginEntry[]>(getPlugins());

  useEffect(() => onPluginsChanged(setPlugins), []);

  return (
    <YdScreen>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingVertical: t.spacing.md }}>
        <YdText variant="title">YuDream</YdText>
        <YdButton title="设置" variant="ghost" onPress={() => navigation.navigate('Settings')} />
      </View>
      <FlatList
        data={plugins}
        keyExtractor={(item) => item.code}
        contentContainerStyle={{ gap: t.spacing.md, paddingBottom: t.spacing.xl }}
        renderItem={({ item }) => (
          <YdCard onPress={() => navigation.navigate('PluginHost', { code: item.code, title: item.code })}>
            <YdText style={{ fontWeight: t.typography.weightMedium }}>{item.code}</YdText>
            <YdText variant="secondary">v{item.version}</YdText>
          </YdCard>
        )}
        ListEmptyComponent={
          <YdText variant="secondary" style={{ textAlign: 'center', marginTop: t.spacing.xl }}>
            暂无可用插件，联网后将自动同步
          </YdText>
        }
      />
    </YdScreen>
  );
}

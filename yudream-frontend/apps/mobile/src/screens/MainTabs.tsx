import React, { useEffect, useState } from 'react';
import { createBottomTabNavigator, type BottomTabBarProps } from '@react-navigation/bottom-tabs';
import { Pressable, View } from 'react-native';
import Icon from 'react-native-vector-icons/Ionicons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTheme } from '@/core/theme/ThemeProvider';
import { YdText } from '@/components';
import { UpdateDialog } from '@/components/UpdateDialog';
import { getActiveDomain } from '@/core/domains/store';
import { checkAppUpdate, type AppUpdateInfo } from '@/core/update/updateService';
import { HomeTabScreen } from '@/screens/HomeTabScreen';
import { PluginsTabScreen } from '@/screens/PluginsTabScreen';
import { ProfileTabScreen } from '@/screens/ProfileTabScreen';
import type { MainTabParamList } from '@/navigation/types';

const Tabs = createBottomTabNavigator<MainTabParamList>();

const TAB_ITEMS: { name: keyof MainTabParamList; label: string; icon: string }[] = [
  { name: '首页', label: '首页', icon: 'home' },
  { name: '应用', label: '应用', icon: 'grid' },
  { name: '我的', label: '我的', icon: 'person' },
];

/** 设计稿同款悬浮药丸 tab 栏：surface 胶囊 + 主色激活药丸（图标+文字同色）。 */
function PillTabBar({ state, navigation }: BottomTabBarProps) {
  const t = useTheme();
  const insets = useSafeAreaInsets();

  return (
    <View style={{ paddingHorizontal: t.spacing.lg, paddingBottom: Math.max(insets.bottom, 12) + 8, backgroundColor: 'transparent' }}>
      <View
        style={{
          flexDirection: 'row',
          backgroundColor: t.colors.bgSurface,
          borderRadius: 36,
          borderWidth: 1,
          borderColor: t.colors.borderSubtle,
          padding: 4,
        }}
      >
        {state.routes.map((route, index) => {
          const item = TAB_ITEMS[index];
          if (!item) {
            return null;
          }
          const focused = state.index === index;
          const onPress = () => {
            const event = navigation.emit({ type: 'tabPress', target: route.key, canPreventDefault: true });
            if (!focused && !event.defaultPrevented) {
              navigation.navigate(route.name);
            }
          };
          return (
            <Pressable
              key={route.key}
              accessibilityRole="tab"
              accessibilityState={{ selected: focused }}
              onPress={onPress}
              style={{
                flex: 1,
                height: 54,
                borderRadius: 26,
                alignItems: 'center',
                justifyContent: 'center',
                flexDirection: 'row',
                gap: 6,
                backgroundColor: focused ? t.colors.accent : 'transparent',
              }}
            >
              <Icon name={focused ? item.icon : `${item.icon}-outline`} size={19} color={focused ? t.colors.onAccent : t.colors.textTertiary} />
              <YdText
                numberOfLines={1}
                style={{
                  fontSize: 12,
                  fontWeight: focused ? t.typography.weightMedium : t.typography.weightRegular,
                  color: focused ? t.colors.onAccent : t.colors.textTertiary,
                }}
              >
                {item.label}
              </YdText>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

/** 主界面：底部导航（首页 / 应用 / 我的）+ 启动后更新检查公告弹窗（强制更新不可关闭）。 */
export function MainTabs() {
  const [update, setUpdate] = useState<AppUpdateInfo | null>(null);

  // 每次进入主界面（登录成功/冷启动恢复会话）检查一次更新；失败静默（插件未装/网络问题均不阻塞）
  useEffect(() => {
    let alive = true;
    const domain = getActiveDomain();
    if (!domain) {
      return;
    }
    void checkAppUpdate(domain.serverUrl).then((info) => {
      if (alive && info?.updateAvailable) {
        setUpdate(info);
      }
    });
    return () => {
      alive = false;
    };
  }, []);

  return (
    <View style={{ flex: 1 }}>
      <Tabs.Navigator
        tabBar={(props) => <PillTabBar {...props} />}
        screenOptions={{
          headerShown: false,
        }}
      >
        <Tabs.Screen name="首页" component={HomeTabScreen} />
        <Tabs.Screen name="应用" component={PluginsTabScreen} />
        <Tabs.Screen name="我的" component={ProfileTabScreen} />
      </Tabs.Navigator>
      <UpdateDialog info={update} onDismiss={() => setUpdate(null)} />
    </View>
  );
}

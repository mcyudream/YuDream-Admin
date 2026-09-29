import React, { useEffect, useRef, useState } from 'react';
import { createBottomTabNavigator, type BottomTabBarProps } from '@react-navigation/bottom-tabs';
import { Animated, Pressable, View } from 'react-native';
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

const BAR_HEIGHT = 44;

/**
 * 悬浮 tab 栏（紧凑版）：surface 胶囊内一枚弹簧滑块在选中项之间滑动，
 * 图标/文字随之换色——切换有物理感，不再生硬跳变。
 * 滑块用 RN 内核 Animated（弹簧），避免为单个动效引入 reanimated 工作流
 * （reanimated 的 worklet 与 Re.Pack SWC/Hermes release 管线冲突会 SEGV）。
 */
function PillTabBar({ state, navigation }: BottomTabBarProps) {
  const t = useTheme();
  const insets = useSafeAreaInsets();
  // 每个条目的几何（onLayout 回填），滑块据此定位
  const [items, setItems] = useState<{ x: number; width: number }[]>([]);
  const pillX = useRef(new Animated.Value(0)).current;
  const pillW = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    const target = items[state.index];
    if (target) {
      Animated.parallel([
        Animated.spring(pillX, { toValue: target.x, useNativeDriver: true, friction: 8, tension: 200 }),
        Animated.spring(pillW, { toValue: target.width, useNativeDriver: true, friction: 8, tension: 200 }),
      ]).start();
    }
  }, [state.index, items, pillX, pillW]);

  return (
    <View
      style={{
        paddingHorizontal: 24,
        paddingBottom: Math.max(insets.bottom, 10) + 6,
        backgroundColor: 'transparent',
      }}
    >
      <View
        style={{
          flexDirection: 'row',
          backgroundColor: t.colors.bgSurface,
          borderRadius: 23,
          borderWidth: 1,
          borderColor: t.colors.borderSubtle,
          padding: 3,
        }}
      >
        {items.length === state.routes.length ? (
          <Animated.View
            pointerEvents="none"
            style={{
              position: 'absolute',
              top: 3,
              left: 3,
              height: BAR_HEIGHT - 6,
              borderRadius: 19,
              backgroundColor: t.colors.accent,
              transform: [{ translateX: pillX }],
              width: pillW,
            }}
          />
        ) : null}
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
              onLayout={(e) => {
                const { x, width } = e.nativeEvent.layout;
                setItems((prev) => {
                  const next = [...prev];
                  next[index] = { x, width };
                  return next;
                });
              }}
              style={({ pressed }) => ({
                flex: 1,
                height: BAR_HEIGHT - 6,
                borderRadius: 19,
                alignItems: 'center',
                justifyContent: 'center',
                flexDirection: 'row',
                gap: 5,
                opacity: pressed ? 0.75 : 1,
              })}
            >
              <Icon
                name={focused ? item.icon : `${item.icon}-outline`}
                size={17}
                color={focused ? t.colors.onAccent : t.colors.textTertiary}
              />
              <YdText
                numberOfLines={1}
                style={{
                  fontSize: 11,
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
          // 页面切换 200ms 淡入，配合滑块消掉生硬跳变
          animation: 'fade',
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

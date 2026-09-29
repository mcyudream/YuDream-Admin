/**
 * 首页富内容条目（内容页风格，参考小黑盒/贴吧式信息流）：
 * 通栏平铺在内容底色上（无卡片框），作者行 + 标题 + 摘要 + 配图
 * （单图右侧缩略 / 多图三列网格，多于 3 张显示「共 N 张」）+ 底部标签与互动计数，
 * 条目之间由列表的细分割线区隔；按压整条高亮。纯展示，点击整条打开应用。
 */
import React from 'react';
import { Image, Pressable, Text, View } from 'react-native';
import Icon from 'react-native-vector-icons/Ionicons';
import { useTheme } from '@/core/theme/ThemeProvider';
import type { MobileFeedItem } from '@/core/manifest/types';

interface FeedItemProps {
  item: MobileFeedItem;
  onPress: () => void;
}

function relativeTime(ts: number): string {
  if (!ts || ts <= 0) {
    return '';
  }
  const diff = Date.now() - ts;
  const minute = 60_000;
  const hour = 60 * minute;
  const day = 24 * hour;
  if (diff < minute) {
    return '刚刚';
  }
  if (diff < hour) {
    return `${Math.floor(diff / minute)} 分钟前`;
  }
  if (diff < day) {
    return `${Math.floor(diff / hour)} 小时前`;
  }
  if (diff < 30 * day) {
    return `${Math.floor(diff / day)} 天前`;
  }
  const d = new Date(ts);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function formatCount(n: number): string {
  if (n >= 10000) {
    return `${(n / 10000).toFixed(1)}w`;
  }
  if (n >= 1000) {
    return `${(n / 1000).toFixed(1)}k`;
  }
  return String(n);
}

function CountBadge({ icon, count, color, size }: {
  icon: string;
  count: number;
  color: string;
  size: number;
}) {
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 4 }}>
      <Icon name={icon} size={size} color={color} />
      <Text style={{ color, fontSize: size }}>{formatCount(count)}</Text>
    </View>
  );
}

export function FeedItem({ item, onPress }: FeedItemProps) {
  const t = useTheme();
  const images = item.images.slice(0, 3);
  const hiddenCount = (item.imageCount ?? item.images.length) - images.length;
  const metaColor = t.colors.textTertiary;

  return (
    <Pressable
      onPress={onPress}
      android_ripple={{ color: t.colors.fillHover }}
      style={({ pressed }) => ({
        backgroundColor: pressed ? t.colors.fillHover : 'transparent',
        paddingHorizontal: 16,
        paddingVertical: 12,
        gap: 8,
      })}
    >
      {/* 作者行：头像 + 昵称 · 时间（时间右对齐） */}
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
        {item.author.avatar ? (
          <Image
            source={{ uri: item.author.avatar }}
            style={{ width: 32, height: 32, borderRadius: 16, backgroundColor: t.colors.fillHover }}
          />
        ) : (
          <View
            style={{
              width: 32,
              height: 32,
              borderRadius: 16,
              backgroundColor: t.colors.fillHover,
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Text style={{ color: t.colors.textSecondary, fontSize: 13, fontWeight: '600' }}>
              {(item.author.name || '匿名').slice(0, 1)}
            </Text>
          </View>
        )}
        <Text
          numberOfLines={1}
          style={{ color: t.colors.textSecondary, fontSize: t.typography.sizeSm, fontWeight: '500', flexShrink: 1 }}
        >
          {item.author.name || '匿名'}
        </Text>
        {item.createTime ? (
          <Text style={{ color: t.colors.textTertiary, fontSize: t.typography.sizeXs + 1 }}>
            · {relativeTime(item.createTime)}
          </Text>
        ) : null}
      </View>

      {/* 标题 + 摘要；单图时左文右图（参考社区信息流排布） */}
      <View style={{ flexDirection: 'row', gap: 10 }}>
        <View style={{ flex: 1, gap: 5 }}>
          <Text
            numberOfLines={2}
            style={{
              color: t.colors.textPrimary,
              fontSize: t.typography.sizeMd + 1,
              fontWeight: '700',
              lineHeight: 23,
            }}
          >
            {item.title}
          </Text>
          {item.summary ? (
            <Text
              numberOfLines={2}
              style={{ color: t.colors.textSecondary, fontSize: t.typography.sizeSm, lineHeight: 19 }}
            >
              {item.summary}
            </Text>
          ) : null}
        </View>
        {images.length === 1 ? (
          <View>
            <Image
              source={{ uri: images[0] }}
              style={{ width: 112, height: 82, borderRadius: 10, backgroundColor: t.colors.fillHover }}
              resizeMode="cover"
            />
            {hiddenCount > 0 ? (
              <View
                pointerEvents="none"
                style={{
                  position: 'absolute',
                  right: 0,
                  bottom: 0,
                  paddingHorizontal: 6,
                  paddingVertical: 2,
                  borderTopLeftRadius: 10,
                  borderBottomRightRadius: 10,
                  backgroundColor: 'rgba(0,0,0,0.5)',
                }}
              >
                <Text style={{ color: '#ffffff', fontSize: 10 }}>{`共${item.imageCount}张`}</Text>
              </View>
            ) : null}
          </View>
        ) : null}
      </View>

      {/* 多图：三列网格铺满内容宽 */}
      {images.length > 1 ? (
        <View style={{ flexDirection: 'row', gap: 5 }}>
          {images.map((uri, i) => (
            <View key={`${item.id}-${i}`} style={{ flex: 1, aspectRatio: 4 / 3 }}>
              <Image
                source={{ uri }}
                style={{ width: '100%', height: '100%', borderRadius: 10, backgroundColor: t.colors.fillHover }}
                resizeMode="cover"
              />
              {i === images.length - 1 && hiddenCount > 0 ? (
                <View
                  pointerEvents="none"
                  style={{
                    position: 'absolute',
                    right: 0,
                    bottom: 0,
                    paddingHorizontal: 6,
                    paddingVertical: 2,
                    borderTopLeftRadius: 10,
                    borderBottomRightRadius: 10,
                    backgroundColor: 'rgba(0,0,0,0.5)',
                  }}
                >
                  <Text style={{ color: '#ffffff', fontSize: 10 }}>{`共${item.imageCount}张`}</Text>
                </View>
              ) : null}
            </View>
          ))}
        </View>
      ) : null}

      {/* 底部：标签 + 互动计数（浏览/评论/赞，赞靠右收尾） */}
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8, marginTop: 2 }}>
        {item.tagName ? (
          <View
            style={{
              paddingHorizontal: 8,
              paddingVertical: 2,
              borderRadius: 6,
              backgroundColor: t.colors.fillHover,
            }}
          >
            <Text numberOfLines={1} style={{ color: t.colors.textSecondary, fontSize: t.typography.sizeXs }}>
              {item.tagName}
            </Text>
          </View>
        ) : null}
        <View style={{ flex: 1 }} />
        {item.viewCount != null ? (
          <CountBadge icon="eye-outline" count={item.viewCount} color={metaColor} size={t.typography.sizeXs + 1} />
        ) : null}
        <CountBadge icon="chatbubble-outline" count={item.commentCount ?? 0} color={metaColor} size={t.typography.sizeXs + 1} />
        <CountBadge icon="thumbs-up-outline" count={item.likeCount ?? 0} color={metaColor} size={t.typography.sizeXs + 1} />
      </View>
    </Pressable>
  );
}

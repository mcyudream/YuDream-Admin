/**
 * 首页富内容条目（设计稿 post-card）：surface 卡片 + 作者行（头像/昵称/时间/标签）
 * + 标题 + 摘要 + 配图（单图全宽，多图九宫格缩略，多于 3 张显示「共 N 张」）
 * + 底部互动计数。纯展示，点击整条打开应用。
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

export function FeedItem({ item, onPress }: FeedItemProps) {
  const t = useTheme();
  const images = item.images.slice(0, 3);
  const hiddenCount = (item.imageCount ?? item.images.length) - images.length;

  return (
    <Pressable
      onPress={onPress}
      android_ripple={{ color: t.colors.fillHover }}
      style={({ pressed }) => ({
        borderRadius: t.radii.lg,
        borderWidth: 1,
        borderColor: t.colors.borderSubtle,
        backgroundColor: pressed ? t.colors.fillHover : t.colors.bgSurface,
        paddingHorizontal: 14,
        paddingVertical: 14,
        gap: 10,
      })}
    >
      {/* 作者行 */}
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
        {item.author.avatar ? (
          <Image
            source={{ uri: item.author.avatar }}
            style={{ width: 36, height: 36, borderRadius: 18, backgroundColor: t.colors.fillHover }}
          />
        ) : (
          <View
            style={{
              width: 36,
              height: 36,
              borderRadius: 18,
              backgroundColor: t.colors.accent,
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Text style={{ color: t.colors.onAccent, fontSize: 14, fontWeight: '500' }}>
              {(item.author.name || '匿名').slice(0, 1)}
            </Text>
          </View>
        )}
        <View style={{ flex: 1, gap: 1 }}>
          <Text
            numberOfLines={1}
            style={{ color: t.colors.textPrimary, fontSize: t.typography.sizeSm, fontWeight: '500' }}
          >
            {item.author.name || '匿名'}
          </Text>
          {item.createTime ? (
            <Text style={{ color: t.colors.textTertiary, fontSize: t.typography.sizeXs + 1 }}>
              {relativeTime(item.createTime)}
            </Text>
          ) : null}
        </View>
        {item.tagName ? (
          <View
            style={{
              paddingHorizontal: 8,
              paddingVertical: 3,
              borderRadius: 999,
              backgroundColor: t.colors.fillHover,
            }}
          >
            <Text numberOfLines={1} style={{ color: t.colors.textSecondary, fontSize: t.typography.sizeXs }}>
              {item.tagName}
            </Text>
          </View>
        ) : null}
      </View>

      {/* 标题 + 摘要 */}
      <Text
        numberOfLines={2}
        style={{
          color: t.colors.textPrimary,
          fontSize: t.typography.sizeMd,
          fontWeight: '700',
          lineHeight: 22,
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

      {/* 配图：单图全宽，多图缩略行 */}
      {images.length === 1 ? (
        <View>
          <Image
            source={{ uri: images[0] }}
            style={{ width: '100%', height: 118, borderRadius: 10, backgroundColor: t.colors.fillHover }}
            resizeMode="cover"
          />
          {hiddenCount > 0 ? (
            <View
              pointerEvents="none"
              style={{
                position: 'absolute',
                right: 0,
                top: 0,
                paddingHorizontal: 6,
                paddingVertical: 2,
                borderBottomLeftRadius: 10,
                borderTopRightRadius: 10,
                backgroundColor: 'rgba(0,0,0,0.5)',
              }}
            >
              <Text style={{ color: '#ffffff', fontSize: 10 }}>{`共${item.imageCount}张`}</Text>
            </View>
          ) : null}
        </View>
      ) : images.length > 1 ? (
        <View style={{ flexDirection: 'row', gap: 4 }}>
          {images.map((uri, i) => (
            <View key={`${item.id}-${i}`}>
              <Image
                source={{ uri }}
                style={{ width: 96, height: 72, borderRadius: 8, backgroundColor: t.colors.fillHover }}
              />
              {i === images.length - 1 && hiddenCount > 0 ? (
                <View
                  pointerEvents="none"
                  style={{
                    position: 'absolute',
                    right: 0,
                    top: 0,
                    paddingHorizontal: 6,
                    paddingVertical: 2,
                    borderBottomLeftRadius: 8,
                    borderTopRightRadius: 8,
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

      {/* 互动计数 */}
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 14 }}>
        {item.viewCount != null ? (
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 4 }}>
            <Icon name="eye-outline" size={14} color={t.colors.textTertiary} />
            <Text style={{ color: t.colors.textTertiary, fontSize: t.typography.sizeXs + 1 }}>
              {formatCount(item.viewCount)}
            </Text>
          </View>
        ) : null}
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 4 }}>
          <Icon name="chatbubble-outline" size={14} color={t.colors.textTertiary} />
          <Text style={{ color: t.colors.textTertiary, fontSize: t.typography.sizeXs + 1 }}>
            {formatCount(item.commentCount ?? 0)}
          </Text>
        </View>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 4 }}>
          <Icon name="thumbs-up-outline" size={14} color={t.colors.textTertiary} />
          <Text style={{ color: t.colors.textTertiary, fontSize: t.typography.sizeXs + 1 }}>
            {formatCount(item.likeCount ?? 0)}
          </Text>
        </View>
      </View>
    </Pressable>
  );
}

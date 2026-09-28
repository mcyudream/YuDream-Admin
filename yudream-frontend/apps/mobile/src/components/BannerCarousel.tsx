/**
 * 首页轮播图：分页横向滑动 + 自动轮播 + 指示点。
 * 纯 RN 实现，不引第三方库；图片经 resolveAssetUrl 支持站内相对路径。
 */
import React, { useEffect, useRef, useState } from 'react';
import {
  Image,
  NativeSyntheticEvent,
  NativeScrollEvent,
  Pressable,
  ScrollView,
  View,
} from 'react-native';
import { YdText } from './YdText';
import { useTheme } from '@/core/theme/ThemeProvider';
import { resolveAssetUrl } from '@/core/domains/assetUrl';
import { getActiveDomain } from '@/core/domains/store';
import type { DomainBanner } from '@/core/domains/store';

const AUTO_INTERVAL_MS = 5000;
const BANNER_HEIGHT = 148;

interface BannerCarouselProps {
  banners: DomainBanner[];
  onPress?: (banner: DomainBanner) => void;
}

export function BannerCarousel({ banners, onPress }: BannerCarouselProps) {
  const t = useTheme();
  const scrollRef = useRef<ScrollView>(null);
  const [pageWidth, setPageWidth] = useState(0);
  const [index, setIndex] = useState(0);

  // 自动轮播：定时推进到下一页，末页回卷到首页
  useEffect(() => {
    if (banners.length < 2 || pageWidth <= 0) {
      return;
    }
    const timer = setInterval(() => {
      setIndex((prev) => {
        const next = (prev + 1) % banners.length;
        scrollRef.current?.scrollTo({ x: next * pageWidth, animated: true });
        return next;
      });
    }, AUTO_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [banners.length, pageWidth]);

  if (banners.length === 0) {
    return null;
  }

  const onScroll = (e: NativeSyntheticEvent<NativeScrollEvent>) => {
    const page = Math.round(e.nativeEvent.contentOffset.x / e.nativeEvent.layoutMeasurement.width);
    if (page !== index && page >= 0 && page < banners.length) {
      setIndex(page);
    }
  };

  return (
    <View
      onLayout={(e) => setPageWidth(e.nativeEvent.layout.width)}
      style={{ borderRadius: t.radii.lg, overflow: 'hidden' }}
    >
      {pageWidth > 0 ? (
        <ScrollView
          ref={scrollRef}
          horizontal
          pagingEnabled
          showsHorizontalScrollIndicator={false}
          onMomentumScrollEnd={onScroll}
          style={{ height: BANNER_HEIGHT }}
        >
          {banners.map((banner, i) => {
            const uri = resolveAssetUrl(getActiveDomain()?.serverUrl ?? '', banner.imageUrl);
            return (
              <Pressable
                key={`${banner.imageUrl}-${i}`}
                onPress={() => onPress?.(banner)}
                style={{ width: pageWidth, height: BANNER_HEIGHT }}
              >
                <Image source={{ uri }} style={{ width: '100%', height: '100%' }} resizeMode="cover" />
                {banner.title ? (
                  <View
                    pointerEvents="none"
                    style={{
                      position: 'absolute',
                      left: 0,
                      right: 0,
                      bottom: 0,
                      paddingHorizontal: t.spacing.md,
                      paddingVertical: t.spacing.sm,
                      backgroundColor: 'rgba(0,0,0,0.35)',
                    }}
                  >
                    <YdText variant="secondary" numberOfLines={1} style={{ color: '#ffffff' }}>
                      {banner.title}
                    </YdText>
                  </View>
                ) : null}
              </Pressable>
            );
          })}
        </ScrollView>
      ) : (
        <View style={{ height: BANNER_HEIGHT, backgroundColor: t.colors.fillHover }} />
      )}
      {banners.length > 1 ? (
        <View
          pointerEvents="none"
          style={{
            position: 'absolute',
            bottom: t.spacing.sm,
            left: 0,
            right: 0,
            flexDirection: 'row',
            justifyContent: 'center',
            gap: 6,
          }}
        >
          {banners.map((_, i) => (
            <View
              key={i}
              style={{
                width: i === index ? 16 : 6,
                height: 6,
                borderRadius: 3,
                backgroundColor: i === index ? '#ffffff' : 'rgba(255,255,255,0.45)',
              }}
            />
          ))}
        </View>
      ) : null}
    </View>
  );
}

# kkFileView 自构建镜像（备选路径）。
# 首选方案是同步官方镜像：sh docker/kkfileview/push.sh
# 仅在官方镜像不可用（架构不匹配、网络受限、需定制字体或默认配置）时，
# 用本文件从源码构建：
#   docker build --build-arg KKFILEVIEW_VERSION=5.0.2 \
#     -t registry.yudream.online/yudream/yudreamadmin/kkfileview:5.0.2 docker/kkfileview

ARG KKFILEVIEW_VERSION=5.0.2

# ---- 构建阶段：克隆指定 tag 并编译 kkFileView 服务端（父 POM 锁定 JDK 21） ----
FROM swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/library/maven:3.9-eclipse-temurin-21 AS build
ARG KKFILEVIEW_VERSION
ARG ASPOSE_CAD_VERSION=25.10
WORKDIR /src
RUN apt-get update \
    && apt-get install -y --no-install-recommends git curl \
    && rm -rf /var/lib/apt/lists/* \
    && git clone --depth 1 --branch "v${KKFILEVIEW_VERSION}" https://github.com/kekingcn/kkFileView.git .
# aspose-cad 只在 Aspose 官方仓库（central 没有），跨国链路常把 40MB 的 jar 下截断：
# 先用 curl 断点续传预下载再 install-file 进本地仓库；版本需与上游 pom 的 aspose-cad.version 一致
RUN --mount=type=cache,target=/root/.m2 \
    curl -fSL --retry 10 --retry-all-errors -C - \
      -o /tmp/aspose-cad.jar "https://repository.aspose.com/repo/com/aspose/aspose-cad/${ASPOSE_CAD_VERSION}/aspose-cad-${ASPOSE_CAD_VERSION}.jar" \
    && curl -fSL --retry 10 --retry-all-errors -C - \
      -o /tmp/aspose-cad.pom "https://repository.aspose.com/repo/com/aspose/aspose-cad/${ASPOSE_CAD_VERSION}/aspose-cad-${ASPOSE_CAD_VERSION}.pom" \
    && mvn -q org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file \
      -Dfile=/tmp/aspose-cad.jar -DpomFile=/tmp/aspose-cad.pom \
    && rm -f /tmp/aspose-cad.jar /tmp/aspose-cad.pom
# server 模块产出可执行 fat jar：server/target/kkFileView-<version>.jar
# /root/.m2 用 BuildKit 缓存卷，构建重试时不必重下全部依赖
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests package

# ---- 运行阶段：与官方 kkfileview-base 对齐（Ubuntu 24.04 + JRE 21 + LibreOffice + 中文字体） ----
FROM swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/library/ubuntu:24.04
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       openjdk-21-jre-headless tzdata locales xfonts-utils fontconfig libreoffice-nogui \
       ttf-wqy-microhei ttf-wqy-zenhei xfonts-wqy curl \
    && locale-gen zh_CN.UTF-8 \
    && rm -rf /var/lib/apt/lists/*
ENV LANG=zh_CN.UTF-8 \
    LC_ALL=zh_CN.UTF-8
WORKDIR /opt/kkfileview
COPY --from=build /src/server/target/kkFileView-*.jar /opt/kkfileview/app.jar
# kk 的配置热载组件只读 <home>/server/src/main/config/application.properties
# （home 默认取 user.dir，即 WORKDIR）。文件缺失时 WatermarkConfigConstants 等静态配置保持 null，
# commonHeader.ftl 会把 watermark_x_space 等数值参数渲染成空串，所有预览页内联 JS 直接语法错误。
# 该文件自带 ${ENV:default} 占位（KK_TRUST_HOST、KK_BASE_URL、WATERMARK_* 等），容器 env 依旧生效。
COPY --from=build /src/server/src/main/config/application.properties /opt/kkfileview/server/src/main/config/application.properties
# office.home 保持 default，由 kkFileView 自动探测 /usr/lib/libreoffice
EXPOSE 8012
ENTRYPOINT ["java", "-jar", "/opt/kkfileview/app.jar"]

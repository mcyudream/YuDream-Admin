FROM swr.cn-north-4.myhuaweicloud.com/ddn-k8s/mcr.microsoft.com/playwright:v1.60.0-noble

WORKDIR /app
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
ENV COREPACK_NPM_REGISTRY=https://registry.npmmirror.com \
    npm_config_registry=https://registry.npmmirror.com
RUN corepack enable && pnpm install --frozen-lockfile --registry https://registry.npmmirror.com
COPY tsconfig.json ./
COPY src ./src
RUN pnpm exec tsc -p tsconfig.json
RUN pnpm prune --prod

ENV NODE_ENV=production \
    RENDER_HOST=0.0.0.0 \
    RENDER_PORT=3000 \
    PLAYWRIGHT_BROWSERS_PATH=/ms-playwright \
    HOME=/tmp \
    TMPDIR=/tmp \
    XDG_CONFIG_HOME=/tmp/.config \
    XDG_CACHE_HOME=/tmp/.cache

EXPOSE 3000
USER pwuser
CMD ["node", "dist/index.js"]

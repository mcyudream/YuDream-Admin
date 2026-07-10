FROM mcr.microsoft.com/playwright:v1.51.1-noble

WORKDIR /app
COPY package.json ./
RUN corepack enable && pnpm install --frozen-lockfile=false
COPY tsconfig.json ./
COPY src ./src
RUN pnpm exec tsc -p tsconfig.json
RUN pnpm prune --prod

ENV NODE_ENV=production \
    RENDER_HOST=0.0.0.0 \
    RENDER_PORT=3000 \
    PLAYWRIGHT_BROWSERS_PATH=/ms-playwright

EXPOSE 3000
USER pwuser
CMD ["node", "dist/index.js"]

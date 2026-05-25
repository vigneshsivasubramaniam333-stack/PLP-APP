FROM node:20-alpine AS builder
WORKDIR /app

# Slower networks: longer npm timeouts and retries
COPY .npmrc.docker /root/.npmrc

COPY package.json package-lock.json ./
COPY packages/shared/package.json packages/shared/package.json
COPY packages/platform-ui/package.json packages/platform-ui/package.json

# Install workspace deps (lockfile at frontend root)
RUN npm ci --no-audit --no-fund 2>/dev/null || npm install --no-audit --no-fund

COPY packages/shared ./packages/shared
COPY packages/platform-ui ./packages/platform-ui

WORKDIR /app/packages/platform-ui
RUN npm run build

FROM nginx:alpine
COPY --from=builder /app/packages/platform-ui/dist /usr/share/nginx/html
COPY packages/platform-ui/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]

FROM node:20-alpine AS builder
WORKDIR /app
COPY .npmrc.docker /root/.npmrc
COPY package.json package-lock.json* ./
COPY packages/shared/package.json packages/shared/package.json
COPY packages/design-system/package.json packages/design-system/package.json
COPY packages/anchor-portal/package.json packages/anchor-portal/package.json
RUN npm ci --no-audit --no-fund 2>/dev/null || npm install --no-audit --no-fund
COPY packages/shared ./packages/shared
COPY packages/design-system ./packages/design-system
COPY packages/anchor-portal ./packages/anchor-portal
WORKDIR /app/packages/anchor-portal
RUN npm run build

FROM nginx:alpine
COPY --from=builder /app/packages/anchor-portal/dist /usr/share/nginx/html
COPY packages/anchor-portal/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]

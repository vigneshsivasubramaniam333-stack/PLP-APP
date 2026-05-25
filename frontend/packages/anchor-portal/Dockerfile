FROM node:20-alpine AS builder
WORKDIR /app
COPY package.json package-lock.json* ./
COPY packages/shared ./packages/shared
COPY packages/anchor-portal ./packages/anchor-portal
RUN npm install
WORKDIR /app/packages/anchor-portal
RUN npm run build

FROM nginx:alpine
COPY --from=builder /app/packages/anchor-portal/dist /usr/share/nginx/html
COPY packages/anchor-portal/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]

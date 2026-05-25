FROM node:20-alpine AS builder
WORKDIR /app
COPY package.json package-lock.json* ./
COPY packages/shared ./packages/shared
COPY packages/borrower-portal ./packages/borrower-portal
RUN npm install
WORKDIR /app/packages/borrower-portal
RUN npm run build

FROM nginx:alpine
COPY --from=builder /app/packages/borrower-portal/dist /usr/share/nginx/html
COPY packages/borrower-portal/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]

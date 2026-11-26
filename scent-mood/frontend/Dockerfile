FROM node:24-alpine AS build
WORKDIR /app
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ .
RUN npm run build

FROM ghcr.io/nginx/nginx-unprivileged:alpine
ENV API_UPSTREAM=http://backend:8080
ENV NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1
COPY frontend/nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
USER 101
EXPOSE 8080

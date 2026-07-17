# The SPA is pre-built on the host (npm install && npm run build) so the image
# build needs no npm network — only nginx serves the static dist. The API and
# actuator endpoints are proxied to the P4 gateway (same origin, no CORS).
FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY dist /usr/share/nginx/html

EXPOSE 80

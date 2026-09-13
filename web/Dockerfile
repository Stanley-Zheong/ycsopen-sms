FROM nginx:alpine
COPY web/dist/ /usr/share/nginx/html/
COPY deploy/nginx/default.conf /etc/nginx/conf.d/default.conf
EXPOSE 80

FROM nginx:alpine

COPY blade-admin/dist/ /usr/share/nginx/html/
COPY deploy/nas/nginx/default.conf /etc/nginx/conf.d/default.conf

EXPOSE 80 443

FROM haproxy:1.8.30-buster

RUN mkdir -p /var/run/haproxy && chown -R haproxy: /var/run/haproxy

COPY haproxy.cfg /usr/local/etc/haproxy/haproxy.cfg

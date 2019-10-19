FROM haproxy:1.8

COPY haproxy.cfg /usr/local/etc/haproxy/haproxy.cfg

RUN mkdir /var/lib/haproxy && \
    mkdir /var/run/haproxy && \
    useradd haproxy && \
    chown haproxy: /var/lib/haproxy /var/run/haproxy

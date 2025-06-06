FROM gerritforge/docker-nfs-server:3.11.3

COPY exports.txt /etc/exports

# To avoid ownership issues, the user must be the same betweeen the
# server and the client, hence we are creating it explicitly in both.
RUN adduser --disabled-password --gecos "" --uid 1000 gerrit

RUN chown gerrit:gerrit /var/lib/nfs

COPY gerrit-git-mount-entrypoint.sh /usr/local/bin

ENTRYPOINT /usr/local/bin/gerrit-git-mount-entrypoint.sh

FROM erichough/nfs-server

COPY exports.txt /etc/exports

# To avoid ownership issues, the user must be the same betweeen the server and the client,
# hence we are creating it explicitly in both.
RUN adduser --disabled-password --gecos "" --uid 1000 gerrit

# /var/gerrit/git is the shared directory
RUN mkdir --parents /var/gerrit/git

RUN chown gerrit:gerrit /var/lib/nfs
RUN chown gerrit:gerrit /var/gerrit/git
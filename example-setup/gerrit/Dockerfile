FROM gerritcodereview/gerrit:3.6.3-almalinux8

USER root

RUN yum install -y gettext

COPY --chown=gerrit:gerrit high-availability.jar /var/gerrit/plugins/high-availability.jar

# Install plugins
ADD --chown=gerrit \
    https://gerrit-ci.gerritforge.com/view/Plugins-stable-3.6/job/plugin-healthcheck-bazel-stable-3.6/lastSuccessfulBuild/artifact/bazel-bin/plugins/healthcheck/healthcheck.jar \
    /var/gerrit/plugins/

# Install lib modules
ADD --chown=gerrit \
   https://repo1.maven.org/maven2/com/gerritforge/global-refdb/3.6.3.3/global-refdb-3.6.3.3.jar \
   /var/gerrit/lib/global-refdb.jar


COPY --chown=gerrit:gerrit entrypoint.sh /tmp/
COPY --chown=gerrit:gerrit configs/gerrit.config.template /var/gerrit/etc/
COPY --chown=gerrit:gerrit configs/high-availability.config.template /var/gerrit/etc/
COPY --chown=gerrit:gerrit configs/healthcheck.config /var/gerrit/etc/

# Expose ssh and http ports
EXPOSE 29418 8080

ENTRYPOINT [ "/tmp/entrypoint.sh" ]

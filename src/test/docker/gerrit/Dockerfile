FROM almalinux:8.5

# Install dependencies
RUN yum -y install \
    git \
    java-11-openjdk \
    procps \
    sudo \
    passwd \
    gettext \
    && yum -y clean all

ENV GERRIT_VERSION=3.6
ENV JAVA_HOME /usr/lib/jvm/jre-11-openjdk

# Add gerrit user
RUN adduser -p -m --uid 1000 gerrit --home-dir /home/gerrit
RUN echo "gerrit:gerrit" | chpasswd
RUN echo "gerrit ALL=(root) NOPASSWD:ALL" > /etc/sudoers.d/gerrit && \
    chmod 0440 /etc/sudoers.d/gerrit

# Create gerrit installation directory
RUN mkdir -p /var/gerrit && chown -R gerrit /var/gerrit

ADD --chown=gerrit \
    "https://gerrit-ci.gerritforge.com/job/Gerrit-bazel-stable-$GERRIT_VERSION/lastSuccessfulBuild/artifact/gerrit/bazel-bin/release.war" \
    /tmp/gerrit.war

ADD --chown=gerrit \
"https://gerrit-ci.gerritforge.com/job/plugin-javamelody-bazel-master-stable-$GERRIT_VERSION/lastSuccessfulBuild/artifact/bazel-bin/plugins/javamelody/javamelody.jar" \
    /var/gerrit/plugins/javamelody.jar
ADD --chown=gerrit \
    "https://gerrit-ci.gerritforge.com/job/plugin-high-availability-bazel-stable-$GERRIT_VERSION/lastSuccessfulBuild/artifact/bazel-bin/plugins/high-availability/high-availability.jar" \
    /var/gerrit/plugins/high-availability.jar

ADD --chown=gerrit \
    "https://repo1.maven.org/maven2/com/gerritforge/global-refdb/$GERRIT_VERSION.3.4/global-refdb-$GERRIT_VERSION.3.4.jar" \
    /tmp

ADD --chown=gerrit:gerrit ./wait-for-it.sh /bin

# Change user
USER gerrit

# Expose ssh and http ports
EXPOSE 29418 8080

COPY --chown=gerrit ./entrypoint.sh /bin

USER root

ENTRYPOINT /bin/entrypoint.sh

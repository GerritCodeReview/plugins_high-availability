FROM gerritcodereview/gerrit:3.5.6-ubuntu20

ENV GERRIT_BRANCH=stable-3.5

ENV GERRIT_CI_URL=https://archive-ci.gerritforge.com/job

USER root

RUN apt update && apt install -y iputils-ping nmap curl lsof gettext net-tools sudo

USER gerrit

ADD --chown=gerrit:gerrit $GERRIT_CI_URL/plugin-javamelody-bazel-master-$GERRIT_BRANCH/lastSuccessfulBuild/artifact/bazel-bin/plugins/javamelody/javamelody.jar /var/gerrit/plugins/javamelody.jar
ADD --chown=gerrit:gerrit $GERRIT_CI_URL/plugin-high-availability-bazel-$GERRIT_BRANCH/lastSuccessfulBuild/artifact/bazel-bin/plugins/high-availability/high-availability.jar /var/gerrit/plugins/high-availability.jar

ADD --chown=gerrit:gerrit https://repo1.maven.org/maven2/com/gerritforge/global-refdb/3.5.6/global-refdb-3.5.6.jar /var/gerrit/lib/global-refdb.jar

USER root

ADD start.sh /bin/
ADD wait-for-it.sh /bin/

RUN rm -Rf /var/gerrit/{git,index,cache}/*

ARG GERRIT_UID=1000
RUN usermod -u ${GERRIT_UID} gerrit &> /dev/null

ENTRYPOINT ["/usr/bin/env"]
CMD /bin/start.sh

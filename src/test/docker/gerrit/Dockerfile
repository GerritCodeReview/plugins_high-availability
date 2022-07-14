FROM gerritcodereview/gerrit:3.0.12

ENV GERRIT_BRANCH=stable-3.0

ENV GERRIT_CI_URL=https://archive-ci.gerritforge.com/job

USER root

RUN yum install -y iputils-ping netcat curl lsof gettext moreutils net-tools netcat inetutils-ping sudo

USER gerrit

ADD --chown=gerrit:gerrit $GERRIT_CI_URL/plugin-javamelody-bazel-$GERRIT_BRANCH/lastSuccessfulBuild/artifact/bazel-bin/plugins/javamelody/javamelody.jar /var/gerrit/plugins/javamelody.jar
ADD --chown=gerrit:gerrit $GERRIT_CI_URL/plugin-high-availability-bazel-$GERRIT_BRANCH/lastSuccessfulBuild/artifact/bazel-bin/plugins/high-availability/high-availability.jar /var/gerrit/plugins/high-availability.jar

USER root

ADD start.sh /bin/
ADD wait-for-it.sh /bin/

RUN rm -Rf /var/gerrit/{git,index,cache}/*

ARG GERRIT_UID=1000
RUN usermod -u ${GERRIT_UID} gerrit &> /dev/null

ENTRYPOINT ["/usr/bin/env"]
CMD /bin/start.sh

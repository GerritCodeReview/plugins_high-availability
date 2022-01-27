FROM gerritcodereview/gerrit:2.16.22

ENV GERRIT_BRANCH=stable-2.16

ENV GERRIT_CI_URL=https://archive-ci.gerritforge.com/job

USER root

RUN yum install -y iputils-ping netcat postgresql curl lsof gettext moreutils net-tools netcat inetutils-ping sudo

USER gerrit

ADD --chown=gerrit:gerrit $GERRIT_CI_URL/plugin-javamelody-bazel-$GERRIT_BRANCH/lastSuccessfulBuild/artifact/bazel-bin/plugins/javamelody/javamelody.jar /var/gerrit/plugins/javamelody.jar
ADD --chown=gerrit:gerrit $GERRIT_CI_URL/plugin-javamelody-bazel-$GERRIT_BRANCH/lastSuccessfulBuild/artifact/bazel-bin/plugins/javamelody/javamelody-deps_deploy.jar /var/gerrit/lib/javamelody-deps_deploy.jar
ADD --chown=gerrit:gerrit $GERRIT_CI_URL/plugin-high-availability-bazel-$GERRIT_BRANCH/lastSuccessfulBuild/artifact/bazel-bin/plugins/high-availability/high-availability.jar /var/gerrit/plugins/high-availability.jar
ADD --chown=gerrit:gerrit $GERRIT_CI_URL/plugin-delete-project-bazel-$GERRIT_BRANCH/lastSuccessfulBuild/artifact/bazel-bin/plugins/delete-project/delete-project.jar /var/gerrit/plugins/delete-project.jar

USER root

ADD start.sh /bin/
ADD wait-for-it.sh /bin/

RUN rm -Rf /var/gerrit/{git,index,cache}/*

ARG GERRIT_UID=1000
RUN usermod -u ${GERRIT_UID} gerrit &> /dev/null
CMD /bin/start.sh

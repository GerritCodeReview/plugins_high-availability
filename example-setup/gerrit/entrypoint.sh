#!/bin/bash -x

function setup_high-availability_config {

  echo "Replacing variables for file /var/gerrit/etc/high-availability.config.template"
  cat /var/gerrit/etc/high-availability.config.template | envsubst | sed 's/#{name}#/${name}/g' > /var/gerrit/etc/high-availability.config

  cat /var/gerrit/etc/high-availability.config
}

function setup_gerrit_config {
  echo "Replacing variables for file /var/gerrit/etc/gerrit.config.template"
  cat /var/gerrit/etc/gerrit.config.template | envsubst > /var/gerrit/etc/gerrit.config
}

JAVA_OPTS='--add-opens java.base/java.net=ALL-UNNAMED --add-opens java.base/java.lang.invoke=ALL-UNNAMED'

setup_high-availability_config
setup_gerrit_config

echo "Init phase ..."
java $JAVA_OPTS -jar /var/gerrit/bin/gerrit.war init --batch --install-all-plugins -d /var/gerrit

echo "Reindexing phase ..."
java $JAVA_OPTS -jar /var/gerrit/bin/gerrit.war reindex -d /var/gerrit

echo "Running Gerrit ..."
exec /var/gerrit/bin/gerrit.sh run


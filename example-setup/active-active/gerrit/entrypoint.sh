#!/bin/bash -x

JAVA_OPTS='--add-opens java.base/java.net=ALL-UNNAMED --add-opens java.base/java.lang.invoke=ALL-UNNAMED'

echo "Init phase ..."
java $JAVA_OPTS -jar /var/gerrit/bin/gerrit.war init --batch --install-all-plugins -d /var/gerrit

echo "Reindexing phase ..."
java $JAVA_OPTS -jar /var/gerrit/bin/gerrit.war reindex -d /var/gerrit

echo "Running Gerrit ..."
exec /var/gerrit/bin/gerrit.sh run


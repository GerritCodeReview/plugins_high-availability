#!/bin/bash -e

wait-for-it.sh postgres:5432 -t 600 -- echo "Postgres is up"

if [[ ! -z "$WAIT_FOR" ]]
then
  wait-for-it.sh $WAIT_FOR -t 600 -- echo "$WAIT_FOR is up"
fi

sudo -u gerrit cp /var/gerrit/etc/gerrit.config.orig /var/gerrit/etc/gerrit.config

if [[ ! -f /var/gerrit/etc/ssh_host_ed25519_key ]]
then
  echo "Initializing Gerrit site ..."
  sudo -u gerrit java -jar /var/gerrit/bin/gerrit.war init -d /var/gerrit --batch
fi

echo "Reindexing Gerrit ..."
sudo -u gerrit java -jar /var/gerrit/bin/gerrit.war reindex -d /var/gerrit
sudo -u gerrit git config -f /var/gerrit/etc/gerrit.config gerrit.canonicalWebUrl http://$HOSTNAME/

touch /var/gerrit/logs/{gc_log,error_log,httpd_log,sshd_log,replication_log} && chown -R gerrit: /var/gerrit && tail -f /var/gerrit/logs/* | grep --line-buffered -v 'HEAD /' &

echo "Running Gerrit ..."
sudo -u gerrit /var/gerrit/bin/gerrit.sh run

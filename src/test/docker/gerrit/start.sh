#!/bin/bash -e

if [[ ! -z "$WAIT_FOR" ]]
then
  wait-for-it.sh $WAIT_FOR -t 600 -- echo "$WAIT_FOR is up"
fi

sudo -u gerrit cp /var/gerrit/etc/gerrit.config.orig /var/gerrit/etc/gerrit.config
sudo -u gerrit cp /var/gerrit/etc/high-availability.config.orig /var/gerrit/etc/high-availability.config

if [[ ! -f /var/gerrit/etc/ssh_host_ed25519_key ]]
then
  echo "Initializing Gerrit site ..."
  sudo -u gerrit java -jar /var/gerrit/bin/gerrit.war init -d /var/gerrit --batch
  chown -R gerrit: /var/gerrit/shareddir
fi

echo "Reindexing Gerrit ..."
sudo -u gerrit java -jar /var/gerrit/bin/gerrit.war reindex -d /var/gerrit
sudo -u gerrit git config -f /var/gerrit/etc/gerrit.config gerrit.canonicalWebUrl http://$HOSTNAME/

sudo -u gerrit touch /var/gerrit/logs/{gc_log,error_log,httpd_log,sshd_log,replication_log} && tail -f /var/gerrit/logs/* | grep --line-buffered -v 'HEAD /' &

echo "Running Gerrit ..."
sudo -u gerrit bash -c "cd /var/gerrit/lib && ln -sf ../plugins/high-availability.jar ."
sudo -u gerrit git config -f /var/gerrit/etc/gerrit.config --add gerrit.installDbModule com.ericsson.gerrit.plugins.highavailability.ValidationModule
sudo -u gerrit git config -f /var/gerrit/etc/gerrit.config --add gerrit.installModule com.gerritforge.gerrit.globalrefdb.validation.LibModule

sudo -u gerrit bash -c "export AWS_ACCESS_KEY_ID=theKey && export AWS_SECRET_ACCESS_KEY=theSecret && /var/gerrit/bin/gerrit.sh run"

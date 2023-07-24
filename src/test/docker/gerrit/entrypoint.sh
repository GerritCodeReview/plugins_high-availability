#!/bin/sh

if [[ ! -z "$WAIT_FOR" ]]
then
  wait-for-it.sh $WAIT_FOR -t 600 -- echo "$WAIT_FOR is up"
fi

chown -R gerrit /var/gerrit/etc
sudo -u gerrit cp /var/gerrit/etc/jgit.config.orig /var/gerrit/etc/jgit.config
sudo -u gerrit cp /var/gerrit/etc/gerrit.config.orig /var/gerrit/etc/gerrit.config
sudo -u gerrit cp /var/gerrit/etc/high-availability.config.orig /var/gerrit/etc/high-availability.config
sudo -u gerrit cp /var/gerrit/etc/zookeeper-refdb.config.orig /var/gerrit/etc/zookeeper-refdb.config


echo "Mount NFS ..."
mkdir /var/gerrit/git && chown gerrit:gerrit /var/gerrit/git
mount -t nfs4 -o actimeo=120 nfs-server:/git /var/gerrit/git

echo "Init gerrit..."
sudo -u gerrit java -jar /tmp/gerrit.war init -d /var/gerrit --batch --install-all-plugins
chown -R gerrit: /var/gerrit/shareddir

echo "Reindexing Gerrit..."
cd /var/gerrit && sudo -u gerrit java -jar /var/gerrit/bin/gerrit.war reindex -d /var/gerrit
sudo -u gerrit git config -f /var/gerrit/etc/gerrit.config gerrit.canonicalWebUrl http://$HOSTNAME/
sudo -u gerrit touch /var/gerrit/logs/{gc_log,error_log,httpd_log,sshd_log,replication_log} && tail -f /var/gerrit/logs/* | grep --line-buffered -v 'HEAD /' &

echo "Running Gerrit ..."
sudo -u gerrit /var/gerrit/bin/gerrit.sh run

#!/bin/sh

MOUNT_POINT=/var/gerrit/git

echo "Creating mountpoint $MOUNT_POINT"

mkdir --parents $MOUNT_POINT
chown gerrit:gerrit $MOUNT_POINT

echo "ls -al $MOUNT_POINT ..."
ls -al $MOUNT_POINT

echo "Running NFS server ... "
echo "======================="
/usr/local/bin/entrypoint.sh
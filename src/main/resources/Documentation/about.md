
This plugin allows making Gerrit highly available by having redundant Gerrit
primary instances.

The primary instances must be:

* sharing the git repositories using a shared file system (e.g. NFS)
* behind a load balancer (e.g. HAProxy)

Currently, the mode supported is one active primary instance and multiple backup
(passive) primary instances but eventually the plan is to support `n` active
primary instances. In the active/passive mode, the active primary instance is
handling all traffic while the passives are kept updated to be always ready
to take over.

Even if git repositories are shared by the primary instances, there are a few
areas of concern in order to be able to switch traffic between primary instances
in a transparent manner from the user's perspective. The 4 areas of concern are
things that Gerrit stores either in memory or locally in the review site:

* caches
* secondary indexes
* stream-events
* web sessions

They need either to be shared or kept local to each primary instances but
synchronized. This plugin needs to be installed in all the primary instances
and it will take care of sharing or synchronizing them.

#### Caches
Every time a cache eviction occurs in one of the primary instances, the
eviction will be forwarded the other primary nodes so their caches do not
contain stale entries.

#### Secondary indexes
Every time the secondary index is modified in one of the primary instances,
e.g., a change is added, updated or removed from the index, the others primary
instances index are updated accordingly. This way, both indexes are kept
synchronized.

#### Stream events
Every time a stream event occurs in one of the primary instances
(see [more events info](https://gerrit-review.googlesource.com/Documentation/cmd-stream-events.html#events)),
the event is forwarded to the other primary instances which re-plays it.
This way, the output of the stream-events command is the same, no matter
which primary instance a client is connected to.

#### Web session
The built-in Gerrit H2 based web session cache is replaced with a file based
implementation that is shared amongst the primary instances.

## Setup

Prerequisites:

* Git repositories must be located on a shared file system
* A directory on a shared file system must be available for @PLUGIN@ to use

For the primary instances:

* Configure gerrit.basePath in gerrit.config to the shared repositories location
* Configure gerrit.serverId in gerrit.config based on [config](config.md)'s introduction
* Install and configure this @PLUGIN@ plugin [further](config.md) or based on below.

Here is an example of the minimal @PLUGIN@.config:

Active primary instance

```
[main]
  sharedDirectory = /directory/accessible/from/both/nodes

[peerInfo "static"]
  url = http://backupNodeHost1:8081/

[http]
  user = username
  password = password
```

Backup primary instance

```
[main]
  sharedDirectory = /directory/accessible/from/both/nodes

[peerInfo "static"]
  url = http://primaryNodeHost:8080/

[http]
  user = username
  password = password
```

### HA replica site

It is possible to create a copy of the primary instance site and configure both
sites to run in HA mode as peers. This is possible when the directory where
the copy will be created is accessible from this machine. Such a replica site
can be created by means of a gerrit [site init](../../../Documentation/pgm-init.html) step,
contributed by the plugin under its init section.

This init step is optional but defaults to creating the replica. If you want to
create the other site manually, or if the other site needs to be created in a
directory not accessible from this machine, then please skip that init step.

For further information and supported options, refer to [config](config.md)
documentation.

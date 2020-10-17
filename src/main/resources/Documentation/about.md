
This plugin allows making Gerrit highly available by having redundant Gerrit
masters.

The masters must be:

* connecting to the same database
* sharing the git repositories using a shared file system (e.g. NFS)
* behind a load balancer (e.g. HAProxy)

Default mode is one primary (active) master and multiple backup (passive) masters
but `n` active masters can be configured. In the active/passive mode, the active
master is handling all traffic while the passives are kept updated to be always
ready to take over.

Even if database and git repositories are shared by the masters, there are a few
areas of concern in order to be able to switch traffic between masters in a
transparent manner from the user's perspective. The 4 areas of concern are
things that Gerrit stores either in memory or locally in the review site:

* caches
* secondary indexes
* stream-events
* web sessions

They need either to be shared or kept local to each master but synchronized.
This plugin needs to be installed in all the masters and it will take care of sharing
or synchronizing them.

#### Caches
Every time a cache eviction occurs in one of the masters, the eviction will be
forwarded the other masters so their caches do not contain stale entries.

#### Secondary indexes
Every time the secondary index is modified in one of the masters, e.g., a change
is added, updated or removed from the index, the others master's index are
updated accordingly. This way, both indexes are kept synchronized.

#### Stream events
Every time a stream event occurs in one of the masters (see [more events info]
(https://gerrit-review.googlesource.com/Documentation/cmd-stream-events.html#events)),
the event is forwarded to the other masters which re-plays it. This way, the
output of the stream-events command is the same, no matter which master a client
is connected to.

#### Web session
The built-in Gerrit H2 based web session cache is replaced with a file based
implementation that is shared amongst the masters.

## Active/passive setup

Prerequisites:

* Unique database server must be accessible from all the masters
* Git repositories must be located on a shared file system
* A directory on a shared file system must be available for @PLUGIN@ to use

For the masters:

* Configure the database section in gerrit.config to use the shared database
* Configure gerrit.basePath in gerrit.config to the shared repositories location
* Configure gerrit.serverId in gerrit.config based on [config](config.md)'s introduction
* Install and configure this @PLUGIN@ plugin [further](config.md) or based on below.

Here is an example of the minimal @PLUGIN@.config:

Primary master

```
[main]
  sharedDirectory = /directory/accessible/from/both/masters

[peerInfo "static"]
  url = http://backupMasterHost1:8081/

[http]
  user = username
  password = password
```

Backup master

```
[main]
  sharedDirectory = /directory/accessible/from/both/masters

[peerInfo "static"]
  url = http://primaryMasterHost:8080/

[http]
  user = username
  password = password
```

### HA replica site

It is possible to create a copy of the master site and configure both sites to run
in HA mode as peers. This is possible when the directory where the copy will be
created is accessible from this machine. Such a replica site can be created by
means of a gerrit [site init](../../../Documentation/pgm-init.html) step,
contributed by the plugin under its init section.

This init step is optional but defaults to creating the replica. If you want to
create the other site manually, or if the other site needs to be created in a
directory not accessible from this machine, then please skip that init step.

For further information and supported options, refer to [config](config.md)
documentation.

## Active/active setup

Prerequisites:

* Unique database server must be accessible from all the masters
* Git repositories must be located on a shared file system
* A directory on a shared file system must be available for @PLUGIN@ to use
* An implementation of global-refdb (e.g. Zookeeper) must be accessible from all the masters

For the masters:

* Configure the database section in gerrit.config to use the shared database
* Configure gerrit.basePath in gerrit.config to the shared repositories location
* Configure gerrit.serverId in gerrit.config based on [config](config.md)'s introduction
* Install and configure this @PLUGIN@ plugin [further](config.md) or based on example
configuration
* Install @PLUGIN@ plugin as a database module in $GERRIT_SITE/lib(please note that
@PLUGIN plugin must be installed as a plugin and as a database module) and add
`installDbModule = com.ericsson.gerrit.plugins.highavailability.ValidationModule`
to the gerrit section in gerrit.config
* Install [global-refdb library](https://mvnrepository.com/artifact/com.gerritforge/global-refdb) as a library module in $GERRIT_SITE/lib and add
`installModule = com.gerritforge.gerrit.globalrefdb.validation.LibModule` to the gerrit
section in gerrit.config
* Install and configure [zookeeper-refdb plugin](https://gerrit-ci.gerritforge.com/view/Plugins-master/job/plugin-zookeeper-refdb-bazel-master) based on [config.md](https://gerrit.googlesource.com/plugins/zookeeper-refdb/+/refs/heads/master/src/main/resources/Documentation/config.md)
* Configure ref-database.enabled = true in @PLUGIN@.config to enable validation with
global-refdb.

Here is an example of the minimal @PLUGIN@.config:

Active node one

```
[main]
  sharedDirectory = /directory/accessible/from/both/masters

[peerInfo "static"]
  url = http://backupNodeHost1:8081/

[http]
  user = username
  password = password

[ref-database]
  enabled = true
```

Active node two

```
[main]
  sharedDirectory = /directory/accessible/from/both/masters

[peerInfo "static"]
  url = http://primaryNodeHost:8080/

[http]
  user = username
  password = password

[ref-database]
  enabled = true
```

Minimal zookeeper-refdb.config for both active nodes:

```
[ref-database "zookeeper"]
  connectString = zookeeperhost:2181
```
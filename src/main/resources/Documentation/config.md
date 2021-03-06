
@PLUGIN@ Configuration
=========================

The @PLUGIN@ plugin must be installed on all the instances. Each instance should
be configured with the same [gerrit.serverId](https://gerrit-documentation.storage.googleapis.com/Documentation/3.2.0/config-gerrit.html#gerrit.serverId).
If there are existing changes in [NoteDb](https://gerrit-documentation.storage.googleapis.com/Documentation/3.2.0/note-db.html)
made with another `serverId`, then this plugin might not be able to access them.
Likewise, if the HA gerrit.serverIds differ, then changes conveyed by one
instance will not be accessible by the other.

The following fields should be specified in `$site_path/etc/@PLUGIN@.config` files:

File '@PLUGIN@.config'
--------------------

### Static definition of the high-availability nodes.

```
[main]
  sharedDirectory = /directory/accessible/from/both/instances
[autoReindex]
  enabled = false
[peerInfo]
  strategy = static
[peerInfo "static"]
  url = first_target_instance_url
  url = second_target_instance_url
[http]
  user = username
  password = password
```

### Dynamic jgroups-based discovery of the high-availability nodes

```
[main]
  sharedDirectory = /directory/accessible/from/both/instances
[autoReindex]
  enabled = false
[peerInfo]
  strategy = jgroups
[peerInfo "jgroups"]
  myUrl = local_instance_url
[jgroups]
  clusterName = foo
  skipInterface = lo*
  skipInterface = eth2
  protocolStack = protocolStack.xml
[http]
  user = username
  password = password
[healthcheck]
  enable = true
```

```main.sharedDirectory```
:   Path to a directory accessible from both instances.
    When given as a relative path, then it is resolved against the $SITE_PATH
    or Gerrit server. For example, if $SITE_PATH is "/gerrit/root" and
    sharedDirectory is given as "shared/dir" then the real path of the shared
    directory is "/gerrit/root/shared/dir". When not specified, the default
    is "shared".

```autoReindex.enabled```
:   Enable the tracking of the latest change indexed under data/high-availability
    for each of the indexes. At startup scans all the changes, accounts and groups
    and reindex the ones that have been updated by other nodes while the server was down.
    When not specified, the default is "false", that means no automatic tracking
    and indexing at start.

```autoReindex.delay```
:   When autoReindex is enabled, indicates the delay aftere the plugin startup,
    before triggering the conditional reindexing of all changes, accounts and groups.
    Delay is expressed in Gerrit time values as in [websession.cleanupInterval](#websessioncleanupInterval).
    When not specified, the default is "10 seconds".

```autoReindex.pollInterval```
:   When autoReindex is enabled, indicates the interval between the conditional
    reindexing of all changes, accounts and groups.
    Delay is expressed in Gerrit time values as in [websession.cleanupInterval](#websessioncleanupInterval).
    When not specified, polling of conditional reindexing is disabled.

```peerInfo.strategy```
:   Strategy to find other peers. Supported strategies are `static` or `jgroups`.
    Defaults to `jgroups`.
* The `static` strategy allows to staticly configure the peer gerrit instance using
the configuration parameter `peerInfo.static.url`.
* The `jgroups` strategy allows that a gerrit instance discovers the peer
instance by using JGroups to send multicast messages. In this case the
configuration parameters `peerInfo.jgroups.*` are used to control the sending of
the multicast messages. During startup each instance will advertise its address
over a JGroups multicast message. JGroups takes care to inform each cluster when
a member joins or leaves the cluster.

```peerInfo.static.url```
:   Specify the URL for the peer instance. If more than one peer instance is to be
    configured, add as many url entries as necessary.

```peerInfo.jgroups.myUrl```
:   The URL of this instance to be broadcast to other peers. If not specified, the
    URL is determined from the `httpd.listenUrl` in the `gerrit.config`.
    If `httpd.listenUrl` is configured with multiple values, is configured to work
    with a reverse proxy (i.e. uses `proxy-http` or `proxy-https` scheme), or is
    configured to listen on all local addresses (i.e. using hostname `*`), then
    the URL must be explicitly specified with `myUrl`.

```jgroups.clusterName```
:   The name of the high-availability cluster. When peers discover themselves dynamically this
    name is used to determine which instances should work together.  Only those Gerrit
    interfaces which are configured for the same clusterName will communicate with each other.
    Defaults to "GerritHA".

```jgroups.skipInterface```
:   A name or a wildcard of network interface(s) which should be skipped
    for JGroups communication. Peer discovery may fail if the host has multiple
    network interfaces and an inappropriate interface is chosen by JGroups.
    This option can be repeated many times in the `jgroups` section.
    Defaults to the list of: `lo*`, `utun*`, `awdl*` which are known to be
    inappropriate for JGroups communication.

```jgroups.protocolStack```
:   This optional parameter specifies the path of an xml file that contains the
    definition of JGroups protocol stack. If not specified the default protocol stack
    will be used. May be an absolute or relative path. If the path is relative it is
    resolved from the site's `etc` folder. For more information on protocol stack and
    its configuration file syntax please refer to JGroups documentation.
    See [JGroups - Advanced topics](http://jgroups.org/manual-3.x/html/user-advanced.html).

NOTE: To work properly in certain environments, JGroups needs the System property
`java.net.preferIPv4Stack` to be set to `true`.
See [JGroups - Trouble shooting](http://jgroups.org/tutorial/index.html#_trouble_shooting).

```http.user```
:   Username to connect to the peer instance.

```http.password```
:   Password to connect to the peer instance.

@PLUGIN@ plugin uses REST API calls to keep the target instance in-sync. It
is possible to customize the parameters of the underlying http client doing these
calls by specifying the following fields:

```http.connectionTimeout```
:   Maximum interval of time in milliseconds the plugin waits for a connection
    to the target instance. When not specified, the default value is set to 5000ms.

```http.socketTimeout```
:   Maximum interval of time in milliseconds the plugin waits for a response from the
    target instance once the connection has been established. When not specified,
    the default value is set to 5000ms.

```http.maxTries```
:   Maximum number of times the plugin should attempt when calling a REST API in
    the target instance. Setting this value to 0 will disable retries. When not
    specified, the default value is 360. After this number of failed tries, an
    error is logged.

```http.retryInterval```
:   The interval of time in milliseconds between the subsequent auto-retries.
    When not specified, the default value is set to 10000ms.

```cache.synchronize```
:   Whether to synchronize cache evictions.
    Defaults to true.

```cache.threadPoolSize```
:   Maximum number of threads used to send cache evictions to the target instance.
    Defaults to 4.

```cache.pattern```
:   Pattern to match names of custom caches for which evictions should be
    forwarded (in addition to the core caches that are always forwarded). May be
    specified more than once to add multiple patterns.
    Defaults to an empty list, meaning only evictions of the core caches are
    forwarded.

```event.synchronize```
:   Whether to synchronize stream events.
    Defaults to true.

```index.numStripedLocks```
:   Number of striped locks to use during reindexing. Should be of the same order
    of magnitude of the open changes. I.e.: if one has 5000 changes, one might want
    to have at least 1000 striped locks. The value has to be tuned empirically
    by checking the number of failures in acquiring the locking. Checking the
    `consider increasing the number of shards` warnings should help.
    Defaults to 10.

```index.synchronize```
:   Whether to synchronize secondary indexes.
    Defaults to true.

```index.synchronizeForced```
:   Whether to synchronize forced index events. E.g. on-line reindex
    automatically triggered upon version upgrades.
    Defaults to true.

```index.threadPoolSize```
:   Maximum number of threads used to send index events to the target instance.
    Defaults to 4.

```index.batchThreadPoolSize```
:   Maximum number of threads used to send batch index events to the target instance
    and not associated to an interactive action performed by a user.
    Defaults equal index.threadPoolSize.

```index.maxTries```
:   Maximum number of times the plugin should attempt to reindex changes.
    Setting this value to 0 will disable retries. After this number of failed tries,
    an error is logged and the local index should be considered stale and needs
    to be investigated and manually reindexed.
    Defaults to 2.

```index.retryInterval```
:   The interval of time in milliseconds between the subsequent auto-retries.
    Defaults to 30000 (30 seconds).

NOTE: the default settings for `http.socketTimeout` and `http.maxTries` ensure
that the plugin will keep retrying to forward a message for one hour.

```websession.synchronize```
:   Whether to synchronize web sessions.
    Defaults to true.

```websession.cleanupInterval```
:   Frequency for deleting expired web sessions. Values should use common time
    unit suffixes to express their setting:
* s, sec, second, seconds
* m, min, minute, minutes
* h, hr, hour, hours
* d, day, days
* w, week, weeks (`1 week` is treated as `7 days`)
* mon, month, months (`1 month` is treated as `30 days`)
* y, year, years (`1 year` is treated as `365 days`)
If a time unit suffix is not specified, `hours` is assumed.
Defaults to 24 hours.

```healthcheck.enable```
:   Whether to enable the health check endpoint. Defaults to 'true'.

```ref-database.enabled```
:   Enable the use of a global ref-database. Defaults to 'false'.

```ref-database.enforcementRules.<policy>```
:   Level of consistency enforcement across sites on a project:refs basis.
    Supports two values for enforcing the policy on multiple projects or refs.
    If the project or ref is omitted, apply the policy to all projects or all refs.

    The <policy> can be one of the following values:

    1. REQUIRED - Throw an exception if a git ref-update is processed against
    a local ref not yet in sync with the global ref-database.
    The user transaction is cancelled. LOCK_FAILURE is reported upstream.

    2. IGNORED - Do not validate against the global ref-database.

    *Example:*
    ```
    [ref-database "enforcementRules"]
       IGNORED = AProject:/refs/heads/feature
    ```

    Ignore the alignment with the global ref-db for AProject on refs/heads/feature.

    Defaults to no rule. All projects are REQUIRED to be consistent on all refs.

File 'gerrit.config'
--------------------

```gerrit.instanceId```
:   Optional identifier for this Gerrit instance.
[[docs](https://gerrit-documentation.storage.googleapis.com/Documentation/3.2.0/config-gerrit.html#gerrit.instanceId)].

Whilst this is not, specifically, a high-availability plugin configuration, it plays
an important role on which events are forwarded to peers.

If `instanceId` is set, events produced by that Gerrit instance will contain its
origin in the `instanceId` field, allowing to track where they are coming from.

The high-availability plugin will check the `instanceId` value to decide whether
an event should be forwarded: events that originated from different Gerrit instances
will not be forwarded.

When neither `gerrit.instanceId` nor `event.instanceId` are set, it is not possible
to identify the origin of the event and thus the event is always forwarded.

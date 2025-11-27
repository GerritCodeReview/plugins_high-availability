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

### JGroups based discovery and message transport

In this case URLs of peers are not needed. All peers just need to join the same JGroups cluster
defined by the `jgroups.clusterName`.
```
[main]
  transport = jgroups
  sharedDirectory = /directory/accessible/from/both/instances
[autoReindex]
  enabled = false
[jgroups]
  clusterName = foo
  skipInterface = lo*
  skipInterface = eth2
  protocolStack = protocolStack.xml
  timeout = 5000
  maxTries = 100
```

### PubSub based message transport

In this case no discovery is required. Gerrit instances will just subscribe
to the same PubSub topic(s). They will publish all messages to those topics and will
pull all messages except for their own from their subscription.

```
[main]
  transport = pubsub
  sharedDirectory = /directory/accessible/from/both/instances
[autoReindex]
  enabled = false
[pubsub]
  provider = gcp
  topic = gerrit
  streamEventsTopic = stream-events
[pubsub "gcp"]
  gcloudProject = project
  privateKeyLocation = etc/serviceAccountKey.json
  ackDeadline = 10s
  messageRetentionDuration = 7days
  retainAckedMessages = true
  subscriptionTimeout = 10s
  shutdownTimeout = 10s
  publisherThreadPoolSize = 4
  subscriberThreadPoolSize = 4
  minimumBackoff = 10s
  maximumBackoff = 10m
  maxDeliveryAttempts = 5
```

### PubSub AWS provider configuration

If you use AWS SNS/SQS as the PubSub provider, configure the following properties:

```
[main]
  transport = pubsub
  sharedDirectory = /directory/accessible/from/both/instances
[pubsub]
  provider = aws
  topic = gerrit
  streamEventsTopic = stream-events
[pubsub "aws"]
  region = us-east-1
  accessKeyIdLocation = etc/aws-access-key-id
  secretAccessKeyLocation = etc/aws-secret-access-key
  maxReceiveCount = 5
  messageProcessingThreadPoolSize = 4
```

```main.sharedDirectory```
:   Path to a directory accessible from both instances.
    When given as a relative path, then it is resolved against the $SITE_PATH
    or Gerrit server. For example, if $SITE_PATH is "/gerrit/root" and
    sharedDirectory is given as "shared/dir" then the real path of the shared
    directory is "/gerrit/root/shared/dir". When not specified, the default
    is "shared".

```main.transport```
:   Message transport layer. Could be: `http`, `jgroups` or `pubsub`.
    When not specificed the default is `http`.
    When set to `jgroups` or `pubsub` then all `peerInfo.*` sections are unnecessary and ignored.

```autoReindex.enabled```
:   Enable the tracking of the latest change indexed under data/high-availability
    for each of the indexes. At startup scans all the changes and accounts and reindex
    the ones that have been updated by other nodes while the server was down.
    When not specified, the default is "false", that means no automatic tracking
    and indexing at start.

```autoReindex.delay```
:   When autoReindex is enabled, indicates the delay aftere the plugin startup,
    before triggering the conditional reindexing of all changes and accounts.
    Delay is expressed in Gerrit time values as in [websession.cleanupInterval](#websessioncleanupInterval).
    When not specified, the default is "10 seconds".

```autoReindex.pollInterval```
:   When autoReindex is enabled, indicates the interval between the conditional
    reindexing of all changes and accounts.
    Delay is expressed in Gerrit time values as in [websession.cleanupInterval](#websessioncleanupInterval).
    When not specified, polling of conditional reindexing is disabled.

**NOTE:** The indexSync feature exposes a REST endpoint that can be used to discover project names.
Admins are advised to restrict access to the REST endpoints exposed by this plugin.

**Note:** For projects and groups reindexing, [scheduled indexer](https://gerrit-review.googlesource.com/Documentation/config-gerrit.html#scheduledIndexer) can be enabled with specific configurations.
Example configurations:

```
[scheduledIndexer "groups"]
    enabled = true
    interval = 1h
    startTime = 13:00

[scheduledIndexer "projects"]
    enabled = true
    interval = 1h
    startTime = 13:00
```
Note: Ensure these settings are added to enable periodic reindexing of groups and projects.
Groups and projects may become outdated if indexing events are missed due to the node being down or
some networking issues.

```indexSync.enabled```
:   When indexSync is enabled, the primary servers will synchronize indexes with the intention to
    self-heal any missed reindexing event.

```indexSync.delay```
:   If enabled, index sync will start running after this initial delay.
    Delay is expressed in Gerrit time values as in [websession.cleanupInterval](#websessioncleanupInterval).
    When not specified, the default is zero: run immediately.

```indexSync.period```
    Period between two index sync executions. If any execution of this task takes longer than
    this period, then subsequent executions may start late, but will not concurrently execute.
    Delay is expressed in Gerrit time values as in [websession.cleanupInterval](#websessioncleanupInterval).
    When not specified, the default is `2 seconds`.

```indexSync.initialSyncAge```
    This options defines the max age of changes in the other peer for which local index shall
    be synchronized on the initial run of the index sync task. The age defined here is usualy
    larger than the `syncAge` in order to accommodate max foreseen downtime of a server during
    restarts.
    The age is express in the format of the `age:` change query parameter.
    When not specified, the default is `1hour`.

```indexSync.syncAge```
    This option defines the max age of changes in the other peer for this local index shall be
    synchronized on each run, except for the initial run.
    The age is express in the format of the `age:` change query parameter.
    When not specified, the default is `5minutes`.


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
:   The URL of this instance to be broadcast to other peers. Alternatively, this URL
    can also be specified using the environment variable `GERRIT_URL`. This is useful
    in environments like Kubernetes, where manual configuration of each Gerrit
    instance is not possible.
    If neither the configuration option nor the system property is specified, the
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

```jgroups.kubernetes```
:   If true, a protocol stack optimized for Kubernetes will be used. Peers will be discovered
    by querying the Kubernetes API server for pods. The functionality is provided by the
    [jgroups-kubernetes extension](https://github.com/jgroups-extras/jgroups-kubernetes).
    To enable Gerrit to use the Kubernetes API, the pods require a ServiceAccount with
    permissions to list pods ([example](https://github.com/jgroups-extras/jgroups-kubernetes#demo)).
    Further, Gerrit requires a valid TLS certificate in its keystore, since the Kubernetes
    API server requires TLS. (Default: false)

```jgroups.kubernetes.namespace```
:   The namespace in which to query for pods. (Default: default)

```jgroups.kubernetes.label```
:   A label that will be used to select the pods in the format `label=value`. Can be set
    multiple times.

```jgroups.timeout```
:   Maximum interval of time in milliseconds the JGroups wait for a response
    Value is expressed in Gerrit time values as in [websession.cleanupInterval](#websessioncleanupInterval).
    forwarding a message. When not specified, the default value is 5 seconds.

```jgroups.maxTries```
:   Maximum number of times JGroups should attempt to forward a messages. Must be at least 1.
    Setting this option to zero or negative will assume the default value.
    When not specified, the default value is 720 times.

```jgroups.retryInterval```
:   The interval of time in milliseconds between the subsequent auto-retries.
    Value is expressed in Gerrit time values as in [websession.cleanupInterval](#websessioncleanupInterval).
    When not specified, the default value is set to 10 seconds.

```jgroups.threadPoolSize```
:   Maximum number of threads used to execute JGroups calls towards target instances.

NOTE: the default settings for `jgroups.timeout` and `jgroups.maxTries` ensure
that JGroups will keep retrying to forward a message for one hour.

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
    to the target instance.
    Value is expressed in Gerrit time values as in [websession.cleanupInterval](#websessioncleanupInterval).
    When not specified, the default value is set to 5 seconds.

```http.socketTimeout```
:   Maximum interval of time in milliseconds the plugin waits for a response from the
    target instance once the connection has been established.
    Value is expressed in Gerrit time values as in [websession.cleanupInterval](#websessioncleanupInterval).
    When not specified, the default value is set to 5 seconds.

```http.maxTries```
:   Maximum number of times the plugin should attempt when calling a REST API in
    the target instance. Must be at least 1.
    Setting this option to zero or negative will assume the default value;
    When not specified, the default value is 360. After this number of failed attempts, an
    error is logged.

```http.retryInterval```
:   The interval of time in milliseconds between the subsequent auto-retries.
    Value is expressed in Gerrit time values as in [websession.cleanupInterval](#websessioncleanupInterval).
    When not specified, the default value is set to 10 seconds.

```http.threadPoolSize```
:   Maximum number of threads used to execute REST calls towards target instances.

```pubsub.provider```
:   The provider of the pubsub service. Supported values are: `gcp`.
    This setting is mandatory when using pubsub.

```pubsub.topic```
:   The default pubsub topic for publishing all messages that do not have
    a specific topic. Defaults to `gerrit`.

```pubsub.streamEventsTopic```
:   PubSub topic to publish event messages to. Defaults to `stream-events`.

```pubsub.gcp.gcloudProject```
:   The name of the GCP project containing the PubSub topic to be used. This
    setting is mandatory if using PubSub.

```pubsub.gcp.privateKeyLocation```
:   The location of the file containing the service account key of the service
    account that Gerrit can use to create subscriptions for the topic configured
    in [pubsub.topic](#pubsubtopic) and to subscribe to it. This setting is
    mandatory if using PubSub.

```pubsub.gcp.ackDeadline```
:   Time span the PubSub subscription will wait for acknowledgement of the message
    before declaring message delivery as failed. Defaults to 10s.
    Value is expressed in Gerrit time values as in [websession.cleanupInterval](#websessioncleanupInterval).

```pubsub.gcp.messageRetentionDuration```
:   How long to retain unacknowledged messages in the subscription's backlog.
    If `retainAckedMessages` is `true`, then this also configures the retention
    of acknowledged messages. Defaults to 7 days.

```pubsub.gcp.retainAckedMessages```
:   Indicates whether to retain acknowledged messages. Applies to subscriptions.
    Defaults to `false`.

```pubsub.gcp.subscriptionTimeout```
:   Timeout for establishing the subscription. Defaults to 10s.
    Value is expressed in Gerrit time values as in [websession.cleanupInterval](#websessioncleanupInterval).

```pubsub.gcp.shutdownTimeout```
:   Timeout for waiting the publisher and subscriber to shut down. Defaults to 10s.
    Value is expressed in Gerrit time values as in [websession.cleanupInterval](#websessioncleanupInterval).

```pubsub.gcp.publisherThreadPoolSize```
:   Thread pool size for PubSub publisher. Defaults to 4.

```pubsub.gcp.subscriberThreadPoolSize```
:   Thread pool size for PubSub subscriber. Defaults to 4.

```pubsub.gcp.minimumBackoff```
:   The minimum delay between consecutive deliveries of a given message.
    Defaults to 10 seconds.

```pubsub.gcp.maximumBackoff```
:   The maximum delay between consecutive deliveries of a given message.
    Defaults to 10 minutes.

```pubsub.gcp.maxDeliveryAttempts```
:   The maximum number of delivery attempts for any message. After this many
    failed delivery attempts the message is moved to the dead letter topic.

```pubsub.aws.region```
:   The AWS region where SNS/SQS is hosted. This setting is mandatory when
    using AWS as the PubSub provider.

```pubsub.aws.accessKeyIdLocation```
:   The location of the file containing the AWS access key ID. May be an
    absolute or relative path. If relative, it is resolved the site directory.
    This setting is mandatory when using AWS as the PubSub provider.

```pubsub.aws.secretAccessKeyLocation```
:   The location of the file containing the AWS secret access key. May be an
    absolute or relative path. If relative, it is resolved from the site directory.
    This setting is mandatory when using AWS as the PubSub provider.

```pubsub.aws.maxReceiveCount```
:   The maximum number of times a message can be received from the SQS queue
    before it is considered a failure and moved to the dead letter queue (if
    configured). Defaults to 5.

```pubsub.aws.messageProcessingThreadPoolSize```
:   The number of threads used to process messages received from SQS. Controls
    the concurrency of message processing. Defaults to 4.

```cache.synchronize```
:   Whether to synchronize cache evictions.
    Defaults to true.

```cache.pattern```
:   Pattern to match names of custom caches for which evictions should be
    forwarded (in addition to the core caches that are always forwarded). May be
    specified more than once to add multiple patterns.
    Defaults to an empty list, meaning only evictions of the core caches are
    forwarded.

```event.allowedListeners```
:   Class name or package name of the event listener that is always allowed to receive
    all events generated locally or from a remote end.
    Can be specified multiple times for allowing multiple listeners classes or packages.
    Defaults to an empty list.

```event.synchronize```
:   Whether to synchronize stream events.
    Defaults to true.

```index.synchronize```
:   Whether to synchronize secondary indexes.
    Defaults to true.

```index.synchronizeForced```
:   Whether to synchronize forced index events. E.g. on-line reindex
    automatically triggered upon version upgrades.
    Defaults to true.

```index.threadPoolSize```
:   Maximum number of threads used to process index events in the receiving gerrit instance.
    Defaults to 4.

```index.batchThreadPoolSize```
:   Maximum number of threads used to process batch index events in the receiving gerrit instance
    and not associated to an interactive action performed by a user.
    Defaults to `index.threadPoolSize`.

```index.maxTries```
:   Maximum number of times the plugin should attempt to reindex changes.
    Must be at least 1.
    Setting this option to zero or negative will assume the default value;
    After this number of failed tries, an error is logged and the local index should be considered
    stale and needs to be investigated and manually reindexed.
    Defaults to 2.

```index.initialDelay```
:   The initial delay, internally converted in milliseconds, of triggering the
    indexing operation after the indexing even has been received.
    Typically needed when there is a well-known latency of propagation of the updates
    across the nodes sharing the same NFS volume.
    Value is expressed in Gerrit time values as in [websession.cleanupInterval](#websessioncleanupInterval).
    Defaults to 0 milliseconds, meaning that indexing happens immediately when the indexing
    event is received.

```index.retryInterval```
:   The interval of time in milliseconds between the subsequent auto-retries.
    Value is expressed in Gerrit time values as in [websession.cleanupInterval](#websessioncleanupInterval).
    Defaults to 30 seconds.

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

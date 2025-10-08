// Copyright (C) 2015 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.ericsson.gerrit.plugins.highavailability;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.gerritforge.gerrit.globalrefdb.validation.SharedRefDbConfiguration;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;

@Singleton
public class Configuration {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();

  public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
  public static final String PLUGIN_NAME = "high-availability";
  public static final String PLUGIN_CONFIG_FILE = PLUGIN_NAME + ".config";

  // common parameter to peerInfo section
  static final String PEER_INFO_SECTION = "peerInfo";

  // common parameter to jgroups section
  static final String JGROUPS_SECTION = "jgroups";

  // common parameters to cache and index sections
  static final String THREAD_POOL_SIZE_KEY = "threadPoolSize";
  static final String INITIAL_DELAY = "initialDelay";
  static final String BATCH_THREAD_POOL_SIZE_KEY = "batchThreadPoolSize";
  static final int DEFAULT_THREAD_POOL_SIZE = 4;

  private final Main main;
  private final AutoReindex autoReindex;
  private final IndexSync indexSync;
  private final PeerInfo peerInfo;
  private final JGroups jgroups;
  private final JGroupsKubernetes jgroupsKubernetes;
  private final Http http;
  private final PubSub pubSub;
  private final Cache cache;
  private final Event event;
  private final Index index;
  private final Websession websession;
  private PeerInfoStatic peerInfoStatic;
  private PeerInfoJGroups peerInfoJGroups;
  private HealthCheck healthCheck;
  private final SharedRefDbConfiguration sharedRefDb;

  public enum PeerInfoStrategy {
    JGROUPS,
    STATIC
  }

  public enum Transport {
    HTTP,
    JGROUPS,
    PUBSUB
  }

  @Inject
  Configuration(SitePaths sitePaths) {
    this(getConfigFile(sitePaths, PLUGIN_CONFIG_FILE), sitePaths);
  }

  @VisibleForTesting
  public Configuration(Config cfg, SitePaths site) {
    main = new Main(site, cfg);
    autoReindex = new AutoReindex(cfg);
    indexSync = new IndexSync(cfg);
    peerInfo = new PeerInfo(cfg);
    switch (peerInfo.strategy()) {
      case STATIC:
        peerInfoStatic = new PeerInfoStatic(cfg);
        break;
      case JGROUPS:
        peerInfoJGroups = new PeerInfoJGroups(cfg);
        break;
      default:
        throw new IllegalArgumentException("Not supported strategy: " + peerInfo.strategy);
    }
    jgroups = new JGroups(site, cfg);
    jgroupsKubernetes = new JGroupsKubernetes(cfg);
    http = new Http(cfg);
    pubSub = new PubSub(cfg);
    cache = new Cache(cfg);
    event = new Event(cfg);
    index = new Index(cfg);
    websession = new Websession(cfg);
    healthCheck = new HealthCheck(cfg);
    sharedRefDb = new SharedRefDbConfiguration(cfg, PLUGIN_NAME);
  }

  private static FileBasedConfig getConfigFile(SitePaths sitePaths, String configFileName) {
    FileBasedConfig cfg =
        new FileBasedConfig(sitePaths.etc_dir.resolve(configFileName).toFile(), FS.DETECTED);
    String fileConfigFileName = cfg.getFile().getPath();
    try {
      log.atInfo().log("Loading configuration from %s", fileConfigFileName);
      cfg.load();
    } catch (IOException | ConfigInvalidException e) {
      log.atSevere().withCause(e).log("Unable to load configuration from %s", fileConfigFileName);
    }
    return cfg;
  }

  public Main main() {
    return main;
  }

  public AutoReindex autoReindex() {
    return autoReindex;
  }

  public IndexSync indexSync() {
    return indexSync;
  }

  public PeerInfo peerInfo() {
    return peerInfo;
  }

  public PeerInfoStatic peerInfoStatic() {
    return peerInfoStatic;
  }

  public PeerInfoJGroups peerInfoJGroups() {
    return peerInfoJGroups;
  }

  public JGroups jgroups() {
    return jgroups;
  }

  public JGroupsKubernetes jgroupsKubernetes() {
    return jgroupsKubernetes;
  }

  public Http http() {
    return http;
  }

  public PubSub pubSub() {
    return pubSub;
  }

  public Cache cache() {
    return cache;
  }

  public Event event() {
    return event;
  }

  public Index index() {
    return index;
  }

  public Websession websession() {
    return websession;
  }

  public HealthCheck healthCheck() {
    return healthCheck;
  }

  public SharedRefDbConfiguration sharedRefDb() {
    return sharedRefDb;
  }

  private static int getInt(Config cfg, String section, String name, int defaultValue) {
    try {
      return cfg.getInt(section, name, defaultValue);
    } catch (IllegalArgumentException e) {
      log.atSevere().log("invalid value for %s; using default value %d", name, defaultValue);
      log.atFine().withCause(e).log("Failed to retrieve integer value");
      return defaultValue;
    }
  }

  private static int getMaxTries(Config cfg, String section, String name, int defaultValue) {
    int v = getInt(cfg, section, name, defaultValue);
    return 1 <= v ? v : defaultValue;
  }

  private static Duration getDuration(
      Config cfg, String section, String setting, Duration defaultValue) {
    return Duration.ofMillis(
        ConfigUtil.getTimeUnit(cfg, section, null, setting, defaultValue.toMillis(), MILLISECONDS));
  }

  public static class Main {
    static final String MAIN_SECTION = "main";
    static final String SHARED_DIRECTORY_KEY = "sharedDirectory";
    static final String DEFAULT_SHARED_DIRECTORY = "shared";
    static final String TRANSPORT_KEY = "transport";
    static final Transport DEFAULT_TRANSPORT = Transport.HTTP;

    private final Transport transport;
    private final Path sharedDirectory;

    private Main(SitePaths site, Config cfg) {
      transport = cfg.getEnum(MAIN_SECTION, null, TRANSPORT_KEY, DEFAULT_TRANSPORT);
      String shared = Strings.emptyToNull(cfg.getString(MAIN_SECTION, null, SHARED_DIRECTORY_KEY));
      if (shared == null) {
        shared = DEFAULT_SHARED_DIRECTORY;
      }
      Path p = Paths.get(shared);
      if (p.isAbsolute()) {
        sharedDirectory = p;
      } else {
        sharedDirectory = site.resolve(shared);
      }
    }

    public Transport transport() {
      return transport;
    }

    public Path sharedDirectory() {
      return sharedDirectory;
    }
  }

  public static class AutoReindex {

    static final String AUTO_REINDEX_SECTION = "autoReindex";
    static final String ENABLED = "enabled";
    static final String DELAY = "delay";
    static final String POLL_INTERVAL = "pollInterval";
    static final boolean DEFAULT_AUTO_REINDEX = false;
    static final Duration DEFAULT_DELAY = Duration.ofSeconds(10);
    static final Duration DEFAULT_POLL_INTERVAL = Duration.ZERO;

    private final boolean enabled;
    private final Duration delay;
    private final Duration pollInterval;

    public AutoReindex(Config cfg) {
      enabled = cfg.getBoolean(AUTO_REINDEX_SECTION, ENABLED, DEFAULT_AUTO_REINDEX);
      delay = getDuration(cfg, AUTO_REINDEX_SECTION, DELAY, DEFAULT_DELAY);
      pollInterval = getDuration(cfg, AUTO_REINDEX_SECTION, POLL_INTERVAL, DEFAULT_POLL_INTERVAL);
    }

    public boolean enabled() {
      return enabled;
    }

    public Duration delay() {
      return delay;
    }

    public Duration pollInterval() {
      return pollInterval;
    }
  }

  public static class IndexSync {

    static final String INDEX_SYNC_SECTION = "indexSync";
    static final String ENABLED = "enabled";
    static final String DELAY = "delay";
    static final String PERIOD = "period";
    static final String INITIAL_SYNC_AGE = "initialSyncAge";
    static final String SYNC_AGE = "syncAge";

    static final boolean DEFAULT_SYNC_INDEX = false;
    static final Duration DEFAULT_DELAY = Duration.ofSeconds(0);
    static final Duration DEFAULT_PERIOD = Duration.ofSeconds(2);
    static final String DEFAULT_INITIAL_SYNC_AGE = "1hour";
    static final String DEFAULT_SYNC_AGE = "1minute";

    private final boolean enabled;
    private final Duration delay;
    private final Duration period;
    private final String initialSyncAge;
    private final String syncAge;

    public IndexSync(Config cfg) {
      enabled = cfg.getBoolean(INDEX_SYNC_SECTION, ENABLED, DEFAULT_SYNC_INDEX);
      delay = getDuration(cfg, INDEX_SYNC_SECTION, DELAY, DEFAULT_DELAY);
      period = getDuration(cfg, INDEX_SYNC_SECTION, PERIOD, DEFAULT_PERIOD);

      String v = cfg.getString(INDEX_SYNC_SECTION, "", INITIAL_SYNC_AGE);
      initialSyncAge = v != null ? v : DEFAULT_INITIAL_SYNC_AGE;

      v = cfg.getString(INDEX_SYNC_SECTION, "", SYNC_AGE);
      syncAge = v != null ? v : DEFAULT_SYNC_AGE;
    }

    public boolean enabled() {
      return enabled;
    }

    public Duration delay() {
      return delay;
    }

    public Duration period() {
      return period;
    }

    public String initialSyncAge() {
      return initialSyncAge;
    }

    public String syncAge() {
      return syncAge;
    }
  }

  public static class PeerInfo {
    static final PeerInfoStrategy DEFAULT_PEER_INFO_STRATEGY = PeerInfoStrategy.STATIC;
    static final String STRATEGY_KEY = "strategy";

    private final PeerInfoStrategy strategy;

    private PeerInfo(Config cfg) {
      strategy = cfg.getEnum(PEER_INFO_SECTION, null, STRATEGY_KEY, DEFAULT_PEER_INFO_STRATEGY);
      log.atFine().log("Strategy: %s", strategy.name());
    }

    public PeerInfoStrategy strategy() {
      return strategy;
    }
  }

  public static class PeerInfoStatic {
    static final String STATIC_SUBSECTION = PeerInfoStrategy.STATIC.name().toLowerCase();
    static final String URL_KEY = "url";

    private final Set<String> urls;

    private PeerInfoStatic(Config cfg) {
      urls =
          Arrays.stream(cfg.getStringList(PEER_INFO_SECTION, STATIC_SUBSECTION, URL_KEY))
              .filter(Objects::nonNull)
              .filter(s -> !s.isEmpty())
              .map(s -> CharMatcher.is('/').trimTrailingFrom(s))
              .collect(Collectors.toSet());
      log.atFine().log("Urls: %s", urls);
    }

    public Set<String> urls() {
      return ImmutableSet.copyOf(urls);
    }
  }

  public static class PeerInfoJGroups {
    static final String JGROUPS_SUBSECTION = PeerInfoStrategy.JGROUPS.name().toLowerCase();
    static final String MY_URL_KEY = "myUrl";

    private final String myUrl;

    private PeerInfoJGroups(Config cfg) {
      myUrl = trimTrailingSlash(cfg.getString(PEER_INFO_SECTION, JGROUPS_SUBSECTION, MY_URL_KEY));
      log.atFine().log("My Url: %s", myUrl);
    }

    public String myUrl() {
      return myUrl;
    }

    @Nullable
    private static String trimTrailingSlash(@Nullable String in) {
      return in == null ? in : CharMatcher.is('/').trimTrailingFrom(in);
    }
  }

  public static class JGroups {
    static final int DEFAULT_MAX_TRIES = 720;
    static final Duration DEFAULT_RETRY_INTERVAL = Duration.ofSeconds(10);

    static final String SKIP_INTERFACE_KEY = "skipInterface";
    static final String CLUSTER_NAME_KEY = "clusterName";
    static final String MAX_TRIES_KEY = "maxTries";
    static final String RETRY_INTERVAL_KEY = "retryInterval";
    static final String TIMEOUT_KEY = "timeout";
    static final String KUBERNETES_KEY = "kubernetes";
    static final String PROTOCOL_STACK_KEY = "protocolStack";
    static final ImmutableList<String> DEFAULT_SKIP_INTERFACE_LIST =
        ImmutableList.of("lo*", "utun*", "awdl*");
    static final String DEFAULT_CLUSTER_NAME = "GerritHA";

    private final ImmutableList<String> skipInterface;
    private final String clusterName;
    private final Duration timeout;
    private final int maxTries;
    private final Duration retryInterval;
    private final int threadPoolSize;
    private final boolean useKubernetes;
    private final Optional<Path> protocolStack;

    private JGroups(SitePaths site, Config cfg) {
      String[] skip = cfg.getStringList(JGROUPS_SECTION, null, SKIP_INTERFACE_KEY);
      skipInterface = skip.length == 0 ? DEFAULT_SKIP_INTERFACE_LIST : ImmutableList.copyOf(skip);
      log.atFine().log("Skip interface(s): %s", skipInterface);
      clusterName = getString(cfg, JGROUPS_SECTION, null, CLUSTER_NAME_KEY, DEFAULT_CLUSTER_NAME);
      log.atFine().log("Cluster name: %s", clusterName);
      timeout = getDuration(cfg, JGROUPS_SECTION, TIMEOUT_KEY, DEFAULT_TIMEOUT);
      maxTries = getMaxTries(cfg, JGROUPS_SECTION, MAX_TRIES_KEY, DEFAULT_MAX_TRIES);
      retryInterval = getDuration(cfg, JGROUPS_SECTION, RETRY_INTERVAL_KEY, DEFAULT_RETRY_INTERVAL);
      threadPoolSize = getInt(cfg, JGROUPS_SECTION, THREAD_POOL_SIZE_KEY, DEFAULT_THREAD_POOL_SIZE);
      useKubernetes = cfg.getBoolean(JGROUPS_SECTION, KUBERNETES_KEY, false);
      protocolStack = getProtocolStack(cfg, site);
      log.atFine().log(
          "Protocol stack config %s",
          protocolStack.isPresent() ? protocolStack.get() : "not configured, using default stack.");
    }

    private static String getString(
        Config cfg, String section, String subSection, String name, String defaultValue) {
      String value = cfg.getString(section, subSection, name);
      return value == null ? defaultValue : value;
    }

    private static Optional<Path> getProtocolStack(Config cfg, SitePaths site) {
      String location = cfg.getString(JGROUPS_SECTION, null, PROTOCOL_STACK_KEY);
      return location == null ? Optional.empty() : Optional.of(site.etc_dir.resolve(location));
    }

    public Optional<Path> protocolStack() {
      return protocolStack;
    }

    public ImmutableList<String> skipInterface() {
      return skipInterface;
    }

    public String clusterName() {
      return clusterName;
    }

    public Duration timeout() {
      return timeout;
    }

    public int maxTries() {
      return maxTries;
    }

    public Duration retryInterval() {
      return retryInterval;
    }

    public int threadPoolSize() {
      return threadPoolSize;
    }

    public boolean useKubernetes() {
      return useKubernetes;
    }
  }

  public static class JGroupsKubernetes {
    static final String KUBERNETES_SUBSECTION = "kubernetes";
    static final String NAMESPACE_KEY = "namespace";
    static final String LABEL_KEY = "label";

    private final String namespace;
    private final List<String> labels;

    public JGroupsKubernetes(Config cfg) {
      namespace = cfg.getString(JGROUPS_SECTION, KUBERNETES_SUBSECTION, NAMESPACE_KEY);
      labels = Arrays.asList(cfg.getStringList(JGROUPS_SECTION, KUBERNETES_SUBSECTION, LABEL_KEY));
    }

    public String namespace() {
      return namespace;
    }

    public List<String> labels() {
      return labels;
    }
  }

  public static class Http {
    public static final int DEFAULT_MAX_TRIES = 360;
    public static final Duration DEFAULT_RETRY_INTERVAL = Duration.ofSeconds(10);
    public static final int DEFAULT_THREAD_POOL_SIZE = 4;

    static final String HTTP_SECTION = "http";
    static final String USER_KEY = "user";
    static final String PASSWORD_KEY = "password";
    static final String CONNECTION_TIMEOUT_KEY = "connectionTimeout";
    static final String SOCKET_TIMEOUT_KEY = "socketTimeout";
    static final String MAX_TRIES_KEY = "maxTries";
    static final String RETRY_INTERVAL_KEY = "retryInterval";
    static final String THREAD_POOL_SIZE_KEY = "threadPoolSize";

    private final String user;
    private final String password;
    private final Duration connectionTimeout;
    private final Duration socketTimeout;
    private final int maxTries;
    private final Duration retryInterval;
    private final int threadPoolSize;

    private Http(Config cfg) {
      user = Strings.nullToEmpty(cfg.getString(HTTP_SECTION, null, USER_KEY));
      password = Strings.nullToEmpty(cfg.getString(HTTP_SECTION, null, PASSWORD_KEY));
      connectionTimeout = getDuration(cfg, HTTP_SECTION, CONNECTION_TIMEOUT_KEY, DEFAULT_TIMEOUT);
      socketTimeout = getDuration(cfg, HTTP_SECTION, SOCKET_TIMEOUT_KEY, DEFAULT_TIMEOUT);
      maxTries = getMaxTries(cfg, HTTP_SECTION, MAX_TRIES_KEY, DEFAULT_MAX_TRIES);
      retryInterval = getDuration(cfg, HTTP_SECTION, RETRY_INTERVAL_KEY, DEFAULT_RETRY_INTERVAL);
      threadPoolSize = getInt(cfg, HTTP_SECTION, THREAD_POOL_SIZE_KEY, DEFAULT_THREAD_POOL_SIZE);
    }

    public String user() {
      return user;
    }

    public String password() {
      return password;
    }

    public Duration connectionTimeout() {
      return connectionTimeout;
    }

    public Duration socketTimeout() {
      return socketTimeout;
    }

    public int maxTries() {
      return maxTries;
    }

    public Duration retryInterval() {
      return retryInterval;
    }

    public int threadPoolSize() {
      return threadPoolSize;
    }
  }

  public static class PubSub {
    static final String PUBSUB_SECTION = "pubsub";
    static final String GCLOUD_PROJECT_FIELD = "gcloudProject";
    static final String PRIVATE_KEY_LOCATION_FIELD = "privateKeyLocation";
    static final String TOPIC_FIELD = "topic";
    static final String ACK_DEADLINE_FIELD = "ackDeadline";
    static final String SUBSCRIPTION_TIMEOUT_FIELD = "subscriptionTimeout";
    static final String SHUTDOWN_TIMEOUT_FIELD = "shutdownTimeout";
    static final String PUBLISHER_THREAD_POOL_SIZE_FIELD = "publisherThreadPoolSize";
    static final String SUBSCRIBER_THREAD_POOL_SIZE_FIELD = "subscriberThreadPoolSize";

    static final Duration DEFAULT_ACK_DEADLINE = Duration.ofSeconds(10);
    static final Duration DEFAULT_SUBSCRIPTION_TIMEOUT = Duration.ofSeconds(10);
    static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);
    static final String DEFAULT_TOPIC = "gerrit";
    static final int DEFAULT_PUBLISHER_THREAD_POOL_SIZE = 4;
    static final int DEFAULT_SUBSCRIBER_THREAD_POOL_SIZE = 4;

    private final String gcloudProject;
    private final String privateKeyLocation;
    private final Duration ackDeadline;
    private final Duration subscriptionTimeout;
    private final Duration shutdownTimeout;
    private final String topic;
    private final int publisherThreadPoolSize;
    private final int subscriberThreadPoolSize;

    @Inject
    public PubSub(Config cfg) {
      this.gcloudProject = getString(cfg, PUBSUB_SECTION, GCLOUD_PROJECT_FIELD, null);
      this.privateKeyLocation = getString(cfg, PUBSUB_SECTION, PRIVATE_KEY_LOCATION_FIELD, null);
      this.topic = getString(cfg, PUBSUB_SECTION, TOPIC_FIELD, DEFAULT_TOPIC);
      this.ackDeadline = getDuration(cfg, PUBSUB_SECTION, ACK_DEADLINE_FIELD, DEFAULT_ACK_DEADLINE);
      this.subscriptionTimeout =
          getDuration(
              cfg, PUBSUB_SECTION, SUBSCRIPTION_TIMEOUT_FIELD, DEFAULT_SUBSCRIPTION_TIMEOUT);
      this.shutdownTimeout =
          getDuration(cfg, PUBSUB_SECTION, SHUTDOWN_TIMEOUT_FIELD, DEFAULT_SHUTDOWN_TIMEOUT);
      this.publisherThreadPoolSize =
          cfg.getInt(
              PUBSUB_SECTION, PUBLISHER_THREAD_POOL_SIZE_FIELD, DEFAULT_PUBLISHER_THREAD_POOL_SIZE);
      this.subscriberThreadPoolSize =
          cfg.getInt(
              PUBSUB_SECTION,
              SUBSCRIBER_THREAD_POOL_SIZE_FIELD,
              DEFAULT_SUBSCRIBER_THREAD_POOL_SIZE);
    }

    public static String getString(Config cfg, String section, String field, String def) {
      String value = cfg.getString(section, null, field);
      return value == null ? def : value;
    }

    public String gCloudProject() {
      return gcloudProject;
    }

    public String privateKeyLocation() {
      return privateKeyLocation;
    }

    public Duration ackDeadline() {
      return ackDeadline;
    }

    public Duration subscriptionTimeout() {
      return subscriptionTimeout;
    }

    public Duration shutdownTimeout() {
      return shutdownTimeout;
    }

    public String topic() {
      return topic;
    }

    public int publisherThreadPoolSize() {
      return publisherThreadPoolSize;
    }

    public int subscriberThreadPoolSize() {
      return subscriberThreadPoolSize;
    }
  }

  /** Common parameters to cache, event, index and websession */
  public abstract static class Forwarding {
    static final boolean DEFAULT_SYNCHRONIZE = true;
    static final String SYNCHRONIZE_KEY = "synchronize";

    private final boolean synchronize;

    private Forwarding(Config cfg, String section) {
      synchronize = getBoolean(cfg, section, SYNCHRONIZE_KEY, DEFAULT_SYNCHRONIZE);
    }

    private static boolean getBoolean(
        Config cfg, String section, String name, boolean defaultValue) {
      try {
        return cfg.getBoolean(section, name, defaultValue);
      } catch (IllegalArgumentException e) {
        log.atSevere().log("invalid value for %s; using default value %s", name, defaultValue);
        log.atFine().withCause(e).log("Failed to retrieve boolean value");
        return defaultValue;
      }
    }

    public boolean synchronize() {
      return synchronize;
    }
  }

  public static class Cache extends Forwarding {
    static final String CACHE_SECTION = "cache";
    static final String PATTERN_KEY = "pattern";

    private final List<String> patterns;

    private Cache(Config cfg) {
      super(cfg, CACHE_SECTION);
      patterns = Arrays.asList(cfg.getStringList(CACHE_SECTION, null, PATTERN_KEY));
    }

    public List<String> patterns() {
      return Collections.unmodifiableList(patterns);
    }
  }

  public static class Event extends Forwarding {
    static final String EVENT_SECTION = "event";
    static final String ALLOWED_LISTENERS = "allowedListeners";

    private final Set<String> allowedListeners;

    private Event(Config cfg) {
      super(cfg, EVENT_SECTION);

      allowedListeners = Sets.newHashSet(cfg.getStringList(EVENT_SECTION, null, ALLOWED_LISTENERS));
    }

    public Set<String> allowedListeners() {
      return allowedListeners;
    }
  }

  public static class Index extends Forwarding {
    static final int DEFAULT_MAX_TRIES = 2;
    static final Duration DEFAULT_RETRY_INTERVAL = Duration.ofSeconds(30);
    static final Duration DEFAULT_INITIAL_DELAY = Duration.ofMillis(0);

    static final String INDEX_SECTION = "index";
    static final String MAX_TRIES_KEY = "maxTries";
    static final String RETRY_INTERVAL_KEY = "retryInterval";
    static final String SYNCHRONIZE_FORCED_KEY = "synchronizeForced";
    static final boolean DEFAULT_SYNCHRONIZE_FORCED = true;

    private final int threadPoolSize;
    private final long initialDelayMsec;
    private final int batchThreadPoolSize;
    private final Duration retryInterval;
    private final int maxTries;
    private final boolean synchronizeForced;

    private Index(Config cfg) {
      super(cfg, INDEX_SECTION);
      threadPoolSize = getInt(cfg, INDEX_SECTION, THREAD_POOL_SIZE_KEY, DEFAULT_THREAD_POOL_SIZE);
      initialDelayMsec =
          getDuration(cfg, INDEX_SECTION, INITIAL_DELAY, DEFAULT_INITIAL_DELAY).toMillis();
      batchThreadPoolSize = getInt(cfg, INDEX_SECTION, BATCH_THREAD_POOL_SIZE_KEY, threadPoolSize);
      retryInterval = getDuration(cfg, INDEX_SECTION, RETRY_INTERVAL_KEY, DEFAULT_RETRY_INTERVAL);
      maxTries = getMaxTries(cfg, INDEX_SECTION, MAX_TRIES_KEY, DEFAULT_MAX_TRIES);
      synchronizeForced =
          cfg.getBoolean(INDEX_SECTION, SYNCHRONIZE_FORCED_KEY, DEFAULT_SYNCHRONIZE_FORCED);
    }

    public int threadPoolSize() {
      return threadPoolSize;
    }

    public long initialDelayMsec() {
      return initialDelayMsec;
    }

    public int batchThreadPoolSize() {
      return batchThreadPoolSize;
    }

    public Duration retryInterval() {
      return retryInterval;
    }

    public int maxTries() {
      return maxTries;
    }

    public boolean synchronizeForced() {
      return synchronizeForced;
    }
  }

  public static class Websession extends Forwarding {
    static final String WEBSESSION_SECTION = "websession";
    static final String CLEANUP_INTERVAL_KEY = "cleanupInterval";
    static final String DEFAULT_CLEANUP_INTERVAL_AS_STRING = "24 hours";
    static final Duration DEFAULT_CLEANUP_INTERVAL = Duration.ofHours(24);

    private final Duration cleanupInterval;

    private Websession(Config cfg) {
      super(cfg, WEBSESSION_SECTION);
      cleanupInterval =
          getDuration(cfg, WEBSESSION_SECTION, CLEANUP_INTERVAL_KEY, DEFAULT_CLEANUP_INTERVAL);
    }

    public Duration cleanupInterval() {
      return cleanupInterval;
    }
  }

  public static class HealthCheck {
    static final String HEALTH_CHECK_SECTION = "healthCheck";
    static final String ENABLE_KEY = "enable";
    static final boolean DEFAULT_HEALTH_CHECK_ENABLED = true;

    private final boolean enabled;

    private HealthCheck(Config cfg) {
      enabled = cfg.getBoolean(HEALTH_CHECK_SECTION, ENABLE_KEY, DEFAULT_HEALTH_CHECK_ENABLED);
    }

    public boolean enabled() {
      return enabled;
    }
  }
}

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
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;

@Singleton
public record Configuration(
    Main main,
    IndexSync indexSync,
    PeerInfo peerInfo,
    PeerInfoStatic peerInfoStatic,
    PeerInfoJGroups peerInfoJGroups,
    JGroups jgroups,
    JGroupsKubernetes jgroupsKubernetes,
    Http http,
    Cache cache,
    Event event,
    Index index,
    Websession websession,
    HealthCheck healthCheck,
    SharedRefDbConfiguration sharedRefDb) {

  private static final FluentLogger log = FluentLogger.forEnclosingClass();

  // Common config key names
  static final String BATCH_THREAD_POOL_SIZE_KEY = "batchThreadPoolSize";
  static final String MAX_TRIES_KEY = "maxTries";
  static final String PEER_INFO_SECTION = "peerInfo";
  static final String JGROUPS_SECTION = "jgroups";
  static final String RETRY_INTERVAL_KEY = "retryInterval";
  static final String THREAD_POOL_SIZE_KEY = "threadPoolSize";

  // Common default values
  static final int DEFAULT_THREAD_POOL_SIZE = 4;
  public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

  public enum PeerInfoStrategy {
    JGROUPS,
    STATIC
  }

  public enum Transport {
    HTTP,
    JGROUPS,
    PROVIDED
  }

  @Inject
  Configuration(SitePaths sitePaths, @PluginName String pluginName) {
    this(loadConfig(sitePaths, pluginName + ".config"), sitePaths, pluginName);
  }

  @VisibleForTesting
  public Configuration(Config cfg, SitePaths site, String pluginName) {
    this(
        new Main(site, cfg),
        new IndexSync(cfg),
        new PeerInfo(cfg),
        new PeerInfoStatic(cfg),
        new PeerInfoJGroups(cfg),
        new JGroups(site, cfg),
        new JGroupsKubernetes(cfg),
        new Http(cfg),
        new Cache(cfg),
        new Event(cfg),
        new Index(cfg),
        new Websession(cfg),
        new HealthCheck(cfg),
        new SharedRefDbConfiguration(cfg, pluginName));
  }

  public record Main(Transport transport, Path sharedDirectory) {
    static final String MAIN_SECTION = "main";
    static final String SHARED_DIRECTORY_KEY = "sharedDirectory";

    static final String DEFAULT_SHARED_DIRECTORY = "shared";

    Main(SitePaths site, Config cfg) {
      this(
          cfg.getEnum(MAIN_SECTION, null, "transport", Transport.HTTP),
          resolveSharedDirectory(site, cfg));
    }

    private static Path resolveSharedDirectory(SitePaths site, Config cfg) {
      String shared = Strings.emptyToNull(cfg.getString(MAIN_SECTION, null, SHARED_DIRECTORY_KEY));
      if (shared == null) {
        shared = DEFAULT_SHARED_DIRECTORY;
      }
      Path p = Paths.get(shared);
      return p.isAbsolute() ? p : site.resolve(shared);
    }
  }

  public record IndexSync(
      boolean enabled, Duration delay, Duration period, String initialSyncAge, String syncAge) {

    static final String INDEX_SYNC_SECTION = "indexSync";

    IndexSync(Config cfg) {
      this(
          cfg.getBoolean(INDEX_SYNC_SECTION, "enabled", false),
          getDuration(cfg, INDEX_SYNC_SECTION, "delay", Duration.ofSeconds(0)),
          getDuration(cfg, INDEX_SYNC_SECTION, "period", Duration.ofSeconds(2)),
          nullToDefault(cfg.getString(INDEX_SYNC_SECTION, "", "initialSyncAge"), "1hour"),
          nullToDefault(cfg.getString(INDEX_SYNC_SECTION, "", "syncAge"), "1minute"));
    }
  }

  public record PeerInfo(PeerInfoStrategy strategy) {
    static final String STRATEGY_KEY = "strategy";

    static final PeerInfoStrategy DEFAULT_PEER_INFO_STRATEGY = PeerInfoStrategy.STATIC;

    PeerInfo(Config cfg) {
      this(cfg.getEnum(PEER_INFO_SECTION, null, STRATEGY_KEY, DEFAULT_PEER_INFO_STRATEGY));
    }
  }

  public record PeerInfoStatic(ImmutableSet<String> urls) {
    static final String STATIC_SUBSECTION = PeerInfoStrategy.STATIC.name().toLowerCase();
    static final String URL_KEY = "url";

    PeerInfoStatic(Config cfg) {
      this(
          Arrays.stream(cfg.getStringList(PEER_INFO_SECTION, STATIC_SUBSECTION, URL_KEY))
              .filter(Objects::nonNull)
              .filter(s -> !s.isEmpty())
              .map(s -> CharMatcher.is('/').trimTrailingFrom(s))
              .collect(ImmutableSet.toImmutableSet()));
    }
  }

  public record PeerInfoJGroups(@Nullable String myUrl) {
    static final String JGROUPS_SUBSECTION = PeerInfoStrategy.JGROUPS.name().toLowerCase();
    static final String MY_URL_KEY = "myUrl";

    PeerInfoJGroups(Config cfg) {
      this(trimTrailingSlash(cfg.getString(PEER_INFO_SECTION, JGROUPS_SUBSECTION, MY_URL_KEY)));
    }

    @Nullable
    private static String trimTrailingSlash(@Nullable String in) {
      return in == null ? in : CharMatcher.is('/').trimTrailingFrom(in);
    }
  }

  public record JGroups(
      ImmutableList<String> skipInterface,
      String clusterName,
      Duration timeout,
      int maxTries,
      Duration retryInterval,
      int threadPoolSize,
      boolean useKubernetes,
      Optional<Path> protocolStack) {

    static final String CLUSTER_NAME_KEY = "clusterName";
    static final String PROTOCOL_STACK_KEY = "protocolStack";
    static final String SKIP_INTERFACE_KEY = "skipInterface";

    static final String DEFAULT_CLUSTER_NAME = "GerritHA";
    static final int DEFAULT_MAX_TRIES = 720;
    static final Duration DEFAULT_RETRY_INTERVAL = Duration.ofSeconds(10);
    static final ImmutableList<String> DEFAULT_SKIP_INTERFACE_LIST =
        ImmutableList.of("lo*", "utun*", "awdl*");

    JGroups(SitePaths site, Config cfg) {
      this(
          buildSkipInterface(cfg),
          nullToDefault(
              cfg.getString(JGROUPS_SECTION, null, CLUSTER_NAME_KEY), DEFAULT_CLUSTER_NAME),
          getDuration(cfg, JGROUPS_SECTION, "timeout", DEFAULT_TIMEOUT),
          getMaxTries(cfg, JGROUPS_SECTION, MAX_TRIES_KEY, DEFAULT_MAX_TRIES),
          getDuration(cfg, JGROUPS_SECTION, RETRY_INTERVAL_KEY, DEFAULT_RETRY_INTERVAL),
          getInt(cfg, JGROUPS_SECTION, THREAD_POOL_SIZE_KEY, DEFAULT_THREAD_POOL_SIZE),
          cfg.getBoolean(JGROUPS_SECTION, "kubernetes", false),
          buildProtocolStack(cfg, site));
    }

    private static ImmutableList<String> buildSkipInterface(Config cfg) {
      String[] skip = cfg.getStringList(JGROUPS_SECTION, null, SKIP_INTERFACE_KEY);
      return skip.length == 0 ? DEFAULT_SKIP_INTERFACE_LIST : ImmutableList.copyOf(skip);
    }

    private static Optional<Path> buildProtocolStack(Config cfg, SitePaths site) {
      String location = cfg.getString(JGROUPS_SECTION, null, PROTOCOL_STACK_KEY);
      return location == null ? Optional.empty() : Optional.of(site.etc_dir.resolve(location));
    }
  }

  public record JGroupsKubernetes(String namespace, ImmutableList<String> labels) {
    static final String KUBERNETES_SUBSECTION = "kubernetes";

    JGroupsKubernetes(Config cfg) {
      this(
          cfg.getString(JGROUPS_SECTION, KUBERNETES_SUBSECTION, "namespace"),
          ImmutableList.copyOf(cfg.getStringList(JGROUPS_SECTION, KUBERNETES_SUBSECTION, "label")));
    }
  }

  public record Http(
      String user,
      String password,
      Duration connectionTimeout,
      Duration socketTimeout,
      int maxTries,
      Duration retryInterval,
      int threadPoolSize,
      int connectionsPerRoute,
      int maxConnections) {

    static final String CONNECTION_TIMEOUT_KEY = "connectionTimeout";
    static final String HTTP_SECTION = "http";
    static final String PASSWORD_KEY = "password";
    static final String SOCKET_TIMEOUT_KEY = "socketTimeout";
    static final String USER_KEY = "user";

    static final int DEFAULT_MAX_TRIES = 360;
    static final Duration DEFAULT_RETRY_INTERVAL = Duration.ofSeconds(10);
    static final int DEFAULT_THREAD_POOL_SIZE = 4;

    Http(Config cfg) {
      this(
          Strings.nullToEmpty(cfg.getString(HTTP_SECTION, null, USER_KEY)),
          Strings.nullToEmpty(cfg.getString(HTTP_SECTION, null, PASSWORD_KEY)),
          getDuration(cfg, HTTP_SECTION, CONNECTION_TIMEOUT_KEY, DEFAULT_TIMEOUT),
          getDuration(cfg, HTTP_SECTION, SOCKET_TIMEOUT_KEY, DEFAULT_TIMEOUT),
          getMaxTries(cfg, HTTP_SECTION, MAX_TRIES_KEY, DEFAULT_MAX_TRIES),
          getDuration(cfg, HTTP_SECTION, RETRY_INTERVAL_KEY, DEFAULT_RETRY_INTERVAL),
          getInt(cfg, HTTP_SECTION, THREAD_POOL_SIZE_KEY, DEFAULT_THREAD_POOL_SIZE),
          getInt(cfg, HTTP_SECTION, "connectionsPerRoute", 100),
          getInt(cfg, HTTP_SECTION, "maxConnections", 200));
    }
  }

  /** Common parameters to cache, event, index and websession */
  public sealed interface Forwarding permits Cache, Event, Index, Websession {
    boolean DEFAULT_SYNCHRONIZE = true;
    String SYNCHRONIZE_KEY = "synchronize";

    boolean synchronize();
  }

  public record Cache(boolean synchronize, ImmutableList<String> patterns) implements Forwarding {
    static final String CACHE_SECTION = "cache";
    static final String PATTERN_KEY = "pattern";

    Cache(Config cfg) {
      this(
          getBoolean(
              cfg, CACHE_SECTION, Forwarding.SYNCHRONIZE_KEY, Forwarding.DEFAULT_SYNCHRONIZE),
          ImmutableList.copyOf(cfg.getStringList(CACHE_SECTION, null, PATTERN_KEY)));
    }
  }

  public record Event(boolean synchronize, ImmutableSet<String> allowedListeners)
      implements Forwarding {
    static final String ALLOWED_LISTENERS = "allowedListeners";
    static final String EVENT_SECTION = "event";

    Event(Config cfg) {
      this(
          getBoolean(
              cfg, EVENT_SECTION, Forwarding.SYNCHRONIZE_KEY, Forwarding.DEFAULT_SYNCHRONIZE),
          ImmutableSet.copyOf(cfg.getStringList(EVENT_SECTION, null, ALLOWED_LISTENERS)));
    }
  }

  public record Index(
      boolean synchronize,
      int threadPoolSize,
      long initialDelayMsec,
      int batchThreadPoolSize,
      Duration retryInterval,
      int maxTries,
      boolean synchronizeForced)
      implements Forwarding {

    static final String INDEX_SECTION = "index";
    static final String SYNCHRONIZE_FORCED_KEY = "synchronizeForced";

    static final int DEFAULT_MAX_TRIES = 2;
    static final Duration DEFAULT_RETRY_INTERVAL = Duration.ofSeconds(30);
    static final boolean DEFAULT_SYNCHRONIZE_FORCED = true;

    Index(Config cfg) {
      this(
          getBoolean(
              cfg, INDEX_SECTION, Forwarding.SYNCHRONIZE_KEY, Forwarding.DEFAULT_SYNCHRONIZE),
          getInt(cfg, INDEX_SECTION, THREAD_POOL_SIZE_KEY, DEFAULT_THREAD_POOL_SIZE),
          getDuration(cfg, INDEX_SECTION, "initialDelay", Duration.ofMillis(0)).toMillis(),
          getInt(
              cfg,
              INDEX_SECTION,
              BATCH_THREAD_POOL_SIZE_KEY,
              getInt(cfg, INDEX_SECTION, THREAD_POOL_SIZE_KEY, DEFAULT_THREAD_POOL_SIZE)),
          getDuration(cfg, INDEX_SECTION, RETRY_INTERVAL_KEY, DEFAULT_RETRY_INTERVAL),
          getMaxTries(cfg, INDEX_SECTION, MAX_TRIES_KEY, DEFAULT_MAX_TRIES),
          cfg.getBoolean(INDEX_SECTION, SYNCHRONIZE_FORCED_KEY, DEFAULT_SYNCHRONIZE_FORCED));
    }
  }

  public record Websession(boolean synchronize, Duration cleanupInterval) implements Forwarding {
    static final String CLEANUP_INTERVAL_KEY = "cleanupInterval";
    static final String WEBSESSION_SECTION = "websession";

    static final Duration DEFAULT_CLEANUP_INTERVAL = Duration.ofHours(24);
    static final String DEFAULT_CLEANUP_INTERVAL_AS_STRING = "24 hours";

    Websession(Config cfg) {
      this(
          getBoolean(
              cfg, WEBSESSION_SECTION, Forwarding.SYNCHRONIZE_KEY, Forwarding.DEFAULT_SYNCHRONIZE),
          getDuration(cfg, WEBSESSION_SECTION, CLEANUP_INTERVAL_KEY, DEFAULT_CLEANUP_INTERVAL));
    }
  }

  public record HealthCheck(boolean enabled) {
    static final String ENABLE_KEY = "enable";
    static final String HEALTH_CHECK_SECTION = "healthCheck";

    static final boolean DEFAULT_HEALTH_CHECK_ENABLED = true;

    HealthCheck(Config cfg) {
      this(cfg.getBoolean(HEALTH_CHECK_SECTION, ENABLE_KEY, DEFAULT_HEALTH_CHECK_ENABLED));
    }
  }

  // Config parsing utilities

  private static FileBasedConfig loadConfig(SitePaths sitePaths, String configFileName) {
    FileBasedConfig cfg =
        new FileBasedConfig(sitePaths.etc_dir.resolve(configFileName).toFile(), FS.DETECTED);
    try {
      log.atInfo().log("Loading configuration from %s", cfg.getFile().getPath());
      cfg.load();
    } catch (IOException | ConfigInvalidException e) {
      log.atSevere().withCause(e).log(
          "Unable to load configuration from %s", cfg.getFile().getPath());
    }
    return cfg;
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

  private static boolean getBoolean(Config cfg, String section, String name, boolean defaultValue) {
    try {
      return cfg.getBoolean(section, name, defaultValue);
    } catch (IllegalArgumentException e) {
      log.atSevere().log("invalid value for %s; using default value %s", name, defaultValue);
      log.atFine().withCause(e).log("Failed to retrieve boolean value");
      return defaultValue;
    }
  }

  private static String nullToDefault(@Nullable String value, String defaultValue) {
    return value != null ? value : defaultValue;
  }
}

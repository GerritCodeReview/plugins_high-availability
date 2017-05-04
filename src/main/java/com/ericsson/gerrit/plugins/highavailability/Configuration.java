// Copyright (C) 2015 Ericsson
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

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Configuration {
  private String skipInterfacePattern = null;

  private static final Logger log = LoggerFactory.getLogger(Configuration.class);

  static final String SHARED_DIRECTORY = "sharedDirectory";
  static final String URL_KEY = "url";
  static final String USER_KEY = "user";
  static final String PASSWORD_KEY = "password";
  static final String CONNECTION_TIMEOUT_KEY = "connectionTimeout";
  static final String SOCKET_TIMEOUT_KEY = "socketTimeout";
  static final String MAX_TRIES_KEY = "maxTries";
  static final String RETRY_INTERVAL_KEY = "retryInterval";
  static final String INDEX_THREAD_POOL_SIZE_KEY = "indexThreadPoolSize";
  static final String CACHE_THREAD_POOL_SIZE_KEY = "cacheThreadPoolSize";
  static final String SKIP_INTERFACE_PATTERN = "skipInterfacePattern";
  static final String PEER_INFO_STRATEGY = "peerInfoStrategy";
  static final String CLUSTER_NAME = "clusterName";
  static final String PREFER_IPV4 = "preferIPv4";

  static final int DEFAULT_TIMEOUT_MS = 5000;
  static final int DEFAULT_MAX_TRIES = 5;
  static final int DEFAULT_RETRY_INTERVAL = 1000;
  static final int DEFAULT_THREAD_POOL_SIZE = 1;
  static final String DEFAULT_CLUSTER_NAME = "GerritPeerDiscovery";
  static final boolean DEFAULT_PREFER_IPV4 = false;
  static final String DEFAULT_SKIP_INTERFACE_PATTERN = "lo\\d|utun\\d|awdl\\d";
  static final PeerInfoStrategy DEFAULT_PEER_INFO_STRATEGY = PeerInfoStrategy.CONFIG;

  private final String url;
  private final String user;
  private final String password;
  private final int connectionTimeout;
  private final int socketTimeout;
  private final int maxTries;
  private final int retryInterval;
  private final int indexThreadPoolSize;
  private final int cacheThreadPoolSize;
  private boolean preferIPv4;
  private String clusterName;

  public enum PeerInfoStrategy { JGROUPS, CONFIG };
  private PeerInfoStrategy strategy;

  @Inject
  Configuration(PluginConfigFactory config, @PluginName String pluginName) {
    PluginConfig cfg = config.getFromGerritConfig(pluginName, true);
    url = Strings.nullToEmpty(cfg.getString(URL_KEY));
    user = Strings.nullToEmpty(cfg.getString(USER_KEY));
    password = Strings.nullToEmpty(cfg.getString(PASSWORD_KEY));
    connectionTimeout = getInt(cfg, CONNECTION_TIMEOUT_KEY, DEFAULT_TIMEOUT_MS);
    socketTimeout = getInt(cfg, SOCKET_TIMEOUT_KEY, DEFAULT_TIMEOUT_MS);
    maxTries = getInt(cfg, MAX_TRIES_KEY, DEFAULT_MAX_TRIES);
    retryInterval = getInt(cfg, RETRY_INTERVAL_KEY, DEFAULT_RETRY_INTERVAL);
    indexThreadPoolSize = getInt(cfg, INDEX_THREAD_POOL_SIZE_KEY, DEFAULT_THREAD_POOL_SIZE);
    cacheThreadPoolSize = getInt(cfg, CACHE_THREAD_POOL_SIZE_KEY, DEFAULT_THREAD_POOL_SIZE);
    preferIPv4 = cfg.getBoolean(PREFER_IPV4, DEFAULT_PREFER_IPV4);
    clusterName = cfg.getString(CLUSTER_NAME, DEFAULT_CLUSTER_NAME);
    strategy = cfg.getEnum(PEER_INFO_STRATEGY, DEFAULT_PEER_INFO_STRATEGY);
    skipInterfacePattern = cfg.getString(SKIP_INTERFACE_PATTERN, DEFAULT_SKIP_INTERFACE_PATTERN);
  }

  private int getInt(PluginConfig cfg, String name, int defaultValue) {
    try {
      return cfg.getInt(name, defaultValue);
    } catch (IllegalArgumentException e) {
      log.error(String.format("invalid value for %s; using default value %d", name, defaultValue));
      log.debug("Failed retrieve integer value: " + e.getMessage(), e);
      return defaultValue;
    }
  }

  public PeerInfoStrategy getPeerInfoStrategy() {
    return strategy;
  }

  public int getConnectionTimeout() {
    return connectionTimeout;
  }

  public int getMaxTries() {
    return maxTries;
  }

  public int getRetryInterval() {
    return retryInterval;
  }

  public int getSocketTimeout() {
    return socketTimeout;
  }

  public String getUrl() {
    return CharMatcher.is('/').trimTrailingFrom(url);
  }

  public String getUser() {
    return user;
  }

  public String getPassword() {
    return password;
  }

  public int getIndexThreadPoolSize() {
    return indexThreadPoolSize;
  }

  public int getCacheThreadPoolSize() {
    return cacheThreadPoolSize;
  }

  public String getClusterName() {
    return clusterName;
  }

  public boolean getPreferIPv4() {
    return preferIPv4;
  }

  public String getSkipInterfacePattern() {
    return skipInterfacePattern;
  }
}

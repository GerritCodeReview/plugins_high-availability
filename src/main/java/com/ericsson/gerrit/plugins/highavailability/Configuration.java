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

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.inject.Inject;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Configuration {
  private static final Logger log = LoggerFactory.getLogger(Configuration.class);

  //main section
  static final String MAIN_SECTION = "main";
  static final String SHARED_DIRECTORY_KEY = "sharedDirectory";

  //peerInfo section
  static final String PEER_INFO_SECTION = "peerInfo";
  static final String URL_KEY = "url";

  //http section
  static final String HTTP_SECTION = "http";
  static final String USER_KEY = "user";
  static final String PASSWORD_KEY = "password";
  static final String CONNECTION_TIMEOUT_KEY = "connectionTimeout";
  static final String SOCKET_TIMEOUT_KEY = "socketTimeout";
  static final String MAX_TRIES_KEY = "maxTries";
  static final String RETRY_INTERVAL_KEY = "retryInterval";

  //cache section
  static final String CACHE_SECTION = "cache";
  static final String CACHE_THREAD_POOL_SIZE_KEY = "cacheThreadPoolSize";

  //index section
  static final String INDEX_SECTION = "index";
  static final String INDEX_THREAD_POOL_SIZE_KEY = "indexThreadPoolSize";

  //websession section
  static final String WEBSESSION_SECTION = "websession";
  static final String CLEANUP_INTERVAL_KEY = "cleanupInterval";

  static final int DEFAULT_TIMEOUT_MS = 5000;
  static final int DEFAULT_MAX_TRIES = 5;
  static final int DEFAULT_RETRY_INTERVAL = 1000;
  static final int DEFAULT_THREAD_POOL_SIZE = 1;
  static final long DEFAULT_CLEANUP_INTERVAL_MS = HOURS.toMillis(24);

  private final String url;
  private final String user;
  private final String password;
  private final int connectionTimeout;
  private final int socketTimeout;
  private final int maxTries;
  private final int retryInterval;
  private final int indexThreadPoolSize;
  private final int cacheThreadPoolSize;
  private final String sharedDirectory;
  private final long cleanupInterval;

  @Inject
  Configuration(PluginConfigFactory pluginConfigFactory, @PluginName String pluginName) {
    Config cfg = pluginConfigFactory.getGlobalPluginConfig(pluginName);
    sharedDirectory = Strings.emptyToNull(cfg.getString(MAIN_SECTION, null, SHARED_DIRECTORY_KEY));
    if (sharedDirectory == null) {
      throw new ProvisionException(SHARED_DIRECTORY_KEY + " must be configured");
    }
    url = Strings.nullToEmpty(cfg.getString(PEER_INFO_SECTION, null, URL_KEY));
    user = Strings.nullToEmpty(cfg.getString(HTTP_SECTION, null, USER_KEY));
    password = Strings.nullToEmpty(cfg.getString(HTTP_SECTION, null, PASSWORD_KEY));
    connectionTimeout = getInt(cfg, HTTP_SECTION, CONNECTION_TIMEOUT_KEY, DEFAULT_TIMEOUT_MS);
    socketTimeout = getInt(cfg, HTTP_SECTION, SOCKET_TIMEOUT_KEY, DEFAULT_TIMEOUT_MS);
    maxTries = getInt(cfg, HTTP_SECTION, MAX_TRIES_KEY, DEFAULT_MAX_TRIES);
    retryInterval = getInt(cfg, HTTP_SECTION, RETRY_INTERVAL_KEY, DEFAULT_RETRY_INTERVAL);
    cacheThreadPoolSize =
        getInt(cfg, CACHE_SECTION, CACHE_THREAD_POOL_SIZE_KEY, DEFAULT_THREAD_POOL_SIZE);
    indexThreadPoolSize =
        getInt(cfg, INDEX_SECTION, INDEX_THREAD_POOL_SIZE_KEY, DEFAULT_THREAD_POOL_SIZE);
    cleanupInterval =
        ConfigUtil.getTimeUnit(
            Strings.nullToEmpty(cfg.getString(WEBSESSION_SECTION, null, CLEANUP_INTERVAL_KEY)),
            DEFAULT_CLEANUP_INTERVAL_MS,
            MILLISECONDS);
  }

  private int getInt(Config cfg, String section, String name, int defaultValue) {
    try {
      return cfg.getInt(section, name, defaultValue);
    } catch (IllegalArgumentException e) {
      log.error(String.format("invalid value for %s; using default value %d", name, defaultValue));
      log.debug("Failed retrieve integer value: " + e.getMessage(), e);
      return defaultValue;
    }
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

  public String getSharedDirectory() {
    return sharedDirectory;
  }

  public Long getCleanupInterval() {
    return cleanupInterval;
  }
}

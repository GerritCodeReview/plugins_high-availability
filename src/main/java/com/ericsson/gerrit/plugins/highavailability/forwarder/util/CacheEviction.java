// Copyright (C) 2017 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.forwarder.util;

import com.ericsson.gerrit.plugins.highavailability.cache.Constants;
import com.google.common.cache.Cache;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class CacheEviction {
  private static final Logger log = LoggerFactory.getLogger(CacheEviction.class);
  private static final String GERRIT = "gerrit";

  private final DynamicMap<Cache<?, ?>> cacheMap;

  @Inject
  public CacheEviction(DynamicMap<Cache<?, ?>> cacheMap) {
    this.cacheMap = cacheMap;
  }

  public void evict(String cacheName, Object key) {
    Cache<?, ?> cache = cacheMap.get(GERRIT, cacheName);
    evictCache(cache, cacheName, key);
  }

  private void evictCache(Cache<?, ?> cache, String cacheName, Object key) {
    if (Constants.PROJECT_LIST.equals(cacheName)) {
      // One key is holding the list of projects
      cache.invalidateAll();
    } else {
      cache.invalidate(key);
    }
    log.debug("Invalidated " + cacheName);
  }
}

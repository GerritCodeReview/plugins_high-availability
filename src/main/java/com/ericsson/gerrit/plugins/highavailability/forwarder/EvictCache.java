// Copyright (C) 2018 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.forwarder;

import com.ericsson.gerrit.plugins.highavailability.cache.Constants;
import com.google.common.cache.Cache;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class EvictCache {
  private static final Logger logger = LoggerFactory.getLogger(EvictCache.class);

  private final DynamicMap<Cache<?, ?>> cacheMap;

  @Inject
  public EvictCache(DynamicMap<Cache<?, ?>> cacheMap) {
    this.cacheMap = cacheMap;
  }

  public void evict(String pluginName, String cacheName, Object key) throws CacheNotFoundException {
    Cache<?, ?> cache = cacheMap.get(pluginName, cacheName);
    if (cache == null) {
      throw new CacheNotFoundException(pluginName, cacheName);
    }
    try {
      Context.setForwardedEvent(true);
      evictCache(cache, cacheName, key);
    } finally {
      Context.unsetForwardedEvent();
    }
  }

  private void evictCache(Cache<?, ?> cache, String cacheName, Object key) {
    if (Constants.PROJECT_LIST.equals(cacheName)) {
      // One key is holding the list of projects
      cache.invalidateAll();
      logger.debug("Invalidated cache {}", cacheName);
    } else {
      cache.invalidate(key);
      logger.debug("Invalidated cache {}[{}]", cacheName, key);
    }
  }
}

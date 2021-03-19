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
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Evict cache entries. This class is meant to be used on the receiving side of the {@link
 * Forwarder} since it will prevent cache evictions to be forwarded again causing an infinite
 * forwarding loop between the 2 nodes.
 */
@Singleton
public class ForwardedCacheEvictionHandler {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();

  private final DynamicMap<Cache<?, ?>> cacheMap;
  private final ProjectCache projectCache;

  @Inject
  public ForwardedCacheEvictionHandler(
      DynamicMap<Cache<?, ?>> cacheMap, ProjectCache projectCache) {
    this.cacheMap = cacheMap;
    this.projectCache = projectCache;
  }

  /**
   * Evict an entry from the cache of the local node, eviction will not be forwarded to the other
   * node.
   *
   * @param entry the cache entry to evict
   * @throws CacheNotFoundException if cache does not exist
   */
  public void evict(CacheEntry entry) throws CacheNotFoundException {
    // special case(s), evicted using Gerrit core API
    if (Constants.PROJECTS.equals(entry.getCacheName())) {
      projectCache.evict((Project.NameKey) entry.getKey());
      log.atFine().log("Invalidated via ProjectCache.evict(%s)", entry.getKey());
      return;
    }

    // generic cases
    Cache<?, ?> cache = cacheMap.get(entry.getPluginName(), entry.getCacheName());
    if (cache == null) {
      throw new CacheNotFoundException(entry.getPluginName(), entry.getCacheName());
    }
    try {
      Context.setForwardedEvent(true);
      if (Constants.PROJECT_LIST.equals(entry.getCacheName())) {
        // One key is holding the list of projects
        cache.invalidateAll();
        log.atFine().log("Invalidated cache %s", entry.getCacheName());
      } else {
        cache.invalidate(entry.getKey());
        log.atFine().log("Invalidated cache %s[%s]", entry.getCacheName(), entry.getKey());
      }
    } finally {
      Context.unsetForwardedEvent();
    }
  }
}

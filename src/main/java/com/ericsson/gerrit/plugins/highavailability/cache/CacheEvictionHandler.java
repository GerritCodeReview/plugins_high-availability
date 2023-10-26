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

package com.ericsson.gerrit.plugins.highavailability.cache;

import com.ericsson.gerrit.plugins.highavailability.forwarder.Context;
import com.ericsson.gerrit.plugins.highavailability.forwarder.Forwarder;
import com.google.common.cache.RemovalNotification;
import com.google.gerrit.server.cache.CacheRemovalListener;
import com.google.inject.Inject;

class CacheEvictionHandler<K, V> implements CacheRemovalListener<K, V> {
  private final Forwarder forwarder;
  private final CachePatternMatcher matcher;

  @Inject
  CacheEvictionHandler(Forwarder forwarder, CachePatternMatcher matcher) {
    this.forwarder = forwarder;
    this.matcher = matcher;
  }

  @Override
  public void onRemoval(String plugin, String cache, RemovalNotification<K, V> notification) {
    if (!Context.isForwardedEvent() && !notification.wasEvicted() && matcher.matches(cache)) {
      forwarder.evict(cache, notification.getKey());
    }
  }
}

// Copyright (C) 2025 The Android Open Source Project
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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class ForwarderMetricsRegistry {

  private final ForwarderMetrics.Factory metricsFactory;

  private Map<EventType, ForwarderMetrics> metrics = new HashMap<>();

  @Inject
  public ForwarderMetricsRegistry(ForwarderMetrics.Factory metricsFactory) {
    this.metricsFactory = metricsFactory;
  }

  public ForwarderMetrics get(EventType eventType) {
    return metrics.get(eventType);
  }

  public void put(EventType task) {
    metrics.put(task, metricsFactory.create(task));
  }

  public void putAll(Collection<EventType> eventTypes) {
    for (EventType eventType : eventTypes) {
      put(eventType);
    }
  }
}

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

import com.google.gerrit.metrics.Counter0;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.util.Locale;

public class ForwarderMetrics {
  private final Counter0 failureCounterMetric;
  private final Counter0 successCounterMetric;

  public interface Factory {
    ForwarderMetrics create(EventType eventType);
  }

  @AssistedInject
  public ForwarderMetrics(MetricMaker metricMaker, @Assisted EventType eventType) {
    String event = eventType.toString().toLowerCase(Locale.US);
    this.failureCounterMetric =
        metricMaker.newCounter(
            String.format("forwarding_%s_event/failure", event),
            new Description(String.format("%s events forwarding failures count", event))
                .setCumulative()
                .setRate()
                .setUnit("failures"));
    this.successCounterMetric =
        metricMaker.newCounter(
            String.format("forwarding_%s_event/success", event),
            new Description(String.format("%s events forwarding success count", event))
                .setCumulative()
                .setRate()
                .setUnit("successes"));
  }

  public void recordResult(boolean isSuccessful) {
    if (isSuccessful) {
      successCounterMetric.increment();
    } else {
      failureCounterMetric.increment();
    }
  }
}

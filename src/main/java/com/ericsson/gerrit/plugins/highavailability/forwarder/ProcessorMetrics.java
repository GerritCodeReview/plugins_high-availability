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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.metrics.Counter0;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer0;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

public class ProcessorMetrics {
  private final Timer0 processingTimeMetric;
  private final Timer0 totalTimeMetric;
  private final Counter0 failureCounterMetric;
  private final Counter0 successCounterMetric;

  public interface Factory {
    ProcessorMetrics create(EventType eventType);
  }

  @AssistedInject
  public ProcessorMetrics(MetricMaker metricMaker, @Assisted EventType eventType) {
    this.processingTimeMetric =
        metricMaker.newTimer(
            String.format("forwarded_%s_event_handler/time_processing", eventType),
            new Description(
                    String.format(
                        "Time from receiving an %s event to finish processing it.", eventType))
                .setCumulative()
                .setUnit(Description.Units.MILLISECONDS));
    this.totalTimeMetric =
        metricMaker.newTimer(
            String.format("forwarded_%s_event_handler/time_total", eventType),
            new Description(
                    String.format(
                        "Time from %s event scheduling to finish processing it.", eventType))
                .setCumulative()
                .setUnit(Description.Units.MILLISECONDS));
    this.failureCounterMetric =
        metricMaker.newCounter(
            String.format("forwarded_%s_event_handler/failure", eventType),
            new Description(String.format("%s events forwarding failures count", eventType))
                .setCumulative()
                .setRate());
    this.successCounterMetric =
        metricMaker.newCounter(
            String.format("forwarded_%s_event_handler/success", eventType),
            new Description(String.format("%s events forwarding success count", eventType))
                .setCumulative()
                .setRate());
  }

  public void recordResult(boolean isSuccessful) {
    if (isSuccessful) {
      successCounterMetric.increment();
    } else {
      failureCounterMetric.increment();
    }
  }

  public void recordProcessingTime(Long processingTime) {
    processingTimeMetric.record(processingTime, TimeUnit.MILLISECONDS);
  }

  public void recordTotalTime(Long totalTime) {
    totalTimeMetric.record(totalTime, TimeUnit.MILLISECONDS);
  }

  public void record(@Nullable Instant eventCreatedOn, Instant startTime, boolean success) {
    Instant now = Instant.now();
    recordResult(success);
    recordProcessingTime(Duration.between(startTime, now).toMillis());
    if (eventCreatedOn != null) {
      recordTotalTime(Duration.between(eventCreatedOn, now).toMillis());
    }
  }
}

// Copyright (C) 2024 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.forwarder.retry;

import com.ericsson.gerrit.plugins.highavailability.forwarder.IndexEvent;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class IndexingRetry<T> {

  private final T id;
  private final Optional<IndexEvent> event;
  private AtomicInteger retryNumber = new AtomicInteger(0);

  public IndexingRetry(T id, Optional<IndexEvent> event) {
    this.id = id;
    this.event = event;
  }

  public Optional<IndexEvent> getEvent() {
    return event;
  }

  public T getId() {
    return id;
  }

  public int incrementAndGetRetryNumber() {
    return retryNumber.incrementAndGet();
  }
}

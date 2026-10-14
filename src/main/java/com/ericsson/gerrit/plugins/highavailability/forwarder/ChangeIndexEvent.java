// Copyright (C) 2026 The Android Open Source Project
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/** Details of a change index event. {@code metaSha} is mandatory. */
public class ChangeIndexEvent {
  public Instant eventCreatedOn;
  @Nullable public String targetSha;
  public String metaSha;

  public ChangeIndexEvent(Instant eventCreatedOn, @Nullable String targetSha, String metaSha) {
    this.eventCreatedOn = Objects.requireNonNull(eventCreatedOn, "eventCreatedOn");
    this.targetSha = targetSha;
    this.metaSha = Objects.requireNonNull(metaSha, "metaSha");
  }

  @Override
  public String toString() {
    return "ChangeIndexEvent@"
        + format(eventCreatedOn)
        + ((targetSha != null) ? "/target:" + targetSha : "")
        + "/meta:"
        + metaSha;
  }

  public static String format(Instant eventTs) {
    return LocalDateTime.ofInstant(eventTs, ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME);
  }
}

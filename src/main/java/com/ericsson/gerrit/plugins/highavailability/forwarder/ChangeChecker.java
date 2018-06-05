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

package com.ericsson.gerrit.plugins.highavailability.forwarder;

import com.ericsson.gerrit.plugins.highavailability.index.ChangeIndexedEvent;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import java.util.Optional;

/** Encapsulates the logic of verifying the up-to-date status of a change. */
public interface ChangeChecker {

  /**
   * Checks if the local change is aligned with the indexEvent received.
   *
   * @param indexEvent indexing event
   * @param changeTsGraceInterval tolerance of the time-stamp check
   * @return true if the local change is up-to-date, false otherwise.
   * @throws IOException if an I/O error occurred while reading the local change
   * @throws OrmException if the local ReviewDb cannot be opened
   */
  public boolean isChangeUpToDate(
      Optional<ChangeIndexedEvent> indexEvent, int changeTsGraceInterval)
      throws IOException, OrmException;

  /**
   * Returns the last computed up-to-date change time-stamp.
   *
   * <p>Compute the up-to-date change time-stamp when it is invoked for the very first time.
   *
   * @return the change timestamp epoch in seconds
   * @throws IOException if an I/O error occurred while reading the local change
   * @throws OrmException if the local ReviewDb cannot be opened
   */
  public long getComputedChangeTs() throws IOException, OrmException;
}

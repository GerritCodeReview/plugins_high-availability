// Copyright (C) 2022 The Android Open Source Project
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

import com.google.gerrit.server.events.EventListener;

/** Allow to trigger an event listener unconditionally. */
public interface AllowedForwardedEventListener {

  /**
   * Control whether an event listener should be allowed unconditionally.
   *
   * @param listener the event listener
   * @return true if the listener should be allowed, false otherwise
   */
  boolean isAllowed(EventListener listener);
}

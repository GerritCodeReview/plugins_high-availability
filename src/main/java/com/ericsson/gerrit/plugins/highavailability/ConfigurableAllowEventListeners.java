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

package com.ericsson.gerrit.plugins.highavailability;

import com.ericsson.gerrit.plugins.highavailability.forwarder.AllowEventListener;
import com.google.gerrit.server.events.EventListener;
import com.google.inject.Inject;
import java.util.Set;

/** Configure the allowed listeners in high-availability.config */
public class ConfigurableAllowEventListeners implements AllowEventListener {
  private final Set<String> allowedListenerClasses;

  @Inject
  ConfigurableAllowEventListeners(Configuration config) {
    allowedListenerClasses = config.event().allowedListeners();
  }

  @Override
  public boolean isAllowed(EventListener listener) {
    return allowedListenerClasses.contains(listener.getClass().getName());
  }
}

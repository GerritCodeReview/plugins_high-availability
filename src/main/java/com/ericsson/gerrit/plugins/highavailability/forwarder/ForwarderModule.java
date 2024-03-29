// Copyright (C) 2017 The Android Open Source Project
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

import com.ericsson.gerrit.plugins.highavailability.ConfigurableAllowedEventListeners;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.server.events.EventDispatcher;
import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

public class ForwarderModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(AllowedForwardedEventListener.class)
        .to(ConfigurableAllowedEventListeners.class)
        .in(Scopes.SINGLETON);
    DynamicItem.bind(binder(), EventDispatcher.class).to(ForwardedAwareEventBroker.class);
  }
}

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

package com.ericsson.gerrit.plugins.highavailability.forwarder.jgroups;

import org.jgroups.blocks.MessageDispatcher;
import org.jgroups.blocks.RequestHandler;

import com.ericsson.gerrit.plugins.highavailability.forwarder.Forwarder;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.inject.Scopes;

public class JGroupsForwarderModule extends LifecycleModule {

  @Override
  protected void configure() {
    bind(Forwarder.class).to(JGroupsForwarder.class);
    bind(MessageDispatcher.class).toProvider(MessageDispatcherProvider.class).in(Scopes.SINGLETON);
    bind(RequestHandler.class).to(MessageProcessor.class);
    listener().to(OnStartStop.class);
  }
}

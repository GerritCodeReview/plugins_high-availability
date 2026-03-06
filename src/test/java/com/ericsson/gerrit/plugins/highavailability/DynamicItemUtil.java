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

package com.ericsson.gerrit.plugins.highavailability;

import com.ericsson.gerrit.plugins.highavailability.forwarder.Forwarder;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;

public class DynamicItemUtil {
  public static DynamicItem<Forwarder> asDynamicItem(Forwarder forwarder) {
    AbstractModule m =
        new AbstractModule() {
          @Override
          protected void configure() {
            DynamicItem.itemOf(binder(), Forwarder.class);
            DynamicItem.bind(binder(), Forwarder.class).toInstance(forwarder);
          }
        };
    return Guice.createInjector(m)
        .getInstance(Key.get(new TypeLiteral<DynamicItem<Forwarder>>() {}));
  }
}

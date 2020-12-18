// Copyright (C) 2020 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.event;

import static com.google.common.truth.Truth.assertThat;

import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.GsonProvider;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.data.AccountAttribute;
import com.google.gerrit.server.data.RefUpdateAttribute;
import com.google.gerrit.server.events.RefUpdatedEvent;
import com.google.gson.Gson;
import org.junit.Test;

public class EventDeserializerTest {
  private static final String LEGACY_PROJECT_KEY = "{\"name\": \"project\"}";

  private static final String NEW_PROJECT_KEY = "\"project\"";

  private final Gson gson = new GsonProvider().get();

  @Test
  public void deserializePatchSetCreatedEventLegacyProjectKey() {
    Project.NameKey n = gson.fromJson(LEGACY_PROJECT_KEY, Project.NameKey.class);
    assertThat(n.get()).isEqualTo("project");
  }

  @Test
  public void deserializePatchSetCreatedEventNewProjectKey() {
    Project.NameKey n = gson.fromJson(NEW_PROJECT_KEY, Project.NameKey.class);
    assertThat(n.get()).isEqualTo("project");
  }

  @Test
  public void refUpdatedEventRoundTrip() {
    RefUpdatedEvent refUpdatedEvent = new RefUpdatedEvent();

    RefUpdateAttribute refUpdatedAttribute = new RefUpdateAttribute();
    refUpdatedAttribute.refName = "refs/heads/master";
    refUpdatedEvent.refUpdate = createSupplier(refUpdatedAttribute);

    AccountAttribute accountAttribute = new AccountAttribute();
    accountAttribute.email = "some.user@domain.com";
    refUpdatedEvent.submitter = createSupplier(accountAttribute);

    String serializedEvent = gson.toJson(refUpdatedEvent);
    RefUpdatedEvent e = gson.fromJson(serializedEvent, RefUpdatedEvent.class);

    assertThat(e).isNotNull();
    assertThat(e.refUpdate).isInstanceOf(Supplier.class);
    assertThat(e.refUpdate.get().refName).isEqualTo(refUpdatedAttribute.refName);
    assertThat(e.submitter).isInstanceOf(Supplier.class);
    assertThat(e.submitter.get().email).isEqualTo(accountAttribute.email);
  }

  private static <T> Supplier<T> createSupplier(T value) {
    return Suppliers.memoize(() -> value);
  }
}

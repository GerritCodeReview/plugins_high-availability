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

package com.ericsson.gerrit.plugins.highavailability.replication.events;

import static com.google.common.truth.Truth.assertThat;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DeserializerTest {
  private Deserializer deserializer;

  @Before
  public void setUp() {
    deserializer = new Deserializer();
  }

  @Test
  public void getClassFromJsonWithValidClass() throws Exception {
    String jsonString =
        "{\"class\":\"com.ericsson.gerrit.plugins.highavailability.replication.events.ForwardedGitReferenceUpdatedEvent\"}";
    Class<?> cls = deserializer.getClassFromJson(new JsonParser().parse(jsonString));
    assertThat(cls.getCanonicalName())
        .isEqualTo(
            "com.ericsson.gerrit.plugins.highavailability.replication.events.ForwardedGitReferenceUpdatedEvent");
  }

  @Test(expected = JsonParseException.class)
  public void getClassFromJsonWithoutClass() throws Exception {
    String jsonString =
        "{\"type\":\"com.ericsson.gerrit.plugins.highavailability.replication.events.ForwardedGitReferenceUpdatedEvent\"}";
    Class<?> cls = deserializer.getClassFromJson(new JsonParser().parse(jsonString));
    assertThat(cls).isEqualTo(null);
  }

  @Test
  public void getClassFromStringWithValidClass() throws Exception {
    String clsString =
        "com.ericsson.gerrit.plugins.highavailability.replication.events.ForwardedGitReferenceUpdatedEvent";
    Class<?> cls = deserializer.getClassFromString(clsString);
    assertThat(cls.getCanonicalName()).isEqualTo(clsString);
  }

  @Test(expected = JsonParseException.class)
  public void getClassFromStringWithInvalidClass() throws Exception {
    String clsString = "com.ericsson.gerrit.plugins.highavailability.replication.events.Invalid";
    Class<?> cls = deserializer.getClassFromString(clsString);
    assertThat(cls).isEqualTo(null);
  }
}

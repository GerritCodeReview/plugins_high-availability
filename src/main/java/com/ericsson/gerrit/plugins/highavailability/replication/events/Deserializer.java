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

import com.google.gerrit.extensions.events.ProjectEvent;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.inject.Singleton;
import java.lang.reflect.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Deserializer implements JsonDeserializer<ProjectEvent> {
  private static final Logger log = LoggerFactory.getLogger(Deserializer.class);

  @Override
  public ProjectEvent deserialize(
      JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    Class<?> cls = getClassFromJson(json);

    return context.deserialize(json, cls);
  }

  public Class<?> getClassFromJson(JsonElement json) throws JsonParseException {
    if (!json.isJsonObject()) {
      throw new JsonParseException("Not an object");
    }

    JsonElement classJson = json.getAsJsonObject().get("class");
    if (classJson == null
        || !classJson.isJsonPrimitive()
        || !classJson.getAsJsonPrimitive().isString()) {
      throw new JsonParseException("'class' attribute is not a string: " + classJson);
    }

    String classStr = classJson.getAsJsonPrimitive().getAsString();
    json.getAsJsonObject().remove("class");

    return getClassFromString(classStr);
  }

  public Class<?> getClassFromString(String classStr) throws JsonParseException {
    Class<?> cls = null;

    try {
      cls = Class.forName(classStr);
    } catch (ClassNotFoundException e) {
      throw new JsonParseException("Unknown replication event class: " + classStr);
    }

    return cls;
  }
}

// Copyright (C) 2023 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.forwarder.commands;

import com.ericsson.gerrit.plugins.highavailability.forwarder.EventType;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommandDeserializer implements JsonDeserializer<Command> {

  private static final List<Class<? extends Command>> CMD_CLASSES =
      List.of(
          IndexChange.Update.class,
          IndexChange.BatchUpdate.class,
          IndexChange.Delete.class,
          IndexAccount.class,
          IndexGroup.class,
          IndexProject.class,
          EvictCache.class,
          PostEvent.class,
          AddToProjectList.class,
          RemoveFromProjectList.class);
  private static final Map<EventType, Class<?>> COMMAND_TYPE_TO_CLASS_MAPPING = new HashMap<>();

  static {
    for (Class<?> clazz : CMD_CLASSES) {
      try {
        Field type = clazz.getDeclaredField("TYPE");
        COMMAND_TYPE_TO_CLASS_MAPPING.put((EventType) type.get(null), clazz);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public Command deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    if (!json.isJsonObject()) {
      throw new JsonParseException("Not an object");
    }
    JsonElement typeJson = json.getAsJsonObject().get("type");
    if (typeJson == null
        || !typeJson.isJsonPrimitive()
        || !typeJson.getAsJsonPrimitive().isString()) {
      throw new JsonParseException("Type is not a string: " + typeJson);
    }
    String type = typeJson.getAsJsonPrimitive().getAsString();
    Class<?> commandClass = COMMAND_TYPE_TO_CLASS_MAPPING.get(EventType.valueOf(type));
    if (commandClass == null) {
      throw new JsonParseException("Unknown command type: " + type);
    }
    return context.deserialize(json, commandClass);
  }
}

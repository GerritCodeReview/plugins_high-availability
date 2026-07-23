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

package com.ericsson.gerrit.plugins.highavailability.forwarder.rest;

import com.ericsson.gerrit.plugins.highavailability.cache.Constants;
import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.server.cache.CacheDef;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class CacheKeyJsonParser {
  private final Gson gson;
  private final DynamicMap<CacheDef<?, ?>> cachesMap;

  @Inject
  public CacheKeyJsonParser(@RestGson Gson gson, DynamicMap<CacheDef<?, ?>> cachesMap) {
    this.gson = gson;
    this.cachesMap = cachesMap;
  }

  public Object fromJson(String cacheName, String jsonString) {
    JsonElement json = gson.fromJson(Strings.nullToEmpty(jsonString), JsonElement.class);
    Supplier<JsonElement> id = Suppliers.memoize(() -> json.getAsJsonObject().get("id"));
    Supplier<JsonElement> uuid = Suppliers.memoize(() -> json.getAsJsonObject().get("uuid"));

    // Need to add a case for 'adv_bases'
    switch (cacheName) {
      case Constants.PROJECT_LIST:
        return gson.fromJson(json, Object.class);
      case Constants.PROJECTS:
        return Project.nameKey(CharMatcher.is('\"').trimFrom(json.getAsString()));
      default:
        try {
          return gson.fromJson(json, getCacheKeyType(cacheName));
        } catch (Exception e) {
          return gson.fromJson(json, Object.class);
        }
    }
  }

  private Class<?> getCacheKeyType(String cacheName) {
    int dot = cacheName.indexOf('.');
    String pluginName = Constants.GERRIT;
    String pluginCacheName = cacheName;
    if (dot > 0) {
      pluginName = cacheName.substring(0, dot);
      pluginCacheName = cacheName.substring(dot + 1);
    }

    CacheDef<?, ?> cacheDef = cachesMap.get(pluginName, pluginCacheName);
    if (cacheDef == null) {
      throw new IllegalStateException("Unable to find definition for cache '" + cacheName + "'");
    }

    return cacheDef.keyType().getRawType();
  }
}

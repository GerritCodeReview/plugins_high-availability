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
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventDeserializer;
import com.google.gerrit.server.events.SupplierDeserializer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Singleton;

@Singleton
final class GsonParser {
  private final Gson gson =
      new GsonBuilder()
          .registerTypeAdapter(Event.class, new EventDeserializer())
          .registerTypeAdapter(Supplier.class, new SupplierDeserializer())
          .create();

  public Gson gson() {
    return gson;
  }

  Object fromJson(String cacheName, String json) {
    Object key;
    // Need to add a case for 'adv_bases'
    switch (cacheName) {
      case Constants.ACCOUNTS:
        key = gson.fromJson(Strings.nullToEmpty(json).trim(), Account.Id.class);
        break;
      case Constants.GROUPS:
        key = gson.fromJson(Strings.nullToEmpty(json).trim(), AccountGroup.Id.class);
        break;
      case Constants.GROUPS_BYINCLUDE:
      case Constants.GROUPS_MEMBERS:
        key = gson.fromJson(Strings.nullToEmpty(json).trim(), AccountGroup.UUID.class);
        break;
      case Constants.PROJECT_LIST:
        key = gson.fromJson(Strings.nullToEmpty(json), Object.class);
        break;
      default:
        try {
          key = gson.fromJson(Strings.nullToEmpty(json).trim(), String.class);
        } catch (Exception e) {
          key = gson.fromJson(Strings.nullToEmpty(json), Object.class);
        }
    }
    return key;
  }

  String toJson(String cacheName, Object key) {
    String json;
    // Need to add a case for 'adv_bases'
    switch (cacheName) {
      case Constants.ACCOUNTS:
        json = gson.toJson(key, Account.Id.class);
        break;
      case Constants.GROUPS:
        json = gson.toJson(key, AccountGroup.Id.class);
        break;
      case Constants.GROUPS_BYINCLUDE:
      case Constants.GROUPS_MEMBERS:
        json = gson.toJson(key, AccountGroup.UUID.class);
        break;
      case Constants.PROJECT_LIST:
      default:
        json = gson.toJson(key);
    }
    return json;
  }
}

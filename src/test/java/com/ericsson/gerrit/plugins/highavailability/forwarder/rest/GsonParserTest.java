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

import static com.google.common.truth.Truth.assertThat;

import com.ericsson.gerrit.plugins.highavailability.cache.Constants;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.server.account.CachedAccountDetails;
import com.google.gerrit.server.events.EventGsonProvider;
import com.google.gson.Gson;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class GsonParserTest {
  private static final Object EMPTY_JSON = "{}";
  private final Gson gson = new EventGsonProvider().get();
  private final GsonParser objectUnderTest = new GsonParser(gson);

  @Test
  public void accountIDParse() {
    Account.Id accountId = Account.id(1);
    String json = gson.toJson(accountId);
    assertThat(accountId).isEqualTo(objectUnderTest.fromJson(Constants.ACCOUNTS, json));
  }

  @Test
  public void cachedAccountDetailsKeyRoundTrip() {
    objectUnderTest.fromJson(
        Constants.ACCOUNTS,
        gson.toJson(CachedAccountDetails.Key.create(Account.id(42), ObjectId.zeroId())));
  }

  @Test
  public void accountGroupIDParse() {
    AccountGroup.Id accountGroupId = AccountGroup.id(1);
    String json = gson.toJson(accountGroupId);
    assertThat(accountGroupId).isEqualTo(objectUnderTest.fromJson(Constants.GROUPS, json));
  }

  @Test
  public void accountGroupUUIDParse() {
    AccountGroup.UUID accountGroupUuid = AccountGroup.uuid("abc123");
    String json = gson.toJson(accountGroupUuid);
    assertThat(accountGroupUuid)
        .isEqualTo(objectUnderTest.fromJson(Constants.GROUPS_BYINCLUDE, json));
  }

  @Test
  public void stringParse() {
    String key = "key";
    String json = gson.toJson(key);
    assertThat(key).isEqualTo(objectUnderTest.fromJson(Constants.PROJECTS, json));
  }

  @Test
  public void noKeyParse() {
    Object object = new Object();
    String json = gson.toJson(object);
    assertThat(json).isEqualTo(EMPTY_JSON);
  }
}

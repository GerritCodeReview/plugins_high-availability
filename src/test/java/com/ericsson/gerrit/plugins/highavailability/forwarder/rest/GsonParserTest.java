// Copyright (C) 2015 Ericsson
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

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;

import com.ericsson.gerrit.plugins.highavailability.cache.Contants;

import org.junit.Test;

public class GsonParserTest {
  private static final Object EMPTY_JSON = "{}";

  @Test
  public void AccountIDParse() {
    Account.Id accountId = new Account.Id(1);
    String json = GsonParser.toJson(Contants.ACCOUNTS, accountId);
    assertThat(accountId)
        .isEqualTo(GsonParser.fromJson(Contants.ACCOUNTS, json));
  }

  @Test
  public void AccountGroupIDParse() {
    AccountGroup.Id accountGroupId = new AccountGroup.Id(1);
    String json = GsonParser.toJson(Contants.GROUPS, accountGroupId);
    assertThat(accountGroupId)
        .isEqualTo(GsonParser.fromJson(Contants.GROUPS, json));
  }

  @Test
  public void AccountGroupUUIDParse() {
    AccountGroup.UUID accountGroupUuid = new AccountGroup.UUID("abc123");
    String json =
        GsonParser.toJson(Contants.GROUPS_BYINCLUDE, accountGroupUuid);
    assertThat(accountGroupUuid)
        .isEqualTo(GsonParser.fromJson(Contants.GROUPS_BYINCLUDE, json));
  }

  @Test
  public void StringParse() {
    String key = "key";
    String json = GsonParser.toJson(Contants.PROJECTS, key);
    assertThat(key)
        .isEqualTo(GsonParser.fromJson(Contants.PROJECTS, json));
  }

  @Test
  public void NoKeyParse() {
    Object object = new Object();
    String json = GsonParser.toJson(Contants.PROJECT_LIST, object);
    assertThat(json).isEqualTo(EMPTY_JSON);
  }
}

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
import com.google.common.cache.CacheLoader;
import com.google.common.cache.Weigher;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.PrivateInternals_DynamicMapImpl;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.server.cache.CacheDef;
import com.google.gerrit.server.events.EventGsonProvider;
import com.google.gson.Gson;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Providers;
import java.time.Duration;
import org.junit.Before;
import org.junit.Test;

public class CacheKeyJsonParserTest {
  private static final Object EMPTY_JSON = "{}";
  private final Gson gson = RestForwarderModule.buildRestGson(new EventGsonProvider().get());
  private CacheKeyJsonParser objectUnderTest;

  private PrivateInternals_DynamicMapImpl<CacheDef<?, ?>> cacheDefMap;

  @Before
  public void setUp() throws Exception {
    cacheDefMap =
        (PrivateInternals_DynamicMapImpl<CacheDef<?, ?>>) DynamicMap.<CacheDef<?, ?>>emptyMap();

    defineCache(Constants.GROUPS_BYMEMBER, Account.Id.class);
    defineCache(Constants.ACCOUNTS, Account.Id.class);
    defineCache(Constants.TOKENS, Account.Id.class);
    defineCache(Constants.GROUPS, AccountGroup.Id.class);
    defineCache(Constants.GROUPS_BYINCLUDE, AccountGroup.UUID.class);
    defineCache(Constants.GROUPS_MEMBERS, AccountGroup.UUID.class);

    objectUnderTest = new CacheKeyJsonParser(gson, cacheDefMap);
  }

  private void defineCache(String cacheName, Class<?> keyClass) {
    RegistrationHandle unused =
        cacheDefMap.put(
            Constants.GERRIT, cacheName, Providers.of(new TestCacheDef<>(cacheName, keyClass)));
  }

  static class TestCacheDef<K> implements CacheDef<K, Object> {
    private final Class<K> keyClass;
    private final String name;

    TestCacheDef(String name, Class<K> keyClass) {
      this.name = name;
      this.keyClass = keyClass;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public String configKey() {
      return "";
    }

    @Override
    public TypeLiteral<K> keyType() {
      return TypeLiteral.get(keyClass);
    }

    @Override
    public TypeLiteral<Object> valueType() {
      return null;
    }

    @Override
    public long maximumWeight() {
      return 0;
    }

    @Override
    public Duration expireAfterWrite() {
      return null;
    }

    @Override
    public Duration expireFromMemoryAfterAccess() {
      return null;
    }

    @Override
    public Duration refreshAfterWrite() {
      return null;
    }

    @Override
    public Weigher<K, Object> weigher() {
      return null;
    }

    @Override
    public CacheLoader<K, Object> loader() {
      return null;
    }
  }

  @Test
  public void accountIDParse() {
    Account.Id accountId = Account.id(1);
    String json = gson.toJson(accountId);
    assertThat(accountId).isEqualTo(objectUnderTest.fromJson(Constants.ACCOUNTS, json));
    assertThat(accountId).isEqualTo(objectUnderTest.fromJson(Constants.GROUPS_BYMEMBER, json));
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
  public void projectNameKeyParse() {
    Project.NameKey name = Project.nameKey("foo");
    String json = gson.toJson(name);
    assertThat(name).isEqualTo(objectUnderTest.fromJson(Constants.PROJECTS, json));
  }

  @Test
  public void stringParse() {
    String key = "key";
    String json = gson.toJson(key);
    assertThat(key).isEqualTo(objectUnderTest.fromJson("string-keyed-cache", json));
  }

  @Test
  public void noKeyParse() {
    Object object = new Object();
    String json = gson.toJson(object);
    assertThat(json).isEqualTo(EMPTY_JSON);
  }
}

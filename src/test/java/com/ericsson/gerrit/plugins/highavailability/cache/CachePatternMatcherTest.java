// Copyright (C) 2018 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.cache;

import static com.google.common.truth.Truth.assertWithMessage;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CachePatternMatcherTest {

  @Mock(answer = RETURNS_DEEP_STUBS)
  private Configuration configurationMock;

  @Test
  public void testCachePatternMatcher() throws Exception {
    when(configurationMock.cache().patterns()).thenReturn(ImmutableList.of("^my_cache.*", "other"));
    CachePatternMatcher matcher = new CachePatternMatcher(configurationMock);
    for (String cache :
        ImmutableList.of(
            "groups",
            "groups_byinclude",
            "groups_byname",
            "groups_byuuid",
            "groups_external",
            "groups_members",
            "ldap_usernames",
            "projects",
            "sshkeys",
            "tokens",
            "my_cache_a",
            "my_cache_b",
            "other")) {
      assertWithMessage(cache + " should match").that(matcher.matches(cache)).isTrue();
    }
    for (String cache :
        ImmutableList.of(
            "accounts",
            "adv_bases",
            "change_kind",
            "change_notes",
            "changes",
            "conflicts",
            "diff",
            "diff_intraline",
            "diff_summary",
            "git_tags",
            "ldap_group_existence",
            "ldap_groups",
            "ldap_groups_byinclude",
            "mergeability",
            "oauth_tokens",
            "permission_sort",
            "project_list",
            "plugin_resources",
            "static_content",
            "foo")) {
      assertWithMessage(cache + " should not match").that(matcher.matches(cache)).isFalse();
    }
  }

  @Test
  public void testShouldNotMatchWebSessionsWhenNotSynchronized() {
    String cache = "web_sessions";
    when(configurationMock.cache().patterns()).thenReturn(Collections.emptyList());
    when(configurationMock.websession().synchronize()).thenReturn(false);
    CachePatternMatcher matcher = new CachePatternMatcher(configurationMock);

    assertWithMessage(cache + " should NOT match when websession.synchronize is false")
        .that(matcher.matches(cache))
        .isFalse();
  }

  @Test
  public void testShouldMatchWebSessionsWhenSynchronized() {
    String cache = "web_sessions";
    when(configurationMock.cache().patterns()).thenReturn(Collections.emptyList());
    when(configurationMock.websession().synchronize()).thenReturn(true);
    CachePatternMatcher matcher = new CachePatternMatcher(configurationMock);

    assertWithMessage(cache + " should match when websession.synchronize is true")
        .that(matcher.matches(cache))
        .isTrue();
  }
}

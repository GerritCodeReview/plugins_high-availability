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

package com.ericsson.gerrit.plugins.highavailability.forwarder.pubsub.gcp;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.forwarder.Forwarder.Result;
import com.ericsson.gerrit.plugins.highavailability.forwarder.IndexEvent;
import com.google.pubsub.v1.ReceivedMessage;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PubSubForwarderTest {
  private static PubSubTestSystem testSystem;

  TestApp app1;
  TestApp app2;

  @BeforeClass
  public static void startTestSystem() throws Exception {
    testSystem = PubSubTestSystem.create();
  }

  @Before
  public void setUp() throws Exception {
    app1 = new TestApp("inst-1", testSystem);
    app1.start();

    app2 = new TestApp("inst-2", testSystem);
    app2.start();
  }

  @After
  public void tearDown() throws Exception {
    app1.stop();
    app2.stop();
  }

  @AfterClass
  public static void stopTestSystem() throws Exception {
    testSystem.cleanup();
  }

  @Test
  public void indexAccount_publishSubscribe_OK() throws Exception {
    when(app2.cmdProcessor.handle(any())).thenReturn(true);
    CompletableFuture<Result> result = app1.forwarder.indexAccount(100, new IndexEvent());
    assertThat(result.get().result()).isTrue();
    verify(app2.cmdProcessor, timeout(10000).times(1)).handle(any());
  }

  @Test
  public void indexAccount_publishSubscribe_Retries() throws Exception {
    when(app2.cmdProcessor.handle(any())).thenReturn(false, true);
    CompletableFuture<Result> result = app1.forwarder.indexAccount(100, new IndexEvent());
    assertThat(result.get().result()).isTrue();
    verify(app2.cmdProcessor, timeout(10000).atLeast(2)).handle(any());
  }

  @Test
  public void maxReceiveCount_exceeded_movedToDlt() throws Exception {
    TestApp dltMonitoringApp = new TestApp("dlt-monitor", testSystem);
    dltMonitoringApp.start();
    dltMonitoringApp.drainDltMessages();

    when(app2.cmdProcessor.handle(any())).thenReturn(false);
    CompletableFuture<Result> result = app1.forwarder.indexAccount(100, new IndexEvent());
    assertThat(result.get().result()).isTrue();

    // wait long enough so that max delivery attempts is exhausted and message moved to DLT
    verify(app2.cmdProcessor, timeout(20000).atLeast(5)).handle(any());

    List<ReceivedMessage> messages = dltMonitoringApp.receiveMessagesFromDlt();
    assertThat(messages).hasSize(1);
  }
}

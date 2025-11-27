// Copyright (C) 2026 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.forwarder.pubsub.aws;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.forwarder.Forwarder.Result;
import com.ericsson.gerrit.plugins.highavailability.forwarder.IndexEvent;
import com.ericsson.gerrit.plugins.highavailability.forwarder.commands.IndexAccount;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

@RunWith(MockitoJUnitRunner.class)
public class AwsPubSubForwarderTest {

  private static final int MAX_RECEIVE_COUNT = 3;

  private static AwsPubSubTestSystem testSystem;

  private volatile TestApp app1;
  private volatile TestApp app2;

  @BeforeClass
  public static void startTestSystem() {
    testSystem = AwsPubSubTestSystem.create();
    testSystem.start();
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
  public static void stopTestSystem() {
    testSystem.stop();
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
  public void maxReceiveCount_exceeded_movedToDlq() throws Exception {
    TestApp dlqMonitoringApp = new TestApp("inst-3", testSystem);
    dlqMonitoringApp.start();

    assertThat(dlqMonitoringApp.receiveMessagesFromDlq()).isEmpty();

    // Always fail processing the message, force it to be moved to DLQ
    when(app2.cmdProcessor.handle(any())).thenReturn(false);

    CompletableFuture<Result> result = app1.forwarder.indexAccount(100, new IndexEvent());
    assertThat(result.get().result()).isTrue();

    // handle() should be called MAX_RECEIVE_COUNT times before message is moved to DLQ
    verify(app2.cmdProcessor, timeout(10000).atLeast(MAX_RECEIVE_COUNT)).handle(any());

    // non-processed message should appear in DLQ
    assertThat(dlqMonitoringApp.receiveMessagesFromDlq()).hasSize(1);
  }

  @Test
  public void willNotProcessOwnMessages() throws Exception {
    String msgBody = app1.gson.toJson(new IndexAccount(100, new IndexEvent().eventCreatedOn));
    Message msg =
        Message.builder()
            .body(msgBody)
            .messageAttributes(
                Map.of(
                    "instanceId",
                    MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(app1.instanceId)
                        .build()))
            .build();

    SqsMessagePoller poller = app1.pollerFactory.create(app1.defaultQueueInfo);
    poller.processMessage(msg);
    verify(app1.cmdProcessor, times(0)).handle(any());
  }
}

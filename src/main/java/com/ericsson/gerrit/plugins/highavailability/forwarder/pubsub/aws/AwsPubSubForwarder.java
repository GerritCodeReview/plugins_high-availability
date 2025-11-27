package com.ericsson.gerrit.plugins.highavailability.forwarder.pubsub.aws;

import com.ericsson.gerrit.plugins.highavailability.forwarder.Forwarder;
import com.ericsson.gerrit.plugins.highavailability.forwarder.IndexEvent;
import com.ericsson.gerrit.plugins.highavailability.forwarder.commands.AddToProjectList;
import com.ericsson.gerrit.plugins.highavailability.forwarder.commands.Command;
import com.ericsson.gerrit.plugins.highavailability.forwarder.commands.CommandsGson;
import com.ericsson.gerrit.plugins.highavailability.forwarder.commands.EvictCache;
import com.ericsson.gerrit.plugins.highavailability.forwarder.commands.IndexAccount;
import com.ericsson.gerrit.plugins.highavailability.forwarder.commands.IndexChange;
import com.ericsson.gerrit.plugins.highavailability.forwarder.commands.IndexGroup;
import com.ericsson.gerrit.plugins.highavailability.forwarder.commands.IndexProject;
import com.ericsson.gerrit.plugins.highavailability.forwarder.commands.PostEvent;
import com.ericsson.gerrit.plugins.highavailability.forwarder.commands.RemoveFromProjectList;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.gerrit.server.events.Event;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import software.amazon.awssdk.services.sns.SnsAsyncClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

@Singleton
public class AwsPubSubForwarder implements Forwarder {
  private final Gson gson;
  private final String instanceId;
  private final SnsAsyncClient sns;
  private final String defaultTopicArn;

  @Inject
  AwsPubSubForwarder(
      @CommandsGson Gson gson,
      @GerritInstanceId String instanceId,
      SnsAsyncClient sns,
      @DefaultTopicArn String defaultTopicArn) {
    this.gson = gson;
    this.instanceId = instanceId;
    this.sns = sns;
    this.defaultTopicArn = defaultTopicArn;
  }

  @Override
  public CompletableFuture<Boolean> indexAccount(int accountId, IndexEvent indexEvent) {
    return execute(new IndexAccount(accountId));
  }

  @Override
  public CompletableFuture<Boolean> indexChange(
      String projectName, int changeId, IndexEvent indexEvent) {
    return execute(new IndexChange.Update(projectName, changeId));
  }

  @Override
  public CompletableFuture<Boolean> batchIndexChange(
      String projectName, int changeId, IndexEvent indexEvent) {
    return execute(new IndexChange.Update(projectName, changeId, true));
  }

  @Override
  public CompletableFuture<Boolean> deleteChangeFromIndex(int changeId, IndexEvent indexEvent) {
    return execute(new IndexChange.Delete(changeId));
  }

  @Override
  public CompletableFuture<Boolean> indexGroup(String uuid, IndexEvent indexEvent) {
    return execute(new IndexGroup(uuid));
  }

  @Override
  public CompletableFuture<Boolean> indexProject(String projectName, IndexEvent indexEvent) {
    return execute(new IndexProject(projectName));
  }

  @Override
  public CompletableFuture<Boolean> send(Event event) {
    return execute(new PostEvent(event));
  }

  @Override
  public CompletableFuture<Boolean> evict(String cacheName, Object key) {
    return execute(new EvictCache(cacheName, gson.toJson(key)));
  }

  @Override
  public CompletableFuture<Boolean> addToProjectList(String projectName) {
    return execute(new AddToProjectList(projectName));
  }

  @Override
  public CompletableFuture<Boolean> removeFromProjectList(String projectName) {
    return execute(new RemoveFromProjectList(projectName));
  }

  @Override
  public CompletableFuture<Boolean> deleteAllChangesForProject(NameKey projectName) {
    // TODO
    return null;
  }

  private CompletableFuture<Boolean> execute(Command cmd) {
    String msg = gson.toJson(cmd);
    CompletableFuture<PublishResponse> rsp =
        sns.publish(
            PublishRequest.builder()
                .topicArn(defaultTopicArn)
                .message(msg)
                .messageAttributes(
                    Map.of(
                        "instanceId",
                        MessageAttributeValue.builder()
                            .dataType("String")
                            .stringValue(instanceId)
                            .build()))
                .build());
    return rsp.thenApply(r -> r.sdkHttpResponse().isSuccessful());
  }
}

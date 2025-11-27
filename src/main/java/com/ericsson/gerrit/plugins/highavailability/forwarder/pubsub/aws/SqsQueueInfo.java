package com.ericsson.gerrit.plugins.highavailability.forwarder.pubsub.aws;

public record SqsQueueInfo(String name, String url, String arn) {}

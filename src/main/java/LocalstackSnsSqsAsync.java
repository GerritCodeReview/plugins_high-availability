import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsAsyncClient;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sns.model.CreateTopicResponse;
import software.amazon.awssdk.services.sns.model.DeleteTopicRequest;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;

public class LocalstackSnsSqsAsync {

  private static final String LOCALSTACK = "http://localhost:4566";

  public static void main(String[] args) throws Exception {

    var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"));

    var sns =
        SnsAsyncClient.builder()
            .region(Region.US_EAST_1)
            .endpointOverride(URI.create(LOCALSTACK))
            .credentialsProvider(credentials)
            .httpClientBuilder(NettyNioAsyncHttpClient.builder())
            .build();

    var sqs =
        SqsAsyncClient.builder()
            .region(Region.US_EAST_1)
            .endpointOverride(URI.create(LOCALSTACK))
            .credentialsProvider(credentials)
            .httpClientBuilder(NettyNioAsyncHttpClient.builder())
            .build();

    // 1) Create SNS topic
    CompletableFuture<String> topicArnF =
        sns.createTopic(CreateTopicRequest.builder().name("AsyncTopic").build())
            .thenApply(CreateTopicResponse::topicArn);

    String topicArn = topicArnF.get();
    System.out.println("Created topic: " + topicArn);

    // 2) Create SQS queue
    CompletableFuture<String> queueUrlF =
        sqs.createQueue(CreateQueueRequest.builder().queueName("AsyncQueue").build())
            .thenApply(CreateQueueResponse::queueUrl);

    String queueUrl = queueUrlF.get();
    System.out.println("Created queue: " + queueUrl);

    // Get queue ARN
    String queueArn =
        sqs.getQueueAttributes(
                GetQueueAttributesRequest.builder()
                    .queueUrl(queueUrl)
                    .attributeNames(QueueAttributeName.QUEUE_ARN)
                    .build())
            .thenApply(r -> r.attributes().get(QueueAttributeName.QUEUE_ARN))
            .get();

    // Allow SNS to publish to SQS
    String policy =
        "{\n"
            + "  \"Version\": \"2012-10-17\",\n"
            + "  \"Statement\": [{\n"
            + "    \"Effect\": \"Allow\",\n"
            + "    \"Principal\": \"*\",\n"
            + "    \"Action\": \"sqs:SendMessage\",\n"
            + "    \"Resource\": \""
            + queueArn
            + "\",\n"
            + "    \"Condition\": { \"ArnEquals\": { \"aws:SourceArn\": \""
            + topicArn
            + "\" } }\n"
            + "  }]\n"
            + "}";

    sqs.setQueueAttributes(
            SetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributes(Map.of(QueueAttributeName.POLICY, policy))
                .build())
        .get();

    // 3) Subscribe SQS queue to the topic
    sns.subscribe(
            SubscribeRequest.builder()
                .topicArn(topicArn)
                .protocol("sqs")
                .endpoint(queueArn)
                .returnSubscriptionArn(true)
                .build())
        .get();

    // 4) Publish message WITH attributes
    sns.publish(
            PublishRequest.builder()
                .topicArn(topicArn)
                .message("Hello async SNS!")
                .messageAttributes(
                    Map.of(
                        "origin",
                            MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue("LocalStackTest")
                                .build(),
                        "counter",
                            MessageAttributeValue.builder()
                                .dataType("Number")
                                .stringValue("1")
                                .build()))
                .build())
        .get();

    System.out.println("Published message with attributes.");

    // 5) Receive message (async polling)
    ReceiveMessageResponse msgResp =
        sqs.receiveMessage(
                ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(1)
                    .waitTimeSeconds(5)
                    .messageAttributeNames("All")
                    .build())
            .get();

    msgResp
        .messages()
        .forEach(
            m -> {
              System.out.println("----- RECEIVED MESSAGE -----");
              System.out.println("Body: " + m.body());
              System.out.println("Attributes:");
              m.messageAttributes()
                  .forEach((k, v) -> System.out.println("  " + k + ": " + v.stringValue()));
              System.out.println("----------------------------");

              // delete
              sqs.deleteMessage(
                  DeleteMessageRequest.builder()
                      .queueUrl(queueUrl)
                      .receiptHandle(m.receiptHandle())
                      .build());
            });

    // cleanup
    sns.deleteTopic(DeleteTopicRequest.builder().topicArn(topicArn).build()).get();
    sqs.deleteQueue(DeleteQueueRequest.builder().queueUrl(queueUrl).build()).get();

    System.out.println("Done.");
  }
}

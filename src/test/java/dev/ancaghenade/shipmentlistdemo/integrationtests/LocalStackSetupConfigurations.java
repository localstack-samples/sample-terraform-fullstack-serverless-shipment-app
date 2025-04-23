package dev.ancaghenade.shipmentlistdemo.integrationtests;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.AttachRolePolicyRequest;
import software.amazon.awssdk.services.iam.model.CreateRoleRequest;
import software.amazon.awssdk.services.iam.model.GetRoleRequest;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.AddPermissionRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.Environment;
import software.amazon.awssdk.services.lambda.model.FunctionCode;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.Event;
import software.amazon.awssdk.services.s3.model.LambdaFunctionConfiguration;
import software.amazon.awssdk.services.s3.model.NotificationConfiguration;
import software.amazon.awssdk.services.s3.model.PutBucketNotificationConfigurationRequest;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

@Testcontainers
@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT)
public class LocalStackSetupConfigurations {

  @Container
  protected static LocalStackContainer localStack =
    new LocalStackContainer(DockerImageName.parse("localstack/localstack:latest"))
    .withEnv("LOCALSTACK_HOST", "localhost.localstack.cloud")
    .withEnv("LAMBDA_RUNTIME_ENVIRONMENT_TIMEOUT", "60")
    .withEnv("DEBUG", "1");

  private static final Logger LOGGER = LoggerFactory.getLogger(LocalStackSetupConfigurations.class);
  protected static Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(LOGGER);
  protected TestRestTemplate restTemplate = new TestRestTemplate();

  protected static final String BUCKET_NAME = "shipment-picture-bucket";
  protected static String BASE_URL = "http://localhost:8081";
  protected static Region region = Region.of(localStack.getRegion());
  protected static S3Client s3Client;
  protected static DynamoDbClient dynamoDbClient;
  protected static LambdaClient lambdaClient;
  protected static SqsClient sqsClient;
  protected static SnsClient snsClient;
  protected static IamClient iamClient;
  protected static Logger logger = LoggerFactory.getLogger(LocalStackSetupConfigurations.class);
  protected static ObjectMapper objectMapper = new ObjectMapper();
  protected static URI localStackEndpoint;

  @BeforeAll()
  protected static void setupConfig() {
    localStackEndpoint = localStack.getEndpoint();
  }

  @DynamicPropertySource
  static void overrideConfigs(DynamicPropertyRegistry registry) {
    registry.add("aws.s3.endpoint",
      () -> localStackEndpoint);
    registry.add(
      "aws.dynamodb.endpoint", () -> localStackEndpoint);
    registry.add(
      "aws.sqs.endpoint", () -> localStackEndpoint);
    registry.add(
      "aws.sns.endpoint", () -> localStackEndpoint);
    registry.add("aws.credentials.secret-key", localStack::getSecretKey);
    registry.add("aws.credentials.access-key", localStack::getAccessKey);
    registry.add("aws.region", localStack::getRegion);
    registry.add("shipment-picture-bucket", () -> BUCKET_NAME);
  }

  protected static void createClients() {
    s3Client = S3Client.builder()
      .region(region)
      .endpointOverride(localStack.getEndpointOverride(LocalStackContainer.Service.S3))
      .credentialsProvider(StaticCredentialsProvider.create(
        AwsBasicCredentials.create(localStack.getAccessKey(), localStack.getSecretKey())))
      .build();
    dynamoDbClient = DynamoDbClient.builder()
      .region(region)
      .endpointOverride(localStack.getEndpointOverride(Service.DYNAMODB))
      .credentialsProvider(StaticCredentialsProvider.create(
        AwsBasicCredentials.create(localStack.getAccessKey(), localStack.getSecretKey())))
      .build();
    lambdaClient = LambdaClient.builder()
      .region(region)
      .endpointOverride(localStack.getEndpointOverride(Service.LAMBDA))
      .credentialsProvider(StaticCredentialsProvider.create(
        AwsBasicCredentials.create(localStack.getAccessKey(), localStack.getSecretKey())))
      .build();
    sqsClient = SqsClient.builder()
      .region(region)
      .endpointOverride(localStack.getEndpointOverride(Service.SQS))
      .credentialsProvider(StaticCredentialsProvider.create(
        AwsBasicCredentials.create(localStack.getAccessKey(), localStack.getSecretKey())))
      .build();
    snsClient = SnsClient.builder()
      .region(region)
      .endpointOverride(localStack.getEndpointOverride(Service.SNS))
      .credentialsProvider(StaticCredentialsProvider.create(
        AwsBasicCredentials.create(localStack.getAccessKey(), localStack.getSecretKey())))
      .build();
    iamClient = IamClient.builder()
      .region(Region.AWS_GLOBAL)
      .endpointOverride(localStack.getEndpointOverride(Service.IAM))
      .credentialsProvider(StaticCredentialsProvider.create(
        AwsBasicCredentials.create(localStack.getAccessKey(), localStack.getSecretKey())))
      .build();
  }

  protected static void createIAMRole() {
    var roleName = "lambda_exec_role";
    // assume role policy document
    var assumeRolePolicyDocument = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"Service\":\"lambda.amazonaws.com\"},\"Action\":\"sts:AssumeRole\"}]}";

    // call createRole API with request using name and policy
    iamClient.createRole(CreateRoleRequest.builder()
      .roleName(roleName)
      .assumeRolePolicyDocument(assumeRolePolicyDocument)
      .build());

    var policyArn = "arn:aws:iam::aws:policy/AmazonS3FullAccess";

    // attach s3 full access policy to role
    iamClient.attachRolePolicy(
      AttachRolePolicyRequest.builder()
      .roleName(roleName)
      .policyArn(policyArn)
      .build());

  }

  private static String getQueueUrl(SqsClient sqsClient, String queueName) {
    return sqsClient.getQueueUrl(r -> r.queueName(queueName)).queueUrl();
  }

  protected static void createSNSSubscription() {
    String topicArn = snsClient.listTopics().topics().get(0).topicArn();
    // get the queue URL
    String queueName = "update_shipment_picture_queue";

    // create get queue attributes request
    var request = GetQueueAttributesRequest.builder()
      .queueUrl(getQueueUrl(sqsClient, queueName))
      .attributeNames(QueueAttributeName.QUEUE_ARN)
      .build();

    // call API with the request and get the attributes
    var response = sqsClient.getQueueAttributes(request);
    // extract queue arn
    String queueArn = response.attributes().get(QueueAttributeName.QUEUE_ARN);

    // create the queue subscribe to topic request
    var subscribeRequest = SubscribeRequest.builder()
      .topicArn(topicArn)
      .protocol("sqs")
      .endpoint(queueArn)
      .build();

    // call subscribe API with request
    snsClient.subscribe(subscribeRequest);
  }

  protected static void createSQS() {
    // queue name
    var queueName = "update_shipment_picture_queue";

    // request to create queue
    var request = CreateQueueRequest.builder()
      .queueName(queueName)
      .build();

    // call createQueue API with the request
    sqsClient.createQueue(request);
  }

  protected static void createSNS() {
    // topic name
    var topicName = "update_shipment_picture_topic";

    // create topic request
    var request = CreateTopicRequest.builder()
      .name(topicName)
      .build();

    // call createTopic API with request
    snsClient.createTopic(request);
  }

  protected static void createBucketNotificationConfiguration()
  throws IOException, InterruptedException, org.json.JSONException {

    try {
      // lambda needs to be in state "Active" in order to proceed with adding permissions
      // this can take 2-3 seconds to reach
      var result = localStack.execInContainer(formatCommand(
        "awslocal lambda get-function --function-name shipment-picture-lambda-validator"));
      var obj = new JSONObject(result.getStdout()).getJSONObject("Configuration");
      var state = obj.getString("State");
      while (!state.equals("Active")) {
        result = localStack.execInContainer(formatCommand(
          "awslocal lambda get-function --function-name shipment-picture-lambda-validator"));
        obj = new JSONObject(result.getStdout()).getJSONObject("Configuration");
        state = obj.getString("State");
      }

      // create notification configuration
      var notificationConfiguration = NotificationConfiguration.builder()
        .lambdaFunctionConfigurations(
          LambdaFunctionConfiguration.builder().id("shipment-picture-lambda-validator")
          .lambdaFunctionArn(
            "arn:aws:lambda:" + region +
            ":000000000000:function:shipment-picture-lambda-validator")
          .events(Event.S3_OBJECT_CREATED).build()).build();

      // create the request for trigger
      var request = PutBucketNotificationConfigurationRequest.builder()
        .bucket(BUCKET_NAME)
        .notificationConfiguration(notificationConfiguration)
        .build();

      // call the PutBucketNotificationConfiguration API with the request
      s3Client.putBucketNotificationConfiguration(request);

    } catch (Exception e) {
      System.err.println("Error creating bucket notification configuration: " + e.getMessage());
    }
  }

  protected static void createLambdaResources() {
    var functionName = "shipment-picture-lambda-validator";
    var runtime = "java17";
    var handler = "dev.ancaghenade.shipmentpicturelambdavalidator.ServiceHandler::handleRequest";
    var zipFilePath = "shipment-picture-lambda-validator/target/shipment-picture-lambda-validator.jar";
    var sourceArn = "arn:aws:s3:000000000000:" + BUCKET_NAME;
    var statementId = "AllowExecutionFromS3Bucket";
    var action = "lambda:InvokeFunction";
    var principal = "s3.amazonaws.com";

    var getRoleResponse = iamClient.getRole(GetRoleRequest.builder()
      .roleName("lambda_exec_role")
      .build());

    var roleArn = getRoleResponse.role().arn();

    try {
      var zipFileBytes = Files.readAllBytes(Paths.get(zipFilePath));
      var zipFileBuffer = ByteBuffer.wrap(zipFileBytes);

      var createFunctionRequest = CreateFunctionRequest.builder()
        .functionName(functionName)
        .runtime(runtime)
        .handler(handler)
        .code(FunctionCode.builder().zipFile(SdkBytes.fromByteBuffer(zipFileBuffer)).build())
        .role(roleArn)
        .timeout(60)
        .memorySize(512)
        // bucket name that is being passed as env var because it's randomly generated
        .environment(
          Environment.builder().variables(Collections.singletonMap("BUCKET", BUCKET_NAME))
          .build())
        .build();

      lambdaClient.createFunction(
        createFunctionRequest);

      var request = AddPermissionRequest.builder()
        .functionName(functionName)
        .statementId(statementId)
        .action(action)
        .principal(principal)
        .sourceArn(sourceArn)
        .sourceAccount("000000000000")
        .build();

      // call the addPermission API with the request
      lambdaClient.addPermission(request);

    } catch (Exception e) {
      System.err.println("Error creating Lambda function: " + e.getMessage());
    }
  }

  protected static void createDynamoDBResources() {

    // table name
    var tableName = "shipment";

    // attribute definitions
    var attributeDefinition = AttributeDefinition.builder()
      .attributeName("shipmentId")
      .attributeType(ScalarAttributeType.S)
      .build();

    // create key schema
    var keySchemaElement = KeySchemaElement.builder()
      .attributeName("shipmentId")
      .keyType(KeyType.HASH)
      .build();

    // CreateTableRequest with table name, attribute definitions, key schema, and billing mode
    var createTableRequest = CreateTableRequest.builder()
      .tableName(tableName)
      .attributeDefinitions(attributeDefinition)
      .keySchema(keySchemaElement)
      .billingMode(BillingMode.PAY_PER_REQUEST)
      .build();

    // createTable operation to create the table
    dynamoDbClient.createTable(createTableRequest);

    // create attribute values for the item
    var shipmentId = AttributeValue.builder().s("3317ac4f-1f9b-4bab-a974-4aa9876d5547")
      .build();
    var recipientName = AttributeValue.builder().s("Harry Potter").build();
    // add other attributes as needed

    // create a map to hold the item attribute values
    var item = new HashMap < String,
      AttributeValue > ();
    item.put("shipmentId", shipmentId);
    item.put("recipient", AttributeValue.builder()
      .m(Map.of(
        "name", recipientName,
        "address", AttributeValue.builder()
        .m(Map.of(
          "postalCode", AttributeValue.builder().s("LNDNGB").build(),
          "street", AttributeValue.builder().s("Privet Drive").build(),
          "number", AttributeValue.builder().s("4").build(),
          "city", AttributeValue.builder().s("Little Whinging").build(),
          "additionalInfo", AttributeValue.builder().s("").build()
        ))
        .build()
      ))
      .build());

    var senderName = AttributeValue.builder().s("Warehouse of Unicorns").build();

    item.put("sender", AttributeValue.builder()
      .m(Map.of(
        "name", senderName,
        "address", AttributeValue.builder()
        .m(Map.of(
          "postalCode", AttributeValue.builder().s("98653").build(),
          "street", AttributeValue.builder().s("47th Street").build(),
          "number", AttributeValue.builder().s("5").build(),
          "city", AttributeValue.builder().s("Townsville").build(),
          "additionalInfo", AttributeValue.builder().s("").build()
        ))
        .build()
      ))
      .build());
    item.put("weight", AttributeValue.builder().s("2.3").build());

    // create a PutItemRequest with the table name and item
    var putItemRequest = PutItemRequest.builder()
      .tableName(tableName)
      .item(item)
      .build();

    // call the putItem operation to add the item to the table
    dynamoDbClient.putItem(putItemRequest);
  }

  protected static void createS3Bucket() {
    // bucket name
    var bucketName = BUCKET_NAME;
    // CreateBucketRequest with the bucket name
    var createBucketRequest = CreateBucketRequest.builder()
      .bucket(bucketName)
      .build();
    // createBucket operation to create the bucket
    s3Client.createBucket(createBucketRequest);

    var putBucketPolicyRequest = PutBucketPolicyRequest.builder()
      .bucket(bucketName)
      .policy(
        "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Sid\":\"AllowLambdaInvoke\",\"Effect\":\"Allow\",\"Principal\":{\"AWS\":\"*\"},\"Action\":\"s3:GetObject\",\"Resource\":\"arn:aws:s3:::" +
        BUCKET_NAME + "/*\"}]}")
      .build();

    s3Client.putBucketPolicy(putBucketPolicyRequest);
  }

  protected static ExecResult executeInContainer(String command) throws Exception {

    final
    var execResult = localStack.execInContainer(formatCommand(command));
    // assertEquals(0, execResult.getExitCode());

    final
    var logs = execResult.getStdout() + execResult.getStderr();
    logger.info(logs);
    logger.error(execResult.getExitCode() != 0 ? execResult + " - DOES NOT WORK" : "");
    return execResult;
  }

  private static String[] formatCommand(String command) {
    return command.split(" ");
  }
}

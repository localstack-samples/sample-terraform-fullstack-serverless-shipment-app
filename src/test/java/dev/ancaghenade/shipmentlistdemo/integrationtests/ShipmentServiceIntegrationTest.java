package dev.ancaghenade.shipmentlistdemo.integrationtests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.ancaghenade.shipmentlistdemo.entity.Shipment;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ActiveProfiles("dev")
class ShipmentServiceIntegrationTest extends LocalStackSetupConfigurations {

  @BeforeAll
  public static void setup() {
    LocalStackSetupConfigurations.setupConfig();

    localStack.followOutput(logConsumer);

    s3Client = S3Client.builder()
      .region(region)
      .endpointOverride(localStack.getEndpointOverride(LocalStackContainer.Service.S3))
      .build();
    dynamoDbClient = DynamoDbClient.builder()
      .region(region)
      .endpointOverride(localStack.getEndpointOverride(Service.DYNAMODB))
      .build();

    createS3Bucket();
    createDynamoDBResources();

  }

  @Test
  @Order(1)
  void testFileUploadToS3() throws Exception {
    // Prepare the file to upload
    var imageData = new byte[0];
    try {
      imageData = Files.readAllBytes(Path.of("src/test/java/resources/cat.jpg"));
    } catch (IOException e) {
      e.printStackTrace();
    }
    var resource = new ByteArrayResource(imageData) {
      @Override
      public String getFilename() {
        return "cat.jpg";
      }
    };
    var shipmentId = "3317ac4f-1f9b-4bab-a974-4aa9876d5547";
    // build the URL with the id as a path variable
    var url = "/api/shipment/" + shipmentId + "/image/upload";
    // set the request headers
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    // request body with the file resource and headers
    MultiValueMap < String, Object > requestBody = new LinkedMultiValueMap < > ();
    requestBody.add("file", resource);
    HttpEntity < MultiValueMap < String, Object >> requestEntity = new HttpEntity < > (requestBody,
      headers);

    ResponseEntity < String > responseEntity = restTemplate.exchange(BASE_URL + url,
      HttpMethod.POST, requestEntity, String.class);

    assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
    var execResult = executeInContainer(
      "awslocal s3api list-objects --bucket shipment-picture-bucket --query length(Contents[])");
    assertEquals(String.valueOf(1), execResult.getStdout().trim());
  }

  @Test
  @Order(2)
  void testFileDownloadFromS3() {

    var shipmentId = "3317ac4f-1f9b-4bab-a974-4aa9876d5547";
    // build the URL with the id as a path variable
    var url = "/api/shipment/" + shipmentId + "/image/download";

    ResponseEntity < byte[] > responseEntity = restTemplate.exchange(BASE_URL + url,
      HttpMethod.GET, null, byte[].class);

    assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
    // object is not empty
    assertNotNull(responseEntity.getBody());
  }

  @Test
  @Order(3)
  void testAddShipmentToDynamoDB() throws IOException {

    var url = "/api/shipment";
    // set the request headers

    var json = new File("src/test/java/resources/shipmentToUpload.json");
    var shipment = objectMapper.readValue(json, Shipment.class);

    var headers = new HttpHeaders();
    headers.setContentType(MediaType.valueOf(MediaType.APPLICATION_JSON_VALUE));

    HttpEntity < Shipment > requestEntity = new HttpEntity < > (shipment,
      headers);

    ResponseEntity < String > responseEntity = restTemplate.exchange(BASE_URL + url,
      HttpMethod.POST, requestEntity, String.class);

    assertEquals(HttpStatus.OK, responseEntity.getStatusCode());

  }

  @Test
  @Order(4)
  void testGetTwoShipmentsFromDynamoDB() {

    var url = "/api/shipment";
    // set the request headers
    ResponseEntity < List < Shipment >> responseEntity = restTemplate.exchange(BASE_URL + url,
      HttpMethod.GET, null, new ParameterizedTypeReference < > () {});

    if (responseEntity.getStatusCode().is2xxSuccessful()) {
      List < Shipment > shipmentList = responseEntity.getBody();
      assertEquals(2, shipmentList.size());
    }
  }

  @Test
  @Order(5)
  void testDeleteShipmentFromDynamoDB() {

    var url = "/api/shipment";
    var shipmentId = "/3317ac4f-1f9b-4bab-a974-4aa9876d5547";

    // set the request headers
    ResponseEntity < String > deleteResponseEntity = restTemplate.exchange(BASE_URL + url + shipmentId,
      HttpMethod.DELETE, null, String.class);

    assertEquals(HttpStatus.OK, deleteResponseEntity.getStatusCode());
    assertEquals("Shipment has been deleted", deleteResponseEntity.getBody());

    ResponseEntity < List < Shipment >> getResponseEntity = restTemplate.exchange(BASE_URL + url,
      HttpMethod.GET, null, new ParameterizedTypeReference < > () {});

    if (getResponseEntity.getStatusCode().is2xxSuccessful()) {
      List < Shipment > shipmentList = getResponseEntity.getBody();
      assertEquals(1, shipmentList.size());
    }
  }
}

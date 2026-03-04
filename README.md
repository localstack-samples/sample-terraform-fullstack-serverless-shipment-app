# Full-Stack Serverless Shipment Management App deployed with Terraform on LocalStack

| Key          | Value                                                                                                     |
| ------------ | --------------------------------------------------------------------------------------------------------- |
| Environment  | LocalStack, AWS                                                                                           |
| Services     | S3, Lambda, DynamoDB, SNS, SQS                                                                            |
| Integrations | Terraform, AWS SDK, Spring Boot, React, Testcontainers                                                    |
| Categories   | Serverless, Storage, Messaging                                                                            |
| Level        | Intermediate                                                                                              |
| Use Case     | IaC Testing, AWS Parity                                                                                   |
| GitHub       | [Repository link](https://github.com/localstack-samples/sample-terraform-fullstack-serverless-shipment-app)     |

## Introduction

This sample demonstrates a full-stack shipment management application that showcases integration between multiple AWS services. The application consists of a Spring Boot backend with a React frontend, implementing CRUD operations on shipments with Lambda image processing capabilities. To test this application sample, we will demonstrate how you use LocalStack to deploy the infrastructure on your developer machine and run the application locally. The demo highlights the ease of switching from actual AWS dependencies to LocalStack emulation for development environments without any code changes.

## Architecture

The following diagram shows the architecture that this sample application builds and deploys:

![Architecture Diagram](sample-pictures/architecture.png)

-   [S3](https://docs.localstack.cloud/aws/services/s3/) for storing shipment pictures and Lambda deployment packages.
-   [Lambda](https://docs.localstack.cloud/aws/services/lambda/) function that validates uploaded pictures, applies watermarks, and replaces non-compliant files.
-   [DynamoDB](https://docs.localstack.cloud/aws/services/dynamodb/) table to store shipment entities with enhanced client mapping.
-   [SNS](https://docs.localstack.cloud/aws/services/sns/) topic that receives update notifications from the Lambda function.
-   [SQS](https://docs.localstack.cloud/aws/services/sqs/) queue that subscribes to the SNS topic and delivers messages to the Spring Boot application.

## Prerequisites

- A valid [LocalStack for AWS license](https://localstack.cloud/pricing). Your license provides a `LOCALSTACK_AUTH_TOKEN` to activate LocalStack.
- [`localstack` CLI](https://docs.localstack.cloud/getting-started/installation/#localstack-cli) with a [`LOCALSTACK_AUTH_TOKEN`](https://docs.localstack.cloud/getting-started/auth-token/).
- [AWS CLI](https://docs.localstack.cloud/user-guide/integrations/aws-cli/) with the [`awslocal` wrapper](https://docs.localstack.cloud/user-guide/integrations/aws-cli/#localstack-aws-cli-awslocal).
- [Terraform](https://docs.localstack.cloud/user-guide/integrations/terraform/) with the [`tflocal`](https://github.com/localstack/terraform-local) wrapper.
- [Maven 3.8.5+](https://maven.apache.org/install.html) & [Java 17](https://www.java.com/en/download/help/download_options.html)
- [Node.js](https://nodejs.org/en/download/) & [npm](https://docs.npmjs.com/downloading-and-installing-node-js-and-npm)
- [`make`](https://www.gnu.org/software/make/) (**optional**, but recommended for running the sample application)

## Installation

To run the sample application, you need to install the required dependencies.

First, clone the repository:

```shell
git clone https://github.com/localstack-samples/sample-terraform-fullstack-serverless-shipment-app.git
```

Then, navigate to the project directory:

```shell
cd sample-terraform-fullstack-serverless-shipment-app
```

Next, install the project dependencies by running the following command:

```shell
make install
```

This will:
- Build the Lambda validator JAR file
- Install frontend dependencies via npm

## Deployment

Start LocalStack with the `LOCALSTACK_AUTH_TOKEN` pre-configured:

```shell
localstack auth set-token <your-auth-token>
localstack start
```

To deploy the sample application, run the following command:

```shell
make deploy
```

The deployment will create:

- S3 buckets for shipment pictures and Lambda code
- DynamoDB table pre-populated with sample shipments
- Lambda function for image validation and processing
- SNS topic and SQS queue for messaging
- All necessary IAM roles and permissions


## Testing

The sample application provides both automated tests and interactive usage through the web interface.

Start the React frontend:

```shell
cd shipment-list-frontend
npm start
```

The frontend will be available at `http://localhost:3000`.

Start the Spring Boot backend:

```shell
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The backend will be available at `http://localhost:8081`.

You can run full end-to-end integration tests using the following command:

```shell
make test
```


### Using the Application

Once both frontend and backend are running:

1. Visit `http://localhost:3000` to see the shipment list
2. Upload images to shipments using the web interface
3. Valid images will be processed and watermarked by the Lambda function
4. Invalid files will be rejected and replaced with a placeholder
5. Real-time updates are delivered via Server-Sent Events when image processing completes

Available actions:

- Upload new images to existing shipments
- Delete shipments from the list
- View processed images with watermarks
- Create and update shipments via API endpoints


## Use Cases

### IaC Testing

This sample demonstrates Infrastructure as Code (IaC) testing by using identical Terraform configurations for both AWS and LocalStack environments. The application leverages Spring profiles to seamlessly switch between production and development configurations without code changes.

The Terraform configuration defines all necessary AWS resources and their relationships, while `tflocal` automatically reconfigures endpoints for LocalStack. This approach enables:

- Validation of infrastructure changes before AWS deployment
- Consistent development environments across teams
- Faster iteration cycles during development
- Cost-effective testing of AWS integrations

### AWS Parity

The sample showcases LocalStack's AWS parity by demonstrating identical behavior between LocalStack and AWS environments:

- S3 trigger configurations work identically in both environments
- Lambda function execution and environment variable handling
- DynamoDB enhanced client operations and table management
- SNS/SQS messaging patterns and subscription handling
- IAM role and policy enforcement

The application uses Spring Boot profiles (`dev` for LocalStack, `prod` for AWS) with different endpoint configurations (`application-prod.yml`,  `application-dev.yml`), ensuring the same codebase works across both environments. Testcontainers integration provides additional validation that the LocalStack environment accurately emulates AWS behavior.

## Summary

This sample application demonstrates how to build, test, and deploy a full-stack serverless application using AWS services and LocalStack. It showcases the following patterns:

- Defining and deploying S3, Lambda, DynamoDB, SNS, and SQS resources using Terraform.
- Building a Lambda function for automated image processing with watermarking capabilities.  
- Integrating multiple AWS services in a Spring Boot application with reactive messaging.
- Using Spring Boot profiles to seamlessly switch between LocalStack and AWS environments.
- Implementing real-time updates using Server-Sent Events and SQS message consumption.
- Leveraging Testcontainers for integration testing against LocalStack infrastructure.
- Utilizing `tflocal` and `awslocal` to streamline local development workflows.

## Learn More

- [Smooth transition from AWS to LocalStack for your DEV environment](https://hashnode.localstack.cloud/smooth-transition-from-aws-to-localstack-for-your-dev-environment) (**recommended**)
- [LocalStack and AWS Parity explained](https://blog.localstack.cloud/2022-08-04-parity-explained/)
- [Using AWS SDKs with LocalStack](https://docs.localstack.cloud/aws/integrations/aws-sdks/)
- [Deploying Terraform with LocalStack](https://docs.localstack.cloud/user-guide/integrations/terraform/)
- [Testcontainers with LocalStack](https://docs.localstack.cloud/user-guide/integrations/testcontainers/) 

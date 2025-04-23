terraform {
  required_providers {
    random = {
      source = "hashicorp/random"
      version = "~> 3.6.2"
    }
  }
}

provider "aws" {
  region = local.aws_region
}

provider "random" {
}

resource "random_pet" "random_name" {
  length    = 2
  separator = "-"
}

variable "account_id" {
  description = "AWS Account ID"
  default     = "932043840972"
}

variable "sns_topic_name" {
  description = "SNS Topic Name"
  default     = "update_shipment_picture_topic"
}

# S3 bucket
resource "aws_s3_bucket" "shipment_picture_bucket" {
  bucket        = "shipment-picture-bucket-${random_pet.random_name.id}"
  force_destroy = true
  lifecycle {
    prevent_destroy = false
  }
}

# DynamoDB table creation
resource "aws_dynamodb_table" "shipment" {
  name           = "shipment"
  read_capacity  = 10
  write_capacity = 5

  attribute {
    name = "shipmentId"
    type = "S"
  }
  hash_key = "shipmentId"
  server_side_encryption {
    enabled = true
  }

  stream_enabled   = true
  stream_view_type = "NEW_AND_OLD_IMAGES"
}

# Populate the table
resource "aws_dynamodb_table_item" "shipment" {
  for_each   = local.tf_data
  table_name = aws_dynamodb_table.shipment.name
  hash_key   = "shipmentId"
  item       = jsonencode(each.value)
}

# Define a bucket for the lambda zip
resource "aws_s3_bucket" "lambda_code_bucket" {
  bucket        = "shipment-picture-lambda-validator-bucket-${random_pet.random_name.id}"
  force_destroy = true
  lifecycle {
    prevent_destroy = false
  }
}

# Lambda source code
resource "aws_s3_object" "lambda_code" {
  bucket = aws_s3_bucket.lambda_code_bucket.id
  key    = "shipment-picture-lambda-validator.jar"
  source = "../shipment-picture-lambda-validator/target/shipment-picture-lambda-validator.jar"
}

# Lambda definition
resource "aws_lambda_function" "shipment_picture_lambda_validator" {
  function_name = "shipment-picture-lambda-validator"
  handler       = "dev.ancaghenade.shipmentpicturelambdavalidator.ServiceHandler::handleRequest"
  runtime       = "java17"
  role          = aws_iam_role.lambda_exec.arn
  s3_bucket     = aws_s3_bucket.lambda_code_bucket.id
  s3_key        = aws_s3_object.lambda_code.key
  memory_size   = 512
  timeout       = 60
  environment {
    variables = {
      BUCKET = aws_s3_bucket.shipment_picture_bucket.bucket
      SNS_TOPIC_ARN_DEV = local.sns_topic_arn_dev
      SNS_TOPIC_ARN_PROD = local.sns_topic_arn_prod
    }
  }
}

# Define trigger for S3
resource "aws_s3_bucket_notification" "demo_bucket_notification" {
  bucket = aws_s3_bucket.shipment_picture_bucket.id
  lambda_function {
    lambda_function_arn = aws_lambda_function.shipment_picture_lambda_validator.arn
    events              = ["s3:ObjectCreated:*"]
  }
}

# Give S3 permission to call Lambda
resource "aws_lambda_permission" "s3_lambda_exec_permission" {
  statement_id  = "AllowExecutionFromS3Bucket"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.shipment_picture_lambda_validator.function_name
  principal     = "s3.amazonaws.com"
  source_arn    = aws_s3_bucket.shipment_picture_bucket.arn
}

# Define role to execute Lambda
resource "aws_iam_role" "lambda_exec" {
  name = "lambda_exec_role"

  assume_role_policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "lambda.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
EOF
}


# Attach policy (S3 access) to Lambda role
resource "aws_iam_role_policy_attachment" "lambda_exec_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonS3FullAccess"
  role       = aws_iam_role.lambda_exec.name
}

# Define IAM role policy that grants permissions to access & process on AWS CloudWatch Logs, S3
resource "aws_iam_role_policy" "lambda_exec_policy" {
  name = "lambda_exec_policy"
  role = aws_iam_role.lambda_exec.id

  policy = <<EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
              "logs:CreateLogGroup",
              "logs:CreateLogStream",
              "logs:PutLogEvents"
            ],
            "Resource": "arn:aws:logs:*:*:*"
          },
          {
            "Effect": "Allow",
            "Action": [
              "s3:GetObject",
              "s3:PutObject",
              "sns:Publish"
            ],
            "Resource": [
              "arn:aws:s3:::shipment-picture-bucket-${random_pet.random_name.id}",
              "arn:aws:s3:::shipment-picture-bucket-${random_pet.random_name.id}/*",
              "${aws_sns_topic.update_shipment_picture_topic.arn}"
            ]
          }
          ]
          }
          EOF
}

# Define the topic
resource "aws_sns_topic" "update_shipment_picture_topic" {
  name = "${var.sns_topic_name}"
}

# Define the queue
resource "aws_sqs_queue" "update_shipment_picture_queue" {
  name = "update_shipment_picture_queue"
}

# Define subscription
resource "aws_sns_topic_subscription" "shipment_app_subscription" {
  topic_arn = aws_sns_topic.update_shipment_picture_topic.arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.update_shipment_picture_queue.arn
}


# Define policy to allow SNS to send message to SQS
resource "aws_sqs_queue_policy" "shipment_app_queue_policy" {
  queue_url = aws_sqs_queue.update_shipment_picture_queue.id

  policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowSNSSendMessage",
      "Effect": "Allow",
      "Principal": "*",
      "Action": "sqs:SendMessage",
      "Resource": "${aws_sqs_queue.update_shipment_picture_queue.arn}",
      "Condition": {
        "ArnEquals": {
          "aws:SourceArn": "${aws_sns_topic.update_shipment_picture_topic.arn}"
        }
      }
    }
  ]
}
EOF
}

# Define the SQS subscription
resource "aws_sns_topic_subscription" "shipment_app_topic_subscription" {
  topic_arn = aws_sns_topic.update_shipment_picture_topic.arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.update_shipment_picture_queue.arn

  # Additional subscription attributes
#  raw_message_delivery = true
  filter_policy        = ""
  delivery_policy      = ""

  # Ensure the subscription is confirmed automatically
  confirmation_timeout_in_minutes = 1
}

# save generated bucket name to properties file
resource "local_file" "properties_file" {
  content = <<-EOT
    shipment-picture-bucket=${aws_s3_bucket.shipment_picture_bucket.bucket}
    shipment-picture-bucket-validator=${aws_s3_bucket.lambda_code_bucket.bucket}
  EOT
  depends_on = [aws_s3_bucket.shipment_picture_bucket]

  filename = "../src/main/resources/buckets.properties"
}



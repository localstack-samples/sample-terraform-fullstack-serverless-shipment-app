export AWS_ACCESS_KEY_ID ?= test
export AWS_SECRET_ACCESS_KEY ?= test
export AWS_DEFAULT_REGION=us-east-1
SHELL := /bin/bash

## Show this help
usage:
		@fgrep -h "##" $(MAKEFILE_LIST) | fgrep -v fgrep | sed -e 's/\\$$//' | sed -e 's/##//'

## Check if all required prerequisites are installed
check:
	@command -v docker > /dev/null 2>&1 || { echo "Docker is not installed. Please install Docker and try again."; exit 1; }
	@command -v node > /dev/null 2>&1 || { echo "Node.js is not installed. Please install Node.js and try again."; exit 1; }
	@command -v localstack > /dev/null 2>&1 || { echo "LocalStack is not installed. Please install LocalStack and try again."; exit 1; }
	@command -v terraform > /dev/null 2>&1 || { echo "Terraform is not installed. Please install Terraform and try again."; exit 1; }
	@command -v tflocal > /dev/null 2>&1 || { echo "tflocal is not installed. Please install tflocal and try again."; exit 1; }
	@command -v mvn > /dev/null 2>&1 || { echo "Maven is not installed. Please install Maven and try again."; exit 1; }
	@command -v java > /dev/null 2>&1 || { echo "Java is not installed. Please install Java and try again."; exit 1; }
	@echo "All required prerequisites are available."
	
## Install dependencies
install:
	@echo "Installing dependencies..."
	cd shipment-picture-lambda-validator && mvn clean package shade:shade && cd ..
	@if [ ! -d "shipment-list-frontend/node_modules" ]; then \
		echo "node_modules not found. Running npm install..."; \
		cd shipment-list-frontend && npm install; \
	fi
	@echo "All required dependencies are now available."

## Deploy the infrastructure
deploy:
	@echo "Deploying the infrastructure..."
	cd terraform && tflocal init && tflocal plan && tflocal apply --auto-approve
	@echo "Infrastructure deployed successfully."

## Test the application
test:
	@echo "Running the tests..."
	mvn test -Dtest=dev.ancaghenade.shipmentlistdemo.integrationtests.ShipmentServiceIntegrationTest
	@echo "Tests run successfully."

## Run the application
run:
	@echo "Running the Spring Boot application..."
	mvn spring-boot:run -Dspring-boot.run.profiles=dev 

## Run the React application
frontend:
	@echo "Running the React application..."
	cd shipment-list-frontend && npm start

## Start LocalStack in detached mode
start:
		localstack start -d

## Stop the Running LocalStack container
stop:
		@echo
		localstack stop

## Make sure the LocalStack container is up
ready:
		@echo Waiting on the LocalStack container...
		@localstack wait -t 30 && echo LocalStack is ready to use! || (echo Gave up waiting on LocalStack, exiting. && exit 1)

## Save the logs in a separate file
logs:
		@localstack logs > logs.txt

.PHONY: usage install check start ready deploy logs stop test

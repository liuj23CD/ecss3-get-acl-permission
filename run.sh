#!/bin/bash

# Set environment variables
export ECS_S3_ACCESS_KEY="object_user1"
export ECS_S3_SECRET_KEY="voqyWj0auE+0VdgntbzJt/paOitCVGt9Jzh9G3rV"
# export ECS_S3_SECRET_KEY="ChangeMeChangeMeChangeMeChangeMeChangeMe"
export ECS_S3_ENDPOINT="http://192.168.31.200:9020"
export ECS_S3_BUCKET="test1"

# Build and run with Maven
mvn clean package
java -jar target/ecs-get-acl-1.0-SNAPSHOT.jar

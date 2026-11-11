<<<<<<< HEAD
# ECS S3 ACL Reader

This Java program reads and displays the ACL (Access Control List) for an S3 bucket and all objects within it.

## Prerequisites

- Java 8 or higher
- Maven 3.6 or higher

## Configuration

Set the following environment variables before running:

```bash
export ECS_S3_ACCESS_KEY="your_access_key"
export ECS_S3_SECRET_KEY="your_secret_key"
export ECS_S3_ENDPOINT="http://your_ecs_endpoint:port"
export ECS_S3_BUCKET="your_bucket_name"
```

Or copy `.env.example` to `.env` and fill in the values.

## Building

```bash
mvn clean package
```

## Running

### Using the provided script:
```bash
./run.sh
```

### Manual run:
```bash
# Set environment variables first
export ECS_S3_ACCESS_KEY="object_admin1"
export ECS_S3_SECRET_KEY="ChangeMeChangeMeChangeMeChangeMeChangeMe"
export ECS_S3_ENDPOINT="http://192.168.31.200:9020"
export ECS_S3_BUCKET="test1"

# Run
java -jar target/ecs-get-acl-1.0-SNAPSHOT.jar
```

## Output

The program will display:
1. Bucket ACL with owner and grants
2. All objects in the bucket with their respective ACLs

## Dependencies

- AWS SDK for Java S3 (com.amazonaws:aws-java-sdk-s3)
=======
# ecss3-get-acl-permission
>>>>>>> 7782b8d4037266c41852e6c682dd5fbe86215177

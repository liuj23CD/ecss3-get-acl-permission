import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.model.AccessControlList;
import com.amazonaws.services.s3.model.Grant;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.handlers.HandlerChainFactory;
import com.amazonaws.http.HttpResponseHandler;
import com.amazonaws.http.ExecutionContext;

import java.util.List;

/**
 * Main class to read S3 bucket and object ACLs
 */
public class Main {

    public static void main(String[] args) {
        // Read configuration from environment variables
        String accessKey = System.getenv("ECS_S3_ACCESS_KEY");
        String secretKey = System.getenv("ECS_S3_SECRET_KEY");
        String endpoint = System.getenv("ECS_S3_ENDPOINT");
        String bucketName = System.getenv("ECS_S3_BUCKET");

        // Validate configuration
        if (accessKey == null || accessKey.isEmpty()) {
            System.err.println("Error: ECS_S3_ACCESS_KEY environment variable is not set");
            System.exit(1);
        }
        if (secretKey == null || secretKey.isEmpty()) {
            System.err.println("Error: ECS_S3_SECRET_KEY environment variable is not set");
            System.exit(1);
        }
        if (endpoint == null || endpoint.isEmpty()) {
            System.err.println("Error: ECS_S3_ENDPOINT environment variable is not set");
            System.exit(1);
        }
        if (bucketName == null || bucketName.isEmpty()) {
            System.err.println("Error: ECS_S3_BUCKET environment variable is not set");
            System.exit(1);
        }

        System.out.println("=== ECS S3 ACL Reader ===");
        System.out.println("Endpoint: " + endpoint);
        System.out.println("Bucket: " + bucketName);
        System.out.println();

        try {
            // Create S3 client
            AmazonS3 s3Client = createS3Client(accessKey, secretKey, endpoint);

            // Read bucket ACL
            readBucketACL(s3Client, bucketName);

            // Read all objects and their ACLs
            readAllObjectsACL(s3Client, bucketName);

            System.out.println("\n=== Done ===");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Create AmazonS3 client with provided credentials
     */
    private static AmazonS3 createS3Client(String accessKey, String secretKey, String endpoint) {
        BasicAWSCredentials awsCredentials = new BasicAWSCredentials(accessKey, secretKey);

        ClientConfiguration clientConfig = new ClientConfiguration();
        clientConfig.setConnectionTimeout(10000); // 10 seconds
        clientConfig.setSocketTimeout(60000);     // 60 seconds
        clientConfig.setMaxErrorRetry(3);
        clientConfig.setSignerOverride("S3SignerType");

        return AmazonS3ClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint, "us-east-1"))
                .withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
                .withPathStyleAccessEnabled(true)
                .withClientConfiguration(clientConfig)
                .build();
    }

    /**
     * Read and display bucket ACL
     */
    private static void readBucketACL(AmazonS3 s3Client, String bucketName) {
        System.out.println("--- Bucket ACL: " + bucketName + " ---");

        try {
            // Get bucket ACL
            AccessControlList acl = s3Client.getBucketAcl(bucketName);

            // Display owner
            System.out.println("Owner: " + acl.getOwner().getDisplayName());

            // Display grants
            acl.getGrants().forEach(grant -> {
                System.out.println("  " + grant.toString());
            });

        } catch (Exception e) {
            System.err.println("Error reading bucket ACL: " + e.getMessage());
        }
    }

    /**
     * List all objects in bucket and read their ACLs
     */
    private static void readAllObjectsACL(AmazonS3 s3Client, String bucketName) {
        System.out.println("\n--- Object ACLs ---");

        ListObjectsV2Request request = new ListObjectsV2Request()
                .withBucketName(bucketName);

        ListObjectsV2Result result;
        do {
            result = s3Client.listObjectsV2(request);

            List<S3ObjectSummary> objects = result.getObjectSummaries();
            System.out.println("Found " + objects.size() + " objects in this batch");

            for (S3ObjectSummary obj : objects) {
                String key = obj.getKey();
                System.out.println("\nObject: " + key);

                try {
                    // Get object ACL
                    AccessControlList acl = s3Client.getObjectAcl(bucketName, key);

                    // Display owner
                    System.out.println("  Owner: " + acl.getOwner().getDisplayName());

                    // Display grants
                    acl.getGrants().forEach(grant -> {
                        System.out.println("  " + grant.toString());
                    });

                } catch (Exception e) {
                    System.err.println("Error reading object ACL: " + e.getMessage());
                }
            }

            // Get the next batch if there are more objects
            request.setContinuationToken(result.getNextContinuationToken());

        } while (result.isTruncated());

        System.out.println("\n--- End of Object ACLs ---");
    }
}

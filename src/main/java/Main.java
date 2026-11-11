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
import com.amazonaws.services.s3.model.Grantee;
import com.amazonaws.services.s3.model.CanonicalGrantee;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.handlers.HandlerChainFactory;
import com.amazonaws.http.HttpResponseHandler;
import com.amazonaws.http.ExecutionContext;

import java.util.List;
import java.io.FileWriter;
import java.io.IOException;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

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

        // Check for resume option
        boolean resume = false;
        String resumeFile = null;
        String continuationToken = null;
        
        for (String arg : args) {
            if (arg.equals("--resume")) {
                resume = true;
            } else if (arg.startsWith("--resume-file=")) {
                resumeFile = arg.substring("--resume-file=".length());
            }
        }
        
        // Generate output file name with timestamp
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String outputFileName = "acl_output_" + timestamp + ".csv";
        
        // If resuming, use the original output file
        if (resume && resumeFile != null && new File(resumeFile).exists()) {
            outputFileName = resumeFile;
            // Read continuation token from resume file
            continuationToken = readContinuationToken(resumeFile + ".resume");
            if (continuationToken != null) {
                System.out.println("Resuming from token: " + continuationToken);
            }
        }
        
        try (FileWriter fw = new FileWriter(outputFileName, resume)) {
            // Write CSV header if not resuming
            if (!resume) {
                fw.write("Type,Key,GranteeID,DisplayName,Permission\n");
            }
            
            // Create S3 client
            AmazonS3 s3Client = createS3Client(accessKey, secretKey, endpoint);

            // Read bucket ACL (only if not resuming)
            if (!resume) {
                readBucketACL(s3Client, bucketName, fw);
            }

            // Read all objects and their ACLs
            readAllObjectsACL(s3Client, bucketName, fw, outputFileName, continuationToken);

            System.out.println("\n=== Done ===");
            System.out.println("Output saved to: " + outputFileName);
            
            // Clean up resume file on success
            if (resume && new File(outputFileName + ".resume").exists()) {
                new File(outputFileName + ".resume").delete();
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Read continuation token from resume file
     */
    private static String readContinuationToken(String resumeFileName) {
        File file = new File(resumeFileName);
        if (!file.exists()) {
            return null;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine();
            if (line != null && line.startsWith("continuationToken=")) {
                return line.substring("continuationToken=".length());
            }
        } catch (IOException e) {
            System.err.println("Warning: Failed to read resume file: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Write continuation token to resume file
     */
    private static void writeContinuationToken(String resumeFileName, String token) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(resumeFileName))) {
            writer.write("continuationToken=" + token);
        } catch (IOException e) {
            System.err.println("Warning: Failed to write resume file: " + e.getMessage());
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
    private static void readBucketACL(AmazonS3 s3Client, String bucketName, FileWriter fw) throws IOException {
        System.out.println("--- Bucket ACL: " + bucketName + " ---");

        // Get bucket ACL
        AccessControlList acl = s3Client.getBucketAcl(bucketName);

        // Display owner
        System.out.println("Owner: " + acl.getOwner().getDisplayName());

        // Write bucket ACL to file
        fw.write("Bucket," + bucketName + "," + escapeCsv(acl.getOwner().getId()) + "," + escapeCsv(acl.getOwner().getDisplayName()) + ",OWNER\n");

        // Display grants
        acl.getGrants().forEach(grant -> {
            System.out.println("  Grant:");
            System.out.println("    Grantee: " + grant.getGrantee());
            if (grant.getGrantee() instanceof CanonicalGrantee) {
                CanonicalGrantee canonicalGrantee = (CanonicalGrantee) grant.getGrantee();
                System.out.println("      ID: " + canonicalGrantee.getIdentifier());
                System.out.println("      Display Name: " + canonicalGrantee.getDisplayName());
            }
            System.out.println("    Permission: " + grant.getPermission());
            
            // Write grant to file
            try {
                if (grant.getGrantee() instanceof CanonicalGrantee) {
                    CanonicalGrantee canonicalGrantee = (CanonicalGrantee) grant.getGrantee();
                    fw.write("Bucket," + bucketName + "," + canonicalGrantee.getIdentifier() + "," + escapeCsv(canonicalGrantee.getDisplayName()) + "," + grant.getPermission() + "\n");
                }
            } catch (IOException e) {
                System.err.println("Error writing to file: " + e.getMessage());
            }
        });
    }

    /**
     * List all objects in bucket and read their ACLs
     */
    private static void readAllObjectsACL(AmazonS3 s3Client, String bucketName, FileWriter fw, String outputFileName, String continuationToken) {
        System.out.println("\n--- Object ACLs ---");

        ListObjectsV2Request request = new ListObjectsV2Request()
                .withBucketName(bucketName);
        
        // Set continuation token if resuming
        if (continuationToken != null && !continuationToken.isEmpty()) {
            request.setContinuationToken(continuationToken);
        }

        ListObjectsV2Result result;
        long totalObjects = 0;
        int batchNum = 0;
        String lastContinuationToken = null;
        
        do {
            batchNum++;
            result = s3Client.listObjectsV2(request);

            List<S3ObjectSummary> objects = result.getObjectSummaries();
            System.out.println("Batch " + batchNum + ": Found " + objects.size() + " objects (Total: " + (totalObjects + objects.size()) + ")");
            totalObjects += objects.size();

            for (S3ObjectSummary obj : objects) {
                String key = obj.getKey();
                
                // Progress indicator every 1000 objects
                if (totalObjects % 1000 == 0) {
                    System.out.println("Processed " + totalObjects + " objects...");
                }

                try {
                    // Get object ACL
                    AccessControlList acl = s3Client.getObjectAcl(bucketName, key);

                    // Write object owner to file
                    fw.write("Object," + escapeCsv(key) + "," + escapeCsv(acl.getOwner().getId()) + "," + escapeCsv(acl.getOwner().getDisplayName()) + ",OWNER\n");

                    // Display owner
                    System.out.println("Object: " + key);
                    System.out.println("  Owner: " + acl.getOwner().getDisplayName());

                    // Display grants
                    acl.getGrants().forEach(grant -> {
                        System.out.println("  Grant:");
                        System.out.println("    Grantee: " + grant.getGrantee());
                        if (grant.getGrantee() instanceof CanonicalGrantee) {
                            CanonicalGrantee canonicalGrantee = (CanonicalGrantee) grant.getGrantee();
                            System.out.println("      ID: " + canonicalGrantee.getIdentifier());
                            System.out.println("      Display Name: " + canonicalGrantee.getDisplayName());
                        }
                        System.out.println("    Permission: " + grant.getPermission());
                        
                        // Write grant to file
                        try {
                            if (grant.getGrantee() instanceof CanonicalGrantee) {
                                CanonicalGrantee canonicalGrantee = (CanonicalGrantee) grant.getGrantee();
                                fw.write("Object," + escapeCsv(key) + "," + canonicalGrantee.getIdentifier() + "," + escapeCsv(canonicalGrantee.getDisplayName()) + "," + grant.getPermission() + "\n");
                            }
                        } catch (IOException e) {
                            System.err.println("Error writing to file: " + e.getMessage());
                        }
                    });

                } catch (Exception e) {
                    System.err.println("Error reading object ACL: " + e.getMessage());
                }
            }

            // Save continuation token for resume
            lastContinuationToken = result.getNextContinuationToken();
            if (lastContinuationToken != null) {
                writeContinuationToken(outputFileName + ".resume", lastContinuationToken);
            }

            // Get the next batch if there are more objects
            request.setContinuationToken(lastContinuationToken);

        } while (result.isTruncated());

        System.out.println("\n--- Total objects processed: " + totalObjects + " ---");
        System.out.println("--- End of Object ACLs ---");
    }
    
    /**
     * Escape CSV field values (handle commas, quotes, newlines)
     */
    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        // If value contains comma, quote, or newline, wrap in quotes and escape quotes
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}

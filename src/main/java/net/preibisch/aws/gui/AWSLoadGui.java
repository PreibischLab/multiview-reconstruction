package net.preibisch.aws.gui;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.AmazonS3URI;
import com.bigdistributor.aws.data.CredentialConfig;
import com.bigdistributor.aws.data.CredentialSupplier;
import com.bigdistributor.aws.dataexchange.aws.s3.func.auth.AWSCredentialInstance;
import com.bigdistributor.aws.dataexchange.aws.s3.func.bucket.S3BucketInstance;
import com.bigdistributor.aws.spimloader.AWSSpimLoader;
import com.bigdistributor.aws.utils.AWS_DEFAULT;
import fiji.util.gui.GenericDialogPlus;
import net.preibisch.aws.spimloader.AWSLoadParseQueryXML;
import net.preibisch.aws.tools.AWSDataParam;
import net.preibisch.aws.tools.TempFolder;


public class AWSLoadGui {
    private final static String defaultUri = "s3://mzouink-test/dataset-n5.xml";
    private final static String defaultKeyPath = "/Users/Marwan/Desktop/BigDistributer/aws_credentials/bigdistributer.csv";

    private final static String defaultExtra = "interestpoints";
    private final static String defaultRegion = "eu-central-1";
    private AWSLoadParseQueryXML result;


    public boolean readData() {
        final GenericDialogPlus gd = new GenericDialogPlus("AWS Input");
        gd.addFileField("Key: ", defaultKeyPath, 45);
        gd.addMessage("");
        gd.addStringField("Input uri: ", defaultUri, 30);
        gd.addMessage("");
//        gd.addStringField("Extras: ", defaultExtra, 30);
        gd.addStringField("Region: ", defaultRegion, 30);

        gd.showDialog();

        if (gd.wasCanceled())
            return false;

        String keyPath = gd.getNextString();
        String uri = gd.getNextString();
//        String extrasField = gd.getNextString();
        String regionField = gd.getNextString();

//        String[] extras = new String[]{};
//        if(!extrasField.isEmpty()) extras = extrasField.split(",");

        AWSCredentials credentials = AWSCredentialInstance.init(keyPath).get();
        AmazonS3 s3client = AmazonS3ClientBuilder
                .standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(Regions.fromName(regionField))
                .build();

        AWSSpimLoader.init(s3client, uri);
        CredentialConfig.set(new CredentialSupplier(credentials.getAWSAccessKeyId(), credentials.getAWSSecretKey(),regionField));
//        AWSDataParam params = AWSDataParam.init(bucketName, path, xmlFile, Regions.fromName(regionField), extras);
           result = new AWSLoadParseQueryXML();

        if (!result.queryXML(s3client, TempFolder.get(),new AmazonS3URI(uri))) {
            return false;
        }
        AWSDataParam.setCloudMode(true);
        this.result = result;
//
        return true;


    }

    public AWSLoadParseQueryXML getResult() {
        return result;
    }

    public static void main(String[] args) {
        new AWSLoadGui().readData();
    }

}

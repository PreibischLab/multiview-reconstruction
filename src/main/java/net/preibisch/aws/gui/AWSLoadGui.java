package net.preibisch.aws.gui;

import com.amazonaws.regions.Regions;
import com.bigdistributor.aws.dataexchange.aws.s3.func.auth.AWSCredentialInstance;
import com.bigdistributor.aws.dataexchange.aws.s3.func.bucket.S3BucketInstance;
import fiji.util.gui.GenericDialogPlus;
import net.preibisch.aws.spimloader.AWSLoadParseQueryXML;
import net.preibisch.aws.tools.AWSDataParam;
import net.preibisch.aws.tools.TempFolder;


public class AWSLoadGui {
    private final static String defaultBucketName = "bigstitcher";
    private final static String defaultKeyPath = "/Users/Marwan/Desktop/BigDistributer/aws_credentials/bigdistributer.csv";
    private final static String defaultPath = "data";
    private final static String defaultFileName = "dataset-n5.xml";
    private final static String defaultExtra = "interestpoints";
    private final static String defaultRegion = "eu-central-1";
    private AWSLoadParseQueryXML result;


    public boolean readData() {
        final GenericDialogPlus gd = new GenericDialogPlus("AWS Input");
        gd.addFileField("Key: ", defaultKeyPath, 45);
        gd.addMessage("");
        gd.addStringField("Input uri: ", defaultBucketName, 30);
        gd.addStringField("Path: ", defaultPath, 30);
        gd.addStringField("XML File: ", defaultFileName, 30);
        gd.addStringField("Extras: ", defaultExtra, 30);
        gd.addStringField("Region: ", defaultRegion, 30);

        gd.showDialog();

        if (gd.wasCanceled())
            return false;

        String keyPath = gd.getNextString();
        String bucketName = gd.getNextString();
        String path = gd.getNextString();
        String xmlFile = gd.getNextString();
        String extrasField = gd.getNextString();
        String regionField = gd.getNextString();

        AWSLoadParseQueryXML result = null;
        AWSCredentialInstance.init(keyPath);
        S3BucketInstance.init(AWSCredentialInstance.get(), Regions.fromName(regionField),bucketName,path);
        String[] extras = new String[]{};
        if(!extrasField.isEmpty()) extras = extrasField.split(",");

        AWSDataParam params = AWSDataParam.init(bucketName, path, xmlFile, Regions.fromName(regionField), extras);
           result = new AWSLoadParseQueryXML();

        try {
            if (!result.queryXML(S3BucketInstance.get(), TempFolder.get(),params)) {
                return false;
            }
        } catch (IllegalAccessException e) {
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

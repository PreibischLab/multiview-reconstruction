package net.preibisch.aws.spimloader;


import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3URI;
import com.bigdistributor.aws.dataexchange.aws.s3.S3Utils;
import com.bigdistributor.aws.dataexchange.aws.s3.func.bucket.S3BucketInstance;
import mpicbg.spim.data.SpimDataException;
import net.preibisch.aws.tools.AWSDataParam;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;

import java.io.File;
import java.io.IOException;

public class AWSLoadParseQueryXML extends LoadParseQueryXML {

    public boolean queryXML(AmazonS3 s3, File tmpFolder, AmazonS3URI uri) {
        try {
            this.xmlfilename = uri.getKey();
            this.io = new XmlIoSpimData2("");

            this.data = io.load(S3Utils.download(s3,tmpFolder,uri.getURI().toString()).getAbsolutePath());
//            s3.downloadFrom(tmpFolder, params.getPath(), params.getExtraFiles());
        } catch (SpimDataException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
}

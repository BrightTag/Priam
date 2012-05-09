package com.netflix.priam.defaultimpl;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.netflix.priam.IConfiguration;
import com.netflix.priam.ICredential;
import com.netflix.priam.aws.AWSMembership;
import com.netflix.priam.aws.S3BackupPath;
import com.netflix.priam.aws.S3FileSystem;
import com.netflix.priam.aws.SDBInstanceFactory;
import com.netflix.priam.aws.UpdateSecuritySettings;
import com.netflix.priam.backup.AbstractBackupPath;
import com.netflix.priam.backup.IBackupFileSystem;
import com.netflix.priam.compress.ICompression;
import com.netflix.priam.compress.SnappyCompression;
import com.netflix.priam.identity.IMembership;
import com.netflix.priam.identity.IPriamInstanceFactory;
import com.netflix.priam.utils.Sleeper;
import com.netflix.priam.utils.ThreadSleeper;

import org.quartz.SchedulerFactory;
import org.quartz.impl.StdSchedulerFactory;

public class PriamGuiceModule extends AbstractModule
{
    @Override
    protected void configure()
    {
        bind(SchedulerFactory.class).to(StdSchedulerFactory.class).asEagerSingleton();
        bind(IConfiguration.class).to(PriamConfiguration.class).asEagerSingleton();
        bind(IPriamInstanceFactory.class).to(SDBInstanceFactory.class);
        bind(IMembership.class).to(AWSMembership.class);
        bind(ICredential.class).to(ClearCredential.class);
        bind(IBackupFileSystem.class).to(S3FileSystem.class);
        bind(AbstractBackupPath.class).to(S3BackupPath.class);
        bind(ICompression.class).to(SnappyCompression.class);
        bind(Sleeper.class).to(ThreadSleeper.class);
    }

    @Provides // todo: @Singleton
    AmazonAutoScaling provideAutoScalingClient(IConfiguration config, AWSCredentials awsCredentials)
    {
        AmazonAutoScaling client = new AmazonAutoScalingClient(awsCredentials);
        client.setEndpoint(getAwsEndpoint("autoscaling", config.getDC()));
        return client;
    }

    @Provides // todo: @Singleton
    AmazonEC2 provideEc2Client(IConfiguration config, AWSCredentials awsCredentials)
    {
        AmazonEC2 client = new AmazonEC2Client(awsCredentials);
        client.setEndpoint(getAwsEndpoint("ec2", config.getDC()));
        return client;
    }

    @Provides @Singleton
    AWSCredentials provideAwsCredentials(ICredential credential) {
        return new BasicAWSCredentials(credential.getAccessKeyId(), credential.getSecretAccessKey());
    }

    @Provides @Singleton @Named(UpdateSecuritySettings.PORT_BINDING)
    int provideIngressRulePort()
    {
        // todo : default to standard cassandra gossip port 7000
        return Integer.getInteger("priam.security.ingress_rule_port", 7103);
    }

    /*
     * For a complete list of AWS endpoints see:
     * http://docs.amazonwebservices.com/general/latest/gr/rande.html
     */
    private static final String getAwsEndpoint(String api, String region) {
        return api + "." + region + ".amazonaws.com";  
    }
}

package com.netflix.priam.defaultimpl;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.AvailabilityZone;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;
import com.amazonaws.services.simpledb.AmazonSimpleDBClient;
import com.amazonaws.services.simpledb.model.Attribute;
import com.amazonaws.services.simpledb.model.Item;
import com.amazonaws.services.simpledb.model.SelectRequest;
import com.amazonaws.services.simpledb.model.SelectResult;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.priam.IConfiguration;
import com.netflix.priam.ICredential;
import com.netflix.priam.utils.SystemUtils;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class PriamConfiguration implements IConfiguration
{
    private static final String PRIAM_PRE = "priam";

    private static final String CONFIG_CASS_HOME_DIR = PRIAM_PRE + ".cass.home";
    private static final String CONFIG_CASS_START_SCRIPT = PRIAM_PRE + ".cass.startscript";
    private static final String CONFIG_CASS_STOP_SCRIPT = PRIAM_PRE + ".cass.stopscript";
    private static final String CONFIG_CLUSTER_NAME = PRIAM_PRE + ".clustername";
    private static final String CONFIG_SEED_PROVIDER_NAME = PRIAM_PRE + ".seed.provider";
    private static final String CONFIG_LOAD_LOCAL_PROPERTIES = PRIAM_PRE + ".localbootstrap.enable";
    private static final String CONFIG_MAX_HEAP_SIZE = PRIAM_PRE + ".heap.size.";
    private static final String CONFIG_DATA_LOCATION = PRIAM_PRE + ".data.location";
    private static final String CONFIG_MR_ENABLE = PRIAM_PRE + ".multiregion.enable";
    private static final String CONFIG_CL_LOCATION = PRIAM_PRE + ".commitlog.location";
    private static final String CONFIG_JMX_LISTERN_PORT_NAME = PRIAM_PRE + ".jmx.port";
    private static final String CONFIG_AVAILABILITY_ZONES = PRIAM_PRE + ".zones.available";
    private static final String CONFIG_SAVE_CACHE_LOCATION = PRIAM_PRE + ".cache.location";
    private static final String CONFIG_NEW_MAX_HEAP_SIZE = PRIAM_PRE + ".heap.newgen.size.";
    private static final String CONFIG_DIRECT_MAX_HEAP_SIZE = PRIAM_PRE + ".direct.memory.size.";
    private static final String CONFIG_THRIFT_LISTERN_PORT_NAME = PRIAM_PRE + ".thrift.port";
    private static final String CONFIG_STORAGE_LISTERN_PORT_NAME = PRIAM_PRE + ".storage.port";
    private static final String CONFIG_CL_BK_LOCATION = PRIAM_PRE + ".backup.commitlog.location";
    private static final String CONFIG_THROTTLE_UPLOAD_PER_SECOND = PRIAM_PRE + ".upload.throttle";
    private static final String CONFIG_IN_MEMORY_COMPACTION_LIMIT = PRIAM_PRE + ".memory.compaction.limit";
    private static final String CONFIG_COMPACTION_THROUHPUT = PRIAM_PRE + ".compaction.throughput";
    private static final String CONFIG_MAX_HINT_WINDOW_IN_MS = PRIAM_PRE + ".hint.window";
    private static final String CONFIG_HINT_DELAY = PRIAM_PRE + ".hint.delay";
    private static final String CONFIG_BOOTCLUSTER_NAME = PRIAM_PRE + ".bootcluster";
    private static final String CONFIG_ENDPOINT_SNITCH = PRIAM_PRE + ".endpoint_snitch";
    private static final String CONFIG_MEMTABLE_TOTAL_SPACE = PRIAM_PRE + ".memtabletotalspace";

    // Backup and Restore
    private static final String CONFIG_BACKUP_THREADS = PRIAM_PRE + ".backup.threads";
    private static final String CONFIG_RESTORE_PREFIX = PRIAM_PRE + ".restore.prefix";
    private static final String CONFIG_INCR_BK_ENABLE = PRIAM_PRE + ".backup.incremental.enable";
    private static final String CONFIG_CL_BK_ENABLE = PRIAM_PRE + ".backup.commitlog.enable";
    private static final String CONFIG_AUTO_RESTORE_SNAPSHOTNAME = PRIAM_PRE + ".restore.snapshot";
    private static final String CONFIG_BUCKET_NAME = PRIAM_PRE + ".s3.bucket";
    private static final String CONFIG_BACKUP_HOUR = PRIAM_PRE + ".backup.hour";
    private static final String CONFIG_S3_BASE_DIR = PRIAM_PRE + ".s3.base_dir";
    private static final String CONFIG_RESTORE_THREADS = PRIAM_PRE + ".restore.threads";
    private static final String CONFIG_RESTORE_CLOSEST_TOKEN = PRIAM_PRE + ".restore.closesttoken";
    private static final String CONFIG_RESTORE_KEYSPACES = PRIAM_PRE + ".restore.keyspaces";
    private static final String CONFIG_BACKUP_CHUNK_SIZE = PRIAM_PRE + ".backup.chunksizemb";
    private static final String CONFIG_BACKUP_RETENTION = PRIAM_PRE + ".backup.retention";
    private static final String CONFIG_BACKUP_RACS = PRIAM_PRE + ".backup.racs";
    private static final String CONFIG_MULTITHREADED_COMPACTION = PRIAM_PRE + ".multithreaded.compaction";
    private static final String CONFIG_STREAMING_THROUGHPUT_MB = PRIAM_PRE + ".streaming.throughput.mb";
    private static final String CONFIG_SECURITY_GROUP_NAME = PRIAM_PRE + ".security.group.name";

    // Amazon specific
    private static final String CONFIG_ASG_NAME = PRIAM_PRE + ".az.asgname";
    private static final String CONFIG_REGION_NAME = PRIAM_PRE + ".az.region";
    private final String RAC = SystemUtils.getDataFromUrl("http://169.254.169.254/latest/meta-data/placement/availability-zone");
    private final String PUBLIC_HOSTNAME = SystemUtils.getDataFromUrl("http://169.254.169.254/latest/meta-data/public-hostname");
    private final String PUBLIC_IP = SystemUtils.getDataFromUrl("http://169.254.169.254/latest/meta-data/public-ipv4");
    private final String INSTANCE_ID = SystemUtils.getDataFromUrl("http://169.254.169.254/latest/meta-data/instance-id");
    private final String INSTANCE_TYPE = SystemUtils.getDataFromUrl("http://169.254.169.254/latest/meta-data/instance-type");
    private static String REGION = System.getenv("EC2_REGION");

    // Defaults 
    private final String DEFAULT_CLUSTER_NAME = "cass_cluster";
    private final String DEFAULT_DATA_LOCATION = "/var/lib/cassandra/data";
    private final String DEFAULT_COMMIT_LOG_LOCATION = "/var/lib/cassandra/commitlog";
    private final String DEFAULT_CACHE_LOCATION = "/var/lib/cassandra/saved_caches";
    private final String DEFULT_ENDPOINT_SNITCH = "org.apache.cassandra.locator.Ec2Snitch";
    private final String DEFAULT_SEED_PROVIDER = "com.netflix.priam.cassandra.NFSeedProvider";

    // rpm based. Can be modified for tar based.
    private final String DEFAULT_CASS_HOME_DIR = "/etc/cassandra";
    private final String DEFAULT_CASS_START_SCRIPT = "/etc/init.d/cassandra start";
    private final String DEFAULT_CASS_STOP_SCRIPT = "/etc/init.d/cassandra stop";
    private final String DEFAULT_BACKUP_LOCATION = "backup";
    private final String DEFAULT_BUCKET_NAME = "cassandra-archive";
    private final String DEFAULT_SECURITY_GROUP_NAME = "cass_cluster";
    private String DEFAULT_AVAILABILITY_ZONES = "";

    private final String DEFAULT_MAX_DIRECT_MEM = "50G";
    private final String DEFAULT_MAX_HEAP = "8G";
    private final String DEFAULT_MAX_NEWGEN_HEAP = "2G";
    private final int DEFAULT_JMX_PORT = 7199;
    private final int DEFAULT_THRIFT_PORT = 9160;
    private final int DEFAULT_STORAGE_PORT = 7000;
    private final int DEFAULT_BACKUP_HOUR = 12;
    private final int DEFAULT_BACKUP_THREADS = 2;
    private final int DEFAULT_RESTORE_THREADS = 8;
    private final int DEFAULT_BACKUP_CHUNK_SIZE = 10;
    private final int DEFAULT_BACKUP_RETENTION = 0;

    private PriamProperties config;
    private static final Logger logger = LoggerFactory.getLogger(PriamConfiguration.class);

    private static class Attributes
    {
        public final static String APP_ID = "appId";
        public final static String PROPERTY = "property";
        public final static String PROPERTY_VALUE = "value";
        public final static String REGION = "region";
    }

    private static final String DOMAIN = "PriamProperties";

    private static String ALL_QUERY = "select * from " + DOMAIN + " where " + Attributes.APP_ID + "='%s'";
    private final ICredential provider;

    @Inject
    public PriamConfiguration(ICredential provider)
    {
        this.provider = provider;
    }

    @Override
    public void intialize()
    {
        getEc2Region();
        setDefaultRACList(REGION);
        populateSimpleDBConfigurations();
        overrideSimpleDBConfigurationsWithSystemProperties();
        logger.debug("PriamConfiguration:\n" + config);
        SystemUtils.createDirs(getBackupCommitLogLocation());
        SystemUtils.createDirs(getCommitLogLocation());
        SystemUtils.createDirs(getCacheLocation());
        SystemUtils.createDirs(getDataFileLocation());
    }

    /*
     * HACK - This class requires much refactoring to allow more flexible configuration, for now just allow for
     * overriding config parameters in system properties vs requiring SimpleDB updates.
     */
    private void overrideSimpleDBConfigurationsWithSystemProperties()
    {
        for (Map.Entry entry : System.getProperties().entrySet()) {
            config.put(entry.getKey(), entry.getValue());
        }
    }

    private void getEc2Region()
    {
        // Search in java opt properties
        REGION = StringUtils.isBlank(REGION) ? System.getProperty("EC2_REGION") : REGION;
        // Infer from zone
        if (StringUtils.isBlank(REGION))
            REGION = RAC.substring(0, RAC.length() - 1);
        logger.info("REGION set to " + REGION);
    }
    
    /**
     * Get the fist 3 available zones in the region 
     */
    public void setDefaultRACList(String region){
        AmazonEC2 client = new AmazonEC2Client(new BasicAWSCredentials(provider.getAccessKeyId(), provider.getSecretAccessKey()));
        client.setEndpoint("ec2." + region + ".amazonaws.com");
        DescribeAvailabilityZonesResult res = client.describeAvailabilityZones();
        List<String> zone = Lists.newArrayList(); 
        for(AvailabilityZone reg : res.getAvailabilityZones()){
            if( reg.getState().equals("available") )
                zone.add(reg.getZoneName());
            if( zone.size() == 3)
                break;
        }
        DEFAULT_AVAILABILITY_ZONES =  StringUtils.join(zone, ",");
    }

    private void populateSimpleDBConfigurations()
    {
        String appid = System.getProperty("priam.simpledb.domain", "cassandra");
        // End point is us-east-1
        AmazonSimpleDBClient simpleDBClient = new AmazonSimpleDBClient(new BasicAWSCredentials(provider.getAccessKeyId(), provider.getSecretAccessKey()));
        config = new PriamProperties();
        config.put(CONFIG_REGION_NAME, REGION);
        String nextToken = null;
        do
        {
            SelectRequest request = new SelectRequest(String.format(ALL_QUERY, appid));
            request.setNextToken(nextToken);
            SelectResult result = simpleDBClient.select(request);
            nextToken = result.getNextToken();
            Iterator<Item> itemiter = result.getItems().iterator();
            while (itemiter.hasNext())
                addProperty(itemiter.next());

        } while (nextToken != null);
    }

    private void addProperty(Item item)
    {
        Iterator<Attribute> attrs = item.getAttributes().iterator();
        String prop = "";
        String value = "";
        String dc = "";
        while (attrs.hasNext())
        {
            Attribute att = attrs.next();
            if (att.getName().equals(Attributes.PROPERTY))
                prop = att.getValue();
            else if (att.getName().equals(Attributes.PROPERTY_VALUE))
                value = att.getValue();
            else if (att.getName().equals(Attributes.REGION))
                dc = att.getValue();
        }
        // Ignore, if not this region
        if (StringUtils.isNotBlank(dc) && !dc.equals(REGION))
            return;
        // Override only if region is specified
        if (config.contains(prop) && StringUtils.isBlank(dc))
            return;
        config.put(prop, value);
    }

    @Override
    public String getCassStartupScript()
    {
        return config.getProperty(CONFIG_CASS_START_SCRIPT, DEFAULT_CASS_START_SCRIPT);
    }

    @Override
    public String getCassStopScript()
    {
        return config.getProperty(CONFIG_CASS_STOP_SCRIPT, DEFAULT_CASS_STOP_SCRIPT);
    }

    @Override
    public String getCassHome()
    {
        return config.getProperty(CONFIG_CASS_HOME_DIR, DEFAULT_CASS_HOME_DIR);
    }

    @Override
    public String getBackupLocation()
    {
        return config.getProperty(CONFIG_S3_BASE_DIR, DEFAULT_BACKUP_LOCATION);
    }

    @Override
    public String getBackupPrefix()
    {
        return config.getProperty(CONFIG_BUCKET_NAME, DEFAULT_BUCKET_NAME);
    }

    @Override
    public int getBackupRetentionDays()
    {
        return config.getInteger(CONFIG_BACKUP_RETENTION, DEFAULT_BACKUP_RETENTION);
    }

    @Override
    public List<String> getBackupRacs()
    {
        return config.getList(CONFIG_BACKUP_RACS);
    }

    @Override
    public String getRestorePrefix()
    {
        return config.getProperty(CONFIG_RESTORE_PREFIX);
    }

    @Override
    public List<String> getRestoreKeySpaces()
    {
        return config.getList(CONFIG_RESTORE_KEYSPACES);
    }

    @Override
    public String getDataFileLocation()
    {
        return config.getProperty(CONFIG_DATA_LOCATION, DEFAULT_DATA_LOCATION);
    }

    @Override
    public String getCacheLocation()
    {
        return config.getProperty(CONFIG_SAVE_CACHE_LOCATION, DEFAULT_CACHE_LOCATION);
    }

    @Override
    public String getCommitLogLocation()
    {
        return config.getProperty(CONFIG_CL_LOCATION, DEFAULT_COMMIT_LOG_LOCATION);
    }

    @Override
    public String getBackupCommitLogLocation()
    {
        return config.getProperty(CONFIG_CL_BK_LOCATION, "");
    }

    @Override
    public long getBackupChunkSize()
    {
        long size = config.getLong(CONFIG_BACKUP_CHUNK_SIZE, DEFAULT_BACKUP_CHUNK_SIZE);
        return size*1024*1024L;
    }

    @Override
    public boolean isCommitLogBackup()
    {
        return config.getBoolean(CONFIG_CL_BK_ENABLE, false);
    }

    @Override
    public int getJmxPort()
    {
        return config.getInteger(CONFIG_JMX_LISTERN_PORT_NAME, DEFAULT_JMX_PORT);
    }

    @Override
    public int getThriftPort()
    {
        return config.getInteger(CONFIG_THRIFT_LISTERN_PORT_NAME, DEFAULT_THRIFT_PORT);
    }

    @Override
    public int getStoragePort()
    {
        return config.getInteger(CONFIG_STORAGE_LISTERN_PORT_NAME, DEFAULT_STORAGE_PORT);
    }

    @Override
    public String getSnitch()
    {
        return config.getProperty(CONFIG_ENDPOINT_SNITCH, DEFULT_ENDPOINT_SNITCH);
    }

    @Override
    public String getAppName()
    {
        return config.getProperty(CONFIG_CLUSTER_NAME, DEFAULT_CLUSTER_NAME);
    }

    @Override
    public String getSecurityGroupName()
    {
        return config.getProperty(CONFIG_SECURITY_GROUP_NAME, DEFAULT_SECURITY_GROUP_NAME);
    }

    @Override
    public String getRac()
    {
        return RAC;
    }

    @Override
    public List<String> getRacs()
    {
        return config.getList(CONFIG_AVAILABILITY_ZONES, DEFAULT_AVAILABILITY_ZONES);
    }

    @Override
    public String getHostname()
    {
        return PUBLIC_HOSTNAME;
    }

    @Override
    public String getInstanceName()
    {
        return INSTANCE_ID;
    }

    @Override
    public String getHeapSize()
    {
        return config.getProperty(CONFIG_MAX_HEAP_SIZE + INSTANCE_TYPE, DEFAULT_MAX_HEAP);
    }

    @Override
    public String getHeapNewSize()
    {
        return config.getProperty(CONFIG_NEW_MAX_HEAP_SIZE + INSTANCE_TYPE, DEFAULT_MAX_NEWGEN_HEAP);
    }

    @Override
    public String getMaxDirectMemory()
    {
        return config.getProperty(CONFIG_DIRECT_MAX_HEAP_SIZE + INSTANCE_TYPE, DEFAULT_MAX_DIRECT_MEM);
    }

    @Override
    public int getBackupHour()
    {
        return config.getInteger(CONFIG_BACKUP_HOUR, DEFAULT_BACKUP_HOUR);
    }

    @Override
    public String getRestoreSnapshot()
    {
        return config.getProperty(CONFIG_AUTO_RESTORE_SNAPSHOTNAME, "");
    }

    @Override
    public String getDC()
    {
        return config.getProperty(CONFIG_REGION_NAME, "");
    }

    @Override
    public void setDC(String region)
    {
        config.setProperty(CONFIG_REGION_NAME, region);
    }

    @Override
    public boolean isMultiDC()
    {
        return config.getBoolean(CONFIG_MR_ENABLE, false);
    }

    @Override
    public int getMaxBackupUploadThreads()
    {

        return config.getInteger(CONFIG_BACKUP_THREADS, DEFAULT_BACKUP_THREADS);
    }

    @Override
    public int getMaxBackupDownloadThreads()
    {
        return config.getInteger(CONFIG_RESTORE_THREADS, DEFAULT_RESTORE_THREADS);
    }

    @Override
    public boolean isRestoreClosestToken()
    {
        return config.getBoolean(CONFIG_RESTORE_CLOSEST_TOKEN, false);
    }

    @Override
    public String getASGName()
    {
        return config.getProperty(CONFIG_ASG_NAME, "");
    }

    @Override
    public boolean isIncrBackup()
    {
        return config.getBoolean(CONFIG_INCR_BK_ENABLE, true);
    }

    @Override
    public String getHostIP()
    {
        return PUBLIC_IP;
    }

    @Override
    public int getUploadThrottle()
    {
        return config.getInteger(CONFIG_THROTTLE_UPLOAD_PER_SECOND, Integer.MAX_VALUE);
    }

    @Override
    public boolean isLocalBootstrapEnabled()
    {
        return config.getBoolean(CONFIG_LOAD_LOCAL_PROPERTIES, false);
    }

    @Override
    public int getInMemoryCompactionLimit()
    {
        return config.getInteger(CONFIG_IN_MEMORY_COMPACTION_LIMIT, 128);
    }

    @Override
    public int getCompactionThroughput()
    {
        return config.getInteger(CONFIG_COMPACTION_THROUHPUT, 8);
    }

    @Override
    public int getMaxHintWindowInMS()
    {
        return config.getInteger(CONFIG_MAX_HINT_WINDOW_IN_MS, 8);
    }

    @Override
    public int getHintHandoffDelay()
    {
        return config.getInteger(CONFIG_HINT_DELAY, 1);
    }

    @Override
    public String getBootClusterName()
    {
        return config.getProperty(CONFIG_BOOTCLUSTER_NAME, "");
    }

    @Override
    public String getSeedProviderName()
    {
        return config.getProperty(CONFIG_SEED_PROVIDER_NAME, DEFAULT_SEED_PROVIDER);
    }

    private class PriamProperties extends Properties
    {

        private static final long serialVersionUID = 1L;

        public int getInteger(String prop, int defaultValue)
        {
            return getProperty(prop) == null ? defaultValue : Integer.parseInt(getProperty(prop));
        }

        public long getLong(String prop, long defaultValue)
        {
            return getProperty(prop) == null ? defaultValue : Long.parseLong(getProperty(prop));
        }

        public boolean getBoolean(String prop, boolean defaultValue)
        {
            return getProperty(prop) == null ? defaultValue : Boolean.parseBoolean(getProperty(prop));
        }

        public List<String> getList(String prop)
        {
            if (getProperty(prop) == null)
                return Lists.newArrayList();
            return Arrays.asList(getProperty(prop).split(","));
        }

        public List<String> getList(String prop, String defaultValue)
        {
            if (getProperty(prop) == null)
                return Lists.newArrayList(defaultValue.split(","));
            return getList(prop);
        }

    }

    @Override
    /**
     * Defaults to 0, means dont set it in yaml
     */
    public int getMemtableTotalSpaceMB()
    {
        return config.getInteger(CONFIG_MEMTABLE_TOTAL_SPACE, 1024);
    }

    @Override
    public int getStreamingThroughputMB()
    {
        return config.getInteger(CONFIG_STREAMING_THROUGHPUT_MB, 400);
    }

    @Override
    public boolean getMultithreadedCompaction()
    {
        return config.getBoolean(CONFIG_MULTITHREADED_COMPACTION, false);
    }

}

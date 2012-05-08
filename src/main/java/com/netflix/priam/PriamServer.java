package com.netflix.priam;

import java.text.ParseException;
import java.util.Random;
import java.util.Set;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.priam.aws.UpdateCleanupPolicy;
import com.netflix.priam.aws.UpdateSecuritySettings;
import com.netflix.priam.aws.UpdateSecuritySettingsTask;
import com.netflix.priam.backup.IncrementalBackup;
import com.netflix.priam.backup.Restore;
import com.netflix.priam.backup.SnapshotBackup;
import com.netflix.priam.identity.InstanceIdentity;
import com.netflix.priam.scheduler.PriamScheduler;
import com.netflix.priam.scheduler.SimpleTimer;
import com.netflix.priam.utils.Sleeper;
import com.netflix.priam.utils.SystemUtils;
import com.netflix.priam.utils.TuneCassandra;

import org.apache.commons.collections.CollectionUtils;
import org.joda.time.Duration;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Start all tasks here - Property update task - Backup task - Restore task -
 * Incremental backup
 */
@Singleton
public class PriamServer
{
    private static final Logger log = LoggerFactory.getLogger(PriamServer.class);

    private final PriamScheduler scheduler;
    private final IConfiguration config;
    private final InstanceIdentity id;
    private final UpdateSecuritySettings initialSecuritySettings;
    private final Sleeper sleeper;

    @Inject
    public PriamServer(IConfiguration config, PriamScheduler scheduler, InstanceIdentity id,
        UpdateSecuritySettings initialSecuritySettings, Sleeper sleeper)
    {
        this.config = config;
        this.scheduler = scheduler;
        this.id = id;
        this.initialSecuritySettings = initialSecuritySettings;
        this.sleeper = sleeper;
    }

    public void initialize() throws Exception
    {     
        if (id.getInstance().isOutOfService())
            return;

        // start to schedule jobs
        scheduler.start();

        // update security settings.
        if (config.isMultiDC())
        {
            Set<String> addedIps = initialSecuritySettings.updateSecurityGroup();
            if (! addedIps.isEmpty())
            {
                /*
                 * By default wait 60s for initial security group additions to propagate at startup time.
                 * todo: What are the consequences of not pausing here?
                 */
                int waitTimeSeconds = Integer.getInteger("priam.security_group_update_wait_seconds", 60);
                log.info("Sleeping {}s to allow initial ec2 security group additions to propagate...", waitTimeSeconds);
                try
                {
                    sleeper.sleep(Duration.standardSeconds(waitTimeSeconds).getMillis());
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
            }

            if (id.isSeed())
                scheduleRegularSecurityGroupUpdates();
        }

        // Run the task to re-write the cassandra config file
        scheduler.runTaskNow(TuneCassandra.class);

        // restore from backup else start cassandra.
        if (!config.getRestoreSnapshot().equals(""))
            scheduler.addTask(Restore.JOBNAME, Restore.class, Restore.getTimer());
        else
            SystemUtils.startCassandra(true, config); // Start cassandra.

        // Start the snapshot backup schedule - Always run this. (If you want to
        // set it off, set backup hour to -1)
        if (config.getBackupHour() >= 0 && (CollectionUtils.isEmpty(config.getBackupRacs()) ||
            config.getBackupRacs().contains(config.getRac())))
        {
            scheduler.addTask(SnapshotBackup.JOBNAME, SnapshotBackup.class, SnapshotBackup.getTimer(config));

            // Start the Incremental backup schedule if enabled
            if (config.isIncrBackup())
                scheduler.addTask(IncrementalBackup.JOBNAME, IncrementalBackup.class, IncrementalBackup.getTimer());
        }
        
        //Set cleanup
        scheduler.addTask(UpdateCleanupPolicy.JOBNAME, UpdateCleanupPolicy.class, UpdateCleanupPolicy.getTimer());
    }

    public InstanceIdentity getId()
    {
        return id;
    }

    private void scheduleRegularSecurityGroupUpdates() throws SchedulerException, ParseException
    {
        // todo: make this period configurable
        final int twoMinutes = 120 * 1000;
        scheduler.addTask(UpdateSecuritySettingsTask.JOBNAME, UpdateSecuritySettingsTask.class,
            new SimpleTimer(UpdateSecuritySettingsTask.JOBNAME, twoMinutes + new Random().nextInt(twoMinutes)));
    }
}

package com.netflix.priam.aws;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.priam.IConfiguration;
import com.netflix.priam.scheduler.Task;

/**
 * Adapts the UpdateSecuritySettings to a scheduled Task
 */
@Singleton
public class UpdateSecuritySettingsTask extends Task
{
    public static final String JOBNAME = "Update_SG";

    private final UpdateSecuritySettings updateSecuritySettings;

    @Inject
    public UpdateSecuritySettingsTask(IConfiguration config, UpdateSecuritySettings updateSecuritySettings)
    {
        super(config);
        this.updateSecuritySettings = updateSecuritySettings;
    }

    @Override
    public void execute() throws Exception
    {
        updateSecuritySettings.updateSecurityGroup();
    }

    @Override
    public String getName()
    {
        return JOBNAME;
    }
}
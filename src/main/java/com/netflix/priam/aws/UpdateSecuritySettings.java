package com.netflix.priam.aws;

import java.util.Set;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.netflix.priam.IConfiguration;
import com.netflix.priam.identity.IMembership;
import com.netflix.priam.identity.IPriamInstanceFactory;
import com.netflix.priam.identity.PriamInstance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * this class will associate an Public IP's with a new instance so they can talk
 * across the regions.
 * 
 * Requirement: 1) Nodes in the same region needs to be able to talk to each
 * other. 2) Nodes in other regions needs to be able to talk to the others in
 * the other region.
 * 
 * Assumption: 1) IPriamInstanceFactory will provide the membership... and will
 * be visible across the regions 2) IMembership amazon or any other
 * implementation which can tell if the instance is part of the group (ASG in
 * amazons case).
 * 
 */
public class UpdateSecuritySettings
{
    private static final Logger log = LoggerFactory.getLogger(UpdateSecuritySettings.class);

    public static final String JOBNAME = "Update_SG";
    public static final String PORT_BINDING = "IngressRulePort";
    
    private final IMembership membership;
    private final IPriamInstanceFactory factory;
    private final String clusterName;
    private final String securityGroupName;
    private final int port;

    @Inject
    public UpdateSecuritySettings(IConfiguration config, IMembership membership, IPriamInstanceFactory factory,
        @Named(PORT_BINDING) int port)
    {
        this.membership = membership;
        this.factory = factory;
        this.clusterName = config.getAppName();
        this.port = port;
        this.securityGroupName = config.getSecurityGroupName();
    }

    private static final Function<PriamInstance, String> INSTANCE_TO_IP_RANGE = new Function<PriamInstance, String>()
    {
        @Override
        public String apply(PriamInstance priamInstance)
        {
            return priamInstance.getHostIP() + "/32";
        }
    };

    /**
     * Update the security group permissions to match those and only those needed by the registered
     * PriamInstances.
     *
     * @return Set of ipRanges added to security group
     */
    public Set<String> updateSecurityGroup()
    {
        Set<String> existingIpPermissions = membership.listIpRangesInSecurityGroup(securityGroupName);
        Set<String> requiredIpPermissions = ImmutableSet.copyOf(
            Iterables.transform(factory.getAllIds(clusterName), INSTANCE_TO_IP_RANGE));

        Set<String> ruleAdditions = Sets.difference(requiredIpPermissions, existingIpPermissions);
        addRules(ruleAdditions);
        removeRules(Sets.difference(existingIpPermissions, requiredIpPermissions));
        return ruleAdditions;
    }

    private void addRules(Set<String> ipRanges)
    {
        membership.addIngressRules(securityGroupName, ipRanges, port);
        log.info("Added rules for security group {} : {}", securityGroupName, ipRanges);
    }

    private void removeRules(Set<String> ipRanges)
    {
        membership.removeIngressRules(securityGroupName, ipRanges, port);
        log.info("Revoked rules for security group {} : {}", securityGroupName, ipRanges);
    }
}

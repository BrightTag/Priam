package com.netflix.priam.aws;

import java.util.Collection;
import java.util.Set;

import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.amazonaws.services.autoscaling.model.Instance;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.RevokeSecurityGroupIngressRequest;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.netflix.priam.identity.IMembership;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service wrapper to EC2 security group management and auto-scaling group queries.
 */
public class AWSMembership implements IMembership
{
    private static final Logger logger = LoggerFactory.getLogger(AWSMembership.class);

    private final Provider<AmazonEC2> ec2ClientProvider;
    private final Provider<AmazonAutoScaling> awsAutoScalingClientProvider;
  
    @Inject
    public AWSMembership(Provider<AmazonEC2> ec2ClientProvider, 
        Provider<AmazonAutoScaling> awsAutoScalingClientProvider)
    {
        this.ec2ClientProvider = ec2ClientProvider;
        this.awsAutoScalingClientProvider = awsAutoScalingClientProvider;
    }

    private static final Set<String> SHUTDOWN_STATES =
        ImmutableSet.of("shutting-down", "terminating", "terminated");

    @Override
    public Set<String> getAutoScalingGroupActiveMembers(String autoScalingGroupName)
    {
        AmazonAutoScaling client = null;
        try
        {
            client = awsAutoScalingClientProvider.get();
            DescribeAutoScalingGroupsResult res = client.describeAutoScalingGroups(
                new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(autoScalingGroupName));

            ImmutableSet.Builder<String> activeMembersBuilder = ImmutableSet.builder();
            // todo : need this loop here? shouldn't we only get zero or one group back?
            for (AutoScalingGroup autoScalingGroup : res.getAutoScalingGroups())
            {
                for (Instance instance : autoScalingGroup.getInstances())
                {
                    if (! SHUTDOWN_STATES.contains(instance.getLifecycleState().toLowerCase())) {
                        activeMembersBuilder.add(instance.getInstanceId());
                    }
                }
            }
            Set<String> activeMembers = activeMembersBuilder.build();
            logger.info("Querying Amazon returned following instance in the ASG: {} --> {}",
                autoScalingGroupName, activeMembers);
            return activeMembers;
        }
        finally
        {
            if (client != null)
                client.shutdown();
        }
    }

    /**
     * Actual membership AWS source of truth...
     */
    @Override
    public int getAutoScalingGroupMaxSize(String autoScalingGroupName)
    {
        AmazonAutoScaling client = null;
        try
        {
            client = awsAutoScalingClientProvider.get();
            DescribeAutoScalingGroupsResult res = client.describeAutoScalingGroups(
                new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(autoScalingGroupName));
            int size = 0;
            // todo : remove this loop
            for (AutoScalingGroup asg : res.getAutoScalingGroups())
            {
                size += asg.getMaxSize();
            }
            logger.info("Query on ASG returning {} instances", size);
            return size;
        }
        finally
        {
            if (client != null)
                client.shutdown();
        }
    }

    /**
     * Adds a iplist to the SG.
     */
    public void addIngressRules(String securityGroupName, Collection<String> listIPs, int port)
    {
        AmazonEC2 client = null;
        if (! listIPs.isEmpty())
        {
            try
            {
                client = ec2ClientProvider.get();
                client.authorizeSecurityGroupIngress(new AuthorizeSecurityGroupIngressRequest()
                    .withGroupName(securityGroupName)
                    .withIpPermissions(new IpPermission()
                        .withFromPort(port)
                        .withIpProtocol("tcp")
                        .withIpRanges(listIPs)
                        .withToPort(port)));
                logger.info("Done adding ACL to: " + StringUtils.join(listIPs, ","));
            }
            finally
            {
                if (client != null)
                    client.shutdown();
            }
        }
    }

    /**
     * removes a iplist from the SG
     */
    public void removeIngressRules(String securityGroupName, Collection<String> listIPs, int port)
    {
        AmazonEC2 client = null;
        if (! listIPs.isEmpty())
        {
            try
            {
                client = ec2ClientProvider.get();
                client.revokeSecurityGroupIngress(new RevokeSecurityGroupIngressRequest()
                    .withGroupName(securityGroupName)
                    .withIpPermissions(new IpPermission()
                        .withFromPort(port)
                        .withIpProtocol("tcp")
                        .withIpRanges(listIPs)
                        .withToPort(port)));
            }
            finally
            {
                if (client != null)
                    client.shutdown();
            }
        }
    }

    public Set<String> listIpRangesInSecurityGroup(String securityGroupName)
    {
        AmazonEC2 client = null;
        try
        {
            client = ec2ClientProvider.get();
            DescribeSecurityGroupsResult result = client.describeSecurityGroups(
                new DescribeSecurityGroupsRequest().withGroupNames(securityGroupName));
            ImmutableSet.Builder<String> ipRanges = ImmutableSet.builder();
            for (SecurityGroup group : result.getSecurityGroups()) {
                for (IpPermission perm : group.getIpPermissions()) {
                    ipRanges.addAll(perm.getIpRanges());
                }

            }
            return ipRanges.build();
        }
        finally
        {
            if (client != null)
                client.shutdown();
        }
    }
}

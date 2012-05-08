package com.netflix.priam.identity;

import java.util.Collection;
import java.util.Set;

/**
 * Interface to manage membership meta information such as size of RAC, list of
 * nodes in RAC etc. Also perform ACL updates used in multi-regional clusters
 */
public interface IMembership
{
    /**
     * Return the set of ec2 instances in the provided AWS auto-scaling group
     */
    public Set<String> getAutoScalingGroupActiveMembers(String autoScalingGroupName);

    /**
     * @return maximum size of the named auto-scaling group
     */
    public int getAutoScalingGroupMaxSize(String autoScalingGroupName);

    /**
     * Add security group ingress rules
     *
     * @param securityGroupName ec2 security group name
     * @param listIPs ip address permissions in CIDR notation (e.g. "198.51.100.1/24")
     * @param port listen port defined in rules
     */
    public void addIngressRules(String securityGroupName, Collection<String> listIPs, int port);

    /**
     * Remove security group ingress rules
     *
     * @param securityGroupName ec2 security group name
     * @param listIPs ip address permissions in CIDR notation (e.g. "198.51.100.1/24")
     * @param port listen port defined in rules
     */
    public void removeIngressRules(String securityGroupName, Collection<String> listIPs, int port);

    /**
     * List all IP address ranges in CIDR notation permitted by the given security group.
     */
    public Set<String> listIpRangesInSecurityGroup(String securityGroupName);
}
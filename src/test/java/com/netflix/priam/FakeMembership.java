package com.netflix.priam;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import com.netflix.priam.identity.IMembership;

public class FakeMembership implements IMembership
{
    private final List<String> instances;

    public FakeMembership(List<String> priamInstances)
    {
        // todo : InstanceIdentityTest.testDeadInstance depends on this being a mutable List
        this.instances = priamInstances;
    }
    
    @Override
    public Set<String> getAutoScalingGroupActiveMembers(String autoScalingGroupName)
    {
        return ImmutableSet.copyOf(instances);
    }

    @Override
    public int getAutoScalingGroupMaxSize(String autoScalingGroupName)
    {
        return 3;
    }

    @Override
    public void addIngressRules(String securityGroupName, Collection<String> listIPs, int port)
    {
        // no-op
    }

    @Override
    public void removeIngressRules(String securityGroupName, Collection<String> listIPs, int port)
    {
        // no-op
    }
  
    @Override
    public Set<String> listIpRangesInSecurityGroup(String securityGroupName)
    {
        return ImmutableSet.of();
    }
}

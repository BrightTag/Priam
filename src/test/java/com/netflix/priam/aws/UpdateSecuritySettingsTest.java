package com.netflix.priam.aws;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.netflix.priam.IConfiguration;
import com.netflix.priam.identity.IMembership;
import com.netflix.priam.identity.IPriamInstanceFactory;
import com.netflix.priam.identity.PriamInstance;

import org.junit.Test;

import mockit.Mocked;
import mockit.NonStrictExpectations;
import mockit.Verifications;

import static org.junit.Assert.assertEquals;

public class UpdateSecuritySettingsTest {

    private @Mocked IConfiguration config;
    private @Mocked IMembership membership;
    private @Mocked IPriamInstanceFactory factory;

    @Test
    public void execute()
    {
        final int gossipPort = 9999;
        
        new NonStrictExpectations() {{
            config.getAppName(); result = "my_cassandra_app"; times = 2;
            // one old ip address is existing...
            membership.listIpRangesInSecurityGroup("my_cassandra_app"); result =
              ImmutableSet.of("1.1.1.1/32", "2.2.2.2/32");
            // and one new ip address should be added
            factory.getAllIds("my_cassandra_app"); result =
              ImmutableList.of(priamInstance("2.2.2.2"), priamInstance("3.3.3.3"));
        }};

        assertEquals(ImmutableSet.of("3.3.3.3/32"),
            new UpdateSecuritySettings(config, membership, factory, gossipPort).updateSecurityGroup());

        new Verifications() {{
            membership.addIngressRules("my_cassandra_app", ImmutableSet.of("3.3.3.3/32"), gossipPort);
            membership.removeIngressRules("my_cassandra_app", ImmutableSet.of("1.1.1.1/32"), gossipPort);
        }};
    }
  
    private static PriamInstance priamInstance(String publicIp)
    {
        PriamInstance instance = new PriamInstance();
        instance.setHostIP(publicIp);
        return instance;
    }
}
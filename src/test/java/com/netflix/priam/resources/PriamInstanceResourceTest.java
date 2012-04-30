package com.netflix.priam.resources;

import java.util.List;

import javax.ws.rs.WebApplicationException;

import com.google.common.collect.ImmutableList;
import com.netflix.priam.IConfiguration;
import com.netflix.priam.identity.IPriamInstanceFactory;
import com.netflix.priam.identity.PriamInstance;

import mockit.Expectations;
import mockit.Mocked;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class PriamInstanceResourceTest
{
    private static final String APP_NAME = "myApp";
    private static final int NODE_ID = 3;

    private @Mocked IConfiguration config;
    private @Mocked IPriamInstanceFactory factory;
    private PriamInstanceResource resource;

    @Before
    public void setUp()
    {
        resource = new PriamInstanceResource(config, factory);
    }

    @Test
    public void getInstances()
    {
        new Expectations() {
            PriamInstance instance1, instance2, instance3;
            List<PriamInstance> instances = ImmutableList.of(instance1, instance2, instance3);

            {
                config.getAppName(); result = APP_NAME;
                factory.getAllIds(APP_NAME); result = instances;
                instance1.toString(); result = "instance1";
                instance2.toString(); result = "instance2";
                instance3.toString(); result = "instance3";
            }
        };

        assertEquals("instance1\ninstance2\ninstance3\n", resource.getInstances());
    }

    @Test
    public void getInstance()
    {
        final String expected = "plain text describing the instance";
        new Expectations() {
            PriamInstance instance;

            {
                config.getAppName(); result = APP_NAME;
                factory.getInstance(APP_NAME, NODE_ID); result = instance;
                instance.toString(); result = expected;
            }
        };

        assertEquals(expected, resource.getInstance(NODE_ID));
    }

    @Test
    public void getInstance_notFound()
    {
        new Expectations() {{
            config.getAppName(); result = APP_NAME;
            factory.getInstance(APP_NAME, NODE_ID); result = null;
        }};

        try
        {
          resource.getInstance(NODE_ID);
          fail("Expected WebApplicationException thrown");
        } catch(WebApplicationException e)
        {
            assertEquals(404, e.getResponse().getStatus());
            assertEquals("No priam instance with id " + NODE_ID + " found", e.getResponse().getEntity());
        }
    }
}

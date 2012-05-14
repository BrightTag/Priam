package com.netflix.priam.identity;

import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.priam.IConfiguration;
import com.netflix.priam.utils.RetryableCallable;
import com.netflix.priam.utils.Sleeper;
import com.netflix.priam.utils.TokenManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides the central place to create and consume the identity of
 * the instance - token, seeds etc.
 * 
 */
@Singleton
public class InstanceIdentity
{
    private static final Logger logger = LoggerFactory.getLogger(InstanceIdentity.class);
    private final IPriamInstanceFactory factory;
    private final IMembership membership;
    private final IConfiguration config;
    private final Sleeper sleeper;

    private PriamInstance myInstance;
    private boolean isReplace = false;

    @Inject
    public InstanceIdentity(IPriamInstanceFactory factory, IMembership membership, IConfiguration config,
            Sleeper sleeper) throws Exception
    {
        this.factory = factory;
        this.membership = membership;
        this.config = config;
        this.sleeper = sleeper;
        init();
    }

    public PriamInstance getInstance()
    {
        return myInstance;
    }

    public void init() throws Exception
    {
        // try to grab the token which was already assigned
        myInstance = new RetryableCallable<PriamInstance>()
        {
            @Override
            public PriamInstance retriableCall() throws Exception
            {
                // Check if this node is decomissioned
                for (PriamInstance ins : factory.getAllIds(config.getAppName() + "-dead"))
                {
                    logger.debug(String.format("Iterating though the hosts: %s", ins.getInstanceId()));
                    if (ins.getInstanceId().equals(config.getInstanceName()))
                    {
                        ins.setOutOfService(true);
                        return ins;
                    }
                }
                for (PriamInstance ins : factory.getAllIds(config.getAppName()))
                {
                    logger.debug(String.format("Iterating though the hosts: %s", ins.getInstanceId()));
                    if (ins.getInstanceId().equals(config.getInstanceName()))
                        return ins;
                }
                return null;
            }
        }.call();
        // Grab a dead token
        if (null == myInstance)
            myInstance = new GetDeadToken().call();
        // Grab a new token
        if (null == myInstance)
            myInstance = new GetNewToken().call();
        logger.info("My token: " + myInstance.getToken());
    }

    private ListMultimap<String, PriamInstance> getRacMap()
    {
        return Multimaps.index(factory.getAllIds(config.getAppName()), 
            new Function<PriamInstance, String>()
        {
            @Override
            public String apply(PriamInstance instance)
            {
                return instance.getRac();
            }
        });
    }

    public class GetDeadToken extends RetryableCallable<PriamInstance>
    {
        @Override
        public PriamInstance retriableCall() throws Exception
        {
            final List<PriamInstance> allIds = factory.getAllIds(config.getAppName());
            Set<String> asgInstances = membership.getAutoScalingGroupActiveMembers(config.getASGName());
            // Sleep random interval - upto 15 sec
            sleeper.sleep(new Random().nextInt(5000) + 10000);
            for (PriamInstance dead : allIds)
            {
                // test same zone and is it is alive.
                if (!dead.getRac().equals(config.getRac()) || asgInstances.contains(dead.getInstanceId()))
                    continue;
                logger.info("Found dead instances: " + dead.getInstanceId());
                PriamInstance markAsDead = factory.create(dead.getApp() + "-dead", dead.getId(), dead.getInstanceId(), dead.getHostName(), dead.getHostIP(), dead.getRac(), dead.getVolumes(),
                        dead.getToken());

                // GAO: HACK!  Want to gut all this, but for now don't want Priam deleting rows from SimpleDB!
                // remove it as we marked it down...
                //factory.delete(dead);

                isReplace = true;
                String payLoad = markAsDead.getToken();
                logger.info("Trying to grab slot {} with availability zone {}", markAsDead.getId(), markAsDead.getRac());
                return factory.create(config.getAppName(), markAsDead.getId(), config.getInstanceName(), config.getHostname(), config.getHostIP(), config.getRac(), markAsDead.getVolumes(), payLoad);
            }
            return null;
        }
    }

    public class GetNewToken extends RetryableCallable<PriamInstance>
    {
        @Override
        public PriamInstance retriableCall() throws Exception
        {
            // Sleep random interval - upto 15 sec
            // TODO : remove!
            sleeper.sleep(new Random().nextInt(15000));

            ListMultimap<String, PriamInstance> locMap = getRacMap();
            int hash = TokenManager.regionOffset(config.getDC());
            // use this hash so that the nodes are spred far away from the other
            // regions.

            int max = hash;
            for (PriamInstance data : locMap.values())
                max = (data.getRac().equals(config.getRac()) && (data.getId() > max)) ? data.getId() : max;
            int maxSlot = max - hash;
            int my_slot = 0;
            
            if (hash == max && locMap.get(config.getRac()).size() == 0)
                my_slot = config.getRacs().indexOf(config.getRac()) + maxSlot;
            else
                my_slot = config.getRacs().size() + maxSlot;

            String payload = TokenManager.createToken(my_slot, config.getRacs().size(),
                membership.getAutoScalingGroupMaxSize(config.getASGName()), config.getDC());
            return factory.create(config.getAppName(), my_slot + hash, config.getInstanceName(),
                config.getHostname(), config.getHostIP(), config.getRac(), null, payload);
        }
    }

    private final Predicate<PriamInstance> differentHostPredicate = new Predicate<PriamInstance>() {
        @Override
        public boolean apply(PriamInstance instance) {
            return !instance.getHostName().equals(myInstance.getHostName());
        }
    };

    public List<String> getSeeds() throws UnknownHostException
    {
        ListMultimap<String, PriamInstance> locMap = getRacMap();
        List<String> seeds = new LinkedList<String>();
        for (String loc : locMap.keySet())
        {
            PriamInstance instance = Iterables.tryFind(locMap.get(loc), differentHostPredicate).orNull();
            if (instance != null)
                seeds.add(instance.getHostName());
        }
        // handle a non-clustered node by returning the localhost address, otherwise Cassandra will fail to start
        if (seeds.isEmpty()) {
            seeds.add("127.0.0.1");
        }
        return seeds;
    }
    
    public boolean isSeed()
    {
        ListMultimap<String, PriamInstance> locMap = getRacMap();
        String ip = locMap.get(myInstance.getRac()).get(0).getHostName();
        return myInstance.getHostName().equals(ip);
    }
    
    public boolean isReplace()
    {
        return isReplace;
    }
}

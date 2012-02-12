/**
 * CacheFactory.java - Constructs Cache objects.
 *
 * Copyright (c) 2012 Midokura KK. All rights reserved.
 */

package com.midokura.midolman.util;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class CacheFactory {
    private static final Logger log =
        LoggerFactory.getLogger(CacheFactory.class);

    public static final int CACHE_EXPIRATION_SECONDS = 60;

    /**
     * Create a Cache object.
     *
     * @param config configuration for the Cache object
     * @return a Cache object
     * @throws CacheException if an error occurs
     */
    public static Cache create(HierarchicalConfiguration config)
        throws CacheException {
        Cache cache = null;
        String cacheType = config.configurationAt("midolman")
                                 .getString("cache_type", "memcache");
        boolean isValid = false;

        try {
            if (cacheType.equals("memcache")) {
                isValid = true;
                // set log4j logging for spymemcached client
                Properties props = System.getProperties();
                props.put("net.spy.log.LoggerImpl",
                          "net.spy.memcached.compat.log.Log4JLogger");
                System.setProperties(props);

                String memcacheHosts = config.configurationAt("memcache")
                                             .getString("memcache_hosts");

                cache = new MemcacheCache(memcacheHosts,
                                          CACHE_EXPIRATION_SECONDS);
            } else if (cacheType.equals("cassandra")) {
                isValid = true;
                String servers = config.configurationAt("cassandra")
                                       .getString("servers");
                String cluster = config.configurationAt("cassandra")
                                       .getString("cluster");
                String keyspace = config.configurationAt("cassandra")
                                        .getString("keyspace");
                int replicationFactor = config.configurationAt("cassandra")
                                              .getInt("replication_factor");

                cache = new CassandraCache(servers, cluster, keyspace,
                                           "nat", replicationFactor,
                                           CACHE_EXPIRATION_SECONDS);
            }
        } catch (Exception e) {
            throw new CacheException("error while creating cache", e);
        }

        if (!isValid) {
            throw new CacheException("unknown cache type");
        }

        return cache;
    }
}

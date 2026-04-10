/*
 * Copyright 2005 Sun Microsystems, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.roller.planet.business;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.planet.business.fetcher.FeedFetcher;
import org.apache.roller.planet.business.fetcher.FetcherException;
import org.apache.roller.planet.pojos.Subscription;
import org.apache.roller.weblogger.TestUtils;
import org.apache.roller.weblogger.business.WebloggerFactory;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


/**
 * Test database implementation of PlanetManager.
 */
public class RomeFeedFetcherTest  {
    
    public static Log log = LogFactory.getLog(RomeFeedFetcherTest.class);
    
    String feed_url = "https://rollerweblogger.org/roller/feed/entries/atom";
    
    @BeforeEach
    public void setUp() throws Exception {
        // setup planet
        TestUtils.setupWeblogger();
    }
    
    @AfterEach
    public void tearDown() throws Exception {
    }

    @Test
    public void testFetchFeed() throws FetcherException {

        assumeTrue(isFeedReachable(), "Skipping test because external feed endpoint is not reachable");

        try {
            FeedFetcher feedFetcher = WebloggerFactory.getWeblogger().getFeedFetcher();
            
            // fetch feed
            Subscription sub = feedFetcher.fetchSubscription(feed_url);
            assertNotNull(sub);
            assertEquals(feed_url, sub.getFeedURL());
            assertEquals("https://rollerweblogger.org/roller/", sub.getSiteURL());
            assertEquals("Blogging Roller", sub.getTitle());
            assertNotNull(sub.getLastUpdated());
            assertTrue(!sub.getEntries().isEmpty());

        } catch (FetcherException ex) {
            assumeTrue(!isNetworkTimeout(ex), "Skipping: feed endpoint timed out during fetch");
            log.error("Error fetching feed", ex);
            throw ex;
        }
    }


    @Test
    public void testFetchFeedConditionally() throws FetcherException {

        assumeTrue(isFeedReachable(), "Skipping test because external feed endpoint is not reachable");

        try {
            FeedFetcher feedFetcher = WebloggerFactory.getWeblogger().getFeedFetcher();
            
            // fetch feed
            Subscription sub = feedFetcher.fetchSubscription(feed_url);
            assertNotNull(sub);
            assertEquals(feed_url, sub.getFeedURL());
            assertEquals("https://rollerweblogger.org/roller/", sub.getSiteURL());
            assertEquals("Blogging Roller", sub.getTitle());
            assertNotNull(sub.getLastUpdated());
            assertTrue(!sub.getEntries().isEmpty());
            
            // now do a conditional fetch and we should get back null
            Subscription updatedSub = feedFetcher.fetchSubscription(feed_url, sub.getLastUpdated());
            assertNull(updatedSub);

        } catch (FetcherException ex) {
            assumeTrue(!isNetworkTimeout(ex), "Skipping: feed endpoint timed out during fetch");
            log.error("Error fetching feed", ex);
            throw ex;
        }
    }

    private boolean isNetworkTimeout(FetcherException ex) {
        Throwable cause = ex.getRootCause();
        while (cause != null) {
            if (cause instanceof java.net.http.HttpTimeoutException
                    || cause instanceof java.net.ConnectException
                    || cause instanceof java.net.SocketTimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private boolean isFeedReachable() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(feed_url))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();

            HttpResponse<Void> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build()
                    .send(request, HttpResponse.BodyHandlers.discarding());

            return response.statusCode() < 500;
        } catch (Exception ex) {
            log.info("External feed endpoint not reachable for test execution: " + feed_url, ex);
            return false;
        }
    }
    
}

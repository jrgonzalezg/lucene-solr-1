/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.solr.metrics;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import com.codahale.metrics.Metric;
import com.codahale.metrics.Timer;
import org.apache.commons.io.FileUtils;
import org.apache.lucene.util.TestUtil;
import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.NodeConfig;
import org.apache.solr.core.PluginInfo;
import org.apache.solr.core.SolrInfoMBean;
import org.apache.solr.metrics.reporters.MockMetricReporter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SolrMetricsIntegrationTest extends SolrTestCaseJ4 {
  private static final int MAX_ITERATIONS = 20;
  private static final String METRIC_NAME = "requestTimes";
  private static final String HANDLER_NAME = "standard";
  private static final String[] REPORTER_NAMES = {"reporter1", "reporter2"};
  private static final String UNIVERSAL = "universal";
  private static final String SPECIFIC = "specific";
  private static final String MULTIGROUP = "multigroup";
  private static final String MULTIREGISTRY = "multiregistry";
  private static final String[] ALL_REPORTERS = {REPORTER_NAMES[0], REPORTER_NAMES[1], UNIVERSAL, SPECIFIC, MULTIGROUP, MULTIREGISTRY};
  private static final SolrInfoMBean.Category HANDLER_CATEGORY = SolrInfoMBean.Category.QUERYHANDLER;

  private CoreContainer cc;

  @Before
  public void beforeTest() throws Exception {
    Path home = Paths.get(TEST_HOME());
    // define these properties, they are used in solrconfig.xml
    System.setProperty("solr.test.sys.prop1", "propone");
    System.setProperty("solr.test.sys.prop2", "proptwo");
    String solrXml = FileUtils.readFileToString(Paths.get(home.toString(), "solr-metricreporter.xml").toFile(), "UTF-8");
    cc = createCoreContainer(home, solrXml);
    h.coreName = DEFAULT_TEST_CORENAME;
    NodeConfig cfg = cc.getConfig();
    PluginInfo[] plugins = cfg.getMetricReporterPlugins();
    assertNotNull(plugins);
    assertEquals(10, plugins.length);
    Map<String, SolrMetricReporter> reporters = SolrMetricManager.getReporters("solr.node");
    assertEquals(4, reporters.size());
    assertTrue("Reporter '" + REPORTER_NAMES[0] + "' missing in solr.node", reporters.containsKey(REPORTER_NAMES[0]));
    assertTrue("Reporter '" + UNIVERSAL + "' missing in solr.node", reporters.containsKey(UNIVERSAL));
    assertTrue("Reporter '" + MULTIGROUP + "' missing in solr.node", reporters.containsKey(MULTIGROUP));
    assertTrue("Reporter '" + MULTIREGISTRY + "' missing in solr.node", reporters.containsKey(MULTIREGISTRY));
    SolrMetricReporter reporter = reporters.get(REPORTER_NAMES[0]);
    assertTrue("Reporter " + reporter + " is not an instance of " + MockMetricReporter.class.getName(),
        reporter instanceof  MockMetricReporter);
    reporter = reporters.get(UNIVERSAL);
    assertTrue("Reporter " + reporter + " is not an instance of " + MockMetricReporter.class.getName(),
        reporter instanceof  MockMetricReporter);
  }

  @After
  public void afterTest() throws Exception {
    SolrCoreMetricManager metricManager = h.getCore().getMetricManager();
    Map<String, SolrMetricReporter> reporters = SolrMetricManager.getReporters(metricManager.getRegistryName());

    deleteCore();

    for (String reporterName : ALL_REPORTERS) {
      SolrMetricReporter reporter = reporters.get(reporterName);
      MockMetricReporter mockReporter = (MockMetricReporter) reporter;
      assertTrue("Reporter " + reporterName + " was not closed: " + mockReporter, mockReporter.didClose);
    }
  }

  @Test
  public void testConfigureReporter() throws Exception {
    Random random = random();

    int iterations = TestUtil.nextInt(random, 0, MAX_ITERATIONS);
    for (int i = 0; i < iterations; ++i) {
      h.query(req("*"));
    }

    String metricName = SolrMetricManager.mkName(METRIC_NAME, HANDLER_CATEGORY.toString(), HANDLER_NAME);
    SolrCoreMetricManager metricManager = h.getCore().getMetricManager();
    Map<String, SolrMetricReporter> reporters = SolrMetricManager.getReporters(metricManager.getRegistryName());
    assertEquals(ALL_REPORTERS.length, reporters.size());

    for (String reporterName : ALL_REPORTERS) {
      SolrMetricReporter reporter = reporters.get(reporterName);
      assertNotNull("Reporter " + reporterName + " was not found.", reporter);
      assertTrue(reporter instanceof MockMetricReporter);

      MockMetricReporter mockReporter = (MockMetricReporter) reporter;
      assertTrue("Reporter " + reporterName + " was not initialized: " + mockReporter, mockReporter.didInit);
      assertTrue("Reporter " + reporterName + " was not validated: " + mockReporter, mockReporter.didValidate);
      assertFalse("Reporter " + reporterName + " was incorrectly closed: " + mockReporter, mockReporter.didClose);

      Metric metric = mockReporter.reportMetric(metricName);
      assertNotNull("Metric " + metricName + " was not reported.", metric);
      assertTrue("Metric " + metricName + " is not an instance of Timer: " + metric, metric instanceof Timer);

      Timer timer = (Timer) metric;
      assertEquals(iterations, timer.getCount());
    }
  }
}

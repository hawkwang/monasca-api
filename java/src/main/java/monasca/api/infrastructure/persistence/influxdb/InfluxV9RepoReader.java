/*
 * Copyright (c) 2014 Hewlett-Packard Development Company, L.P.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package monasca.api.infrastructure.persistence.influxdb;

import com.google.inject.Inject;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;

import monasca.api.ApiConfig;

public class InfluxV9RepoReader {

  private static final Logger logger = LoggerFactory.getLogger(InfluxV9RepoReader.class);

  private final ApiConfig config;

  private final String influxName;
  private final String influxUrl;
  private final String influxCreds;
  private final String influxUser;
  private final String influxPass;
  private final String baseAuthHeader;

  private final CloseableHttpClient httpClient;

  @Inject
  public InfluxV9RepoReader(final ApiConfig config) {
    this.config = config;

    this.influxName = config.influxDB.getName();
    this.influxUrl = config.influxDB.getUrl() + "/query";
    this.influxUser = config.influxDB.getUser();
    this.influxPass = config.influxDB.getPassword();
    this.influxCreds = this.influxUser + ":" + this.influxPass;

    this.baseAuthHeader = "Basic " + new String(Base64.encodeBase64(this.influxCreds.getBytes()));

    PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
    cm.setMaxTotal(config.influxDB.getMaxHttpConnections());

    // We inject InfluxV9RepoReader as a singleton. So, we must share connections safely.
    this.httpClient = HttpClients.custom().setConnectionManager(cm).build();

  }

  protected String read(final String query) throws Exception {

    HttpGet request = new HttpGet(this.influxUrl + "?q=" + URLEncoder.encode(query, "UTF-8")
                                  + "&db=" + URLEncoder.encode(this.influxName, "UTF-8"));

    request.addHeader("content-type", "application/json");
    request.addHeader("Authorization", this.baseAuthHeader);

    try {

      logger.debug("Sending query {} to influx database {} at {}", query, this.influxName,
                   this.influxUrl);

      HttpResponse response = this.httpClient.execute(request);

      int rc = response.getStatusLine().getStatusCode();

      if (rc != HttpStatus.SC_OK) {

        HttpEntity entity = response.getEntity();
        String responseString = EntityUtils.toString(entity, "UTF-8");
        logger.error("Failed to query influx database {} at {}: {}",
                     this.influxName, this.influxUrl, String.valueOf(rc));
        logger.error("Http response: {}", responseString);

        throw new Exception(rc + ":" + responseString);
      }

      logger.debug("Successfully queried influx database {} at {}",
                   this.influxName, this.influxUrl);

      HttpEntity entity = response.getEntity();
      return entity != null ? EntityUtils.toString(entity) : null;

    } finally {

      request.releaseConnection();

    }
  }

}

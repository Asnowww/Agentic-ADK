/**
 * Copyright (C) 2024 AIDC-AI
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
package com.alibaba.langengine.momento.vectorstore;

import com.alibaba.langengine.momento.MomentoException;
import momento.sdk.CacheClient;
import momento.sdk.auth.CredentialProvider;
import momento.sdk.auth.EnvVarCredentialProvider;
import momento.sdk.config.Configurations;
import momento.sdk.exceptions.SdkException;
import momento.sdk.responses.cache.control.CacheCreateResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.time.Duration;


public class MomentoClient implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(MomentoClient.class);

    private final MomentoParam momentoParam;
    private final CacheClient cacheClient;

    public MomentoClient(MomentoParam momentoParam) {
        this.momentoParam = momentoParam;
        try {
            CredentialProvider credentialProvider = new EnvVarCredentialProvider("MOMENTO_AUTH_TOKEN");
            // Override with provided auth token if available
            if (momentoParam.getAuthToken() != null && !momentoParam.getAuthToken().isEmpty()) {
                credentialProvider = CredentialProvider.fromString(momentoParam.getAuthToken());
            }

            this.cacheClient = CacheClient.builder(
                            credentialProvider,
                            Configurations.Laptop.v1(),
                            Duration.ofSeconds(momentoParam.getDefaultTtlSeconds()))
                    .build();

            // Ensure cache exists
            createCacheIfNotExist(momentoParam.getCacheName());

        } catch (SdkException e) {
            log.error("Failed to initialize Momento client", e);
            throw new MomentoException("MOMENTO_CLIENT_INIT_ERROR", "Failed to initialize Momento client", e);
        } catch (Exception e) {
            log.error("Unexpected error during Momento client initialization", e);
            throw new MomentoException("MOMENTO_CLIENT_INIT_UNEXPECTED_ERROR", "Unexpected error during Momento client initialization", e);
        }
    }

    private void createCacheIfNotExist(String cacheName) {
        CacheCreateResponse createCacheResponse = cacheClient.createCache(cacheName).join();
        if (createCacheResponse instanceof CacheCreateResponse.Error) {
            CacheCreateResponse.Error error = (CacheCreateResponse.Error) createCacheResponse;
            String errorMessage = error.getMessage();
            if (!errorMessage.contains("ALREADY_EXISTS")) {
                throw new MomentoException("MOMENTO_CACHE_CREATE_ERROR", "Failed to create cache: " + cacheName + ", Error: " + errorMessage, error.getCause());
            }
            log.info("Momento cache '{}' already exists.", cacheName);
        } else if (createCacheResponse instanceof CacheCreateResponse.Success) {
            log.info("Momento cache '{}' created successfully.", cacheName);
        }
    }

    public CacheClient getCacheClient() {
        return cacheClient;
    }

    @Override
    public void close() {
        if (cacheClient != null) {
            try {
                cacheClient.close();
                log.info("Momento CacheClient closed successfully.");
            } catch (Exception e) {
                log.error("Error closing Momento CacheClient", e);
            }
        }
    }

    // Placeholder for upsert operation
    public void upsert(String indexName, String cacheName, String id, float[] vector, String text) {
        log.warn("MomentoClient.upsert not yet implemented.");
        // TODO: Implement actual upsert logic using cacheClient.vectorIndexClient().upsert
    }

    // Placeholder for search operation
    public void search(String indexName, String cacheName, float[] queryVector, int topK) {
        log.warn("MomentoClient.search not yet implemented.");
        // TODO: Implement actual search logic using cacheClient.vectorIndexClient().search
    }

    // Placeholder for delete operation
    public void delete(String indexName, String cacheName, String id) {
        log.warn("MomentoClient.delete not yet implemented.");
        // TODO: Implement actual delete logic using cacheClient.vectorIndexClient().delete
    }
}

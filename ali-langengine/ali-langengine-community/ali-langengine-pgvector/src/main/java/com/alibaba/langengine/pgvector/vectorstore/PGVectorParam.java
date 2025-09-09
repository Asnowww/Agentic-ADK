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
package com.alibaba.langengine.pgvector.vectorstore;

import lombok.Data;


@Data
public class PGVectorParam {
    private InitParam initParam = new InitParam();
    private String fieldNamePageContent = "page_content";
    private String fieldNameUniqueId = "content_id";
    private String fieldMeta = "meta_data";

    @Data
    public static class InitParam {
        private String tableName = "langengine_pg_collection";
        private String vectorDistance = "cosine";
        private int dimension = 768;
        private String indexType = "ivfflat";
    }
}

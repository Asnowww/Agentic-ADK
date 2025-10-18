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
package com.alibaba.langengine.turbopuffer.vectorstore;

import com.alibaba.fastjson.JSON;
import lombok.Data;

import java.util.Map;


@Data
public class TurbopufferParam {

    /**
     * 向量字段名
     */
    private String fieldNameEmbedding = "embeddings";

    /**
     * 内容字段名
     */
    private String fieldNamePageContent = "content";

    /**
     * 唯一ID字段名
     */
    private String fieldNameUniqueId = "id";

    /**
     * 距离度量类型 (cosine, euclidean, dot_product)
     */
    private String distanceMetric = "cosine";

    /**
     * 自定义搜索扩展参数
     */
    private Map<String, Object> searchParams = JSON.parseObject("{}");

    /**
     * 初始化参数, 用于创建Namespace
     */
    private InitParam initParam = new InitParam();

    @Data
    public static class InitParam {

        /**
         * 是否使用uniqueId作为唯一键, 如果是的话, addDocuments的时候uniqueId不要为空
         */
        private boolean fieldUniqueIdAsPrimaryKey = true;

        /**
         * embeddings字段向量维度, 如果设置为0, 则会通过embedding模型查询一条数据, 看维度是多少
         */
        private int fieldEmbeddingsDimension = 1536;

        /**
         * 请求超时时间（毫秒）
         */
        private int requestTimeoutMs = 30000;

        /**
         * 连接超时时间（毫秒）
         */
        private int connectTimeoutMs = 10000;

        /**
         * 读取超时时间（毫秒）
         */
        private int readTimeoutMs = 30000;

        /**
         * 最大重试次数
         */
        private int maxRetries = 3;

    }

}

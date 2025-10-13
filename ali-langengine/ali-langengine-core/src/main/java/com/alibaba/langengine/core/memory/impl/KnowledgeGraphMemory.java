/**
 * Copyright (C) 2025 AIDC-AI
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
package com.alibaba.langengine.core.memory.impl;

import com.alibaba.langengine.core.chatmodel.BaseChatModel;
import com.alibaba.langengine.core.memory.BaseChatMemory;
import com.alibaba.langengine.core.memory.BaseMemory;
import com.alibaba.langengine.core.memory.graph.*;
import com.alibaba.langengine.core.messages.BaseMessage;
import com.alibaba.langengine.core.messages.HumanMessage;
import com.alibaba.langengine.core.messages.MessageConverter;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识图谱记忆
 *
 * 将对话内容转换为结构化的知识图谱，支持复杂的知识检索和推理
 *
 * 核心功能：
 * 1. 自动知识抽取：从对话中提取实体和关系
 * 2. 知识图谱构建：维护实体-关系网络
 * 3. 智能检索：基于图结构检索相关知识
 * 4. 知识推理：通过图遍历发现隐含关系
 *
 * 使用场景：
 * - 客户关系管理（CRM）：跟踪客户、产品、订单等实体关系
 * - 教育助手：构建学科知识图谱，理解概念关系
 * - 项目管理：跟踪任务、人员、资源的关系网络
 * - 技术文档助手：理解代码、API、模块之间的依赖关系
 *
 * 示例：
 * <pre>
 * KnowledgeGraphMemory memory = KnowledgeGraphMemory.builder()
 *     .llm(chatModel)
 *     .maxHops(2)
 *     .topEntities(5)
 *     .autoExtraction(true)
 *     .build();
 *
 * // 保存对话，自动抽取知识
 * memory.saveContext(sessionId, inputs, outputs);
 *
 * // 加载相关知识
 * Map<String, Object> context = memory.loadMemoryVariables(sessionId, inputs);
 * </pre>
 *
 * @author xiaoxuan.lp
 */
@Slf4j
@Data
public class KnowledgeGraphMemory extends BaseMemory {

    /**
     * 图谱存储
     */
    private GraphStore graphStore;

    /**
     * 知识抽取器
     */
    private KnowledgeExtractor extractor;

    /**
     * 对话记忆（用于存储原始对话）
     */
    private BaseChatMemory chatMemory;

    /**
     * 是否自动抽取知识
     */
    private boolean autoExtraction = true;

    /**
     * 检索时的最大跳数（图遍历深度）
     */
    private int maxHops = 2;

    /**
     * 返回最重要的实体数量
     */
    private int topEntities = 5;

    /**
     * 知识图谱的内存键
     */
    private String graphMemoryKey = "knowledge_graph";

    /**
     * 最近对话的内存键
     */
    private String conversationMemoryKey = "conversation";

    /**
     * 时间衰减因子（每天）
     */
    private double decayFactor = 0.99;

    /**
     * 弱关系修剪阈值
     */
    private double pruneThreshold = 0.1;

    /**
     * 会话实体跟踪（sessionId -> 会话中涉及的实体）
     */
    private Map<String, Set<String>> sessionEntities = new HashMap<>();

    public KnowledgeGraphMemory() {
        this.graphStore = new InMemoryGraphStore();
        this.extractor = new KnowledgeExtractor();
        this.chatMemory = new ConversationBufferMemory();
    }

    public KnowledgeGraphMemory(BaseChatModel llm) {
        this.graphStore = new InMemoryGraphStore();
        this.extractor = new KnowledgeExtractor(llm);
        this.chatMemory = new ConversationBufferMemory();
    }

    public KnowledgeGraphMemory(GraphStore graphStore, KnowledgeExtractor extractor) {
        this.graphStore = graphStore;
        this.extractor = extractor;
        this.chatMemory = new ConversationBufferMemory();
    }

    /**
     * Builder模式
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private BaseChatModel llm;
        private GraphStore graphStore;
        private KnowledgeExtractor extractor;
        private BaseChatMemory chatMemory;
        private boolean autoExtraction = true;
        private int maxHops = 2;
        private int topEntities = 5;
        private double decayFactor = 0.99;
        private double pruneThreshold = 0.1;

        public Builder llm(BaseChatModel llm) {
            this.llm = llm;
            return this;
        }

        public Builder graphStore(GraphStore graphStore) {
            this.graphStore = graphStore;
            return this;
        }

        public Builder extractor(KnowledgeExtractor extractor) {
            this.extractor = extractor;
            return this;
        }

        public Builder chatMemory(BaseChatMemory chatMemory) {
            this.chatMemory = chatMemory;
            return this;
        }

        public Builder autoExtraction(boolean autoExtraction) {
            this.autoExtraction = autoExtraction;
            return this;
        }

        public Builder maxHops(int maxHops) {
            this.maxHops = maxHops;
            return this;
        }

        public Builder topEntities(int topEntities) {
            this.topEntities = topEntities;
            return this;
        }

        public Builder decayFactor(double decayFactor) {
            this.decayFactor = decayFactor;
            return this;
        }

        public Builder pruneThreshold(double pruneThreshold) {
            this.pruneThreshold = pruneThreshold;
            return this;
        }

        public KnowledgeGraphMemory build() {
            KnowledgeGraphMemory memory = new KnowledgeGraphMemory();

            // 设置图谱存储
            if (graphStore != null) {
                memory.setGraphStore(graphStore);
            } else {
                memory.setGraphStore(new InMemoryGraphStore());
            }

            // 设置知识抽取器
            if (extractor != null) {
                memory.setExtractor(extractor);
            } else if (llm != null) {
                memory.setExtractor(new KnowledgeExtractor(llm));
            } else {
                memory.setExtractor(new KnowledgeExtractor());
            }

            // 设置对话记忆
            if (chatMemory != null) {
                memory.setChatMemory(chatMemory);
            } else {
                memory.setChatMemory(new ConversationBufferMemory());
            }

            memory.setAutoExtraction(autoExtraction);
            memory.setMaxHops(maxHops);
            memory.setTopEntities(topEntities);
            memory.setDecayFactor(decayFactor);
            memory.setPruneThreshold(pruneThreshold);

            return memory;
        }
    }

    @Override
    public List<String> memoryVariables() {
        return Arrays.asList(graphMemoryKey, conversationMemoryKey);
    }

    @Override
    public Map<String, Object> loadMemoryVariables(String sessionId, Map<String, Object> inputs) {
        Map<String, Object> result = new HashMap<>();

        // 1. 加载最近对话
        if (chatMemory != null) {
            Map<String, Object> chatContext = chatMemory.loadMemoryVariables(sessionId, inputs);
            result.put(conversationMemoryKey, chatContext.get(chatMemory.getMemoryKey()));
        }

        // 2. 加载相关知识图谱
        String knowledgeContext = loadKnowledgeContext(sessionId, inputs);
        result.put(graphMemoryKey, knowledgeContext);

        return result;
    }

    /**
     * 加载相关的知识上下文
     */
    private String loadKnowledgeContext(String sessionId, Map<String, Object> inputs) {
        // 获取当前会话相关的实体
        Set<String> relevantEntityIds = getRelevantEntityIds(sessionId, inputs);

        if (relevantEntityIds.isEmpty()) {
            return "暂无相关知识";
        }

        StringBuilder context = new StringBuilder();
        context.append("相关知识：\n");

        // 对每个相关实体，获取其子图
        Set<SubGraph> subGraphs = new HashSet<>();
        for (String entityId : relevantEntityIds) {
            SubGraph subGraph = graphStore.getSubGraph(entityId, maxHops);
            if (!subGraph.isEmpty()) {
                subGraphs.add(subGraph);
            }
        }

        // 转换为自然语言
        for (SubGraph subGraph : subGraphs) {
            context.append(subGraph.toNaturalLanguage()).append("\n");
        }

        // 添加重要实体信息
        List<Entity> topEntitiesList = graphStore.getTopEntities(topEntities);
        if (!topEntitiesList.isEmpty()) {
            context.append("\n重要实体：\n");
            for (Entity entity : topEntitiesList) {
                context.append(String.format("- %s (%s)\n",
                    entity.getName(),
                    entity.getType()));
            }
        }

        return context.toString();
    }

    /**
     * 获取相关实体ID
     */
    private Set<String> getRelevantEntityIds(String sessionId, Map<String, Object> inputs) {
        Set<String> entityIds = new HashSet<>();

        // 1. 从会话跟踪中获取
        if (sessionId != null && sessionEntities.containsKey(sessionId)) {
            entityIds.addAll(sessionEntities.get(sessionId));
        }

        // 2. 从当前输入中提取
        if (inputs != null && !inputs.isEmpty()) {
            String inputText = extractInputText(inputs);
            if (!inputText.isEmpty()) {
                List<Entity> inputEntities = extractEntitiesFromText(inputText);
                for (Entity entity : inputEntities) {
                    // 在图谱中查找匹配的实体
                    Entity existing = graphStore.getEntityByNameAndType(entity.getName(), entity.getType());
                    if (existing != null) {
                        entityIds.add(existing.getId());
                    }
                }
            }
        }

        return entityIds;
    }

    /**
     * 从输入中提取文本
     */
    private String extractInputText(Map<String, Object> inputs) {
        StringBuilder text = new StringBuilder();

        for (Object value : inputs.values()) {
            if (value instanceof String) {
                text.append(value).append(" ");
            } else if (value instanceof List) {
                for (Object item : (List<?>) value) {
                    if (item instanceof BaseMessage) {
                        text.append(((BaseMessage) item).getContent()).append(" ");
                    }
                }
            }
        }

        return text.toString().trim();
    }

    /**
     * 从文本中提取实体
     */
    private List<Entity> extractEntitiesFromText(String text) {
        KnowledgeExtractor.ExtractionResult result = extractor.extractFromText(text);
        return result.getEntities();
    }

    @Override
    public void saveContext(String sessionId, Map<String, Object> inputs, Map<String, Object> outputs) {
        // 1. 保存到对话记忆
        if (chatMemory != null) {
            chatMemory.saveContext(sessionId, inputs, outputs);
        }

        // 2. 自动抽取知识（如果启用）
        if (autoExtraction) {
            extractAndSaveKnowledge(sessionId, inputs, outputs);
        }
    }

    /**
     * 抽取并保存知识
     */
    private void extractAndSaveKnowledge(String sessionId, Map<String, Object> inputs, Map<String, Object> outputs) {
        try {
            // 获取对话消息
            List<BaseMessage> messages = new ArrayList<>();

            // 从inputs中提取消息
            for (Object value : inputs.values()) {
                if (value instanceof String) {
                    messages.add(new HumanMessage((String) value));
                } else if (value instanceof List) {
                    for (Object item : (List<?>) value) {
                        if (item instanceof BaseMessage) {
                            messages.add((BaseMessage) item);
                        }
                    }
                }
            }

            // 从outputs中提取消息
            for (Object value : outputs.values()) {
                if (value instanceof String) {
                    messages.add(new HumanMessage((String) value));
                } else if (value instanceof List) {
                    for (Object item : (List<?>) value) {
                        if (item instanceof BaseMessage) {
                            messages.add((BaseMessage) item);
                        }
                    }
                }
            }

            if (messages.isEmpty()) {
                return;
            }

            // 抽取知识
            KnowledgeExtractor.ExtractionResult result = extractor.extractFromMessages(messages);

            // 保存实体
            for (Entity entity : result.getEntities()) {
                graphStore.addEntity(entity);

                // 跟踪会话实体
                if (sessionId != null) {
                    sessionEntities.computeIfAbsent(sessionId, k -> new HashSet<>())
                        .add(entity.getId());
                }
            }

            // 保存关系
            for (Relation relation : result.getRelations()) {
                graphStore.addRelation(relation);
            }

            log.debug("Knowledge extraction completed: {} entities, {} relations",
                result.getEntities().size(), result.getRelations().size());

        } catch (Exception e) {
            log.error("Failed to extract knowledge", e);
        }
    }

    @Override
    public void clear(String sessionId) {
        if (sessionId == null) {
            // 清空所有
            graphStore.clear();
            sessionEntities.clear();
            if (chatMemory != null) {
                chatMemory.clear(null);
            }
        } else {
            // 只清空会话相关的实体跟踪
            sessionEntities.remove(sessionId);
            if (chatMemory != null) {
                chatMemory.clear(sessionId);
            }
        }
    }

    /**
     * 应用时间衰减
     */
    public void applyTimeDecay() {
        graphStore.applyTimeDecay(decayFactor);
    }

    /**
     * 修剪弱关系
     */
    public int pruneWeakRelations() {
        return graphStore.pruneWeakRelations(pruneThreshold);
    }

    /**
     * 手动添加实体
     */
    public void addEntity(Entity entity) {
        graphStore.addEntity(entity);
    }

    /**
     * 手动添加关系
     */
    public void addRelation(Relation relation) {
        graphStore.addRelation(relation);
    }

    /**
     * 查询实体
     */
    public Entity getEntity(String entityId) {
        return graphStore.getEntity(entityId);
    }

    /**
     * 搜索实体
     */
    public List<Entity> searchEntities(String keyword) {
        return graphStore.searchEntities(keyword);
    }

    /**
     * 获取实体的子图
     */
    public SubGraph getSubGraph(String entityId, int depth) {
        return graphStore.getSubGraph(entityId, depth);
    }

    /**
     * 查找最短路径
     */
    public List<Object> findShortestPath(String sourceId, String targetId) {
        return graphStore.findShortestPath(sourceId, targetId);
    }

    /**
     * 获取图谱统计信息
     */
    public Map<String, Object> getStatistics() {
        return graphStore.getStatistics();
    }

    /**
     * 获取图谱存储（用于高级操作）
     */
    public GraphStore getGraphStore() {
        return graphStore;
    }
}

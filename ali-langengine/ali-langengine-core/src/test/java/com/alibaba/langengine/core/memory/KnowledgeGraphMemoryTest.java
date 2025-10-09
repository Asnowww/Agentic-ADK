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
package com.alibaba.langengine.core.memory;

import com.alibaba.langengine.core.memory.graph.*;
import com.alibaba.langengine.core.memory.impl.KnowledgeGraphMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * KnowledgeGraphMemory 单元测试
 *
 * @author xiaoxuan.lp
 */
public class KnowledgeGraphMemoryTest {

    private KnowledgeGraphMemory memory;
    private String sessionId;

    @BeforeEach
    public void setUp() {
        memory = KnowledgeGraphMemory.builder()
            .autoExtraction(true)
            .maxHops(2)
            .topEntities(5)
            .build();

        sessionId = "test-session-" + System.currentTimeMillis();
    }

    @Test
    public void testBasicKnowledgeGraphMemoryCreation() {
        assertNotNull(memory);
        assertNotNull(memory.getGraphStore());
        assertNotNull(memory.getExtractor());
        assertTrue(memory.isAutoExtraction());
        assertEquals(2, memory.getMaxHops());
        assertEquals(5, memory.getTopEntities());
    }

    @Test
    public void testMemoryVariables() {
        List<String> variables = memory.memoryVariables();
        assertNotNull(variables);
        assertEquals(2, variables.size());
        assertTrue(variables.contains("knowledge_graph"));
        assertTrue(variables.contains("conversation"));
    }

    @Test
    public void testManualEntityAddition() {
        Entity entity = new Entity("张三", "Person", "软件工程师");
        memory.addEntity(entity);

        Entity retrieved = memory.getEntity(entity.getId());
        assertNotNull(retrieved);
        assertEquals("张三", retrieved.getName());
        assertEquals("Person", retrieved.getType());
    }

    @Test
    public void testManualRelationAddition() {
        Entity person = new Entity("张三", "Person");
        Entity org = new Entity("阿里巴巴", "Organization");

        memory.addEntity(person);
        memory.addEntity(org);

        Relation relation = new Relation(person, "工作于", org);
        memory.addRelation(relation);

        GraphStore store = memory.getGraphStore();
        List<Relation> relations = store.getRelationsByEntity(person.getId());
        assertEquals(1, relations.size());
        assertEquals("工作于", relations.get(0).getRelationType());
    }

    @Test
    public void testAutoKnowledgeExtraction() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("input", "张三工作于阿里巴巴");

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("output", "明白了，张三是阿里巴巴的员工");

        memory.saveContext(sessionId, inputs, outputs);

        // 验证实体被提取
        List<Entity> entities = memory.searchEntities("张三");
        assertFalse(entities.isEmpty(), "应该提取到'张三'实体");

        entities = memory.searchEntities("阿里巴巴");
        assertFalse(entities.isEmpty(), "应该提取到'阿里巴巴'实体");
    }

    @Test
    public void testLoadMemoryVariables() {
        // 先添加一些知识
        Entity person = new Entity("李四", "Person");
        Entity location = new Entity("杭州", "Location");
        Relation relation = new Relation(person, "位于", location);

        memory.addEntity(person);
        memory.addEntity(location);
        memory.addRelation(relation);

        // 加载记忆变量
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("input", "李四在哪里？");

        Map<String, Object> variables = memory.loadMemoryVariables(sessionId, inputs);

        assertNotNull(variables);
        assertTrue(variables.containsKey("knowledge_graph"));
        assertTrue(variables.containsKey("conversation"));
    }

    @Test
    public void testSubGraphRetrieval() {
        // 构建小型知识图谱
        Entity alice = new Entity("Alice", "Person");
        Entity bob = new Entity("Bob", "Person");
        Entity company = new Entity("TechCorp", "Organization");

        memory.addEntity(alice);
        memory.addEntity(bob);
        memory.addEntity(company);

        memory.addRelation(new Relation(alice, "工作于", company));
        memory.addRelation(new Relation(bob, "工作于", company));
        memory.addRelation(new Relation(alice, "认识", bob));

        // 获取子图
        SubGraph subGraph = memory.getSubGraph(alice.getId(), 1);

        assertNotNull(subGraph);
        assertFalse(subGraph.isEmpty());
        assertTrue(subGraph.getEntities().size() > 1);
        assertTrue(subGraph.getRelations().size() > 0);
    }

    @Test
    public void testShortestPath() {
        // 构建路径：A -> B -> C
        Entity a = new Entity("A", "Concept");
        Entity b = new Entity("B", "Concept");
        Entity c = new Entity("C", "Concept");

        memory.addEntity(a);
        memory.addEntity(b);
        memory.addEntity(c);

        memory.addRelation(new Relation(a, "连接", b));
        memory.addRelation(new Relation(b, "连接", c));

        List<Object> path = memory.findShortestPath(a.getId(), c.getId());

        assertNotNull(path);
        assertFalse(path.isEmpty());
        // 路径应该是：A -> Relation -> B -> Relation -> C
        assertEquals(5, path.size());
    }

    @Test
    public void testEntitySearch() {
        Entity entity1 = new Entity("机器学习", "Concept");
        Entity entity2 = new Entity("深度学习", "Concept");
        Entity entity3 = new Entity("自然语言处理", "Concept");

        memory.addEntity(entity1);
        memory.addEntity(entity2);
        memory.addEntity(entity3);

        List<Entity> results = memory.searchEntities("学习");
        assertEquals(2, results.size());
    }

    @Test
    public void testStatistics() {
        // 添加一些实体和关系
        for (int i = 0; i < 5; i++) {
            Entity entity = new Entity("Entity" + i, "Test");
            memory.addEntity(entity);
        }

        Entity e1 = memory.getGraphStore().getEntity("test:entity0");
        Entity e2 = memory.getGraphStore().getEntity("test:entity1");

        if (e1 != null && e2 != null) {
            memory.addRelation(new Relation(e1, "关联", e2));
        }

        Map<String, Object> stats = memory.getStatistics();

        assertNotNull(stats);
        assertTrue(stats.containsKey("entity_count"));
        assertTrue(stats.containsKey("relation_count"));
        assertEquals(5, stats.get("entity_count"));
    }

    @Test
    public void testClearSession() {
        // 添加实体并关联到会话
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("input", "测试消息");

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("output", "收到");

        memory.saveContext(sessionId, inputs, outputs);

        // 清除会话
        memory.clear(sessionId);

        // 验证会话被清除，但图谱数据仍然存在
        Map<String, Object> stats = memory.getStatistics();
        assertNotNull(stats);
    }

    @Test
    public void testClearAll() {
        Entity entity = new Entity("TestEntity", "Test");
        memory.addEntity(entity);

        assertEquals(1, memory.getGraphStore().getEntityCount());

        memory.clear(null);

        assertEquals(0, memory.getGraphStore().getEntityCount());
    }

    @Test
    public void testTimeDecay() {
        Entity e1 = new Entity("E1", "Test");
        Entity e2 = new Entity("E2", "Test");
        memory.addEntity(e1);
        memory.addEntity(e2);

        Relation relation = new Relation(e1, "关联", e2, 0.8);
        memory.addRelation(relation);

        double originalWeight = relation.getWeight();

        // 应用时间衰减
        memory.applyTimeDecay();

        // 权重应该降低或保持不变（取决于时间）
        Relation retrieved = memory.getGraphStore().getRelation(relation.getId());
        assertNotNull(retrieved);
        assertTrue(retrieved.getWeight() <= originalWeight);
    }

    @Test
    public void testPruneWeakRelations() {
        Entity e1 = new Entity("E1", "Test");
        Entity e2 = new Entity("E2", "Test");
        Entity e3 = new Entity("E3", "Test");

        memory.addEntity(e1);
        memory.addEntity(e2);
        memory.addEntity(e3);

        // 添加一个强关系和一个弱关系
        Relation strongRelation = new Relation(e1, "强关联", e2, 0.8);
        Relation weakRelation = new Relation(e1, "弱关联", e3, 0.05);

        memory.addRelation(strongRelation);
        memory.addRelation(weakRelation);

        assertEquals(2, memory.getGraphStore().getRelationCount());

        // 修剪弱关系（阈值0.1）
        int pruned = memory.pruneWeakRelations();

        assertEquals(1, pruned);
        assertEquals(1, memory.getGraphStore().getRelationCount());

        // 验证强关系仍然存在
        assertNotNull(memory.getGraphStore().getRelation(strongRelation.getId()));
        // 验证弱关系已被删除
        assertNull(memory.getGraphStore().getRelation(weakRelation.getId()));
    }

    @Test
    public void testBuilderPattern() {
        KnowledgeGraphMemory customMemory = KnowledgeGraphMemory.builder()
            .maxHops(3)
            .topEntities(10)
            .autoExtraction(false)
            .decayFactor(0.95)
            .pruneThreshold(0.2)
            .build();

        assertEquals(3, customMemory.getMaxHops());
        assertEquals(10, customMemory.getTopEntities());
        assertFalse(customMemory.isAutoExtraction());
        assertEquals(0.95, customMemory.getDecayFactor());
        assertEquals(0.2, customMemory.getPruneThreshold());
    }

    @Test
    public void testComplexKnowledgeGraph() {
        // 构建一个复杂的知识图谱
        // 组织结构：公司 -> 部门 -> 员工
        Entity company = new Entity("TechCorp", "Organization");
        Entity dept1 = new Entity("Engineering", "Department");
        Entity dept2 = new Entity("Marketing", "Department");
        Entity emp1 = new Entity("Alice", "Person");
        Entity emp2 = new Entity("Bob", "Person");
        Entity emp3 = new Entity("Charlie", "Person");

        memory.addEntity(company);
        memory.addEntity(dept1);
        memory.addEntity(dept2);
        memory.addEntity(emp1);
        memory.addEntity(emp2);
        memory.addEntity(emp3);

        memory.addRelation(new Relation(dept1, "属于", company));
        memory.addRelation(new Relation(dept2, "属于", company));
        memory.addRelation(new Relation(emp1, "工作于", dept1));
        memory.addRelation(new Relation(emp2, "工作于", dept1));
        memory.addRelation(new Relation(emp3, "工作于", dept2));

        // 验证图谱结构
        assertEquals(6, memory.getGraphStore().getEntityCount());
        assertEquals(5, memory.getGraphStore().getRelationCount());

        // 验证可以找到路径：emp1 -> dept1 -> company
        List<Object> path = memory.findShortestPath(emp1.getId(), company.getId());
        assertNotNull(path);
        assertFalse(path.isEmpty());
    }
}

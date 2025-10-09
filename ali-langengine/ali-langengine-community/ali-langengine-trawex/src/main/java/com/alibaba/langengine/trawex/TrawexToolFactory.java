package com.alibaba.langengine.trawex;

import com.alibaba.langengine.core.tool.BaseTool;
import com.alibaba.langengine.trawex.service.TrawexClient;
import com.alibaba.langengine.trawex.tool.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Trawex 工具工厂
 * 用于创建和管理 Trawex 相关工具
 * 
 * @author AIDC-AI
 */
public class TrawexToolFactory {
    
    private final TrawexClient client;
    
    public TrawexToolFactory() {
        this.client = new TrawexClient();
    }
    
    public TrawexToolFactory(String apiKey, String apiSecret, String baseUrl) {
        this.client = new TrawexClient(apiKey, apiSecret, baseUrl);
    }
    
    public TrawexToolFactory(TrawexClient client) {
        this.client = client;
    }
    
    /**
     * 创建酒店搜索工具
     */
    public TrawexHotelSearchTool createHotelSearchTool() {
        return new TrawexHotelSearchTool(client);
    }
    
    /**
     * 创建酒店详情工具
     */
    public TrawexHotelDetailsTool createHotelDetailsTool() {
        return new TrawexHotelDetailsTool(client);
    }
    
    /**
     * 创建航班搜索工具
     */
    public TrawexFlightSearchTool createFlightSearchTool() {
        return new TrawexFlightSearchTool(client);
    }
    
    /**
     * 获取所有酒店相关工具
     */
    public List<BaseTool> getHotelTools() {
        List<BaseTool> tools = new ArrayList<>();
        tools.add(createHotelSearchTool());
        tools.add(createHotelDetailsTool());
        return tools;
    }
    
    /**
     * 获取所有航班相关工具
     */
    public List<BaseTool> getFlightTools() {
        List<BaseTool> tools = new ArrayList<>();
        tools.add(createFlightSearchTool());
        return tools;
    }
    
    /**
     * 获取所有工具
     */
    public List<BaseTool> getAllTools() {
        List<BaseTool> tools = new ArrayList<>();
        tools.add(createHotelSearchTool());
        tools.add(createHotelDetailsTool());
        tools.add(createFlightSearchTool());
        return tools;
    }
    
    /**
     * 根据名称获取工具
     */
    public BaseTool getToolByName(String toolName) {
        switch (toolName) {
            case "Trawex.search_hotels":
                return createHotelSearchTool();
            case "Trawex.get_hotel_details":
                return createHotelDetailsTool();
            case "Trawex.search_flights":
                return createFlightSearchTool();
            default:
                throw new IllegalArgumentException("Unknown tool name: " + toolName);
        }
    }
}

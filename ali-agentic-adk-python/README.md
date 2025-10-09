# use
````
 pip install ali-agentic-adk-python
````

## MCP Extension

```python
import asyncio

from ali_agentic_adk_python.extension.mcp import (
    McpSessionManager,
    McpStdioServerConfig,
)


async def main() -> list:
    manager = McpSessionManager(
        servers=[
            McpStdioServerConfig(
                namespace="filesystem",
                command="uvx",
                args=["modelcontextprotocol/server-filesystem"],
            )
        ]
    )
    await manager.start()
    return manager.build_google_adk_tools()


mcp_tools = asyncio.run(main())
```

# License
Apache-2.0
Copyright (C) 2025 AIDC-AI All rights reserved.

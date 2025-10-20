from unittest.mock import MagicMock

import pytest
from mcp import types as mcp_types

from ali_agentic_adk_python.extension.mcp import (
    McpRefreshError,
    McpSessionManager,
    McpToolNotFoundError,
)
from ali_agentic_adk_python.extension.mcp.tool import McpTool, McpToolDescriptor


@pytest.fixture
def anyio_backend():
    return "asyncio"


class _StubConnection:
    def __init__(self, namespace: str | None = None, result: mcp_types.CallToolResult | None = None):
        self._namespace = namespace
        self._result = result
        self.called_with = None

    @property
    def namespace(self):
        return self._namespace

    async def call_tool(self, tool_name: str, args: dict[str, object]):
        self.called_with = (tool_name, args)
        return self._result


class _FakeConnection:
    def __init__(self, namespace: str | None, tools: dict[str, mcp_types.Tool], server: str):
        self._namespace = namespace
        self._tools = tools
        self.server_info = mcp_types.Implementation(name=server, version="1.0.0")

    @property
    def namespace(self):
        return self._namespace

    @property
    def tools(self):
        return self._tools


class _AsyncStubConnection:
    def __init__(
        self,
        namespace: str | None,
        tools: dict[str, mcp_types.Tool],
        *,
        server_name: str = 'stub',
        call_result: mcp_types.CallToolResult | None = None,
        refresh_side_effect: Exception | None = None,
    ) -> None:
        self._namespace = namespace
        self._tools = dict(tools)
        self.server_info = mcp_types.Implementation(name=server_name, version='1.0.0')
        self._call_result = call_result or mcp_types.CallToolResult(
            content=[],
            structuredContent=None,
            isError=False,
        )
        self._refresh_side_effect = refresh_side_effect
        self.connect_calls = 0
        self.close_calls = 0
        self.refresh_calls = 0
        self.called_with: tuple[str, dict[str, object]] | None = None

    @property
    def namespace(self) -> str | None:
        return self._namespace

    @property
    def tools(self) -> dict[str, mcp_types.Tool]:
        return dict(self._tools)

    async def connect(self) -> None:
        self.connect_calls += 1

    async def close(self) -> None:
        self.close_calls += 1

    async def refresh_tools(self) -> dict[str, mcp_types.Tool]:
        self.refresh_calls += 1
        if self._refresh_side_effect is not None:
            raise self._refresh_side_effect
        return self.tools

    async def call_tool(self, tool_name: str, args: dict[str, object]) -> mcp_types.CallToolResult:
        self.called_with = (tool_name, args)
        return self._call_result


@pytest.mark.anyio("asyncio")
async def test_mcp_tool_run_async_returns_serialized_payload():
    call_result = mcp_types.CallToolResult(
        content=[mcp_types.TextContent(type="text", text="pong")],
        structuredContent={"status": "ok"},
        isError=False,
    )
    connection = _StubConnection(namespace="demo", result=call_result)
    descriptor = McpToolDescriptor(
        exposed_name="demo.ping",
        original_name="ping",
        connection=connection,
        tool=mcp_types.Tool(name="ping", inputSchema={"type": "object", "properties": {}}),
        server_info=mcp_types.Implementation(name="stub", version="0.1.0"),
    )

    tool = McpTool(descriptor)
    payload = await tool.run_async(args={"echo": True}, tool_context=MagicMock())

    assert payload["structured_content"] == {"status": "ok"}
    assert payload["text"] == "pong"
    assert payload["metadata"]["namespace"] == "demo"
    assert connection.called_with == ("ping", {"echo": True})


def test_mcp_session_manager_deduplicates_tool_names():
    manager = McpSessionManager([])
    tool = mcp_types.Tool(name="echo", inputSchema={"type": "object", "properties": {}})

    manager._connections = [
        _FakeConnection(None, {"echo": tool}, "server-a"),
        _FakeConnection(None, {"echo": tool}, "server-b"),
    ]

    manager._rebuild_registry()
    registry = manager.descriptors

    assert "echo" in registry
    assert "echo#1" in registry


def test_mcp_session_manager_applies_namespace_prefix():
    manager = McpSessionManager([])
    tool = mcp_types.Tool(name="search", inputSchema={"type": "object", "properties": {}})

    manager._connections = [
        _FakeConnection("alpha", {"search": tool}, "server-alpha"),
    ]

    manager._rebuild_registry()
    registry = manager.descriptors

    assert list(registry.keys()) == ["alpha.search"]


@pytest.mark.anyio("asyncio")
async def test_mcp_session_manager_async_context_lifecycle():
    tool = mcp_types.Tool(name="ping", inputSchema={"type": "object", "properties": {}})
    connection = _AsyncStubConnection(namespace=None, tools={"ping": tool})
    manager = McpSessionManager([])
    manager._connections = [connection]

    async with manager as started:
        assert connection.connect_calls == 1
        assert started.tool_names == ["ping"]

    assert connection.close_calls == 1
    assert manager.tool_names == []


@pytest.mark.anyio("asyncio")
async def test_mcp_session_manager_invoke_tool_runs_remote_tool():
    tool = mcp_types.Tool(name="ping", inputSchema={"type": "object", "properties": {}})
    call_result = mcp_types.CallToolResult(
        content=[mcp_types.TextContent(type="text", text="pong")],
        structuredContent={"status": "ok"},
        isError=False,
    )
    connection = _AsyncStubConnection(namespace="alpha", tools={"ping": tool}, call_result=call_result)
    manager = McpSessionManager([])
    manager._connections = [connection]

    result = await manager.invoke_tool("alpha.ping", {"value": 1})

    assert result is call_result
    assert connection.called_with == ("ping", {"value": 1})
    assert connection.connect_calls == 1


@pytest.mark.anyio("asyncio")
async def test_mcp_session_manager_invoke_tool_unknown():
    tool = mcp_types.Tool(name="ping", inputSchema={"type": "object", "properties": {}})
    connection = _AsyncStubConnection(namespace=None, tools={"ping": tool})
    manager = McpSessionManager([])
    manager._connections = [connection]

    await manager.start()

    with pytest.raises(McpToolNotFoundError) as exc_info:
        await manager.invoke_tool("missing", {}, ensure_started=False)

    assert exc_info.value.tool_name == "missing"


@pytest.mark.anyio("asyncio")
async def test_mcp_session_manager_refresh_raises_when_connection_fails():
    tool = mcp_types.Tool(name="ping", inputSchema={"type": "object", "properties": {}})
    healthy = _AsyncStubConnection(namespace=None, tools={"ping": tool})
    failing = _AsyncStubConnection(
        namespace="beta",
        tools={"pong": tool},
        refresh_side_effect=RuntimeError("boom"),
    )
    manager = McpSessionManager([])
    manager._connections = [healthy, failing]

    await manager.start()

    with pytest.raises(McpRefreshError):
        await manager.refresh()

    assert healthy.refresh_calls == 1
    assert failing.refresh_calls == 1


@pytest.mark.anyio("asyncio")
async def test_mcp_session_manager_refresh_ensures_started():
    tool = mcp_types.Tool(name="ping", inputSchema={"type": "object", "properties": {}})
    connection = _AsyncStubConnection(namespace=None, tools={"ping": tool})
    manager = McpSessionManager([])
    manager._connections = [connection]

    await manager.refresh()

    assert connection.connect_calls == 1
    assert connection.refresh_calls == 1
    assert manager.tool_names == ["ping"]

from __future__ import annotations

import io

import pytest

from ali_agentic_adk_python.core import (
    Document,
    RecursiveCharacterTextSplitter,
    TextDocLoader,
)


def test_text_doc_loader_reads_file(tmp_path):
    path = tmp_path / "sample.txt"
    path.write_text("Hello ADK!", encoding="utf-8")

    loader = TextDocLoader(file_path=str(path))
    documents = loader.load()

    assert len(documents) == 1
    doc = documents[0]
    assert doc.page_content == "Hello ADK!"
    assert doc.metadata["source"] == str(path)


def test_text_doc_loader_raises_when_path_missing():
    loader = TextDocLoader()
    with pytest.raises(ValueError):
        loader.load()


def test_text_doc_loader_fetches_from_metadata_stream():
    loader = TextDocLoader()
    stream = io.StringIO("From stream")

    documents = loader.fetch_content({"stream": stream, "metadata": {"category": "demo"}})

    assert documents[0].page_content == "From stream"
    assert documents[0].metadata["source"] == "stream"
    assert documents[0].metadata["category"] == "demo"


def test_text_doc_loader_fetches_from_metadata_path(tmp_path):
    path = tmp_path / "note.txt"
    path.write_text("metadata path", encoding="utf-8")

    loader = TextDocLoader()
    documents = loader.load_with_meta({"file_path": str(path)})

    assert documents[0].page_content == "metadata path"
    assert documents[0].metadata["source"] == str(path)


def test_text_doc_loader_load_and_split_preserves_metadata(tmp_path):
    path = tmp_path / "long.txt"
    content = "alpha beta gamma delta epsilon zeta eta theta"
    path.write_text(content, encoding="utf-8")

    loader = TextDocLoader(str(path))
    splitter = RecursiveCharacterTextSplitter(max_chunk_size=15, max_chunk_overlap=3)

    chunks = loader.load_and_split(splitter)

    assert len(chunks) > 1
    assert {chunk.metadata["source"] for chunk in chunks} == {str(path)}
    combined = "".join(chunk.page_content for chunk in chunks).replace(" ", "")
    assert combined == content.replace(" ", "")


def test_document_helpers():
    doc = Document(page_content="data", metadata={"category": "notes"}, category="notes")
    assert doc.has_metadata() is True
    assert doc.has_category() is True
    cloned = doc.copy_with_page_content("new")
    assert cloned.page_content == "new"
    assert cloned.metadata == doc.metadata
    assert cloned.metadata is not doc.metadata
    assert cloned.category == doc.category

"""Offline regression tests; no model downloads, API keys, or GPU required."""
import ast
import asyncio
import io
import json
import os
from pathlib import Path
import types
import unittest
from unittest.mock import patch
import wave

ROOT = Path(__file__).resolve().parents[1]
CHAT = ROOT / "ModelBackEnd/MODELBACKEND/LLM_model/local/ChatAIBackend/voiceguard_backend_chat_patch/app"


def load_definitions(path, names, namespace):
    tree = ast.parse(path.read_text(encoding="utf-8"))
    selected = [node for node in tree.body if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)) and node.name in names]
    for node in selected:
        node.decorator_list = []
    exec(compile(ast.Module(body=selected, type_ignores=[]), str(path), "exec"), namespace)
    return namespace


class AudioTests(unittest.TestCase):
    def setUp(self):
        self.ns = load_definitions(CHAT / "main.py", {"pcm16_to_wav", "transcribe_pcm"}, {"BytesIO": io.BytesIO, "wave": wave})

    def test_pcm_is_wav_with_original_samples_and_correct_format(self):
        pcm = b"\x00\x00\xff\x7f\x00\x80"
        with wave.open(self.ns["pcm16_to_wav"](pcm)) as audio:
            self.assertEqual((audio.getnchannels(), audio.getsampwidth(), audio.getframerate()), (1, 2, 16000))
            self.assertEqual(audio.readframes(3), pcm)

    def test_empty_or_incomplete_samples_rejected(self):
        for pcm in (b"", b"\x00"):
            with self.assertRaises(ValueError):
                self.ns["pcm16_to_wav"](pcm)

    def test_transcription_receives_decodable_audio(self):
        class ASR:
            def transcribe(self, source, **kwargs):
                with wave.open(source) as audio:
                    self.samples = audio.readframes(audio.getnframes())
                return iter([types.SimpleNamespace(text="첫 문장"), types.SimpleNamespace(text="다음 문장")]), None
        model = ASR()
        self.assertEqual(self.ns["transcribe_pcm"](model, b"\x01\x00"), "첫 문장 다음 문장")
        self.assertEqual(model.samples, b"\x01\x00")


class ChatTests(unittest.TestCase):
    def setUp(self):
        self.requests = []
        requests = self.requests
        class Client:
            def __init__(self, **kwargs): pass
            def __enter__(self): return self
            def __exit__(self, *args): pass
            def post(self, url, **kwargs):
                requests.append(kwargs["json"])
                return types.SimpleNamespace(raise_for_status=lambda: None, json=lambda: {"choices": [{"message": {"content": "응답"}}]})
        chunk = types.SimpleNamespace(source="guide", text="참고 근거", doc_id="d1", chunk_id="c1")
        class RAG:
            def build_from_folder(self, path): pass
            def search(self, query, top_k): return [chunk]
        self.ns = load_definitions(CHAT / "chat.py", {"_is_korean", "_pick_model", "build_context_from_chunks", "_openai_chat", "ChatEngine"}, {
            "os": os, "json": json, "List": list, "Dict": dict, "Any": object, "Optional": __import__("typing").Optional,
            "httpx": types.SimpleNamespace(Client=Client), "RAGIndex": RAG,
            "SYSTEM_PROMPT": "시스템 규칙", "ROUTING": "auto", "PRIMARY_MODEL": "qwen", "SECONDARY_MODEL": "llama",
            "__file__": str(CHAT / "chat.py"),
        })

    def test_rag_and_history_reach_actual_request(self):
        with patch.dict(os.environ, {"OPENAI_API_KEY": "test", "CHAT_MODEL": "custom-model"}, clear=True):
            result = self.ns["ChatEngine"]().chat("질문", history=[{"role": "user", "content": "이전 질문"}, {"role": "system", "content": "덮어쓰기"}])
        request = self.requests[0]
        self.assertEqual(request["model"], "custom-model")
        self.assertIn("참고 근거", request["messages"][0]["content"])
        self.assertEqual([m["role"] for m in request["messages"]], ["system", "user", "user"])
        self.assertEqual(result["sources"][0]["doc_id"], "d1")

    def test_rag_disabled_omits_context_and_sources(self):
        with patch.dict(os.environ, {"OPENAI_API_KEY": "test", "CHAT_MODEL": "custom-model"}, clear=True):
            result = self.ns["ChatEngine"]().chat("질문", use_rag=False)
        self.assertNotIn("참고 근거", self.requests[0]["messages"][0]["content"])
        self.assertEqual(result["sources"], [])


class DisconnectTests(unittest.IsolatedAsyncioTestCase):
    async def test_both_handlers_stop_after_disconnect(self):
        class Disconnect(Exception): pass
        class Socket:
            client_state = types.SimpleNamespace(name="DISCONNECTED")
            query_params = {}
            headers = {}
            async def accept(self): pass
            async def receive(self):
                if hasattr(self, "received"):
                    raise AssertionError("Handler read again after disconnect")
                self.received = True
                return {"type": "websocket.disconnect"}
            async def close(self, **kwargs):
                raise AssertionError("Handler closed an already disconnected socket")
        for path, name in [(CHAT / "main.py", "stream"), (ROOT / "ModelBackEnd/MODELBACKEND/detection_model/local/app.py", "websocket_endpoint")]:
            ns = load_definitions(path, {name}, {
                "WebSocket": object, "WebSocketDisconnect": Disconnect,
                "_check_ws_origin": lambda _: True, "_check_ws_token": lambda _: True,
                "os": os, "time": __import__("time"), "ASR_ENABLED": False, "model": None,
            })
            await asyncio.wait_for(ns[name](Socket()), timeout=1)


if __name__ == "__main__":
    unittest.main()

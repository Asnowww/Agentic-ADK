///**
// * Copyright (C) 2024 AIDC-AI
// * <p>
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// * <p>
// * http://www.apache.org/licenses/LICENSE-2.0
// * <p>
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//package com.alibaba.langengine.mcp.server.backup;
//
//import com.alibaba.langengine.mcp.spec.schema.prompts.GetPromptRequest;
//import com.alibaba.langengine.mcp.spec.schema.prompts.GetPromptResult;
//import com.alibaba.langengine.mcp.spec.schema.prompts.Prompt;
//
//import java.util.concurrent.CompletableFuture;
//import java.util.function.Function;
//
//public class RegisteredPrompt {
//
//    Prompt prompt;
//
//    Function<GetPromptRequest, CompletableFuture<GetPromptResult>> messageProvider;
//
//    public RegisteredPrompt(Prompt prompt, Function<GetPromptRequest, CompletableFuture<GetPromptResult>> messageProvider) {
//        this.prompt = prompt;
//        this.messageProvider = messageProvider;
//    }
//
//    public Prompt getPrompt() {
//        return prompt;
//    }
//
//    public void setPrompt(Prompt prompt) {
//        this.prompt = prompt;
//    }
//
//    public Function<GetPromptRequest, CompletableFuture<GetPromptResult>> getMessageProvider() {
//        return messageProvider;
//    }
//
//    public void setMessageProvider(Function<GetPromptRequest, CompletableFuture<GetPromptResult>> messageProvider) {
//        this.messageProvider = messageProvider;
//    }
//}

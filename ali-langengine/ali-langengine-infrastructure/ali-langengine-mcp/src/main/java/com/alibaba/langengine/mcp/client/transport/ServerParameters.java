/**
 * Copyright (C) 2024 AIDC-AI
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.langengine.mcp.client.transport;

import com.alibaba.langengine.mcp.util.Assert;
import lombok.Data;

import java.util.*;
import java.util.stream.Collectors;

@Data
public class ServerParameters {

    private static final List<String> DEFAULT_INHERITED_ENV_VARS = System.getProperty("os.name")
            .toLowerCase()
            .contains("win")
            ? Arrays.asList("APPDATA", "HOMEDRIVE", "HOMEPATH", "LOCALAPPDATA", "PATH", "PROCESSOR_ARCHITECTURE",
            "SYSTEMDRIVE", "SYSTEMROOT", "TEMP", "USERNAME", "USERPROFILE")
            : Arrays.asList("HOME", "LOGNAME", "PATH", "SHELL", "TERM", "USER");

    private String command;

    private List<String> args = new ArrayList<>();

    private Map<String, String> env;

    private ServerParameters(String command, List<String> args, Map<String, String> env) {
        this.command = command;
        this.args = args;
        this.env = new HashMap<>(getDefaultEnvironment());
        if (env != null && !env.isEmpty()) {
            this.env.putAll(env);
        }
    }

    public static Builder builder(String command) {
        return new Builder(command);
    }

    public static class Builder {

        private String command;

        private List<String> args = new ArrayList<>();

        private Map<String, String> env = new HashMap<>();

        public Builder(String command) {
            Assert.notNull(command, "The command can not be null");
            this.command = command;
        }

        public Builder args(String... args) {
            Assert.notNull(args, "The args can not be null");
            this.args = Arrays.asList(args);
            return this;
        }

        public Builder args(List<String> args) {
            Assert.notNull(args, "The args can not be null");
            this.args = new ArrayList<>(args);
            return this;
        }

        public Builder arg(String arg) {
            Assert.notNull(arg, "The arg can not be null");
            this.args.add(arg);
            return this;
        }

        public Builder env(Map<String, String> env) {
            if (env != null && !env.isEmpty()) {
                this.env.putAll(env);
            }
            return this;
        }

        public Builder addEnvVar(String key, String value) {
            Assert.notNull(key, "The key can not be null");
            Assert.notNull(value, "The value can not be null");
            this.env.put(key, value);
            return this;
        }

        public ServerParameters build() {
            return new ServerParameters(command, args, env);
        }

    }

    private static Map<String, String> getDefaultEnvironment() {
        return System.getenv()
                .entrySet()
                .stream()
                .filter(entry -> DEFAULT_INHERITED_ENV_VARS.contains(entry.getKey()))
                .filter(entry -> entry.getValue() != null)
                .filter(entry -> !entry.getValue().startsWith("()"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}

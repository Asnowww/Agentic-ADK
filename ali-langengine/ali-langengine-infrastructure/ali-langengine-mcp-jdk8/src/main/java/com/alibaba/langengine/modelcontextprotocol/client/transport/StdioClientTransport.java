/*
 * Copyright 2024-2024 the original author or authors.
 */

package com.alibaba.langengine.modelcontextprotocol.client.transport;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alibaba.langengine.modelcontextprotocol.spec.JSONRPCMessage;
import com.alibaba.langengine.modelcontextprotocol.spec.McpClientTransport;
import com.alibaba.langengine.modelcontextprotocol.spec.McpSchema;
import com.alibaba.langengine.modelcontextprotocol.util.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.SynchronousSink;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Implementation of the MCP Stdio transport that communicates with a server process using
 * standard input/output streams. Messages are exchanged as newline-delimited JSON-RPC
 * messages over stdin/stdout, with errors and debug information sent to stderr.
 *
 * @author Christian Tzolov
 * @author Dariusz Jędrzejczyk
 */
public class StdioClientTransport implements McpClientTransport {

    private static final Logger logger = LoggerFactory.getLogger(StdioClientTransport.class);

    private final Sinks.Many<JSONRPCMessage> inboundSink;

    private final Sinks.Many<JSONRPCMessage> outboundSink;

    /**
     * The server process being communicated with
     */
    private Process process;

    private ObjectMapper objectMapper;

    /**
     * Scheduler for handling inbound messages from the server process
     */
    private Scheduler inboundScheduler;

    /**
     * Scheduler for handling outbound messages to the server process
     */
    private Scheduler outboundScheduler;

    /**
     * Scheduler for handling error messages from the server process
     */
    private Scheduler errorScheduler;

    /**
     * Parameters for configuring and starting the server process
     */
    private final ServerParameters params;

    private final Sinks.Many<String> errorSink;

    private volatile boolean isClosing = false;

    // visible for tests
    private Consumer<String> stdErrorHandler = new Consumer<String>() {
        @Override
        public void accept(String error) {
            logger.info("STDERR Message received: {}", error);
        }
    };

    /**
     * Creates a new StdioClientTransport with the specified parameters and default
     * ObjectMapper.
     *
     * @param params The parameters for configuring the server process
     */
    public StdioClientTransport(ServerParameters params) {
        this(params, new ObjectMapper());
    }

    /**
     * Creates a new StdioClientTransport with the specified parameters and ObjectMapper.
     *
     * @param params       The parameters for configuring the server process
     * @param objectMapper The ObjectMapper to use for JSON serialization/deserialization
     */
    public StdioClientTransport(ServerParameters params, ObjectMapper objectMapper) {
        Assert.notNull(params, "The params can not be null");
        Assert.notNull(objectMapper, "The ObjectMapper can not be null");

        this.inboundSink = Sinks.many().unicast().onBackpressureBuffer();
        this.outboundSink = Sinks.many().unicast().onBackpressureBuffer();

        this.params = params;

        // Configure ObjectMapper to handle non-standard getter methods
        this.objectMapper = configureObjectMapper(objectMapper);

        this.errorSink = Sinks.many().unicast().onBackpressureBuffer();

        // Start threads
        this.inboundScheduler = Schedulers.fromExecutorService(Executors.newSingleThreadExecutor(), "inbound");
        this.outboundScheduler = Schedulers.fromExecutorService(Executors.newSingleThreadExecutor(), "outbound");
        this.errorScheduler = Schedulers.fromExecutorService(Executors.newSingleThreadExecutor(), "error");
    }

    /**
     * Starts the server process and initializes the message processing streams. This
     * method sets up the process with the configured command, arguments, and environment,
     * then starts the inbound, outbound, and error processing threads.
     *
     * @throws RuntimeException if the process fails to start or if the process streams
     *                          are null
     */
    @Override
    public CompletableFuture<Void> connect(Function<CompletableFuture<JSONRPCMessage>, CompletableFuture<JSONRPCMessage>> handler) {
        return CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                handleIncomingMessages(handler);
                handleIncomingErrors();

                // Prepare command and environment
                List<String> fullCommand = new ArrayList<String>();
                fullCommand.add(params.getCommand());
                fullCommand.addAll(params.getArgs());

                ProcessBuilder processBuilder = getProcessBuilder();
                processBuilder.command(fullCommand);
                processBuilder.environment().putAll(params.getEnv());

                // Start the process
                try {
                    process = processBuilder.start();
                } catch (IOException e) {
                    throw new RuntimeException("Failed to start process with command: " + fullCommand, e);
                }

                // Validate process streams
                if (process.getInputStream() == null || process.getOutputStream() == null) {
                    process.destroy();
                    throw new RuntimeException("Process input or output stream is null");
                }

                // Start threads
                startInboundProcessing();
                startOutboundProcessing();
                startErrorProcessing();
            }
        });
    }

    /**
     * Creates and returns a new ProcessBuilder instance. Protected to allow overriding in
     * tests.
     *
     * @return A new ProcessBuilder instance
     */
    protected ProcessBuilder getProcessBuilder() {
        return new ProcessBuilder();
    }

    /**
     * Sets the handler for processing transport-level errors.
     *
     * <p>
     * The provided handler will be called when errors occur during transport operations,
     * such as connection failures or protocol violations.
     * </p>
     *
     * @param errorHandler a consumer that processes error messages
     */
    public void setStdErrorHandler(Consumer<String> errorHandler) {
        this.stdErrorHandler = errorHandler;
    }

    /**
     * Waits for the server process to exit.
     *
     * @throws RuntimeException if the process is interrupted while waiting
     */
    public void awaitForExit() {
        try {
            this.process.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException("Process interrupted", e);
        }
    }

    /**
     * Starts the error processing thread that reads from the process's error stream.
     * Error messages are logged and emitted to the error sink.
     */
    private void startErrorProcessing() {
        this.errorScheduler.schedule(new Runnable() {
            @Override
            public void run() {
                BufferedReader processErrorReader = null;
                try {
                    processErrorReader = new BufferedReader(
                            new InputStreamReader(process.getErrorStream()));
                    String line;
                    while (!isClosing && (line = processErrorReader.readLine()) != null) {
                        final String errorLine = line;
                        try {
                            if (!errorSink.tryEmitNext(errorLine).isSuccess()) {
                                if (!isClosing) {
                                    logger.error("Failed to emit error message");
                                }
                                break;
                            }
                        } catch (Exception e) {
                            if (!isClosing) {
                                logger.error("Error processing error message", e);
                            }
                            break;
                        }
                    }
                } catch (IOException e) {
                    if (!isClosing) {
                        logger.error("Error reading from error stream", e);
                    }
                } finally {
                    isClosing = true;
                    errorSink.tryEmitComplete();
                    if (processErrorReader != null) {
                        try {
                            processErrorReader.close();
                        } catch (IOException e) {
                            logger.error("Error closing error stream reader", e);
                        }
                    }
                }
            }
        });
    }

    private void handleIncomingMessages(final Function<CompletableFuture<JSONRPCMessage>, CompletableFuture<JSONRPCMessage>> inboundMessageHandler) {
        this.inboundSink.asFlux()
                .subscribe(new Consumer<JSONRPCMessage>() {
                    @Override
                    public void accept(final JSONRPCMessage message) {
                        if (message != null && !isClosing) {
                            CompletableFuture<JSONRPCMessage> messageFuture = CompletableFuture.completedFuture(message);
                            try {
                                CompletableFuture<JSONRPCMessage> resultFuture = inboundMessageHandler.apply(messageFuture);
                                // We don't need to do anything with the result as this is just handling incoming messages
                            } catch (Exception e) {
                                if (!isClosing) {
                                    logger.error("Error processing inbound message", e);
                                }
                            }
                        }
                    }
                });
    }

    private void handleIncomingErrors() {
        this.errorSink.asFlux().subscribe(new Consumer<String>() {
            @Override
            public void accept(String e) {
                stdErrorHandler.accept(e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> sendMessage(JSONRPCMessage message) {
        if (this.outboundSink.tryEmitNext(message).isSuccess()) {
            // TODO: essentially we could reschedule ourselves in some time and make
            // another attempt with the already read data but pause reading until
            // success
            // In this approach we delegate the retry and the backpressure onto the
            // caller. This might be enough for most cases.
            return CompletableFuture.completedFuture(null);
        } else {
            CompletableFuture<Void> future = new CompletableFuture<Void>();
            future.completeExceptionally(new RuntimeException("Failed to enqueue message"));
            return future;
        }
    }

    /**
     * Starts the inbound processing thread that reads JSON-RPC messages from the
     * process's input stream. Messages are deserialized and emitted to the inbound sink.
     */
    private void startInboundProcessing() {
        this.inboundScheduler.schedule(new Runnable() {
            @Override
            public void run() {
                BufferedReader processReader = null;
                try {
                    processReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line;
                    while (!isClosing && (line = processReader.readLine()) != null) {
                        final String currentLine = line;
                        try {
                            JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(objectMapper, currentLine);
                            if (!inboundSink.tryEmitNext(message).isSuccess()) {
                                if (!isClosing) {
                                    logger.error("Failed to enqueue inbound message: {}", message);
                                }
                                break;
                            }
                        } catch (Exception e) {
                            if (!isClosing) {
                                logger.error("Error processing inbound message for line: " + currentLine, e);
                            }
                            break;
                        }
                    }
                } catch (IOException e) {
                    if (!isClosing) {
                        logger.error("Error reading from input stream", e);
                    }
                } finally {
                    isClosing = true;
                    inboundSink.tryEmitComplete();
                    if (processReader != null) {
                        try {
                            processReader.close();
                        } catch (IOException e) {
                            logger.error("Error closing input stream reader", e);
                        }
                    }
                }
            }
        });
    }

    /**
     * Starts the outbound processing thread that writes JSON-RPC messages to the
     * process's output stream. Messages are serialized to JSON and written with a newline
     * delimiter.
     */
    private void startOutboundProcessing() {
        this.handleOutbound(new Function<Flux<JSONRPCMessage>, Flux<JSONRPCMessage>>() {
            @Override
            public Flux<JSONRPCMessage> apply(Flux<JSONRPCMessage> messages) {
                return messages
                        // this bit is important since writes come from user threads and we
                        // want to ensure that the actual writing happens on a dedicated thread
                        .publishOn(outboundScheduler)
                        .handle(new BiConsumer<JSONRPCMessage, SynchronousSink<JSONRPCMessage>>() {
                            @Override
                            public void accept(JSONRPCMessage message, SynchronousSink<JSONRPCMessage> s) {
                                if (message != null && !isClosing) {
                                    try {
                                        String jsonMessage = objectMapper.writeValueAsString(message);
                                        // Escape any embedded newlines in the JSON message as per spec:
                                        // https://spec.modelcontextprotocol.io/specification/basic/transports/#stdio
                                        // - Messages are delimited by newlines, and MUST NOT contain
                                        // embedded newlines.
                                        jsonMessage = jsonMessage.replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n");

                                        final java.io.OutputStream os = process.getOutputStream();
                                        synchronized (os) {
                                            os.write(jsonMessage.getBytes(StandardCharsets.UTF_8));
                                            os.write("\n".getBytes(StandardCharsets.UTF_8));
                                            os.flush();
                                        }
                                        s.next(message);
                                    } catch (IOException e) {
                                        s.error(new RuntimeException(e));
                                    }
                                }
                            }
                        });
            }
        });
    }

    protected void handleOutbound(Function<Flux<JSONRPCMessage>, Flux<JSONRPCMessage>> outboundConsumer) {
        outboundConsumer.apply(outboundSink.asFlux())
                .doOnComplete(new Runnable() {
                    @Override
                    public void run() {
                        isClosing = true;
                        outboundSink.tryEmitComplete();
                    }
                })
                .doOnError(new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable e) {
                        if (!isClosing) {
                            logger.error("Error in outbound processing", e);
                            isClosing = true;
                            outboundSink.tryEmitComplete();
                        }
                    }
                })
                .subscribe();
    }

    /**
     * Gracefully closes the transport by destroying the process and disposing of the
     * schedulers. This method sends a TERM signal to the process and waits for it to exit
     * before cleaning up resources.
     *
     * @return A CompletableFuture that completes when the transport is closed
     */
    @Override
    public CompletableFuture<Void> closeGracefully() {
        final CompletableFuture<Void> future = new CompletableFuture<Void>();

        // Run the shutdown process in a separate thread
        CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                try {
                    isClosing = true;
                    logger.debug("Initiating graceful shutdown");

                    // First complete all sinks to stop accepting new messages
                    inboundSink.tryEmitComplete();
                    outboundSink.tryEmitComplete();
                    errorSink.tryEmitComplete();

                    // Give a short time for any pending messages to be processed
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    logger.debug("Sending TERM to process");
                    if (process != null) {
                        process.destroy();
                        // Wait for the process to exit
                        try {
                            process.waitFor();
                            if (process.exitValue() != 0) {
                                logger.warn("Process terminated with code " + process.exitValue());
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            logger.warn("Process wait interrupted", e);
                        }
                    } else {
                        logger.warn("Process not started");
                    }

                    // The Threads are blocked on readLine so disposeGracefully would not
                    // interrupt them, therefore we issue an async hard dispose.
                    inboundScheduler.dispose();
                    errorScheduler.dispose();
                    outboundScheduler.dispose();

                    logger.debug("Graceful shutdown completed");
                    future.complete(null);
                } catch (Exception e) {
                    logger.error("Error during graceful shutdown", e);
                    future.completeExceptionally(e);
                }
            }
        });

        return future;
    }

    public Sinks.Many<String> getErrorSink() {
        return this.errorSink;
    }

    @Override
    public <T> T unmarshalFrom(Object data, TypeReference<T> typeRef) {
        return this.objectMapper.convertValue(data, typeRef);
    }
    
    /**
     * Configures the ObjectMapper to handle non-standard getter methods.
     * 
     * @param objectMapper the ObjectMapper to configure
     * @return the configured ObjectMapper
     */
    private ObjectMapper configureObjectMapper(ObjectMapper objectMapper) {
        // Create a copy of the ObjectMapper to avoid modifying the original
        ObjectMapper configuredMapper = objectMapper.copy();
        
        // Configure visibility for methods without 'get' prefix
        configuredMapper.setVisibility(com.fasterxml.jackson.annotation.PropertyAccessor.GETTER, 
                                      com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY);
        configuredMapper.setVisibility(com.fasterxml.jackson.annotation.PropertyAccessor.FIELD, 
                                      com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY);
        
        return configuredMapper;
    }

}

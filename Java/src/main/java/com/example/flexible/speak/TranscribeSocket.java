/**
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.flexible.speak;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1beta1.RecognitionConfig;
import com.google.cloud.speech.v1beta1.RecognitionConfig.AudioEncoding;
import com.google.cloud.speech.v1beta1.SpeechGrpc;
import com.google.cloud.speech.v1beta1.StreamingRecognitionConfig;
import com.google.cloud.speech.v1beta1.StreamingRecognitionResult;
import com.google.cloud.speech.v1beta1.StreamingRecognizeRequest;
import com.google.cloud.speech.v1beta1.StreamingRecognizeResponse;
import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.auth.ClientAuthInterceptor;
import io.grpc.stub.StreamObserver;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TranscribeSocket extends WebSocketAdapter
    implements StreamObserver<StreamingRecognizeResponse> {

  private static final Logger logger = Logger.getLogger(TranscribeSocket.class.getName());
  StreamObserver<StreamingRecognizeRequest> requestObserver;
  private Gson gson;
  ManagedChannel channel;

  public TranscribeSocket() {
    gson = new Gson();
  }

  /**
   * Called when the client sends this server some raw bytes (ie audio data).
   */
  @Override
  public void onWebSocketBinary(byte[] payload, int offset, int len) {
    if (isConnected()) {
      StreamingRecognizeRequest request =
          StreamingRecognizeRequest.newBuilder()
              .setAudioContent(ByteString.copyFrom(payload, offset, len))
              .build();
      requestObserver.onNext(request);
    }
  }

  /**
   * Called when the client sends this server some text.
   */
  @Override
  public void onWebSocketText(String message) {
    if (isConnected()) {
      Constraints constraints = gson.fromJson(message, Constraints.class);
      logger.info(String.format("Got sampleRate: %s", constraints.sampleRate));

      try {
        channel = createChannel("speech.googleapis.com", 443);
        requestObserver = SpeechGrpc.newStub(channel).streamingRecognize(this);
        // Build and send a StreamingRecognizeRequest containing the parameters for
        // processing the audio.
        RecognitionConfig config =
            RecognitionConfig.newBuilder()
            .setEncoding(AudioEncoding.LINEAR16)
            .setSampleRate(constraints.sampleRate)
            .setLanguageCode("en-US")
            .build();
        StreamingRecognitionConfig streamingConfig =
            StreamingRecognitionConfig.newBuilder()
            .setConfig(config)
            .setInterimResults(true)
            .setSingleUtterance(false)
            .build();

        StreamingRecognizeRequest initial =
            StreamingRecognizeRequest.newBuilder().setStreamingConfig(streamingConfig).build();
        requestObserver.onNext(initial);

        getRemote().sendString(message);
      } catch (IOException e) {
        logger.log(Level.WARNING, "Error onWebSocketText", e);
      }
    }
  }

  public void closeApiChannel() {
    try {
      channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      logger.log(Level.WARNING, "Error closing api channel", e);
    }
  }

  /**
   * Called when the connection to the client is closed.
   */
  @Override
  public void onWebSocketClose(int statusCode, String reason) {
    logger.info("Websocket close.");
    requestObserver.onCompleted();
    closeApiChannel();
  }

  /**
   * Called if there's an error connecting with the client.
   */
  @Override
  public void onWebSocketError(Throwable cause) {
    logger.log(Level.WARNING, "Websocket error", cause);
    requestObserver.onError(cause);
    closeApiChannel();
  }


  /**
   * Called when the Speech API has a transcription result for us.
   */
  @Override
  public void onNext(StreamingRecognizeResponse response) {
    List<StreamingRecognitionResult> results = response.getResultsList();
    if (results.size() < 1) {
      return;
    }

    try {
      StreamingRecognitionResult result = results.get(0);
      logger.info("Got result " + result);
      //String transcript = result.getAlternatives(0).getTranscript();
      getRemote().sendString(gson.toJson(result));
    } catch (IOException e) {
      logger.log(Level.WARNING, "Error sending to websocket", e);
    }
  }

  /**
   * Called if the API call throws an error.
   */
  @Override
  public void onError(Throwable error) {
    logger.log(Level.WARNING, "recognize failed", error);
    // Close the websocket
    getSession().close(500, error.toString());
    closeApiChannel();
  }

  /**
   * Called when the API call is complete.
   */
  @Override
  public void onCompleted() {
    logger.info("recognize completed.");
    // Close the websocket
    getSession().close();
    closeApiChannel();
  }

  // Taken wholesale from StreamingRecognizeClient.java
  private static final List<String> OAUTH2_SCOPES =
      Arrays.asList("https://www.googleapis.com/auth/cloud-platform");

  static ManagedChannel createChannel(String host, int port) throws IOException {
    ClassLoader classLoader = new TranscribeSocket().getClass().getClassLoader();
    File file = new File(classLoader.getResource("TestProject-6255b5ddcad2.json").getFile());
    FileInputStream fis = new FileInputStream(file);
    System.out.println("Total file size to read (in bytes) : "+ fis.available());

    GoogleCredentials creds = GoogleCredentials.fromStream(fis);
    creds = creds.createScoped(OAUTH2_SCOPES);
    ManagedChannel channel =
        ManagedChannelBuilder.forAddress(host, port)
            .intercept(new ClientAuthInterceptor(creds, Executors.newSingleThreadExecutor()))
            .build();

    return channel;
  }
}

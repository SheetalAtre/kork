/*
 * Copyright 2023 OpsMx, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kork.retrofit.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class SpinnakerRetrofit2AsyncErrorHandlerTest {

  private static final MockWebServer mockWebServer = new MockWebServer();

  private static Retrofit asyncRetrofit2Service;

  @BeforeAll
  public static void setupOnce() {
    Executor executor = Executors.newFixedThreadPool(3);
    asyncRetrofit2Service =
        new Retrofit.Builder()
            .baseUrl(mockWebServer.url("/").toString())
            .client(
                new OkHttpClient.Builder()
                    .callTimeout(1, TimeUnit.SECONDS)
                    .connectTimeout(1, TimeUnit.SECONDS)
                    .build())
            .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance(executor))
            .addConverterFactory(JacksonConverterFactory.create())
            .build();
  }

  @AfterAll
  public static void shutdownOnce() throws Exception {
    mockWebServer.shutdown();
  }

  @Test
  public void testEnqueueAsyncSuccessResponse() throws InterruptedException, ExecutionException {
    final String validJsonResponseBodyString = "{\"name\":\"test\"}";
    AsyncRetrofit2Service testApi = asyncRetrofit2Service.create(AsyncRetrofit2Service.class);

    mockWebServer.enqueue(
        new MockResponse().setResponseCode(200).setBody(validJsonResponseBodyString));

    CompletableFuture<Response<Map<String, Object>>> futureResponse =
        enqueueAsync(
            testApi.testAsyncApiServiceForJsonResponse()); // async API call using CompletableFuture

    Response<Map<String, Object>> response = futureResponse.get();

    assertNotNull(response);
    assertTrue(response.isSuccessful());
    assertEquals(HttpStatus.OK.value(), response.code());
    assertNotNull(response.body());
    assertEquals("test", response.body().get("name"));
  }

  @Test
  public void testEnqueueAsyncErrorResponse() {
    final String validJsonResponseBodyString = "{\"name\":\"test\"}";

    AsyncRetrofit2Service testApi = asyncRetrofit2Service.create(AsyncRetrofit2Service.class);

    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(HttpStatus.NOT_FOUND.value())
            .setBody(validJsonResponseBodyString));

    CompletableFuture<Response<Map<String, Object>>> futureResponse =
        enqueueAsync(testApi.testAsyncApiServiceForJsonResponse());

    ExecutionException executionException =
        assertThrows(ExecutionException.class, () -> futureResponse.get());
    assertTrue(executionException.getCause() instanceof SpinnakerHttpException);

    SpinnakerHttpException exception = (SpinnakerHttpException) executionException.getCause();

    String expectedMessage =
        String.format(
            "Status: %s, URL: %s, Message: %s",
            HttpStatus.NOT_FOUND.value(),
            mockWebServer.url("/").toString() + "retrofit2/jsonresponse",
            HttpStatus.NOT_FOUND.value() + " " + "Client Error");
    assertEquals(expectedMessage, exception.getMessage());
  }

  private <T> CompletableFuture<Response<T>> enqueueAsync(Call<T> call) {
    CompletableFuture<Response<T>> futureResponse = new CompletableFuture<>();
    call.enqueue(
        new Callback<T>() {
          @Override
          public void onResponse(Call<T> call, Response<T> response) {
            futureResponse.complete(response);
          }

          @Override
          public void onFailure(Call<T> call, Throwable t) {
            futureResponse.completeExceptionally(t);
          }
        });
    return futureResponse;
  }

  interface AsyncRetrofit2Service {
    @retrofit2.http.GET("/retrofit2/jsonresponse")
    Call<Map<String, Object>> testAsyncApiServiceForJsonResponse();
  }
}

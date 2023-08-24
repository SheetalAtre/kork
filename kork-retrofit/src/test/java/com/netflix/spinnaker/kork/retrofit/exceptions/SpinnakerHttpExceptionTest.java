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
 *
 */

package com.netflix.spinnaker.kork.retrofit.exceptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import retrofit.RetrofitError;
import retrofit.client.Response;
import retrofit.converter.GsonConverter;
import retrofit.mime.TypedString;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class SpinnakerHttpExceptionTest {
  private static final String CUSTOM_MESSAGE = "custom message";

  public void testSpinnakerHttpExceptionFromRetrofitError() {
    String url = "http://localhost";
    String reason = "reason";
    int statusCode = 200;
    String message = "arbitrary message";
    Response response =
        new Response(
            url,
            statusCode,
            reason,
            List.of(),
            new TypedString("{ message: \"" + message + "\", name: \"test\" }"));
    RetrofitError retrofitError =
        RetrofitError.httpError(url, response, new GsonConverter(new Gson()), String.class);
    SpinnakerHttpException spinnakerHttpException = new SpinnakerHttpException(retrofitError);
    assertThat(spinnakerHttpException.getResponseBody()).isNotNull();
    Map<String, Object> errorResponseBody = spinnakerHttpException.getResponseBody();
    assertThat(errorResponseBody.get("name")).isEqualTo("test");
    assertThat(spinnakerHttpException.getResponseCode()).isEqualTo(statusCode);
    assertThat(spinnakerHttpException.getReason()).isEqualTo(reason);
    assertThat(spinnakerHttpException.getMessage())
        .isEqualTo("Status: " + statusCode + ", URL: " + url + ", Message: " + message);
  }

  @Test
  public void testSpinnakerHttpExceptionFromRetrofitException() {
    final String url = "http://localhost/";
    final String validJsonResponseBodyString = "{\"name\":\"test\"}";
    ResponseBody responseBody =
        ResponseBody.create(
            MediaType.parse("application/json" + "; charset=utf-8"), validJsonResponseBodyString);
    retrofit2.Response response =
        retrofit2.Response.error(HttpStatus.NOT_FOUND.value(), responseBody);

    Retrofit retrofit2Service =
        new Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(JacksonConverterFactory.create())
            .build();
    SpinnakerHttpException notFoundException =
        new SpinnakerHttpException(response, retrofit2Service);
    assertNotNull(notFoundException.getResponseBody());
    Map<String, Object> errorResponseBody = notFoundException.getResponseBody();
    assertEquals(errorResponseBody.get("name"), "test");
    assertEquals(HttpStatus.NOT_FOUND.value(), notFoundException.getResponseCode());
    assertEquals(url, retrofit2Service.baseUrl().toString());
    assertEquals("Response.error()", notFoundException.getReason()); // set by Response.error

    assertTrue(
        notFoundException.getMessage().contains(String.valueOf(HttpStatus.NOT_FOUND.value())));
  }

  @Test
  public void testSpinnakerHttpException_NewInstance() {
    final String url = "http://localhost";
    final String reason = "reason";
    Response response = new Response(url, 200, reason, List.of(), null);
    try {
      RetrofitError error = RetrofitError.httpError(url, response, null, null);
      throw new SpinnakerHttpException(error);
    } catch (SpinnakerException e) {
      SpinnakerException newException = e.newInstance(CUSTOM_MESSAGE);

      assertTrue(newException instanceof SpinnakerHttpException);
      SpinnakerHttpException spinnakerHttpException = ((SpinnakerHttpException) newException);

      assertEquals(spinnakerHttpException.getMessage(), CUSTOM_MESSAGE);

      assertEquals(e, newException.getCause());
      assertEquals(HttpStatus.OK.value(), spinnakerHttpException.getResponseCode());
      assertEquals(url, spinnakerHttpException.getUrl());
      assertNull(spinnakerHttpException.getReason());
    }
  }

  @Test
  public void testByteArrayBodyContentRetrofit2() throws Exception {

    // byte[] byteArray = {0x12, 0x34, 0x56, 0x78};
    String message = "arbitrary message";

    String msg = "{ \"message\": \"" + message + "\", \"name\": \"test\" }";
    byte[] byteArray = msg.getBytes();
    final String url = "http://localhost/";

    ResponseBody responseBody =
        ResponseBody.create(MediaType.parse("application/octet-stream"), byteArray);

    retrofit2.Response response =
        retrofit2.Response.error(HttpStatus.UNAUTHORIZED.value(), responseBody);

    Retrofit retrofit2Service =
        new Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(JacksonConverterFactory.create())
            .addConverterFactory(new SpinnakerHttpException.Retrofit2ByteArrayToHashMapConverter())
            .build();

    SpinnakerHttpException spinnakerHttpException =
        new SpinnakerHttpException(response, retrofit2Service);

    assertNotNull(spinnakerHttpException.getResponseBody());
    // hashmap for json body
    // Without the ByteArrayConverterFactoryTestUtil, and with only GsonConverterFactory,
    // the getBodyAs() will give a com.fasterxml.jackson.core.JsonParseException
    Map<String, Object> errorResponseBody = spinnakerHttpException.getResponseBody();

    assertNull(spinnakerHttpException.getCause());
    assertEquals("Response.error()", spinnakerHttpException.getReason()); // set by Response.error
    assertEquals(
        spinnakerHttpException.getMessage(),
        "Status: 401, URL: http://localhost/, Message: Response.error()");
    assertNotNull(errorResponseBody);

    assertEquals(errorResponseBody.get("name"), "test");
    assertEquals(HttpStatus.UNAUTHORIZED.value(), spinnakerHttpException.getResponseCode());
    assertEquals(url, retrofit2Service.baseUrl().toString());
    assertEquals("Response.error()", spinnakerHttpException.getReason()); // set by Response.error

    assertTrue(
        spinnakerHttpException
            .getMessage()
            .contains(String.valueOf(HttpStatus.UNAUTHORIZED.value())));
  }

  @Test
  public void testByteArrayBodyContentRetrofit() {
    String url = "http://localhost";
    String reason = "reason";
    int statusCode = 200;
    String message = "arbitrary message";
    // byte[] byteArray = {0x12, 0x34, 0x56, 0x78};
    String msg = "{ \"message\": \"" + message + "\", \"name\": \"test\" }";
    byte[] byteArray = msg.getBytes();

    retrofit.mime.TypedByteArray typedByteArray =
        new retrofit.mime.TypedByteArray("application/octet-stream", byteArray);

    Response response =
        new Response(
            url,
            statusCode,
            reason,
            List.of(new retrofit.client.Header("Content-Type", "application/octet-stream")),
            typedByteArray);
    retrofit.converter.Converter converter =
        new SpinnakerHttpException.RetrofitByteArrayToHashMapConverter();

    RetrofitError retrofitError =
        RetrofitError.httpError(url, response, converter, java.util.HashMap.class);
    assertNotNull(retrofitError);

    assertEquals(statusCode, retrofitError.getResponse().getStatus());

    HashMap<String, String> errorResponse =
        (HashMap<String, String>) retrofitError.getBodyAs(HashMap.class);
    assertNotNull(errorResponse);

    SpinnakerHttpException spinnakerHttpException = new SpinnakerHttpException(retrofitError);
    assertThat(spinnakerHttpException.getResponseBody()).isNotNull();
    Map<String, Object> errorResponseBody = spinnakerHttpException.getResponseBody();
    assertThat(errorResponseBody.get("name")).isEqualTo("test");
    assertThat(spinnakerHttpException.getResponseCode()).isEqualTo(statusCode);
    assertThat(spinnakerHttpException.getReason()).isEqualTo(reason);
    /*
    TODO :
    assertThat(spinnakerHttpException.getMessage())
      .isEqualTo("Status: " + statusCode + ", URL: " + url + ", Message: " + message);*/
    assertThat(spinnakerHttpException.getMessage())
        .isEqualTo("Status: " + statusCode + ", URL: " + url + ", Message: " + reason);
  }
}

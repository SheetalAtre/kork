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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class SpinnakerHttpExceptionTest {
  @Test
  public void testSpinnakerHttpExceptionFromRetrofitException() {
    final String validJsonResponseBodyString = "{\"name\":\"test\"}";
    ResponseBody responseBody =
        ResponseBody.create(
            MediaType.parse("application/json" + "; charset=utf-8"), validJsonResponseBodyString);
    retrofit2.Response response =
        retrofit2.Response.error(HttpStatus.NOT_FOUND.value(), responseBody);

    Retrofit retrofit2Service =
        new Retrofit.Builder()
            .baseUrl("http://localhost")
            .addConverterFactory(JacksonConverterFactory.create())
            .build();
    RetrofitException retrofitException = RetrofitException.httpError(response, retrofit2Service);
    SpinnakerHttpException notFoundException = new SpinnakerHttpException(retrofitException);
    assertNotNull(notFoundException.getResponseBody());
    Map<String, Object> errorResponseBody = notFoundException.getResponseBody();
    assertEquals(errorResponseBody.get("name"), "test");
    assertEquals(HttpStatus.NOT_FOUND.value(), notFoundException.getResponseCode());
    assertTrue(
        notFoundException.getMessage().contains(String.valueOf(HttpStatus.NOT_FOUND.value())));
  }
}

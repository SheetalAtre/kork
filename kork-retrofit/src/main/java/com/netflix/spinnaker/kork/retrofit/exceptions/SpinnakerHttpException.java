/*
 * Copyright 2020 Google, Inc.
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;
import okhttp3.ResponseBody;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import retrofit.RetrofitError;
import retrofit.client.Response;
import retrofit.converter.GsonConverter;
import retrofit2.Converter;

/**
 * An exception that exposes the {@link Response} of a given HTTP {@link RetrofitError} or {@link
 * okhttp3.Response} if retrofit 2.x used and a detail message that extracts useful information from
 * the {@link Response} or {@link okhttp3.Response}. Both {@link Response} and {@link
 * okhttp3.Response} can't be set together.
 */
@NonnullByDefault
public class SpinnakerHttpException extends SpinnakerServerException {
  private final Response response;
  private HttpHeaders headers;

  private final retrofit2.Response<?> retrofit2Response;

  private final Map<String, Object> responseBody;
  private final retrofit2.Retrofit retrofit;

  private final int responseCode;

  private final String url;

  private final String reason;

  private static final Map<String, Object> jsonErrorResponseBody =
      Map.of("message", "failed to parse response");

  public SpinnakerHttpException(RetrofitError e) {
    super(e);
    this.response = e.getResponse();
    this.retrofit2Response = null;
    this.retrofit = null;
    responseBody = (Map<String, Object>) e.getBodyAs(HashMap.class);
    this.responseCode = response.getStatus();
    this.reason = response.getReason() != null ? response.getReason() : e.getMessage();
    this.url = response.getUrl();
  }

  /**
   * The constructor handles the HTTP retrofit2 exception, similar to retrofit logic. It is used
   * with {@link com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory}.
   */
  public SpinnakerHttpException(
      retrofit2.Response<?> retrofit2Response, retrofit2.Retrofit retrofit) {
    this.response = null;
    this.retrofit2Response = retrofit2Response;
    this.retrofit = retrofit;
    if ((retrofit2Response.code() == HttpStatus.NOT_FOUND.value())
        || (retrofit2Response.code() == HttpStatus.BAD_REQUEST.value())) {
      setRetryable(false);
    }
    responseBody = this.getErrorBodyAs();
    this.responseCode = retrofit2Response.code();
    this.reason = retrofit2Response.message() != null ? retrofit2Response.message() : "";
    this.url = retrofit2Response.raw().request().url().toString();
  }

  /**
   * Construct a SpinnakerHttpException with a specified message. This allows code to catch a
   * SpinnakerHttpException and throw a new one with a custom message, while still allowing
   * SpinnakerRetrofitExceptionHandlers to handle the exception and respond with the appropriate
   * http status code.
   *
   * <p>Validating only one of {@link Response} or {@link okhttp3.Response} is set at a time using
   * {@link Preconditions}.checkState.
   *
   * @param message the message
   * @param cause the cause. Note that this is required (i.e. can't be null) since in the absence of
   *     a cause or a RetrofitError that provides the cause, SpinnakerHttpException is likely not
   *     the appropriate exception class to use.
   */
  public SpinnakerHttpException(String message, SpinnakerHttpException cause) {
    super(message, cause);

    Preconditions.checkState(
        !(cause.response != null && cause.retrofit2Response != null),
        "Can't set both response and retrofit2Response");

    this.response = cause.response;
    this.retrofit2Response = cause.retrofit2Response;
    this.retrofit = null;
    this.responseCode = cause.responseCode;
    this.reason = null;
    this.url = cause.url;
    this.responseBody = cause.responseBody;
  }

  public int getResponseCode() {
    return this.responseCode;
  }

  public String getUrl() {
    return url;
  }

  public String getReason() {
    return reason;
  }

  public HttpHeaders getHeaders() {
    if (headers == null) {
      headers = new HttpHeaders();
      if (response != null) {
        response.getHeaders().forEach(header -> headers.add(header.getName(), header.getValue()));
      } else {
        retrofit2Response
            .headers()
            .names()
            .forEach(
                key -> {
                  headers.addAll(key, retrofit2Response.headers().values(key));
                });
      }
    }
    return headers;
  }

  @Override
  public String getMessage() {
    if (reason != null && !reason.isEmpty()) {
      return String.format(
          "Status: %s, URL: %s, Message: %s", this.responseCode, this.url, this.reason);
    } else {
      return super.getMessage();
    }
  }

  @Override
  public SpinnakerHttpException newInstance(String message) {
    return new SpinnakerHttpException(message, this);
  }

  public Map<String, Object> getResponseBody() {
    return this.responseBody;
  }

  /**
   * HTTP response body converted to specified {@code type}. {@code null} if there is no response.
   *
   * @throws RuntimeException wrapping the underlying IOException if unable to convert the body to
   *     the specified {@code type}.
   */
  public Map<String, Object> getErrorBodyAs() {
    if (retrofit2Response == null) {
      return null;
    }

    Converter<ResponseBody, Map> converter =
        retrofit.responseBodyConverter(Map.class, new Annotation[0]);
    try {
      return converter.convert(retrofit2Response.errorBody());
    } catch (IOException e) {
      return jsonErrorResponseBody;
    }
  }

  static class Retrofit2ByteArrayToHashMapConverter extends Converter.Factory {
    @Override
    public Converter<ResponseBody, ?> responseBodyConverter(
        java.lang.reflect.Type type, Annotation[] annotations, retrofit2.Retrofit retrofit) {
      if (type == byte[].class) {
        return new Converter<okhttp3.ResponseBody, Map<String, Object>>() {
          @Override
          public Map<String, Object> convert(okhttp3.ResponseBody value) throws IOException {
            // return value.bytes();
            byte src[] = value.bytes();
            com.fasterxml.jackson.databind.ObjectMapper om =
                new com.fasterxml.jackson.databind.ObjectMapper();
            TypeReference<Map<String, Object>> tr = new TypeReference<Map<String, Object>>() {};
            Map<String, Object> val = om.readValue(src, tr);
            return val;
          }
        };
      }
      return null;
    }
  }

  static class RetrofitByteArrayToHashMapConverter implements retrofit.converter.Converter {
    private static final java.nio.charset.Charset UTF_8 = java.nio.charset.Charset.forName("UTF-8");
    private final retrofit.converter.Converter gsonConverter;

    public RetrofitByteArrayToHashMapConverter() {
      this.gsonConverter = new GsonConverter(new Gson());
    }

    @Override
    public Object fromBody(retrofit.mime.TypedInput body, java.lang.reflect.Type type)
        throws retrofit.converter.ConversionException {
      try {
        String contentType = body.mimeType();

        if (contentType != null) {
          if (contentType.contains("json")) {
            return gsonConverter.fromBody(body, type);
          } else if (contentType.contains("octet-stream")) {
            byte[] bytes = StreamUtils.readBytes(body.in());
            String json = new String(bytes, UTF_8);
            java.lang.reflect.Type mapType =
                new com.google.gson.reflect.TypeToken<HashMap<String, Object>>() {}.getType();
            return new com.google.gson.Gson().fromJson(json, mapType);
          }
        }
        throw new retrofit.converter.ConversionException(
            "Unsupported content type: " + contentType);

      } catch (IOException e) {
        throw new retrofit.converter.ConversionException(e);
      }
    }

    @Override
    public retrofit.mime.TypedOutput toBody(Object object) {
      /* TODO */
      return null;
    }

    private static class StreamUtils {
      public static byte[] readBytes(java.io.InputStream inputStream) throws IOException {
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];
        int bytesRead;
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        while ((bytesRead = inputStream.read(buffer)) != -1) {
          output.write(buffer, 0, bytesRead);
        }
        return output.toByteArray();
      }
    }
  }
}

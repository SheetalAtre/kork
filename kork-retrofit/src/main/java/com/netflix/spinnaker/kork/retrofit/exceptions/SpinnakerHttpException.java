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

import com.google.common.base.Preconditions;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import retrofit.RetrofitError;
import retrofit.client.Response;

/**
 * An exception that exposes the {@link Response} of a given HTTP {@link RetrofitError} or {@link
 * okhttp3.Response} of a {@link RetrofitException} if retrofit 2.x used and a detail message that
 * extracts useful information from the {@link Response} or {@link okhttp3.Response}. Both {@link
 * Response} and {@link okhttp3.Response} can't be set together..
 */
@NonnullByDefault
public class SpinnakerHttpException extends SpinnakerServerException {
  private final Response response;
  private HttpHeaders headers;

  private final retrofit2.Response retrofit2Response;

  private final Map<String, Object> responseBody;

  public SpinnakerHttpException(RetrofitError e) {
    super(e);
    this.response = e.getResponse();
    this.retrofit2Response = null;
    responseBody = (Map<String, Object>) e.getBodyAs(HashMap.class);

    if (responseBody != null) {
      responseBody.put("message", getErrorMessage());
    }
  }

  public SpinnakerHttpException(RetrofitException e) {
    super(e);
    this.response = null;
    this.retrofit2Response = e.getResponse();
    responseBody = (Map<String, Object>) e.getErrorBodyAs(HashMap.class);
    if (responseBody != null) {
      responseBody.put("message", getErrorMessage());
    }
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
    // Note that getRawMessage() is null in this case.

    Preconditions.checkState(
        !(cause.response != null && cause.retrofit2Response != null),
        "Can't set both response and retrofit2Response");

    this.response = cause.response;
    this.retrofit2Response = cause.retrofit2Response;
    if (cause.responseBody == null) {
      this.responseBody = new HashMap<>();
    } else {
      this.responseBody = cause.responseBody;
    }
    responseBody.put("message", message);
  }

  public int getResponseCode() {
    if (response != null) {
      return response.getStatus();
    } else {
      return retrofit2Response.code();
    }
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
    return (String) responseBody.get("message");
  }

  private String getErrorMessage() {
    String defaultMessage = getResponseCode() + " " + getReason();
    String rawMessage =
        responseBody != null
            ? (String) responseBody.getOrDefault("message", defaultMessage)
            : defaultMessage;
    return String.format(
        "Status: %s, URL: %s, Message: %s", getResponseCode(), getUrl(), rawMessage);
  }

  private String getUrl() {
    if (response != null) {
      return response.getUrl();
    } else {
      return retrofit2Response.raw().request().url().toString();
    }
  }

  private String getReason() {
    if (response != null) {
      return response.getReason();
    } else {
      return retrofit2Response.message();
    }
  }

  @Override
  public SpinnakerHttpException newInstance(String message) {
    return new SpinnakerHttpException(message, this);
  }

  public Map<String, Object> getResponseBody() {
    return this.responseBody;
  }
}

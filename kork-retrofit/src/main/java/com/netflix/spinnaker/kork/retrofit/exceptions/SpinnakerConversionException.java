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

import retrofit.RetrofitError;

/** Wraps an exception of kind {@link RetrofitError.Kind} CONVERSION. */
public class SpinnakerConversionException extends SpinnakerServerException {

  public SpinnakerConversionException(String message, Throwable cause) {
    super(message, cause);
  }

  @Override
  public SpinnakerConversionException newInstance(String message) {
    return new SpinnakerConversionException(message, this);
  }

  @Override
  public Boolean getRetryable() {
    return Boolean.FALSE;
  }
}

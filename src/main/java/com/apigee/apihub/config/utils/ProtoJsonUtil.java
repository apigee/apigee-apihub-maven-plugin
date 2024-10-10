/**
 * Copyright 2024 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.apigee.apihub.config.utils;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.protobuf.AbstractMessage.Builder;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;

/**
 * Generic ProtoJsonUtil to be used to serialize and deserialize Proto to json
 * Source: https://stackoverflow.com/questions/28545401/java-json-protobuf-back-conversion
 * @author Marcello.deeSales@gmail.com
 *
 */
public final class ProtoJsonUtil {

  /**
   * Makes a Json from a given message or builder
   * 
   * @param messageOrBuilder is the instance
   * @return The string representation
   * @throws IOException if any error occurs
   */
  public static String toJson(MessageOrBuilder messageOrBuilder) throws IOException {
    return JsonFormat.printer().print(messageOrBuilder);
  }
  
  /**
   * Makes a Json from a given message or builder
   * 
   * @param messageOrBuilder is the instance
   * @return The string representation
   * @throws IOException if any error occurs
   */
  public static String toPrettyPrintJson(MessageOrBuilder messageOrBuilder) throws IOException {
	  Gson gson = new GsonBuilder().setPrettyPrinting().create();
	  return gson.toJson(JsonParser.parseString(JsonFormat.printer().print(messageOrBuilder)));
  }

  /**
   * Makes a new instance of message based on the json and the class
   * @param <T> is the class type
   * @param json is the json instance
   * @param clazz is the class instance
   * @return An instance of T based on the json values
   * @throws IOException if any error occurs
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static <T extends Message> T fromJson(String json, Class<T> clazz) throws IOException {
    // https://stackoverflow.com/questions/27642021/calling-parsefrom-method-for-generic-protobuffer-class-in-java/33701202#33701202
    Builder builder = null;
    try {
      // Since we are dealing with a Message type, we can call newBuilder()
      builder = (Builder) clazz.getMethod("newBuilder").invoke(null);

    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
        | NoSuchMethodException | SecurityException e) {
      return null;
    }

    // The instance is placed into the builder values
    JsonFormat.parser().ignoringUnknownFields().merge(json, builder);

    // the instance will be from the build
    return (T) builder.build();
  }
}
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

import static java.lang.String.format;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;

public class FQDNHelper {
	
	static Logger logger = LogManager.getLogger(FQDNHelper.class);

	/**
	 * To update the FQDN
	 * @param profile
	 * @param resource
	 * @param resourceFQDN
	 * @param resourceStr
	 * @return
	 * @throws IOException
	 */
	public static String updateFQDNJsonKey (BuildProfile profile, String resource, String resourceFQDN, String resourceStr) throws IOException {
		try {
			JsonObject originalObject = new Gson().fromJson(resourceStr, JsonObject.class);
			if (originalObject.has(resource)) {
				logger.debug(format("%s exist, replacing with FQDN", resource));
		        JsonObject obj = originalObject.getAsJsonObject(resource);
				JsonObject newObj = new JsonObject();
				for (String key : obj.keySet()) {
		            newObj.add(format(resourceFQDN, profile.getProjectId(), profile.getLocation(), key), obj.get(key));
		        }
		        originalObject.remove(resource);
		        originalObject.add(resource, newObj);

				return originalObject.toString();
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("updateFQDNJsonKey failure for " + resource + " : " + e.getMessage());
		}
		return resourceStr;
	}
	
	
	/**
	 * To update the Json Value with FQDN
	 * @param jsonPath
	 * @param newVal
	 * @param resourceStr
	 * @return
	 * @throws IOException
	 */
	public static String updateFQDNJsonValue (String jsonPath, String newVal, String resourceStr) throws IOException {
		try {
				if(checkIfJsonElementExist(jsonPath, resourceStr)) {
					Configuration configuration = Configuration.builder().options(Option.DEFAULT_PATH_LEAF_TO_NULL).build();
					String attrVal = JsonPath.parse(resourceStr, configuration).read(jsonPath);
					if(attrVal!= null) {
						String newJson = JsonPath.parse(resourceStr).set(jsonPath, newVal+"/"+attrVal).jsonString();
						return newJson;
					}
				}
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("updateFQDNJsonValue failure: " + e.getMessage());
		}
		return resourceStr;
	}
	
	/**
	 * To replace the Json Value with new value
	 * @param jsonPath
	 * @param newVal
	 * @param resourceStr
	 * @return
	 * @throws IOException
	 */
	public static String replaceFQDNJsonValue (String jsonPath, String newVal, String resourceStr) throws IOException {
		try {
				if(checkIfJsonElementExist(jsonPath, resourceStr)) {
					Configuration configuration = Configuration.builder().options(Option.DEFAULT_PATH_LEAF_TO_NULL).build();
					String attrVal = JsonPath.parse(resourceStr, configuration).read(jsonPath);
					if(attrVal!= null) {
						String newJson = JsonPath.parse(resourceStr).set(jsonPath, newVal).jsonString();
						return newJson;
					}
				}
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("replaceFQDNJsonValue failure: " + e.getMessage());
		}
		return resourceStr;
	}
	
	/**
	 * To update the Json String Array with FQDN
	 * @param jsonPath
	 * @param newVal
	 * @param resourceStr
	 * @return
	 * @throws IOException
	 */
	public static String updateFQDNJsonStringArray (String jsonPath, String newVal, String resourceStr) throws IOException {
		try {
				if(checkIfJsonArrayEmpty(jsonPath, resourceStr)) {
					Configuration configuration = Configuration.builder().options(Option.DEFAULT_PATH_LEAF_TO_NULL).build();
					List<String> values = JsonPath.parse(resourceStr, configuration).read(jsonPath);
					List<String> updValues= new ArrayList<String>();
					if(values!= null && values.size()>0) {
						for (String value : values) {
							System.out.println("value: "+ value);
							updValues.add(format("%s/%s", newVal, value));
						}
						String newJson = JsonPath.parse(resourceStr).set(jsonPath, updValues).jsonString();
						return newJson;
					}
				}
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("updateFQDNJsonValue failure: " + e.getMessage());
		}
		return resourceStr;
	}
	
	/**
	 * Check if a json attribute exist using its jsonpath
	 * @param jsonPath
	 * @param str
	 * @return
	 * @throws Exception
	 */
	public static boolean checkIfJsonElementExist (String jsonPath, String str) throws Exception {
		try {
			Configuration configuration = Configuration.builder().options(Option.SUPPRESS_EXCEPTIONS).build();
			Object attrVal = JsonPath.parse(str, configuration).read(jsonPath);
			if(attrVal!= null) {
				return true;
			}
		}
		catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("checkIfJsonElementExist failure: " + e.getMessage());
		}
		return false; 
	}
	
	/**
	 * Check if a json array is empty using its jsonpath
	 * @param jsonPath
	 * @param str
	 * @return
	 * @throws Exception
	 */
	public static boolean checkIfJsonArrayEmpty (String jsonPath, String str) throws Exception {
		try {
			Configuration configuration = Configuration.builder().options(Option.SUPPRESS_EXCEPTIONS).build();
			List<Object> attrVal = JsonPath.parse(str, configuration).read(jsonPath);
			if(attrVal!= null && attrVal.size()>0) {
				return true;
			}
		}
		catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("checkIfJsonArrayEmpty failure: " + e.getMessage());
		}
		return false; 
	}
	
}

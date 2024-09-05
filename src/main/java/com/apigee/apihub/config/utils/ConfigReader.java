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

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class ConfigReader {

	public static List<String> parseConfig(String configFile) throws ParseException, IOException {

		Logger logger = LogManager.getLogger(ConfigReader.class);

		JSONParser parser = new JSONParser();
		ArrayList<String> out = null;
		try {
			BufferedReader bufferedReader = new BufferedReader(new java.io.FileReader(configFile));

			JSONArray configs = (JSONArray) parser.parse(bufferedReader);

			if (configs == null)
				return null;

			out = new ArrayList<String>();
			for (Object config : configs) {
				out.add(((JSONObject) config).toJSONString());
			}
		} catch (IOException ie) {
			logger.info(ie.getMessage());
			throw ie;
		} catch (ParseException pe) {
			logger.info(pe.getMessage());
			throw pe;
		}
		return out;
	}

}

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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.plugin.MojoFailureException;

import com.apigee.apihub.config.mavenplugin.ApisMojo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

public class PluginUtils {
	static Logger logger = LogManager.getLogger(ApisMojo.class);

	/**
	 * 
	 * @param aStr
	 * @param projectId
	 * @param location
	 * @return
	 */
	public static String replacer (String aStr, String from, String to) {
		aStr = aStr.replaceAll(from, to);
		return aStr;
	}
	
	/**
	 * 
	 * @param objList
	 * @param exportFilePath
	 * @param entity
	 * @throws IOException
	 * @throws MojoFailureException
	 */
	public static void exportToFile(List<String> objList, String exportFilePath, String entity)
            throws IOException, MojoFailureException {
		BufferedWriter bw = null;
		FileWriter fw = null;
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try {
			// Convert the ArrayList to a JSON array
	        JsonArray jsonArray = new JsonArray();
	        for (String jsonString : objList) {
	            jsonArray.add(JsonParser.parseString(jsonString));
	        }
	        String prettyJson = gson.toJson(jsonArray);
			String fileName = exportFilePath+ File.separator+entity+".json";
			fw = new FileWriter(fileName);
			bw = new BufferedWriter(fw);
			bw.write(prettyJson);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (bw != null)
					bw.close();
				if (fw != null)
					fw.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	}

}

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

public final class PluginConstants {
	
	public static final int PAGE_SIZE = 1000;
	public static final String PROJECT_ID = "PROJECT_ID";
	public static final String LOCATION = "LOCATION";
	public static final String PATTERN = "projects\\\\/[^\\\\/]+\\\\/locations\\\\/[^\\\\/]+";
	public static final String PATTERN1 = "projects\\/[^\\/]+\\/locations\\/[^\\/]+";
	public static final String[] REMOVE_FIELDS = {"createTime", "updateTime", "details", 
													"lintResponse", "versions", "apiVersions", 
													"state", "discoveryMode", "errorDetail",
													"definitionType", "mandatory", "specs",
													"apiOperations", "definitions"
												};
}

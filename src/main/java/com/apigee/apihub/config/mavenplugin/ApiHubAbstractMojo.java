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

package com.apigee.apihub.config.mavenplugin;

import org.apache.maven.plugin.AbstractMojo;

import com.apigee.apihub.config.utils.BuildProfile;

public abstract class ApiHubAbstractMojo extends AbstractMojo {

	/**
	 * Gateway options
	 *
	 * @parameter property="apigee.apihub.config.options"
	 */
	private String options;

	/**
	 * Config Dir
	 *
	 * @parameter property="apigee.apihub.config.dir"
	 */
	private String configDir;
	
	/**
	 * Config File
	 *
	 * @parameter property="apigee.apihub.config.file"
	 */
	private String configFile;
	
	/**
	 * Apigee API Hub Project ID
	 *
	 * @parameter property="apigee.apihub.projectId"
	 */
	private String projectId;
	
	/**
	 * Apigee APIHub Location
	 * @parameter property="apigee.apihub.location"
	 */
	private String location;

	/**
	 * Spec Directory
	 *
	 * @parameter property="apigee.apihub.spec.dir"
	 */
	private String specDirectory;
	
	/**
	 * service account file
	 * @parameter property="apigee.apihub.serviceaccount.file"
 	 */
	private String serviceAccountFilePath;
	
	/**
	 * Gateway bearer token
	 *
	 * @parameter property="apigee.apihub.bearer"
	 */
	private String bearer;

	/**
	 * Skip running this plugin. Default is false.
	 *
	 * @parameter default-value="false"
	 */
	private boolean skip = false;

	public BuildProfile buildProfile;

	public ApiHubAbstractMojo() {
		super();
	}

	public BuildProfile getProfile() {
		this.buildProfile = new BuildProfile();
		this.buildProfile.setLocation(this.location);
		this.buildProfile.setProjectId(this.projectId);
		this.buildProfile.setOptions(this.options);
		this.buildProfile.setConfigFile(this.configFile);
		this.buildProfile.setConfigDir(this.configDir);
		this.buildProfile.setSpecDirectory(this.specDirectory);
		this.buildProfile.setServiceAccountFilePath(this.serviceAccountFilePath);
		this.buildProfile.setBearer(this.bearer);
		return buildProfile;
	}
	
	public boolean isSkip() {
		return skip;
	}

	public void setSkip(boolean skip) {
		this.skip = skip;
	}
	
	public String getOptions() {
		return options;
	}

	public void setOptions(String options) {
		this.options = options;
	}

}

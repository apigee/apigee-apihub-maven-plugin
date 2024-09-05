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

import java.io.FileInputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.apihub.v1.ApiHubClient;
import com.google.cloud.apihub.v1.ApiHubSettings;

public class ApiHubClientSingleton {

	static Logger logger = LogManager.getLogger(ApiHubClientSingleton.class);
	
	// Static variable reference of apiHubClient of type ApiHubClientSingleton
	private static ApiHubClientSingleton apiHubClientObj = null;
	
	private ApiHubClient apiHubClient;
	
	public void setApiHubClient(ApiHubClient apiHubClient) {
		this.apiHubClient = apiHubClient;
	}
	
	public ApiHubClient getApiHubClient() {
		return apiHubClient;
	}

	private ApiHubClientSingleton(BuildProfile profile) throws Exception {
		GoogleCredentials credentials = null;
		try {
			if(profile.getServiceAccountFilePath() == null && profile.getBearer() == null) {
				throw new Exception("Service Account or Bearer Token is missing");
			}
			else if(profile.getServiceAccountFilePath()!=null) {
				logger.info("Using the service account file to authenticate");
				credentials = GoogleCredentials
						.fromStream(new FileInputStream(profile.getServiceAccountFilePath()))
						.createScoped("https://www.googleapis.com/auth/cloud-platform");
			}else{
				logger.info("Using the bearer token");
				credentials = GoogleCredentials.newBuilder().setAccessToken(new AccessToken(profile.getBearer(), null)).build();
			}
			ApiHubSettings hubSettings = ApiHubSettings.newHttpJsonBuilder()
					.setCredentialsProvider(FixedCredentialsProvider.create(credentials)).build();
			setApiHubClient(ApiHubClient.create(hubSettings));
		} catch (Exception e) {
			throw e;
		}
	}
	
    // Static method to create instance of ApiHubClientSingleton class
    public static ApiHubClientSingleton getInstance(BuildProfile profile) throws Exception
    {
        if (apiHubClientObj == null)
        	apiHubClientObj = new ApiHubClientSingleton(profile);
 
        return apiHubClientObj;
    }
}

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

import static java.lang.String.format;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.json.simple.parser.ParseException;

import com.apigee.apihub.config.utils.ApiHubClientSingleton;
import com.apigee.apihub.config.utils.BuildProfile;
import com.apigee.apihub.config.utils.ConfigReader;
import com.apigee.apihub.config.utils.ProtoJsonUtil;
import com.google.api.client.util.Key;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode.Code;
import com.google.cloud.apihub.v1.ApiHubClient;
import com.google.cloud.apihub.v1.ExternalApiName;
import com.google.cloud.apihub.v1.LocationName;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.protobuf.FieldMask;

/**
 * Goal to configure External APIs in Apigee API Hub
 *
 * @author ssvaidyanathan
 * @goal externalapis
 * @phase install
 */
public class ExternalApisMojo extends ApiHubAbstractMojo {
	static Logger logger = LogManager.getLogger(ExternalApisMojo.class);

	public static final String ____ATTENTION_MARKER____ = "************************************************************************";

	enum OPTIONS {
		none, create, update, delete, sync
	}

	OPTIONS buildOption = OPTIONS.none;

	private BuildProfile buildProfile;

	/**
	 * Constructor.
	 */
	public ExternalApisMojo() {
		super();
	}
	
	
	public static class ExternalApi {
        @Key
        public String name;
    }
	
	protected String getExternalApiName(String payload) 
            throws MojoFailureException {
		Gson gson = new Gson();
		try {
			ExternalApi externalApi = gson.fromJson(payload, ExternalApi.class);
			return externalApi.name;
		} catch (JsonParseException e) {
		  throw new MojoFailureException(e.getMessage());
		}
	}

	/**
	 * Initilization
	 * @throws MojoExecutionException
	 * @throws MojoFailureException
	 */
	public void init() throws MojoExecutionException, MojoFailureException {
		try {
			logger.info(____ATTENTION_MARKER____);
			logger.info("API Hub External API");
			logger.info(____ATTENTION_MARKER____);

			String options = "";
			buildProfile = super.getProfile();

			options = super.getOptions();
			if (options != null) {
				buildOption = OPTIONS.valueOf(options);
			}
			if (buildOption == OPTIONS.none) {
				logger.info("Skipping Artifact (default action)");
				return;
			}

			logger.debug("Build option " + buildOption.name());

			
			if (buildProfile.getProjectId() == null) {
				throw new MojoExecutionException("Apigee API hub Project ID is missing");
			}
			if (buildProfile.getLocation() == null) {
				throw new MojoExecutionException("Apigee API hub Location is missing");
			}
			if (buildProfile.getServiceAccountFilePath() == null && buildProfile.getBearer() == null) {
				throw new MojoExecutionException("Service Account file path or Bearer token is missing");
			}
			if (buildProfile.getConfigDir() == null) {
				throw new MojoExecutionException("API Confile Dir is missing");
			}
			 

		} catch (IllegalArgumentException e) {
			throw new RuntimeException("Invalid apigee.option provided");
		} catch (RuntimeException e) {
			throw e;
		} 

	}

	/**
	 * Entry point for the mojo.
	 */
	public void execute() throws MojoExecutionException, MojoFailureException {
		if (super.isSkip()) {
			getLog().info("Skipping");
			return;
		}

		try {
			init();
			logger.info(format("Fetching externalApis.json file from %s directory", buildProfile.getConfigDir()));
			List<String> externalApis = ConfigReader.parseConfig(buildProfile.getConfigDir()+"/externalApis.json");
			processExternalApis(externalApis);

		} catch (MojoFailureException e) {
			throw e;
		} catch (RuntimeException e) {
			throw e;
		} catch (ParseException e) {
			throw new MojoFailureException(e.getMessage());
		} catch (IOException e) {
			throw new MojoFailureException(e.getMessage());
		}
	}

	/**
	 * 
	 * @param externalApis
	 * @throws MojoExecutionException
	 */
	public void processExternalApis(List<String> externalApis) throws MojoExecutionException {
		try {
			if (buildOption != OPTIONS.update && 
					buildOption != OPTIONS.create &&
	                buildOption != OPTIONS.delete &&
	                buildOption != OPTIONS.sync) {
					return;
			}
			for (String externalApi : externalApis) {
				String externalApiName = getExternalApiName(externalApi);
				if (externalApiName == null) {
	        		throw new IllegalArgumentException("External API does not have a name");
	        	}
				if (doesExternalApiExist(buildProfile, externalApiName)) {
					switch (buildOption) {
						case create:
							logger.info(format("External API \"%s\" already exists. Skipping.", externalApiName));
							break;
						case update:
							logger.info(format("External API \"%s\" already exists. Updating.", externalApiName));
							//update
							doUpdate(buildProfile, externalApiName, externalApi);
							break;
						case delete:
							logger.info(format("External API \"%s\" already exists. Deleting.", externalApiName));
							//delete
							doDelete(buildProfile, externalApiName);
							break;
						case sync:
							logger.info(format("External API \"%s\" already exists. Deleting and recreating.", externalApiName));
							//delete
							doDelete(buildProfile, externalApiName);
							logger.info(format("Creating External API - %s", externalApiName));
							//create
							doCreate(buildProfile, externalApiName, externalApi);
							break;
					}
				} else {
					switch (buildOption) {
						case create:
	                    case sync:
	                    case update:
	                    	logger.info(format("Creating External API - %s", externalApiName));
	                    	//create
	                    	doCreate(buildProfile, externalApiName, externalApi);
							break;
	                    case delete:
                            logger.info(format("External API \"%s\" does not exist. Skipping.", externalApiName));
                            break;
					}
				}
			}
		}catch (Exception e) {
			throw new RuntimeException(e.getMessage());
		}
	}
	
	/**
	 * Create External API
	 * @param externalApiName
	 * @param externalApiStr
	 * @throws MojoExecutionException
	 */
	public void doCreate(BuildProfile profile, String externalApiName, String externalApiStr) throws MojoExecutionException {
		ApiHubClient apiHubClient = null;
		try {
			apiHubClient = ApiHubClientSingleton.getInstance(profile).getApiHubClient();
			LocationName parent = LocationName.of(profile.getProjectId(), profile.getLocation());
			com.google.cloud.apihub.v1.ExternalApi externalApipObj = ProtoJsonUtil.fromJson(updateAttributeFQDN(profile, externalApiName, externalApiStr), com.google.cloud.apihub.v1.ExternalApi.class);
		    apiHubClient.createExternalApi(parent, externalApipObj, externalApiName);
		    logger.info("Create success");
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Create failure: " + e.getMessage());
		}
	}

	/**
	 * Delete External API
	 * @param profile
	 * @param externalApiName
	 * @throws MojoExecutionException
	 */
	public void doDelete(BuildProfile profile, String externalApiName) throws MojoExecutionException {
		ApiHubClient apiHubClient = null;
		try {
			apiHubClient = ApiHubClientSingleton.getInstance(profile).getApiHubClient();
			ExternalApiName name = ExternalApiName.of(profile.getProjectId(), profile.getLocation(), externalApiName);
		    apiHubClient.deleteExternalApi(name);
		    logger.info("Delete success");
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Delete failure: " + e.getMessage());
		}
	}
	
	/**
	 * Update External API
	 * @param profile
	 * @param externalApiName
	 * @param externalApiStr
	 * @throws MojoExecutionException
	 */
	public void doUpdate(BuildProfile profile, String externalApiName, String externalApiStr) throws MojoExecutionException {
		ApiHubClient apiHubClient = null;
		try {
			apiHubClient = ApiHubClientSingleton.getInstance(profile).getApiHubClient();
			com.google.cloud.apihub.v1.ExternalApi externalApiObj = ProtoJsonUtil.fromJson(updateAttributeFQDN(profile, externalApiName, externalApiStr), com.google.cloud.apihub.v1.ExternalApi.class);
			//updating the name field in the externalApi object to projects/{project}/locations/{location}/externalApis/{externalApi} format as its required by the updateExternalApi method
			externalApiObj = externalApiObj.toBuilder().setName(format("projects/%s/locations/%s/externalApis/%s", profile.getProjectId(), profile.getLocation(), externalApiName)).build();
			List<String> fieldMaskValues = new ArrayList<>();
			fieldMaskValues.add("display_name");
			fieldMaskValues.add("description");
			fieldMaskValues.add("documentation");
	        fieldMaskValues.add("endpoints");
	        fieldMaskValues.add("paths");
			FieldMask updateMask = FieldMask.newBuilder().addAllPaths(fieldMaskValues).build();
		    apiHubClient.updateExternalApi(externalApiObj, updateMask);
		    logger.info("Update success");
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Update failure: " + e.getMessage());
		}
	}
	
	/**
	 * Check if an external API exist
	 *  
	 * @param profile
	 * @param externalApiName
	 * @return
	 * @throws IOException
	 */
	public static boolean doesExternalApiExist(BuildProfile profile, String externalApiName)
	            throws IOException {
		try {
        	logger.info("Checking if External API - " +externalApiName + " exist");
        	ApiHubClient apiHubClient = ApiHubClientSingleton.getInstance(profile).getApiHubClient();
        	ExternalApiName name = ExternalApiName.of(profile.getProjectId(), profile.getLocation(), externalApiName);
        	com.google.cloud.apihub.v1.ExternalApi externalApiResponse = apiHubClient.getExternalApi(name);
        	if(externalApiResponse == null) 
            	return false;
        }
        catch (ApiException e) {
			if(e.getStatusCode().getCode().equals(Code.NOT_FOUND)) {
				return false;
			}
        }
        catch (Exception e) {
            throw new IOException(e.getMessage());
        }
        return true;
    }
	
	/**
	 * TO update the attributes with FQDN
	 * @param profile
	 * @param externalApiName
	 * @param externalApiStr
	 * @return
	 * @throws IOException
	 */
	public static String updateAttributeFQDN (BuildProfile profile, String externalApiName, String externalApiStr) throws IOException {
		try {
			JsonElement je = new Gson().fromJson(externalApiStr, JsonElement.class);
			JsonObject jo = je.getAsJsonObject();
			JsonElement attributes = jo.get("attributes");
			if(attributes!=null) {
				logger.debug("attributes exist, replacing with FQDN");
				JsonObject attributesObj = attributes.getAsJsonObject();
				Set<Map.Entry<String, JsonElement>> entries = attributesObj.entrySet();
				for(Map.Entry<String, JsonElement> entry: entries) {
			         ((JsonObject) attributes).add(format("projects/%s/locations/%s/attributes/%s", profile.getProjectId(), profile.getLocation(), entry.getKey()), entry.getValue());
			         attributesObj.remove(entry.getKey());
				}
				logger.debug("updated json:"+je);
				return je.toString();
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("updateAttributeFQDN failure: " + e.getMessage());
		}
		return externalApiStr;
	}
	
}

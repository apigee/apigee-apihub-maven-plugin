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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.json.simple.parser.ParseException;

import com.apigee.apihub.config.utils.ApiHubClientSingleton;
import com.apigee.apihub.config.utils.BuildProfile;
import com.apigee.apihub.config.utils.ConfigReader;
import com.apigee.apihub.config.utils.PluginConstants;
import com.apigee.apihub.config.utils.PluginUtils;
import com.apigee.apihub.config.utils.ProtoJsonUtil;
import com.google.api.client.util.Key;
import com.google.api.client.util.Strings;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode.Code;
import com.google.cloud.apihub.v1.ApiHubClient;
import com.google.cloud.apihub.v1.ApiName;
import com.google.cloud.apihub.v1.DeleteApiRequest;
import com.google.cloud.apihub.v1.ListApisRequest;
import com.google.cloud.apihub.v1.ListApisResponse;
import com.google.cloud.apihub.v1.LocationName;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.protobuf.FieldMask;

/**
 * Goal to configure APIs in Apigee API Hub
 *
 * @author ssvaidyanathan
 * @goal apis
 * @phase install
 */
public class ApisMojo extends ApiHubAbstractMojo {
	static Logger logger = LogManager.getLogger(ApisMojo.class);

	public static final String ____ATTENTION_MARKER____ = "************************************************************************";
			
	enum OPTIONS {
		none, create, update, delete, sync, export
	}

	OPTIONS buildOption = OPTIONS.none;

	private BuildProfile buildProfile;

	/**
	 * Constructor.
	 */
	public ApisMojo() {
		super();
	}
	
	
	public static class Api {
        @Key
        public String name;
    }
	
	protected String getApiName(String payload) 
            throws MojoFailureException {
		Gson gson = new Gson();
		try {
			Api api = gson.fromJson(payload, Api.class);
			String[] parts = api.name.split("/");
			String apiName = parts[parts.length - 1];
			return apiName;
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
			logger.info("API Hub APIs");
			logger.info(____ATTENTION_MARKER____);

			String options = "";
			buildProfile = super.getProfile();

			options = super.getOptions();
			if (options != null) {
				buildOption = OPTIONS.valueOf(options);
			}
			if (buildOption == OPTIONS.none) {
				logger.info("Skipping APIs (default action)");
				return;
			}

			logger.debug("Build option " + buildOption.name());

			if (Strings.isNullOrEmpty(buildProfile.getProjectId())) {
				throw new MojoExecutionException("Apigee API hub Project ID is missing or empty");
			}
			if (Strings.isNullOrEmpty(buildProfile.getLocation())) {
				throw new MojoExecutionException("Apigee API hub Location is missing or empty");
			}
			if (Strings.isNullOrEmpty(buildProfile.getServiceAccountFilePath()) && Strings.isNullOrEmpty(buildProfile.getBearer())) {
				throw new MojoExecutionException("Service Account file path or Bearer token is missing or empty");
			}
			if (!buildOption.equals(OPTIONS.export) && Strings.isNullOrEmpty(buildProfile.getConfigDir())) {
				throw new MojoExecutionException("API Config Directory is missing");
			}
			if (buildOption.equals(OPTIONS.export) && Strings.isNullOrEmpty(buildProfile.getConfigExportDir())) {
				throw new MojoExecutionException("Config Export Directory is missing");
			}
			if (buildOption.equals(OPTIONS.export) && !Strings.isNullOrEmpty(buildProfile.getConfigExportDir())) {
				File f = new File(buildProfile.getConfigExportDir());
				if (!f.exists() || !f.isDirectory()) {
					throw new MojoExecutionException("Config Export Directory is not created or is incorrect");
				}
			}
			 

		} catch (IllegalArgumentException e) {
			throw new RuntimeException("Invalid apigee.apihub.config.options provided");
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
			if(buildOption == OPTIONS.export) {
				logger.info(format("Exporting apis.json file to %s directory", buildProfile.getConfigExportDir()));
				exportApis(buildProfile);
			} else {
				logger.info(format("Fetching apis.json file from %s directory", buildProfile.getConfigDir()));
				List<String> apis = ConfigReader.parseConfig(buildProfile.getConfigDir()+"/apis.json");
				processApis(apis);
			}

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
	 * @param apis
	 * @throws MojoExecutionException
	 */
	public void processApis(List<String> apis) throws MojoExecutionException {
		try {
			if (buildOption != OPTIONS.update && 
					buildOption != OPTIONS.create &&
	                buildOption != OPTIONS.delete &&
	                buildOption != OPTIONS.sync) {
					return;
			}
			for (String api : apis) {
				api = PluginUtils.replacer(api, PluginConstants.PATTERN, format("projects/%s/locations/%s", buildProfile.getProjectId(), buildProfile.getLocation()));
				String apiName = getApiName(api);
				if (apiName == null) {
	        		throw new IllegalArgumentException("Api does not have a name");
	        	}
				if (doesApiExist(buildProfile, apiName)) {
					switch (buildOption) {
						case create:
							logger.info(format("Api \"%s\" already exists. Skipping.", apiName));
							break;
						case update:
							logger.info(format("Api \"%s\" already exists. Updating.", apiName));
							//update
							doUpdate(buildProfile, api);
							break;
						case delete:
							logger.info(format("Api \"%s\" already exists. Deleting.", apiName));
							//delete
							doDelete(buildProfile, apiName);
							break;
						case sync:
							logger.info(format("Api \"%s\" already exists. Deleting and recreating.", apiName));
							//delete
							doDelete(buildProfile, apiName);
							logger.info(format("Creating Api - %s", apiName));
							//create
							doCreate(buildProfile, apiName, api);
							break;
					}
				} else {
					switch (buildOption) {
						case create:
	                    case sync:
	                    case update:
	                    	logger.info(format("Creating Api - %s", apiName));
	                    	//create
	                    	doCreate(buildProfile, apiName, api);
							break;
	                    case delete:
                            logger.info(format("Api \"%s\" does not exist. Skipping.", apiName));
                            break;
					}
				}
			}
		}catch (Exception e) {
			throw new RuntimeException(e.getMessage());
		}
	}
	
	/**
	 * 
	 * @param profile
	 * @throws MojoExecutionException
	 */
	public void exportApis(BuildProfile profile) throws MojoExecutionException {
		ApiHubClient apiHubClient = null;
		List<String> apiList = new ArrayList<String>();
		try {
			apiHubClient = ApiHubClientSingleton.getInstance(profile).getApiHubClient();
			ListApisRequest request =
				ListApisRequest.newBuilder()
					.setParent(LocationName.of(profile.getProjectId(), profile.getLocation()).toString())
					//.setFilter("display_name=\"API 1\"")
					.setPageSize(PluginConstants.PAGE_SIZE).build();
			while (true) {
		     ListApisResponse response = apiHubClient.listApisCallable().call(request);
		     for (com.google.cloud.apihub.v1.Api api : response.getApisList()) {
		    	 String apiStr = ProtoJsonUtil.toJson(api);
		    	 apiStr = PluginUtils.replacer(apiStr, PluginConstants.PATTERN1, format("projects/%s/locations/%s", PluginConstants.PROJECT_ID, PluginConstants.LOCATION));
		    	 apiList.add(PluginUtils.cleanseResponse(apiStr));
		     }
		     String nextPageToken = response.getNextPageToken();
		     logger.debug("nextPageToken: "+nextPageToken);
		     if (!Strings.isNullOrEmpty(nextPageToken)) {
		       request = request.toBuilder().setPageToken(nextPageToken).build();
		     } else {
		       break;
		     }
		   }
			PluginUtils.exportToFile(apiList, profile.getConfigExportDir(), "apis");
		}catch (Exception e) {
			throw new RuntimeException(e.getMessage());
		}
		finally {
			apiHubClient.close();
		}
	}
	
	/**
	 * Create Api
	 * @param profile
	 * @param apiName
	 * @param apiStr
	 * @throws MojoExecutionException
	 */
	public void doCreate(BuildProfile profile, String apiName, String apiStr) throws MojoExecutionException {
		ApiHubClient apiHubClient = null;
		try {
			apiHubClient = ApiHubClientSingleton.getInstance(profile).getApiHubClient();
			LocationName parent = LocationName.of(profile.getProjectId(), profile.getLocation());
			com.google.cloud.apihub.v1.Api apiObj = ProtoJsonUtil.fromJson(apiStr, com.google.cloud.apihub.v1.Api.class);
			apiHubClient.createApi(parent, apiObj, apiName);
		    logger.info("Create success");
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Create failure: " + e.getMessage());
		}
	}


	/**
	 * Delete Api
	 * @param profile
	 * @param apiName
	 * @throws MojoExecutionException
	 */
	public void doDelete(BuildProfile profile, String apiName) throws MojoExecutionException {
		ApiHubClient apiHubClient = null;
		try {
			apiHubClient = ApiHubClientSingleton.getInstance(profile).getApiHubClient();
			ApiName name = ApiName.of(profile.getProjectId(), profile.getLocation(), apiName);
			DeleteApiRequest request = DeleteApiRequest.newBuilder().setName(name.toString()).setForce(profile.getForceDelete()).build();
			apiHubClient.deleteApi(request);
		    logger.info("Delete success");
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Delete failure: " + e.getMessage());
		}
	}
	
	/**
	 * Update Api
	 * @param profile
	 * @param apiStr
	 * @throws MojoExecutionException
	 */
	public void doUpdate(BuildProfile profile, String apiStr) throws MojoExecutionException {
		ApiHubClient apiHubClient = null;
		try {
			apiHubClient = ApiHubClientSingleton.getInstance(profile).getApiHubClient();
			com.google.cloud.apihub.v1.Api apiObj = ProtoJsonUtil.fromJson(apiStr, com.google.cloud.apihub.v1.Api.class);
			List<String> fieldMaskValues = new ArrayList<>();
			fieldMaskValues.add("display_name");
			fieldMaskValues.add("description");
			fieldMaskValues.add("owner");
			fieldMaskValues.add("documentation");
			fieldMaskValues.add("target_user");
			fieldMaskValues.add("team");
			fieldMaskValues.add("business_unit");
			fieldMaskValues.add("maturity_level");
			fieldMaskValues.add("api_style");
			fieldMaskValues.add("attributes");
			FieldMask updateMask = FieldMask.newBuilder().addAllPaths(fieldMaskValues).build();
			apiHubClient.updateApi(apiObj, updateMask);
		    logger.info("Update success");
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Update failure: " + e.getMessage());
		}
	}
	
	/**
	 * Check if an Api exist
	 *  
	 * @param profile
	 * @param apiName
	 * @return
	 * @throws IOException
	 */
	public static boolean doesApiExist(BuildProfile profile, String apiName)
	            throws IOException {
		try {
        	logger.info("Checking if Api - " +apiName + " exist");
        	ApiHubClient apiHubClient = ApiHubClientSingleton.getInstance(profile).getApiHubClient();
        	ApiName name = ApiName.of(profile.getProjectId(), profile.getLocation(), apiName);
        	com.google.cloud.apihub.v1.Api apiResponse = apiHubClient.getApi(name);
        	if(apiResponse == null) 
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
	
}

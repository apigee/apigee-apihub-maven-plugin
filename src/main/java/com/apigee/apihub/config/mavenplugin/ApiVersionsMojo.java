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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import com.google.cloud.apihub.v1.DeleteVersionRequest;
import com.google.cloud.apihub.v1.ListApisRequest;
import com.google.cloud.apihub.v1.ListApisResponse;
import com.google.cloud.apihub.v1.ListVersionsRequest;
import com.google.cloud.apihub.v1.ListVersionsResponse;
import com.google.cloud.apihub.v1.LocationName;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.protobuf.FieldMask;

/**
 * Goal to configure API Versions in Apigee API Hub
 *
 * @author ssvaidyanathan
 * @goal apiversions
 * @phase install
 */
public class ApiVersionsMojo extends ApiHubAbstractMojo {
	static Logger logger = LogManager.getLogger(ApiVersionsMojo.class);

	public static final String ____ATTENTION_MARKER____ = "************************************************************************";

	enum OPTIONS {
		none, create, update, delete, sync, export
	}

	OPTIONS buildOption = OPTIONS.none;

	private BuildProfile buildProfile;

	/**
	 * Constructor.
	 */
	public ApiVersionsMojo() {
		super();
	}
	
	
	public static class ApiVersion {
        @Key
        public String name;
    }
	
	protected String getApiVersionId(String payload) 
            throws MojoFailureException {
		Gson gson = new Gson();
		try {
			ApiVersion apiVersion = gson.fromJson(payload, ApiVersion.class);
			String[] parts = apiVersion.name.split("/");
			String apiVersionId = parts[parts.length - 1];
			return apiVersionId;
		} catch (JsonParseException e) {
		  throw new MojoFailureException(e.getMessage());
		}
	}
	protected String getApiVersionName(String payload) 
            throws MojoFailureException {
		Gson gson = new Gson();
		try {
			ApiVersion apiVersion = gson.fromJson(payload, ApiVersion.class);
			return apiVersion.name;
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
			logger.info("API Hub API Versions");
			logger.info(____ATTENTION_MARKER____);

			String options = "";
			buildProfile = super.getProfile();

			options = super.getOptions();
			if (options != null) {
				buildOption = OPTIONS.valueOf(options);
			}
			if (buildOption == OPTIONS.none) {
				logger.info("Skipping API Version (default action)");
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
			if (!buildOption.equals(OPTIONS.export) && buildProfile.getConfigDir() == null) {
				throw new MojoExecutionException("API Confile Dir is missing");
			}
			if (buildOption.equals(OPTIONS.export) && buildProfile.getConfigExportDir() == null) {
				throw new MojoExecutionException("Confile Export Dir is missing");
			}
			if (buildOption.equals(OPTIONS.export) && buildProfile.getConfigExportDir() != null) {
				File f = new File(buildProfile.getConfigExportDir());
				if (!f.exists() || !f.isDirectory()) {
					throw new MojoExecutionException("Export Dir is not created or is incorrect");
				}
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
			if(buildOption == OPTIONS.export) {
				logger.info(format("Exporting apiVersions.json file to %s directory", buildProfile.getConfigExportDir()));
				exportApiVersions(buildProfile);
			} else {
				logger.info(format("Fetching apiVersions.json file from %s directory", buildProfile.getConfigDir()));
				List<String> apiVersions = ConfigReader.parseConfig(buildProfile.getConfigDir()+"/apiVersions.json");
				processApiVersions(apiVersions);
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
	 * @param apiVersions
	 * @throws MojoExecutionException
	 */
	public void processApiVersions(List<String> apiVersions) throws MojoExecutionException {
		try {
			if (buildOption != OPTIONS.update && 
					buildOption != OPTIONS.create &&
	                buildOption != OPTIONS.delete &&
	                buildOption != OPTIONS.sync) {
					return;
			}
			for (String apiVersion : apiVersions) {
				apiVersion = PluginUtils.replacer(apiVersion, PluginConstants.PATTERN, format("projects/%s/locations/%s", buildProfile.getProjectId(), buildProfile.getLocation()));
				String apiVersionId = getApiVersionId(apiVersion);
				String apiVersionName = getApiVersionName(apiVersion); //FQDN
				if (apiVersionName == null) {
	        		throw new IllegalArgumentException("Api Version does not have a name");
	        	}
				if (doesApiVersionExist(buildProfile, apiVersionName)) {
					switch (buildOption) {
						case create:
							logger.info(format("Api Version \"%s\" already exists. Skipping.", apiVersionName));
							break;
						case update:
							logger.info(format("Api Version \"%s\" already exists. Updating.", apiVersionName));
							//update
							doUpdate(buildProfile, apiVersion);
							break;
						case delete:
							logger.info(format("Api Version \"%s\" already exists. Deleting.", apiVersionName));
							//delete
							doDelete(buildProfile, apiVersionName);
							break;
						case sync:
							logger.info(format("Api Version \"%s\" already exists. Deleting and recreating.", apiVersionName));
							//delete
							doDelete(buildProfile, apiVersionName);
							logger.info(format("Creating Api Version - %s", apiVersionName));
							//create
							doCreate(buildProfile, apiVersionName, apiVersionId, apiVersion);
							break;
					}
				} else {
					switch (buildOption) {
						case create:
	                    case sync:
	                    case update:
	                    	logger.info(format("Creating Api Version - %s", apiVersionName));
	                    	//create
	                    	doCreate(buildProfile, apiVersionName, apiVersionId, apiVersion);
							break;
	                    case delete:
                            logger.info(format("Api Version \"%s\" does not exist. Skipping.", apiVersionName));
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
	public void exportApiVersions(BuildProfile profile) throws MojoExecutionException {
		ApiHubClient apiHubClient = null;
		List<String> apiVersionsList = new ArrayList<String>();
		try {
			apiHubClient = ApiHubClientSingleton.getInstance(profile).getApiHubClient();
			//Get the list of APIs
			ListApisRequest request =
					ListApisRequest.newBuilder()
						.setParent(LocationName.of(profile.getProjectId(), profile.getLocation()).toString())
						//.setFilter("display_name=\"foo\"")
						.setPageSize(PluginConstants.PAGE_SIZE).build();
			while (true) {
				ListApisResponse response = apiHubClient.listApisCallable().call(request);
				for (com.google.cloud.apihub.v1.Api api : response.getApisList()) {
					//Get the list of API Versions
					ListVersionsRequest verRequest =
							ListVersionsRequest.newBuilder()
								.setParent(api.getName())
								//.setFilter("display_name=\"API 1\"")
								.setPageSize(PluginConstants.PAGE_SIZE).build();
					while (true) {
						ListVersionsResponse verResponse = apiHubClient.listVersionsCallable().call(verRequest);
						for (com.google.cloud.apihub.v1.Version version : verResponse.getVersionsList()) {
							String apiVersionStr = ProtoJsonUtil.toJson(version);
							apiVersionStr = PluginUtils.replacer(apiVersionStr, PluginConstants.PATTERN1, format("projects/%s/locations/%s", PluginConstants.PROJECT_ID, PluginConstants.LOCATION));
							apiVersionsList.add(PluginUtils.cleanseResponse(apiVersionStr));
						}
						String verNextPageToken = verResponse.getNextPageToken();
						if (!Strings.isNullOrEmpty(verNextPageToken)) {
							verRequest = verRequest.toBuilder().setPageToken(verNextPageToken).build();
					    } else {
					       break;
					    }
					}
				}
				String nextPageToken = response.getNextPageToken();
				if (!Strings.isNullOrEmpty(nextPageToken)) {
			       request = request.toBuilder().setPageToken(nextPageToken).build();
			    } else {
			       break;
			    }
		   }
			PluginUtils.exportToFile(apiVersionsList, profile.getConfigExportDir(), "apiVersions");
		}catch (Exception e) {
			throw new RuntimeException(e.getMessage());
		}
		finally {
			apiHubClient.close();
		}
	}
	
	/**
	 * Create Api Version
	 * @param apiVersionName
	 * @param apiVersionId
	 * @param apiVersionStr
	 * @throws MojoExecutionException
	 */
	public void doCreate(BuildProfile profile, String apiVersionName, String apiVersionId, String apiVersionStr) throws MojoExecutionException {
		ApiHubClient apiHubClient = null;
		try {
			apiHubClient = ApiHubClientSingleton.getInstance(profile).getApiHubClient();
			//parse projects/PROJECT_ID/locations/LOCATION/apis/{api}/versions/{version}/specs/{spec} to get api and version
			String pattern = ".*\\/apis\\/([a-zA-Z0-9-_]+)\\/versions\\/([a-zA-Z0-9-_]+).*";
			Pattern p = Pattern.compile(pattern);
			Matcher m = p.matcher(apiVersionName);
			if(m.matches()) {
				String apiName = m.group(1);
				String version = m.group(2);
				
				ApiName parent = ApiName.of(profile.getProjectId(), profile.getLocation(), apiName);
				com.google.cloud.apihub.v1.Version apiVersionObj = ProtoJsonUtil.fromJson(apiVersionStr, com.google.cloud.apihub.v1.Version.class);
				apiHubClient.createVersion(parent, apiVersionObj, version);
				logger.info("Create success");
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Create failure: " + e.getMessage());
		}
	}

	/**
	 * Delete Api Version
	 * @param profile
	 * @param apiVersionName
	 * @throws MojoExecutionException
	 */	
	public void doDelete(BuildProfile profile, String apiVersionName) throws MojoExecutionException {
		ApiHubClient apiHubClient = null;
		try {
			apiHubClient = ApiHubClientSingleton.getInstance(profile).getApiHubClient();
			DeleteVersionRequest request = DeleteVersionRequest.newBuilder().setName(apiVersionName).setForce(profile.getForceDelete()).build();
			apiHubClient.deleteVersion(request);
		    logger.info("Delete success");
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Delete failure: " + e.getMessage());
		}
	}
	
	/**
	 * Update Api Version
	 * @param profile
	 * @param apiVersionStr
	 * @throws MojoExecutionException
	 */
	public void doUpdate(BuildProfile profile, String apiVersionStr) throws MojoExecutionException {
		ApiHubClient apiHubClient = null;
		try {
			apiHubClient = ApiHubClientSingleton.getInstance(profile).getApiHubClient();
			com.google.cloud.apihub.v1.Version apiVersionObj = ProtoJsonUtil.fromJson(apiVersionStr, com.google.cloud.apihub.v1.Version.class);
			List<String> fieldMaskValues = new ArrayList<>();
			fieldMaskValues.add("display_name");
			fieldMaskValues.add("description");
			fieldMaskValues.add("documentation");
			fieldMaskValues.add("deployments");
			fieldMaskValues.add("lifecycle");
			fieldMaskValues.add("compliance");
			fieldMaskValues.add("accreditation");
			fieldMaskValues.add("attributes");
			FieldMask updateMask = FieldMask.newBuilder().addAllPaths(fieldMaskValues).build();
			apiHubClient.updateVersion(apiVersionObj, updateMask);
		    logger.info("Update success");
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Update failure: " + e.getMessage());
		}
	}
	
	/**
	 * Check if an Api Version exist
	 *  
	 * @param profile
	 * @param apiVersionName
	 * @return
	 * @throws IOException
	 */
	public static boolean doesApiVersionExist(BuildProfile profile, String apiVersionName)
	            throws IOException {
		try {
        	logger.info("Checking if Api Version - " +apiVersionName + " exist");
        	ApiHubClient apiHubClient = ApiHubClientSingleton.getInstance(profile).getApiHubClient();
        	com.google.cloud.apihub.v1.Version apiVersionResponse = apiHubClient.getVersion(apiVersionName);
        	if(apiVersionResponse == null) 
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

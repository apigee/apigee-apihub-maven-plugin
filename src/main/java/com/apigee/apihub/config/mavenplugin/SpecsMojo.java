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
import com.apigee.apihub.config.utils.FQDNHelper;
import com.apigee.apihub.config.utils.PluginConstants;
import com.apigee.apihub.config.utils.PluginUtils;
import com.apigee.apihub.config.utils.ProtoJsonUtil;
import com.google.api.client.util.Key;
import com.google.api.client.util.Strings;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode.Code;
import com.google.cloud.apihub.v1.ApiHubClient;
import com.google.cloud.apihub.v1.ListApisRequest;
import com.google.cloud.apihub.v1.ListApisResponse;
import com.google.cloud.apihub.v1.ListSpecsRequest;
import com.google.cloud.apihub.v1.ListSpecsResponse;
import com.google.cloud.apihub.v1.ListVersionsRequest;
import com.google.cloud.apihub.v1.ListVersionsResponse;
import com.google.cloud.apihub.v1.LocationName;
import com.google.cloud.apihub.v1.SpecContents;
import com.google.cloud.apihub.v1.VersionName;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.protobuf.FieldMask;

/**
 * Goal to configure Spec in Apigee API Hub
 *
 * @author ssvaidyanathan
 * @goal specs
 * @phase install
 */
public class SpecsMojo extends ApiHubAbstractMojo {
	static Logger logger = LogManager.getLogger(SpecsMojo.class);

	public static final String ____ATTENTION_MARKER____ = "************************************************************************";

	enum OPTIONS {
		none, create, update, delete, sync, export
	}

	OPTIONS buildOption = OPTIONS.none;

	private BuildProfile buildProfile;

	/**
	 * Constructor.
	 */
	public SpecsMojo() {
		super();
	}
	
	
	public static class Spec {
        @Key
        public String name;
    }
	
	protected String getSpecId(String payload) 
            throws MojoFailureException {
		Gson gson = new Gson();
		try {
			Spec spec = gson.fromJson(payload, Spec.class);
			String[] parts = spec.name.split("/");
			String specName = parts[parts.length - 1];
			return specName;
		} catch (JsonParseException e) {
		  throw new MojoFailureException(e.getMessage());
		}
	}
	
	protected String getSpecName(String payload) 
            throws MojoFailureException {
		Gson gson = new Gson();
		try {
			Spec spec = gson.fromJson(payload, Spec.class);
			return spec.name;
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
			logger.info("API Hub Specs");
			logger.info(____ATTENTION_MARKER____);

			String options = "";
			buildProfile = super.getProfile();

			options = super.getOptions();
			if (options != null) {
				buildOption = OPTIONS.valueOf(options);
			}
			if (buildOption == OPTIONS.none) {
				logger.info("Skipping Spec (default action)");
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
				logger.info(format("Exporting specs.json file to %s directory", buildProfile.getConfigExportDir()));
				exportSpecs(buildProfile);
			} else {
				logger.info(format("Fetching specs.json file from %s directory", buildProfile.getConfigDir()));
				List<String> specs = ConfigReader.parseConfig(buildProfile.getConfigDir()+"/specs.json");
				processSpecs(specs);
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
	 * @param specs
	 * @throws MojoExecutionException
	 */
	public void processSpecs(List<String> specs) throws MojoExecutionException {
		try {
			if (buildOption != OPTIONS.update && 
					buildOption != OPTIONS.create &&
	                buildOption != OPTIONS.delete &&
	                buildOption != OPTIONS.sync) {
					return;
			}
			for (String spec : specs) {
				spec = PluginUtils.replacer(spec, PluginConstants.PATTERN, format("projects/%s/locations/%s", buildProfile.getProjectId(), buildProfile.getLocation()));
				String specId = getSpecId(spec);
				String specName = getSpecName(spec); //FQDN
				if (specName == null) {
	        		throw new IllegalArgumentException("Spec does not have a name");
	        	}
				if (doesSpecExist(buildProfile, specName)) {
					switch (buildOption) {
						case create:
							logger.info(format("Spec \"%s\" already exists. Skipping.", specName));
							break;
						case update:
							logger.info(format("Spec \"%s\" already exists. Updating.", specName));
							//update
							doUpdate(buildProfile, spec);
							break;
						case delete:
							logger.info(format("Spec \"%s\" already exists. Deleting.", specName));
							//delete
							doDelete(buildProfile, specName);
							break;
						case sync:
							logger.info(format("Spec \"%s\" already exists. Deleting and recreating.", specName));
							//delete
							doDelete(buildProfile, specName);
							logger.info(format("Creating Spec - %s", specName));
							//create
							doCreate(buildProfile, specName, specId, spec);
							break;
					}
				} else {
					switch (buildOption) {
						case create:
	                    case sync:
	                    case update:
	                    	logger.info(format("Creating Spec - %s", specName));
	                    	//create
	                    	doCreate(buildProfile, specName, specId, spec);
							break;
	                    case delete:
                            logger.info(format("Spec \"%s\" does not exist. Skipping.", specName));
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
	public void exportSpecs(BuildProfile profile) throws MojoExecutionException {
		Gson gson = new Gson();
		ApiHubClient apiHubClient = null;
		List<String> specsList = new ArrayList<String>();
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
							// Get the list of Specs
							ListSpecsRequest specRequest =
									ListSpecsRequest.newBuilder()
									.setParent(version.getName())
									//.setFilter("display_name=\"API 1\"")
									.setPageSize(PluginConstants.PAGE_SIZE).build();
							while (true) {
								ListSpecsResponse specResponse = apiHubClient.listSpecsCallable().call(specRequest);
								for (com.google.cloud.apihub.v1.Spec spec : specResponse.getSpecsList()) {
									SpecContents specContentResponse = apiHubClient.getSpecContents(spec.getName());
									String specContentStr = ProtoJsonUtil.toJson(specContentResponse);
									JsonObject jsonObject1 = gson.fromJson(specContentStr, JsonObject.class);
							    	String specStr = ProtoJsonUtil.toJson(spec);
							    	JsonObject jsonObject2 = gson.fromJson(specStr, JsonObject.class);
							    	jsonObject2.add("contents", jsonObject1);
							    	String newStr = gson.toJson(jsonObject2);
							    	newStr = PluginUtils.replacer(newStr, PluginConstants.PATTERN1, format("projects/%s/locations/%s", PluginConstants.PROJECT_ID, PluginConstants.LOCATION));
							    	specsList.add(PluginUtils.cleanseResponse(newStr));
							    }
								String specNextPageToken = specResponse.getNextPageToken();
							     if (!Strings.isNullOrEmpty(specNextPageToken)) {
							    	 specRequest = specRequest.toBuilder().setPageToken(specNextPageToken).build();
							     } else {
							       break;
							     }
							}
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
			PluginUtils.exportToFile(specsList, profile.getConfigExportDir(), "specs");
		}catch (Exception e) {
			throw new RuntimeException(e.getMessage());
		}
		finally {
			apiHubClient.close();
		}
	}
	
	/**
	 * Create Spec
	 * @param specName
	 * @param specId
	 * @param specStr
	 * @throws MojoExecutionException
	 */
	public void doCreate(BuildProfile profile, String specName, String specId, String specStr) throws MojoExecutionException {
		ApiHubClient apiHubClient = null;
		try {
			apiHubClient = ApiHubClientSingleton.getInstance(profile).getApiHubClient();
			//parse projects/PROJECT_ID/locations/LOCATION/apis/{api}/versions/{version}/specs/{spec} to get api and version
			String pattern = ".*\\/apis\\/([a-zA-Z0-9-_]+)\\/versions\\/([a-zA-Z0-9-_]+).*";
			Pattern p = Pattern.compile(pattern);
			Matcher m = p.matcher(specName);
			if(m.matches()) {
				String apiName = m.group(1);
				String version = m.group(2);
				
				VersionName parent = VersionName.of(profile.getProjectId(), profile.getLocation(), apiName, version);
				com.google.cloud.apihub.v1.Spec specObj = ProtoJsonUtil.fromJson(specStr, com.google.cloud.apihub.v1.Spec.class);
				apiHubClient.createSpec(parent, specObj, specId);
				logger.info("Create success");
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Create failure: " + e.getMessage());
		}
	}

	/**
	 * Delete Spec
	 * @param profile
	 * @param specName
	 * @throws MojoExecutionException
	 */
	public void doDelete(BuildProfile profile, String specName) throws MojoExecutionException {
		ApiHubClient apiHubClient = null;
		try {
			apiHubClient = ApiHubClientSingleton.getInstance(profile).getApiHubClient();
			apiHubClient.deleteSpec(specName);
		    logger.info("Delete success");
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Delete failure: " + e.getMessage());
		}
	}
	
	/**
	 * Update Spec
	 * @param profile
	 * @param specStr
	 * @throws MojoExecutionException
	 */
	public void doUpdate(BuildProfile profile, String specStr) throws MojoExecutionException {
		ApiHubClient apiHubClient = null;
		try {
			apiHubClient = ApiHubClientSingleton.getInstance(profile).getApiHubClient();
			com.google.cloud.apihub.v1.Spec specObj = ProtoJsonUtil.fromJson(specStr, com.google.cloud.apihub.v1.Spec.class);
			List<String> fieldMaskValues = new ArrayList<>();
			fieldMaskValues.add("display_name");
			if(FQDNHelper.checkIfJsonElementExist("$.sourceUri", specStr))
				fieldMaskValues.add("source_uri");
			fieldMaskValues.add("lint_response");			
			fieldMaskValues.add("attributes");
			if(FQDNHelper.checkIfJsonElementExist("$.contents", specStr))
				fieldMaskValues.add("contents");
			fieldMaskValues.add("spec_type");;
			FieldMask updateMask = FieldMask.newBuilder().addAllPaths(fieldMaskValues).build();
			apiHubClient.updateSpec(specObj, updateMask);
		    logger.info("Update success");
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Update failure: " + e.getMessage());
		}
	}
	
	/**
	 * Check if Spec exist
	 *  
	 * @param profile
	 * @param specName
	 * @return
	 * @throws IOException
	 */
	public static boolean doesSpecExist(BuildProfile profile, String specName)
	            throws IOException {
		try {
        	logger.info("Checking if Spec - " +specName + " exist");
        	ApiHubClient apiHubClient = ApiHubClientSingleton.getInstance(profile).getApiHubClient();
        	com.google.cloud.apihub.v1.Spec specResponse = apiHubClient.getSpec(specName);
        	if(specResponse == null) 
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

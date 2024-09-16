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
import com.apigee.apihub.config.utils.ProtoJsonUtil;
import com.google.api.client.util.Key;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode.Code;
import com.google.cloud.apihub.v1.ApiHubClient;
import com.google.cloud.apihub.v1.VersionName;
import com.google.gson.Gson;
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
		none, create, update, delete, sync
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
			logger.info(format("Fetching specs.json file from %s directory", buildProfile.getConfigDir()));
			List<String> specs = ConfigReader.parseConfig(buildProfile.getConfigDir()+"/specs.json");
			processSpecs(specs);

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
				String specName = getSpecName(spec);
				String pattern = "^([a-zA-Z0-9-_]+)\\/versions\\/([a-zA-Z0-9-_]+)\\/specs\\/([a-zA-Z0-9-_]+)$"; //{api}/versions/{version}/specs/{spec}
				Pattern p = Pattern.compile(pattern);
				Matcher m = p.matcher(specName);
				if (specName == null) {
	        		throw new IllegalArgumentException("Spec does not have a name");
	        	}
				else if(specName != null && !m.matches()) {
					throw new IllegalArgumentException(format("Spec should be in %s format", pattern));
				}
				if (doesSpecExist(buildProfile, specName)) {
					switch (buildOption) {
						case create:
							logger.info(format("Spec \"%s\" already exists. Skipping.", specName));
							break;
						case update:
							logger.info(format("Spec \"%s\" already exists. Updating.", specName));
							//update
							doUpdate(buildProfile, specName, spec);
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
							doCreate(buildProfile, specName, spec);
							break;
					}
				} else {
					switch (buildOption) {
						case create:
	                    case sync:
	                    case update:
	                    	logger.info(format("Creating Spec - %s", specName));
	                    	//create
	                    	doCreate(buildProfile, specName, spec);
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
	 * Create Spec
	 * @param specName
	 * @param specStr
	 * @throws MojoExecutionException
	 */
	public void doCreate(BuildProfile profile, String specName, String specStr) throws MojoExecutionException {
		ApiHubClient apiHubClient = null;
		try {
			apiHubClient = ApiHubClientSingleton.getInstance(profile).getApiHubClient();
			
			//parse the {api}/versions/{version}/specs/{spec}
			String pattern = "^([a-zA-Z0-9-_]+)\\/versions\\/([a-zA-Z0-9-_]+)\\/specs\\/([a-zA-Z0-9-_]+)$"; //{api}/versions/{version}/specs/{spec}
			Pattern p = Pattern.compile(pattern);
			Matcher m = p.matcher(specName);
			if(m.matches()) {
				String apiName = m.group(1);
				String version = m.group(2);
				String specId = m.group(3);
				
				VersionName parent = VersionName.of(profile.getProjectId(), profile.getLocation(), apiName, version);
				
				//replace the name field from {api}/versions/{version}/specs/{spec} to {spec}
				specStr = FQDNHelper.replaceFQDNJsonValue("$.name", specId, specStr);
				
				//update attributes with FQDN if exist
				specStr = FQDNHelper.updateFQDNJsonKey(profile, "attributes", "projects/%s/locations/%s/attributes/%s", specStr);
				
				//update specType with FQDN if exist
				specStr = FQDNHelper.updateFQDNJsonValue("$.specType.attribute", format("projects/%s/locations/%s/attributes", profile.getProjectId(), profile.getLocation()),specStr);
			
				
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
			apiHubClient.deleteSpec(format("projects/%s/locations/%s/apis/%s", profile.getProjectId(), profile.getLocation(), specName));
		    logger.info("Delete success");
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Delete failure: " + e.getMessage());
		}
	}
	
	/**
	 * Update Spec
	 * @param profile
	 * @param specName
	 * @param specStr
	 * @throws MojoExecutionException
	 */
	public void doUpdate(BuildProfile profile, String specName, String specStr) throws MojoExecutionException {
		ApiHubClient apiHubClient = null;
		try {
			apiHubClient = ApiHubClientSingleton.getInstance(profile).getApiHubClient();
			
			//updating the name field in the spec object to projects/{project}/locations/{location}/apis/{api}/versions/{version}/specs/{spec} format as its required by the updateSpec method
			specStr = FQDNHelper.updateFQDNJsonValue("$.name", 
											format("projects/%s/locations/%s/apis", profile.getProjectId(), profile.getLocation()), 
											specStr);
			
			//update attributes with FQDN if exist
			specStr = FQDNHelper.updateFQDNJsonKey(profile, "attributes", "projects/%s/locations/%s/attributes/%s", specStr);

			//update specType with FQDN if exist
			specStr = FQDNHelper.updateFQDNJsonValue("$.specType.attribute", format("projects/%s/locations/%s/attributes", profile.getProjectId(), profile.getLocation()),specStr);
		
			logger.debug("after modifying: "+ specStr);
			
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
        	com.google.cloud.apihub.v1.Spec specResponse = apiHubClient.getSpec(format("projects/%s/locations/%s/apis/%s", profile.getProjectId(), profile.getLocation(), specName));
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

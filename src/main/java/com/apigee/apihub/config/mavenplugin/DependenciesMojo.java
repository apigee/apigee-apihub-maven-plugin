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
import com.google.cloud.apihub.v1.ApiHubDependenciesClient;
import com.google.cloud.apihub.v1.DependencyName;
import com.google.cloud.apihub.v1.LocationName;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.protobuf.FieldMask;

/**
 * Goal to configure Dependencies in Apigee API Hub
 *
 * @author ssvaidyanathan
 * @goal dependencies
 * @phase install
 */
public class DependenciesMojo extends ApiHubAbstractMojo {
	static Logger logger = LogManager.getLogger(DependenciesMojo.class);

	public static final String ____ATTENTION_MARKER____ = "************************************************************************";

	enum OPTIONS {
		none, create, update, delete, sync
	}

	OPTIONS buildOption = OPTIONS.none;

	private BuildProfile buildProfile;

	/**
	 * Constructor.
	 */
	public DependenciesMojo() {
		super();
	}
	
	
	public static class Dependency {
        @Key
        public String name;
    }
	
	protected String getDependencyName(String payload) 
            throws MojoFailureException {
		Gson gson = new Gson();
		try {
			Dependency dependency = gson.fromJson(payload, Dependency.class);
			return dependency.name;
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
			logger.info("API Hub Dependency");
			logger.info(____ATTENTION_MARKER____);

			String options = "";
			buildProfile = super.getProfile();

			options = super.getOptions();
			if (options != null) {
				buildOption = OPTIONS.valueOf(options);
			}
			if (buildOption == OPTIONS.none) {
				logger.info("Skipping Dependency (default action)");
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
			logger.info(format("Fetching dependencies.json file from %s directory", buildProfile.getConfigDir()));
			List<String> dependencies = ConfigReader.parseConfig(buildProfile.getConfigDir()+"/dependencies.json");
			processDependencies(dependencies);

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
	 * @param dependencies
	 * @throws MojoExecutionException
	 */
	public void processDependencies(List<String> dependencies) throws MojoExecutionException {
		try {
			if (buildOption != OPTIONS.update && 
					buildOption != OPTIONS.create &&
	                buildOption != OPTIONS.delete &&
	                buildOption != OPTIONS.sync) {
					return;
			}
			for (String dependency : dependencies) {
				String dependencyName = getDependencyName(dependency);
				if (dependencyName == null) {
	        		throw new IllegalArgumentException("Dependency does not have a name");
	        	}
				if (doesDependencyExist(buildProfile, dependencyName)) {
					switch (buildOption) {
						case create:
							logger.info(format("Dependency \"%s\" already exists. Skipping.", dependencyName));
							break;
						case update:
							logger.info(format("Dependency \"%s\" already exists. Updating.", dependencyName));
							//update
							doUpdate(buildProfile, dependencyName, dependency);
							break;
						case delete:
							logger.info(format("Dependency \"%s\" already exists. Deleting.", dependencyName));
							//delete
							doDelete(buildProfile, dependencyName);
							break;
						case sync:
							logger.info(format("Dependency \"%s\" already exists. Deleting and recreating.", dependencyName));
							//delete
							doDelete(buildProfile, dependencyName);
							logger.info(format("Creating Dependency - %s", dependencyName));
							//create
							doCreate(buildProfile, dependencyName, dependency);
							break;
					}
				} else {
					switch (buildOption) {
						case create:
	                    case sync:
	                    case update:
	                    	logger.info(format("Creating Dependency - %s", dependencyName));
	                    	//create
	                    	doCreate(buildProfile, dependencyName, dependency);
							break;
	                    case delete:
                            logger.info(format("Dependency \"%s\" does not exist. Skipping.", dependencyName));
                            break;
					}
				}
			}
		}catch (Exception e) {
			throw new RuntimeException(e.getMessage());
		}
	}
	
	/**
	 * Create Dependency
	 * @param dependencyName
	 * @param dependencyStr
	 * @throws MojoExecutionException
	 */
	public void doCreate(BuildProfile profile, String dependencyName, String dependencyStr) throws MojoExecutionException {
		ApiHubDependenciesClient apiHubDependenciesClient = null;
		try {
			apiHubDependenciesClient = ApiHubClientSingleton.getDependenciesInstance(profile).getApiHubDependenciesClient();
			LocationName parent = LocationName.of(profile.getProjectId(), profile.getLocation());
			
			//update attributes with FQDN if exist
			dependencyStr = FQDNHelper.updateFQDNJsonKey(profile, "attributes", "projects/%s/locations/%s/attributes/%s", dependencyStr);
			
			//update externalApiResourceName for consumer and supplier
			dependencyStr = FQDNHelper.updateFQDNJsonValue("$.consumer.externalApiResourceName", 
												format("projects/%s/locations/%s", profile.getProjectId(), profile.getLocation()), 
												dependencyStr);
			dependencyStr = FQDNHelper.updateFQDNJsonValue("$.supplier.externalApiResourceName", 
												format("projects/%s/locations/%s", profile.getProjectId(), profile.getLocation()), 
												dependencyStr);
			
			//update operationResourceName for consumer and supplier
			dependencyStr = FQDNHelper.updateFQDNJsonValue("$.consumer.operationResourceName", 
												format("projects/%s/locations/%s", profile.getProjectId(), profile.getLocation()),
												dependencyStr);
			dependencyStr = FQDNHelper.updateFQDNJsonValue("$.supplier.operationResourceName", 
												format("projects/%s/locations/%s", profile.getProjectId(), profile.getLocation()), 
												dependencyStr);
			
			logger.debug("after modifying: "+ dependencyStr);

			
			com.google.cloud.apihub.v1.Dependency dependencyObj = ProtoJsonUtil.fromJson(dependencyStr, com.google.cloud.apihub.v1.Dependency.class);
			apiHubDependenciesClient.createDependency(parent, dependencyObj, dependencyName);
		    logger.info("Create success");
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Create failure: " + e.getMessage());
		}
	}

	/**
	 * Delete Dependency
	 * @param profile
	 * @param dependencyName
	 * @throws MojoExecutionException
	 */
	public void doDelete(BuildProfile profile, String dependencyName) throws MojoExecutionException {
		ApiHubDependenciesClient apiHubDependenciesClient = null;
		try {
			apiHubDependenciesClient = ApiHubClientSingleton.getDependenciesInstance(profile).getApiHubDependenciesClient();
			DependencyName name = DependencyName.of(profile.getProjectId(), profile.getLocation(), dependencyName);
			apiHubDependenciesClient.deleteDependency(name);
		    logger.info("Delete success");
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Delete failure: " + e.getMessage());
		}
	}
	
	/**
	 * Update Dependency
	 * @param profile
	 * @param dependencyName
	 * @param dependencyStr
	 * @throws MojoExecutionException
	 */
	public void doUpdate(BuildProfile profile, String dependencyName, String dependencyStr) throws MojoExecutionException {
		ApiHubDependenciesClient apiHubDependenciesClient = null;
		try {
			apiHubDependenciesClient = ApiHubClientSingleton.getDependenciesInstance(profile).getApiHubDependenciesClient();
			
			//updating the name field in the dependency object to projects/{project}/locations/{location}/dependencies/{dependency} format as its required by the updateDependency method
			dependencyStr = FQDNHelper.updateFQDNJsonValue("$.name", 
											format("projects/%s/locations/%s/dependencies", profile.getProjectId(), profile.getLocation()), 
											dependencyStr);
			
			//update attributes with FQDN if exist
			dependencyStr = FQDNHelper.updateFQDNJsonKey(profile, "attributes", "projects/%s/locations/%s/attributes/%s", dependencyStr);
			
			//update externalApiResourceName for consumer and supplier
			dependencyStr = FQDNHelper.updateFQDNJsonValue("$.consumer.externalApiResourceName", 
												format("projects/%s/locations/%s", profile.getProjectId(), profile.getLocation()), 
												dependencyStr);
			dependencyStr = FQDNHelper.updateFQDNJsonValue("$.supplier.externalApiResourceName", 
												format("projects/%s/locations/%s", profile.getProjectId(), profile.getLocation()), 
												dependencyStr);
			
			//update operationResourceName for consumer and supplier
			dependencyStr = FQDNHelper.updateFQDNJsonValue("$.consumer.operationResourceName", 
												format("projects/%s/locations/%s", profile.getProjectId(), profile.getLocation()),
												dependencyStr);
			dependencyStr = FQDNHelper.updateFQDNJsonValue("$.supplier.operationResourceName", 
												format("projects/%s/locations/%s", profile.getProjectId(), profile.getLocation()), 
												dependencyStr);
			
			logger.debug("after modifying: "+ dependencyStr);
			
			com.google.cloud.apihub.v1.Dependency dependencyObj = ProtoJsonUtil.fromJson(dependencyStr, com.google.cloud.apihub.v1.Dependency.class);
			List<String> fieldMaskValues = new ArrayList<>();
			fieldMaskValues.add("description");
			FieldMask updateMask = FieldMask.newBuilder().addAllPaths(fieldMaskValues).build();
			apiHubDependenciesClient.updateDependency(dependencyObj, updateMask);
		    logger.info("Update success");
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Update failure: " + e.getMessage());
		}
	}
	
	/**
	 * Check if an Dependency exist
	 *  
	 * @param profile
	 * @param dependencyName
	 * @return
	 * @throws IOException
	 */
	public static boolean doesDependencyExist(BuildProfile profile, String dependencyName)
	            throws IOException {
		try {
        	logger.info("Checking if Dependency - " +dependencyName + " exist");
        	ApiHubDependenciesClient apiHubDependenciesClient = ApiHubClientSingleton.getDependenciesInstance(profile).getApiHubDependenciesClient();
        	DependencyName name = DependencyName.of(profile.getProjectId(), profile.getLocation(), dependencyName);
        	com.google.cloud.apihub.v1.Dependency dependencyResponse = apiHubDependenciesClient.getDependency(name);
        	if(dependencyResponse == null) 
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

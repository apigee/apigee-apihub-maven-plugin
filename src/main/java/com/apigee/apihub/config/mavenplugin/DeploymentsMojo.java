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
import com.apigee.apihub.config.utils.PluginConstants;
import com.apigee.apihub.config.utils.PluginUtils;
import com.apigee.apihub.config.utils.ProtoJsonUtil;
import com.google.api.client.util.Key;
import com.google.api.client.util.Strings;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode.Code;
import com.google.cloud.apihub.v1.ApiHubClient;
import com.google.cloud.apihub.v1.DeploymentName;
import com.google.cloud.apihub.v1.ListDeploymentsRequest;
import com.google.cloud.apihub.v1.ListDeploymentsResponse;
import com.google.cloud.apihub.v1.LocationName;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.protobuf.FieldMask;

/**
 * Goal to configure Deployments in Apigee API Hub
 *
 * @author ssvaidyanathan
 * @goal deployments
 * @phase install
 */
public class DeploymentsMojo extends ApiHubAbstractMojo {
	static Logger logger = LogManager.getLogger(DeploymentsMojo.class);

	public static final String ____ATTENTION_MARKER____ = "************************************************************************";

	enum OPTIONS {
		none, create, update, delete, sync, export
	}

	OPTIONS buildOption = OPTIONS.none;

	private BuildProfile buildProfile;

	/**
	 * Constructor.
	 */
	public DeploymentsMojo() {
		super();
	}
	
	
	public static class Deployment {
        @Key
        public String name;
    }
	
	protected String getDeploymentName(String payload) 
            throws MojoFailureException {
		Gson gson = new Gson();
		try {
			Deployment deployment = gson.fromJson(payload, Deployment.class);
			String[] parts = deployment.name.split("/");
			String deploymentName = parts[parts.length - 1];
			return deploymentName;
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
			logger.info("API Hub Deployments");
			logger.info(____ATTENTION_MARKER____);

			String options = "";
			buildProfile = super.getProfile();

			options = super.getOptions();
			if (options != null) {
				buildOption = OPTIONS.valueOf(options);
			}
			if (buildOption == OPTIONS.none) {
				logger.info("Skipping Deployment (default action)");
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
				logger.info(format("Exporting deployments.json file to %s directory", buildProfile.getConfigExportDir()));
				exportDeployments(buildProfile);
			} else {
				logger.info(format("Fetching deployments.json file from %s directory", buildProfile.getConfigDir()));
				List<String> deployments = ConfigReader.parseConfig(buildProfile.getConfigDir()+"/deployments.json");
				processDeployments(deployments);
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
	 * @param deployments
	 * @throws MojoExecutionException
	 */
	public void processDeployments(List<String> deployments) throws MojoExecutionException {
		try {
			if (buildOption != OPTIONS.update && 
					buildOption != OPTIONS.create &&
	                buildOption != OPTIONS.delete &&
	                buildOption != OPTIONS.sync) {
					return;
			}
			for (String deployment : deployments) {
				String deploymentName = getDeploymentName(deployment);
				if (deploymentName == null) {
	        		throw new IllegalArgumentException("Deployment does not have a name");
	        	}
				if (doesDeploymentExist(buildProfile, deploymentName)) {
					switch (buildOption) {
						case create:
							logger.info(format("Deployment \"%s\" already exists. Skipping.", deploymentName));
							break;
						case update:
							logger.info(format("Deployment \"%s\" already exists. Updating.", deploymentName));
							//update
							doUpdate(buildProfile, deployment);
							break;
						case delete:
							logger.info(format("Deployment \"%s\" already exists. Deleting.", deploymentName));
							//delete
							doDelete(buildProfile, deploymentName);
							break;
						case sync:
							logger.info(format("Deployment \"%s\" already exists. Deleting and recreating.", deploymentName));
							//delete
							doDelete(buildProfile, deploymentName);
							logger.info(format("Creating Deployment - %s", deploymentName));
							//create
							doCreate(buildProfile, deploymentName, deployment);
							break;
					}
				} else {
					switch (buildOption) {
						case create:
	                    case sync:
	                    case update:
	                    	logger.info(format("Creating Deployment - %s", deploymentName));
	                    	//create
	                    	doCreate(buildProfile, deploymentName, deployment);
							break;
	                    case delete:
                            logger.info(format("Deployment \"%s\" does not exist. Skipping.", deploymentName));
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
	public void exportDeployments(BuildProfile profile) throws MojoExecutionException {
		ApiHubClient apiHubClient = null;
		List<String> deploymentList = new ArrayList<String>();
		try {
			apiHubClient = ApiHubClientSingleton.getInstance(profile).getApiHubClient();
			ListDeploymentsRequest request =
					ListDeploymentsRequest.newBuilder()
					.setParent(LocationName.of(profile.getProjectId(), profile.getLocation()).toString())
					//.setFilter("display_name=\"API 1\"")
					.setPageSize(PluginConstants.PAGE_SIZE).build();
			while (true) {
		     ListDeploymentsResponse response = apiHubClient.listDeploymentsCallable().call(request);
		     for (com.google.cloud.apihub.v1.Deployment deployment : response.getDeploymentsList()) {
		    	 String deploymentStr = ProtoJsonUtil.toJson(deployment);
		    	 deploymentStr = PluginUtils.replacer(deploymentStr, PluginConstants.PATTERN1, format("projects/%s/locations/%s", PluginConstants.PROJECT_ID, PluginConstants.LOCATION));
		    	 deploymentList.add(PluginUtils.cleanseResponse(deploymentStr));
		     }
		     String nextPageToken = response.getNextPageToken();
		     logger.debug("nextPageToken: "+ nextPageToken);
		     if (!Strings.isNullOrEmpty(nextPageToken)) {
		       request = request.toBuilder().setPageToken(nextPageToken).build();
		     } else {
		       break;
		     }
		   }
			PluginUtils.exportToFile(deploymentList, profile.getConfigExportDir(), "deployments");
		}catch (Exception e) {
			throw new RuntimeException(e.getMessage());
		}
		finally {
			apiHubClient.close();
		}
	}
	
	/**
	 * Create Deployment
	 * @param deploymentName
	 * @param deploymentStr
	 * @throws MojoExecutionException
	 */
	public void doCreate(BuildProfile profile, String deploymentName, String deploymentStr) throws MojoExecutionException {
		ApiHubClient apiHubClient = null;
		try {
			apiHubClient = ApiHubClientSingleton.getInstance(profile).getApiHubClient();
			LocationName parent = LocationName.of(profile.getProjectId(), profile.getLocation());
			com.google.cloud.apihub.v1.Deployment deploymentObj = ProtoJsonUtil.fromJson(deploymentStr, com.google.cloud.apihub.v1.Deployment.class);
			apiHubClient.createDeployment(parent, deploymentObj, deploymentName);
		    logger.info("Create success");
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Create failure: " + e.getMessage());
		}
	}

	/**
	 * Delete Deployment
	 * @param profile
	 * @param deploymentName
	 * @throws MojoExecutionException
	 */
	public void doDelete(BuildProfile profile, String deploymentName) throws MojoExecutionException {
		ApiHubClient apiHubClient = null;
		try {
			apiHubClient = ApiHubClientSingleton.getInstance(profile).getApiHubClient();
			DeploymentName name = DeploymentName.of(profile.getProjectId(), profile.getLocation(), deploymentName);
			apiHubClient.deleteDeployment(name);
		    logger.info("Delete success");
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Delete failure: " + e.getMessage());
		}
	}
	
	/**
	 * Update Deployment
	 * @param profile
	 * @param deploymentStr
	 * @throws MojoExecutionException
	 */
	public void doUpdate(BuildProfile profile, String deploymentStr) throws MojoExecutionException {
		ApiHubClient apiHubClient = null;
		try {
			apiHubClient = ApiHubClientSingleton.getInstance(profile).getApiHubClient();
			com.google.cloud.apihub.v1.Deployment deploymentObj = ProtoJsonUtil.fromJson(deploymentStr, com.google.cloud.apihub.v1.Deployment.class);
			List<String> fieldMaskValues = new ArrayList<>();
			fieldMaskValues.add("display_name");
			fieldMaskValues.add("description");
			fieldMaskValues.add("documentation");
			fieldMaskValues.add("deployment_type");
			fieldMaskValues.add("resource_uri");
			fieldMaskValues.add("endpoints");
			fieldMaskValues.add("slo");
			fieldMaskValues.add("environment");
			fieldMaskValues.add("attributes");
			FieldMask updateMask = FieldMask.newBuilder().addAllPaths(fieldMaskValues).build();
			apiHubClient.updateDeployment(deploymentObj, updateMask);
		    logger.info("Update success");
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Update failure: " + e.getMessage());
		}
	}
	
	/**
	 * Check if an Deployment exist
	 *  
	 * @param profile
	 * @param deploymentName
	 * @return
	 * @throws IOException
	 */
	public static boolean doesDeploymentExist(BuildProfile profile, String deploymentName)
	            throws IOException {
		try {
        	logger.info("Checking if Deployment - " +deploymentName + " exist");
        	ApiHubClient apiHubClient = ApiHubClientSingleton.getInstance(profile).getApiHubClient();
        	DeploymentName name = DeploymentName.of(profile.getProjectId(), profile.getLocation(), deploymentName);
        	com.google.cloud.apihub.v1.Deployment deploymentResponse = apiHubClient.getDeployment(name);
        	if(deploymentResponse == null) 
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

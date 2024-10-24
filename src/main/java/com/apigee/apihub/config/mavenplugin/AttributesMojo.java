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
import com.google.cloud.apihub.v1.AttributeName;
import com.google.cloud.apihub.v1.ListAttributesRequest;
import com.google.cloud.apihub.v1.ListAttributesResponse;
import com.google.cloud.apihub.v1.LocationName;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.protobuf.FieldMask;

/**
 * Goal to configure Artifacts in Apigee API Hub
 *
 * @author ssvaidyanathan
 * @goal attributes
 * @phase install
 */
public class AttributesMojo extends ApiHubAbstractMojo {
	static Logger logger = LogManager.getLogger(AttributesMojo.class);

	public static final String ____ATTENTION_MARKER____ = "************************************************************************";

	enum OPTIONS {
		none, create, update, delete, sync, export
	}

	OPTIONS buildOption = OPTIONS.none;

	private BuildProfile buildProfile;

	/**
	 * Constructor.
	 */
	public AttributesMojo() {
		super();
	}
	
	
	public static class Attribute {
        @Key
        public String name;
    }
	
	protected String getAttributeName(String payload) 
            throws MojoFailureException {
		Gson gson = new Gson();
		try {
			Attribute attribute = gson.fromJson(payload, Attribute.class);
			String[] parts = attribute.name.split("/");
			String attributeName = parts[parts.length - 1];
			return attributeName;
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
			logger.info("API Hub Attribute");
			logger.info(____ATTENTION_MARKER____);

			String options = "";
			buildProfile = super.getProfile();

			options = super.getOptions();
			if (options != null) {
				buildOption = OPTIONS.valueOf(options);
			}
			if (buildOption == OPTIONS.none) {
				logger.info("Skipping Attributes (default action)");
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
				logger.info(format("Exporting attributes.json file to %s directory", buildProfile.getConfigExportDir()));
				exportAttributes(buildProfile);
			} else {
				logger.info(format("Fetching attributes.json file from %s directory", buildProfile.getConfigDir()));
				List<String> attributes = ConfigReader.parseConfig(buildProfile.getConfigDir()+"/attributes.json");
				processAttributes(attributes);
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
	 * @param attributes
	 * @throws MojoExecutionException
	 */
	public void processAttributes(List<String> attributes) throws MojoExecutionException {
		try {
			if (buildOption != OPTIONS.update && 
					buildOption != OPTIONS.create &&
	                buildOption != OPTIONS.delete &&
	                buildOption != OPTIONS.sync) {
					return;
			}
			for (String attribute : attributes) {
				attribute = PluginUtils.replacer(attribute, PluginConstants.PATTERN, format("projects/%s/locations/%s", buildProfile.getProjectId(), buildProfile.getLocation()));
				String attributeName = getAttributeName(attribute);
				if (attributeName == null) {
	        		throw new IllegalArgumentException("Attribute does not have a name");
	        	}
				if (doesAttributeExist(buildProfile, attributeName)) {
					switch (buildOption) {
						case create:
							logger.info(format("Attribute \"%s\" already exists. Skipping.", attributeName));
							break;
						case update:
							logger.info(format("Attribute \"%s\" already exists. Updating.", attributeName));
							//update
							doUpdate(buildProfile, attribute);
							break;
						case delete:
							logger.info(format("Attribute \"%s\" already exists. Deleting.", attributeName));
							//delete
							doDelete(buildProfile, attributeName);
							break;
						case sync:
							logger.info(format("Attribute \"%s\" already exists. Deleting and recreating.", attributeName));
							//delete
							doDelete(buildProfile, attributeName);
							logger.info(format("Creating Attribute - %s", attributeName));
							//create
							doCreate(buildProfile, attributeName, attribute);
							break;
					}
				} else {
					switch (buildOption) {
						case create:
	                    case sync:
	                    case update:
	                    	logger.info(format("Creating Attribute - %s", attributeName));
	                    	//create
	                    	doCreate(buildProfile, attributeName, attribute);
							break;
	                    case delete:
                            logger.info(format("Attribute \"%s\" does not exist. Skipping.", attributeName));
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
	public void exportAttributes(BuildProfile profile) throws MojoExecutionException {
		ApiHubClient apiHubClient = null;
		List<String> attributesList = new ArrayList<String>();
		try {
			apiHubClient = ApiHubClientSingleton.getInstance(profile).getApiHubClient();
			ListAttributesRequest request =
					ListAttributesRequest.newBuilder()
					.setParent(LocationName.of(profile.getProjectId(), profile.getLocation()).toString())
					//.setFilter("display_name=\"API 1\"")
					.setPageSize(PluginConstants.PAGE_SIZE).build();
			while (true) {
		     ListAttributesResponse response = apiHubClient.listAttributesCallable().call(request);
		     for (com.google.cloud.apihub.v1.Attribute attribute : response.getAttributesList()) {
		    	 String attrStr = ProtoJsonUtil.toJson(attribute);
		    	 attrStr = PluginUtils.replacer(attrStr, PluginConstants.PATTERN1, format("projects/%s/locations/%s", PluginConstants.PROJECT_ID, PluginConstants.LOCATION));
		    	 attributesList.add(PluginUtils.cleanseResponse(attrStr));
		     }
		     String nextPageToken = response.getNextPageToken();
		     logger.debug("nextPageToken: "+ nextPageToken);
		     if (!Strings.isNullOrEmpty(nextPageToken)) {
		       request = request.toBuilder().setPageToken(nextPageToken).build();
		     } else {
		       break;
		     }
		   }
			PluginUtils.exportToFile(attributesList, profile.getConfigExportDir(), "attributes");
		}catch (Exception e) {
			throw new RuntimeException(e.getMessage());
		}
	}
	
	/**
	 * Create attribute
	 * @param attributeName
	 * @param attributeStr
	 * @throws MojoExecutionException
	 */
	public void doCreate(BuildProfile profile, String attributeName, String attributeStr) throws MojoExecutionException {
		ApiHubClient apiHubClient = null;
		try {
			apiHubClient = ApiHubClientSingleton.getInstance(profile).getApiHubClient();
			LocationName parent = LocationName.of(profile.getProjectId(), profile.getLocation());
			com.google.cloud.apihub.v1.Attribute attributeObj = ProtoJsonUtil.fromJson(attributeStr, com.google.cloud.apihub.v1.Attribute.class);
		    apiHubClient.createAttribute(parent, attributeObj, attributeName);
		    logger.info("Create success");
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Create failure: " + e.getMessage());
		}
	}

	/**
	 * Delete attribute
	 * @param profile
	 * @param attributeName
	 * @throws MojoExecutionException
	 */
	public void doDelete(BuildProfile profile, String attributeName) throws MojoExecutionException {
		ApiHubClient apiHubClient = null;
		try {
			apiHubClient = ApiHubClientSingleton.getInstance(profile).getApiHubClient();
			AttributeName name = AttributeName.of(profile.getProjectId(), profile.getLocation(), attributeName);
		    apiHubClient.deleteAttribute(name);
		    logger.info("Delete success");
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Delete failure: " + e.getMessage());
		}
	}
	
	/**
	 * Update attribute
	 * @param profile
	 * @param attributeStr
	 * @throws MojoExecutionException
	 */
	public void doUpdate(BuildProfile profile, String attributeStr) throws MojoExecutionException {
		ApiHubClient apiHubClient = null;
		try {
			apiHubClient = ApiHubClientSingleton.getInstance(profile).getApiHubClient();
			com.google.cloud.apihub.v1.Attribute attributeObj = ProtoJsonUtil.fromJson(attributeStr, com.google.cloud.apihub.v1.Attribute.class);
			List<String> fieldMaskValues = new ArrayList<>();
			fieldMaskValues.add("display_name");
			fieldMaskValues.add("description");
			fieldMaskValues.add("cardinality");
	        if(attributeStr.contains("ENUM"))
	        	fieldMaskValues.add("allowed_values");
			FieldMask updateMask = FieldMask.newBuilder().addAllPaths(fieldMaskValues).build();
		    apiHubClient.updateAttribute(attributeObj, updateMask);
		    logger.info("Update success");
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Update failure: " + e.getMessage());
		}
	}
	
	/**
	 * Check if an attribute exist
	 *  
	 * @param profile
	 * @param attributeName
	 * @return
	 * @throws IOException
	 */
	public static boolean doesAttributeExist(BuildProfile profile, String attributeName)
	            throws IOException {
		try {
        	logger.info("Checking if Attribute - " +attributeName + " exist");
        	ApiHubClient apiHubClient = ApiHubClientSingleton.getInstance(profile).getApiHubClient();
        	AttributeName name = AttributeName.of(profile.getProjectId(), profile.getLocation(), attributeName);
        	com.google.cloud.apihub.v1.Attribute attributeResponse = apiHubClient.getAttribute(name);
        	if(attributeResponse == null) 
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
		
	/*public static void main (String args[]) throws Exception{
		ApiHubClient apiHubClient = null;
		try {
			BuildProfile profile = new BuildProfile();
			profile.setProjectId("project");
			profile.setLocation("region");
			profile.setBearer("token");
			apiHubClient = ApiHubClientSingleton.getInstance(profile).getApiHubClient();
			AttributeName name = AttributeName.of(profile.getProjectId(), profile.getLocation(), "sample-attribute3");
        	com.google.cloud.apihub.v1.Attribute attributeResponse = apiHubClient.getAttribute(name);
        	logger.info(attributeResponse);
		} catch (ApiException e) {
			if(e.getStatusCode().getCode().equals(Code.NOT_FOUND)) {
				logger.info("here");
			}
        }
		finally {
        	apiHubClient.close();
        }
	}*/
	
}

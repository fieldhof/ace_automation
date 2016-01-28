package org.temp.probeersel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator{

	private final HttpThread thready = new HttpThread();
	
	@Override
	public void start(BundleContext context) throws Exception {
		
		thready.start();
		
	}

	@Override
	public void stop(BundleContext context) throws Exception {
	
		
	}
	
	/**
	 * Returns response body of call to targetURL
	 * @param targetURL
	 * @param urlParameters
	 * @return
	 */
	private String executeCall(String reqMethod, String targetURL, String urlParameters) {
		HttpURLConnection connection = null;
		try {
			// Create connection
			URL url = new URL(targetURL);
			connection = (HttpURLConnection) url.openConnection();
			
			connection.setRequestMethod(reqMethod);
			connection.setInstanceFollowRedirects(false);
			
			return getResponse(connection.getInputStream());

		} catch (IOException e) {
			System.out.println("Something went wrong: " + e.getMessage());
			e.printStackTrace();
			return null;
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
	}
	
	/**
	 * Retrieves the response from the inputstream
	 * @param is
	 * @return
	 * @throws IOException
	 */
	private String getResponse(InputStream is) throws IOException {
		BufferedReader rd = new BufferedReader(new InputStreamReader(is));
		String line;
		StringBuffer response = new StringBuffer();
		while ((line = rd.readLine()) != null) {
			response.append(line);
			response.append('\r');
		}
		rd.close();
		is.close();
		return response.toString();
	}
	
	private String getResponseHeader(String reqMethod, String targetURL, String urlParameters, String header) {
		HttpURLConnection connection = null;
		try {
			// Create connection
			URL url = new URL(targetURL);
			connection = (HttpURLConnection) url.openConnection();
			
			connection.setRequestMethod(reqMethod);
			connection.setFixedLengthStreamingMode(0);
			connection.setUseCaches(true);
			connection.setDoInput(true);
			connection.setDoOutput(true);
			connection.setInstanceFollowRedirects(false);
			
			connection.getOutputStream().close();
			connection.getInputStream().close();
			
			return connection.getHeaderField(header);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
	}
	
	private class HttpThread extends Thread {
		
		
		
		@Override
		public void run() {
			
			String location, response;
			while(true) {
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					e.printStackTrace();
					return;
				}
				//Get new workspace location
				location = getResponseHeader("POST", "http://localhost:8080/client/work", "", "location");
				if(location == null) {
					System.err.println("Could not get location");
					continue;
				}
				//Get all targets
				response = executeCall("GET", location + "/target", "");
				if(response == null) {
					System.err.println("Could not get targets from " + location);
					continue;
				}
				
				JSONArray targets;
				try {
					targets = new JSONArray(response);
				} catch (JSONException e) {
					System.err.println("Could not parse JSONArray");
					continue;
				}
				
				//For every target
				for(int i = 0; i < targets.length(); i++) {
					String targetID;
					try {
						targetID = targets.getString(i);
					} catch (JSONException e) {
						System.err.println("Could not get targetID from targets");
						continue;
					}
					
					//Get target and check if it need registration and/or approval
					String targetDesc = executeCall("GET", location + "/target/" + targetID, "");
					if(targetDesc == null) {
						System.err.println("Could not get target " + targetID);
						continue;
					}
					JSONObject target;
					try {
						target = new JSONObject(targetDesc);
						JSONObject state = target.getJSONObject("state");
						boolean isRegistered = state.getBoolean("isRegistered");
						boolean needsApproval = state.getBoolean("needsApproval");
						
						
						//Cannot register and approve in one go
						if(!isRegistered) {
							//Register target
							executeCall("POST", location + "/target/" + targetID + "/register", "");
						} else if(needsApproval) {
							//Approve target
							executeCall("POST", location + "/target/" + targetID + "/approve", "");
						}
					} catch (JSONException e) {
						e.printStackTrace();
						continue;
					}
					
					
				}
				
				getResponseHeader("POST", location, "", "");
				getResponseHeader("DELETE", location, "", "");
			}
			
			/*
			 * POST /client/work/
			 * retrieve 'location' header (/client/work/{WID})
			 * 
			 * GET /client/work/{WID}/target
			 * for every target
			 *   GET /client/work/{WID}/target/{TID}/
			 *   if target != registered
			 *     POST /client/work/{WID}/target/{TID}/register
			 *   if target != approved
			 *     POST /client/work/{WID}/target/{TID}/approve
			 *     
			 * POST /client/work/{WID}
			 */
			
		
		}
		
	}

}

package org.eclipse.orion.internal.server.servlets.build;

import java.io.IOException;
import java.net.URI;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.core.runtime.*;
import org.eclipse.orion.internal.server.servlets.*;
import org.eclipse.orion.internal.server.servlets.workspace.WebElementResourceHandler;
import org.eclipse.orion.internal.server.servlets.workspace.WebUser;
import org.eclipse.orion.server.servlets.OrionServlet;
import org.json.*;

/**
 * Processes an HTTP request for a site configuration resource.
 */
public class SiteConfigurationResourceHandler extends WebElementResourceHandler<SiteConfiguration> {

	private final ServletResourceHandler<IStatus> statusHandler;

	public SiteConfigurationResourceHandler(ServletResourceHandler<IStatus> statusHandler) {
		this.statusHandler = statusHandler;
	}

	/**
	 * Creates a new site configuration with the given name, a generated id, and the rest of its 
	 * properties given by <code>object</code>.
	 * @param user User creating the SiteConfiguration
	 * @param name Name for the SiteConfiguration
	 * @param object Object from which other properties will be drawn
	 * @return The created SiteConfiguration.
	 */
	public static SiteConfiguration createFromJSON(WebUser user, String name, JSONObject object) throws CoreException {
		SiteConfiguration siteConfig = user.createSiteConfiguration(name);
		copyProperties(object, siteConfig);
		return siteConfig;
	}

	private static void copyProperties(JSONObject object, SiteConfiguration siteConfig) {
		String authName = object.optString(SiteConfigurationConstants.KEY_AUTH_NAME, null);
		if (authName != null)
			siteConfig.setAuthName(authName);

		String authPassword = object.optString(SiteConfigurationConstants.KEY_AUTH_PASSWORD, null);
		if (authPassword != null)
			siteConfig.setAuthPassword(authPassword);

		String hostDomain = object.optString(SiteConfigurationConstants.KEY_HOST_DOMAIN, null);
		if (hostDomain != null)
			siteConfig.setHostDomain(hostDomain);

		JSONArray mappings = object.optJSONArray(SiteConfigurationConstants.KEY_MAPPINGS);
		if (mappings != null)
			siteConfig.setMappings(mappings);
	}

	/**
	 * @param baseLocation The URI of the SiteConfigurationServlet
	 * @return Representation of <code>siteConfig</code> as a JSONObject.
	 */
	public static JSONObject toJSON(SiteConfiguration siteConfig, URI baseLocation) {
		JSONObject result = WebElementResourceHandler.toJSON(siteConfig);
		try {
			result.put(ProtocolConstants.KEY_LOCATION, URIUtil.append(baseLocation, siteConfig.getId()).toString());
			result.put(SiteConfigurationConstants.KEY_MAPPINGS, siteConfig.getMappingsJSON());
			result.putOpt(SiteConfigurationConstants.KEY_AUTH_NAME, siteConfig.getAuthName());
			result.putOpt(SiteConfigurationConstants.KEY_AUTH_PASSWORD, siteConfig.getAuthPassword());
			result.putOpt(SiteConfigurationConstants.KEY_HOST_DOMAIN, siteConfig.getHostDomain());
		} catch (JSONException e) {
			// Can't happen
		}
		return result;
	}

	private boolean handleGet(HttpServletRequest request, HttpServletResponse response, SiteConfiguration siteConfig) throws IOException {
		JSONObject result = toJSON(siteConfig, getURI(request));
		OrionServlet.writeJSONResponse(request, response, result);
		return true;
	}

	private boolean handlePost(HttpServletRequest request, HttpServletResponse response, SiteConfiguration siteConfig) throws IOException {
		// if we're creating
		//    Write siteConfig into the body
		//    Start it, if the request has that action in it
		//    Respond 201
		// else we're just starting/stopping
		//    Just do it
		//    Respond 200
		String pathInfo = request.getPathInfo();
		IPath path = new Path(pathInfo == null ? "" : pathInfo); //$NON-NLS-1$
		boolean created = false;
		if (path.segmentCount() == 0) {
			created = true;
		}

		JSONObject result = toJSON(siteConfig, getURI(request));
		OrionServlet.writeJSONResponse(request, response, result);

		if (created) {
			response.setStatus(HttpServletResponse.SC_CREATED);
			URI location = URIUtil.append(getURI(request), siteConfig.getId());
			response.addHeader(ProtocolConstants.HEADER_LOCATION, location.toString());
		}
		return true;
	}

	private boolean handlePut(HttpServletRequest request, HttpServletResponse response, SiteConfiguration siteConfig) {
		WebUser user = WebUser.fromUserName(request.getRemoteUser());
		// FIXME implement
		return false;
	}

	private boolean handleDelete(HttpServletRequest request, HttpServletResponse response, SiteConfiguration siteConfig) throws CoreException {
		WebUser user = WebUser.fromUserName(request.getRemoteUser());
		user.deleteSiteConfiguration(siteConfig);
		return true;
	}

	@Override
	public boolean handleRequest(HttpServletRequest request, HttpServletResponse response, SiteConfiguration siteConfig) throws ServletException {
		if (siteConfig == null) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_NOT_FOUND, "Site configuration not found", null)); //$NON-NLS-1$
		}
		try {
			switch (getMethod(request)) {
				case GET :
					return handleGet(request, response, siteConfig);
				case PUT :
					return handlePut(request, response, siteConfig);
				case POST :
					return handlePost(request, response, siteConfig);
				case DELETE :
					return handleDelete(request, response, siteConfig);
			}
		} catch (IOException e) {
			return statusHandler.handleRequest(request, response, new ServerStatus(IStatus.ERROR, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage(), e));
		} catch (CoreException e) {
			return statusHandler.handleRequest(request, response, e.getStatus());
		}
		return false;
	}
}

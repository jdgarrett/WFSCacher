package com.jgarrett.wfscache.servlet;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet implementation class WFSCacher
 */

@WebServlet("/WFSCacher")
public class WFSCacher extends HttpServlet {
	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		BufferedInputStream webToProxyBuf = null;
		BufferedOutputStream proxyToClientBuf = null;
		HttpURLConnection con;

		try {
			int statusCode;
			int oneByte;
			String baseURL = request.getServerName();

			String urlString = request.getScheme()
					+ "://"
					+ baseURL
					+ "/geoserver/geonode/ows?service=WFS&version=1.0.0&request=GetFeature&";
			String queryString = request.getQueryString();
			String cachePath = getServletContext().getRealPath("/cache");
			String layer = request.getParameter("typeName");
			String evict = request.getParameter("evict");
			boolean bEvict = evict == null ? false : Boolean
					.parseBoolean(evict);
			if (bEvict) {
				if (layer != null) {
					// evict tiles from the layer
					File layerDir = new File(cachePath + "/" + layer);
					if (layerDir.exists()) {
						delete(layerDir, false);
					}
				} else {
					// evict all tiles
					File cacheDir = new File(cachePath);
					if (cacheDir.exists()) {
						delete(cacheDir, true);
					}
				}
				response.setStatus(200);
				response.getOutputStream().close();
				return;
			}
			if (layer == null) {
				response.setStatus(502);
				response.getOutputStream().close();
				return;
			}
			String tilex = request.getParameter("tilex");
			String tiley = request.getParameter("tiley");
			int x = tilex == null ? 0 : Integer.parseInt(tilex);
			int y = tiley == null ? 0 : Integer.parseInt(tiley);

			File layerDir = new File(cachePath + "/" + layer);
			// if the directory does not exist, create it
			if (!layerDir.exists()) {
				try {
					layerDir.mkdir();
				} catch (SecurityException se) {
				}
			}

			File xDir = new File(cachePath + "/" + layer + "/" + x);
			// if the directory does not exist, create it
			if (!xDir.exists()) {
				try {
					xDir.mkdir();
				} catch (SecurityException se) {
				}
			}

			File yJson = new File(cachePath + "/" + layer + "/" + x + "/" + y
					+ ".json");
			// if the file does not exist, create it
			if (!yJson.exists()) {
				System.out.println("Caching " + yJson.getAbsolutePath());
				yJson.createNewFile();
				// cache it
				urlString += queryString == null ? "" : queryString;
				System.out.println(cachePath);
				URL url = new URL(urlString);

				con = (HttpURLConnection) url.openConnection();
				HttpURLConnection.setFollowRedirects(false);
				con.setRequestMethod("GET");
				con.setDoOutput(true);
				con.setDoInput(true);
				con.setUseCaches(true);

				for (Enumeration<String> e = request.getHeaderNames(); e
						.hasMoreElements();) {
					String headerName = e.nextElement().toString();
					con.setRequestProperty(headerName,
							request.getHeader(headerName));
				}

				con.connect();

				statusCode = con.getResponseCode();
				response.setStatus(statusCode);

				for (Iterator<Entry<String, List<String>>> i = con
						.getHeaderFields().entrySet().iterator(); i.hasNext();) {
					Entry<String, List<String>> mapEntry = (Entry<String, List<String>>) i
							.next();
					if (mapEntry.getKey() != null) {
						System.out.println("Key: "
								+ mapEntry.getKey().toString()
								+ " :: Value: "
								+ ((List<String>) mapEntry.getValue()).get(0)
										.toString());
						response.setHeader(mapEntry.getKey().toString(),
								((List<String>) mapEntry.getValue()).get(0)
										.toString());
					}
				}

				InputStream in = con.getInputStream();

				FileOutputStream fos = new FileOutputStream(yJson);

				byte[] buffer = new byte[4096];
				int length;
				while ((length = in.read(buffer)) > 0) {
					fos.write(buffer, 0, length);
				}

				fos.close();

				FileInputStream fis = new FileInputStream(yJson);

				webToProxyBuf = new BufferedInputStream(fis);
				proxyToClientBuf = new BufferedOutputStream(
						response.getOutputStream());

				while ((oneByte = webToProxyBuf.read()) != -1)
					proxyToClientBuf.write(oneByte);

				proxyToClientBuf.flush();
				proxyToClientBuf.close();

				webToProxyBuf.close();
				con.disconnect();
			} else {
				// return cached
				response.setStatus(200);
				response.setHeader("Content-Type", "application/json");
				response.setHeader("Content-Encoding", "gzip");
				FileInputStream fis = new FileInputStream(yJson);

				webToProxyBuf = new BufferedInputStream(fis);
				proxyToClientBuf = new BufferedOutputStream(
						response.getOutputStream());

				while ((oneByte = webToProxyBuf.read()) != -1)
					proxyToClientBuf.write(oneByte);

				proxyToClientBuf.flush();
				proxyToClientBuf.close();

				webToProxyBuf.close();
			}

		} catch (Exception e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
		} finally {
		}
	}

	@Override
	public void init() throws ServletException {
	}

	public void destroy() {
		super.destroy();
	}

	private static void delete(File file, boolean keepBaseDir)
			throws IOException {

		if (file.isDirectory()) {

			// directory is empty, then delete it
			if (file.list().length == 0) {
				if (!keepBaseDir) {
					file.delete();
					System.out.println("Directory is deleted : "
							+ file.getAbsolutePath());
				}
			} else {
				// list all the directory contents
				String files[] = file.list();

				for (String temp : files) {
					// construct the file structure
					File fileDelete = new File(file, temp);

					// recursive delete
					delete(fileDelete, false);
				}

				// check the directory again, if empty then delete it
				if (file.list().length == 0 && !keepBaseDir) {
					file.delete();
					System.out.println("Directory is deleted : "
							+ file.getAbsolutePath());
				}
			}
		} else if (!keepBaseDir) {
			// if file, then delete it
			file.delete();
			System.out.println("File is deleted : " + file.getAbsolutePath());
		}
	}

}
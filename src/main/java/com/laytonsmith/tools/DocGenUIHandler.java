package com.laytonsmith.tools;

import com.laytonsmith.PureUtilities.WebUtility;
import com.laytonsmith.PureUtilities.XMLDocument;
import com.laytonsmith.PureUtilities.ZipReader;
import com.laytonsmith.annotations.api;
import com.laytonsmith.core.constructs.CString;
import com.laytonsmith.core.exceptions.ConfigCompileException;
import com.laytonsmith.core.exceptions.ConfigRuntimeException;
import com.laytonsmith.core.functions.Crypto;
import com.laytonsmith.core.functions.Exceptions;
import com.laytonsmith.core.functions.FunctionBase;
import com.laytonsmith.core.functions.FunctionList;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.xpath.XPathExpressionException;
import org.xml.sax.SAXException;

/**
 *
 * @author lsmith
 */
public class DocGenUIHandler {
	
	private final String pagePrefix = "<!-- This page is maintained automatically. If you would like to"
			+ " make changes to it, the proper way to do this is to make a pull request for the plugin itself. -->\n\n";
	
	public static class QuickStop extends RuntimeException{
		//
	}
	public static interface ProgressManager{
		void setProgress(Integer i);
		void setStatus(String status);
	}
	public static void main(String [] args){
		DocGenUI.main(args);
	}
	
	private static final Map<String, String> baseHeaders = new HashMap<String, String>();
	static{
		baseHeaders.put("User-Agent", "CommandHelper-DocUploader");		
	}

	URL url;
	String username;
	String password;
	String prefix;
	boolean isStaged;
	boolean doFunctions;
	boolean doExamples;
	boolean doEvents;
	boolean doTemplates;
	int totalPages = 0;
	int atPage = 0;
	
	private ProgressManager progress;
	
	boolean stop = false;
	URL endpoint;

	public DocGenUIHandler(ProgressManager progress, URL url, String username, String password, String prefix, 
			boolean isStaged, boolean doFunctions,
			boolean doExamples, boolean doEvents, boolean doTemplates) {
		
		this.progress = progress;
		
		this.url = url;
		this.username = username;
		this.password = password;
		this.prefix = prefix;
		this.isStaged = isStaged;
		this.doFunctions = doFunctions;
		this.doExamples = doExamples;
		this.doEvents = doEvents;
		this.doTemplates = doTemplates;
		if(!this.prefix.endsWith("/")){
			this.prefix += "/";
		}
		if(this.prefix.startsWith("/")){
			this.prefix = this.prefix.substring(1);
		}
	}
	
	public void stop(){
		stop = true;
	}
	
	public void checkStop(){
		if(stop){
			throw new QuickStop();
		}
	}
	
	public void go() throws Exception{
		try{
			endpoint = new URL(url.toString() + "/w/api.php");
			if(getPage(endpoint).getResponseCode() != 200){
				throw new Exception("Unable to reach wiki API.");
			}
			doLogin();
			//Now, gather up the page count, so we can set our progress bar correctly
			if(doFunctions){
				totalPages += getFunctionCount();
			}
			if(doExamples){
				totalPages += getExampleCount();
			}
			if(doEvents){
				totalPages += getEventCount();
			}
			if(doTemplates){
				totalPages += getTemplateCount();
			}
			totalPages += getMiscCount();
			progress.setProgress(0);
			if(doFunctions){
				doFunctions();
			}
			if(doExamples){
				doExamples();
			}
			if(doEvents){
				doEvents();
			}
			if(doTemplates){
				doTemplates();
			}
		} finally{
			progress.setProgress(0);
		}
	}
	
	private void doFunctions() throws XPathExpressionException{
		doUpload(DocGen.functions("wiki", api.Platforms.INTERPRETER_JAVA, isStaged), "/API");
	}
	
	private void doExamples() throws ConfigCompileException, XPathExpressionException{
		//So they are alphabetical, so we always have a consistent upload order, to
		//facilitate tracing problems.
		SortedSet<String> names = new TreeSet<String>();
		for(FunctionBase base : FunctionList.getFunctionList(api.Platforms.INTERPRETER_JAVA)){
			String name = base.getName();
			if(base.appearInDocumentation()){
				names.add(name);
			}
		}
		for(String name : names){			
			String docs = DocGen.examples(name);
			doUpload(docs, "/API/" + name);
		}
	}
	
	private void doEvents(){
		//TODO
	}
	
	private void doTemplates(){
		//TODO
	}
	
	private void doUpload(String wikiMarkup, String page) throws XPathExpressionException{
		checkStop();
		if(page.startsWith("/")){
			//The prefix already has this
			page = page.substring(1);
		}
		wikiMarkup = pagePrefix + wikiMarkup;
		//The full path 
		String fullPath = prefix + page;
		progress.setStatus("Uploading " + fullPath);
		//First we need to get the edit token
		XMLDocument content = getXML(endpoint, mapCreator(
				"action", "query",
				"titles", fullPath,
				"prop", "revisions",
				"rvprop", "sha1",
				"format", "xml"
		));
		checkStop();
		String sha1 = content.getNode("/api/query/pages/page/revisions/rev/@sha1");
		String sha1local = getSha1(wikiMarkup);
		if(!sha1.equals(sha1local)){			
			XMLDocument query = getXML(endpoint, mapCreator(
					"action", "query",
					"titles", fullPath,
					"prop", "info",
					"intoken", "edit",
					"format", "xml"
			));
			checkStop();
			String edittoken = query.getNode("/api/query/pages/page/@edittoken");			
			XMLDocument edit = getXML(endpoint, mapCreator(
					"action", "edit",
					"title", fullPath,
					"text", wikiMarkup,
					"summary", "Automatic documentation update. (This is a bot edit)",
					"bot", "true",
					"format", "xml",

					//This must always come last
					"token", edittoken
			), false);
		}
		//TODO: Check protection on this page. It should be protected
		incProgress();
	}
	
	private void incProgress(){
		atPage++;
		if(totalPages == 0){
			progress.setProgress(null);
		} else {
			progress.setProgress((int)((float)atPage / (float)totalPages * 100.0));
		}
	}
	
	private int getFunctionCount(){
		return 1;
	}
	
	private int getExampleCount(){
		return FunctionList.getFunctionList(api.Platforms.INTERPRETER_JAVA).size();
	}
	
	private int getEventCount(){
		return 1;
	}
	
	private int getTemplateCount() throws IOException{
		try {
			int count = 0;
			ZipReader reader = new ZipReader(new File(DocGenUIHandler.class.getResource("/docs").toURI()));
			Queue<File> q = new LinkedList<File>();
			q.addAll(Arrays.asList(reader.listFiles()));
			while(q.peek() != null){
				ZipReader r = new ZipReader(q.poll());
				if(r.isDirectory()){
					q.addAll(Arrays.asList(r.listFiles()));
				} else {
					count++;
				}
			}
			return count;
		} catch (URISyntaxException ex) {
			throw new APIException(ex);
		}
	}
	
	private int getMiscCount(){
		int total = 0;

		return total;
	}
	
	private void doLogin() throws MalformedURLException, XPathExpressionException{
		checkStop();
		XMLDocument login = getXML(endpoint, mapCreator(
				"format", "xml",
				"action", "login",
				"lgname", username,
				"lgpassword", password
		));
		if("NeedToken".equals(login.getNode("/api/login/@result"))){
			XMLDocument login2 = getXML(endpoint, mapCreator(
				"format", "xml",
				"action", "login",
				"lgname", username,
				"lgpassword", password,
				"lgtoken", login.getNode("/api/login/@token")
			));
			if(!"Success".equals(login2.getNode("/api/login/@result"))){
				if("WrongPass".equals(login2.getNode("/api/login/@result"))){
					throw new APIException("Wrong password.");
				}
				throw new APIException("Could not log in successfully.");
			}
		}
		progress.setStatus("Logged in");
		password = null;
	}
	
	public static class APIException extends RuntimeException{
		
		public APIException(String message){
			super(message);
		}

		public APIException(Throwable cause) {
			super("API responded incorrectly.", cause);
		}
		
	}
	
	private static String getSha1(String content){
		try {
                MessageDigest digest = java.security.MessageDigest.getInstance("SHA1");
                digest.update(content.getBytes());
                String hash = Crypto.toHex(digest.digest()).toLowerCase();
                return hash;
            } catch (NoSuchAlgorithmException ex) {
                throw new RuntimeException("An error occured while trying to hash your data", ex);
            }
	}
	
	private static Map<String, String> mapCreator(String ... strings){
		if(strings.length % 2 != 0){
			throw new Error("Only an even number of parameters may be passed to mapCreator");
		}
		Map<String, String> map = new HashMap<String, String>();
		for(int i = 0; i < strings.length; i+=2){
			map.put(strings[i], strings[i + 1]);
		}
		return map;
	}
	private static WebUtility.HTTPCookies cookieStash = new WebUtility.HTTPCookies();
	private static XMLDocument getXML(URL url, Map<String, String> params) throws APIException{
		return getXML(url, params, true);
	}
	private static XMLDocument getXML(URL url, Map<String, String> params, boolean useURL) throws APIException{
		try{
			XMLDocument doc = new XMLDocument(getPage(url, params, useURL).getContent());
			if(doc.nodeExists("/api/error")){
				//Doh.
				throw new APIException(doc.getNode("/api/error/@info"));
			}
			return doc;
		} catch(XPathExpressionException e){
			throw new APIException(e);
		} catch(SAXException e){
			throw new APIException(e);
		} catch(IOException e){
			throw new APIException(e);
		}
	}
	private static WebUtility.HTTPResponse getPage(URL url) throws IOException{
		return getPage(url, null);
	}
	private static WebUtility.HTTPResponse getPage(URL url, Map<String, String> params) throws IOException{
		return getPage(url, params, false);
	}
	/**
	 * The wiki apparently wants the parameters in the URL, but the method set to post. If useURL is
	 * true, it will merge the params into the url.
	 * @param url
	 * @param params
	 * @param useURL
	 * @return
	 * @throws IOException 
	 */
	private static WebUtility.HTTPResponse getPage(URL url, Map<String, String> params, boolean useURL) throws IOException{
		Map<String, String> headers = new HashMap<String, String>(baseHeaders);
		if (params != null && !params.isEmpty() && useURL) {
            StringBuilder b = new StringBuilder(url.getQuery() == null ? "" : url.getQuery());
            if (b.length() != 0) {
                b.append("&");
            }
            b.append(WebUtility.encodeParameters(params));
            String query = b.toString();
            url = new URL(url.getProtocol(), url.getHost(), url.getPort(), url.getPath() + "?" + query);
        }
		headers.put("Host", url.getHost());
		return WebUtility.GetPage(url, WebUtility.HTTPMethod.POST, headers, params, cookieStash, true);
	}
	
}
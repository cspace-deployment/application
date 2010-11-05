/* Copyright 2010 University of Cambridge
 * Licensed under the Educational Community License (ECL), Version 2.0. You may not use this file except in 
 * compliance with this License.
 *
 * You may obtain a copy of the ECL 2.0 License at https://source.collectionspace.org/collection-space/LICENSE.txt
 */
package org.collectionspace.chain.csp.persistence.services.vocab;
// test comment

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.collectionspace.bconfigutils.bootstrap.BootstrapConfigController;
import org.collectionspace.chain.controller.ChainServlet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mortbay.jetty.HttpHeaders;
import org.mortbay.jetty.testing.HttpTester;
import org.mortbay.jetty.testing.ServletTester;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// These are to test the functionality for Organization as defined in WebUI.java
public class TestOrgThroughWebapp {
	private static final Logger log=LoggerFactory.getLogger(TestOrgThroughWebapp.class);
	private static String cookie;
	
	// XXX refactor
	protected InputStream getResource(String name) {
		String path=getClass().getPackage().getName().replaceAll("\\.","/")+"/"+name;
		return Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
	}
	
	private static void login(ServletTester tester) throws IOException, Exception {
		HttpTester out=jettyDo(tester,"GET","/chain/login?userid=test@collectionspace.org&password=testtest",null);
		assertEquals(303,out.getStatus());
		cookie=out.getHeader("Set-Cookie");
		log.debug("Got cookie "+cookie);
	}
	
	// XXX refactor
	private static HttpTester jettyDo(ServletTester tester,String method,String path,String data) throws IOException, Exception {
		HttpTester request = new HttpTester();
		HttpTester response = new HttpTester();
		request.setMethod(method);
		request.setHeader("Host","tester");
		request.setURI(path);
		request.setVersion("HTTP/1.0");		
		if(cookie!=null)
			request.addHeader(HttpHeaders.COOKIE,cookie);
		if(data!=null)
			request.setContent(data);
		response.parse(tester.getResponses(request.generate()));
		return response;
	}
	
	// XXX refactor into other copy of this method
	private static ServletTester setupJetty() throws Exception {
		BootstrapConfigController config_controller=new BootstrapConfigController(null);
		config_controller.addSearchSuffix("test-config-loader2.xml");
		config_controller.go();
		String base=config_controller.getOption("services-url");		
		ServletTester tester=new ServletTester();
		tester.setContextPath("/chain");
		tester.addServlet(ChainServlet.class, "/*");
		tester.addServlet("org.mortbay.jetty.servlet.DefaultServlet", "/");
		tester.setAttribute("storage","service");
		tester.setAttribute("store-url",base+"/cspace-services/");	
		tester.setAttribute("config-filename","default.xml");
		tester.start();
		login(tester);
		return tester;
	}
	
	@BeforeClass public static void reset() throws Exception {
		log.info("TestOrgThroughWebapp: initialize");
		ServletTester jetty=setupJetty();
		//test if need to reset data - only reset it org auth are null
		HttpTester out=jettyDo(jetty,"GET","/chain/authorities/organization/",null);
		if(out.getStatus()<299){
			JSONArray results=new JSONObject(out.getContent()).getJSONArray("items");
			if(results.length()==0){
				jettyDo(jetty,"GET","/chain/reset/nodelete",null);
			}
		}		
		log.info("TestOrgThroughWebapp: initialize finished");
	}
	
	
	/**
	 * Tests that an authority search includes the expected item
	 * difference between an authority search and a vocabulary search is:
	 * auth search searches all the vocabularies within an auth
	 * vocab search just searches the one vocabulary	 * 
	 */
	@Test public void testAuthoritiesSearch() throws Exception {
		log.info("ORG : AuthoritiesSearch : test_start");
		ServletTester jetty=setupJetty();
		// Create
	    JSONObject data=new JSONObject("{'fields':{'displayName':'Test My Authority1'}}");
	    HttpTester out=jettyDo(jetty,"POST","/chain/vocabularies/organization/",data.toString());              
	    assertTrue(out.getStatus()<300);
	    String url=out.getHeader("Location");
		// Search
		out=jettyDo(jetty,"GET","/chain/authorities/organization/search?query=Test+My+Authority1",null);
		assertTrue(out.getStatus()<299);
		JSONArray results=new JSONObject(out.getContent()).getJSONArray("results");
		assertTrue(results.length()>0);
		Boolean test =false;
		for(int i=0;i<results.length();i++) {
			JSONObject entry=results.getJSONObject(i);
			log.info(entry.toString());
			if(entry.getString("displayName").toLowerCase().contains("test my authority1")){
				test = true;
			}
			assertEquals(entry.getString("number"),entry.getString("displayName"));
			assertTrue(entry.has("refid"));
		}
		assertTrue(test);
		// Delete
		out=jettyDo(jetty,"DELETE","/chain/vocabularies"+url,null);
		assertTrue(out.getStatus()<299);
		out=jettyDo(jetty,"GET","/chain/vocabularies"+url,null);
		assertEquals(400,out.getStatus());
		log.info("ORG : AuthoritiesSearch : test_end");
	}
	/**
	 * Tests that an authority list includes the expected item
	 * difference between an authority list and a vocabulary list is:
	 * auth list lists all the vocabularies within an auth
	 * vocab list just list the one vocabulary	 * 
	 */
	@Test public void testAuthoritiesList() throws Exception {
		log.info("ORG : AuthoritiesList : test_start");
		ServletTester jetty=setupJetty();
		// Create
	    JSONObject data=new JSONObject("{'fields':{'displayName':'Test My Authority2'}}");
	    HttpTester out=jettyDo(jetty,"POST","/chain/vocabularies/organization/",data.toString());              
	    assertTrue(out.getStatus()<300);
	    String url=out.getHeader("Location");
	    // Get List
		int resultsize =1;
		int pagenum = 0;
		String checkpagination = "";
		boolean found=false;
		while(resultsize >0){
			log.info("ORG : AuthoritiesList : Get Page: "+pagenum);
			out=jettyDo(jetty,"GET","/chain/authorities/organization?pageSize=40&pageNum="+pagenum,null);
			pagenum++;
			assertTrue(out.getStatus()<299);
			JSONArray results=new JSONObject(out.getContent()).getJSONArray("items");

			if(results.length()==0 || checkpagination.equals(results.getJSONObject(0).getString("csid"))){
				resultsize=0;
				//testing whether we have actually returned the same page or the next page - all csid returned should be unique
			}
			checkpagination = results.getJSONObject(0).getString("csid");

			for(int i=0;i<results.length();i++) {
				JSONObject entry=results.getJSONObject(i);
				if(entry.getString("displayName").toLowerCase().contains("test my authority2")){
					found=true;
					resultsize=0;
				}
			}
		}
		assertTrue(found);
		// Delete
		out=jettyDo(jetty,"DELETE","/chain/vocabularies"+url,null);
		assertTrue(out.getStatus()<299);
		out=jettyDo(jetty,"GET","/chain/vocabularies"+url,null);
		assertEquals(400,out.getStatus());
		log.info("ORG : AuthoritiesList : test_end");
	}
	/**
	 * Tests that an vocabulary search includes the expected item
	 * difference between an authority search and a vocabulary search is:
	 * auth search searches all the vocabularies within an auth
	 * vocab search just searches the one vocabulary	 * 
	 */
	@Test public void testOrganizationSearch() throws Exception {
		log.info("ORG : OrganizationSearch : test_start");
		ServletTester jetty=setupJetty();
		// Create
	    JSONObject data=new JSONObject("{'fields':{'displayName':'Test Organization XXX'}}");
	    HttpTester out=jettyDo(jetty,"POST","/chain/vocabularies/organization/",data.toString());              
	    assertTrue(out.getStatus()<300);
	    String url=out.getHeader("Location");
		// Search
		//Nuxeos rebuild borks this test - lost partial matching
		//out = GETData("/vocabularies/organization/search?query=Test+Organ", jetty);
		out = GETData("/vocabularies/organization/search?query=Test+Organization", jetty);
		assertTrue(out.getStatus()<299);
			
		JSONArray results=new JSONObject(out.getContent()).getJSONArray("results");

		Boolean test =false;
		for(int i=0;i<results.length();i++) {
			JSONObject entry=results.getJSONObject(i);
			log.info(entry.toString());
			if(entry.getString("displayName").toLowerCase().contains("test organization xxx")){
				test = true;
			}
			assertEquals(entry.getString("number"),entry.getString("displayName"));
			assertTrue(entry.has("refid"));
		}
		assertTrue(test);
		
		
		// Delete
		out=jettyDo(jetty,"DELETE","/chain/vocabularies"+url,null);
		assertTrue(out.getStatus()<299);
		out=jettyDo(jetty,"GET","/chain/vocabularies"+url,null);
		assertEquals(400,out.getStatus());

		log.info("ORG : OrganizationSearch : test_end");
	}
	
	/**
	 * Tests that a vocabularies organization list includes the expected item
	 * difference between an authority list and a vocabulary list is:
	 * auth list lists all the vocabularies within an auth
	 * vocab list just list the one vocabulary	 * 
	 */
	@Test public void testOrganizationList() throws Exception {
		log.info("ORG : OrganizationList : test_start");
		
		ServletTester jetty=setupJetty();
		// Create
	    JSONObject data=new JSONObject("{'fields':{'displayName':'Test my Org XXX1'}}");
	    HttpTester out=jettyDo(jetty,"POST","/chain/vocabularies/organization/",data.toString());              
	    assertTrue(out.getStatus()<300);
	    String url=out.getHeader("Location");

		int resultsize =1;
		int pagenum = 0;
		String checkpagination = "";
		boolean found=false;
		while(resultsize >0){
			log.info("ORG : OrganizationList : GET page:"+pagenum);
			out=jettyDo(jetty,"GET","/chain/vocabularies/organization?pageSize=40&pageNum="+pagenum,null);
			pagenum++;
			assertTrue(out.getStatus()<299);
			JSONArray results=new JSONObject(out.getContent()).getJSONArray("items");

			if(results.length()==0 || checkpagination.equals(results.getJSONObject(0).getString("csid"))){
				resultsize=0;
				//testing whether we have actually returned the same page or the next page - all csid returned should be unique
			}
			checkpagination = results.getJSONObject(0).getString("csid");
			for(int i=0;i<results.length();i++) {
				JSONObject entry=results.getJSONObject(i);
				if(entry.getString("displayName").toLowerCase().contains("test my org xxx1")){
					found=true;
					resultsize=0;
				}
			}
		}
		assertTrue(found);
		
		// Delete
		out=jettyDo(jetty,"DELETE","/chain/vocabularies"+url,null);
		assertTrue(out.getStatus()<299);
		out=jettyDo(jetty,"GET","/chain/vocabularies"+url,null);
		assertEquals(400,out.getStatus());	

		log.info("ORG : OrganizationList : test_end");
	}

	// Tests a READ for an organization
	@Test public void testOrganizationGet() throws Exception {
		log.info("ORG : OrganizationGet : test_start");
		ServletTester jetty=setupJetty();
		// Create
	    JSONObject data=new JSONObject("{'fields':{'displayName':'TestmyOrgXXX2'}}");
	    HttpTester out=jettyDo(jetty,"POST","/chain/vocabularies/organization/",data.toString());              
	    assertTrue(out.getStatus()<300);
	    String url=out.getHeader("Location");
	    // Search
		out=jettyDo(jetty,"GET","/chain/vocabularies/organization/search?query=TestmyOrgXXX2",null);
		
		assertTrue(out.getStatus()<299);
		// Find candidate
		JSONArray results=new JSONObject(out.getContent()).getJSONArray("results");
		log.info(Integer.toString(results.length()));
		
		assertTrue(results.length()>0);
		JSONObject entry=results.getJSONObject(0);
		String csid=entry.getString("csid");
		out=jettyDo(jetty,"GET","/chain/vocabularies/organization/"+csid,null);
		JSONObject fields=new JSONObject(out.getContent()).getJSONObject("fields");
		assertEquals(csid,fields.getString("csid"));
		assertEquals("TestmyOrgXXX2",fields.getString("displayName"));

		
		// Delete
		out=jettyDo(jetty,"DELETE","/chain/vocabularies"+url,null);
		assertTrue(out.getStatus()<299);
		out=jettyDo(jetty,"GET","/chain/vocabularies"+url,null);
		assertEquals(400,out.getStatus());	
		log.info("ORG : OrganizationGet : test_end");
	}
	// Tests an Update for an Organization
	@Test public void testOrganizationCreateUpdateDelete() throws Exception {
		log.info("ORG : OrganizationCreateUpdateDelete : test_start");
		ServletTester jetty=setupJetty();
		// Create
		JSONObject data=new JSONObject("{'fields':{'displayName':'Test my Org XXX4'}}");
		HttpTester out=jettyDo(jetty,"POST","/chain/vocabularies/organization/",data.toString());		
		assertTrue(out.getStatus()<300);
		String url=out.getHeader("Location");
		// Read
		out=jettyDo(jetty,"GET","/chain/vocabularies"+url,null);
		assertTrue(out.getStatus()<299);
		data=new JSONObject(out.getContent()).getJSONObject("fields");
		assertEquals(data.getString("csid"),url.split("/")[2]);
		assertEquals("Test my Org XXX4",data.getString("displayName"));
		// Update
		data=new JSONObject("{'fields':{'displayName':'A New Test Org'}}");
		out=jettyDo(jetty,"PUT","/chain/vocabularies"+url,data.toString());		
		assertTrue(out.getStatus()<300);
		// Read
		out=jettyDo(jetty,"GET","/chain/vocabularies"+url,null);
		assertTrue(out.getStatus()<299);
		data=new JSONObject(out.getContent()).getJSONObject("fields");
		assertEquals(data.getString("csid"),url.split("/")[2]);
		assertEquals("A New Test Org",data.getString("displayName"));
		// Delete
		out=jettyDo(jetty,"DELETE","/chain/vocabularies"+url,null);
		assertTrue(out.getStatus()<299);
		out=jettyDo(jetty,"GET","/chain/vocabularies"+url,null);
		assertEquals(400,out.getStatus());		
		// Try another delete - should fail
		out=jettyDo(jetty,"DELETE","/chain/vocabularies"+url,null);
		assertEquals(400,out.getStatus());	
		log.info("ORG : OrganizationCreateUpdateDelete : test_end");
		
	}

	/* this test will only work if you have field set up in default xml with two authorities assigned. 
	 * Therefore only until default needs that behaviour this test will have to manually run
	 * don't forget to add in the instances necceassry as well.
	 * @Test */
	 public void testNamesMultiAssign() throws Exception {
			log.info("ORG : NamesMultiAssign : test_start");
		ServletTester jetty=setupJetty();
		// Create in single assign list: 
		JSONObject data=new JSONObject("{'fields':{'displayName':'Custom Data'}}");
		HttpTester out=jettyDo(jetty,"POST","/chain/vocabularies/pcustom/",data.toString());		
		assertTrue(out.getStatus()<300);
		String url=out.getHeader("Location");
		data=new JSONObject("{'fields':{'displayName':'Custom Data 3'}}");
		out=jettyDo(jetty,"POST","/chain/vocabularies/pcustom/",data.toString());		
		assertTrue(out.getStatus()<300);
		String url3=out.getHeader("Location");
		// Create in second single assign list: 
		data=new JSONObject("{'fields':{'displayName':'Custom Data 2'}}");
		out=jettyDo(jetty,"POST","/chain/vocabularies/person/",data.toString());		
		assertTrue(out.getStatus()<300);
		String url2=out.getHeader("Location");
		// Read
		out=jettyDo(jetty,"GET","/chain/vocabularies"+url,null);
		assertTrue(out.getStatus()<299);
		data=new JSONObject(out.getContent()).getJSONObject("fields");
		assertEquals(data.getString("csid"),url.split("/")[2]);
		assertEquals("Custom Data",data.getString("displayName"));
		
		out=jettyDo(jetty,"GET","/chain/intake/autocomplete/currentOwner?q=Custom&limit=150",null);
		String one = out.getContent();
		out=jettyDo(jetty,"GET","/chain/intake/autocomplete/depositor?q=Custom&limit=150",null);
		String two = out.getContent();
		
		// Delete
		out=jettyDo(jetty,"DELETE","/chain/vocabularies"+url,null);
		assertTrue(out.getStatus()<299);
		out=jettyDo(jetty,"GET","/chain/vocabularies"+url,null);
		assertEquals(400,out.getStatus());
		// Delete
		out=jettyDo(jetty,"DELETE","/chain/vocabularies"+url3,null);
		assertTrue(out.getStatus()<299);
		out=jettyDo(jetty,"GET","/chain/vocabularies"+url3,null);
		assertEquals(400,out.getStatus());
		// Delete
		out=jettyDo(jetty,"DELETE","/chain/vocabularies"+url2,null);
		assertTrue(out.getStatus()<299);
		out=jettyDo(jetty,"GET","/chain/vocabularies"+url2,null);
		assertEquals(400,out.getStatus());	

		log.info("ORG : NamesMultiAssign : test_end");
	}
	
	// Tests both a person and an organization autocomplete for an organization
	@Test public void testAutocompletesForOrganization() throws Exception {
		log.info("ORG : AutocompletesForOrganization : test_start");
		ServletTester jetty=setupJetty();
		// Create
		log.info("ORG : AutocompletesForOrganization : CREATE");
	    JSONObject org=new JSONObject("{'fields':{'displayName':'Test my Org XXX5'}}");
	    HttpTester out=jettyDo(jetty,"POST","/chain/vocabularies/organization/",org.toString());              
	    assertTrue(out.getStatus()<300);
	    String url1=out.getHeader("Location");
	    // Add a person
		log.info("ORG : AutocompletesForOrganization : ADD Person");
	    JSONObject person=new JSONObject("{'fields':{'displayName':'Test Auto Person'}}");
	    out=jettyDo(jetty,"POST","/chain/vocabularies/person/",person.toString());              
	    assertTrue(out.getStatus()<300);
	    String url2=out.getHeader("Location");	  
	    // A second organization
		log.info("ORG : AutocompletesForOrganization : Add org");
	    JSONObject org2=new JSONObject("{'fields':{'displayName':'Test another Org'}}");
	    out=jettyDo(jetty,"POST","/chain/vocabularies/organization/",org2.toString());              
	    assertTrue(out.getStatus()<300);
	    String url3=out.getHeader("Location");	
	    
	    // Test Autocomplete contactName
		log.info("ORG : AutocompletesForOrganization : test against contact Name");
	    out=jettyDo(jetty,"GET","/chain/vocabularies/organization/autocomplete/contactName?q=Test+Auto&limit=150",null);
		assertTrue(out.getStatus()<299);
		JSONArray data = new JSONArray(out.getContent());
		for(int i=0;i<data.length();i++) {
			JSONObject entry=data.getJSONObject(i);
			assertTrue(entry.getString("label").toLowerCase().contains("test auto person"));
			assertTrue(entry.has("urn"));
		}
		// Test Autocomplete subBody
		log.info("ORG : AutocompletesForOrganization : test against subBody");
	    out=jettyDo(jetty,"GET","/chain/vocabularies/organization/autocomplete/subBody?q=Test+another&limit=150",null);
		assertTrue(out.getStatus()<299);
		 data = new JSONArray(out.getContent());
			for(int i=0;i<data.length();i++) {
				JSONObject entry=data.getJSONObject(i);;
			assertTrue(entry.getString("label").toLowerCase().contains("test another org"));
			assertTrue(entry.has("urn"));
		}		
		// Delete
		log.info("ORG : AutocompletesForOrganization : DELETE");
		out=jettyDo(jetty,"DELETE","/chain/vocabularies"+url1,null);
		assertTrue(out.getStatus()<299);
		out=jettyDo(jetty,"GET","/chain/vocabularies"+url1,null);
		assertEquals(400,out.getStatus());	
		out=jettyDo(jetty,"DELETE","/chain/vocabularies"+url2,null);
		assertTrue(out.getStatus()<299);
		out=jettyDo(jetty,"GET","/chain/vocabularies"+url2,null);
		assertEquals(400,out.getStatus());	
		out=jettyDo(jetty,"DELETE","/chain/vocabularies"+url3,null);
		assertTrue(out.getStatus()<299);
		out=jettyDo(jetty,"GET","/chain/vocabularies"+url3,null);
		assertEquals(400,out.getStatus());	
		log.info("ORG : AutocompletesForOrganization : test_end");
	}
	// Tests that a redirect goes to the expected place
	@Test public void testAutocompleteRedirect() throws Exception {
		log.info("ORG : AutocompleteRedirect : test_start");
		ServletTester jetty=setupJetty();
		
		HttpTester out=jettyDo(jetty,"GET","/chain/objects/source-vocab/contentOrganization",null);
		assertTrue(out.getStatus()<299);
		JSONArray data=new JSONArray(out.getContent());
		boolean test = false;
		for(int i=0;i<data.length();i++){
			String url=data.getJSONObject(i).getString("url");
			if(url.equals("/vocabularies/organization")){
				test=true;
			}
		}
		assertTrue("correct vocab not found",test);
		log.info("ORG : AutocompleteRedirect : test_end");
		
	}	
}

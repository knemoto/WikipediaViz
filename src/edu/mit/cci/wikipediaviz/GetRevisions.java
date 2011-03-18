package edu.mit.cci.wikipediaviz;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.mortbay.log.Log;

import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPMethod;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.urlfetch.URLFetchService;
import com.google.appengine.api.urlfetch.URLFetchServiceFactory;

public class GetRevisions {
	/**
	 * @param args
	 */
	private static final Logger log = Logger.getLogger(GetRevisions.class.getName());



	public List<String> sortMap(String data, int rank) {
		//log.info(data);
		List<String> output = new LinkedList<String>();
		Hashtable<String,Integer> table = new Hashtable<String,Integer>();
		Hashtable<String,Integer> editSizeTable = new Hashtable<String,Integer>();
		int prevSize  = 0;
		String[] lines = data.split("\n");
		for (int i = 0; i < lines.length; i++) {
			//log.info(i + "\t" + lines[i].split("\t").length + "\t" + lines[i]);
			String[] arr = lines[i].split("\t");
			if (arr.length < 1)
				continue;
			String user = arr[1];
			int size = Integer.parseInt(arr[4]);
			int diff = size - prevSize;
			if (editSizeTable.containsKey(user)) {
				int v = editSizeTable.get(user);
				v += diff;
				editSizeTable.put(user,v);
			} else {
				editSizeTable.put(user,diff);
			}
			prevSize = size;
			if (table.containsKey(user)) {
				int v = table.get(user);
				v++;
				table.put(user, v);
			} else {
				table.put(user, 1);
			}
		}

		ArrayList al = new ArrayList(table.entrySet());

		Collections.sort(al, new Comparator(){
			public int compare(Object obj1, Object obj2){
				Map.Entry ent1 =(Map.Entry)obj1;
				Map.Entry ent2 =(Map.Entry)obj2;
				return -(((int)Integer.parseInt(ent1.getValue().toString())) - ((int)Integer.parseInt(ent2.getValue().toString())));
			}
		});
		int alsize = al.size();
		if (alsize < rank)
			rank = alsize;
		else if (rank == 0)
			rank = alsize;
		for (int j = 0; j < rank; j++) {
			String str = al.get(j).toString();
			String user = str.substring(0,str.lastIndexOf("="));
			String edits = str.substring(str.lastIndexOf("=")+1);
			String editSize = String.valueOf(editSizeTable.get(user));
			output.add(edits + "\t" + user + "\t" + editSize);
			//log.info(edits + "\t" + user + "\t" + editSize);
		}
		return output;
	}
	
	public String getSeqColabNetwork(String data) {
		Map<String,Map<String,Integer>> matrix = new HashMap<String,Map<String,Integer>>();
		Map<String,String> map = new TreeMap<String,String>();
		List<String> list = new LinkedList<String>();
		String edges = "";
		String[] lines = data.split("\n");
		for (String line:lines) {
			String[] arr = line.split("\t");
			String userName = arr[1];
			String timestamp = arr[2];
			map.put(timestamp, userName);
			if (!list.contains(userName))
				list.add(userName);
		}
		String editor = "";
		String prevEditor = "";
		for (String timestamp:map.keySet()) {
			editor = map.get(timestamp);
			if (prevEditor.length() == 0) {
				prevEditor = editor;
				continue;
			}
			String key = "";
			String value = "";
			if (list.indexOf(editor) > list.indexOf(prevEditor)) {
				key = prevEditor;
				value = editor;
			} else {
				key = editor;
				value = prevEditor;
			}
			if (matrix.containsKey(key)) {
				Map<String,Integer> vMap = matrix.get(key);
				if (vMap.containsKey(value)) {
					int weight = vMap.get(value);
					weight++;
					vMap.put(value, weight);
				} else {
					vMap.put(value, 1);
				}
				matrix.put(key, vMap);
			} else {
				Map<String,Integer> vMap = new HashMap<String,Integer>();
				vMap.put(value, 1);
				matrix.put(key, vMap);
			}
			prevEditor = editor;
		}
		
		for (String from : matrix.keySet()) {
			Map<String,Integer> vMap = matrix.get(from);
			for (String to : vMap.keySet()) {
				int weight = vMap.get(to);
				edges += from + "\t" + to + "\t" + String.valueOf(weight) + "\n";
			}
		}
		return edges;
	}
	public String getArticleRevisions(String lang, String title, String _limit) {
		String data = "";
		int limit = Integer.parseInt(_limit);
		Result result = new Result();

		try {
			title = title.replaceAll(" ", "_");
			String xml = getArticleRevisionsXML(lang, title,"");

			XMLParseRevision parse = new XMLParseRevision(title,result,xml);
			//parse.setUserName(userName);
			parse.parse();
			int count = 0;
			while(result.hasNextId()) {
				count++;
				if (limit != 0 && count >= limit)
					break;
				String nextId = result.getNextId();
				data += result.getResult();
				result.clear();
				//tmpData.clear();
				xml = getArticleRevisionsXML(lang, title, nextId);
				parse = new XMLParseRevision(title,result,xml);
				parse.parse();
				//Thread.sleep(1000);
			}
			data += result.getResult();
		}catch(Exception e) {
			e.printStackTrace();
		}
		return data;
	}


	private String getArticleRevisionsXML(String lang, String pageid, String nextId) {
		// TODO Auto-generated method stub
		String rvstartid = "&rvstartid=" + nextId;
		if (nextId.equals("")) {
			rvstartid = "";
		}
		String xml = "";

		try {
			//String urlStr = "http://en.wikipedia.org/w/api.php";
			pageid = URLEncoder.encode(pageid,"UTF-8");
			
			String urlStr = "http://" + lang + ".wikipedia.org/w/api.php?format=xml&action=query&prop=revisions&titles="+pageid+"&rvlimit=500&rvprop=flags%7Ctimestamp%7Cuser%7Csize&rvdir=older"+rvstartid;
			//urlStr = URLEncoder.encode(urlStr);
			log.info(urlStr);
			URL url = new URL(urlStr);
			HttpURLConnection urlCon = (HttpURLConnection)url.openConnection();
			urlCon.setRequestMethod("GET");
			urlCon.setInstanceFollowRedirects(false);
			/*urlCon.addRequestProperty("format", "xml");
            urlCon.addRequestProperty("action", "query");
            urlCon.addRequestProperty("prop", "revisions");
            urlCon.addRequestProperty("titles", pageid);
            urlCon.addRequestProperty("rvlimit", "500");
            urlCon.addRequestProperty("rvprop", "flags|timestamp|user|size");
            urlCon.addRequestProperty("rvdir", "newer");*/

			urlCon.connect();
			//urlCon.setRequestProperty("titles", pageid);

			BufferedReader reader = new BufferedReader(new InputStreamReader(urlCon.getInputStream()));
			//BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
			String line;

			while ((line = reader.readLine()) != null) {
				xml += line + "\n";
			}
			//log.info(xml);
			reader.close();

		} catch (MalformedURLException e) {
			// ...
			log.info(e.getMessage());
		} catch (IOException e) {
			// ...
			log.info(e.getMessage());
		}
		return xml;
	}

}

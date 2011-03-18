package edu.mit.cci.wikipediaviz;

import edu.mit.cci.wikipediaviz.PMF;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

import javax.jdo.PersistenceManager;
import javax.servlet.ServletException;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SuppressWarnings("serial")
public class WikipediaEditVizServlet extends HttpServlet {

	private static final Logger log = Logger.getLogger(WikipediaEditVizServlet.class.getName());

	public void doGet(HttpServletRequest request, HttpServletResponse response)
	throws IOException, ServletException {
		try {
			SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			log.info("Start doGet");
			ServletContext context = this.getServletContext();
			String responseStr = "";

			//文字コードとMIMEタイプを指定する
			response.setContentType("text/html;charset=utf-8");
			//log.info("LOG@HelloServlet:: characterEncoding " + response.getCharacterEncoding());
			PrintWriter out = response.getWriter();
			String eol = "\n";

			responseStr += "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\" \"http://www.w3.org/TR/html4/strict.dtd\">" + eol;
			responseStr += "<html>" + eol;
			responseStr += "<head>" + eol;
			responseStr += "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">" + eol;
			responseStr += "<title>Get TOP10 Contributers</title>" + eol;
			responseStr += "<meta http-equiv=\"Content-Style-Type\" content=\"text/css\">" + eol;
			responseStr += "<meta http-equiv=\"Content-Script-Type\" content=\"text/javascript\">" + eol;
			responseStr += "<link href=\"css/flexgrid.css\" rel=\"stylesheet\" type=\"text/css\">" + eol;
			responseStr += "<script type=\"text/javascript\" src=\"js/jquery-1.2.1.js\"></script>" + eol;
			responseStr += "<script type=\"text/javascript\" src=\"js/treemap.js\"></script>" + eol;
			responseStr += "<script type=\"text/javascript\" src=\"js/processing.js\"></script>" + eol;
			responseStr += "<script type=\"text/javascript\" src=\"js/init.js\"></script>" + eol;
			responseStr += "<script type=\"text/javascript\">" + eol;

			String tableContents = "";
			String code = "";
			boolean includeMe = false;
			int myNumber = -1;
			// UTF-8で入力を受付
			request.setCharacterEncoding("UTF-8");
			// Get arguments
			String pageTitle = request.getParameter("name"); // WikiPedia article title
			String loginUser = "";
			if (request.getParameter("user") != null)
				loginUser = request.getParameter("user");
			boolean cacheFlag = true;
			if (request.getParameter("cache") != null) {
				if (request.getParameter("cache").equals("true"))
					cacheFlag = true;
				else
					cacheFlag = false;
			}
			// # of edits (limit x 500 edits)
			String limit = "1"; // default: last 500 edits
			if (request.getParameter("limit") != null) {
				limit = request.getParameter("limit");
			}
			// # of nodes 
			String nodeLimit = "10"; // default: 10 nodes
			if (request.getParameter("nodelimit") != null) {
				nodeLimit = request.getParameter("nodelimit");
			}
			// Canvas size
			String size = "300";
			if (request.getParameter("size") != null) {
				size = request.getParameter("size");
			}
			// Language
			String lang = "en";
			if (request.getParameter("lang") != null) {
				lang = request.getParameter("lang");
			}
			
			 // User name
			//Enumeration<String> params = request.getParameterNames();
			if (pageTitle != null) {
				if (loginUser.length() == 0){
					//loginUser = "nil";
				}
				log.info("name " + pageTitle);
				log.info("user " + loginUser);

				List<String> lines = new LinkedList<String>();

				String data = "";
				// Searching cache
				PersistenceManager pm = PMF.get().getPersistenceManager();
				
				String query = "select from " + ArticleCache.class.getName() + " where pageTitle==\'" + pageTitle.replaceAll("\'", "\\\\\'") + "\'";
				List<ArticleCache> articleCaches = (List<ArticleCache>)pm.newQuery(query).execute();
				GetRevisions gr = new GetRevisions();
				
				// No cache
				if (articleCaches.isEmpty()) {
					log.info("No cached");
					
					// Get # of edits on the pageTitle
					String download = gr.getArticleRevisions(lang, pageTitle,limit);
					String[] line = download.split("\n");
					for (int i = 0; i < line.length; i++) {
						String[] arr = line[i].split("\t");
						//arr[0] pageTitle, arr[1] userName, arr[2] timestamp, arr[3] minor, arr[4] size
						String timestamp = arr[2];
						timestamp = timestamp.replaceAll("T", " ");
						timestamp = timestamp.replaceAll("Z", "");
						data += arr[0] + "\t" + arr[1] + "\t" + timestamp + "\t0\t" + arr[4] + "\n";
						
						// Storing data to cache
						ArticleCache articleCache = new ArticleCache(arr[0],arr[1],df.parse(timestamp),Integer.parseInt(arr[4]));
						PersistenceManager pmWriter = PMF.get().getPersistenceManager();
						try {
							pmWriter.makePersistent(articleCache);
						} finally {
							pmWriter.close();
						}
					}
				}
				// Already cached and using cache
				else if (!articleCaches.isEmpty() && cacheFlag){
					log.info("Cashed");
					for(ArticleCache ac:articleCaches) {
						data += ac.getPageTitle() + "\t" + ac.getAuthor() + "\t" + df.format(ac.getDate()) + "\t0\t" + String.valueOf(ac.getSize()) + "\n";
					}
				}
				// Already cached but not using it
				else if (!articleCaches.isEmpty() && !cacheFlag) {
					// Clear cached data
					for(ArticleCache ac:articleCaches) {
						pm.deletePersistent(ac);
					}
					// Get # of edits on the pageTitle
					String download = gr.getArticleRevisions(lang, pageTitle, limit);
					String[] line = download.split("\n");
					for (int i = 0; i < line.length; i++) {
						String[] arr = line[i].split("\t");
						//arr[0] pageTitle, arr[1] userName, arr[2] timestamp, arr[3] minor, arr[4] size
						String timestamp = arr[2];
						timestamp = timestamp.replaceAll("T", " ");
						timestamp = timestamp.replaceAll("Z", "");
						data += arr[0] + "\t" + arr[1] + "\t" + timestamp + "\t0\t" + arr[4] + "\n";
						
						// Storing data
						ArticleCache articleCache = new ArticleCache(arr[0],arr[1],df.parse(timestamp),Integer.parseInt(arr[4]));
						PersistenceManager pmWriter = PMF.get().getPersistenceManager();
						try {
							pmWriter.makePersistent(articleCache);
						} finally {
							pmWriter.close();
						}
					}
				}
				pm.close();
				
				// Sort data, with second parameter: getting Top N editors
				//log.info(data);
				lines = gr.sortMap(data,0); // Get all editors' rank
				
				String userName_editSize = "";
				for (int i = 0; i < lines.size(); i++) {
					String name = lines.get(i).split("\t")[1];
					String editSize = lines.get(i).split("\t")[2];
					userName_editSize += name + "\t" + editSize + "\n";
				}
				
				// Get absolute path to skeleton file
				String path = context.getRealPath("/skelton/skelton_spring.js");

				String nodes = "";
				for (int i = 0; i < lines.size(); i++) {
					// Name \t # of edits
					nodes += lines.get(i).split("\t")[1] + "\t" + lines.get(i).split("\t")[0] + "\n";
				}
				log.info(nodes);
				// Get edge data
				// From editor \t To editor \t weight
				log.info(data);
				String edges = gr.getSeqColabNetwork(data);
				log.info(edges);
				if (edges.length() > 0) {
					//log.info(nodes);
					//log.info(edges);
					//Optimization op = new Optimization();
					//String location = op.run(nodes, edges, Integer.valueOf(size), Integer.valueOf(size));
					//log.info("Location\n" + location);
					Processing pro = new Processing();
					//code = pro.processingCode(location, edges, loginUser, path, userName_editSize, size, size);
					code = pro.processingCode(nodes, edges, loginUser, path, userName_editSize, size);
					//log.info("Processing code\n" + code);

					// 多次元尺度構成法の場合
					//sd.setupData(nodes,edges);
					//code = sd.processingCode(sd.scaledown(0.01),edges,loginUser);
				}

				//出力ストリームを取得する
				out = response.getWriter();

				// processingの起動
				responseStr += "window.onload=function(){" + eol;
				
				responseStr += "var canvas = document.getElementsByTagName(\'canvas\')[0];" + eol;
				// Processingのソースコードが書かれた要素を参照
				responseStr += "var codeElement = document.getElementById(\'processing-code\');" + eol;
				responseStr += "var code = codeElement.textContent || codeElement.innerText;" + eol;
				responseStr += "Processing(canvas,code);" + eol;
				
				responseStr += "};" + eol;
				responseStr += "</script>" + eol;

				responseStr += "<script id=\"processing-code\" type=\"application/processing\">" + eol;
				// ここにProcessingのコードを挿入
				responseStr += code;
				responseStr += "</script>" + eol;

				responseStr += "</head>" + eol;
				responseStr += "<body>" + eol;

				responseStr += "<div><canvas width=\"" + size + "\" height=\"" + size + "\"></canvas></div>" + eol;

				responseStr += tableContents;

				responseStr += "</body>" + eol;
				responseStr += "</html>" + eol;
				
				out.println(responseStr);
				out.close();
			}
		}
		catch(Exception e){
			e.printStackTrace();
			throw new ServletException(e);
		}
	}
}

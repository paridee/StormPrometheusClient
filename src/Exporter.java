import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.MetricsServlet;

public class Exporter {
	public final static Logger logger	=	LogManager.getLogger(Exporter.class);
	String UIUrl;
	String UIPort;
	String clientPort;
	ArrayList<String> 	activeTopologies	=	new ArrayList();
	private String windowSize;
	HashMap<String,Gauge> gauges	=	new HashMap<String,Gauge>();
	
	public Exporter(String url, String port1, String port2, String window) {
		super();
		UIUrl			=	url;
		UIPort			=	port1;
		clientPort		=	port2;
		this.windowSize	=	window;
		this.monitor();
	}

	private void resetGauges(){
		CollectorRegistry.defaultRegistry.clear();;
	}
	
	private void makeGauge(JSONObject obj,Class aClass,String name,String help,String[]	labels,String[]	labelsValue){
		Gauge 	shellGauge;
		if(this.gauges.containsKey(name)){
			shellGauge	=	gauges.get(name);
		}
		else{
			shellGauge	=	Gauge.build().name(name).help(help).labelNames(labels).register();
			gauges.put(name, shellGauge);
		}
		double value	=	0;
		if(aClass.equals(Integer.class)){
			value		=	obj.getInt(name);
		}
		else if(aClass.equals(String.class)){
			//logger.debug("reading string "+obj.getString(name));
			value		=	Double.parseDouble(obj.getString(name));
		}
		Gauge.Child	childG=	new Gauge.Child();
		childG.set(value);
		shellGauge.setChild(childG, labelsValue);
	}
	
	private void monitor() {
		launchWebServerForPrometheus(Integer.parseInt(clientPort));
		//logger.debug("going to monitor");
		String temp	=	UIUrl.substring(0, 5);
		if(!temp.equals("http")){
			UIUrl	=	"http://"+UIUrl;
		}
		while(true){
			logger.debug("cycle");
			JSONObject	summary	=	null;
			resetGauges();
			this.gauges.clear();
			try {
				summary					=	readJsonFromUrl(UIUrl+":"+UIPort+"/api/v1/topology/summary");
				JSONArray	topologies	=	summary.getJSONArray("topologies");
				String[]	labels	=	new String[1];
				labels[0]			=	"name";
				for(int i=0;i<topologies.length();i++){
					JSONObject	topology	=	topologies.getJSONObject(i);
					//logger.debug("Topology:\n\n"+topology.toString());
					if(topology.getString("status").equals("ACTIVE")){
						this.activeTopologies.add(topology.getString("encodedId"));
						logger.debug("Active topology found: "+topology.getString("id"));
						String uptStr	=	topology.getString("uptime");
						String[] upt	=	uptStr.split(" ");
						int[]	uptInt	=	new int[5];		
						for(int j=0;j<upt.length;j++){
							uptInt[j]	=	Integer.parseInt(upt[j].substring(0, upt[j].length()-1));
						}
						Gauge.Child	uptime	=	new Gauge.Child();
						String[] labelsTop	=	new String[1];
						labelsTop[0]		=	topology.getString("name");
						this.makeGauge(topology,Integer.class, "tasksTotal", "Total number of tasks for this topology", labels, labelsTop);
						this.makeGauge(topology,Integer.class, "workersTotal", "Number of workers used for this topology", labels, labelsTop);
						this.makeGauge(topology,Integer.class, "executorsTotal", "Number of executors used for this topology", labels, labelsTop);
						JSONObject 	topologyJson	=	readJsonFromUrl(UIUrl+":"+UIPort+"/api/v1/topology/"+topology.getString("id")+"?window=600");
						this.makeGauge(topologyJson,Integer.class, "window", "Window size for metric misuration", labels, labelsTop);
						
						JSONArray	spoutsArray		=	topologyJson.getJSONArray("spouts");
						JSONArray	boltsArray		=	topologyJson.getJSONArray("bolts");						
						processSpoutsArray(spoutsArray,topology.getString("name"));
						processBoltsArray(boltsArray,topology.getString("name"));
					}
				}
			} catch (JSONException | IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				logger.debug(e.getMessage());
				return;
			}
			try {
				Thread.sleep(Integer.parseInt(this.windowSize)*1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}	
		}
	}
	
	private void processBoltsArray(JSONArray boltsArray, String string) {
		String[]	labels		=	new String[2];
		labels[0]				=	"name";
		labels[1]				=	"operatorName";
		for(int i=0;i<boltsArray.length();i++){
			JSONObject	bolt		=	boltsArray.getJSONObject(i);
			String[]	labelsV		=	new String[2];
			labelsV[0]				=	string;
			labelsV[1]				=	bolt.getString("boltId");
			this.makeGauge(bolt,String.class, "capacity", "This value indicates number of messages executed * average execute latency / time window", labels, labelsV);
			this.makeGauge(bolt,String.class, "processLatency", "Bolt's average time to ack a message after it's received", labels, labelsV);
			this.makeGauge(bolt,String.class, "executeLatency", "Average time for bolt's execute method", labels, labelsV);
			this.makeGauge(bolt,Integer.class, "tasks", "Total number of tasks for the bolt", labels, labelsV);
			this.makeGauge(bolt,Integer.class, "executed", "Total number of tasks for the bolt", labels, labelsV);
			this.makeGauge(bolt,Integer.class, "executors", "	Number of executors for the spout", labels, labelsV);
		}
	}

	private void processSpoutsArray(JSONArray spoutsArray, String string) {
		String[]	labels		=	new String[2];
		labels[0]				=	"name";
		labels[1]				=	"operatorName";
		for(int i=0;i<spoutsArray.length();i++){
			JSONObject	spout		=	spoutsArray.getJSONObject(i);
			String[]	labelsV		=	new String[2];
			labelsV[0]				=	string;
			labelsV[1]				=	spout.getString("spoutId");
			this.makeGauge(spout,Integer.class, "executors", "	Number of executors for the spout", labels, labelsV);
			this.makeGauge(spout,String.class, "completeLatency", "Total latency for processing the message", labels, labelsV);
			this.makeGauge(spout,Integer.class, "tasks", "Total number of tasks for the spout", labels, labelsV);
			this.makeGauge(spout,Integer.class, "acked", "Total number of acked for the spout", labels, labelsV);
			this.makeGauge(spout,Integer.class, "emitted", "Emitted tuple from the spout", labels, labelsV);
		}
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		BasicConfigurator.configure();			//default logging configuration
		if(args.length!=4){
			logger.debug("Wrong usage, arguments: STORM_UI_URL STORM_UI_PORT PROMETHEUS_CLIENT_PORT WINDOW_SIZE");
		}
		else{
			logger.debug("Arguments "+args[0]+":"+args[1]+" "+args[2]+" "+args[3]);
			new Exporter(args[0],args[1],args[2],args[3]);
		}
	}
	
	  public static JSONObject readJsonFromUrl(String url) throws IOException, JSONException {
		    InputStream is = new URL(url).openStream();
		    try {
		      BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
		      String jsonText = readAll(rd);
		      JSONObject json = new JSONObject(jsonText);
		      return json;
		    } finally {
		      is.close();
		    }
		  }

	  private static String readAll(Reader rd) throws IOException {
		    StringBuilder sb = new StringBuilder();
		    int cp;
		    while ((cp = rd.read()) != -1) {
		      sb.append((char) cp);
		    }
		    return sb.toString();
		  }
	  
	public static void launchWebServerForPrometheus(int port){
		  Server server = new Server(port);
		  ServletContextHandler context = new ServletContextHandler();
		  context.setContextPath("/");
		  server.setHandler(context);
		  context.addServlet(new ServletHolder(new MetricsServlet()), "/metrics");
		  try {
			  server.start();
		  } catch (Exception e1) {
			  e1.printStackTrace();
		  }
	}
	
}

package http.client;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
public class Client {
	private String host;
	private String url;
	private final int default_port = 80;
	private Scanner scanner;
	private ArrayList<String> links = new ArrayList<String>();
	private ArrayList<String> images = new ArrayList<String>();
	private ArrayList<String> path = new ArrayList<String>();
	private ArrayList<String> text = new ArrayList<String>();
	private int marker;
	public Client(){

		scanner = new Scanner(System.in);
		while(true){
			if(!play_your_game()) break;
		}

		scanner.close();
	}
	
	private boolean play_your_game(){
		System.out.println("Unesite adresu (ili \"stop\" za prekidanje): ");
		marker = 0;
		path.clear();
		String input = scanner.nextLine();
		path.add(input);
		while(true){
			init();
			System.out.println(path.get(marker));
			if(!solve_host_url(path.get(marker))) return false;
			int http_code = show();
			solve_http_code(http_code);
			print_links();
			System.out.println(links.size()+1+". "+"Back");
			System.out.println("Choose an option");
			input = scanner.nextLine();
			while(!isInteger(input) || Integer.parseInt(input)<=0 || Integer.parseInt(input) > links.size()+1 ){
				System.out.println("Chose right option:");
				input = scanner.nextLine();
			}
				int opt = Integer.parseInt(input);
				if(opt == links.size()+1){
					path.remove(marker);
					marker--;
				}
				else{
					path.add(links.get(opt-1));
					marker++;
				}
			if(marker == -1)
				break;
		}
		return true;
	}
	public static boolean isInteger(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException nfe) {}
        return false;
    }
	
	private void init(){
		links.clear();
		images.clear();
		text.clear();
	}
	
	private boolean solve_host_url(String text){
		if(text.equals("stop"))
			return false;
		if(text.startsWith("http://"))
			text = text.substring(7);
		if(text.startsWith("https://")) 
			text = text.substring(8);
		int startIndexOfHost = text.indexOf("www.");
		if(startIndexOfHost == -1)
			startIndexOfHost = 0;
		
		int endIndexOfHost = text.indexOf("/",startIndexOfHost+1);
		if(endIndexOfHost == -1)
			endIndexOfHost = text.length();
		host = text.substring(startIndexOfHost, endIndexOfHost);
		if(!host.startsWith("www."))
			host = "www." + host;
		if(endIndexOfHost != text.length())
			url = text.substring(endIndexOfHost);
		else 
			url = "/";
		//System.out.println(host + "  -- " + url);
		return true;
	}
	
	public int show(){
		String content;
		Socket socket;
		PrintWriter writer;
		BufferedReader reader;

		try {
			socket = new Socket(host, default_port);
			writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
			send_http_request(writer);
			reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			String response_header = read_header_response(reader);

			System.out.println(response_header);

			int http_code = get_http_code(response_header);
			/*
			 * Moved permanently, trazeni objekat trajno je uklonjen; novi url
			 * navodi se u zaglavlju Location:
			 */
			//while(http_code == 301)
			if (http_code == 301 || http_code == 302) {	
				System.out.println("Saljemo zahtev sa novim hederom ");
				solve_host_url(get_address_from_response(response_header));
				send_http_request(writer);
				response_header = read_header_response(reader);
				System.out.println(response_header);
				//http_code = get_http_code(response_header);
			}
			if (http_code == 200) {	
				content = read_content(reader);
				if(get_title(content)!=null)
					System.out.println(get_title(content));
				content = clear_code(content);
				get_links(content);
				content = delete_scripts(content);
				content = delete_stiles(content);
				PrintWriter write = new PrintWriter("temp.html", "UTF-8");
				write.println(content);
				write.close();			
				try {
					System.out.println("Text");
					get_text();
					System.out.println("Images");
					get_images(content);
				} catch (Exception e) {
					
				}
			}

			socket.close();
			return http_code;
			
		} catch (UnknownHostException e) {
			return -1;
		} catch (IOException e) {
			return -1;
		}
	}
	
	private void send_http_request(PrintWriter writer) {
		writer.println("GET " + url + " HTTP/1.1");
        writer.println("Host: " + host);
        writer.println("User-Agent: Mozzila/4.0");
        writer.println("");
        writer.flush();
	}
	
	private String read_header_response(BufferedReader reader) {
		StringBuilder output = new StringBuilder();
		String input;
		
		try {
			while((input = reader.readLine()) != null){
				output.append(input + "\n") ;
				if(input.isEmpty()) break;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return output.toString();
	}
	
	private int get_http_code(String input) {
		int firstSpace = input.indexOf(" ",0);
		int secondSpace = input.indexOf(" ",firstSpace + 1);
		return Integer.parseInt(input.substring(firstSpace+1, secondSpace));
	}
	
	private String get_address_from_response(String input) {
		StringBuilder output = new StringBuilder();
		int startIndexOfAddress = input.indexOf("Location: ") + 10;
		int endIndexOfAddress = input.indexOf("\n", startIndexOfAddress + 1);
		output.append(input.substring(startIndexOfAddress,endIndexOfAddress));
		return output.toString();
	}
	
	private String read_content(BufferedReader reader) {
		StringBuilder output = new StringBuilder();
		String input;
		Scanner scanner = new Scanner(reader);

			while(scanner.hasNext()){
			    input = scanner.next();
			    //System.out.println(input);
			    output.append(input+" ");
			    // moram da ga prekinem nasilno ne znam koji mu klinac
			    if(input.contains("</html>") || input.contains("</body>")){
			    	break;
			    }
			}
			scanner.close();
		
		return output.toString();
	}
	//izbacicu specijalne karaktere komentare i sl
	public String clear_code(String content){
		content = content.replaceAll("(?s)<!--.*?-->", ""); 
		content = content.replaceAll("(?s)<link.*?/>", "");
		content = content.replaceAll("(?s)&.*?", "");
		
        return content;
	}
	//izbacicu skriptove
	public String delete_scripts(String content){
		content = content.replaceAll("(?s)<script.*?/>", "");
		return content;
	}
	//Izbacicu cssove
	public String delete_stiles(String content){
		content = content.replaceAll("(?s)<style.*?/>", "");
		return content;
	}
	
	public void get_links(String content){
	int startIndexOfHost = content.indexOf("href=\"") + 6;
		int endIndexOfHost = content.indexOf("\"",startIndexOfHost+1);
		//int i = 0;
		while(true){
			String link = content.substring(startIndexOfHost, endIndexOfHost);
			
			//ne dupliramo linkove
			if(!links.contains(solve_address(link)))
				links.add(solve_address(link));
			//System.out.println(i++ + ") "+solve_address(link));
			startIndexOfHost = content.indexOf("href=\"",endIndexOfHost+1) + 6;
			if(startIndexOfHost == 5) break;
			endIndexOfHost = content.indexOf("\"",startIndexOfHost+1);
		}
	
	}
	
	private String solve_address(String input){
		if(input.startsWith("www.")) return input;
		if(input.startsWith("http")) return input;
		if(url.equals("/"))
			return host+input;
		else return host+url+input;
	}
	
	private void print_links(){
		System.out.println("Links");
		for(int i = 1; i <= links.size(); i++)
			System.out.println(i +". " +links.get(i-1));
	}
	
	private String get_title(String content){
		Pattern TITLE_TAG = Pattern.compile("\\<title>(.*)\\</title>", Pattern.CASE_INSENSITIVE|Pattern.DOTALL);
		Matcher matcher = TITLE_TAG.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).replaceAll("[\\s\\<>]+", " ").trim();
        }
        else
            return null;
	}
	
	private void get_text() throws Exception{
		FileReader reader = new FileReader("temp.html");
		
	    text = HTMLUtils.extractText(reader);
	    int i = 0;
	    for (String line : text) {
	        System.out.println(i + ". " + line);
	        i++;
	    }
	    
	}
	
	private void get_images(String content){
		Pattern p = Pattern.compile("<img[^>]*src=[\"']([^\"^']*)",
                Pattern.CASE_INSENSITIVE);
		Matcher m = p.matcher(content);
		int i = 0;
		while (m.find())
		    System.out.println(i++ + ". " + solve_address((m.group(1)).toString()));
	}
	
	private void solve_http_code(int http_code) {
		switch (http_code) {
		case 200:
			break;
		case 301:
			System.out.println("301 Moved Permanently");
			break;
		case 302:
			System.out.println("302 Found");
			break;
		case 305:
			System.out.println("305 Use Proxy");
			break;
		case 306:
			System.out.println("306 (Unused)");
			break;
		case 307:
			System.out.println("307 Temporary Redirect");
			break;
		case 400:
			System.out.println("400 Bad request");
			break;
		case 401:
			System.out.println("401 Unauthorized");
			break;
		case 402:
			System.out.println("402 Payment Required");
			break;
		case 403:
			System.out.println("403 Forbidden");
			break;
		case 404:
			System.out.println("404 Not Found");
			break;
		case 405:
			System.out.println("405 Method Not Allowed");
			break;
		case 406:
			System.out.println("406 Not Acceptable");
			break;
		case 407:
			System.out.println("407 Proxy Authentication Required");
			break;
		case 408:
			System.out.println("408 Request Timeou");
			break;
		case 409:
			System.out.println("409 Conflict");
			break;
		case 410:
			System.out.println("410 Gone");
			break;
		case 411:
			System.out.println("411 Length Required");
			break;
		case 412:
			System.out.println("412 Precondition Failed");
			break;
		case 413:
			System.out.println("413 Request Entity Too Large");
			break;
		case 414:
			System.out.println("414 Request-URI Too Long");
			break;
		case 415:
			System.out.println("415 Unsupported Media Type");
			break;
		case 416:
			System.out.println("416 Requested Range Not Satisfiable");
			break;
		case 417:
			System.out.println("417 Expectation Failed");
			break;
		case 500:
			System.out.println("500 Internal Server Error");
			break;
		case 501:
			System.out.println("501 Not Implemented");
			break;
		case 502:
			System.out.println("502 Bad Gateway");
			break;
		case 503:
			System.out.println("503 Service Unavailable");
			break;
		case 504:
			System.out.println("504 Gateway Timeout");
			break;
		case 505:
			System.out.println("505 HTTP Version Not Supported");
			break;
		default:
			System.out.println("Connection problem");
			break;
		}
	}
	public static void main(String[] args) {
			new Client();
	}
}

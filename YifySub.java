/* 	This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>. */
	
// JVPS EXEC _NO

import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;

import java.io.*;
import java.util.zip.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.awt.Desktop;

class YifySub {
	final static c_reg sub_file_reg = new c_reg("(.*)\\.\\w+$");
	final static c_reg movie_name_reg = new c_reg("(?:.*\\\\)?(.*)\\.\\w+$");
	
	static void show_usage_text() {
		System.out.println("Usage: java [-cp <classpath>] YifySub -m <path> [-l <language>] [--debug] [--batch]\n"
							+ "Options:\n"
							+ "\t-m <path>\tThe path to the video file to download subtitles for\n"
							+ "\t\t\tDoesn't actually need to point to a valid file,\n"
							+ "\t\t\tsince only its name and directory will be used\n"
							+ "\t\t\tThe subtitles will be downloaded in the same directory\n"
							+ "\t-l <language>	Language of the subtitles. Defaults to English\n"
							+ "\t--debug\t\tDisplays some debugging information\n"
							+ "\t--no-output\tSupresses all output. Does not apply to --debug output\n"
							);
		System.exit(1);
	}
	
	public static void main(String[] args) throws Exception {
		String language = "English";
		String full_path = null;
		
		boolean debug_mode = false;
		boolean batch_mode = false;
		
		for(int i = 0; i < args.length; i++) {
			if(args[i].equals("-l")) {
				if(i+1 >= args.length) show_usage_text();
				language = args[i+1];
				i++;
			} else if(args[i].equals("-m")) {
				if(i+1 >= args.length) show_usage_text();
				full_path = args[i+1];
				i++;
			} else if(args[i].equals("--debug")) {
				debug_mode = true;
			} else if(args[i].equals("--batch")) {
				batch_mode = true;
			} else show_usage_text();
		}
		
		if(full_path == null) show_usage_text();
		
		// Make all slashes backward ones
		full_path = full_path.replace("/", "\\");
		
		// Capitalize the first letter of the language
		language = language.substring(0, 1).toUpperCase() + language.substring(1);
		
		String movie_name = movie_name_reg.c_groups(full_path);
		String sub_path = sub_file_reg.c_groups(full_path) + ".srt";
		
		if(movie_name == null) {
			movie_name = full_path;
			sub_path = movie_name + ".srt";
		}
		
		// Debugging
		if(debug_mode) {
			System.out.println("----------");
			System.out.println("Arguments: " + String.join(" ", args));
			System.out.println("Movie Name: " + movie_name);
			System.out.println("Language: " + language);
			System.out.println("Subtitle file: " + sub_path);
			System.out.println("----------");
		}
		
		System.out.println("File name: " + movie_name);
		
		// If the name can be refined, do it
		if(refine_name(movie_name) != null) movie_name = refine_name(movie_name);
		
		System.out.println("Searching for '" + movie_name + "'");
		
		// Search for subs
		ArrayList<String> search_results = get_search_results(movie_name);
		
		// If not found, give the user the option to choose a new name
		// Quits when an empty string or EOF is received
		while(search_results.isEmpty()) {
			if(!movie_name.equals("\2")) System.out.println("Could not find movie: " + movie_name);
			System.out.print("Provide another name: ");
			
			// Create scanner and check for EOF
			Scanner s = new Scanner(System.in);
			if(!s.hasNextLine()) System.exit(1);
			
			// Get movie name
			movie_name = s.nextLine();
			
			// Add an empty line
			System.out.println("");
				
			// Exit if an empty string was input
			if(movie_name.equals("")) System.exit(1);
			
			if(movie_name.equals("\2")) {
				Desktop.getDesktop().browse(new URI("http://www.yifysubtitles.com/search"));
				continue;
			}
			
			// Search for the movie
			search_results = get_search_results(movie_name);
		}
		
		String movie_link;
		
		/* If there are multiple results that fit the 
			criteria, prompt the user to choose one amongst them */
		if(search_results.size() > 1) {
			System.out.println("Mutliple results match the movie name: ");
			
			// Print the results
			for(int i = 0; i < search_results.size(); i++) {
				System.out.println(i + ". " + get_result_movie_name(search_results.get(i)) + ", " + get_result_year(search_results.get(i)));
			}
			
			System.out.print("Download: ");
			
			// If no number was received exit
			Scanner s = new Scanner(System.in);
			if(!s.hasNext() || !s.hasNextInt()) System.exit(1);
			
			int n = s.nextInt();
			if(n < 0 || n > search_results.size()) System.exit(1);
			
			// Get the movie link of the chosen result
			movie_link = get_result_address(search_results.get(n)); 
		} else {
			System.out.println("Movie Found");
			movie_link = get_result_address(search_results.get(0));
		}
		
		// Attempt to get the link to subtitles of the specified language
		String sub_link = get_sub_link(movie_link, language);
		
		// If null was received, no sub of the specific language was found
		if(sub_link == null) {
			System.out.println(language + " subtitles not found");
			System.in.read();
			System.exit(1);
		} else {
			System.out.println(language + " subtitles found");
		}
		
		// Debugging
		if(debug_mode) {
			System.out.println("----------");
			System.out.println("Movie Link: " + sub_link);
			System.out.println("Subtitle Link: " + sub_link);
			System.out.println("----------");
		}
		
		download_sub(sub_link, sub_path);
		System.out.println("Subtitles successfully downloaded");
		
		Thread.sleep(1000);
	}
	
	// Search for a movie and return an arraylist containing the results
	static ArrayList<String> get_search_results(String movie_name) throws Exception {
		// The address to the search page for the specific movie name
		String address = "http://www.yifysubtitles.com/search?q=" + escape(movie_name);
		address = address.replace(' ', '+');
		
		ArrayList<String> search_results = new ArrayList();
		String current_result = null;
		
		// Read from the page
		BufferedReader reader = new BufferedReader(new InputStreamReader(new URL(address).openStream(), "UTF-8"));
		
		/* div_count is used to know when all of 
			the information of a result has been read */
		/* When in_result is true it means that we are currently parsing 
			part of a result and so we add it to the current_result string */
		int div_count = 0;
		boolean in_result = false;
		
		// Read the input line by line
		for(String line = reader.readLine(); line != null; line = reader.readLine()) {
			if(div_count < 0) throw new Exception("Div count error");
			
			// The below string denotes the beginning of a result
			if(line.contains("<div class=\"media-body\">")) {
				in_result = true;
				div_count = 0;
			}
			
			// If not currently in a result we need not act
			if(in_result) {
				current_result += line;
				
				// Adjsut the div counter
				if(line.contains("<div")) div_count++;
				if(line.contains("</div")) div_count--;
				
				/* If div_count is zero, the whole result has been 
					read; we add it to the arraylist and move on */
				if(div_count == 0) {
					search_results.add(current_result);
					
					current_result = null;
					in_result = false;
				}
			}
		}
		
		reader.close();
		
		// Return the arraylist
		return search_results;
	}
	
	// Return the sub link of the first result that's in return get_sub_address(result);
	// The first result per language will be the one with highest rating amongst them
	// Won't download subs with negative rating
	static String get_sub_link(String address, String lang) throws Exception {
		BufferedReader reader = new BufferedReader(new InputStreamReader(new URL(address).openStream(), "UTF-8"));
		
		String result_line;
		
		// Search for the line containing the sub results and stop when it's found
		// At the time of writing, all sub results are on a single line
		for(result_line = reader.readLine(); result_line != null; result_line = reader.readLine()) {
			if(result_line.contains("<tr data-id=\"")) break;
		}
		
		reader.close();
		
		// Make sure the results where found
		if(result_line == null) return null;
		
		// Iterate through the results_line
		while(result_line.contains("<tr data-id=\"")) {
			// Remove the result indicator at the front
			result_line = result_line.substring(result_line.indexOf("<tr data-id=\"") + "<tr data-id=\"".length());
			result_line = result_line.substring(result_line.indexOf(">") + 1);
			
			// Extract the result that's currently at the front of the results line
			String result = result_line.substring(0, result_line.indexOf("</tr>") + "</tr>".length());
			
			// If the language is return get_sub_address(result); and the rating is not negative return the address to the result's sub
			/* If a negative rating has been reached, we know there will be no sub with a positive 
				rating since, subs of a specific language are sorted according to their rankings */
			/* Because all of the results are sorted according to their language, alphabetically, when a result 
				alphabetically lower than $(lang) is reached we know we won't find the subs we are looking for */
			if(get_sub_lang(result).equals(lang)) {
				if(get_sub_rating(result) < 0) return null;
				return get_sub_address(result);
			} else if(get_sub_lang(result).compareTo(lang) > 0) {
				return null;
			}
			
			// Remove the remainings of the rejected result so that the process may continue
			result_line = result_line.substring(result_line.indexOf("</tr>") + "</tr>".length());
		}
		
		return null;
	}
	
	// Download the subtitle from sublink and save it in path with name $(movie_name).srt
	static void download_sub(String sub_link, String sub_path) throws Exception {
		String zip_name = System.getProperty("java.io.tmpdir") + sub_link.substring(sub_link.lastIndexOf('/'));
		
		/* Download the file from sub_link; will be in a zip file */
		
		InputStream input = new BufferedInputStream(new URL(sub_link).openStream());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		
		// Read 1024 at a time
		byte[] buf = new byte[1024]; int n = 0;
		while ((n = input.read(buf)) != -1) {
			output.write(buf, 0, n);
		}
		
		// Close the streams
		input.close();
		output.close();
		
		byte[] response = output.toByteArray();

		// Write to the file
		FileOutputStream file = new FileOutputStream(zip_name);
		file.write(response);
		file.close();
		
		// Read the first(should also be the only one) file in the .zip
		ZipFile zipFile = new ZipFile(zip_name);
		ZipEntry zipEntry = zipFile.entries().nextElement();
		InputStream zip_stream = zipFile.getInputStream(zipEntry);
		
		// The extension of the sub in the zip file
		String sub_ext = zipEntry.getName();
		sub_ext = sub_ext.substring(sub_ext.lastIndexOf('.'));
		
		// Open the srt file to write the sub in
		FileOutputStream srt_stream = new FileOutputStream(sub_path);
		
		// Write to the .srt
		int c;
		while((c = zip_stream.read()) != -1) srt_stream.write(c);
		
		// Close the file handles
		zipFile.close();
		zip_stream.close();
		srt_stream.close();
		
		// Delete the zip file after the sub is extracted
		new File(zip_name).delete();
	}
	
	/** Result Tools */
	
	// Return the name of the specific result
	static String get_result_movie_name(String result) {
		String pf = "<h3 class=\"media-heading\" itemprop=\"name\">";
		String sf = "</h3>";
		
		result = result.substring(result.indexOf(pf) + pf.length());
		return result.substring(0, result.indexOf(sf)).replace("&amp;", "&");
	}
	
	// Return the full address of the result specific result
	static String get_result_address(String result) {
		String pf = "<a href=\"";
		String sf = "\">";
		
		result = result.substring(result.indexOf(pf) + pf.length());
		return "http://www.yifysubtitles.com" + result.substring(0, result.indexOf(sf));
	}
	
	// Return year of a specific result
	static int get_result_year(String result) {
		String pf = "<span class=\"movinfo-section\">";
		String sf = "<small>year</small>";
		
		result = result.substring(0, result.indexOf(sf));
		return Integer.parseInt(result.substring(result.lastIndexOf(pf) + pf.length()));
	}
	
	// Used to escape the movie name in urls
	static String escape(String source) {
		return source.replace("&", "%26");
	}
	
	/* Attempt to refine a movie name so as 
		to make searching for it possible */
	static String refine_name(String source) {
		String pattern = "(.+?)(?=\\.\\d{4}\\.)";
		
		Matcher m = Pattern.compile(pattern).matcher(source);
		
		if(m.find()) return m.group().replace('.', ' ');
		else return null;
	}
	
	/** Sub Tools */
	
	// Get the sub language of a result
	static String get_sub_lang(String result) {
		String pf = "<span class=\"sub-lang\">";
		String sf = "</span>";
		
		result = result.substring(result.indexOf(pf) + pf.length());
		return result.substring(0, result.indexOf(sf));
	}
	
	// Return the rating of a sub result
	static int get_sub_rating(String result) {
		String pf = "<td class=\"rating-cell\">";
		String sf = "</span>";
		
		result = result.substring(result.indexOf(pf) + pf.length());
		result = result.substring(0, result.indexOf(sf));
		result = result.substring(result.lastIndexOf('>') + 1);
		return Integer.parseInt(result);
	}
	
	// Get the link to the sub of a result
	static String get_sub_address(String result) {
		String pf = "<a href=\"";
		String sf = "\">";
		
		result = result.substring(result.indexOf(pf) + pf.length());
		result = result.replace("/subtitles/", "/subtitle/");
		return "http://www.yifysubtitles.com" + result.substring(0, result.indexOf(sf)) + ".zip";
	}
}

class c_reg {
	private final Pattern pattern;
	
	c_reg(String regex) {
		pattern = Pattern.compile(regex);
	}
	
	String c_groups(String input) {
		Matcher m = this.pattern.matcher(input);
		
		boolean r = m.find();
		if(!r) return null;
		
		StringBuilder builder = new StringBuilder();
		for(int i = 1; i <= m.groupCount(); i++) builder.append(m.group(i));
		return builder.toString();
	}
}

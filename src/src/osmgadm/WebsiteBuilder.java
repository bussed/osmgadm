/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package osmgadm;

import java.io.*;
import java.nio.charset.Charset;
import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.cli.*;

/**
 *
 * @author dieterbusse
 */
public class WebsiteBuilder {
    
    Connection conn;
    PreparedStatement stmtc;
    PreparedStatement stmt;
    public static String db_name = "osm";
    public static String db_host = "freebsd";
    public static String db_user = "osm";
    public static String db_pw = "osm";
    public static String template_path = "/Users/dieterbusse/mapping/coding/BoundaryTreePrinter/template/";
    public static String website_path = "/Users/dieterbusse/mapping/website/";
    public static String shape_path = "/Volumes/fast/data/world/shapes/";

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        Options options = new Options();
        options.addOption(OptionBuilder.withLongOpt("dbname").withDescription("dbname (default=osm)").hasArg().withArgName("DB NAME").create());
        options.addOption(OptionBuilder.withLongOpt("dbhost").withDescription("dbhost (default=localhost)").hasArg().withArgName("DB HOST").create());
        options.addOption(OptionBuilder.withLongOpt("dbuser").withDescription("dbuser (default=osm)").hasArg().withArgName("DB USER").create());
        options.addOption(OptionBuilder.withLongOpt("dbpw").withDescription("dbpw (default=osm)").hasArg().withArgName("DB PASSWORD").create());
        options.addOption(OptionBuilder.withLongOpt("websitepath").withDescription("path where tree output will be written. path must end with \"/\"").hasArg().withArgName("websitepath").create());
        options.addOption(OptionBuilder.withLongOpt("shapepath").withDescription("path where oge2ogr commands will be written. path must end with \"/\"").hasArg().withArgName("shapepath").create());
        options.addOption(OptionBuilder.withLongOpt("templatepath").withDescription("path where html templates are. path must end with \"/\"").hasArg().withArgName("templatepath").create());
        options.addOption(OptionBuilder.withDescription("print this message").create("help"));


        // create the parser
        CommandLineParser parser = new PosixParser();
        try {
            // parse the command line arguments
            CommandLine line = parser.parse(options, args);
            
            if (line.hasOption("help")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("BoundaryTreePrinter", options);
                System.exit(0);
            }
            if (line.hasOption("dbname")) {
                db_name = line.getOptionValue("dbname");
            }
            if (line.hasOption("dbhost")) {
                db_host = line.getOptionValue("dbhost");
            }
            if (line.hasOption("dbuser")) {
                db_user = line.getOptionValue("dbuser");
            }
            if (line.hasOption("dbpw")) {
                db_pw = line.getOptionValue("dbpw");
            }
            if (line.hasOption("websitepath")) {
                website_path = line.getOptionValue("websitepath");
            } else {
                Logger.getLogger(WebsiteBuilder.class.getName()).log(Level.SEVERE, "no websitepath specified!");
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("WebsiteBuilder", options);
                System.exit(0);
            }

            if (line.hasOption("shapepath")) {
                shape_path = line.getOptionValue("shapepath");
            } else {
                Logger.getLogger(WebsiteBuilder.class.getName()).log(Level.SEVERE, "no shapepath specified!");
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("WebsiteBuilder", options);
                System.exit(0);
            }
            if (line.hasOption("templatepath")) {
                template_path = line.getOptionValue("templatepath");
            } else {
                Logger.getLogger(WebsiteBuilder.class.getName()).log(Level.SEVERE, "no templatepath specified!");
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("WebsiteBuilder", options);
                System.exit(0);
            }
            
            Logger.getLogger(WebsiteBuilder.class.getName()).log(Level.INFO, "Using websitepath " + website_path + " shapepath " + shape_path + " dbname " + db_name + " dbhost " + db_host + " dbuser " + db_user + " dbpw " + db_pw);
            Logger.getLogger(WebsiteBuilder.class.getName()).log(Level.INFO, "use --help to list all options");
        } catch (ParseException exp) {
            System.err.println(exp.getMessage());
        }
        
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(WebsiteBuilder.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(0);
        }
        
        WebsiteBuilder sc = new WebsiteBuilder();
        sc.start();
        sc.generateReports();
    }
    
    public void start() {
        
        Logger.getLogger(WebsiteBuilder.class.getName()).log(Level.INFO, "Generating Tree Views");
        
        try {
            conn = DriverManager.getConnection("jdbc:postgresql://" + db_host + ":5432/" + db_name, db_user, db_pw);
            stmtc = conn.prepareStatement("select count(*) from tree_world where parent_id = ?");
            
        } catch (SQLException ex) {
            Logger.getLogger(WebsiteBuilder.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        try {
            
            StringBuilder template_top = readFile(template_path + "template_top.html");
            StringBuilder template_nav = readFile(template_path + "template_nav_download.html");
            StringBuilder template_footer = readFile(template_path + "template_footer.html");
            
            StringBuilder template_main = new StringBuilder();
            template_main.append("<div id=\"main\">\n");
            template_main.append("	<div id=\"main_inner\" class=\"fluid\">\n");
            template_main.append("		<div id=\"primaryContent_columnless\">\n");
            template_main.append("			<div id=\"columnA_columnless\">\n");
            template_main.append("				<h3>Country Index</h3>\n");
            template_main.append("				<p>\n");
            template_main.append("					Select Country\n");
            template_main.append("				</p>\n");
            template_main.append("				<br class=\"clear\" />\n");
            template_main.append("        <script type=\"text/javascript\" charset=\"utf-8\">\n");
            template_main.append("            $(document).ready(function() {\n");
            template_main.append("                $('#mytable').dataTable({\n");
            template_main.append("                    \"bPaginate\": false,\n");
            template_main.append("                    \"bSort\": false\n");
            template_main.append("                });\n");
            template_main.append("            } );\n");
            template_main.append("        </script>\n");
            
            template_main.append("<table cellpadding=\"0\" cellspacing=\"0\" border=\"1\" class=\"display\" width=\"100%\" id=\"mytable\">\n");
            template_main.append("<thead><tr><th>Relation ID</th><th>Country Name</th></tr></thead>\n");
            template_main.append("<tbody>\n");
            
            
            
            Statement rstmt = conn.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_READ_ONLY);
            ResultSet srs = rstmt.executeQuery("select level, relation_id, name from tree_world where level=2 and poly is not null;");
            
            while (srs.next()) {
                int al = srs.getInt(1);
                int relid = srs.getInt(2);
                String name = srs.getString(3);
                getCountry(al, relid, name);
                
                
                template_main.append("<tr><td>" + relid + "</td><td><a href=\"" + relid + ".html\">" + name + "</a></td></tr>\n");
            }
            
            
            template_main.append("</tbody>\n");
            template_main.append("</table>\n");
            template_main.append("			</div>\n");
            template_main.append("		</div>\n");
            template_main.append("		<br class=\"clear\" />\n");
            template_main.append("	</div>\n");
            template_main.append("</div>\n");
            
            Writer fw_index = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(website_path + "downloads.html"), Charset.forName("UTF8")));
            fw_index.write(template_top.toString());
            fw_index.write(template_nav.toString());
            fw_index.write(template_main.toString());
            fw_index.write(template_footer.toString());
            fw_index.close();
            
        } catch (SQLException ex) {
            Logger.getLogger(WebsiteBuilder.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(WebsiteBuilder.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }
    
    public void getCountry(int al, int relid, String name) {
        
        StringBuilder template_top = readFile(template_path + "template_top.html");
        StringBuilder template_nav = readFile(template_path + "template_nav_download.html");
        StringBuilder template_footer = readFile(template_path + "template_footer.html");
        StringBuilder template_main = new StringBuilder();
        template_main.append("<div id=\"main\">\n");
        template_main.append("	<div id=\"main_inner\" class=\"fluid\">\n");
        template_main.append("		<div id=\"primaryContent_columnless\">\n");
        template_main.append("			<div id=\"columnA_columnless\">\n");
        template_main.append("				<h3>" + name + "</h3>\n");
        template_main.append("				<p>\n");
        template_main.append("				<a href=\"shapes/" + relid + ".zip\">Download Shapefile</a> Projection: WGS84\n");
        template_main.append("				</p>\n");
        template_main.append("				<br class=\"clear\" />\n");
        template_main.append("        <script type=\"text/javascript\" charset=\"utf-8\">\n");
        template_main.append("            $(document).ready(function() {\n");
        template_main.append("                $('#mytable').dataTable({\n");
        template_main.append("                    \"bPaginate\": false,\n");
        template_main.append("                    \"bSort\": false\n");
        template_main.append("                });\n");
        template_main.append("            } );\n");
        template_main.append("        </script>\n");
        template_main.append("<table cellpadding=\"0\" cellspacing=\"0\" border=\"1\" class=\"display\" width=\"100%\" id=\"mytable\">\n");
        template_main.append("<thead><tr><th>2</th><th>3</th><th>4</th><th>5</th><th>6</th><th>7</th><th>8</th><th>9</th><th>10</th><th>11</th><th>12</th><th>Relation ID</th><th>Member count</th></tr></thead>\n");
        template_main.append("<tbody>\n");
        
        
        template_main.append(getBorderData(al, relid, name));
        
        template_main.append("</tbody>\n");
        template_main.append("</table>\n");
        template_main.append("			</div>\n");
        template_main.append("		</div>\n");
        template_main.append("		<br class=\"clear\" />\n");
        template_main.append("	</div>\n");
        template_main.append("</div>\n");
        
        try {
            Writer fw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(website_path + relid + ".html"), Charset.forName("UTF8")));
            fw.write(template_top.toString());
            fw.write(template_nav.toString());
            fw.write(template_main.toString());
            fw.write(template_footer.toString());
            fw.close();
        } catch (IOException ex) {
            Logger.getLogger(WebsiteBuilder.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }
    
    public String getBorderData(int al, int relid, String name) {

        String prefix = "<tr>";
        String suffix = "";
        for (int i = (al - 2); i > 0; i--) {
            prefix += "<td></td>";
        }
        
        for (int i = (12 - al); i > 0; i--) {
            suffix += "<td></td>";
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        try {
            stmtc.setInt(1, relid);
            ResultSet srsc = stmtc.executeQuery();
            
            if (srsc.next()) {
                count = srsc.getInt(1);
            }
            
            stmt = conn.prepareStatement("select relation_id, name, level from tree_world where parent_id = ? order by 2");
            stmt.setInt(1, relid);
            ResultSet srs = stmt.executeQuery();
            
            String line = prefix + "<td>" + name + "</td>" + suffix;
            line += "<td><a href=\"http://www.openstreetmap.org/browse/relation/" + relid + "\">" + relid + "</a>  <a href=\"http://ra.osmsurround.org/analyzeRelation?relationId=" + relid + "\">RA</a></td><td>" + count + "</td></tr>\n";
            sb.append(line);
            //          System.out.println(line);
            while (srs.next()) {
                int child_id = srs.getInt(1);
                String child_name = srs.getString(2);
                int child_al = srs.getInt(3);
                sb.append(getBorderData(child_al, child_id, child_name));
            }
            
        } catch (SQLException ex) {
            Logger.getLogger(WebsiteBuilder.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return sb.toString();
        
    }
    
    public void generateReports() {
        
        Logger.getLogger(WebsiteBuilder.class.getName()).log(Level.INFO, "Generating Reports");
        StringBuilder template_top = readFile(template_path + "template_top.html");
        StringBuilder template_nav = readFile(template_path + "template_nav_reports.html");
        StringBuilder template_footer = readFile(template_path + "template_footer.html");
        
        StringBuilder template_main = new StringBuilder();
        template_main.append("<div id=\"main\">\n");
        template_main.append("	<div id=\"main_inner\" class=\"fluid\">\n");
        template_main.append("		<div id=\"primaryContent_columnless\">\n");
        template_main.append("			<div id=\"columnA_columnless\">\n");
        template_main.append("				<h3>Reports</h3>\n");
        template_main.append("				<p>\n");
        template_main.append("					Select a Report\n");
        template_main.append("				</p>\n");
        template_main.append("				<br class=\"clear\" />\n");
        
        
        String name = "ISO3166-1";
        String desc = "ISO 3166-1 Country Check";
        String[] columns = new String[]{"ISO Name", "ISO Code", "OSM Name", "Relation ID"};
        String query = "select cname as iso_cname, alpha2 as code, tags->'name' as osmname, id as relation_id from iso3166_1 left outer join relations on (upper(alpha2)=upper(tags->'ISO3166-1'));";
        writeReport(website_path, name, desc, columns, query, 4);
        
        template_main.append("				<h3><a href=\"" + name + ".html\">" + name + "</a></h3>\n");
        template_main.append("				<p>\n");
        template_main.append("					" + desc + "\n");
        template_main.append("				</p>\n");
        template_main.append("				<br class=\"clear\" />\n");
        
        name = "ISO3166-2";
        desc = "ISO 3166-2 Sub Division Check";
        columns = new String[]{"ISO Country Name", "ISO Sub Div Name", "ISO Sub Div Code", "OSM Name", "Relation ID", "Admin Level"};
        query = "select country_name as iso_cname, subdiv_name, subdiv_code, tags->'name' as osm_name, id as relation_id, tags->'admin_level' as admin_level from iso3166_2 left outer join relations on (upper(subdiv_code)=upper(tags->'iso3166-2')) order by 3;";
        
        writeReport(website_path, name, desc, columns, query, 5);
        
        template_main.append("				<h3><a href=\"" + name + ".html\">" + name + "</a></h3>\n");
        template_main.append("				<p>\n");
        template_main.append("					" + desc + "\n");
        template_main.append("				</p>\n");
        template_main.append("				<br class=\"clear\" />\n");
        
        name = "Report-1";
        desc = "1: Invalid Geometry (no closed ring could be found)";
        columns = new String[]{"Relation ID", "Name", "Admin Level"};
        query = "select relation_id, name, level from tree_world where poly is null order by 3;";
        writeReport(website_path, name, desc, columns, query, 1);
        
        template_main.append("				<h3><a href=\"" + name + ".html\">" + name + "</a></h3>\n");
        template_main.append("				<p>\n");
        template_main.append("					" + desc + "\n");
        template_main.append("				</p>\n");
        template_main.append("				<br class=\"clear\" />\n");
        
        
        name = "Report-2";
        desc = "2: Missing Wikipedia link";
        columns = new String[]{"Relation ID", "Name", "Admin Level"};
        query = "select id, tags->'name', tags->'admin_level' from relations where tags->'boundary'='administrative' AND not tags->'type'='multilinestring' and tags::text not like '%wikipedia%' order by 3;";
        writeReport(website_path, name, desc, columns, query, 1);
        
        template_main.append("				<h3><a href=\"" + name + ".html\">" + name + "</a></h3>\n");
        template_main.append("				<p>\n");
        template_main.append("					" + desc + "\n");
        template_main.append("				</p>\n");
        template_main.append("				<br class=\"clear\" />\n");
        
        name = "Report-3";
        desc = "3: Relation has members with strange roles (NOT outer, innter, admin_centre, admin_center, capital, label, subarea, center)";
        columns = new String[]{"Relation ID", "Name", "Admin Level"};
        query = "select distinct(relation_members.relation_id), tree_world.name, tree_world.level from relation_members, tree_world where relation_members.relation_id=tree_world.relation_id and member_role not in ('outer', 'innter', 'admin_centre', 'admin_center', 'capital', 'label', 'subarea', 'center') order by 3;";
        writeReport(website_path, name, desc, columns, query, 1);
        
        template_main.append("				<h3><a href=\"" + name + ".html\">" + name + "</a></h3>\n");
        template_main.append("				<p>\n");
        template_main.append("					" + desc + "\n");
        template_main.append("				</p>\n");
        template_main.append("				<br class=\"clear\" />\n");
        
        name = "Report-4";
        desc = "4: Relation has members with strange admin_levels (NOT 1..12)";
        columns = new String[]{"Relation ID", "Name", "Admin Level"};
        query = "select id, tags->'name', tags->'admin_level' from relations where tags->'admin_level' not in ('1','2','3','4','5','6','7','8','9','10','11','12');";
        writeReport(website_path, name, desc, columns, query, 1);
        
        template_main.append("				<h3><a href=\"" + name + ".html\">" + name + "</a></h3>\n");
        template_main.append("				<p>\n");
        template_main.append("					" + desc + "\n");
        template_main.append("				</p>\n");
        template_main.append("				<br class=\"clear\" />\n");
        template_main.append("			</div>\n");
        template_main.append("		</div>\n");
        template_main.append("		<br class=\"clear\" />\n");
        template_main.append("	</div>\n");
        template_main.append("</div>\n");
        
        try {
            Writer fw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(website_path + "reports.html"), Charset.forName("UTF8")));
            fw.write(template_top.toString());
            fw.write(template_nav.toString());
            fw.write(template_main.toString());
            fw.write(template_footer.toString());
            fw.close();
        } catch (IOException ex) {
            Logger.getLogger(WebsiteBuilder.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        
    }
    
    public void writeReport(String path, String name, String desc, String[] columns, String query, int relcol) {
        
        try {
            conn = DriverManager.getConnection("jdbc:postgresql://" + db_host + ":5432/" + db_name, db_user, db_pw);
            
        } catch (SQLException ex) {
            Logger.getLogger(WebsiteBuilder.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        try {
            StringBuilder template_top = readFile(template_path + "template_top.html");
            StringBuilder template_nav = readFile(template_path + "template_nav_reports.html");
            StringBuilder template_footer = readFile(template_path + "template_footer.html");
            
            StringBuilder template_main = new StringBuilder();
            template_main.append("<div id=\"main\">\n");
            template_main.append("	<div id=\"main_inner\" class=\"fluid\">\n");
            template_main.append("		<div id=\"primaryContent_columnless\">\n");
            template_main.append("			<div id=\"columnA_columnless\">\n");
            template_main.append("				<h3>" + name + "</h3>\n");
            template_main.append("				<p>\n");
            template_main.append("					" + desc + "\n");
            template_main.append("				</p>\n");
            template_main.append("				<br class=\"clear\" />\n");
            template_main.append("        <script type=\"text/javascript\" charset=\"utf-8\">\n");
            template_main.append("            $(document).ready(function() {\n");
            template_main.append("                $('#mytable').dataTable({\n");
            template_main.append("                    \"bPaginate\": false,\n");
            template_main.append("                    \"bSort\": false\n");
            template_main.append("                });\n");
            template_main.append("            } );\n");
            template_main.append("        </script>\n");
            
            
            
            template_main.append("<table cellpadding=\"0\" cellspacing=\"0\" border=\"1\" class=\"display\" width=\"100%\" id=\"mytable\">\n");
            template_main.append("<thead><tr>");
            
            for (String c : columns) {
                template_main.append("<th>" + c + "</th>");
            }
            
            template_main.append("</tr></thead>\n");
            template_main.append("<tbody>\n");
            
            
            Statement rstmt = conn.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_READ_ONLY);
            ResultSet srs = rstmt.executeQuery(query);
            
            while (srs.next()) {
                template_main.append("</tr>\n");
                
                for (int i = 1; i < columns.length + 1; i++) {
                    String v = srs.getString(i);
                    if (v == null) {
                        template_main.append("<td BGCOLOR=\"#FF0000\">NOT FOUND IN OSM</td>");
                    } else {
                        if (i == relcol) {
                            v = "<a href=\"http://www.openstreetmap.org/browse/relation/" + v + "\">" + v + "  <a href=\"http://ra.osmsurround.org/analyzeRelation?relationId=" + v + "\">RA</a></a>";
                        }
                        template_main.append("<td>" + v + "</td>");
                    }
                }
                
                template_main.append("</tr>\n");
            }
            
            template_main.append("</tbody>\n");
            template_main.append("</table>\n");
            template_main.append("			</div>\n");
            template_main.append("		</div>\n");
            template_main.append("		<br class=\"clear\" />\n");
            template_main.append("	</div>\n");
            template_main.append("</div>\n");
            
            Writer fw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path + name + ".html"), Charset.forName("UTF8")));
            fw.write(template_top.toString());
            fw.write(template_nav.toString());
            fw.write(template_main.toString());
            fw.write(template_footer.toString());
            fw.close();
            
        } catch (SQLException ex) {
            Logger.getLogger(WebsiteBuilder.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(WebsiteBuilder.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }
    
    public StringBuilder readFile(String fn) {
        try {
            BufferedReader in = new BufferedReader(new FileReader(fn));
            String s = in.readLine();
            StringBuilder sb = new StringBuilder();
            while (s != null) {
                sb.append(s + "\n");
                s = in.readLine();
            }
            return sb;
            
        } catch (IOException ex) {
            Logger.getLogger(WebsiteBuilder.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
        
    }
}

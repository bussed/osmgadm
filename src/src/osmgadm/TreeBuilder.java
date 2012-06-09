/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package osmgadm;

import java.sql.*;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.cli.*;

/**
 *
 * @author dbusse
 */
public class TreeBuilder {

    public static String db_name = "osm";
    public static String db_host = "freebsd";
    public static String db_user = "osm";
    public static String db_pw = "osm";
    public static String adminlevel = "2";
    Connection conn;
    public static int NUM_THREADS = 4;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        Options options = new Options();
        options.addOption(OptionBuilder.withLongOpt("adminlevel").withDescription("adminlevel (default=2)").hasArg().withArgName("adminlevel").create());
        options.addOption(OptionBuilder.withLongOpt("dbname").withDescription("dbname (default=osm)").hasArg().withArgName("DB NAME").create());
        options.addOption(OptionBuilder.withLongOpt("dbhost").withDescription("dbhost (default=localhost)").hasArg().withArgName("DB HOST").create());
        options.addOption(OptionBuilder.withLongOpt("dbuser").withDescription("dbuser (default=osm)").hasArg().withArgName("DB USER").create());
        options.addOption(OptionBuilder.withLongOpt("dbpw").withDescription("dbpw (default=osm)").hasArg().withArgName("DB PASSWORD").create());
        options.addOption(OptionBuilder.withLongOpt("numthreads").withDescription("number of parallel worker threads (default=4)").hasArg().withArgName("NUM Threads").create());
        options.addOption(OptionBuilder.withDescription("print this message").create("help"));


        // create the parser
        CommandLineParser parser = new PosixParser();
        try {
            // parse the command line arguments
            CommandLine line = parser.parse(options, args);

            if (line.hasOption("help")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("TreeBuilder", options);
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
            if (line.hasOption("adminlevel")) {
                adminlevel = line.getOptionValue("adminlevel");
            }
            if (line.hasOption("numthreads")) {
                NUM_THREADS = Integer.parseInt(line.getOptionValue("numthreads"));
            }
            Logger.getLogger(TreeBuilder.class.getName()).log(Level.INFO, "Using adminlevel " + adminlevel + " dbname " + db_name + " dbhost " + db_host + " dbuser " + db_user + " dbpw " + db_pw+ "  num threads "+NUM_THREADS);
            Logger.getLogger(TreeBuilder.class.getName()).log(Level.INFO, "use --help to list all options");
        } catch (ParseException exp) {
            System.err.println(exp.getMessage());
        }

        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(TreeBuilder.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(0);
        }


        TreeBuilder wc = new TreeBuilder();
        wc.start();

    }

    public void start() {

        try {
            conn = DriverManager.getConnection("jdbc:postgresql://" + db_host + ":5432/" + db_name, db_user, db_pw);

        } catch (SQLException ex) {
            Logger.getLogger(TreeBuilder.class.getName()).log(Level.SEVERE, null, ex);
        }

        try {
            Statement rstmt = conn.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_READ_ONLY);
            ResultSet srs = rstmt.executeQuery("select id from relations where  tags->'boundary'='administrative' AND tags->'admin_level'='" + adminlevel + "' AND not tags->'type'='multilinestring';");

            ArrayList<Integer> al_ids = new ArrayList<Integer>();
            while (srs.next()) {
                al_ids.add(srs.getInt(1));
            }

            Logger.getLogger(TreeBuilder.class.getName()).log(Level.INFO, "Found " + al_ids.size() + " Relations to process");


            //Creating Threads
            ArrayList<UpdatePolyThread> al_pts = new ArrayList<UpdatePolyThread>();
            for (int i = 0; i < NUM_THREADS; i++) {
                al_pts.add(new UpdatePolyThread(i + ""));
            }

            //Filling Threads with Data
            for (int i = 0; i < al_ids.size(); i++) {
                al_pts.get(i % NUM_THREADS).al_ids.add(al_ids.get(i));
            }

            //Start Threads!
            for (int i = 0; i < NUM_THREADS; i++) {
                al_pts.get(i).start();
            }

            srs.close();
            rstmt.close();
        } catch (SQLException ex) {
            Logger.getLogger(TreeBuilder.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    class UpdatePolyThread extends Thread {

        Connection conn;
        ArrayList<Integer> al_ids = new ArrayList<Integer>();

        public UpdatePolyThread(String str) {
            super(str);
            try {
                conn = DriverManager.getConnection("jdbc:postgresql://" + db_host + ":5432/" + db_name, db_user, db_pw);

            } catch (SQLException ex) {
                Logger.getLogger(UpdatePolyThread.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        @Override
        public void run() {
            Logger.getLogger(UpdatePolyThread.class.getName()).log(Level.INFO, "Thread " + getName() + " started. Seeded with " + al_ids.size() + " Relations to process");

            try {
                CallableStatement cs_updatepoly = conn.prepareCall("{call updatepoly(?)}");

                for (Integer id : al_ids) {
                    cs_updatepoly.setInt(1, id);
                    ResultSet srs = cs_updatepoly.executeQuery();
                }

                cs_updatepoly.close();
            } catch (SQLException ex) {
                Logger.getLogger(UpdatePolyThread.class.getName()).log(Level.SEVERE, null, ex);
            }

        }
    }
}

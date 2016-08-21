import java.io.*;
import java.net.*;
import java.util.Random;
import java.util.zip.*;

import processing.core.*;
import processing.data.*;

final class Utils {

  static final float PI = (float) Math.PI;
  static final float HALF_PI = (float) (Math.PI / 2.0);
  static final float THIRD_PI = (float) (Math.PI / 3.0);
  static final float QUARTER_PI = (float) (Math.PI / 4.0);
  static final float TWO_PI = (float) (2.0 * Math.PI);

  static private final long millisOffset = System.currentTimeMillis();

  static String sketchPath;

  static public int millis() {
    return (int) (System.currentTimeMillis() - millisOffset);
  }

  public static Table loadTable(String filename, String options) {
    try {
      String optionStr = Table.extensionOptions(true, filename, options);
      String[] optionList = PApplet.trim(PApplet.split(optionStr, ','));

      Table dictionary = null;
      for (String opt : optionList) {
        if (opt.startsWith("dictionary=")) {
          dictionary = loadTable(opt.substring(opt.indexOf('=') + 1), "tsv");
          return dictionary.typedParse(createInput(filename), optionStr);
        }
      }
      InputStream input = createInput(filename);
      if (input == null) {
        System.err.println(filename + " does not exist or could not be read");
        return null;
      }
      return new Table(input, optionStr);

    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }


  //////////////////////////////////////////////////////////////
  // FILE INPUT
  public static InputStream createInput(String filename) {
    InputStream input = createInputRaw(filename);
    final String lower = filename.toLowerCase();
    if ((input != null) &&
        (lower.endsWith(".gz") || lower.endsWith(".svgz"))) {
      try {
        return new GZIPInputStream(input);
      } catch (IOException e) {
        e.printStackTrace();
        return null;
      }
    }
    return input;
  }

  public static InputStream createInputRaw(String filename) {
    if (filename == null) return null;

    if (sketchPath == null) {
      System.err.println("The sketch path is not set.");
      throw new RuntimeException("Files must be loaded inside setup() or after it has been called.");
    }

    if (filename.length() == 0) {
      // an error will be called by the parent function
      //System.err.println("The filename passed to openStream() was empty.");
      return null;
    }

    // First check whether this looks like a URL. This will prevent online
    // access logs from being spammed with GET /sketchfolder/http://blahblah
    if (filename.contains(":")) {  // at least smells like URL
      try {
        URL url = new URL(filename);
        URLConnection conn = url.openConnection();
        if (conn instanceof HttpURLConnection) {
          HttpURLConnection httpConn = (HttpURLConnection) conn;
          // Will not handle a protocol change (see below)
          httpConn.setInstanceFollowRedirects(true);
          int response = httpConn.getResponseCode();
          // Normally will not follow HTTPS redirects from HTTP due to security concerns
          // http://stackoverflow.com/questions/1884230/java-doesnt-follow-redirect-in-urlconnection/1884427
          if (response >= 300 && response < 400) {
            String newLocation = httpConn.getHeaderField("Location");
            return createInputRaw(newLocation);
          }
          return conn.getInputStream();
        } else if (conn instanceof JarURLConnection) {
          return url.openStream();
        }
      } catch (MalformedURLException mfue) {
        // not a url, that's fine

      } catch (FileNotFoundException fnfe) {
        // Added in 0119 b/c Java 1.5 throws FNFE when URL not available.
        // http://dev.processing.org/bugs/show_bug.cgi?id=403

      } catch (IOException e) {
        // changed for 0117, shouldn't be throwing exception
        e.printStackTrace();
        //System.err.println("Error downloading from URL " + filename);
        return null;
        //throw new RuntimeException("Error downloading from URL " + filename);
      }
    }

    InputStream stream = null;

    // Moved this earlier than the getResourceAsStream() checks, because
    // calling getResourceAsStream() on a directory lists its contents.
    // http://dev.processing.org/bugs/show_bug.cgi?id=716
    try {
      // First see if it's in a data folder. This may fail by throwing
      // a SecurityException. If so, this whole block will be skipped.
      File file = new File(dataPath(filename));
      if (!file.exists()) {
        // next see if it's just in the sketch folder
        file = sketchFile(filename);
      }

      if (file.isDirectory()) {
        return null;
      }
      if (file.exists()) {
        try {
          // handle case sensitivity check
          String filePath = file.getCanonicalPath();
          String filenameActual = new File(filePath).getName();
          // make sure there isn't a subfolder prepended to the name
          String filenameShort = new File(filename).getName();
          // if the actual filename is the same, but capitalized
          // differently, warn the user.
          //if (filenameActual.equalsIgnoreCase(filenameShort) &&
          //!filenameActual.equals(filenameShort)) {
          if (!filenameActual.equals(filenameShort)) {
            throw new RuntimeException("This file is named " +
                                       filenameActual + " not " +
                                       filename + ". Rename the file " +
                                       "or change your code.");
          }
        } catch (IOException e) { }
      }

      // if this file is ok, may as well just load it
      stream = new FileInputStream(file);
      if (stream != null) return stream;

      // have to break these out because a general Exception might
      // catch the RuntimeException being thrown above
    } catch (IOException ioe) {
    } catch (SecurityException se) { }

    // Using getClassLoader() prevents java from converting dots
    // to slashes or requiring a slash at the beginning.
    // (a slash as a prefix means that it'll load from the root of
    // the jar, rather than trying to dig into the package location)
    ClassLoader cl = Utils.class.getClassLoader();

    // by default, data files are exported to the root path of the jar.
    // (not the data folder) so check there first.
    stream = cl.getResourceAsStream("data/" + filename);
    if (stream != null) {
      String cn = stream.getClass().getName();
      // this is an irritation of sun's java plug-in, which will return
      // a non-null stream for an object that doesn't exist. like all good
      // things, this is probably introduced in java 1.5. awesome!
      // http://dev.processing.org/bugs/show_bug.cgi?id=359
      if (!cn.equals("sun.plugin.cache.EmptyInputStream")) {
        return stream;
      }
    }

    // When used with an online script, also need to check without the
    // data folder, in case it's not in a subfolder called 'data'.
    // http://dev.processing.org/bugs/show_bug.cgi?id=389
    stream = cl.getResourceAsStream(filename);
    if (stream != null) {
      String cn = stream.getClass().getName();
      if (!cn.equals("sun.plugin.cache.EmptyInputStream")) {
        return stream;
      }
    }

    try {
      // attempt to load from a local file, used when running as
      // an application, or as a signed applet
      try {  // first try to catch any security exceptions
        try {
          stream = new FileInputStream(dataPath(filename));
          if (stream != null) return stream;
        } catch (IOException e2) { }

        try {
          stream = new FileInputStream(sketchPath(filename));
          if (stream != null) return stream;
        } catch (Exception e) { }  // ignored

        try {
          stream = new FileInputStream(filename);
          if (stream != null) return stream;
        } catch (IOException e1) { }

      } catch (SecurityException se) { }  // online, whups

    } catch (Exception e) {
      //die(e.getMessage(), e);
      e.printStackTrace();
    }

    return null;
  }

  //////////////////////////////////////////////////////////////

  public static String sketchPath() {
    return sketchPath;
  }

  public static String sketchPath(String where) {
    if (sketchPath() == null) {
      return where;
    }
    // isAbsolute() could throw an access exception, but so will writing
    // to the local disk using the sketch path, so this is safe here.
    // for 0120, added a try/catch anyways.
    try {
      if (new File(where).isAbsolute()) return where;
    } catch (Exception e) { }

    return sketchPath() + File.separator + where;
  }

  public static File sketchFile(String where) {
    return new File(sketchPath(where));
  }

  public static String savePath(String where) {
    if (where == null) return null;
    String filename = sketchPath(where);
    PApplet.createPath(filename);
    return filename;
  }

  public static File saveFile(String where) {
    return new File(savePath(where));
  }

  public static String dataPath(String where) {
    return dataFile(where).getAbsolutePath();
  }

  public static File dataFile(String where) {
    // isAbsolute() could throw an access exception, but so will writing
    // to the local disk using the sketch path, so this is safe here.
    File why = new File(where);
    if (why.isAbsolute()) return why;

    URL jarURL = Utils.class.getProtectionDomain().getCodeSource().getLocation();
    // Decode URL
    String jarPath;
    try {
      jarPath = jarURL.toURI().getPath();
    } catch (URISyntaxException e) {
      e.printStackTrace();
      return null;
    }
    if (jarPath.contains("Contents/Java/")) {
      File containingFolder = new File(jarPath).getParentFile();
      File dataFolder = new File(containingFolder, "data");
      return new File(dataFolder, where);
    }
    // Windows, Linux, or when not using a Mac OS X .app file
    File workingDirItem =
      new File(sketchPath + File.separator + "data" + File.separator + where);
//    if (workingDirItem.exists()) {
    return workingDirItem;
//    }
//    // In some cases, the current working directory won't be set properly.
  }

}

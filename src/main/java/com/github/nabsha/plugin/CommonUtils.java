package com.github.nabsha.plugin;

import java.io.PrintWriter;
import java.io.StringWriter;

public class CommonUtils {
  public static String printStackTrace ( Exception e ) {
    StringWriter sw = new StringWriter ();
    PrintWriter pw = new PrintWriter ( sw );
    e.printStackTrace ( pw );
    return sw.toString ();
  }


  public static String esc ( String str ) {
    return "\"" + str + "\"";
  }

}

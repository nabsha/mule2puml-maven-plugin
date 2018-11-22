package com.github.nabsha.plugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class FileUtils {

  /**
   * Path to string
   * @param file
   * @return
   * @throws IOException
   */
  public static String readFile ( Path file ) throws IOException {
    return new String ( Files.readAllBytes ( file ) );

  }

  /**
   * search files recursively in path
   * @param path
   * @param pattern
   * @return
   * @throws IOException
   */
  public static List<Path> searchFiles ( String path, String pattern ) throws IOException {

    return Files.walk ( Paths.get ( path ) )
      .filter ( p -> p.toString ().matches ( path + pattern ) )
      .collect ( Collectors.toList () );

  }


  /**
   * Reads given resource file as a string.
   *
   * @param fileName the path to the resource file
   * @return the file's contents or null if the file could not be opened
   */
  public static List<String> getResourceFileAsList ( String fileName ) {
    InputStream is = FileUtils.class.getClassLoader ().getResourceAsStream ( fileName );
    if ( is != null ) {
      BufferedReader reader = new BufferedReader ( new InputStreamReader ( is ) );
      return reader.lines ().collect ( Collectors.toList () );
    }
    return null;
  }
}

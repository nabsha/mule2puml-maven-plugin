package com.github.nabsha.plugin;


import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.*;
import java.io.*;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.github.nabsha.plugin.CommonUtils.esc;
import static com.github.nabsha.plugin.CommonUtils.printStackTrace;
import static com.github.nabsha.plugin.FileUtils.getResourceFileAsList;
import static com.github.nabsha.plugin.FileUtils.searchFiles;
import static com.github.nabsha.plugin.XMLUtils.*;


/**
 *
 */
@Mojo ( name = "mule2puml", defaultPhase = LifecyclePhase.GENERATE_RESOURCES )
public class MyMojo extends AbstractMojo {
  /**
   * Location of the file.
   */
  @Parameter ( defaultValue = "${project.basedir}", property = "muleSourceDir", required = true )
  private File muleSourceDir;

  @Parameter ( defaultValue = "/.*/src/main/app/.*\\.xml", property = "muleFilesPathRegex", required = false )
  private String muleFilesPathRegex;

  @Parameter ( defaultValue = "/.*/.*\\.properties", property = "propertiesFilesPathRegex", required = false )
  private String propertiesFilesPathRegex;

  @Parameter ( defaultValue = "true", property = "generateUberMuleXML", required = false )
  private boolean generateUberMuleXML;

  @Parameter ( defaultValue = "${project.build.directory}/docs/mule.xml", property = "outputUberMuleXMLPath", required = false )
  private File outputUberMuleXMLPath;

  @Parameter ( defaultValue = "${project.build.directory}/docs/puml", property = "pumlOutputDir", required = false )
  private File pumlOutputDir;


  @Parameter ( property = "entryXPathFilters", required = false )
  private List<String> entryXPathFilters;

  public void execute ()
    throws MojoExecutionException {

    if ( entryXPathFilters == null || entryXPathFilters.isEmpty () ) {
      entryXPathFilters = getResourceFileAsList ( "entryXPath.cfg" );
    }

    List<Path> files = getMuleProjectFiles ( muleSourceDir.getAbsolutePath (), muleFilesPathRegex );

    if ( files == null ) return;

    Document muleUberXML = null;
    try {

      muleUberXML = createMuleUberXML ( files );

    } catch ( Exception e ) {
      throw new MojoExecutionException ( "Failed to merge documents " + printStackTrace ( e ) );
    }

    try {
      replaceProperties ( muleUberXML, muleSourceDir.getAbsolutePath (), propertiesFilesPathRegex );
    } catch ( IOException e ) {
      throw new MojoExecutionException ( "Failed to replace properties" + printStackTrace ( e ) );
    }


    try {
      injectHTTPListenerForAPIKitFlows ( muleUberXML );
    } catch ( XPathExpressionException e ) {
      throw new MojoExecutionException ( "Failed to inject HTTP Listener for APIKit generated flows " + printStackTrace ( e ) );
    }

    try {
      writeMuleUberXML ( muleUberXML );
    } catch ( FileNotFoundException e ) {
      throw new MojoExecutionException ( "Write Uber XML Failed : " + printStackTrace ( e ) );
    }

    String joined = String.join ( " | ", entryXPathFilters );
    getLog ().info ( "Filtering Entry flows using " + joined );
    NodeList results = null;
    try {
      results = findNodeByXPath ( joined, muleUberXML );

      for ( int i = 0; i < results.getLength (); i++ ) {
        generatePuml ( muleUberXML, results.item ( i ), pumlOutputDir.getAbsolutePath () );
      }
    } catch ( XPathExpressionException e ) {
      throw new MojoExecutionException ( "Generate Puml failed: Failed to find node using xpath " + printStackTrace ( e ) );
    } catch ( FileNotFoundException e ) {
      throw new MojoExecutionException ( "Generate Puml failed: " + printStackTrace ( e ) );
    }

  }


  private void generatePuml ( Document muleUberXML, Node item, String pumlOutputDirAbsolutePath ) throws FileNotFoundException, XPathExpressionException {
    Map<String, String> props = new HashMap<> ();
    String name = getAttrValue ( item, "name" ).replace ( ":", "_" ).replace ( "/", "-" );
    File file = new File ( pumlOutputDirAbsolutePath + "/" + name + ".puml" );
    getLog ().info ( "Creating file " + file.getAbsolutePath () );
    file.getParentFile ().mkdirs ();

    PrintWriter out = new PrintWriter ( file );
    walk ( muleUberXML, item, props, out );
    out.close ();
  }

  private void writeMuleUberXML ( Document muleUberXML ) throws FileNotFoundException {
    if ( generateUberMuleXML ) {
      getLog ().info ( "Writing mule uber xml to " + outputUberMuleXMLPath.getAbsolutePath () );
      outputUberMuleXMLPath.getParentFile ().mkdirs ();
      try ( PrintWriter out = new PrintWriter ( outputUberMuleXMLPath ) ) {
        out.println ( docToString ( muleUberXML ) );
      }
    }
  }

  private void injectHTTPListenerForAPIKitFlows ( Document reduced ) throws XPathExpressionException {
    NodeList apikitFlows = findNodeByXPath ( "/*[local-name()='mule']/*[local-name()='flow'][contains(@name, 'get:') or contains(@name, 'put:') or contains(@name, 'post:') or contains(@name, 'patch:') or contains(@name, 'delete:') or contains (@name, 'head:')]", reduced );
    for ( int i = 0; i < apikitFlows.getLength (); i++ ) {
      Node item = apikitFlows.item ( i );
      String name = getAttrValue ( item, "name" );
      Pattern pattern = Pattern.compile ( "[^:]*:([^:]*):.*" );
      Matcher matcher = pattern.matcher ( name );

      if ( matcher.find () ) {
        String group = matcher.group ( 1 );
        NodeList pathAttr = findNodeByXPath ( ".//*[local-name()='listener']/@path", item.getOwnerDocument () );

        Node path = pathAttr.item ( 0 );
        String pathValue = path.getNodeValue ().replace ( "*", "" );
        String finalPath = pathValue + group;
        path.setNodeValue ( finalPath.replace ( "//", "/" ) );
      }
    }
  }

  private void replaceProperties ( Document muleUberXML, String absolutePath, String propertiesFilesPathRegex ) throws IOException {
    // replace properties with values
    getLog ().info ( "Searching for " + propertiesFilesPathRegex + " in " + absolutePath );
    List<Path> propertiesFiles = searchFiles ( absolutePath, propertiesFilesPathRegex );

    getLog ().info ( "Properties files found : " + propertiesFiles );

    propertiesFiles.forEach ( path -> {
      Properties properties = new Properties ();
      try {
        properties.load ( new FileInputStream ( path.toFile () ) );
        properties.forEach ( ( k, v ) -> {
          try {

            NodeList nodeByXPath = findNodeByXPath ( "//attribute::*[contains(., '${" + k + "}')]", muleUberXML );
            for ( int i = 0; i < nodeByXPath.getLength (); i++ ) {
              Node item = nodeByXPath.item ( i );
              item.setNodeValue ( v.toString () );
            }
          } catch ( XPathExpressionException e ) {
            e.printStackTrace ();
          }
        } );
      } catch ( IOException e ) {
        e.printStackTrace ();
      }
    } );
  }

  private Document createMuleUberXML ( List<Path> files ) throws Exception {
    Stream<File> fileStream = files.stream ().map ( Path::toFile );
    Properties namespaces = new Properties ();
    namespaces.load ( this.getClass ().getClassLoader ().getResourceAsStream ( "default-namespaces.properties" ) );
    Document merge = merge ( "/mule", namespaces, fileStream.toArray ( File[]::new ) );
    String mergedDoc = docToString ( merge );
    DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance ();
    docBuilderFactory.setNamespaceAware ( true );
    DocumentBuilder docBuilder = null;
    docBuilder = docBuilderFactory.newDocumentBuilder ();
    Document base = docBuilder.parse ( new InputSource ( new StringReader ( mergedDoc ) ) );
    Document transformed = transform ( base, this.getClass ().getClassLoader ().getResourceAsStream ( "flatten-stage1.xsl" ) );
    return transform ( transformed, this.getClass ().getClassLoader ().getResourceAsStream ( "reduce-stage2.xsl" ) );
  }

  private List<Path> getMuleProjectFiles ( String absolutePath, String muleFilesPathRegex ) throws MojoExecutionException {
    List<Path> files = null;
    try {
      getLog ().info ( "Searching for mule files in " + absolutePath + " with regex " + muleFilesPathRegex );
      files = searchFiles ( absolutePath, muleFilesPathRegex );
      files.forEach ( file -> {
        getLog ().info ( "Found : " + file.toString () );
      } );

    } catch ( IOException e ) {
      throw new MojoExecutionException ( "Failed to search mule source files in " + absolutePath );
    }
    if ( files.isEmpty () ) {
      getLog ().info ( "No files found in " + absolutePath + " with " + muleFilesPathRegex + " pattern" );
      return null;
    }
    return files;
  }


  private Document merge ( String expression, Properties defaultNamespaces, File... files ) throws Exception {
    XPathFactory xPathFactory = XPathFactory.newInstance ();
    XPath xpath = xPathFactory.newXPath ();
    XPathExpression compiledExpression = xpath.compile ( expression );
    DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance ();
    docBuilderFactory.setIgnoringElementContentWhitespace ( true );
    DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder ();

    Document base = docBuilder.parse ( files[ 0 ] );

    defaultNamespaces.stringPropertyNames ().forEach ( key -> {
      base.getDocumentElement ().setAttribute ( key, defaultNamespaces.getProperty ( key ) );
    } );

    Node results = (Node) compiledExpression.evaluate ( base, XPathConstants.NODE );
    if ( results == null ) {
      throw new IOException ( files[ 0 ]
        + ": expression does not evaluate to node" );
    }

    for ( int i = 1; i < files.length; i++ ) {
      Document merge = docBuilder.parse ( files[ i ] );
      Node nextResults = (Node) compiledExpression.evaluate ( merge,
        XPathConstants.NODE );
      while ( nextResults.hasChildNodes () ) {
        Node kid = nextResults.getFirstChild ();
        nextResults.removeChild ( kid );
        kid = base.importNode ( kid, true );
        results.appendChild ( kid );
      }
    }

    return base;
  }

  public String before ( Document document, Node node, Map<String, String> props ) throws XPathExpressionException {

    switch ( node.getNodeName () ) {
      case "flow":
        return "@startuml";

      case "http:listener": {

        String configName = getAttrValue ( node, "config-ref" );

        NodeList nodeByXPath = findNodeByXPath ( "/*[local-name()='mule']/*[local-name()='listener-config'][@name='" + configName + "']", document );
        NamedNodeMap configAttr = nodeByXPath.item ( 0 ).getAttributes ();

        String value = esc ( getAttrValue ( node, "path" ) );
        props.put ( "Entry", value );
        return "";
      }

      case "http:request": {
        String configName = getAttrValue ( node, "config-ref" );
        NodeList nodeByXPath = findNodeByXPath ( "/*[local-name()='mule']/*[local-name()='request-config'][@name='" + configName + "']", document );
        Node configAttr = nodeByXPath.item ( 0 );
        String url = getAttrValue ( configAttr, "protocol" ) + "://"
          + getAttrValue ( configAttr, "host" ) + ":"
          + getAttrValue ( configAttr, "port" )
          + getAttrValue ( configAttr, "basePath" )
          + getAttrValue ( node, "path" );

        return props.get ( "Entry" ) + "-->" + esc ( url );
      }

      case "jms:inbound-endpoint": {
        String jmsIn = esc ( getAttrValue ( node, "queue" ) );
        props.put ( "Entry", jmsIn );
        return "";

      }

      case "choice":
      case "foreach": {
        return "alt " + getAttrValue ( node, "doc:name" );
      }

      case "otherwise":
        return "else otherwise";
      case "when":
        return "else " + esc ( getAttrValue ( node, "expression" ) );

      case "expression-component":
      case "expression":
      case "invoke":
      case "dw:transform-message": {
        return props.get ( "Entry" ) + " --> " + props.get ( "Entry" ) + " : " + getAttrValue ( node, "doc:name" );
      }
      case "db:update":
      case "db:select":
      case "db:delete":

        String entry = props.get ( "Entry" );
        if ( entry != null && entry.isEmpty () )
          return "";
        return entry + "--> " + esc ( node.getNodeName () );

      default:
        return "";
    }
  }


  public String after ( Document document, Node node, Map<String, String> props ) throws XPathExpressionException {
    switch ( node.getNodeName () ) {
      case "flow": {
        props.remove ( "Entry" );
        return "@enduml";
      }
      case "http:request": {
        String configName = getAttrValue ( node, "config-ref" );
        NodeList nodeByXPath = findNodeByXPath ( "/*[local-name()='mule']/*[local-name()='request-config'][@name='" + configName + "']", document );
        Node configAttr = nodeByXPath.item ( 0 );
        String url = getAttrValue ( configAttr, "protocol" ) + "://"
          + getAttrValue ( configAttr, "host" ) + ":"
          + getAttrValue ( configAttr, "port" )
          + getAttrValue ( configAttr, "basePath" )
          + getAttrValue ( node, "path" );

        return esc ( url ) + "-->" + props.get ( "Entry" );
      }

      case "choice":
      case "foreach":
        return "end alt";
      default:
        return "";
    }
  }

  public void print ( String str, PrintWriter out ) {
    if ( str.isEmpty () )
      return;

    out.println ( str );
  }

  public void walk ( Document base, Node node, Map<String, String> props, PrintWriter out ) throws XPathExpressionException {

    print ( before ( base, node, props ), out );

    NodeList nodeList = node.getChildNodes ();

    for ( int i = 0; i < nodeList.getLength (); i++ ) {

      Node currentNode = nodeList.item ( i );

      if ( currentNode.getNodeType () == Node.ELEMENT_NODE ) {

        walk ( base, currentNode, props, out );

      }

    }

    print ( after ( base, node, props ), out );
  }

}

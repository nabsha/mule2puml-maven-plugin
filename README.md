Intro
===

This plugin generates puml diagrams by searching down 'muleSourceDir' and looking for files matching provided pattern.
The plugin then 
 - merges all the files into one big mule xml document
 - It then expands all flow-refs in the big mule xml in-place. i.e. all flow-ref are replaced with the actual sub-flow/flow
 - then removes the redundant sub-flow and flows that are not used anymore
 - then parses the simplified mule flow to generate plantuml (.puml) 


Maven Plugin Configuration
===

```       
			<plugin>
				<artifactId>mule2puml-maven-plugin</artifactId>
				<configuration>
					<muleSourceDir>src/test/resources/mule-sample-api-v1</muleSourceDir>
					<muleFilesPathRegex>/src/main/app/.*\.xml</muleFilesPathRegex>
					<generateUberMuleXML>true</generateUberMuleXML>
					<outputUberMuleXMLPath>target/generated-test-sources/docs/mule.xml</outputUberMuleXMLPath>
					<pumlOutputDir>target/generated-test-sources/docs/puml</pumlOutputDir>
					<entryXPathFilters>
						<entryXPathFilter>/*[local-name()='mule']/*[local-name()='flow']/*[local-name()='listener']/parent::node()</entryXPathFilter>
						<entryXPathFilter>/*[local-name()='mule']/*[local-name()='flow']/*[local-name()='inbound-endpoint']/parent::node()</entryXPathFilter>
						<entryXPathFilter>/*[local-name()='mule']/*[local-name()='flow'][contains(@name, 'sample-api-v1-config')]</entryXPathFilter>
						<!--/c:mule/c:flow[@name='get:/{bancsSchemeNo}:sample-api-v1-config']/@name-->
					</entryXPathFilters>
				</configuration>
			</plugin>
```
Usage
==
 
```mvn com.github.nabsha.plugin:mule2puml-maven-plugin:1.0-SNAPSHOT:mule2puml -DmuleSourceDir=$pwd```
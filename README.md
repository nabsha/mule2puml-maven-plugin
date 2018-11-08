```        <plugin>
            <artifactId>mule2puml-maven-plugin</artifactId>
            <version>1.0-SNAPSHOT</version>
            <configuration>
              <outputUberMuleXMLPath>${project.basedir}/docs/ubermule.xml</outputUberMuleXMLPath>
              <muleSourceDir>${project.basedir}</muleSourceDir>
              <generateUberMuleXML>true</generateUberMuleXML>
            </configuration>
        </plugin>
```

mvn com.github.nabsha.plugin:mule2puml-maven-plugin:1.0-SNAPSHOT:mule2puml -DmuleSourceDir=$pwd
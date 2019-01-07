package com.github.nabsha.plugin;


import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.plugin.testing.WithoutMojo;

import org.junit.Rule;
import static org.junit.Assert.*;
import org.junit.Test;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MyMojoTest extends AbstractMojoTestCase
{

    protected void setUp() throws Exception
    {
        // required for mojo lookups to work
        super.setUp();
    }

    public void testApiKitBasedPumlSequenceGenerator() throws Exception {
        File pom = new File( "src/test/resources/apikit-test/pom.xml" );
        Mojo mojo = lookupMojo ( "mule2puml", pom );
        mojo.execute ();

        byte[] actualBytes = Files.readAllBytes(Paths.get("./target/generated-test-sources/apikit-test/puml/get_-_sample-api-v1-config.puml"));
        byte[] expectedBytes = Files.readAllBytes(Paths.get("./src/test/resources/expected/apikit-test/expected-get_-_sample-api-v1-config.puml"));

        assertArrayEquals(expectedBytes, actualBytes);
    }


    public void testHttpListenerBasedPumlSequenceGenerator() throws Exception {
        File pom = new File( "src/test/resources/http-listener-test/pom.xml" );
        Mojo mojo = lookupMojo ( "mule2puml", pom );
        mojo.execute ();

        byte[] actualBytes = Files.readAllBytes(Paths.get("./target/generated-test-sources/http-listener-test/puml/sample-api-v1-config.puml"));
        byte[] expectedBytes = Files.readAllBytes(Paths.get("./src/test/resources/expected/http-listener-test/expected-sample-api-v1-config.puml"));

        assertArrayEquals(expectedBytes, actualBytes);
    }


    public void testMultipleProjects() throws Exception {
        File pom = new File( "src/test/resources/multiple-projects/pom.xml" );

        Mojo mojo = lookupMojo ( "mule2puml", pom );
        mojo.execute ();



        assertTrue( true );
    }


}


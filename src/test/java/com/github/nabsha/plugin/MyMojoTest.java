package com.github.nabsha.plugin;


import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.plugin.testing.WithoutMojo;

import org.junit.Rule;
import static org.junit.Assert.*;
import org.junit.Test;
import java.io.File;

public class MyMojoTest extends AbstractMojoTestCase
{

    protected void setUp() throws Exception
    {
        // required for mojo lookups to work
        super.setUp();
    }

    public void testSomethingWhichDoesNotNeedTheMojoAndProbablyShouldBeExtractedIntoANewClassOfItsOwn() throws Exception {
        File pom = new File( "src/test/resources/mule-sample-api-v1/pom.xml" );
        Mojo mojo = lookupMojo ( "mule2puml", pom );
        mojo.execute ();
        //MyMojo mojo = new MyMojo ();
        //mojo = (MyMojo) configureMojo ( mojo, extractPluginConfiguration("mule2puml-maven-plugin", pom));

        assertTrue( true );
    }

}


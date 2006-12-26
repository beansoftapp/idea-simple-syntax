package net.intensicode.idea.config.loaded;

import com.intellij.openapi.diagnostic.Logger;
import groovy.lang.GroovyShell;
import jfun.parsec.Parser;
import jfun.parsec.Tok;
import net.intensicode.idea.config.*;
import net.intensicode.idea.config.loaded.parser.AssignmentConsumer;
import net.intensicode.idea.config.loaded.parser.ConfigurationParser;
import net.intensicode.idea.config.loaded.parser.KnownIDConsumer;
import net.intensicode.idea.config.loaded.parser.PropertyConsumer;
import net.intensicode.idea.core.SimpleAttributes;
import net.intensicode.idea.core.SimpleLanguage;
import net.intensicode.idea.core.SimpleLexer;
import net.intensicode.idea.syntax.JParsecLexer;
import net.intensicode.idea.system.OptionsFolder;
import net.intensicode.idea.system.SystemContext;
import net.intensicode.idea.util.LoggerFactory;
import net.intensicode.idea.util.ReaderUtils;

import javax.swing.*;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;



/**
 * TODO: Describe this!
 */
public class LoadedConfiguration implements InstanceConfiguration, ConfigurationProperties
{
    public static final LoadedConfiguration tryLoading( final SystemContext aSystemContext, final String aFileName )
    {
        try
        {
            final OptionsFolder optionsFolder = aSystemContext.getOptionsFolder();
            final String config = optionsFolder.readFileIntoString( aFileName );
            final Reader reader = new StringReader( config );
            return new LoadedConfiguration( aSystemContext, reader );
        }
        catch ( final Throwable t )
        {
            LOG.info( "Failed loading configuration: ", t );
            return null;
        }
    }

    public LoadedConfiguration( final SystemContext aSystemContext, final Reader aReader ) throws IOException
    {
        mySystemContext = aSystemContext;

        loadConfiguration( aReader );
        validateConfiguration();
    }

    // From InstanceConfiguration

    public final Icon getIcon()
    {
        if ( isValidProperty( ICON ) == false ) return null;
        return mySystemContext.getOptionsFolder().loadIcon( getProperty( ICON ) );
    }

    public final String getName()
    {
        return getProperty( NAME );
    }

    public final String getDescription()
    {
        return getProperty( DESCRIPTION );
    }

    public synchronized final String getExampleCode()
    {
        if ( myExampleCode == null )
        {
            try
            {
                myExampleCode = readExampleCode();
            }
            catch ( final Throwable t )
            {
                LOG.info( t.toString() );
                LOG.error( t );
                return t.toString();
            }
        }
        return myExampleCode;
    }

    public final List<String> getKnownTokenIDs()
    {
        return myKnownTokenIDs;
    }

    public final boolean isVisibleToken( final String aID )
    {
        // TODO: Read this from some property?
        return true;
    }

    public final String getTokenAttributes( final String aTokenID )
    {
        final String attributes = myAttributes.get( aTokenID );
        if ( attributes == null ) return NO_ATTRIBUTES;
        return attributes;
    }

    public final String getTokenDescription( final String aTokenID )
    {
        final String description = myDescriptions.get( aTokenID );
        if ( description == null ) return aTokenID;
        return description;
    }

    public synchronized final BracesConfiguration getBracesConfiguration()
    {
        if ( myBracesConfiguration == null )
        {
            myBracesConfiguration = new LoadedBracesConfiguration( this );
        }
        return myBracesConfiguration;
    }

    public synchronized final CommentConfiguration getCommentConfiguration()
    {
        if ( myCommentConfiguration == null )
        {
            myCommentConfiguration = new LoadedCommentConfiguration( this );
        }
        return myCommentConfiguration;
    }

    public synchronized final FileTypeConfiguration getFileTypeConfiguration()
    {
        if ( myFileTypeConfiguration == null )
        {
            myFileTypeConfiguration = new LoadedFileTypeConfiguration( this, mySystemContext.getOptionsFolder() );
        }
        return myFileTypeConfiguration;
    }

    public synchronized final LanguageConfiguration getLanguageConfiguration()
    {
        if ( myLanguage == null )
        {
            myLanguage = SimpleLanguage.getOrCreate( mySystemContext, this );
        }
        return myLanguage;
    }

    public synchronized final SimpleAttributes getAttributes()
    {
        if ( mySimpleAttributes == null )
        {
            mySimpleAttributes = new SimpleAttributes( mySystemContext, this );
        }
        return mySimpleAttributes;
    }

    public synchronized final SimpleLexer getLexer()
    {
        if ( mySyntaxLexer == null )
        {
            try
            {
                LOG.info( "Reading groovy lexer" );
                final GroovyShell shell = new GroovyShell();
                final Parser<Tok[]> lexer = ( Parser<Tok[]> ) shell.evaluate( streamSyntaxDefinition() );
                mySyntaxLexer = new JParsecLexer( lexer );
                LOG.info( "Groovy lexer created" );
            }
            catch ( final Throwable t )
            {
                LOG.info( t.toString() );
                LOG.error( t );
                return null;
            }
        }
        return mySyntaxLexer;
    }

    // From ConfigurationProperties

    public final String getProperty( final String aKey )
    {
        return myProperties.get( aKey );
    }

    public final boolean isValidProperty( final String aKey )
    {
        final String value = getProperty( aKey );
        return value != null && value.length() > 0;
    }

    // Implementation

    private final void loadConfiguration( final Reader aReader ) throws IOException
    {
        final List<String> lines = ReaderUtils.readLines( aReader );
        if ( lines.size() < 3 ) throw new IOException( "Short configuration file" );

        final String configHeader = lines.get( 0 );
        if ( isValidVersion( configHeader ) == false )
        {
            throw new IOException( "Invalid configuration version: " + configHeader );
        }

        final ConfigurationParser parser = new ConfigurationParser();
        parser.addConsumer( new PropertyConsumer( myProperties ) );
        parser.addConsumer( new AssignmentConsumer( myAttributes, "attributes" ) );
        parser.addConsumer( new AssignmentConsumer( myDescriptions, "descriptions" ) );
        parser.addConsumer( new KnownIDConsumer( myKnownTokenIDs, "descriptions" ) );
        parser.consume( lines );
    }

    private final void validateConfiguration() throws IOException
    {
        for ( final String key : REQUIRED_ENTRIES )
        {
            if ( isValidProperty( key ) == false )
            {
                throw new IOException( "Missing '" + key + "' entry" );
            }
        }
    }

    private static final boolean isValidVersion( final String aConfigHeader )
    {
        return aConfigHeader.equals( "[SimpleSyntax:V1.0]" );
    }

    private final String readExampleCode() throws Throwable
    {
        if ( isValidProperty( EXAMPLE_CODE ) == false ) return "No example code!";

        final String exampleCodeFileName = getProperty( EXAMPLE_CODE );
        return mySystemContext.getOptionsFolder().readFileIntoString( exampleCodeFileName );
    }

    private final InputStream streamSyntaxDefinition() throws Throwable
    {
        if ( isValidProperty( SYNTAX_DEFINITION ) == false ) return new ByteArrayInputStream( new byte[0] );

        final String fileName = getProperty( SYNTAX_DEFINITION );
        return mySystemContext.getOptionsFolder().streamFile( fileName );
    }



    private String myExampleCode;

    private SimpleLexer mySyntaxLexer;

    private SimpleLanguage myLanguage;

    private SimpleAttributes mySimpleAttributes;

    private LoadedBracesConfiguration myBracesConfiguration;

    private LoadedCommentConfiguration myCommentConfiguration;

    private LoadedFileTypeConfiguration myFileTypeConfiguration;


    private final SystemContext mySystemContext;

    private final ArrayList<String> myKnownTokenIDs = new ArrayList<String>();

    private final HashMap<String, String> myProperties = new HashMap<String, String>();

    private final HashMap<String, String> myAttributes = new HashMap<String, String>();

    private final HashMap<String, String> myDescriptions = new HashMap<String, String>();


    private static final String NAME = "Name";

    private static final String ICON = "Icon";

    private static final String NO_ATTRIBUTES = "";

    private static final String DESCRIPTION = "Description";

    private static final String EXAMPLE_CODE = "ExampleCode";

    private static final String SYNTAX_DEFINITION = "SyntaxDefinition";

    private static final String[] REQUIRED_ENTRIES = { NAME, DESCRIPTION, SYNTAX_DEFINITION };

    private static final Logger LOG = LoggerFactory.getLogger();
}

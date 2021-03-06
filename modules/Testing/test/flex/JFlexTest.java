package flex;

import JFlex.JFlexer;
import junit.framework.TestCase;
import net.intensicode.idea.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

/**
 * TODO: Describe this!
 */
public final class JFlexTest extends TestCase
{
    public final void testSomething() throws IOException
    {
        final String skeleton = retrieve( "idea-skeleton" );
        final JFlexer flexer = new JFlexer().setSkeleton( skeleton );

        final Reader input = read( "Python.flex" );
        final String result = clean( flexer.generate( input ) );

        final String expected = clean( retrieve( "Python.expected" ) );
        assertEquals( expected, result );
    }

    private final String retrieve( final String aResourceName ) throws IOException
    {
        final InputStream skeletonStream = stream( aResourceName );
        return StreamUtils.loadIntoString( skeletonStream );
    }

    private final Reader read( final String aResourceName )
    {
        return new InputStreamReader( stream( aResourceName ) );
    }

    private final InputStream stream( final String aResourceName )
    {
        return getClass().getResourceAsStream( aResourceName );
    }

    private static final String clean( final String aString )
    {
        final String first = aString.replace( '\r', '\n' );
        final String clean = first.replaceAll( "\n\n", "\n" );
        if ( clean.equals( aString ) ) return aString;
        return clean( clean );
    }
}

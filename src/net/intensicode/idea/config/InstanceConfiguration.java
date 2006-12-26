package net.intensicode.idea.config;

import net.intensicode.idea.core.SimpleAttributes;
import net.intensicode.idea.core.SimpleLexer;

import javax.swing.*;
import java.util.List;



/**
 * TODO: Describe this!
 */
public interface InstanceConfiguration
{
    Icon getIcon();

    String getName();

    String getDescription();

    String getExampleCode();


    List<String> getKnownTokenIDs();

    boolean isVisibleToken( String aID );

    String getTokenAttributes( String aTokenID );

    String getTokenDescription( String aTokenID );


    BracesConfiguration getBracesConfiguration();

    CommentConfiguration getCommentConfiguration();

    FileTypeConfiguration getFileTypeConfiguration();


    LanguageConfiguration getLanguageConfiguration();

    SimpleAttributes getAttributes();

    SimpleLexer getLexer();
}

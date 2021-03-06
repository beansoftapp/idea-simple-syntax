package net.intensicode.idea.config;

import com.intellij.lexer.Lexer;
import net.intensicode.idea.core.ConfigurableAttributes;
import net.intensicode.idea.system.ScriptSupport;

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

    NamesValidatorConfiguration getNamesValidatorConfiguration();


    LanguageConfiguration getLanguageConfiguration();

    ConfigurableAttributes getAttributes();

    ScriptSupport getScriptSupport();

    Lexer getLexer();
}

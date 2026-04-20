package com.slicc.plugin.highlighting;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import com.slicc.plugin.lexer.SliccLexer;
import com.slicc.plugin.lexer.SliccTokenTypes;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

public class SliccSyntaxHighlighter extends SyntaxHighlighterBase {

    // ...... Attribute Keys ............................................................................................................................................................

    // Declaration-level keywords (machine, structure, action, transition ...)
    public static final TextAttributesKey DECL_KEYWORD =
            createTextAttributesKey("SLICC_DECL_KEYWORD",
                    DefaultLanguageHighlighterColors.KEYWORD);

    // Control-flow keywords (if, else, for, while, return)
    public static final TextAttributesKey CONTROL_KEYWORD =
            createTextAttributesKey("SLICC_CONTROL_KEYWORD",
                    DefaultLanguageHighlighterColors.KEYWORD);

    // Primitive type keywords (void, bool, int, float, string)
    public static final TextAttributesKey TYPE_KEYWORD =
            createTextAttributesKey("SLICC_TYPE_KEYWORD",
                    DefaultLanguageHighlighterColors.KEYWORD);

    // Built-in SLICC statements (peek, enqueue, trigger, stall_and_wait ...)
    public static final TextAttributesKey BUILTIN_STMT =
            createTextAttributesKey("SLICC_BUILTIN_STMT",
                    DefaultLanguageHighlighterColors.FUNCTION_CALL);

    // Attribute/qualifier keywords (desc, default, network, rank ...)
    public static final TextAttributesKey ATTRIBUTE_KEYWORD =
            createTextAttributesKey("SLICC_ATTRIBUTE_KEYWORD",
                    DefaultLanguageHighlighterColors.METADATA);

    public static final TextAttributesKey LINE_COMMENT =
            createTextAttributesKey("SLICC_LINE_COMMENT",
                    DefaultLanguageHighlighterColors.LINE_COMMENT);

    public static final TextAttributesKey BLOCK_COMMENT =
            createTextAttributesKey("SLICC_BLOCK_COMMENT",
                    DefaultLanguageHighlighterColors.BLOCK_COMMENT);

    public static final TextAttributesKey STRING =
            createTextAttributesKey("SLICC_STRING",
                    DefaultLanguageHighlighterColors.STRING);

    public static final TextAttributesKey NUMBER =
            createTextAttributesKey("SLICC_NUMBER",
                    DefaultLanguageHighlighterColors.NUMBER);

    public static final TextAttributesKey IDENTIFIER =
            createTextAttributesKey("SLICC_IDENTIFIER",
                    DefaultLanguageHighlighterColors.IDENTIFIER);

    public static final TextAttributesKey OPERATOR =
            createTextAttributesKey("SLICC_OPERATOR",
                    DefaultLanguageHighlighterColors.OPERATION_SIGN);

    public static final TextAttributesKey BRACES =
            createTextAttributesKey("SLICC_BRACES",
                    DefaultLanguageHighlighterColors.BRACES);

    public static final TextAttributesKey PARENS =
            createTextAttributesKey("SLICC_PARENS",
                    DefaultLanguageHighlighterColors.PARENTHESES);

    public static final TextAttributesKey BRACKETS =
            createTextAttributesKey("SLICC_BRACKETS",
                    DefaultLanguageHighlighterColors.BRACKETS);

    public static final TextAttributesKey SEMICOLON =
            createTextAttributesKey("SLICC_SEMICOLON",
                    DefaultLanguageHighlighterColors.SEMICOLON);

    public static final TextAttributesKey COMMA =
            createTextAttributesKey("SLICC_COMMA",
                    DefaultLanguageHighlighterColors.COMMA);

    public static final TextAttributesKey BAD_CHAR =
            createTextAttributesKey("SLICC_BAD_CHAR",
                    HighlighterColors.BAD_CHARACTER);

    // Semantic keys (used by annotator, not by lexer)
    public static final TextAttributesKey MACHINE_NAME =
            createTextAttributesKey("SLICC_MACHINE_NAME",
                    DefaultLanguageHighlighterColors.CLASS_NAME);

    public static final TextAttributesKey STRUCTURE_NAME =
            createTextAttributesKey("SLICC_STRUCTURE_NAME",
                    DefaultLanguageHighlighterColors.CLASS_NAME);

    /**
     * Used for enumeration(...) and state_declaration(...) names:
     * AccessPermission, State, Event, Channel, MachineType, etc.
     * These define new enum types — highlighted like an interface/type alias
     * (distinct from struct/class names).
     */
    public static final TextAttributesKey ENUM_TYPE_NAME =
            createTextAttributesKey("SLICC_ENUM_TYPE_NAME",
                    DefaultLanguageHighlighterColors.INTERFACE_NAME);

    /** Keep for backwards compat — same as ENUM_TYPE_NAME */
    public static final TextAttributesKey ENUM_NAME = ENUM_TYPE_NAME;

    /**
     * Used for enum/state member names declared inside enumeration { } or
     * state_declaration { } bodies: Load, Store, I, LM, LE, Request, …
     * Highlighted like enum constants.
     */
    public static final TextAttributesKey ENUM_MEMBER_VALUE =
            createTextAttributesKey("SLICC_ENUM_MEMBER_VALUE",
                    DefaultLanguageHighlighterColors.CONSTANT);

    public static final TextAttributesKey ACTION_NAME =
            createTextAttributesKey("SLICC_ACTION_NAME",
                    DefaultLanguageHighlighterColors.FUNCTION_DECLARATION);

    public static final TextAttributesKey FUNCTION_NAME =
            createTextAttributesKey("SLICC_FUNCTION_NAME",
                    DefaultLanguageHighlighterColors.FUNCTION_DECLARATION);

    /**
     * Used for structure/TBE member variable names:
     * state, address, DataBlk, v, d, e, pos, allocated_list, …
     * Highlighted like instance fields.
     */
    public static final TextAttributesKey FIELD_NAME =
            createTextAttributesKey("SLICC_FIELD_NAME",
                    DefaultLanguageHighlighterColors.INSTANCE_FIELD);

    public static final TextAttributesKey TRANSITION_LABEL =
            createTextAttributesKey("SLICC_TRANSITION_LABEL",
                    DefaultLanguageHighlighterColors.LABEL);

    // ...... Highlighter .....................................................................................................................................................................

    private static final TextAttributesKey[] EMPTY = new TextAttributesKey[0];

    @NotNull
    @Override
    public Lexer getHighlightingLexer() {
        return new SliccLexer();
    }

    @NotNull
    @Override
    public TextAttributesKey[] getTokenHighlights(IElementType type) {
        // Declaration keywords
        if (type == SliccTokenTypes.KW_MACHINE
                || type == SliccTokenTypes.KW_STRUCTURE
                || type == SliccTokenTypes.KW_ENUMERATION
                || type == SliccTokenTypes.KW_EXTERNAL_TYPE
                || type == SliccTokenTypes.KW_STATE_DECLARATION
                || type == SliccTokenTypes.KW_IN_PORT
                || type == SliccTokenTypes.KW_OUT_PORT
                || type == SliccTokenTypes.KW_ACTION
                || type == SliccTokenTypes.KW_TRANSITION
                || type == SliccTokenTypes.KW_INCLUDE
                || type == SliccTokenTypes.KW_PROTOCOL
                || type == SliccTokenTypes.KW_GLOBAL) {
            return pack(DECL_KEYWORD);
        }
        // Control-flow keywords
        if (type == SliccTokenTypes.KW_IF
                || type == SliccTokenTypes.KW_ELSE
                || type == SliccTokenTypes.KW_FOR
                || type == SliccTokenTypes.KW_WHILE
                || type == SliccTokenTypes.KW_RETURN
                || type == SliccTokenTypes.KW_NEW) {
            return pack(CONTROL_KEYWORD);
        }
        // Type keywords + bool literals
        if (type == SliccTokenTypes.KW_VOID
                || type == SliccTokenTypes.KW_TRUE
                || type == SliccTokenTypes.KW_FALSE) {
            return pack(TYPE_KEYWORD);
        }
        // SLICC built-in statement keywords
        if (type == SliccTokenTypes.KW_PEEK
                || type == SliccTokenTypes.KW_ENQUEUE
                || type == SliccTokenTypes.KW_TRIGGER
                || type == SliccTokenTypes.KW_STALL_AND_WAIT
                || type == SliccTokenTypes.KW_WAKEUP_PORT
                || type == SliccTokenTypes.KW_CHECK_ALLOCATE
                || type == SliccTokenTypes.KW_CHECK_STOP_SLOTS
                || type == SliccTokenTypes.KW_ERROR
                || type == SliccTokenTypes.KW_ASSERT
                || type == SliccTokenTypes.KW_DPRINTF
                || type == SliccTokenTypes.KW_DEBUG_EXPR
                || type == SliccTokenTypes.KW_SET_CACHE_ENTRY
                || type == SliccTokenTypes.KW_UNSET_CACHE_ENTRY
                || type == SliccTokenTypes.KW_SET_TBE
                || type == SliccTokenTypes.KW_UNSET_TBE
                || type == SliccTokenTypes.KW_STATIC_CAST) {
            return pack(BUILTIN_STMT);
        }
        // Attribute keywords
        if (type == SliccTokenTypes.KW_DESC
                || type == SliccTokenTypes.KW_DEFAULT
                || type == SliccTokenTypes.KW_NETWORK
                || type == SliccTokenTypes.KW_VIRTUAL_NETWORK
                || type == SliccTokenTypes.KW_VNET_TYPE
                || type == SliccTokenTypes.KW_RANK
                || type == SliccTokenTypes.KW_INTERFACE
                || type == SliccTokenTypes.KW_EXTERNAL
                || type == SliccTokenTypes.KW_PRIMITIVE
                || type == SliccTokenTypes.KW_ORDERED) {
            return pack(ATTRIBUTE_KEYWORD);
        }
        // Comments
        if (type == SliccTokenTypes.LINE_COMMENT)  return pack(LINE_COMMENT);
        if (type == SliccTokenTypes.BLOCK_COMMENT) return pack(BLOCK_COMMENT);
        // Literals
        if (type == SliccTokenTypes.STRING_LITERAL)  return pack(STRING);
        if (type == SliccTokenTypes.INTEGER_LITERAL
                || type == SliccTokenTypes.FLOAT_LITERAL) return pack(NUMBER);
        // Identifier
        if (type == SliccTokenTypes.IDENTIFIER) return pack(IDENTIFIER);
        // Braces / parens / brackets
        if (type == SliccTokenTypes.OP_LBRACE || type == SliccTokenTypes.OP_RBRACE) return pack(BRACES);
        if (type == SliccTokenTypes.OP_LPAREN || type == SliccTokenTypes.OP_RPAREN) return pack(PARENS);
        if (type == SliccTokenTypes.OP_LBRACKET || type == SliccTokenTypes.OP_RBRACKET) return pack(BRACKETS);
        if (type == SliccTokenTypes.OP_SEMICOLON) return pack(SEMICOLON);
        if (type == SliccTokenTypes.OP_COMMA)     return pack(COMMA);
        // All other operators
        if (type == SliccTokenTypes.OP_ASSIGN || type == SliccTokenTypes.OP_COLON
                || type == SliccTokenTypes.OP_DOT || type == SliccTokenTypes.OP_EQ
                || type == SliccTokenTypes.OP_NEQ || type == SliccTokenTypes.OP_LEQ
                || type == SliccTokenTypes.OP_GEQ || type == SliccTokenTypes.OP_AND
                || type == SliccTokenTypes.OP_OR  || type == SliccTokenTypes.OP_NOT
                || type == SliccTokenTypes.OP_PLUS || type == SliccTokenTypes.OP_MINUS
                || type == SliccTokenTypes.OP_STAR || type == SliccTokenTypes.OP_SLASH
                || type == SliccTokenTypes.OP_PERCENT || type == SliccTokenTypes.OP_AMP
                || type == SliccTokenTypes.OP_LANGLE || type == SliccTokenTypes.OP_RANGLE) {
            return pack(OPERATOR);
        }
        if (type == SliccTokenTypes.BAD_CHARACTER) return pack(BAD_CHAR);
        return EMPTY;
    }
}
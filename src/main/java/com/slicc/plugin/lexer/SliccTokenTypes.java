package com.slicc.plugin.lexer;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.slicc.plugin.lang.SliccLanguage;

public interface SliccTokenTypes {

    // ...... Declaration keywords ..........................................................................................................................................
    IElementType KW_MACHINE          = new SliccTokenType("machine");
    IElementType KW_STRUCTURE        = new SliccTokenType("structure");
    IElementType KW_ENUMERATION      = new SliccTokenType("enumeration");
    IElementType KW_EXTERNAL_TYPE    = new SliccTokenType("external_type");
    IElementType KW_STATE_DECLARATION= new SliccTokenType("state_declaration");
    IElementType KW_IN_PORT          = new SliccTokenType("in_port");
    IElementType KW_OUT_PORT         = new SliccTokenType("out_port");
    IElementType KW_ACTION           = new SliccTokenType("action");
    IElementType KW_TRANSITION       = new SliccTokenType("transition");
    IElementType KW_INCLUDE          = new SliccTokenType("include");
    IElementType KW_PROTOCOL         = new SliccTokenType("protocol");
    IElementType KW_GLOBAL           = new SliccTokenType("global");

    // ...... C++ compatible keywords .................................................................................................................................
    IElementType KW_VOID             = new SliccTokenType("void");
    IElementType KW_IF               = new SliccTokenType("if");
    IElementType KW_ELSE             = new SliccTokenType("else");
    IElementType KW_FOR              = new SliccTokenType("for");
    IElementType KW_WHILE            = new SliccTokenType("while");
    IElementType KW_RETURN           = new SliccTokenType("return");
    IElementType KW_NEW              = new SliccTokenType("new");
    IElementType KW_STATIC_CAST      = new SliccTokenType("static_cast");
    IElementType KW_NULL_KWORD       = new SliccTokenType("OOD_NULL");  // OOD = out of declaration
    IElementType KW_TRUE             = new SliccTokenType("true");
    IElementType KW_FALSE            = new SliccTokenType("false");

    // ...... SLICC built-in functions / statements .......................................................................................
    IElementType KW_PEEK             = new SliccTokenType("peek");
    IElementType KW_ENQUEUE          = new SliccTokenType("enqueue");
    IElementType KW_TRIGGER          = new SliccTokenType("trigger");
    IElementType KW_STALL_AND_WAIT   = new SliccTokenType("stall_and_wait");
    IElementType KW_WAKEUP_PORT      = new SliccTokenType("wakeup_port");
    IElementType KW_CHECK_ALLOCATE   = new SliccTokenType("check_allocate");
    IElementType KW_CHECK_STOP_SLOTS = new SliccTokenType("check_stop_slots");
    IElementType KW_ERROR            = new SliccTokenType("error");
    IElementType KW_ASSERT           = new SliccTokenType("assert");
    IElementType KW_DPRINTF          = new SliccTokenType("DPRINTF");
    IElementType KW_DEBUG_EXPR       = new SliccTokenType("DEBUG_EXPR");
    IElementType KW_SET_CACHE_ENTRY  = new SliccTokenType("set_cache_entry");
    IElementType KW_UNSET_CACHE_ENTRY= new SliccTokenType("unset_cache_entry");
    IElementType KW_SET_TBE          = new SliccTokenType("set_tbe");
    IElementType KW_UNSET_TBE        = new SliccTokenType("unset_tbe");

    // ...... Attribute / qualifier keywords ............................................................................................................
    IElementType KW_DESC             = new SliccTokenType("desc");
    IElementType KW_DEFAULT          = new SliccTokenType("default");
    IElementType KW_NETWORK          = new SliccTokenType("network");
    IElementType KW_VIRTUAL_NETWORK  = new SliccTokenType("virtual_network");
    IElementType KW_VNET_TYPE        = new SliccTokenType("vnet_type");
    IElementType KW_RANK             = new SliccTokenType("rank");
    IElementType KW_INTERFACE        = new SliccTokenType("interface");
    IElementType KW_EXTERNAL         = new SliccTokenType("external");
    IElementType KW_PRIMITIVE        = new SliccTokenType("primitive");
    IElementType KW_ORDERED          = new SliccTokenType("ordered");

    // ...... Literals ..............................................................................................................................................................................
    IElementType INTEGER_LITERAL     = new SliccTokenType("INTEGER_LITERAL");
    IElementType FLOAT_LITERAL       = new SliccTokenType("FLOAT_LITERAL");
    IElementType STRING_LITERAL      = new SliccTokenType("STRING_LITERAL");

    // ...... Identifiers .....................................................................................................................................................................
    IElementType IDENTIFIER          = new SliccTokenType("IDENTIFIER");

    // ...... Operators & punctuation .................................................................................................................................
    IElementType OP_ASSIGN           = new SliccTokenType(":=");
    IElementType OP_EQUALS           = new SliccTokenType("=");
    IElementType OP_SCOPE            = new SliccTokenType("::");  // C++ scope, e.g. std::string
    IElementType OP_COLON            = new SliccTokenType(":");
    IElementType OP_SEMICOLON        = new SliccTokenType(";");
    IElementType OP_COMMA            = new SliccTokenType(",");
    IElementType OP_DOT              = new SliccTokenType(".");
    IElementType OP_LBRACE           = new SliccTokenType("{");
    IElementType OP_RBRACE           = new SliccTokenType("}");
    IElementType OP_LPAREN           = new SliccTokenType("(");
    IElementType OP_RPAREN           = new SliccTokenType(")");
    IElementType OP_LBRACKET         = new SliccTokenType("[");
    IElementType OP_RBRACKET         = new SliccTokenType("]");
    IElementType OP_LANGLE           = new SliccTokenType("<");
    IElementType OP_RANGLE           = new SliccTokenType(">");
    IElementType OP_EQ               = new SliccTokenType("==");
    IElementType OP_NEQ              = new SliccTokenType("!=");
    IElementType OP_LEQ              = new SliccTokenType("<=");
    IElementType OP_GEQ              = new SliccTokenType(">=");
    IElementType OP_AND              = new SliccTokenType("&&");
    IElementType OP_OR               = new SliccTokenType("||");
    IElementType OP_NOT              = new SliccTokenType("!");
    IElementType OP_PLUS             = new SliccTokenType("+");
    IElementType OP_MINUS            = new SliccTokenType("-");
    IElementType OP_STAR             = new SliccTokenType("*");
    IElementType OP_SLASH            = new SliccTokenType("/");
    IElementType OP_PERCENT          = new SliccTokenType("%");
    IElementType OP_AMP              = new SliccTokenType("&");

    // ...... Comments & whitespace .......................................................................................................................................
    IElementType LINE_COMMENT        = new SliccTokenType("LINE_COMMENT");
    IElementType BLOCK_COMMENT       = new SliccTokenType("BLOCK_COMMENT");
    // MUST use the platform singleton so PsiBuilder auto-skips whitespace tokens
    IElementType WHITE_SPACE         = TokenType.WHITE_SPACE;

    // ...... Special .................................................................................................................................................................................
    // MUST use the platform singleton for bad characters
    IElementType BAD_CHARACTER       = TokenType.BAD_CHARACTER;
}
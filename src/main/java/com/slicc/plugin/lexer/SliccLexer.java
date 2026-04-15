package com.slicc.plugin.lexer;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Hand-written lexer for SLICC (.sm / .slicc) files.
 *
 * SLICC syntax is C++-like:
 * ? C-style comments: // and /* *\/
 * ? := for assignment (not =)
 * ? Special top-level keywords: machine, structure, enumeration, action, transition, in_port, ? */
public class SliccLexer extends LexerBase {

    private static final Map<String, IElementType> KEYWORDS = new HashMap<>();

    static {
        KEYWORDS.put("machine",            SliccTokenTypes.KW_MACHINE);
        KEYWORDS.put("structure",          SliccTokenTypes.KW_STRUCTURE);
        KEYWORDS.put("enumeration",        SliccTokenTypes.KW_ENUMERATION);
        KEYWORDS.put("external_type",      SliccTokenTypes.KW_EXTERNAL_TYPE);
        KEYWORDS.put("state_declaration",  SliccTokenTypes.KW_STATE_DECLARATION);
        KEYWORDS.put("in_port",            SliccTokenTypes.KW_IN_PORT);
        KEYWORDS.put("out_port",           SliccTokenTypes.KW_OUT_PORT);
        KEYWORDS.put("action",             SliccTokenTypes.KW_ACTION);
        KEYWORDS.put("transition",         SliccTokenTypes.KW_TRANSITION);
        KEYWORDS.put("include",            SliccTokenTypes.KW_INCLUDE);
        KEYWORDS.put("global",             SliccTokenTypes.KW_GLOBAL);

        KEYWORDS.put("void",              SliccTokenTypes.KW_VOID);
        KEYWORDS.put("bool",              SliccTokenTypes.KW_BOOL);
        KEYWORDS.put("int",               SliccTokenTypes.KW_INT);
        KEYWORDS.put("float",             SliccTokenTypes.KW_FLOAT);
        KEYWORDS.put("string",            SliccTokenTypes.KW_STRING);
        KEYWORDS.put("if",               SliccTokenTypes.KW_IF);
        KEYWORDS.put("else",             SliccTokenTypes.KW_ELSE);
        KEYWORDS.put("for",              SliccTokenTypes.KW_FOR);
        KEYWORDS.put("while",            SliccTokenTypes.KW_WHILE);
        KEYWORDS.put("return",           SliccTokenTypes.KW_RETURN);
        KEYWORDS.put("new",              SliccTokenTypes.KW_NEW);
        KEYWORDS.put("true",             SliccTokenTypes.KW_TRUE);
        KEYWORDS.put("false",            SliccTokenTypes.KW_FALSE);

        KEYWORDS.put("peek",             SliccTokenTypes.KW_PEEK);
        KEYWORDS.put("enqueue",          SliccTokenTypes.KW_ENQUEUE);
        KEYWORDS.put("trigger",          SliccTokenTypes.KW_TRIGGER);
        KEYWORDS.put("stall_and_wait",   SliccTokenTypes.KW_STALL_AND_WAIT);
        KEYWORDS.put("wakeup_port",      SliccTokenTypes.KW_WAKEUP_PORT);
        KEYWORDS.put("check_allocate",   SliccTokenTypes.KW_CHECK_ALLOCATE);
        KEYWORDS.put("check_stop_slots", SliccTokenTypes.KW_CHECK_STOP_SLOTS);
        KEYWORDS.put("error",            SliccTokenTypes.KW_ERROR);
        KEYWORDS.put("assert",           SliccTokenTypes.KW_ASSERT);
        KEYWORDS.put("DPRINTF",          SliccTokenTypes.KW_DPRINTF);
        KEYWORDS.put("DEBUG_EXPR",       SliccTokenTypes.KW_DEBUG_EXPR);
        KEYWORDS.put("set_cache_entry",  SliccTokenTypes.KW_SET_CACHE_ENTRY);
        KEYWORDS.put("unset_cache_entry",SliccTokenTypes.KW_UNSET_CACHE_ENTRY);
        KEYWORDS.put("set_tbe",          SliccTokenTypes.KW_SET_TBE);
        KEYWORDS.put("unset_tbe",        SliccTokenTypes.KW_UNSET_TBE);

        KEYWORDS.put("desc",            SliccTokenTypes.KW_DESC);
        KEYWORDS.put("default",         SliccTokenTypes.KW_DEFAULT);
        KEYWORDS.put("network",         SliccTokenTypes.KW_NETWORK);
        KEYWORDS.put("virtual_network", SliccTokenTypes.KW_VIRTUAL_NETWORK);
        KEYWORDS.put("vnet_type",       SliccTokenTypes.KW_VNET_TYPE);
        KEYWORDS.put("rank",            SliccTokenTypes.KW_RANK);
        KEYWORDS.put("interface",       SliccTokenTypes.KW_INTERFACE);
        KEYWORDS.put("external",        SliccTokenTypes.KW_EXTERNAL);
        KEYWORDS.put("primitive",       SliccTokenTypes.KW_PRIMITIVE);
        KEYWORDS.put("ordered",         SliccTokenTypes.KW_ORDERED);
    }

    private CharSequence myBuffer;
    private int myStart;
    private int myEnd;
    private int myBufferEnd;
    private IElementType myTokenType;

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        myBuffer    = buffer;
        myStart     = startOffset;
        myEnd       = startOffset;
        myBufferEnd = endOffset;
        myTokenType = null;
        advance();
    }

    @Override
    public int getState() { return 0; }

    @Nullable
    @Override
    public IElementType getTokenType() { return myTokenType; }

    @Override
    public int getTokenStart() { return myStart; }

    @Override
    public int getTokenEnd() { return myEnd; }

    @Override
    public void advance() {
        myStart = myEnd;
        if (myStart >= myBufferEnd) {
            myTokenType = null;
            return;
        }
        myTokenType = nextToken();
    }

    @NotNull
    @Override
    public CharSequence getBufferSequence() { return myBuffer; }

    @Override
    public int getBufferEnd() { return myBufferEnd; }

    // ...............................................................................................................................................................................................................
    private IElementType nextToken() {
        int pos = myStart;
        char c = myBuffer.charAt(pos);

        // Whitespace
        if (Character.isWhitespace(c)) {
            while (pos < myBufferEnd && Character.isWhitespace(myBuffer.charAt(pos))) pos++;
            myEnd = pos;
            return SliccTokenTypes.WHITE_SPACE;
        }

        // Line comment //
        if (c == '/' && pos + 1 < myBufferEnd && myBuffer.charAt(pos + 1) == '/') {
            pos += 2;
            while (pos < myBufferEnd && myBuffer.charAt(pos) != '\n') pos++;
            myEnd = pos;
            return SliccTokenTypes.LINE_COMMENT;
        }

        // Block comment /* */
        if (c == '/' && pos + 1 < myBufferEnd && myBuffer.charAt(pos + 1) == '*') {
            pos += 2;
            while (pos + 1 < myBufferEnd) {
                if (myBuffer.charAt(pos) == '*' && myBuffer.charAt(pos + 1) == '/') {
                    pos += 2;
                    break;
                }
                pos++;
            }
            myEnd = pos;
            return SliccTokenTypes.BLOCK_COMMENT;
        }

        // String literal
        if (c == '"') {
            pos++;
            while (pos < myBufferEnd && myBuffer.charAt(pos) != '"') {
                if (myBuffer.charAt(pos) == '\\') pos++; // escape
                pos++;
            }
            if (pos < myBufferEnd) pos++; // closing "
            myEnd = pos;
            return SliccTokenTypes.STRING_LITERAL;
        }

        // Number literal
        if (Character.isDigit(c) || (c == '-' && pos + 1 < myBufferEnd && Character.isDigit(myBuffer.charAt(pos + 1)))) {
            boolean isFloat = false;
            pos++;
            while (pos < myBufferEnd && (Character.isDigit(myBuffer.charAt(pos)) || myBuffer.charAt(pos) == '.')) {
                if (myBuffer.charAt(pos) == '.') isFloat = true;
                pos++;
            }
            // hex 0x...
            if (!isFloat && myEnd == myStart + 1 && c == '0' && pos < myBufferEnd && (myBuffer.charAt(pos) == 'x' || myBuffer.charAt(pos) == 'X')) {
                pos++;
                while (pos < myBufferEnd && isHexDigit(myBuffer.charAt(pos))) pos++;
            }
            myEnd = pos;
            return isFloat ? SliccTokenTypes.FLOAT_LITERAL : SliccTokenTypes.INTEGER_LITERAL;
        }

        // Identifier / keyword
        if (Character.isLetter(c) || c == '_') {
            pos++;
            while (pos < myBufferEnd && (Character.isLetterOrDigit(myBuffer.charAt(pos)) || myBuffer.charAt(pos) == '_')) {
                pos++;
            }
            myEnd = pos;
            String text = myBuffer.subSequence(myStart, myEnd).toString();
            return KEYWORDS.getOrDefault(text, SliccTokenTypes.IDENTIFIER);
        }

        // Two-character operators
        if (pos + 1 < myBufferEnd) {
            char c2 = myBuffer.charAt(pos + 1);
            if (c == ':' && c2 == '=') { myEnd = pos + 2; return SliccTokenTypes.OP_ASSIGN; }
            if (c == '=' && c2 == '=') { myEnd = pos + 2; return SliccTokenTypes.OP_EQ; }
            if (c == '!' && c2 == '=') { myEnd = pos + 2; return SliccTokenTypes.OP_NEQ; }
            if (c == '<' && c2 == '=') { myEnd = pos + 2; return SliccTokenTypes.OP_LEQ; }
            if (c == '>' && c2 == '=') { myEnd = pos + 2; return SliccTokenTypes.OP_GEQ; }
            if (c == '&' && c2 == '&') { myEnd = pos + 2; return SliccTokenTypes.OP_AND; }
            if (c == '|' && c2 == '|') { myEnd = pos + 2; return SliccTokenTypes.OP_OR; }
        }

        // Single-character tokens
        myEnd = pos + 1;
        switch (c) {
            case ':': return SliccTokenTypes.OP_COLON;
            case ';': return SliccTokenTypes.OP_SEMICOLON;
            case ',': return SliccTokenTypes.OP_COMMA;
            case '.': return SliccTokenTypes.OP_DOT;
            case '{': return SliccTokenTypes.OP_LBRACE;
            case '}': return SliccTokenTypes.OP_RBRACE;
            case '(': return SliccTokenTypes.OP_LPAREN;
            case ')': return SliccTokenTypes.OP_RPAREN;
            case '[': return SliccTokenTypes.OP_LBRACKET;
            case ']': return SliccTokenTypes.OP_RBRACKET;
            case '<': return SliccTokenTypes.OP_LANGLE;
            case '>': return SliccTokenTypes.OP_RANGLE;
            case '!': return SliccTokenTypes.OP_NOT;
            case '+': return SliccTokenTypes.OP_PLUS;
            case '-': return SliccTokenTypes.OP_MINUS;
            case '*': return SliccTokenTypes.OP_STAR;
            case '/': return SliccTokenTypes.OP_SLASH;
            case '%': return SliccTokenTypes.OP_PERCENT;
            case '&': return SliccTokenTypes.OP_AMP;
            default:  return SliccTokenTypes.BAD_CHARACTER;
        }
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}

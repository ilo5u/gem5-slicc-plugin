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
        KEYWORDS.put("protocol",           SliccTokenTypes.KW_PROTOCOL);
        KEYWORDS.put("global",             SliccTokenTypes.KW_GLOBAL);

        KEYWORDS.put("void",              SliccTokenTypes.KW_VOID);
        KEYWORDS.put("if",               SliccTokenTypes.KW_IF);
        KEYWORDS.put("else",             SliccTokenTypes.KW_ELSE);
        KEYWORDS.put("return",           SliccTokenTypes.KW_RETURN);
        KEYWORDS.put("static_cast",      SliccTokenTypes.KW_STATIC_CAST);

        KEYWORDS.put("peek",             SliccTokenTypes.KW_PEEK);
        KEYWORDS.put("new",              SliccTokenTypes.KW_NEW);
        KEYWORDS.put("enqueue",          SliccTokenTypes.KW_ENQUEUE);
        KEYWORDS.put("dequeue",          SliccTokenTypes.KW_DEQUEUE);
        KEYWORDS.put("trigger",          SliccTokenTypes.KW_TRIGGER);
        KEYWORDS.put("stall_and_wait",   SliccTokenTypes.KW_STALL_AND_WAIT);
        KEYWORDS.put("wakeup_port",      SliccTokenTypes.KW_WAKEUP_PORT);
        KEYWORDS.put("check_allocate",   SliccTokenTypes.KW_CHECK_ALLOCATE);
        KEYWORDS.put("check_next_cycle", SliccTokenTypes.KW_CHECK_NEXT_CYCLE);
        KEYWORDS.put("check_stop_slots", SliccTokenTypes.KW_CHECK_STOP_SLOTS);
        KEYWORDS.put("check_on_cache_probe", SliccTokenTypes.KW_CHECK_ON_CACHE_PROBE);
        KEYWORDS.put("OOD",              SliccTokenTypes.KW_OOD);
        KEYWORDS.put("defer_enqueueing", SliccTokenTypes.KW_DEFER_ENQUEUEING);
        for (String name : SliccBuiltinFunctions.names()) {
            KEYWORDS.put(name, SliccBuiltinFunctions.tokenType(name));
        }
        KEYWORDS.put("is_valid",         SliccTokenTypes.KW_IS_VALID);
        KEYWORDS.put("is_invalid",       SliccTokenTypes.KW_IS_INVALID);
        KEYWORDS.put("isReady",          SliccTokenTypes.KW_IS_READY);

        KEYWORDS.put("desc",            SliccTokenTypes.KW_DESC);
        KEYWORDS.put("main",            SliccTokenTypes.KW_MAIN);
        KEYWORDS.put("block_on",        SliccTokenTypes.KW_BLOCK_ON);
        KEYWORDS.put("format",          SliccTokenTypes.KW_FORMAT);
        KEYWORDS.put("in_trans",        SliccTokenTypes.KW_IN_TRANS);
        KEYWORDS.put("out_trans",       SliccTokenTypes.KW_OUT_TRANS);
        KEYWORDS.put("default",         SliccTokenTypes.KW_DEFAULT);
        KEYWORDS.put("network",         SliccTokenTypes.KW_NETWORK);
        KEYWORDS.put("virtual_network", SliccTokenTypes.KW_VIRTUAL_NETWORK);
        KEYWORDS.put("vnet_type",       SliccTokenTypes.KW_VNET_TYPE);
        KEYWORDS.put("rank",            SliccTokenTypes.KW_RANK);
        KEYWORDS.put("return_by_pointer", SliccTokenTypes.KW_RETURN_BY_POINTER);
        KEYWORDS.put("interface",       SliccTokenTypes.KW_INTERFACE);
        KEYWORDS.put("external",        SliccTokenTypes.KW_EXTERNAL);
        KEYWORDS.put("primitive",       SliccTokenTypes.KW_PRIMITIVE);
        KEYWORDS.put("ordered",         SliccTokenTypes.KW_ORDERED);
        KEYWORDS.put("template",        SliccTokenTypes.KW_TEMPLATE);
        KEYWORDS.put("constructor",     SliccTokenTypes.KW_CONSTRUCTOR);
        KEYWORDS.put("non_obj",         SliccTokenTypes.KW_NON_OBJ);
        KEYWORDS.put("implicit_ctor",   SliccTokenTypes.KW_IMPLICIT_CTOR);
        KEYWORDS.put("buffer",          SliccTokenTypes.KW_BUFFER);
        KEYWORDS.put("inport",          SliccTokenTypes.KW_INPORT);
        KEYWORDS.put("outport",         SliccTokenTypes.KW_OUTPORT);
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

        // Single-quoted character/label literal: 'pV', 'sM', etc.
        if (c == '\'') {
            pos++;
            while (pos < myBufferEnd && myBuffer.charAt(pos) != '\'') {
                if (myBuffer.charAt(pos) == '\\') pos++; // escape
                pos++;
            }
            if (pos < myBufferEnd) pos++; // closing '
            myEnd = pos;
            return SliccTokenTypes.STRING_LITERAL; // treat same as string literal
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
            if (c == ':' && c2 == ':') { myEnd = pos + 2; return SliccTokenTypes.OP_SCOPE; }
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
            case '=': return SliccTokenTypes.OP_EQUALS;
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

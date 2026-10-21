package com.slicc.plugin.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LightPsiParser;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import com.slicc.plugin.lexer.SliccBuiltinFunctions;
import com.slicc.plugin.lexer.SliccTokenTypes;
import com.slicc.plugin.psi.SliccTypes;
import org.jetbrains.annotations.NotNull;

/**
 * Recursive-descent parser for SLICC.
 *
 * Grammar summary (informal):
 *   file          ::= topDecl*
 *   topDecl       ::= machineDecl | structureDecl | enumerationDecl
 *                   | externalTypeDecl | funcDecl | includeDecl | stateDecl
 *   machineDecl   ::= 'machine' '(' id ':' id ',' string ')' ':' memberList '{' machineBody '}'
 *   structureDecl ::= 'structure' '(' id attrList ')' '{' fieldList '}'
 *   enumerationDecl ::= 'enumeration' '(' id attrList ')' '{' enumMemberList '}'
 *   actionDecl    ::= 'action' '(' id ',' string attrList ')' '{' stmt* '}'
 *   transitionDecl::= 'transition' '(' stateList ',' eventList [',' stateList] ')' [resourceList] '{' actionCallList '}'
 *   funcDecl      ::= type id '(' paramList ')' '{' stmt* '}'
 *   in_portDecl   ::= 'in_port' '(' id ',' type ',' id attrList ')' '{' stmt* '}'
 *   out_portDecl  ::= 'out_port' '(' id ',' type ',' id ')' ';'
 */
public class SliccParser implements PsiParser, LightPsiParser {


    @NotNull
    @Override
    public ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder builder) {
        parseLight(root, builder);
        return builder.getTreeBuilt();
    }

    @Override
    public void parseLight(IElementType root, PsiBuilder builder) {
        builder.setDebugMode(false);
        Marker file = builder.mark();
        parseFile(builder);
        file.done(root);
    }

    // ......... File ................................................................................................................................................................................................

    private void parseFile(PsiBuilder b) {
        while (!b.eof()) {
            if (!parseTopLevelDecl(b)) {
                b.advanceLexer(); // recover
            }
        }
    }

    private boolean parseTopLevelDecl(PsiBuilder b) {
        IElementType tt = b.getTokenType();
        if (tt == SliccTokenTypes.KW_PROTOCOL)      return parseProtocolDecl(b);
        if (tt == SliccTokenTypes.KW_MACHINE)       return parseMachineDecl(b);
        if (tt == SliccTokenTypes.KW_STRUCTURE)     return parseStructureDecl(b);
        if (tt == SliccTokenTypes.KW_ENUMERATION)   return parseEnumerationDecl(b);
        if (tt == SliccTokenTypes.KW_EXTERNAL_TYPE) return parseExternalTypeDecl(b);
        if (tt == SliccTokenTypes.KW_INCLUDE)       return parseIncludeDecl(b);
        if (tt == SliccTokenTypes.KW_STATE_DECLARATION) return parseStateDecl(b);
        if (tt == SliccTokenTypes.KW_GLOBAL)        return parseStructureDecl(b);
        if (tt == SliccTokenTypes.KW_IN_PORT)       return parseInPortDecl(b);
        if (tt == SliccTokenTypes.KW_OUT_PORT)      return parseOutPortDecl(b);
        if (tt == SliccTokenTypes.KW_ACTION)        return parseActionDecl(b);
        if (tt == SliccTokenTypes.KW_TRANSITION)    return parseTransitionDecl(b);
        // free-standing function
        if (isFunctionStart(b))                     return parseFunctionDecl(b);
        // top-level field declaration (e.g. MachineID machineID; in RubySlicc_Defines.sm)
        if (isFieldStart(b))                        return parseFieldOrVar(b);
        return false;
    }

    // ......... protocol "Name" ; (root scope declaration in .slicc files) .............................

    private boolean parseProtocolDecl(PsiBuilder b) {
        Marker m = b.mark();
        b.advanceLexer(); // 'protocol'
        if (b.getTokenType() == SliccTokenTypes.STRING_LITERAL) b.advanceLexer();
        if (b.getTokenType() == SliccTokenTypes.OP_SEMICOLON)   b.advanceLexer();
        m.done(SliccTypes.INCLUDE_DECL); // reuse INCLUDE_DECL node type for simplicity
        return true;
    }

    // ......... machine(...) : ? { ? } ..............................................................................................................................

    private boolean parseMachineDecl(PsiBuilder b) {
        Marker m = b.mark();
        b.advanceLexer(); // 'machine'
        expectToken(b, SliccTokenTypes.OP_LPAREN, "'('");
        parseMachineHeader(b);
        expectToken(b, SliccTokenTypes.OP_RPAREN, "')'");
        // optional ':' member-var list
        if (b.getTokenType() == SliccTokenTypes.OP_COLON) {
            b.advanceLexer();
            parseMachineParams(b);
        }
        if (b.getTokenType() == SliccTokenTypes.OP_LBRACE) {
            parseMachineBody(b);
        }
        m.done(SliccTypes.MACHINE_DECL);
        return true;
    }

    private void parseMachineHeader(PsiBuilder b) {
        // Handles:  MachineType:SpPCache , "desc"
        // The entire "MachineType:SpPCache" is the machine type reference.
        // We consume: ident [ ':' ident ] [ ',' string ]
        parseIdent(b); // e.g. MachineType
        if (b.getTokenType() == SliccTokenTypes.OP_COLON) {
            b.advanceLexer(); // ':'
            parseIdent(b);    // e.g. SpPCache
        }
        if (b.getTokenType() == SliccTokenTypes.OP_COMMA) {
            b.advanceLexer();
            if (b.getTokenType() == SliccTokenTypes.STRING_LITERAL) b.advanceLexer();
        }
    }

    private void parseMachineParams(PsiBuilder b) {
        // The machine params section (between ':' and '{') can contain:
        // - field/variable declarations:   Cycles req_latency := 2;
        // - structure declarations:        structure(SpeculativeController, ...) { ... }
        // - enumeration declarations:      enumeration(Channel, ...) { ... }
        // - include directives:            include "file.sm";
        while (!b.eof() && b.getTokenType() != SliccTokenTypes.OP_LBRACE) {
            int before = b.getCurrentOffset();
            IElementType tt = b.getTokenType();
            if (tt == SliccTokenTypes.KW_STRUCTURE || tt == SliccTokenTypes.KW_GLOBAL) {
                parseStructureDecl(b);
            } else if (tt == SliccTokenTypes.KW_ENUMERATION) {
                parseEnumerationDecl(b);
            } else if (tt == SliccTokenTypes.KW_EXTERNAL_TYPE) {
                parseExternalTypeDecl(b);
            } else if (tt == SliccTokenTypes.KW_INCLUDE) {
                parseIncludeDecl(b);
            } else if (!parseFieldOrVar(b)) {
                b.advanceLexer();
            }
            if (b.getCurrentOffset() == before) b.advanceLexer(); // safety
        }
    }

    private void parseMachineBody(PsiBuilder b) {
        expectToken(b, SliccTokenTypes.OP_LBRACE, "'{'");
        while (!b.eof() && b.getTokenType() != SliccTokenTypes.OP_RBRACE) {
            int before = b.getCurrentOffset();
            if (!parseMachineMember(b)) b.advanceLexer();
            if (b.getCurrentOffset() == before) b.advanceLexer(); // safety
        }
        expectToken(b, SliccTokenTypes.OP_RBRACE, "'}'");
    }

    private boolean parseMachineMember(PsiBuilder b) {
        IElementType tt = b.getTokenType();
        if (tt == SliccTokenTypes.KW_STATE_DECLARATION) return parseStateDecl(b);
        if (tt == SliccTokenTypes.KW_ENUMERATION)       return parseEnumerationDecl(b);
        if (tt == SliccTokenTypes.KW_STRUCTURE || tt == SliccTokenTypes.KW_GLOBAL) return parseStructureDecl(b);
        if (tt == SliccTokenTypes.KW_IN_PORT)           return parseInPortDecl(b);
        if (tt == SliccTokenTypes.KW_OUT_PORT)          return parseOutPortDecl(b);
        if (tt == SliccTokenTypes.KW_ACTION)            return parseActionDecl(b);
        if (tt == SliccTokenTypes.KW_TRANSITION)        return parseTransitionDecl(b);
        if (tt == SliccTokenTypes.KW_INCLUDE)           return parseIncludeDecl(b);
        if (isFunctionStart(b))                         return parseFunctionDecl(b);
        if (isFieldStart(b))                            return parseFieldOrVar(b);
        return false;
    }

    // ......... structure(...) { ? } ..........................................................................................................................................

    private boolean parseStructureDecl(PsiBuilder b) {
        Marker m = b.mark();
        b.advanceLexer(); // 'structure' or 'global'
        expectToken(b, SliccTokenTypes.OP_LPAREN, "'('");
        parseIdent(b);
        skipOptionalPositionalString(b);
        parseAttrList(b);
        expectToken(b, SliccTokenTypes.OP_RPAREN, "')'");
        if (b.getTokenType() == SliccTokenTypes.OP_LBRACE) {
            b.advanceLexer();
            while (!b.eof() && b.getTokenType() != SliccTokenTypes.OP_RBRACE) {
                int before = b.getCurrentOffset();
                if (!parseStructureMember(b)) b.advanceLexer();
                if (b.getCurrentOffset() == before) b.advanceLexer(); // safety
            }
            expectToken(b, SliccTokenTypes.OP_RBRACE, "'}'");
        }
        m.done(SliccTypes.STRUCTURE_DECL);
        return true;
    }

    private boolean parseStructureMember(PsiBuilder b) {
        // method declaration or field
        if (isFunctionStart(b)) return parseFunctionDecl(b);
        return parseFieldOrVar(b);
    }

    // ......... enumeration(...) { ? } ....................................................................................................................................

    private boolean parseEnumerationDecl(PsiBuilder b) {
        Marker m = b.mark();
        b.advanceLexer(); // 'enumeration'
        expectToken(b, SliccTokenTypes.OP_LPAREN, "'('");
        parseIdent(b);
        // Optional positional string arg before attr list:
        // enumeration(MaskPredictorType, "MaskPredictorType_Undefined", desc="...")
        skipOptionalPositionalString(b);
        parseAttrList(b);
        expectToken(b, SliccTokenTypes.OP_RPAREN, "')'");
        expectToken(b, SliccTokenTypes.OP_LBRACE, "'{'");
        while (!b.eof() && b.getTokenType() != SliccTokenTypes.OP_RBRACE) {
            int before = b.getCurrentOffset();
            parseEnumMember(b);
            if (b.getCurrentOffset() == before) b.advanceLexer(); // safety: must always progress
        }
        expectToken(b, SliccTokenTypes.OP_RBRACE, "'}'");
        m.done(SliccTypes.ENUMERATION_DECL);
        return true;
    }

    private void parseEnumMember(PsiBuilder b) {
        // Member name is an IDENTIFIER (includes 'null' which lexes as IDENTIFIER in SLICC)
        if (b.getTokenType() != SliccTokenTypes.IDENTIFIER) {
            b.advanceLexer(); // skip unknown token, progress guard
            return;
        }
        Marker m = b.mark();
        b.advanceLexer();   // member name
        parseAttrList(b);   // optional , AccessPermission:X , desc = "..."
        if (b.getTokenType() == SliccTokenTypes.OP_SEMICOLON) b.advanceLexer();
        m.done(SliccTypes.ENUM_MEMBER);
    }

    // ......... state_declaration(...) { ? } ..................................................................................................................

    private boolean parseStateDecl(PsiBuilder b) {
        Marker m = b.mark();
        b.advanceLexer(); // 'state_declaration'
        expectToken(b, SliccTokenTypes.OP_LPAREN, "'('");
        parseIdent(b);
        skipOptionalPositionalString(b);
        parseAttrList(b);
        expectToken(b, SliccTokenTypes.OP_RPAREN, "')'");
        expectToken(b, SliccTokenTypes.OP_LBRACE, "'{'");
        while (!b.eof() && b.getTokenType() != SliccTokenTypes.OP_RBRACE) {
            int before = b.getCurrentOffset();
            parseEnumMember(b);
            if (b.getCurrentOffset() == before) b.advanceLexer(); // safety
        }
        expectToken(b, SliccTokenTypes.OP_RBRACE, "'}'");
        m.done(SliccTypes.STATE_DECL);
        return true;
    }

    // ......... external_type(...) { ? } ..............................................................................................................................

    private boolean parseExternalTypeDecl(PsiBuilder b) {
        Marker m = b.mark();
        b.advanceLexer(); // 'external_type'
        expectToken(b, SliccTokenTypes.OP_LPAREN, "'('");
        // Type name: may be a keyword (int, bool, string) or a scoped C++ name (std::string)
        Marker nameM = b.mark();
        parseTypeName(b);
        nameM.done(SliccTypes.REFERENCE); // wrap so getNameIdentifier() can find it
        parseAttrList(b);
        expectToken(b, SliccTokenTypes.OP_RPAREN, "')'");
        // optional method body
        if (b.getTokenType() == SliccTokenTypes.OP_LBRACE) {
            b.advanceLexer();
            while (!b.eof() && b.getTokenType() != SliccTokenTypes.OP_RBRACE) {
                int before = b.getCurrentOffset();
                if (!parseFunctionDecl(b)) b.advanceLexer();
                if (b.getCurrentOffset() == before) b.advanceLexer(); // safety
            }
            expectToken(b, SliccTokenTypes.OP_RBRACE, "'}'");
        }
        m.done(SliccTypes.EXTERNAL_TYPE_DECL);
        return true;
    }

    // ......... include "file" ; ............................................................................................................................................................

    private boolean parseIncludeDecl(PsiBuilder b) {
        Marker m = b.mark();
        b.advanceLexer(); // 'include'
        if (b.getTokenType() == SliccTokenTypes.STRING_LITERAL) b.advanceLexer();
        if (b.getTokenType() == SliccTokenTypes.OP_SEMICOLON)   b.advanceLexer();
        m.done(SliccTypes.INCLUDE_DECL);
        return true;
    }

    // ......... action(Name, "shortname", ...) { stmt* } ....................................................................................

    private boolean parseActionDecl(PsiBuilder b) {
        Marker m = b.mark();
        b.advanceLexer(); // 'action'
        expectToken(b, SliccTokenTypes.OP_LPAREN, "'('");
        parseIdent(b); // action name
        // Optional short-name: either a string literal "xx" or single-quoted 'xx'
        // followed by optional attribute list.
        // We must NOT consume the ',' if it is the start of an attribute key=value pair.
        if (b.getTokenType() == SliccTokenTypes.OP_COMMA) {
            // Peek: if next after ',' is a string literal, it's the short name
            Marker peek = b.mark();
            b.advanceLexer(); // consume ','
            if (b.getTokenType() == SliccTokenTypes.STRING_LITERAL) {
                b.advanceLexer(); // consume short name
                peek.drop();
            } else {
                // Not a short name — roll back so parseAttrList can see the ','
                peek.rollbackTo();
            }
        }
        parseAttrList(b);
        expectToken(b, SliccTokenTypes.OP_RPAREN, "')'");
        parseBlock(b);
        m.done(SliccTypes.ACTION_DECL);
        return true;
    }

    // ......... transition(states, events [, nextState]) [resources] { actions } ............

    private boolean parseTransitionDecl(PsiBuilder b) {
        Marker m = b.mark();
        b.advanceLexer(); // 'transition'
        expectToken(b, SliccTokenTypes.OP_LPAREN, "'('");
        parseTransitionStateList(b);
        expectToken(b, SliccTokenTypes.OP_COMMA, "','");
        parseTransitionEventList(b);
        // optional next state
        if (b.getTokenType() == SliccTokenTypes.OP_COMMA) {
            b.advanceLexer();
            parseTransitionStateList(b);
        }
        expectToken(b, SliccTokenTypes.OP_RPAREN, "')'");
        // optional resource list {TagArrayRead, ...}
        if (b.getTokenType() == SliccTokenTypes.OP_LBRACE) {
            // peek ahead: if first token after { is an identifier followed by } or ,
            // and no statements, treat as resource list
            parseResourceListOrBlock(b, m);
            return true;
        }
        m.done(SliccTypes.TRANSITION_DECL);
        return true;
    }

    private void parseResourceListOrBlock(PsiBuilder b, Marker transitionMarker) {
        // Check if this brace block is a resource list (identifiers only, no statements)
        // We use a heuristic: consume it as block, mark as transition
        Marker resMarker = b.mark();
        b.advanceLexer(); // '{'
        // collect resource list items
        boolean isResourceList = true;
        int depth = 1;
        // lookahead: check whether contents look like action calls or resource identifiers
        // Resource list = { Ident [, Ident]* } with no semicolons or := inside
        // We use simple heuristic: if there's a ';' or ':=' it's a block, else resource list
        PsiBuilder.Marker lookahead = b.mark();
        while (!b.eof() && depth > 0) {
            IElementType t = b.getTokenType();
            if (t == SliccTokenTypes.OP_LBRACE) depth++;
            if (t == SliccTokenTypes.OP_RBRACE) { depth--; if (depth == 0) break; }
            if (t == SliccTokenTypes.OP_SEMICOLON || t == SliccTokenTypes.OP_ASSIGN) {
                isResourceList = false;
                break;
            }
            b.advanceLexer();
        }
        lookahead.rollbackTo();

        if (isResourceList) {
            // parse resource list
            while (!b.eof() && b.getTokenType() != SliccTokenTypes.OP_RBRACE) {
                if (b.getTokenType() == SliccTokenTypes.IDENTIFIER) b.advanceLexer();
                else if (b.getTokenType() == SliccTokenTypes.OP_COMMA) b.advanceLexer();
                else b.advanceLexer();
            }
            expectToken(b, SliccTokenTypes.OP_RBRACE, "'}'");
            resMarker.done(SliccTypes.RESOURCE_LIST);
            // now expect the actual action block
            if (b.getTokenType() == SliccTokenTypes.OP_LBRACE) {
                parseTransitionActionBlock(b);
            }
        } else {
            // it's the action block directly
            while (!b.eof() && b.getTokenType() != SliccTokenTypes.OP_RBRACE) {
                int before = b.getCurrentOffset();
                if (!parseStatement(b)) b.advanceLexer();
                if (b.getCurrentOffset() == before) b.advanceLexer(); // safety
            }
            expectToken(b, SliccTokenTypes.OP_RBRACE, "'}'");
            resMarker.done(SliccTypes.TRANSITION_ACTIONS);
        }
        transitionMarker.done(SliccTypes.TRANSITION_DECL);
    }

    private void parseTransitionActionBlock(PsiBuilder b) {
        Marker m = b.mark();
        b.advanceLexer(); // '{'
        while (!b.eof() && b.getTokenType() != SliccTokenTypes.OP_RBRACE) {
            if (b.getTokenType() == SliccTokenTypes.IDENTIFIER) {
                b.advanceLexer();
                if (b.getTokenType() == SliccTokenTypes.OP_SEMICOLON) b.advanceLexer();
            } else {
                b.advanceLexer();
            }
        }
        expectToken(b, SliccTokenTypes.OP_RBRACE, "'}'");
        m.done(SliccTypes.TRANSITION_ACTIONS);
    }

    private void parseTransitionStateList(PsiBuilder b) {
        Marker m = b.mark();
        if (b.getTokenType() == SliccTokenTypes.OP_LBRACE) {
            b.advanceLexer();
            while (!b.eof() && b.getTokenType() != SliccTokenTypes.OP_RBRACE) {
                if (b.getTokenType() == SliccTokenTypes.IDENTIFIER) b.advanceLexer();
                else if (b.getTokenType() == SliccTokenTypes.OP_COMMA) b.advanceLexer();
                else b.advanceLexer();
            }
            expectToken(b, SliccTokenTypes.OP_RBRACE, "'}'");
        } else {
            parseIdent(b);
        }
        m.done(SliccTypes.TRANSITION_STATES);
    }

    private void parseTransitionEventList(PsiBuilder b) {
        Marker m = b.mark();
        if (b.getTokenType() == SliccTokenTypes.OP_LBRACE) {
            b.advanceLexer();
            while (!b.eof() && b.getTokenType() != SliccTokenTypes.OP_RBRACE) {
                if (b.getTokenType() == SliccTokenTypes.IDENTIFIER) b.advanceLexer();
                else if (b.getTokenType() == SliccTokenTypes.OP_COMMA) b.advanceLexer();
                else b.advanceLexer();
            }
            expectToken(b, SliccTokenTypes.OP_RBRACE, "'}'");
        } else {
            parseIdent(b);
        }
        m.done(SliccTypes.TRANSITION_EVENTS);
    }

    // ......... in_port(name, Type, buffer, ...) { stmt* } ..............................................................................

    private boolean parseInPortDecl(PsiBuilder b) {
        Marker m = b.mark();
        b.advanceLexer(); // 'in_port'
        expectToken(b, SliccTokenTypes.OP_LPAREN, "'('");
        parseIdent(b);
        if (b.getTokenType() == SliccTokenTypes.OP_COMMA) { b.advanceLexer(); parseTypeOrIdent(b); }
        if (b.getTokenType() == SliccTokenTypes.OP_COMMA) { b.advanceLexer(); parseIdent(b); }
        parseAttrList(b);
        expectToken(b, SliccTokenTypes.OP_RPAREN, "')'");
        parseBlock(b);
        m.done(SliccTypes.IN_PORT_DECL);
        return true;
    }

    // ......... out_port(name, Type, buffer) ; ...............................................................................................................

    private boolean parseOutPortDecl(PsiBuilder b) {
        Marker m = b.mark();
        b.advanceLexer(); // 'out_port'
        expectToken(b, SliccTokenTypes.OP_LPAREN, "'('");
        parseIdent(b);
        if (b.getTokenType() == SliccTokenTypes.OP_COMMA) { b.advanceLexer(); parseTypeOrIdent(b); }
        if (b.getTokenType() == SliccTokenTypes.OP_COMMA) { b.advanceLexer(); parseIdent(b); }
        parseAttrList(b);
        expectToken(b, SliccTokenTypes.OP_RPAREN, "')'");
        if (b.getTokenType() == SliccTokenTypes.OP_SEMICOLON) b.advanceLexer();
        m.done(SliccTypes.OUT_PORT_DECL);
        return true;
    }

    // ......... Function declaration: retType name(params) { ? } ...................................................

    private boolean parseFunctionDecl(PsiBuilder b) {
        Marker m = b.mark();
        parseTypeOrIdent(b); // return type (wrapped in TYPE_REF)
        // Mark the function name separately so getNameIdentifier() finds it
        Marker nameMark = b.mark();
        if (b.getTokenType() == SliccTokenTypes.IDENTIFIER) {
            b.advanceLexer();
        }
        nameMark.done(SliccTypes.REFERENCE); // use REFERENCE as the name-node marker
        expectToken(b, SliccTokenTypes.OP_LPAREN, "'('");
        parseParamList(b);
        expectToken(b, SliccTokenTypes.OP_RPAREN, "')'");
        parseAttrList(b); // optional , return_by_pointer = "yes" etc.
        if (b.getTokenType() == SliccTokenTypes.OP_SEMICOLON) {
            b.advanceLexer(); // forward declaration
        } else {
            parseBlock(b);
        }
        m.done(SliccTypes.FUNCTION_DECL);
        return true;
    }

    private void parseParamList(PsiBuilder b) {
        Marker m = b.mark();
        while (!b.eof() && b.getTokenType() != SliccTokenTypes.OP_RPAREN) {
            int before = b.getCurrentOffset();
            Marker p = b.mark();
            parseTypeOrIdent(b);
            if (b.getTokenType() == SliccTokenTypes.IDENTIFIER) parseIdent(b);
            p.done(SliccTypes.FIELD_DECL); // params as FIELD_DECL so searchLocalScope finds them
            if (b.getTokenType() == SliccTokenTypes.OP_COMMA) b.advanceLexer();
            if (b.getCurrentOffset() == before) b.advanceLexer(); // safety
        }
        m.done(SliccTypes.PARAM_LIST);
    }

    // ......... Attribute list: , key = "value" ...............................................................................................................

    private void parseAttrList(PsiBuilder b) {
        Marker m = b.mark();
        while (b.getTokenType() == SliccTokenTypes.OP_COMMA) {
            b.advanceLexer(); // consume ','
            Marker attr = b.mark();
            IElementType tt = b.getTokenType();
            if (tt == SliccTokenTypes.IDENTIFIER || isAttrKeyword(tt)) {
                b.advanceLexer(); // key (or type like AccessPermission)
                if (b.getTokenType() == SliccTokenTypes.OP_COLON) {
                    // Type:Value form, e.g. AccessPermission:NotPresent
                    b.advanceLexer(); // ':'
                    if (b.getTokenType() == SliccTokenTypes.IDENTIFIER) b.advanceLexer();
                } else if (b.getTokenType() == SliccTokenTypes.OP_EQUALS) {
                    b.advanceLexer();
                    parseAttrValue(b);
                } else {
                }
                attr.done(SliccTypes.ATTRIBUTE);
            } else {
                // unexpected token after comma - stop attribute list
                attr.drop();
                break;
            }
        }
        m.done(SliccTypes.ATTRIBUTE_LIST);
    }

    private void parseAttrValue(PsiBuilder b) {
        IElementType t = b.getTokenType();
        if (t == SliccTokenTypes.STRING_LITERAL || t == SliccTokenTypes.INTEGER_LITERAL
                || t == SliccTokenTypes.FLOAT_LITERAL) {
            b.advanceLexer();
        } else if (t == SliccTokenTypes.IDENTIFIER || isTypeKeyword(t)) {
            // Could be a plain identifier, or a qualified value like AccessPermission:Read_Only
            b.advanceLexer();
            if (b.getTokenType() == SliccTokenTypes.OP_COLON) {
                b.advanceLexer(); // ':'
                if (b.getTokenType() == SliccTokenTypes.IDENTIFIER) b.advanceLexer();
            }
        } else {
            // fallback: skip one token so we always progress
            if (!b.eof()) b.advanceLexer();
        }
    }

    // ......... Block { stmt* } ...............................................................................................................................................................

    private void parseBlock(PsiBuilder b) {
        if (b.getTokenType() != SliccTokenTypes.OP_LBRACE) return;
        Marker m = b.mark();
        b.advanceLexer();
        while (!b.eof() && b.getTokenType() != SliccTokenTypes.OP_RBRACE) {
            int before = b.getCurrentOffset();
            if (!parseStatement(b)) b.advanceLexer();
            if (b.getCurrentOffset() == before) b.advanceLexer(); // safety
        }
        expectToken(b, SliccTokenTypes.OP_RBRACE, "'}'");
        m.done(SliccTypes.BLOCK);
    }

    // ......... Statement .................................................................................................................................................................................

    private boolean parseStatement(PsiBuilder b) {
        IElementType tt = b.getTokenType();
        // if / else
        if (tt == SliccTokenTypes.KW_IF) { parseIfStmt(b); return true; }
        if (tt == SliccTokenTypes.KW_ELSE) { b.advanceLexer(); parseBlock(b); return true; }
        if (tt == SliccTokenTypes.KW_RETURN) { parseReturnStmt(b); return true; }
        // peek / enqueue / stall_and_wait / check_allocate
        if (tt == SliccTokenTypes.KW_PEEK
                || tt == SliccTokenTypes.KW_ENQUEUE
                || tt == SliccTokenTypes.KW_DEQUEUE
                || tt == SliccTokenTypes.KW_STALL_AND_WAIT
                || tt == SliccTokenTypes.KW_CHECK_ALLOCATE
                || tt == SliccTokenTypes.KW_WAKEUP_PORT
                || tt == SliccTokenTypes.KW_CHECK_NEXT_CYCLE
                || tt == SliccTokenTypes.KW_CHECK_STOP_SLOTS
                || tt == SliccTokenTypes.KW_CHECK_ON_CACHE_PROBE
                || tt == SliccTokenTypes.KW_DEFER_ENQUEUEING
                || SliccBuiltinFunctions.isToken(tt)
                || tt == SliccTokenTypes.KW_TRIGGER
                || tt == SliccTokenTypes.KW_SET_CACHE_ENTRY
                || tt == SliccTokenTypes.KW_UNSET_CACHE_ENTRY
                || tt == SliccTokenTypes.KW_SET_TBE
                || tt == SliccTokenTypes.KW_UNSET_TBE
                || tt == SliccTokenTypes.KW_IS_VALID
                || tt == SliccTokenTypes.KW_IS_INVALID
                || tt == SliccTokenTypes.KW_IS_READY
                || tt == SliccTokenTypes.KW_STATIC_CAST) {
            parseBuiltinStmt(b); return true;
        }
        // variable declaration or expression statement
        if (tt == SliccTokenTypes.IDENTIFIER || isTypeKeyword(tt) || tt == SliccTokenTypes.KW_OOD) {
            parseExprOrDeclStmt(b); return true;
        }
        return false;
    }

    private void parseIfStmt(PsiBuilder b) {
        Marker m = b.mark();
        b.advanceLexer(); // 'if'
        if (b.getTokenType() == SliccTokenTypes.OP_LPAREN) {
            b.advanceLexer();
            parseExpressionUntil(b, SliccTokenTypes.OP_RPAREN);
            expectToken(b, SliccTokenTypes.OP_RPAREN, "')'");
        }
        parseBlock(b);
        if (b.getTokenType() == SliccTokenTypes.KW_ELSE) {
            b.advanceLexer();
            if (b.getTokenType() == SliccTokenTypes.KW_IF) parseIfStmt(b);
            else parseBlock(b);
        }
        m.done(SliccTypes.STATEMENT);
    }

    private void parseReturnStmt(PsiBuilder b) {
        Marker m = b.mark();
        b.advanceLexer(); // 'return'
        if (b.getTokenType() != SliccTokenTypes.OP_SEMICOLON) {
            parseExpressionUntil(b, SliccTokenTypes.OP_SEMICOLON);
        }
        if (b.getTokenType() == SliccTokenTypes.OP_SEMICOLON) b.advanceLexer();
        m.done(SliccTypes.STATEMENT);
    }

    private void parseBuiltinStmt(PsiBuilder b) {
        Marker m = b.mark();
        b.advanceLexer(); // keyword
        if (b.getTokenType() == SliccTokenTypes.OP_LPAREN) {
            b.advanceLexer();
            parseExpressionUntil(b, SliccTokenTypes.OP_RPAREN);
            if (b.getTokenType() == SliccTokenTypes.OP_RPAREN) b.advanceLexer();
        }
        // peek / enqueue have a block body
        if (b.getTokenType() == SliccTokenTypes.OP_LBRACE) {
            parseBlock(b);
        } else if (b.getTokenType() == SliccTokenTypes.OP_SEMICOLON) {
            b.advanceLexer();
        }
        m.done(SliccTypes.STATEMENT);
    }

    private void parseExprOrDeclStmt(PsiBuilder b) {
        // Could be: Type var := expr;  OR  expr;
        if (isLocalVarDecl(b)) {
            parseFieldOrVar(b); // emits FIELD_DECL so searchLocalScope can find it
            return;
        }
        Marker m = b.mark();
        parseExpressionUntil(b, SliccTokenTypes.OP_SEMICOLON);
        if (b.getTokenType() == SliccTokenTypes.OP_SEMICOLON) b.advanceLexer();
        m.done(SliccTypes.STATEMENT);
    }

    /** True if the current position looks like `Type varName` (local variable declaration). */
    private boolean isLocalVarDecl(PsiBuilder b) {
        if (b.getTokenType() != SliccTokenTypes.IDENTIFIER && !isTypeKeyword(b.getTokenType())) return false;
        Marker la = b.mark();
        boolean result = false;
        try {
            parseTypeName(b);
            boolean reference = parseTypeModifiers(b);
            if (b.getTokenType() == SliccTokenTypes.IDENTIFIER) {
                b.advanceLexer();
                result = !reference || b.getTokenType() == SliccTokenTypes.OP_ASSIGN;
            }
        } finally {
            la.rollbackTo();
        }
        return result;
    }

    // ......... Expression (permissive, stops at a terminator) ..................................................................

    private void parseExpressionUntil(PsiBuilder b, IElementType terminator) {
        int depth = 0;
        while (!b.eof()) {
            IElementType t = b.getTokenType();
            if (t == terminator && depth == 0) break;
            if (t == SliccTokenTypes.OP_LBRACE || t == SliccTokenTypes.OP_LPAREN
                    || t == SliccTokenTypes.OP_LBRACKET) depth++;
            if (t == SliccTokenTypes.OP_RBRACE || t == SliccTokenTypes.OP_RPAREN
                    || t == SliccTokenTypes.OP_RBRACKET) {
                if (depth == 0) break;
                depth--;
            }
            b.advanceLexer();
        }
    }

    // ......... Field / variable declaration ........................................................................................................................

    private boolean parseFieldOrVar(PsiBuilder b) {
        if (!isTypeOrIdentStart(b)) return false;
        Marker m = b.mark();
        parseTypeOrIdent(b); // type (possibly pointer)
        if (b.getTokenType() == SliccTokenTypes.IDENTIFIER) {
            parseIdent(b); // name
        }
        parseAttrList(b);
        // optional default value
        if (b.getTokenType() == SliccTokenTypes.OP_ASSIGN) {
            b.advanceLexer();
            parseExpressionUntil(b, SliccTokenTypes.OP_SEMICOLON);
        }
        if (b.getTokenType() == SliccTokenTypes.OP_SEMICOLON) b.advanceLexer();
        m.done(SliccTypes.FIELD_DECL);
        return true;
    }

    // ......... Type reference (possibly with * or template <...>) ......................................................

    private void parseTypeOrIdent(PsiBuilder b) {
        Marker m = b.mark();
        // Consume first part of type name (identifier or any type-like keyword)
        parseTypeName(b);
        parseTypeModifiers(b);
        m.done(SliccTypes.TYPE_REF);
    }

    private boolean parseTypeModifiers(PsiBuilder b) {
        while (b.getTokenType() == SliccTokenTypes.OP_STAR) b.advanceLexer();
        if (b.getTokenType() == SliccTokenTypes.OP_AMP) {
            b.advanceLexer();
            return true;
        }
        return false;
    }

    /**
     * Consume a (possibly scoped) type name such as:
     *   int  bool  string  void  float        — primitive keywords used as type names
     *   CacheMemory  SpSequencer              — user-defined identifiers
     *   std::string  prefetch::Base           — C++ scoped names with ::
     *
     * All of these are valid as the name argument to external_type(...) and
     * as type references in field declarations and parameter lists.
     */
    private void parseTypeName(PsiBuilder b) {
        IElementType tt = b.getTokenType();
        if (tt == SliccTokenTypes.IDENTIFIER || isAnyTypeToken(tt)) {
            b.advanceLexer();
            // Consume ::ident chains (C++ scoped names: std::string, prefetch::Base)
            while (b.getTokenType() == SliccTokenTypes.OP_SCOPE) {
                b.advanceLexer(); // ::
                if (b.getTokenType() == SliccTokenTypes.IDENTIFIER || isAnyTypeToken(b.getTokenType())) {
                    b.advanceLexer();
                }
            }
        }
    }

    /** True for any token that can start a type name — keywords or identifier. */
    private boolean isAnyTypeToken(IElementType t) {
        return isTypeKeyword(t); // void, bool, int, float, string
    }

    private void parseIdent(PsiBuilder b) {
        if (b.getTokenType() == SliccTokenTypes.IDENTIFIER) {
            b.advanceLexer();
        }
    }

    // ......... Helpers .......................................................................................................................................................................................

    private boolean isFunctionStart(PsiBuilder b) {
        // Heuristic: type ident '(' ? look ahead two tokens
        if (!isTypeOrIdentStart(b)) return false;
        // We use a marker-based lookahead
        Marker la = b.mark();
        boolean result = false;
        try {
            if (b.getTokenType() == SliccTokenTypes.IDENTIFIER || isTypeKeyword(b.getTokenType())) {
                parseTypeName(b);
                parseTypeModifiers(b);
                if (b.getTokenType() == SliccTokenTypes.IDENTIFIER) {
                    b.advanceLexer();
                    result = b.getTokenType() == SliccTokenTypes.OP_LPAREN;
                }
            }
        } finally {
            la.rollbackTo();
        }
        return result;
    }

    private boolean isFieldStart(PsiBuilder b) {
        return b.getTokenType() == SliccTokenTypes.IDENTIFIER || isTypeKeyword(b.getTokenType());
    }

    private boolean isTypeOrIdentStart(PsiBuilder b) {
        return b.getTokenType() == SliccTokenTypes.IDENTIFIER || isTypeKeyword(b.getTokenType());
    }

    private boolean isTypeKeyword(IElementType t) {
        return t == SliccTokenTypes.KW_VOID;
    }

    private boolean isAttrKeyword(IElementType t) {
        return t == SliccTokenTypes.KW_DESC || t == SliccTokenTypes.KW_MAIN
                || t == SliccTokenTypes.KW_BLOCK_ON || t == SliccTokenTypes.KW_FORMAT
                || t == SliccTokenTypes.KW_DEFAULT
                || t == SliccTokenTypes.KW_IN_TRANS || t == SliccTokenTypes.KW_OUT_TRANS
                || t == SliccTokenTypes.KW_NETWORK || t == SliccTokenTypes.KW_VIRTUAL_NETWORK
                || t == SliccTokenTypes.KW_VNET_TYPE || t == SliccTokenTypes.KW_RANK
                || t == SliccTokenTypes.KW_RETURN_BY_POINTER
                || t == SliccTokenTypes.KW_INTERFACE || t == SliccTokenTypes.KW_EXTERNAL
                || t == SliccTokenTypes.KW_PRIMITIVE || t == SliccTokenTypes.KW_ORDERED
                || t == SliccTokenTypes.KW_TEMPLATE || t == SliccTokenTypes.KW_CONSTRUCTOR
                || t == SliccTokenTypes.KW_NON_OBJ || t == SliccTokenTypes.KW_IMPLICIT_CTOR
                || t == SliccTokenTypes.KW_BUFFER || t == SliccTokenTypes.KW_INPORT
                || t == SliccTokenTypes.KW_OUTPORT;
    }

    /**
     * Consumes an optional positional string literal that appears after the
     * declaration name but before the key=value attribute list.
     *
     * Examples:
     *   enumeration(MaskPredictorType, "MaskPredictorType_Undefined", desc="...")
     *   structure(Foo, "some description", external="yes")
     *
     * The string is preceded by a comma (just like attributes), but is a bare
     * string value rather than a key=value pair.  We use rollback to
     * distinguish it from the start of a normal attribute list.
     */
    private void skipOptionalPositionalString(PsiBuilder b) {
        if (b.getTokenType() != SliccTokenTypes.OP_COMMA) return;
        Marker peek = b.mark();
        b.advanceLexer(); // consume ','
        if (b.getTokenType() == SliccTokenTypes.STRING_LITERAL) {
            b.advanceLexer(); // consume the string
            peek.drop();      // keep the advance
        } else {
            peek.rollbackTo(); // not a positional string — leave ',' for parseAttrList
        }
    }

    private void expectToken(PsiBuilder b, IElementType token, String msg) {
        if (b.getTokenType() == token) {
            b.advanceLexer();
        } else {
            b.error("Expected " + msg);
        }
    }
}

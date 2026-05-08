package com.slicc.plugin.psi;

import com.intellij.psi.tree.IElementType;

/**
 * Composite PSI node types for SLICC constructs.
 * These are the non-terminal nodes in the parse tree.
 */
public interface SliccTypes {
    // Top-level declarations
    IElementType MACHINE_DECL       = new SliccElementType("MACHINE_DECL");
    IElementType STRUCTURE_DECL     = new SliccElementType("STRUCTURE_DECL");
    IElementType ENUMERATION_DECL   = new SliccElementType("ENUMERATION_DECL");
    IElementType EXTERNAL_TYPE_DECL = new SliccElementType("EXTERNAL_TYPE_DECL");
    IElementType STATE_DECL         = new SliccElementType("STATE_DECL");
    IElementType INCLUDE_DECL       = new SliccElementType("INCLUDE_DECL");

    // Machine members
    IElementType IN_PORT_DECL       = new SliccElementType("IN_PORT_DECL");
    IElementType OUT_PORT_DECL      = new SliccElementType("OUT_PORT_DECL");
    IElementType ACTION_DECL        = new SliccElementType("ACTION_DECL");
    IElementType TRANSITION_DECL    = new SliccElementType("TRANSITION_DECL");
    IElementType FUNCTION_DECL      = new SliccElementType("FUNCTION_DECL");

    // Sub-elements
    IElementType PARAM_LIST         = new SliccElementType("PARAM_LIST");
    IElementType PARAM              = new SliccElementType("PARAM");
    IElementType ATTRIBUTE_LIST     = new SliccElementType("ATTRIBUTE_LIST");
    IElementType ATTRIBUTE          = new SliccElementType("ATTRIBUTE");
    IElementType BLOCK              = new SliccElementType("BLOCK");
    IElementType STATEMENT          = new SliccElementType("STATEMENT");
    IElementType EXPRESSION         = new SliccElementType("EXPRESSION");
    IElementType CALL_EXPR          = new SliccElementType("CALL_EXPR");
    IElementType MEMBER_EXPR        = new SliccElementType("MEMBER_EXPR");
    IElementType REFERENCE          = new SliccElementType("REFERENCE");
    IElementType TYPE_REF           = new SliccElementType("TYPE_REF");

    // Transition-specific
    IElementType TRANSITION_STATES  = new SliccElementType("TRANSITION_STATES");
    IElementType TRANSITION_EVENTS  = new SliccElementType("TRANSITION_EVENTS");
    IElementType TRANSITION_ACTIONS = new SliccElementType("TRANSITION_ACTIONS");
    IElementType RESOURCE_LIST      = new SliccElementType("RESOURCE_LIST");

    // Enum member
    IElementType ENUM_MEMBER        = new SliccElementType("ENUM_MEMBER");

    // Structure field
    IElementType FIELD_DECL         = new SliccElementType("FIELD_DECL");
}

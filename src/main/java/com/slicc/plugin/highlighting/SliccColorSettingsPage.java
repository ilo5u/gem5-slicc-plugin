package com.slicc.plugin.highlighting;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.slicc.plugin.icons.SliccIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

public class SliccColorSettingsPage implements ColorSettingsPage {

    private static final AttributesDescriptor[] DESCRIPTORS = {
            new AttributesDescriptor("Declaration keywords//machine, structure, action, transition", SliccSyntaxHighlighter.DECL_KEYWORD),
            new AttributesDescriptor("Control-flow keywords//if, else, for, while, return",         SliccSyntaxHighlighter.CONTROL_KEYWORD),
            new AttributesDescriptor("Type keywords//void, bool, int, float, string",               SliccSyntaxHighlighter.TYPE_KEYWORD),
            new AttributesDescriptor("Built-in statements//peek, enqueue, trigger, stall_and_wait", SliccSyntaxHighlighter.BUILTIN_STMT),
            new AttributesDescriptor("Attribute keywords//desc, default, network, rank",            SliccSyntaxHighlighter.ATTRIBUTE_KEYWORD),
            new AttributesDescriptor("Comments//Line comment",                                      SliccSyntaxHighlighter.LINE_COMMENT),
            new AttributesDescriptor("Comments//Block comment",                                     SliccSyntaxHighlighter.BLOCK_COMMENT),
            new AttributesDescriptor("Literals//String",                                            SliccSyntaxHighlighter.STRING),
            new AttributesDescriptor("Literals//Number",                                            SliccSyntaxHighlighter.NUMBER),
            new AttributesDescriptor("Identifiers",                                                 SliccSyntaxHighlighter.IDENTIFIER),
            new AttributesDescriptor("Operators",                                                   SliccSyntaxHighlighter.OPERATOR),
            new AttributesDescriptor("Braces and brackets//Braces {}",                             SliccSyntaxHighlighter.BRACES),
            new AttributesDescriptor("Braces and brackets//Parentheses ()",                        SliccSyntaxHighlighter.PARENS),
            new AttributesDescriptor("Braces and brackets//Brackets []",                           SliccSyntaxHighlighter.BRACKETS),
            new AttributesDescriptor("Semantic//Machine name",                                     SliccSyntaxHighlighter.MACHINE_NAME),
            new AttributesDescriptor("Semantic//Structure / external type name",                   SliccSyntaxHighlighter.STRUCTURE_NAME),
            new AttributesDescriptor("Semantic//Enum / state type name (enumeration, state_declaration)", SliccSyntaxHighlighter.ENUM_TYPE_NAME),
            new AttributesDescriptor("Semantic//Enum / state member value (Load, Store, I, LM, …)",      SliccSyntaxHighlighter.ENUM_MEMBER_VALUE),
            new AttributesDescriptor("Semantic//Action name",                                      SliccSyntaxHighlighter.ACTION_NAME),
            new AttributesDescriptor("Semantic//Function / method name",                           SliccSyntaxHighlighter.FUNCTION_NAME),
            new AttributesDescriptor("Semantic//Structure field / member name",                    SliccSyntaxHighlighter.FIELD_NAME),
            new AttributesDescriptor("Semantic//Function name",                                    SliccSyntaxHighlighter.FUNCTION_NAME),
            new AttributesDescriptor("Semantic//Transition label",                                 SliccSyntaxHighlighter.TRANSITION_LABEL),
    };

    @Nullable @Override public Icon getIcon() { return SliccIcons.FILE; }
    @NotNull  @Override public SyntaxHighlighter getHighlighter() { return new SliccSyntaxHighlighter(); }
    @NotNull  @Override public AttributesDescriptor[] getAttributeDescriptors() { return DESCRIPTORS; }
    @NotNull  @Override public ColorDescriptor[]      getColorDescriptors()     { return ColorDescriptor.EMPTY_ARRAY; }
    @NotNull  @Override public String                 getDisplayName()          { return "SLICC (gem5 Ruby)"; }

    @Nullable @Override
    public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        return Map.of(
                "mn",  SliccSyntaxHighlighter.MACHINE_NAME,
                "sn",  SliccSyntaxHighlighter.STRUCTURE_NAME,
                "en",  SliccSyntaxHighlighter.ENUM_NAME,
                "an",  SliccSyntaxHighlighter.ACTION_NAME,
                "fn",  SliccSyntaxHighlighter.FUNCTION_NAME,
                "tl",  SliccSyntaxHighlighter.TRANSITION_LABEL
        );
    }

    @NotNull @Override
    public String getDemoText() {
        return """
                // SLICC Cache Coherence Protocol Demo
                /* gem5 Ruby subsystem */
                
                include "RubySlicc_interfaces.slicc";
                
                machine(MachineType:<mn>SpPCache</mn>, "Private cache for demo")
                    :   CacheMemory *cache;
                        Cycles req_latency := 2;
                        MessageBuffer *reqOut, network = "To", virtual_network = "0",
                                      vnet_type = "request";
                        MessageBuffer *mandatoryQueue;
                {
                    state_declaration(State, default = "SpPCache_State_I") {
                        I,  AccessPermission:NotPresent, desc = "Invalid";
                        M,  AccessPermission:Read_Write, desc = "Modified";
                        S,  AccessPermission:Read_Only,  desc = "Shared";
                    }
                
                    enumeration(<en>Event</en>, desc = "Cache events") {
                        Load,  desc = "CPU load";
                        Store, desc = "CPU store";
                        Data,  desc = "Data from LLC";
                    }
                
                    structure(<sn>Entry</sn>, desc = "Cache entry",
                              interface = "AbstractCacheEntry") {
                        State state,    desc = "";
                        bool dirty,     default = "false", desc = "Dirty flag";
                        DataBlock DataBlk, desc = "";
                    }
                
                    out_port(reqOutPort, SpMsg, reqOut);
                
                    in_port(reqInPort, RubyRequest, mandatoryQueue, rank = 1) {
                        if (reqInPort.isReady(clockEdge())) {
                            peek(reqInPort, RubyRequest) {
                                trigger(Event:Load, in_msg.addr, getCacheEntry(in_msg.addr));
                            }
                        }
                    }
                
                    action(<an>SendGetS</an>, "sgs", desc = "Send GetS to LLC") {
                        enqueue(reqOutPort, SpMsg, req_latency) {
                            out_msg.addr := address;
                            out_msg.Type := SpMsgType:Get;
                        }
                    }
                
                    transition(I, Load, M) {TagArrayWrite} {
                        SendGetS;
                        Pop_Queue;
                    }
                
                    <fn>void</fn> getCacheEntry(Addr addr) {
                        if (cache.isTagPresent(addr)) {
                            return cache.lookup(addr);
                        }
                        return OOD;
                    }
                }
                """;
    }
}
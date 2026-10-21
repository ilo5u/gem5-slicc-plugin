package com.slicc.plugin.icons;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public class SliccIcons {
    public static final Icon SM_FILE = IconLoader.getIcon("/icons/sm.svg", SliccIcons.class);
    public static final Icon SLICC_FILE = IconLoader.getIcon("/icons/slicc.svg", SliccIcons.class);
    public static final Icon FILE = SM_FILE;
    public static final Icon MACHINE = IconLoader.getIcon("/icons/slicc_machine.svg", SliccIcons.class);
    public static final Icon STRUCTURE = IconLoader.getIcon("/icons/slicc_structure.svg", SliccIcons.class);
    public static final Icon ACTION = IconLoader.getIcon("/icons/slicc_action.svg", SliccIcons.class);
    public static final Icon TRANSITION = IconLoader.getIcon("/icons/slicc_transition.svg", SliccIcons.class);
    public static final Icon FUNCTION = IconLoader.getIcon("/icons/slicc_function.svg", SliccIcons.class);
    public static final Icon ENUM = IconLoader.getIcon("/icons/slicc_enum.svg", SliccIcons.class);
}

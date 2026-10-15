package com.wimbli.WorldBorder;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CommandHelpTest {
    private CommandSender sender(Set<String> grants, List<String> messages, boolean player) {
        return (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                new Class<?>[]{player ? Player.class : CommandSender.class}, (proxy, method, args) -> {
                    if (method.getName().equals("hasPermission")) return grants.contains(args[0]);
                    if (method.getName().equals("sendMessage") && args[0] instanceof String text) {
                        messages.add(ChatColor.stripColor(text)); return null;
                    }
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    return null;
                });
    }
    @Test void ordinaryAndOutOfRangeHelpNeverRevealStaff() {
        WBCommand command = new WBCommand(); List<String> messages = new ArrayList<>();
        CommandSender sender = sender(Set.of("worldborder.list"), messages, true);
        for (String[] args : List.of(new String[0], new String[]{"help"}, new String[]{"help", "0"}, new String[]{"help", "99"}, new String[]{"help", "banana"})) {
            messages.clear(); command.onCommand(sender, null, "wb", args);
            assertEquals(3, messages.size()); assertTrue(messages.get(1).startsWith("/wb list"));
            assertFalse(String.join("\n", messages).contains("Staff"));
            assertFalse(String.join("\n", messages).contains("for more"));
        }
        assertEquals(List.of("help", "list"), command.onTabComplete(sender, null, "wb", new String[]{""}));
        assertEquals(List.of("1"), command.onTabComplete(sender, null, "wb", new String[]{"help", ""}));
    }
    @Test void partialStaffStartsOnSeparatePageAndCannotUseDeniedAliases() {
        WBCommand command = new WBCommand(); List<String> messages = new ArrayList<>();
        CommandSender sender = sender(Set.of("worldborder.list", "worldborder.set"), messages, true);
        command.onCommand(sender, null, "wborder", new String[]{"help"});
        assertTrue(messages.get(0).contains("(1/2)")); assertFalse(messages.get(0).contains("Staff"));
        assertTrue(messages.get(messages.size()-1).contains("help 2"));
        messages.clear(); command.onCommand(sender, null, "wb", new String[]{"help", "staff", "99"});
        assertTrue(messages.get(0).contains("Staff")); assertTrue(messages.size() <= 10);
        assertTrue(messages.stream().noneMatch(text -> text.contains("reload")));
        messages.clear(); command.onCommand(sender, null, "wborder", new String[]{"reload"});
        assertEquals(List.of("You do not have permission to do that."), messages);
    }
    @Test void deniedTopicAndDirectStaffRemainGeneric() {
        WBCommand command = new WBCommand(); List<String> messages = new ArrayList<>();
        CommandSender sender = sender(Set.of("worldborder.list"), messages, true);
        for (String[] args : List.of(new String[]{"help", "staff"}, new String[]{"help", "set"})) {
            messages.clear(); command.onCommand(sender, null, "wb", args);
            assertEquals(List.of("You do not have permission to do that."), messages);
        }
    }
    @Test void consoleDocumentationRetainsWorldContextWhileExecutionRequiresPermission() {
        WBCommand command = new WBCommand(); List<String> messages = new ArrayList<>();
        CommandSender sender = sender(Set.of("worldborder.set"), messages, false);
        command.onCommand(sender, null, "wb", new String[0]);
        assertTrue(messages.get(0).contains("Staff"));
        assertTrue(messages.stream().anyMatch(text -> text.contains("<world>")));
        messages.clear(); command.onCommand(sender, null, "wb", new String[]{"reload"});
        assertEquals(List.of("You do not have permission to do that."), messages);
    }
    @Test void unknownShowsErrorThenFilteredHelp() {
        WBCommand command = new WBCommand(); List<String> messages = new ArrayList<>();
        CommandSender sender = sender(Set.of("worldborder.list"), messages, true);
        command.onCommand(sender, null, "wb", new String[]{"unknown"});
        assertEquals(4, messages.size()); assertTrue(messages.get(0).contains("not recognized"));
        assertTrue(messages.get(1).contains("WorldBorder"));
    }
}

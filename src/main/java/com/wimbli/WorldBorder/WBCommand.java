package com.wimbli.WorldBorder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.bukkit.command.*;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import com.wimbli.WorldBorder.cmd.*;


public class WBCommand implements CommandExecutor, TabCompleter
{
	// map of all sub-commands with the command name (string) for quick reference
	public Map<String, WBCmd> subCommands = new LinkedHashMap<String, WBCmd>();
	// ref. list of the commands which can have a world name in front of the command itself (ex. /wb _world_ radius 100)
	private Set<String> subCommandsWithWorldNames = new LinkedHashSet<String>();

	// constructor
	public WBCommand ()
	{
		addCmd(new CmdHelp());			// 1 example
		addCmd(new CmdSet());			// 4 examples for player, 3 for console
		addCmd(new CmdSetcorners());	// 1
		addCmd(new CmdRadius());		// 1
		addCmd(new CmdList());			// 1
		//----- 8 per page of examples
		addCmd(new CmdShape());			// 2
		addCmd(new CmdClear());			// 2
		addCmd(new CmdFill());			// 1
		addCmd(new CmdTrim());			// 1
		addCmd(new CmdBypass());		// 1
		addCmd(new CmdBypasslist());	// 1
		//-----
		addCmd(new CmdKnockback());		// 1
		addCmd(new CmdWrap());			// 1
		addCmd(new CmdWhoosh());		// 1
		addCmd(new CmdGetmsg());		// 1
		addCmd(new CmdSetmsg());		// 1
		addCmd(new CmdWshape());		// 3
		//-----
		addCmd(new CmdPreventPlace());	// 1
		addCmd(new CmdPreventSpawn());	// 1
		addCmd(new CmdDelay());			// 1
		addCmd(new CmdDynmap());		// 1
		addCmd(new CmdDynmapmsg());		// 1
		addCmd(new CmdRemount());		// 1
		addCmd(new CmdFillautosave());	// 1
		addCmd(new CmdPortal());		// 1
		//-----
		addCmd(new CmdDenypearl());		// 1
		addCmd(new CmdReload());		// 1
		addCmd(new CmdDebug());			// 1

		// this is the default command, which shows command example pages; should be last just in case
		addCmd(new CmdCommands());
	}


	private void addCmd(WBCmd cmd)
	{
		subCommands.put(cmd.name, cmd);
		if (cmd.hasWorldNameInput)
			subCommandsWithWorldNames.add(cmd.name);
	}

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] split)
	{
		Player player = (sender instanceof Player) ? (Player)sender : null;
        if (split.length == 0 || split[0].equalsIgnoreCase("help") || split[0].equalsIgnoreCase("commands")) {
            showHelp(sender, split.length == 0 ? new String[0] : Arrays.copyOfRange(split, 1, split.length));
            return true;
        }

		// if world name is passed inside quotation marks, handle that, and get List<String> instead of String[]
		List<String> params = concatenateQuotedWorldName(split);

		String worldName = null;
		// is second parameter the command and first parameter a world name? definitely world name if it was in quotation marks
		if (wasWorldQuotation || (params.size() > 1 && !subCommands.containsKey(params.get(0)) && subCommandsWithWorldNames.contains(params.get(1))))
			worldName = params.get(0);

		// no command specified? show command examples / help
		if (params.isEmpty())
			params.add(0, "commands");

		// determined the command name
		String cmdName = (worldName == null) ? params.get(0).toLowerCase() : params.get(1).toLowerCase();

		// remove command name and (if there) world name from front of param array
		params.remove(0);
		if (worldName != null)
			params.remove(0);

		// make sure command is recognized, default to showing command examples / help if not; also check for specified page number
		if (!subCommands.containsKey(cmdName))
		{
			int page = (player == null) ? 0 : 1;
			try
			{
				page = Integer.parseInt(cmdName);
			}
			catch(NumberFormatException ignored)
			{
				sender.sendMessage(WBCmd.C_ERR + "Command not recognized. Showing command list.");
			}
            showHelp(sender, new String[]{Integer.toString(page)});
            return true;
		}

		WBCmd subCommand = subCommands.get(cmdName);

		// check permission
        if (!permitted(sender, subCommand)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to do that.");
            return true;
        }

		// if command requires world name when run by console, make sure that's in place
		if (player == null && subCommand.hasWorldNameInput && subCommand.consoleRequiresWorldName && worldName == null)
		{
			sender.sendMessage(WBCmd.C_ERR + "This command requires a world to be specified if run by the console.");
			subCommand.sendCmdHelp(sender);
			return true;
		}

		// make sure valid number of parameters has been provided
		if (params.size() < subCommand.minParams || params.size() > subCommand.maxParams)
		{
			if (subCommand.maxParams == 0)
				sender.sendMessage(WBCmd.C_ERR + "This command does not accept any parameters.");
			else
				sender.sendMessage(WBCmd.C_ERR + "You have not provided a valid number of parameters.");
			subCommand.sendCmdHelp(sender);
			return true;
		}

		// execute command
		subCommand.execute(sender, player, params, worldName);

		return true;
	}


	private boolean wasWorldQuotation = false;

	// if world name is surrounded by quotation marks, combine it down and flag wasWorldQuotation if it's first param.
	// also return List<String> instead of input primitive String[]
	private List<String> concatenateQuotedWorldName(String[] split)
	{
		wasWorldQuotation = false;
		List<String> args = new ArrayList<String>(Arrays.asList(split));

		int startIndex = -1;
		for (int i = 0; i < args.size(); i++)
		{
			if (args.get(i).startsWith("\""))
			{
				startIndex = i;
				break;
			}
		}
		if (startIndex == -1)
			return args;

		if (args.get(startIndex).endsWith("\""))
		{
			args.set(startIndex, args.get(startIndex).substring(1, args.get(startIndex).length() - 1));
			if (startIndex == 0)
				wasWorldQuotation = true;
		}
		else
		{
			List<String> concat = new ArrayList<String>(args);
			Iterator<String> concatI = concat.iterator();

			// skip past any parameters in front of the one we're starting on
			for (int i = 1; i < startIndex + 1; i++)
			{
				concatI.next();
			}

			StringBuilder quote = new StringBuilder(concatI.next());
			while (concatI.hasNext())
			{
				String next = concatI.next();
				concatI.remove();
				quote.append(" ");
				quote.append(next);
				if (next.endsWith("\""))
				{
					concat.set(startIndex, quote.substring(1, quote.length() - 1));
					args = concat;
					if (startIndex == 0)
						wasWorldQuotation = true;
					break;
				}
			}
		}
		return args;
	}

	public Set<String> getCommandNames()
	{
		// using TreeSet to sort alphabetically
		Set<String> commands = new TreeSet<>(subCommands.keySet());
		// removing default "commands" command as it's not normally shown or run like other commands
		commands.remove("commands");
		return commands;
	}
    private boolean permitted(CommandSender sender, WBCmd command) {
        return command.permission == null || sender.hasPermission("worldborder." + command.permission);
    }

    private boolean staff(WBCmd command) {
        return !command.name.equals("list") && !command.name.equals("getmsg");
    }

    private List<WBCmd> visible(CommandSender sender, boolean staffSection) {
        List<WBCmd> result = new ArrayList<>();
        for (WBCmd command : subCommands.values())
            if (!command.name.equals("help") && !command.name.equals("commands")
                    && staff(command) == staffSection && permitted(sender, command)) result.add(command);
        result.sort(java.util.Comparator.comparing(command -> command.name.equals("debug")));
        return result;
    }

    public void showHelp(CommandSender sender, String[] args) {
        List<String> ordinary = examples(sender, visible(sender, false));
        List<String> staff = examples(sender, visible(sender, true));
        boolean direct = args.length > 0 && args[0].equalsIgnoreCase("staff");
        if (direct && staff.isEmpty() || ordinary.isEmpty() && staff.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to do that.");
            return;
        }
        if (args.length > 0 && !direct) {
            WBCmd requested = subCommands.get(args[0].toLowerCase(java.util.Locale.ROOT));
            if (requested != null && !requested.name.equals("help") && !requested.name.equals("commands")) {
                if (!permitted(sender, requested)) {
                    sender.sendMessage(ChatColor.RED + "You do not have permission to do that.");
                    return;
                }
                sender.sendMessage(ChatColor.DARK_GRAY + "---------- " + ChatColor.GREEN + "WorldBorder"
                        + (staff(requested) ? " Staff" : "") + ChatColor.DARK_GRAY + " ----------");
                for (String row : examples(sender, List.of(requested))) sender.sendMessage(row);
                sender.sendMessage(ChatColor.DARK_GRAY + "----------------------------------------");
                return;
            }
        }
        int ordinaryPages = (ordinary.size() + 7) / 8;
        int total = ordinaryPages + (staff.size() + 7) / 8;
        int requested = 1;
        try { requested = Integer.parseInt(args[direct ? 1 : 0]); }
        catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) { }
        int page = direct ? ordinaryPages + Math.max(1, Math.min((staff.size() + 7) / 8, requested))
                : Math.max(1, Math.min(total, requested));
        boolean staffPage = page > ordinaryPages;
        List<String> rows = staffPage ? staff : ordinary;
        int first = ((staffPage ? page - ordinaryPages : page) - 1) * 8;
        sender.sendMessage(ChatColor.DARK_GRAY + "---------- " + ChatColor.GREEN + "WorldBorder"
                + (staffPage ? " Staff" : "") + (total > 1 ? ChatColor.DARK_GRAY + " ("
                + ChatColor.WHITE + page + "/" + total + ChatColor.DARK_GRAY + ")" : "")
                + ChatColor.DARK_GRAY + " ----------");
        for (int i = first; i < Math.min(rows.size(), first + 8); i++) sender.sendMessage(rows.get(i));
        sender.sendMessage(ChatColor.DARK_GRAY + (page < total
                ? "---------- /wb help " + (page + 1) + " for more ----------"
                : "----------------------------------------"));
    }

    private List<String> examples(CommandSender sender, List<WBCmd> commands) {
        List<String> result = new ArrayList<>();
        for (WBCmd command : commands) {
            for (String example : sender instanceof Player ? command.cmdExamplePlayer : command.cmdExampleConsole) {
                String raw = ChatColor.stripColor(example).replaceAll(" +", " ").trim();
                if (!raw.startsWith("/")) raw = "/" + raw;
                String[] parts = raw.split(" - ", 2);
                result.add(ChatColor.GREEN + parts[0] + ChatColor.DARK_GRAY + " - "
                        + ChatColor.GRAY + (parts.length > 1 ? parts[1] : "show command usage"));
            }
        }
        return result;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.add("help");
            for (WBCmd candidate : subCommands.values())
                if (!candidate.name.equals("help") && !candidate.name.equals("commands") && permitted(sender, candidate)) options.add(candidate.name);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("help")) {
            if (!visible(sender, true).isEmpty()) options.add("staff");
            if (!visible(sender, false).isEmpty() || !visible(sender, true).isEmpty()) options.add("1");
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(java.util.Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(prefix)).toList();
    }

}

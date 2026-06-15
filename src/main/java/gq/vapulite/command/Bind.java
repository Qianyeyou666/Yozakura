package gq.vapulite.command;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import gq.vapulite.core.Client;
import gq.vapulite.module.Module;
import gq.vapulite.manager.ModuleManager;
import gq.vapulite.util.minecraft.Helper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.BlockPos;
import net.minecraft.world.WorldSettings;
import org.lwjgl.input.Keyboard;

import java.util.*;

public class Bind implements ICommand {

	private Minecraft mc = Minecraft.getMinecraft();

	public Client vapuClient =null;

	public Bind(Client vapuClient) {
		this.vapuClient = vapuClient;
	}


	@Override
	public String getCommandName() {
		return "bind";
	}

	@Override
	public String getCommandUsage(ICommandSender sender) {
		return "/bind <Module> <key>";
	}

	@Override
	public void processCommand(ICommandSender sender, String[] args) throws CommandException {
		if (args.length == 2) {
			String name = args[0];
			String key = args[1];
			Module module = ModuleManager.getModule(name);
			if(module == null){
				Helper.sendMessage("Module not found: " + name);
				return;
			}
			int keyCode = parseKey(key);
			if (keyCode == Integer.MIN_VALUE) {
				Helper.sendMessage("Unknown key: " + key);
				return;
			}
			module.setKey(keyCode);
			Helper.sendMessage("Bind " + module.getName() + " to " + getKeyName(keyCode));
			try {
				Client.SaveConfig();
			}
			catch(Exception e) {
				Helper.sendMessage("Bind saved in memory, but config save failed.");
			}
			return;
		}
		Helper.sendMessage(getCommandUsage(sender));
	}


	@Override
	public boolean canCommandSenderUseCommand(ICommandSender sender) {
		return sender.getCommandSenderEntity() instanceof EntityPlayer;
	}

	@Override
	public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
		if (args.length == 1) {
			List<String> names = new ArrayList<String>();
			for (Module module : ModuleManager.getModules()) {
				names.add(module.getName());
			}
			return CommandBase.getListOfStringsMatchingLastWord(args, names.toArray(new String[names.size()]));
		}
		if (args.length == 2) {
			List<String> keys = new ArrayList<String>();
			keys.add("NONE");
			keys.add("DELETE");
			keys.add("RSHIFT");
			keys.add("LCONTROL");
			keys.add("L");
			keys.add("K");
			keys.add("G");
			keys.add("H");
			keys.add("I");
			keys.add("N");
			keys.add("X");
			return CommandBase.getListOfStringsMatchingLastWord(args, keys.toArray(new String[keys.size()]));
		}
		return Collections.<String>emptyList();
	}

	private int parseKey(String key) {
		if (key == null) {
			return Integer.MIN_VALUE;
		}
		String normalized = key.trim().toUpperCase(Locale.ROOT);
		if (normalized.equals("NONE") || normalized.equals("CLEAR") || normalized.equals("DELETE") || normalized.equals("DEL")) {
			return Keyboard.KEY_NONE;
		}
		int keyCode = Keyboard.getKeyIndex(normalized);
		return keyCode == Keyboard.KEY_NONE ? Integer.MIN_VALUE : keyCode;
	}

	private String getKeyName(int keyCode) {
		if (keyCode == Keyboard.KEY_NONE) {
			return "NONE";
		}
		String keyName = Keyboard.getKeyName(keyCode);
		return keyName == null ? String.valueOf(keyCode) : keyName;
	}

	@Override
	public int compareTo(ICommand arg0) {
		// TODO Auto-generated method stub
		return this.getCommandName().compareTo(arg0.getCommandName());
	}

	@Override
	public List<String> getCommandAliases() {
		// TODO Auto-generated method stub
		return Collections.<String>emptyList();
	}

	@Override
	public boolean isUsernameIndex(String[] args, int index) {
		// TODO Auto-generated method stub
		return false;
	}
}

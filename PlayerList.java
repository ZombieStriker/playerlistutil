import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.Validate;
import org.bukkit.*;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.simple.*;
import org.json.simple.parser.JSONParser;

import com.google.common.base.Strings;
import com.google.common.cache.*;
import com.google.common.collect.*;

/*
 *  Copyright (C) 2017 Zombie_Striker
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU General Public License as published by the Free Software Foundation; either version 2 of
 *  the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with this program;
 *  if not, write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
 *  02111-1307 USA
 */

public class PlayerList {
	private static final Class<?> PACKET_PLAYER_INFO_CLASS = a(7)
			? ReflectionUtil.getNMSClass("PacketPlayOutPlayerInfo")
			: ReflectionUtil.getNMSClass("Packet201PlayerInfo");
	private static final Class<?> PACKET_PLAYER_INFO_DATA_CLASS = a()
			? ReflectionUtil.getNMSClass("PacketPlayOutPlayerInfo$PlayerInfoData")
			: null;
	private static Class<?> WORLD_GAME_MODE_CLASS;
	protected static final Class<?> GAMEPROFILECLASS = a() ? ReflectionUtil.getMojangAuthClass("GameProfile") : null;
	protected static final Class<?> PROPERTYCLASS = a() ? ReflectionUtil.getMojangAuthClass("properties.Property")
			: null;
	private static final Constructor<?> GAMEPROPHILECONSTRUCTOR = a()
			? (Constructor<?>) ReflectionUtil.getConstructor(GAMEPROFILECLASS, UUID.class, String.class).get()
			: null;
	private static final Class<?> CRAFTPLAYERCLASS = ReflectionUtil.getCraftbukkitClass("CraftPlayer", "entity");
	private static final Object WORLD_GAME_MODE_NOT_SET;
	private static final Class<?> CRAFT_CHAT_MESSAGE_CLASS = a()
			? ReflectionUtil.getCraftbukkitClass("CraftChatMessage", "util")
			: null;
	private static final Class<?> PACKET_PLAYER_INFO_PLAYER_ACTION_CLASS = a()
			? ReflectionUtil.getNMSClass("PacketPlayOutPlayerInfo$EnumPlayerInfoAction")
			: null;
	private static final Object PACKET_PLAYER_INFO_ACTION_REMOVE_PLAYER = a()
			? ReflectionUtil.getEnumConstant(PACKET_PLAYER_INFO_PLAYER_ACTION_CLASS, "REMOVE_PLAYER")
			: null;
	private static final Object PACKET_PLAYER_INFO_ACTION_ADD_PLAYER = a()
			? ReflectionUtil.getEnumConstant(PACKET_PLAYER_INFO_PLAYER_ACTION_CLASS, "ADD_PLAYER")
			: null;
	private static final Class<?> PACKET_CLASS = ReflectionUtil.getNMSClass("Packet");
	private static final Class<?> I_CHAT_BASE_COMPONENT_CLASS = a() ? ReflectionUtil.getNMSClass("IChatBaseComponent")
			: null;
	private static final Constructor<?> PACKET_PLAYER_INFO_DATA_CONSTRUCTOR;

	private static Class<?> PACKET_HEADER_FOOTER_CLASS;
	private static Constructor<?> PACKET_HEADER_FOOTER_CONSTRUCTOR = null;
	private static Class<?> CHAT_SERIALIZER;

	private static Class<?> PROPERTY;
	private static Constructor<?> PROPERTY_CONSTRUCTOR;

	private static Class<?> PROPERTY_MAP;

	private static Object invokeChatSerializerA(String text) {
		return ReflectionUtil.invokeMethod(CHAT_SERIALIZER, null, "a", new Class[] { String.class },
				"{\"text\":\"" + text + "\"}");
	}

	// TODO: This bit of code has been added to check specifically for 1.7.10.
	// update. Since this update has changes to it's spawnplayer packet, this
	// hopefully will fix issues with player disconnection on that update
	//
	// http://wiki.vg/Protocol_History#14w04a
	// ||ReflectionUtil.SERVER_VERSION.contains("7_R4")

	static Plugin plugin;

	static {
		// It's hacky, I know, but atleast it gets a plugin instance.
		try {
			File f = new File(Skin.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
			for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
				if (f.getName().contains(p.getName())) {
					plugin = p;
					break;
				}
			}
		} catch (URISyntaxException e) {
		}
		if (plugin == null)
			plugin = Bukkit.getPluginManager().getPlugins()[0];

		WORLD_GAME_MODE_CLASS = ReflectionUtil.getNMSClass("EnumGamemode");
		if (WORLD_GAME_MODE_CLASS == null)
			WORLD_GAME_MODE_CLASS = ReflectionUtil.getNMSClass("WorldSettings$EnumGamemode");
		CHAT_SERIALIZER = ReflectionUtil.getNMSClass("IChatBaseComponent$ChatSerializer");
		if (CHAT_SERIALIZER == null)
			CHAT_SERIALIZER = ReflectionUtil.getNMSClass("ChatSerializer");
		PROPERTY = ReflectionUtil.getMojangAuthClass("properties.Property");
		PROPERTY_CONSTRUCTOR = (Constructor<?>) ReflectionUtil
				.getConstructor(PROPERTY, new Class[] { String.class, String.class, String.class }).get();

		if (PROPERTY == null || PROPERTY_CONSTRUCTOR == null) {
			PROPERTY = ReflectionUtil.getOLDAuthlibClass("properties.Property");
			PROPERTY_CONSTRUCTOR = (Constructor<?>) ReflectionUtil
					.getConstructor(PROPERTY, new Class[] { String.class, String.class, String.class }).get();
		}

		WORLD_GAME_MODE_NOT_SET = a() ? ReflectionUtil.getEnumConstant(WORLD_GAME_MODE_CLASS, "NOT_SET") : null;
		PACKET_PLAYER_INFO_DATA_CONSTRUCTOR = a()
				? (Constructor<?>) ReflectionUtil
						.getConstructor(PACKET_PLAYER_INFO_DATA_CLASS, PACKET_PLAYER_INFO_CLASS, GAMEPROFILECLASS,
								int.class, WORLD_GAME_MODE_CLASS, I_CHAT_BASE_COMPONENT_CLASS)
						.get()
				: null;
		if (ReflectionUtil.isVersionHigherThan(1, 7)) {
			try {
				PACKET_HEADER_FOOTER_CLASS = ReflectionUtil.getNMSClass("PacketPlayOutPlayerListHeaderFooter");
				PACKET_HEADER_FOOTER_CONSTRUCTOR = PACKET_HEADER_FOOTER_CLASS.getConstructors()[0];
			} catch (Exception | Error e) {
			}
		}
	}

	private final static String[] colorcodeOrder = "0123456789abcdef".split("");
	private final static String[] inviscodeOrder = { ",", ".", "\'", "`", " " };

	public static int SIZE_DEFAULT = 20;
	public static int SIZE_TWO = 40;
	public static int SIZE_THREE = 60;
	public static int SIZE_FOUR = 80;

	private List<Object> datas = new ArrayList<>();
	private Map<Integer, String> datasOLD = new HashMap<Integer, String>();

	private UUID ownerUUID;
	private String[] tabs;
	private boolean[] hasCustomTexture;
	private int size = 0;

	private static final HashMap<UUID, PlayerList> lookUpTable = new HashMap<>();

	/**
	 * Due to the amount of times I have to check if a version is higher than 1.8,
	 * all reflection calls will be replace with this method.
	 * 
	 * @param update
	 * @return
	 */
	protected static boolean a(Integer... update) {
		return ReflectionUtil.isVersionHigherThan(1, update.length > 0 ? update[0] : 8);
	}

	public void setHeaderFooter(String header, String footer) {
		Object packet = ReflectionUtil.instantiate(PACKET_HEADER_FOOTER_CONSTRUCTOR);
		ReflectionUtil.setInstanceField(packet, "a", invokeChatSerializerA(header));
		ReflectionUtil.setInstanceField(packet, "b", invokeChatSerializerA(footer));
		sendPacket(packet, Bukkit.getPlayer(this.ownerUUID));
	}

	/**
	 * Tries to return an existing table instance for a player. If one does not
	 * exist, it will create a new one with a default size.
	 * 
	 * @param player
	 * @return null or the player's tablist.
	 */
	public static PlayerList getPlayerList(Player player) {
		if (!lookUpTable.containsKey(player.getUniqueId()))
			return new PlayerList(player, SIZE_TWO);
		return lookUpTable.get(player.getUniqueId());
	}

	public PlayerList(Player player, int size) {
		lookUpTable.put(this.ownerUUID = player.getUniqueId(), this);
		tabs = new String[80];
		hasCustomTexture = new boolean[80];
		this.size = size;
	}

	/**
	 * Returns the name of the tab at the index 'index'
	 * 
	 * @param index
	 *            - the index of the entry in the tablist
	 * 
	 * @return
	 */
	public String getTabName(int index) {
		return tabs[index];
	}

	/**
	 * Resets a player's tablist. Use this if you have want the tablist to return to
	 * the base-minecraft tablist
	 */
	public void resetTablist() {
		this.clearAll();
		int i = 0;
		for (Player player : Bukkit.getOnlinePlayers()) {
			addExistingPlayer(i, player);
			i++;
		}
	}

	/**
	 * Clears all players from the player's tablist.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void clearPlayers() {
		Object packet = ReflectionUtil
				.instantiate((Constructor<?>) ReflectionUtil.getConstructor(PACKET_PLAYER_INFO_CLASS).get());

		if (ReflectionUtil.getInstanceField(packet, "b") instanceof List) {
			List<Object> players = (List<Object>) ReflectionUtil.getInstanceField(packet, "b");

			Object olp = ReflectionUtil.invokeMethod(Bukkit.getServer(), "getOnlinePlayers", null);
			Object[] olpa;
			if (olp instanceof Collection)
				olpa = ((Collection) olp).toArray();
			else
				olpa = ((Player[]) olp);

			for (Object player2 : olpa) {
				Player player = (Player) player2;
				Object gameProfile = GAMEPROFILECLASS
						.cast(ReflectionUtil.invokeMethod(player, "getProfile", new Class[0]));
				Object[] array = (Object[]) ReflectionUtil.invokeMethod(CRAFT_CHAT_MESSAGE_CLASS, null, "fromString",
						new Class[] { String.class }, player.getName());
				Object data = ReflectionUtil.instantiate(PACKET_PLAYER_INFO_DATA_CONSTRUCTOR, packet, gameProfile, 1,
						WORLD_GAME_MODE_NOT_SET, array[0]);
				players.add(data);
			}
			sendNEWTabPackets(getPlayer(), packet, players, PACKET_PLAYER_INFO_ACTION_REMOVE_PLAYER);
		} else {
			Object olp = ReflectionUtil.invokeMethod(Bukkit.getServer(), "getOnlinePlayers", null);
			Object[] players = olp instanceof Collection ? ((Collection<?>) olp).toArray() : (Object[]) olp;
			for (int i = 0; i < players.length; i++) {
				try {
					Object packetLoop = ReflectionUtil.instantiate(
							(Constructor<?>) ReflectionUtil.getConstructor(PACKET_PLAYER_INFO_CLASS).get());
					sendOLDTabPackets(getPlayer(), packetLoop, ((Player) players[i]).getName(), false);
				} catch (Exception e) {
					error();
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * Clears all the custom tabs from the player's tablist.
	 */
	@SuppressWarnings("unchecked")
	public void clearCustomTabs() {
		Object packet = ReflectionUtil
				.instantiate((Constructor<?>) ReflectionUtil.getConstructor(PACKET_PLAYER_INFO_CLASS).get());

		if (ReflectionUtil.getInstanceField(packet, "b") instanceof List) {
			List<Object> players = (List<Object>) ReflectionUtil.getInstanceField(packet, "b");
			for (Object playerData : new ArrayList<>(datas))
				tabs[getIDFromName((String) ReflectionUtil.invokeMethod(
						GAMEPROFILECLASS.cast(ReflectionUtil.invokeMethod(playerData, "a", new Class[0])), "getName",
						null))] = "";
			players.addAll(datas);
			datas.clear();
			sendNEWTabPackets(getPlayer(), packet, players, PACKET_PLAYER_INFO_ACTION_REMOVE_PLAYER);
		} else {
			for (int i = 0; i < size; i++)
				if (datasOLD.containsKey(i))
					try {
						Object packetLoop = ReflectionUtil.instantiate(
								(Constructor<?>) ReflectionUtil.getConstructor(PACKET_PLAYER_INFO_CLASS).get());
						sendOLDTabPackets(getPlayer(), packetLoop, datasOLD.get(i), false);
						tabs[i] = null;
					} catch (Exception e) {
						e.printStackTrace();
					}
			datasOLD.clear();
		}
	}

	/**
	 * Clears all the values for a player's tablist. Use this whenever a player
	 * first joins if you want to create your own tablist.
	 * 
	 * This is here to remind you that you MUST call either this method or the
	 * "clearCustomTabs" method. If you do not, the player will continue to see the
	 * custom tabs until they relog.
	 */
	public void clearAll() {
		clearPlayers();
		clearCustomTabs();
	}

	/**
	 * Use this for changing a value at a specific tab.
	 * 
	 * @param id
	 * @param newName
	 */
	public void updateSlot(int id, String newName) {
		updateSlot(id, newName, false);
	}

	/**
	 * Use this for changing a value at a specific tab.
	 * 
	 * @param id
	 * @param newName
	 */
	public void updateSlot(int id, String newName, boolean usePlayersSkin) {
		if (a()) {
			removeCustomTab(id, true);
			addValue(id, newName, usePlayersSkin);
			hasCustomTexture[id] = usePlayersSkin;
		} else {
			for (int i = id; i < size; i++)
				removeCustomTab(i, false);
			for (int i = id; i < size; i++)
				addValue(i, (i == id) ? newName : datasOLD.get(i).substring(2), false);
			// This is for pre 1.8, no textures needed
		}
	}

	/**
	 * removes a specific player from the player's tablist.
	 * 
	 * @param player
	 */
	@SuppressWarnings("unchecked")
	public void removePlayer(Player player) {
		Object packet = ReflectionUtil
				.instantiate((Constructor<?>) ReflectionUtil.getConstructor(PACKET_PLAYER_INFO_CLASS).get());
		if (ReflectionUtil.getInstanceField(packet, "b") instanceof List) {
			List<Object> players = (List<Object>) ReflectionUtil.getInstanceField(packet, "b");
			Object gameProfile = GAMEPROFILECLASS.cast(ReflectionUtil.invokeMethod(player, "getProfile", new Class[0]));
			Object[] array = (Object[]) ReflectionUtil.invokeMethod(CRAFT_CHAT_MESSAGE_CLASS, null, "fromString",
					new Class[] { String.class }, player.getName());
			Object data = ReflectionUtil.instantiate(PACKET_PLAYER_INFO_DATA_CONSTRUCTOR, packet, gameProfile, 1,
					WORLD_GAME_MODE_NOT_SET, array[0]);
			players.add(data);
			sendNEWTabPackets(player, packet, players, PACKET_PLAYER_INFO_ACTION_REMOVE_PLAYER);
		} else {
			try {
				sendOLDTabPackets(player, packet, player.getName(), false);
			} catch (Exception e) {
				error();
				e.printStackTrace();
			}
		}
	}

	/**
	 * Removes a custom tab from a player's tablist.
	 * 
	 * @param id
	 */
	public void removeCustomTab(int id) {
		removeCustomTab(id, true);
	}

	/**
	 * Removes a custom tab from a player's tablist.
	 * 
	 * @param id
	 */
	@SuppressWarnings("unchecked")
	private void removeCustomTab(int id, boolean remove) {
		Object packet = ReflectionUtil
				.instantiate((Constructor<?>) ReflectionUtil.getConstructor(PACKET_PLAYER_INFO_CLASS).get());
		if (ReflectionUtil.getInstanceField(packet, "b") instanceof List) {
			List<Object> players = (List<Object>) ReflectionUtil.getInstanceField(packet, "b");
			for (Object playerData : new ArrayList<>(datas)) {
				Object gameProfile = GAMEPROFILECLASS.cast(ReflectionUtil.invokeMethod(playerData, "a", new Class[0]));
				String getname = (String) ReflectionUtil.invokeMethod(gameProfile, "getName", null);
				if (getname.startsWith(getNameFromID(id))) {
					tabs[getIDFromName(getname)] = "";
					players.add(playerData);
					if (remove)
						datas.remove(playerData);
					break;
				}
			}
			sendNEWTabPackets(getPlayer(), packet, players, PACKET_PLAYER_INFO_ACTION_REMOVE_PLAYER);
		} else {
			try {
				sendOLDTabPackets(getPlayer(), packet, datasOLD.get(id), false);
				if (remove) {
					tabs[id] = null;
					datasOLD.remove(id);
				}
			} catch (Exception e) {
				error();
				e.printStackTrace();
			}
		}
	}

	/**
	 * 
	 * Use this to add an existing offline player to a player's tablist. The name
	 * variable is so you can modify a player's name in the tablist. If you want the
	 * player-tab to be the same as the player's name, use the other method
	 * 
	 * @param id
	 * @param name
	 * @param player
	 */
	public void addExistingPlayer(int id, String name, OfflinePlayer player) {
		addValue(id, name, player.getUniqueId(), true);
	}

	/**
	 * 
	 * Use this to add an existing offline player to a player's tablist.
	 * 
	 * @param id
	 * @param player
	 */
	public void addExistingPlayer(int id, OfflinePlayer player) {
		addExistingPlayer(id, player.getName(), player);
	}

	/**
	 * Use this to add a new player to the list
	 * 
	 * @param id
	 * @param name
	 * @deprecated If all 80 slots have been taken, new values will not be shown and
	 *             may have the potential to go out of the registered bounds. Use
	 *             the "updateSlot" method to change a slot.
	 */
	@Deprecated
	private void addValue(int id, String name, boolean shouldUseSkin) {
		UUID uuid = (name.length() > 0 && Bukkit.getOfflinePlayer(name).hasPlayedBefore())
				? Bukkit.getOfflinePlayer(name).getUniqueId()
				: UUID.randomUUID();
		this.addValue(id, name, uuid, shouldUseSkin);
	}

	/**
	 * Use this to add a new player to the list
	 * 
	 * @param id
	 * @param name
	 * @deprecated If all 80 slots have been taken, new values will not be shown and
	 *             may have the potential to go out of the registered bounds. Use
	 *             the "updateSlot" method to change a slot.
	 */
	@SuppressWarnings("unchecked")
	@Deprecated
	private void addValue(int id, String name, UUID uuid, boolean updateProfToAddCustomSkin) {
		Object packet = ReflectionUtil
				.instantiate((Constructor<?>) ReflectionUtil.getConstructor(PACKET_PLAYER_INFO_CLASS).get());
		if (ReflectionUtil.getInstanceField(packet, "b") instanceof List) {
			List<Object> players = (List<Object>) ReflectionUtil.getInstanceField(packet, "b");
			Object gameProfile = Bukkit.getPlayer(uuid) != null
					? ReflectionUtil.invokeMethod(getHandle(Bukkit.getPlayer(uuid)), "getProfile", new Class[0])
					: ReflectionUtil.instantiate(GAMEPROPHILECONSTRUCTOR, uuid, getNameFromID(id));
			Object[] array = (Object[]) ReflectionUtil.invokeMethod(CRAFT_CHAT_MESSAGE_CLASS, null, "fromString",
					new Class[] { String.class }, getNameFromID(id) + name);
			Object data = ReflectionUtil.instantiate(PACKET_PLAYER_INFO_DATA_CONSTRUCTOR, packet, gameProfile, 1,
					WORLD_GAME_MODE_NOT_SET, array[0]);
			SkinCallBack call = new SkinCallBack() {
				@Override
				public void callBack(Skin skin, boolean successful, Exception exception) {
					Object profile = GAMEPROFILECLASS.cast(ReflectionUtil.invokeMethod(data, "a", new Class[0]));
					if (successful) {
						try {
							Object map = ReflectionUtil.invokeMethod(profile, "getProperties", new Class[0]);
							if (skin.getBase64() != null && skin.getSignedBase64() != null) {
								ReflectionUtil.invokeMethod(map, "removeAll", new Class[] { String.class }, "textures");
								Object prop = ReflectionUtil.instantiate(PROPERTY_CONSTRUCTOR, "textures",
										skin.getBase64(), skin.getSignedBase64());
								Method m = null;
								for (Method mm : PROPERTY_MAP.getMethods())
									if (mm.getName().equals("put"))
										m = mm;
								try {
									if (m != null)
										m.invoke(map, "textures", prop);
								} catch (Exception e) {
									e.printStackTrace();
								}
							}
						} catch (Error e) {
						}
					}
					String getname = (String) ReflectionUtil.invokeMethod(profile, "getName", null);
					tabs[getIDFromName(getname)] = getname;
					players.add(data);
					datas.add(data);
					sendNEWTabPackets(getPlayer(), packet, players, PACKET_PLAYER_INFO_ACTION_ADD_PLAYER);
				}
			};
			if (updateProfToAddCustomSkin) {
				Skin.getSkin(name, call);
			} else {
				Skin.getSkin("aaa", call);
			}
		} else {
			sendOLDTabPackets(getPlayer(), packet, getNameFromID(id) + name, true);
			tabs[id] = name;
			datasOLD.put(id, getNameFromID(id) + name);
		}
	}

	/**
	 * This is used to create the table. If you want to create a custom tablist,
	 * then this should be called right after the playlist instance has been
	 * created.
	 */
	public void initTable() {
		clearAll();
		for (int i = 0; i < size; i++)
			updateSlot(i, "", false);
	}

	private static void sendNEWTabPackets(Player player, Object packet, List<?> players, Object action) {
		try {
			ReflectionUtil.setInstanceField(packet, "a", action);
			ReflectionUtil.setInstanceField(packet, "b", players);
			sendPacket(packet, player);
		} catch (Exception e) {
			error();
			e.printStackTrace();
		}

	}

	private static void sendOLDTabPackets(Player player, Object packet, String name, boolean isOnline) {
		try {
			ReflectionUtil.setInstanceField(packet, "a", name);
			ReflectionUtil.setInstanceField(packet, "b", isOnline);
			ReflectionUtil.setInstanceField(packet, "c", ((short) 0));
			sendPacket(packet, player);
		} catch (Exception e) {
			error();
			e.printStackTrace();
		}
	}

	private static void sendPacket(Object packet, Player player) {
		Object handle = getHandle(player);
		Object playerConnection = ReflectionUtil.getInstanceField(handle, "playerConnection");
		ReflectionUtil.invokeMethod(playerConnection, "sendPacket", new Class[] { PACKET_CLASS }, packet);
	}

	private static Object getHandle(Player player) {
		return ReflectionUtil.invokeMethod(CRAFTPLAYERCLASS.cast(player), "getHandle", new Class[0]);
	}

	/**
	 * Returns the player.
	 * 
	 * @return the player (if they are online), or null (if they are offline)
	 */
	public Player getPlayer() {
		return Bukkit.getPlayer(this.ownerUUID);
	}

	/**
	 * This returns the ID of a slot at [Row,Columb].
	 * 
	 * @param row
	 * @param col
	 * 
	 * @return
	 */
	public int getID(int row, int col) {
		return (col * 20) + row;
	}

	private static String getNameFromID(int id) {
		String[] a = colorcodeOrder;
		int size1 = 15;
		if (!a()) {
			a = inviscodeOrder;
			size1 = 5;
		}
		String firstletter = a[id / size1];
		String secondletter = a[id % size1];
		if (a())
			return ChatColor.getByChar(firstletter) + "" + ChatColor.getByChar(secondletter) + ChatColor.RESET;
		return firstletter + secondletter;
	}

	private static int getIDFromName(String id) {
		String[] a = colorcodeOrder;
		int size1 = 15;
		int indexAdder = 0;
		if (!a()) {
			a = inviscodeOrder;
			size1 = 5;
			indexAdder = 1;
		}
		int total = 0;
		for (int i = 0; i < a.length; i++) {
			if (a[i].equalsIgnoreCase(id.charAt(0 + indexAdder) + "")) {
				total = size1 * i;
				break;
			}
		}
		for (int i = 0; i < a.length; i++) {
			if (a[i].equalsIgnoreCase(id.charAt(1 + (indexAdder + indexAdder)) + "")) {
				total += i;
				break;
			}
		}
		return total;
	}

	private static void error() {
		Bukkit.broadcastMessage("PLEASE REPORT THIS ISSUE TO" + ChatColor.RED + " ZOMBIE_STRIKER" + ChatColor.RESET
				+ " ON THE BUKKIT FORUMS");
	}
}

/**
 * A small help with reflection
 */
class ReflectionUtil {
	protected static final String SERVER_VERSION;
	static {
		String name = Bukkit.getServer().getClass().getName();
		name = name.substring(name.indexOf("craftbukkit.") + "craftbukkit.".length());
		name = name.substring(0, name.indexOf("."));
		SERVER_VERSION = name;
	}

	protected static boolean isVersionHigherThan(int mainVersion, int secondVersion) {
		String firstChar = SERVER_VERSION.substring(1, 2);
		int fInt = Integer.parseInt(firstChar);
		if (fInt < mainVersion)
			return false;
		StringBuilder secondChar = new StringBuilder();
		for (int i = 3; i < 10; i++) {
			if (SERVER_VERSION.charAt(i) == '_' || SERVER_VERSION.charAt(i) == '.')
				break;
			secondChar.append(SERVER_VERSION.charAt(i));
		}
		int sInt = Integer.parseInt(secondChar.toString());
		if (sInt < secondVersion)
			return false;
		return true;
	}

	/**
	 * Returns the NMS class.
	 * 
	 * @param name
	 *            The name of the class
	 * 
	 * @return The NMS class or null if an error occurred
	 */
	protected static Class<?> getNMSClass(String name) {
		try {
			return Class.forName("net.minecraft.server." + SERVER_VERSION + "." + name);
		} catch (ClassNotFoundException e) {
			return null;
		}
	}

	/**
	 * Returns the NMS class.
	 * 
	 * @param name
	 *            The name of the class
	 * 
	 * @return The NMS class or null if an error occurred
	 */
	protected static Class<?> getOLDAuthlibClass(String name) {
		try {
			return Class.forName("net.minecraft.util.com.mojang.authlib." + name);
		} catch (ClassNotFoundException e) {
			return null;
		}
	}

	/**
	 * Returns the CraftBukkit class.
	 * 
	 * @param name
	 *            The name of the class
	 * 
	 * @return The CraftBukkit class or null if an error occurred
	 */

	protected static Class<?> getCraftbukkitClass(String name, String packageName) {
		try {
			return Class.forName("org.bukkit.craftbukkit." + SERVER_VERSION + "." + packageName + "." + name);
		} catch (ClassNotFoundException e) {
			return null;
		}
	}

	/**
	 * Returns the mojang.authlib class.
	 * 
	 * @param name
	 *            The name of the class
	 * 
	 * @return The mojang.authlib class or null if an error occurred
	 */

	protected static Class<?> getMojangAuthClass(String name) {
		try {
			if (PlayerList.a()) {
				return Class.forName("com.mojang.authlib." + name);
			} else {
				return Class.forName("net.minecraft.util.com.mojang.authlib." + name);
			}
		} catch (ClassNotFoundException e) {
			return null;
		}
	}

	/**
	 * Invokes the method
	 * 
	 * @param handle
	 *            The handle to invoke it on
	 * @param methodName
	 *            The name of the method
	 * @param parameterClasses
	 *            The parameter types
	 * @param args
	 *            The arguments
	 * 
	 * @return The resulting object or null if an error occurred / the method didn't
	 *         return a thing
	 */
	@SuppressWarnings("rawtypes")
	protected static Object invokeMethod(Object handle, String methodName, Class[] parameterClasses, Object... args) {
		return invokeMethod(handle.getClass(), handle, methodName, parameterClasses, args);
	}

	/**
	 * Invokes the method
	 * 
	 * @param clazz
	 *            The class to invoke it from
	 * @param handle
	 *            The handle to invoke it on
	 * @param methodName
	 *            The name of the method
	 * @param parameterClasses
	 *            The parameter types
	 * @param args
	 *            The arguments
	 * 
	 * @return The resulting object or null if an error occurred / the method didn't
	 *         return a thing
	 */
	@SuppressWarnings("rawtypes")
	protected static Object invokeMethod(Class<?> clazz, Object handle, String methodName, Class[] parameterClasses,
			Object... args) {
		Optional<Method> methodOptional = getMethod(clazz, methodName, parameterClasses);
		if (!methodOptional.isPresent())
			return null;
		Method method = methodOptional.get();
		try {
			return method.invoke(handle, args);
		} catch (IllegalAccessException | InvocationTargetException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Sets the value of an instance field
	 * 
	 * @param handle
	 *            The handle to invoke it on
	 * @param name
	 *            The name of the field
	 * @param value
	 *            The new value of the field
	 */
	protected static void setInstanceField(Object handle, String name, Object value) {
		Class<?> clazz = handle.getClass();
		Optional<Field> fieldOptional = getField(clazz, name);
		if (!fieldOptional.isPresent())
			return;
		Field field = fieldOptional.get();
		if (!field.isAccessible())
			field.setAccessible(true);
		try {
			field.set(handle, value);
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Sets the value of an instance field
	 * 
	 * @param handle
	 *            The handle to invoke it on
	 * @param name
	 *            The name of the field
	 * 
	 * @return The result
	 */
	protected static Object getInstanceField(Object handle, String name) {
		Class<?> clazz = handle.getClass();
		Optional<Field> fieldOptional = getField(clazz, name);
		if (!fieldOptional.isPresent())
			return handle;
		Field field = fieldOptional.get();
		if (!field.isAccessible())
			field.setAccessible(true);
		try {
			return field.get(handle);
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Returns an enum constant
	 * 
	 * @param enumClass
	 *            The class of the enum
	 * @param name
	 *            The name of the enum constant
	 * 
	 * @return The enum entry or null
	 */
	protected static Object getEnumConstant(Class<?> enumClass, String name) {
		if (!enumClass.isEnum())
			return null;
		for (Object o : enumClass.getEnumConstants())
			if (name.equals(invokeMethod(o, "name", new Class[0])))
				return o;
		return null;
	}

	/**
	 * Returns the constructor
	 * 
	 * @param clazz
	 *            The class
	 * @param params
	 *            The Constructor parameters
	 * 
	 * @return The Constructor or an empty Optional if there is none with these
	 *         parameters
	 */
	protected static Optional<?> getConstructor(Class<?> clazz, Class<?>... params) {
		try {
			return Optional.of(clazz.getConstructor(params));
		} catch (NoSuchMethodException e) {
			try {
				return Optional.of(clazz.getDeclaredConstructor(params));
			} catch (NoSuchMethodException e2) {
				e2.printStackTrace();
			}
		}
		return Optional.empty();
	}

	/**
	 * Instantiates the class. Will print the errors it gets
	 * 
	 * @param constructor
	 *            The constructor
	 * @param arguments
	 *            The initial arguments
	 * 
	 * @return The resulting object, or null if an error occurred.
	 */
	protected static Object instantiate(Constructor<?> constructor, Object... arguments) {
		try {
			return constructor.newInstance(arguments);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	protected static Optional<Method> getMethod(Class<?> clazz, String name, Class<?>... params) {
		try {
			return Optional.of(clazz.getMethod(name, params));
		} catch (NoSuchMethodException e) {
			try {
				return Optional.of(clazz.getDeclaredMethod(name, params));
			} catch (NoSuchMethodException e2) {
				e2.printStackTrace();
			}
		}
		return Optional.empty();
	}

	protected static Optional<Field> getField(Class<?> clazz, String name) {
		try {
			return Optional.of(clazz.getField(name));
		} catch (NoSuchFieldException e) {
			try {
				return Optional.of(clazz.getDeclaredField(name));
			} catch (NoSuchFieldException e2) {
			}
		}
		return Optional.empty();
	}
}

interface SkinCallBack {
	void callBack(Skin skin, boolean successful, Exception exception);
}

/**
 * Stores information about a minecraft user's skin.
 *
 * This class does implement ConfigurationSerializable, which means that you can
 * use it to save skins in config, but do however note that for the class to be
 * registered correctly, you should always call NameTagChanger.INSTANCE.enable()
 * in your onEnable() (not before checking if it is already enabled, of course)
 * and call NameTagChanger.INSTANCE.disable() in your onDisable (and again,
 * check if NameTagChanger is already disabled first).
 *
 * @author AlvinB
 */
class Skin implements ConfigurationSerializable {

	// Access to this must be asynchronous!
	// private static final LoadingCache<UUID, Skin> SKIN_CACHE = CacheBuilder
	private static Object SKIN_CACHE;

	private static boolean skin_Enabled = false;

	static {
		try {
			SKIN_CACHE = CacheBuilder.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES)
					.build(new CacheLoader<UUID, Skin>() {
						@Override
						public Skin load(UUID uuid) throws Exception {
							MojangAPIUtil.Result<MojangAPIUtil.SkinData> result = MojangAPIUtil.getSkinData(uuid);
							if (result.wasSuccessful()) {
								if (result.getValue() != null) {
									MojangAPIUtil.SkinData data = result.getValue();
									if (data.getSkinURL() == null && data.getCapeURL() == null) {
										return Skin.EMPTY_SKIN;
									}
									return new Skin(data.getUUID(), data.getBase64(), data.getSignedBase64());
								}
							} else {
								throw result.getException();
							}
							return Skin.EMPTY_SKIN;
						}
					});
			skin_Enabled = true;
		} catch (Exception | Error e5) {
		}
	}

	static Map<UUID, String> callbacksUUID = new HashMap<UUID, String>();
	static Map<String, List<SkinCallBack>> callbacks = new HashMap<String, List<SkinCallBack>>();

	/**
	 * Gets the skin for a username.
	 * <p>
	 * Since fetching this skin requires making asynchronous requests to Mojang's
	 * servers, a call back mechanism using the SkinCallBack class is implemented.
	 * This call back allows you to also handle any errors that might have occurred
	 * while fetching the skin. If no users with the specified username can be
	 * found, the skin passed to the callback will be Skin.EMPTY_SKIN.
	 * <p>
	 * The call back will always be fired on the main thread.
	 *
	 * @param username
	 *            the username to get the skin of
	 * @param callBack
	 *            the call back to handle the result of the request
	 */
	public static void getSkin(String username, SkinCallBack callBack) {
		boolean newcall = false;
		if (!callbacks.containsKey(username)) {
			callbacks.put(username, new ArrayList<SkinCallBack>());
			newcall = true;
		}
		callbacks.get(username).add(callBack);

		if (newcall) {
			new BukkitRunnable() {
				String u = username;

				@Override
				public void run() {
					MojangAPIUtil.Result<Map<String, MojangAPIUtil.Profile>> result = MojangAPIUtil
							.getUUID(Collections.singletonList(username));
					if (result.wasSuccessful()) {
						if (result.getValue() == null || result.getValue().isEmpty()) {
							new BukkitRunnable() {
								@Override
								public void run() {
									List<SkinCallBack> calls = callbacks.get(u);
									callbacks.remove(u);
									for (SkinCallBack s : calls) {
										s.callBack(Skin.EMPTY_SKIN, true, null);
									}
								}
							}.runTask(PlayerList.plugin);
							return;
						}
						for (Map.Entry<String, MojangAPIUtil.Profile> entry : result.getValue().entrySet()) {
							if (entry.getKey().equalsIgnoreCase(username)) {
								callbacksUUID.put(entry.getValue().getUUID(), u);
								getSkin(entry.getValue().getUUID(), callBack);
								return;
							}
						}
					} else {
						new BukkitRunnable() {
							@Override
							public void run() {
								List<SkinCallBack> calls = callbacks.get(u);
								callbacks.remove(u);
								for (SkinCallBack s : calls) {
									s.callBack(null, false, result.getException());
								}
							}
						}.runTask(PlayerList.plugin);
					}
				}
			}.runTaskAsynchronously(PlayerList.plugin);
		}
	}

	/**
	 * Gets the skin for a UUID.
	 * <p>
	 * Since fetching this skin might require making asynchronous requests to
	 * Mojang's servers, a call back mechanism using the SkinCallBack class is
	 * implemented. This call back allows you to also handle any errors that might
	 * have occurred while fetching the skin.
	 * <p>
	 * The call back will always be fired on the main thread.
	 *
	 * @param uuid
	 *            the uuid to get the skin of
	 * @param callBack
	 *            the call back to handle the result of the request
	 */
	public static void getSkin(UUID uuid, SkinCallBack callBack) {
		if(!skin_Enabled) {
			callBack.callBack(Skin.EMPTY_SKIN, true, null);
			return;
		}
		// Map<UUID, Skin> asMap = SKIN_CACHE.asMap();
		@SuppressWarnings("unchecked")
		Map<UUID, Skin> asMap = (Map<UUID, Skin>) ReflectionUtil.invokeMethod(SKIN_CACHE, "asMap", new Class[0]);
		if (asMap.containsKey(uuid)) {
			for (SkinCallBack s : callbacks.get(callbacksUUID.get(uuid))) {
				s.callBack(asMap.get(uuid), true, null);
			}
		} else {
			new BukkitRunnable() {
				@Override
				public void run() {
					try {
						// Skin skin = SKIN_CACHE.get(uuid);
						Skin skin = (Skin) ReflectionUtil.invokeMethod(SKIN_CACHE, "get", new Class[] { UUID.class },
								uuid);
						new BukkitRunnable() {
							@Override
							public void run() {
								for (SkinCallBack s : callbacks.get(callbacksUUID.get(uuid))) {
									s.callBack(skin, true, null);
								}
							}
						}.runTask(PlayerList.plugin);
					} catch (Exception e) {
						new BukkitRunnable() {
							@Override
							public void run() {
								for (SkinCallBack s : callbacks.get(callbacksUUID.get(uuid))) {
									s.callBack(null, false, e);
								}
							}
						}.runTask(PlayerList.plugin);
					}
				}
			}.runTaskAsynchronously(PlayerList.plugin);
		}
	}

	public static final Skin EMPTY_SKIN = new Skin();

	private UUID uuid;
	private String base64;
	private String signedBase64;

	/**
	 * Initializes this class with the specified skin.
	 *
	 * @param uuid
	 *            The uuid of the user who this skin belongs to
	 * @param base64
	 *            the base64 data of the skin, as returned by Mojang's servers.
	 * @param signedBase64
	 *            the signed data of the skin, as returned by Mojang's servers.
	 */
	public Skin(UUID uuid, String base64, String signedBase64) {
		Validate.notNull(uuid, "uuid cannot be null");
		Validate.notNull(base64, "base64 cannot be null");
		this.uuid = uuid;
		this.base64 = base64;
		this.signedBase64 = signedBase64;
	}

	private Skin() {
	}

	public boolean hasSignedBase64() {
		return signedBase64 != null;
	}

	public String getSignedBase64() {
		return signedBase64;
	}

	public String getBase64() {
		return base64;
	}

	public UUID getUUID() {
		return uuid;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (!(obj instanceof Skin)) {
			return false;
		}
		Skin skin = (Skin) obj;
		if (skin == Skin.EMPTY_SKIN) {
			return this == Skin.EMPTY_SKIN;
		}
		return skin.base64.equals(this.base64) && skin.uuid.equals(this.uuid)
				&& skin.signedBase64.equals(this.signedBase64);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.base64, this.uuid, this.signedBase64);
	}

	@Override
	public String toString() {
		return "Skin{uuid=" + uuid + ",base64=" + base64 + ",signedBase64=" + signedBase64 + "}";
	}

	@Override
	public Map<String, Object> serialize() {
		Map<String, Object> map = Maps.newHashMap();
		if (this == EMPTY_SKIN) {
			map.put("empty", "true");
		} else {
			map.put("uuid", uuid);
			map.put("base64", base64);
			if (hasSignedBase64()) {
				map.put("signedBase64", signedBase64);
			}
		}
		return map;
	}

	public static Skin deserialize(Map<String, Object> map) {
		if (map.containsKey("empty")) {
			return EMPTY_SKIN;
		} else {
			return new Skin(UUID.fromString((String) map.get("uuid")), (String) map.get("base64"),
					(map.containsKey("signedBase64") ? (String) map.get("signedBase64") : null));
		}
	}
}

/**
 * Implementation to make requests to Mojang's API servers. See
 * http://wiki.vg/Mojang_API for more information.
 * <p>
 * Since all of these methods require connections to Mojang's servers, all of
 * them execute asynchronously, and do therefor not return any values. Instead,
 * a callback mechanism is implemented, which allows for processing of data
 * returned from these requests. If an error occurs when retrieving the data,
 * the 'successful' boolean in the callback will be set to false. In these
 * cases, null will be passed to the callback, even if some data has been
 * received.
 * <p>
 * Each method has an synchronous and an asynchronous version. It is recommended
 * that you use the synchronous version unless you're intending to do more tasks
 * that should be executed asynchronously.
 *
 * @author AlvinB
 */
class MojangAPIUtil {
	private static URL API_STATUS_URL = null;
	private static URL GET_UUID_URL = null;
	private static final JSONParser PARSER = new JSONParser();

	private static Plugin plugin;

	static {
		for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
			if (plugin.getClass().getProtectionDomain().getCodeSource()
					.equals(MojangAPIUtil.class.getProtectionDomain().getCodeSource())) {
				MojangAPIUtil.plugin = plugin;
			}
		}
		try {
			API_STATUS_URL = new URL("https://status.mojang.com/check");
			GET_UUID_URL = new URL("https://api.mojang.com/profiles/minecraft");
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Sets the plugin instance to use for scheduler tasks.
	 * <p>
	 * The plugin instance in the same jar as this class should automatically be
	 * found, so only use this if you for whatever reason need to use another plugin
	 * instance.
	 *
	 * @param plugin
	 *            the plugin instance
	 */
	public static void setPlugin(Plugin plugin) {
		MojangAPIUtil.plugin = plugin;
	}

	/**
	 * Same as #getAPIStatusAsync, but the callback is executed synchronously
	 */
	public static void getAPIStatusWithCallBack(ResultCallBack<Map<String, APIStatus>> callBack) {
		getAPIStatusAsyncWithCallBack((successful, result, exception) -> new BukkitRunnable() {
			@Override
			public void run() {
				callBack.callBack(successful, result, exception);
			}
		}.runTask(plugin));
	}

	/**
	 * Gets the current state of Mojang's API
	 * <p>
	 * The keys of the map passed to the callback is the service, and the value is
	 * the current state of the service. Statuses can be either RED (meaning service
	 * unavailable), YELLOW (meaning service available, but with some issues) and
	 * GREEN (meaning service fully functional).
	 *
	 * @param callBack
	 *            the callback of the request
	 * @see APIStatus
	 */
	@SuppressWarnings("unchecked")
	public static void getAPIStatusAsyncWithCallBack(ResultCallBack<Map<String, APIStatus>> callBack) {
		if (plugin == null) {
			return;
		}
		makeAsyncGetRequest(API_STATUS_URL, (successful, response, exception, responseCode) -> {
			if (callBack == null) {
				return;
			}
			if (successful && responseCode == 200) {
				try {
					Map<String, APIStatus> map = Maps.newHashMap();
					JSONArray jsonArray = (JSONArray) PARSER.parse(response);
					for (JSONObject jsonObject : (List<JSONObject>) jsonArray) {
						for (JSONObject.Entry<String, String> entry : ((Map<String, String>) jsonObject).entrySet()) {
							map.put(entry.getKey(), APIStatus.fromString(entry.getValue()));
						}
					}
					callBack.callBack(true, map, null);
				} catch (Exception e) {
					callBack.callBack(false, null, e);
				}
			} else {
				if (exception != null) {
					callBack.callBack(false, null, exception);
				} else {
					callBack.callBack(false, null,
							new IOException("Failed to obtain Mojang data! Response code: " + responseCode));
				}
			}
		});
	}

	/**
	 * The statuses of Mojang's API used by getAPIStatus().
	 */
	public enum APIStatus {
		RED, YELLOW, GREEN;

		public static APIStatus fromString(String string) {
			switch (string) {
			case "red":
				return RED;
			case "yellow":
				return YELLOW;
			case "green":
				return GREEN;
			default:
				throw new IllegalArgumentException("Unknown status: " + string);
			}
		}
	}

	/**
	 * Same as #getUUIDAtTimeAsync, but the callback is executed synchronously
	 */
	public static void getUUIDAtTimeWithCallBack(String username, long timeStamp, ResultCallBack<UUIDAtTime> callBack) {
		getUUIDAtTimeAsyncWithCallBack(username, timeStamp, (successful, result, exception) -> new BukkitRunnable() {
			@Override
			public void run() {
				callBack.callBack(successful, result, exception);
			}
		}.runTask(plugin));
	}

	/**
	 * Gets the UUID of a name at a certain point in time
	 * <p>
	 * The timestamp is in UNIX Time, and if -1 is used as the timestamp, it will
	 * get the current user who has this name.
	 * <p>
	 * The callback contains the UUID and the current username of the UUID. If the
	 * username was not occupied at the specified time, the next person to occupy
	 * the name will be returned, provided that the name has been changed away from
	 * at least once or is legacy. If the name hasn't been changed away from and is
	 * not legacy, the value passed to the callback will be null.
	 *
	 * @param username
	 *            the username of the player to do the UUID lookup on
	 * @param timeStamp
	 *            the timestamp when the name was occupied
	 * @param callBack
	 *            the callback of the request
	 */
	public static void getUUIDAtTimeAsyncWithCallBack(String username, long timeStamp,
			ResultCallBack<UUIDAtTime> callBack) {
		if (plugin == null) {
			return;
		}
		Validate.notNull(username);
		Validate.isTrue(!username.isEmpty(), "username cannot be empty");
		try {
			URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + username
					+ (timeStamp != -1 ? "?at=" + timeStamp : ""));
			makeAsyncGetRequest(url, (successful, response, exception, responseCode) -> {
				if (callBack == null) {
					return;
				}
				if (successful && (responseCode == 200 || responseCode == 204)) {
					try {
						UUIDAtTime[] uuidAtTime = new UUIDAtTime[1];
						if (responseCode == 200) {
							JSONObject object = (JSONObject) PARSER.parse(response);
							String uuidString = (String) object.get("id");
							uuidAtTime[0] = new UUIDAtTime((String) object.get("name"), getUUIDFromString(uuidString));
						}
						callBack.callBack(true, uuidAtTime[0], null);
					} catch (Exception e) {
						callBack.callBack(false, null, e);
					}
				} else {
					if (exception != null) {
						callBack.callBack(false, null, exception);
					} else {
						callBack.callBack(false, null,
								new IOException("Failed to obtain Mojang data! Response code: " + responseCode));
					}
				}
			});
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
	}

	public static class UUIDAtTime {
		private String name;
		private UUID uuid;

		public UUIDAtTime(String name, UUID uuid) {
			this.name = name;
			this.uuid = uuid;
		}

		public String getName() {
			return name;
		}

		public UUID getUUID() {
			return uuid;
		}

		@Override
		public String toString() {
			return "UUIDAtTime{name=" + name + ",uuid=" + uuid + "}";
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) {
				return true;
			}
			if (!(obj instanceof UUIDAtTime)) {
				return false;
			}
			UUIDAtTime uuidAtTime = (UUIDAtTime) obj;
			return this.name.equals(uuidAtTime.name) && this.uuid.equals(uuidAtTime.uuid);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.name, this.uuid);
		}
	}

	/**
	 * Same as #getNameHistoryAsync, but the callback is executed synchronously
	 */
	public static void getNameHistoryWithCallBack(UUID uuid, ResultCallBack<Map<String, Long>> callBack) {
		getNameHistoryAsyncWithCallBack(uuid, (successful, result, exception) -> new BukkitRunnable() {
			@Override
			public void run() {
				callBack.callBack(successful, result, exception);
			}
		}.runTask(plugin));
	}

	/**
	 * Gets the name history of a certain UUID
	 * <p>
	 * The callback is passed a Map<String, Long>, the String being the name, and
	 * the long being the UNIX millisecond timestamp the user changed to that name.
	 * If the name was the original name of the user, the long will be -1L.
	 * <p>
	 * If an unused UUID is supplied, an empty Map will be passed to the callback.
	 *
	 * @param uuid
	 *            the uuid of the account
	 * @param callBack
	 *            the callback of the request
	 */
	@SuppressWarnings("unchecked")
	public static void getNameHistoryAsyncWithCallBack(UUID uuid, ResultCallBack<Map<String, Long>> callBack) {
		if (plugin == null) {
			return;
		}
		Validate.notNull(uuid, "uuid cannot be null!");
		try {
			URL url = new URL("https://api.mojang.com/user/profiles/" + uuid.toString().replace("-", "") + "/names");
			makeAsyncGetRequest(url, (successful, response, exception, responseCode) -> {
				if (callBack == null) {
					return;
				}
				if (successful && (responseCode == 200 || responseCode == 204)) {
					try {
						Map<String, Long> map = Maps.newHashMap();
						if (responseCode == 200) {
							JSONArray jsonArray = (JSONArray) PARSER.parse(response);
							for (JSONObject jsonObject : (List<JSONObject>) jsonArray) {
								String name = (String) jsonObject.get("name");
								if (jsonObject.containsKey("changedToAt")) {
									map.put(name, (Long) jsonObject.get("changedToAt"));
								} else {
									map.put(name, -1L);
								}
							}
						}
						callBack.callBack(true, map, null);
					} catch (Exception e) {
						callBack.callBack(false, null, e);
					}
				} else {
					if (exception != null) {
						callBack.callBack(false, null, exception);
					} else {
						callBack.callBack(false, null,
								new IOException("Failed to obtain Mojang data! Response code: " + responseCode));
					}
				}
			});
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
	}

	public static void getUUIDWithCallBack(ResultCallBack<Map<String, Profile>> callBack, String... usernames) {
		getUUIDWithCallBack(Arrays.asList(usernames), callBack);
	}

	/**
	 * Same as #getUUIDAsync, but the callback is executed synchronously
	 */
	public static void getUUIDWithCallBack(List<String> usernames, ResultCallBack<Map<String, Profile>> callBack) {
		getUUIDAsyncWithCallBack(usernames, (successful, result, exception) -> new BukkitRunnable() {
			@Override
			public void run() {
				callBack.callBack(successful, result, exception);
			}
		}.runTask(plugin));
	}

	public static void getUUIDAsyncWithCallBack(ResultCallBack<Map<String, Profile>> callBack, String... usernames) {
		getUUIDAsyncWithCallBack(Arrays.asList(usernames), callBack);
	}

	/**
	 * Same as #getUUIDWithCallBack but is entirely executed on the current thread.
	 * Should be used with caution to avoid blocking any important activities on the
	 * current thread.
	 */
	@SuppressWarnings("unchecked")
	public static Result<Map<String, Profile>> getUUID(List<String> usernames) {
		if (plugin == null) {
			return new Result<>(null, false, new RuntimeException("No plugin instance found!"));
		}
		Validate.notNull(usernames, "usernames cannot be null");
		Validate.isTrue(usernames.size() <= 100, "cannot request more than 100 usernames at once");
		JSONArray usernameJson = new JSONArray();
		usernameJson.addAll(usernames.stream().filter(s -> !Strings.isNullOrEmpty(s)).collect(Collectors.toList()));
		RequestResult result = makeSyncPostRequest(GET_UUID_URL, usernameJson.toJSONString());
		if (result == null) {
			return new Result<>(null, false, new RuntimeException("No plugin instance found!"));
		}
		try {
			if (result.successful && result.responseCode == 200) {
				Map<String, Profile> map = Maps.newHashMap();
				JSONArray jsonArray = (JSONArray) PARSER.parse(result.response);
				// noinspection Duplicates
				for (JSONObject jsonObject : (List<JSONObject>) jsonArray) {
					String uuidString = (String) jsonObject.get("id");
					String name = (String) jsonObject.get("name");
					boolean legacy = false;
					if (jsonObject.containsKey("legacy")) {
						legacy = (boolean) jsonObject.get("legacy");
					}
					boolean unpaid = false;
					if (jsonObject.containsKey("demo")) {
						unpaid = (boolean) jsonObject.get("demo");
					}
					map.put(name, new Profile(getUUIDFromString(uuidString), name, legacy, unpaid));
				}
				return new Result<>(map, true, null);
			} else {
				if (result.exception != null) {
					return new Result<>(null, false, result.exception);
				} else {
					return new Result<>(null, false,
							new IOException("Failed to obtain Mojang data! Response code: " + result.responseCode));
				}
			}
		} catch (Exception e) {
			return new Result<>(null, false, e);
		}
	}

	/**
	 * Gets the Profiles of up to 100 usernames.
	 *
	 * @param usernames
	 *            the usernames
	 * @param callBack
	 *            the callback
	 */
	@SuppressWarnings("unchecked")
	public static void getUUIDAsyncWithCallBack(List<String> usernames, ResultCallBack<Map<String, Profile>> callBack) {
		if (plugin == null) {
			return;
		}
		Validate.notNull(usernames, "usernames cannot be null");
		Validate.isTrue(usernames.size() <= 100, "cannot request more than 100 usernames at once");
		JSONArray usernameJson = new JSONArray();
		usernameJson.addAll(usernames.stream().filter(s -> !Strings.isNullOrEmpty(s)).collect(Collectors.toList()));
		makeAsyncPostRequest(GET_UUID_URL, usernameJson.toJSONString(),
				(successful, response, exception, responseCode) -> {
					if (callBack == null) {
						return;
					}
					try {
						if (successful && responseCode == 200) {
							Map<String, Profile> map = Maps.newHashMap();
							JSONArray jsonArray = (JSONArray) PARSER.parse(response);
							// noinspection Duplicates
							for (JSONObject jsonObject : (List<JSONObject>) jsonArray) {
								String uuidString = (String) jsonObject.get("id");
								String name = (String) jsonObject.get("name");
								boolean legacy = false;
								if (jsonObject.containsKey("legacy")) {
									legacy = (boolean) jsonObject.get("legacy");
								}
								boolean unpaid = false;
								if (jsonObject.containsKey("demo")) {
									unpaid = (boolean) jsonObject.get("demo");
								}
								map.put(name, new Profile(getUUIDFromString(uuidString), name, legacy, unpaid));
							}
							callBack.callBack(true, map, null);
						} else {
							if (exception != null) {
								callBack.callBack(false, null, exception);
							} else {
								callBack.callBack(false, null, new IOException(
										"Failed to obtain Mojang data! Response code: " + responseCode));
							}
						}
					} catch (Exception e) {
						callBack.callBack(false, null, e);
					}
				});
	}

	public static class Profile {
		private UUID uuid;
		private String name;
		private boolean legacy;
		private boolean unpaid;

		Profile(UUID uuid, String name, boolean legacy, boolean unpaid) {
			this.uuid = uuid;
			this.name = name;
			this.legacy = legacy;
			this.unpaid = unpaid;
		}

		public UUID getUUID() {
			return uuid;
		}

		public String getName() {
			return name;
		}

		public boolean isLegacy() {
			return legacy;
		}

		public boolean isUnpaid() {
			return unpaid;
		}

		@Override
		public String toString() {
			return "Profile{uuid=" + uuid + ", name=" + name + ", legacy=" + legacy + ", unpaid=" + unpaid + "}";
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) {
				return true;
			}
			if (!(obj instanceof Profile)) {
				return false;
			}
			Profile otherProfile = (Profile) obj;
			return uuid.equals(otherProfile.uuid) && name.equals(otherProfile.name) && legacy == otherProfile.legacy
					&& unpaid == otherProfile.unpaid;
		}

		@Override
		public int hashCode() {
			return Objects.hash(uuid, name, legacy, unpaid);
		}
	}

	/**
	 * Same as #getSkinDataWithCallBack but is entirely executed on the current
	 * thread. Should be used with caution to avoid blocking any important
	 * activities on the current thread.
	 */
	@SuppressWarnings("unchecked")
	public static Result<SkinData> getSkinData(UUID uuid) {
		if (plugin == null) {
			return new Result<>(null, false, new RuntimeException("No plugin instance found!"));
		}
		URL url;
		try {
			url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/"
					+ uuid.toString().replace("-", "") + "?unsigned=false");
		} catch (MalformedURLException e) {
			return new Result<>(null, false, e);
		}
		RequestResult result = makeSyncGetRequest(url);
		if (result == null) {
			return new Result<>(null, false, new RuntimeException("No plugin instance found!"));
		}
		try {
			if (result.successful && (result.responseCode == 200 || result.responseCode == 204)) {
				if (result.responseCode == 204) {
					return new Result<>(null, true, null);
				}
				JSONObject object = (JSONObject) PARSER.parse(result.response);
				JSONArray propertiesArray = (JSONArray) object.get("properties");
				String base64 = null;
				String signedBase64 = null;
				// noinspection Duplicates
				for (JSONObject property : (List<JSONObject>) propertiesArray) {
					String name = (String) property.get("name");
					if (name.equals("textures")) {
						base64 = (String) property.get("value");
						signedBase64 = (String) property.get("signature");
					}
				}
				if (base64 == null) {
					return new Result<>(null, true, null);
				}
				String decodedBase64 = new String(Base64.getDecoder().decode(base64), "UTF-8");
				JSONObject base64json = (JSONObject) PARSER.parse(decodedBase64);
				long timeStamp = (long) base64json.get("timestamp");
				String profileName = (String) base64json.get("profileName");
				UUID profileId = getUUIDFromString((String) base64json.get("profileId"));
				JSONObject textures = (JSONObject) base64json.get("textures");
				String skinURL = null;
				String capeURL = null;
				if (textures.containsKey("SKIN")) {
					JSONObject skinObject = (JSONObject) textures.get("SKIN");
					skinURL = (String) skinObject.get("url");
				}
				if (textures.containsKey("CAPE")) {
					JSONObject capeObject = (JSONObject) textures.get("CAPE");
					capeURL = (String) capeObject.get("url");
				}
				return new Result<>(
						new SkinData(profileId, profileName, skinURL, capeURL, timeStamp, base64, signedBase64), true,
						null);
			} else {
				if (result.exception != null) {
					return new Result<>(null, false, result.exception);
				} else {
					return new Result<>(null, false,
							new IOException("Failed to obtain Mojang data! Response code: " + result.responseCode));
				}
			}
		} catch (Exception e) {
			return new Result<>(null, false, e);
		}
	}

	/**
	 * Same as #getSkinDataAsync, but the callback is executed synchronously
	 */
	public static void getSkinData(UUID uuid, ResultCallBack<SkinData> callBack) {
		getSkinDataAsync(uuid, (successful, result, exception) -> new BukkitRunnable() {
			@Override
			public void run() {
				callBack.callBack(successful, result, exception);
			}
		}.runTask(plugin));
	}

	/**
	 * Gets the Skin data for a certain user. If the user cannot be found, the value
	 * passed to the callback will be null.
	 *
	 * @param uuid
	 *            the uuid of the user
	 * @param callBack
	 *            the callback
	 */
	@SuppressWarnings("unchecked")
	public static void getSkinDataAsync(UUID uuid, ResultCallBack<SkinData> callBack) {
		if (plugin == null) {
			return;
		}
		URL url;
		try {
			url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/"
					+ uuid.toString().replace("-", "") + "?unsigned=false");
		} catch (MalformedURLException e) {
			e.printStackTrace();
			return;
		}
		makeAsyncGetRequest(url, (successful, response, exception, responseCode) -> {
			try {
				if (successful && (responseCode == 200 || responseCode == 204)) {
					if (responseCode == 204) {
						callBack.callBack(true, null, null);
						return;
					}
					JSONObject object = (JSONObject) PARSER.parse(response);
					JSONArray propertiesArray = (JSONArray) object.get("properties");
					String base64 = null;
					String signedBase64 = null;
					// noinspection Duplicates
					for (JSONObject property : (List<JSONObject>) propertiesArray) {
						String name = (String) property.get("name");
						if (name.equals("textures")) {
							base64 = (String) property.get("value");
							signedBase64 = (String) property.get("signature");
						}
					}
					if (base64 == null) {
						callBack.callBack(true, null, null);
						return;
					}
					String decodedBase64 = new String(Base64.getDecoder().decode(base64), "UTF-8");
					JSONObject base64json = (JSONObject) PARSER.parse(decodedBase64);
					long timeStamp = (long) base64json.get("timestamp");
					String profileName = (String) base64json.get("profileName");
					UUID profileId = getUUIDFromString((String) base64json.get("profileId"));
					JSONObject textures = (JSONObject) base64json.get("textures");
					String skinURL = null;
					String capeURL = null;
					if (textures.containsKey("SKIN")) {
						JSONObject skinObject = (JSONObject) textures.get("SKIN");
						skinURL = (String) skinObject.get("url");
					}
					if (textures.containsKey("CAPE")) {
						JSONObject capeObject = (JSONObject) textures.get("CAPE");
						capeURL = (String) capeObject.get("url");
					}
					callBack.callBack(true,
							new SkinData(profileId, profileName, skinURL, capeURL, timeStamp, base64, signedBase64),
							null);
				} else {
					if (exception != null) {
						callBack.callBack(false, null, exception);
					} else {
						callBack.callBack(false, null,
								new IOException("Failed to obtain Mojang data! Response code: " + responseCode));
					}
				}
			} catch (Exception e) {
				callBack.callBack(false, null, e);
			}
		});
	}

	public static class SkinData {
		private UUID uuid;
		private String name;
		private String skinURL;
		private String capeURL;
		private long timeStamp;
		private String base64;
		private String signedBase64;

		public SkinData(UUID uuid, String name, String skinURL, String capeURL, long timeStamp, String base64,
				String signedBase64) {
			this.uuid = uuid;
			this.name = name;
			this.skinURL = skinURL;
			this.capeURL = capeURL;
			this.timeStamp = timeStamp;
			this.base64 = base64;
			this.signedBase64 = signedBase64;
		}

		public UUID getUUID() {
			return uuid;
		}

		public String getName() {
			return name;
		}

		public boolean hasSkinURL() {
			return skinURL != null;
		}

		public String getSkinURL() {
			return skinURL;
		}

		public boolean hasCapeURL() {
			return capeURL != null;
		}

		public String getCapeURL() {
			return capeURL;
		}

		public long getTimeStamp() {
			return timeStamp;
		}

		public String getBase64() {
			return base64;
		}

		public boolean hasSignedBase64() {
			return signedBase64 != null;
		}

		public String getSignedBase64() {
			return signedBase64;
		}

		@Override
		public String toString() {
			return "SkinData{uuid=" + uuid + ",name=" + name + ",skinURL=" + skinURL + ",capeURL=" + capeURL
					+ ",timeStamp=" + timeStamp + ",base64=" + base64 + ",signedBase64=" + signedBase64 + "}";
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) {
				return true;
			}
			if (!(obj instanceof SkinData)) {
				return false;
			}
			SkinData skinData = (SkinData) obj;
			return this.uuid.equals(skinData.uuid) && this.name.equals(skinData.name)
					&& (this.skinURL == null ? skinData.skinURL == null : this.skinURL.equals(skinData.skinURL))
					&& (this.capeURL == null ? skinData.capeURL == null : this.capeURL.equals(skinData.skinURL))
					&& this.timeStamp == skinData.timeStamp && this.base64.equals(skinData.base64)
					&& (this.signedBase64 == null ? skinData.signedBase64 == null
							: this.signedBase64.equals(skinData.signedBase64));

		}

		@Override
		public int hashCode() {
			return Objects.hash(uuid, name, skinURL, capeURL, timeStamp, base64, signedBase64);
		}
	}

	private static RequestResult makeSyncGetRequest(URL url) {
		if (plugin == null) {
			return null;
		}
		StringBuilder response = new StringBuilder();
		try {
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.connect();
			// noinspection Duplicates
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
				String line = reader.readLine();
				while (line != null) {
					response.append(line);
					line = reader.readLine();
				}
				RequestResult result = new RequestResult();
				result.successful = true;
				result.responseCode = connection.getResponseCode();
				result.response = response.toString();
				return result;
			}
		} catch (IOException e) {
			RequestResult result = new RequestResult();
			result.exception = e;
			result.successful = false;
			return result;
		}
	}

	private static void makeAsyncGetRequest(URL url, RequestCallBack asyncCallBack) {
		if (plugin == null) {
			return;
		}
		new BukkitRunnable() {
			@Override
			public void run() {
				StringBuilder response = new StringBuilder();
				try {
					HttpURLConnection connection = (HttpURLConnection) url.openConnection();
					connection.connect();
					// noinspection Duplicates
					try (BufferedReader reader = new BufferedReader(
							new InputStreamReader(connection.getInputStream()))) {
						String line = reader.readLine();
						while (line != null) {
							response.append(line);
							line = reader.readLine();
						}
						asyncCallBack.callBack(true, response.toString(), null, connection.getResponseCode());
					}
				} catch (Exception e) {
					asyncCallBack.callBack(false, response.toString(), e, -1);
				}
			}
		}.runTaskAsynchronously(plugin);
	}

	private static RequestResult makeSyncPostRequest(URL url, String payload) {
		if (plugin == null) {
			return null;
		}
		StringBuilder response = new StringBuilder();
		try {
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setDoOutput(true);
			connection.setRequestMethod("POST");
			connection.setRequestProperty("Content-Type", "application/json");
			connection.connect();
			try (PrintWriter writer = new PrintWriter(connection.getOutputStream())) {
				writer.write(payload);
			}
			// noinspection Duplicates
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
				String line = reader.readLine();
				while (line != null) {
					response.append(line);
					line = reader.readLine();
				}
				RequestResult result = new RequestResult();
				result.successful = true;
				result.responseCode = connection.getResponseCode();
				result.response = response.toString();
				return result;
			}
		} catch (IOException e) {
			RequestResult result = new RequestResult();
			result.successful = false;
			result.exception = e;
			return result;
		}
	}

	private static void makeAsyncPostRequest(URL url, String payload, RequestCallBack asyncCallBack) {
		if (plugin == null) {
			return;
		}
		new BukkitRunnable() {
			@Override
			public void run() {
				StringBuilder response = new StringBuilder();
				try {
					HttpURLConnection connection = (HttpURLConnection) url.openConnection();
					connection.setDoOutput(true);
					connection.setRequestMethod("POST");
					connection.setRequestProperty("Content-Type", "application/json");
					connection.connect();
					try (PrintWriter writer = new PrintWriter(connection.getOutputStream())) {
						writer.write(payload);
					}
					// noinspection Duplicates
					try (BufferedReader reader = new BufferedReader(
							new InputStreamReader(connection.getInputStream()))) {
						String line = reader.readLine();
						while (line != null) {
							response.append(line);
							line = reader.readLine();
						}
						asyncCallBack.callBack(true, response.toString(), null, connection.getResponseCode());
					}
				} catch (Exception e) {
					asyncCallBack.callBack(false, response.toString(), e, -1);
				}
			}
		}.runTaskAsynchronously(plugin);
	}

	public static UUID getUUIDFromString(String string) {
		String uuidString = string.substring(0, 8) + "-" + string.substring(8, 12) + "-" + string.substring(12, 16)
				+ "-" + string.substring(16, 20) + "-" + string.substring(20);
		return UUID.fromString(uuidString);
	}

	@FunctionalInterface
	private interface RequestCallBack {
		void callBack(boolean successful, String response, Exception exception, int responseCode);
	}

	private static class RequestResult {
		boolean successful;
		String response;
		Exception exception;
		int responseCode;
	}

	/**
	 * The callback interface
	 * <p>
	 * Once some data is received (or an error is thrown) the callBack method is
	 * fired with the following data:
	 * <p>
	 * boolean successful - If the data arrived and was interpreted correctly.
	 * <p>
	 * <T> result - The data. Only present if successful is true, otherwise null.
	 * <p>
	 * Exception e - The exception. Only present if successful is false, otherwise
	 * null.
	 * <p>
	 * This interface is annotated with @FunctionalInterface, which allows for
	 * instantiation using lambda expressions.
	 */
	@FunctionalInterface
	public interface ResultCallBack<T> {
		void callBack(boolean successful, T result, Exception exception);
	}

	public static class Result<T> {
		private T value;
		private boolean successful;
		private Exception exception;

		public Result(T value, boolean successful, Exception exception) {
			this.value = value;
			this.successful = successful;
			this.exception = exception;
		}

		public T getValue() {
			return value;
		}

		public boolean wasSuccessful() {
			return successful;
		}

		public Exception getException() {
			return exception;
		}
	}
}

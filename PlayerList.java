

import java.lang.reflect.*;
import java.util.*;

import org.bukkit.*;
import org.bukkit.entity.Player;

public class PlayerList {
	private static final Class<?> PACKET_PLAYER_INFO_CLASS = a(7) ? ReflectionUtil
			.getNMSClass("PacketPlayOutPlayerInfo") : ReflectionUtil
			.getNMSClass("Packet201PlayerInfo");
	private static final Class<?> PACKET_PLAYER_INFO_DATA_CLASS = a() ? ReflectionUtil
			.getNMSClass("PacketPlayOutPlayerInfo$PlayerInfoData") : null;
	private static Class<?> WORLD_GAME_MODE_CLASS;
	private static final Class<?> GAMEPROFILECLASS = a() ? ReflectionUtil
			.getMojangAuthClass("GameProfile") : null;
	private static final Constructor<?> GAMEPROPHILECONSTRUCTOR = a() ? (Constructor<?>) ReflectionUtil
			.getConstructor(GAMEPROFILECLASS, UUID.class, String.class).get()
			: null;
	private static final Class<?> CRAFTPLAYERCLASS = ReflectionUtil
			.getCraftbukkitClass("CraftPlayer", "entity");
	private static final Object WORLD_GAME_MODE_NOT_SET;
	private static final Class<?> CRAFT_CHAT_MESSAGE_CLASS = a() ? ReflectionUtil
			.getCraftbukkitClass("CraftChatMessage", "util") : null;
	private static final Class<?> PACKET_PLAYER_INFO_PLAYER_ACTION_CLASS = a() ? ReflectionUtil
			.getNMSClass("PacketPlayOutPlayerInfo$EnumPlayerInfoAction") : null;
	private static final Object PACKET_PLAYER_INFO_ACTION_REMOVE_PLAYER = a() ? ReflectionUtil
			.getEnumConstant(PACKET_PLAYER_INFO_PLAYER_ACTION_CLASS,
					"REMOVE_PLAYER") : null;
	private static final Object PACKET_PLAYER_INFO_ACTION_ADD_PLAYER = a() ? ReflectionUtil
			.getEnumConstant(PACKET_PLAYER_INFO_PLAYER_ACTION_CLASS,
					"ADD_PLAYER") : null;
	private static final Class<?> PACKET_CLASS = ReflectionUtil
			.getNMSClass("Packet");
	private static final Class<?> I_CHAT_BASE_COMPONENT_CLASS = a() ? ReflectionUtil
			.getNMSClass("IChatBaseComponent") : null;
	private static final Constructor<?> PACKET_PLAYER_INFO_DATA_CONSTRUCTOR;

	static {
		try {
			WORLD_GAME_MODE_CLASS = ReflectionUtil.getNMSClass("EnumGamemode");
		} catch (Exception e) {
			WORLD_GAME_MODE_CLASS = ReflectionUtil
					.getNMSClass("WorldSettings$EnumGamemode");
		}
		WORLD_GAME_MODE_NOT_SET = a() ? ReflectionUtil.getEnumConstant(
				WORLD_GAME_MODE_CLASS, "NOT_SET") : null;
		PACKET_PLAYER_INFO_DATA_CONSTRUCTOR = a() ? (Constructor<?>) ReflectionUtil
				.getConstructor(PACKET_PLAYER_INFO_DATA_CLASS,
						PACKET_PLAYER_INFO_CLASS, GAMEPROFILECLASS, int.class,
						WORLD_GAME_MODE_CLASS, I_CHAT_BASE_COMPONENT_CLASS)
				.get() : null;
	}

	private final static String[] colorcodeOrder = "0123456789abcdef".split("");
	private final static String[] inviscodeOrder = { ",", ".", "\'", "`", " " };

	public static int SIZE_DEFAULT = 20;
	public static int SIZE_TWO = 40;
	public static int SIZE_THREE = 60;
	public static int SIZE_FOUR = 80;

	private List<Object> datas = new ArrayList<>();
	private Map<Integer, String> datasOLD = new HashMap<Integer, String>();

	private UUID uuid;
	private String[] tabs;
	private int size = 0;

	private static final HashMap<UUID, PlayerList> lookUpTable = new HashMap<>();

	/**
	 * Due to the amount of times I have to check if a version is higher than
	 * 1.8, all reflection calls will be replace with this method.
	 * 
	 * @param update
	 * @return
	 */
	private static boolean a(Integer... update) {
		return ReflectionUtil.isVersionHigherThan(1,
				update.length > 0 ? update[0] : 8);
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
		lookUpTable.put(this.uuid = player.getUniqueId(), this);
		tabs = new String[80];
		this.size = a() ? size : SIZE_DEFAULT;
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
	 * Resets a player's tablist. Use this if you have want the tablist to
	 * return to the base-minecraft tablist
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
	@SuppressWarnings("unchecked")
	public void clearPlayers() {
		if (a()) {
			Object packet = ReflectionUtil
					.instantiate((Constructor<?>) ReflectionUtil
							.getConstructor(PACKET_PLAYER_INFO_CLASS).get());
			List<Object> players = (List<Object>) ReflectionUtil
					.getInstanceField(packet, "b");
			for (Player player2 : (Collection<? extends Player>) ReflectionUtil
					.invokeMethod(Bukkit.getServer(), "getOnlinePlayers", null)) {
				Object gameProfile = GAMEPROFILECLASS.cast(ReflectionUtil
						.invokeMethod(player2, "getProfile", new Class[0]));
				Object[] array = (Object[]) ReflectionUtil.invokeMethod(
						CRAFT_CHAT_MESSAGE_CLASS, null, "fromString",
						new Class[] { String.class }, player2.getName());
				Object data = ReflectionUtil.instantiate(
						PACKET_PLAYER_INFO_DATA_CONSTRUCTOR, packet,
						gameProfile, 1, WORLD_GAME_MODE_NOT_SET, array[0]);
				players.add(data);
			}
			sendNEWPackets(getPlayer(), packet, players,
					PACKET_PLAYER_INFO_ACTION_REMOVE_PLAYER);
		} else {
			Object olp = ReflectionUtil.invokeMethod(Bukkit.getServer(),
					"getOnlinePlayers", null);
			Object[] players = olp instanceof Collection ? ((Collection<?>) olp)
					.toArray() : (Object[]) olp;
			for (int i = 0; i < players.length; i++) {
				try {
					Object packet = ReflectionUtil
							.instantiate((Constructor<?>) ReflectionUtil
									.getConstructor(PACKET_PLAYER_INFO_CLASS)
									.get());
					sendOLDPackets(getPlayer(), packet,
							((Player) players[i]).getName(), false);
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
		if (a()) {
			Object packet = ReflectionUtil
					.instantiate((Constructor<?>) ReflectionUtil
							.getConstructor(PACKET_PLAYER_INFO_CLASS).get());
			List<Object> players = (List<Object>) ReflectionUtil
					.getInstanceField(packet, "b");
			for (Object playerData : new ArrayList<>(datas)) {
				Object gameProfile = GAMEPROFILECLASS.cast(ReflectionUtil
						.invokeMethod(playerData, "a", new Class[0]));
				tabs[getIDFromName((String) ReflectionUtil.invokeMethod(
						gameProfile, "getName", null))] = "";
				players.add(playerData);
			}
			datas.clear();
			sendNEWPackets(getPlayer(), packet, players,
					PACKET_PLAYER_INFO_ACTION_REMOVE_PLAYER);
		} else {
			for (int i = 0; i < size; i++) {
				if (!datasOLD.containsKey(i))
					continue;
				try {
					Object packet = ReflectionUtil
							.instantiate((Constructor<?>) ReflectionUtil
									.getConstructor(PACKET_PLAYER_INFO_CLASS)
									.get());
					sendOLDPackets(getPlayer(), packet,
							datasOLD.get(i), false);
					tabs[i] = null;
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			datasOLD.clear();
		}
	}

	/**
	 * Clears all the values for a player's tablist. Use this whenever a player
	 * first joins if you want to create your own tablist.
	 * 
	 * This is here to remind you that you MUST call either this method or the
	 * "clearCustomTabs" method. If you do not, the player will continue to see
	 * the custom tabs until they relog.
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
		if (a()) {
			removeCustomTab(id, true);
			addValue(id, newName);
		} else {
			for (int i = id; i < size; i++)
				removeCustomTab(i, false);
			for (int i = id; i < size; i++)
				addValue(i, (i == id) ? newName : datasOLD.get(i).substring(2));
		}
	}

	/**
	 * removes a specific player from the player's tablist.
	 * 
	 * @param player
	 */
	@SuppressWarnings("unchecked")
	public void removePlayer(Player player) {
		if (a()) {
			Object packet = ReflectionUtil
					.instantiate((Constructor<?>) ReflectionUtil
							.getConstructor(PACKET_PLAYER_INFO_CLASS).get());
			List<Object> players = (List<Object>) ReflectionUtil
					.getInstanceField(packet, "b");
			Object gameProfile = GAMEPROFILECLASS.cast(ReflectionUtil
					.invokeMethod(player, "getProfile", new Class[0]));
			Object[] array = (Object[]) ReflectionUtil.invokeMethod(
					CRAFT_CHAT_MESSAGE_CLASS, null, "fromString",
					new Class[] { String.class }, player.getName());
			Object data = ReflectionUtil.instantiate(
					PACKET_PLAYER_INFO_DATA_CONSTRUCTOR, packet, gameProfile,
					1, WORLD_GAME_MODE_NOT_SET, array[0]);
			players.add(data);
			sendNEWPackets(player, packet, players,
					PACKET_PLAYER_INFO_ACTION_REMOVE_PLAYER);
		} else {
			try {
				Object packet = ReflectionUtil
						.instantiate((Constructor<?>) ReflectionUtil
								.getConstructor(PACKET_PLAYER_INFO_CLASS).get());
				sendOLDPackets(player, packet, player.getName(), false);
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
		if (a()) {
			Object packet = ReflectionUtil
					.instantiate((Constructor<?>) ReflectionUtil
							.getConstructor(PACKET_PLAYER_INFO_CLASS).get());
			List<Object> players = (List<Object>) ReflectionUtil
					.getInstanceField(packet, "b");
			for (Object playerData : new ArrayList<>(datas)) {
				Object gameProfile = GAMEPROFILECLASS.cast(ReflectionUtil
						.invokeMethod(playerData, "a", new Class[0]));
				String getname = (String) ReflectionUtil.invokeMethod(
						gameProfile, "getName", null);
				if (getname.startsWith(getNameFromID(id))) {
					tabs[getIDFromName(getname)] = "";
					players.add(playerData);
					if (remove)
						datas.remove(playerData);
					break;
				}
			}
			sendNEWPackets(getPlayer(), packet, players,
					PACKET_PLAYER_INFO_ACTION_REMOVE_PLAYER);
		} else {
			try {
				Object packet = ReflectionUtil
						.instantiate((Constructor<?>) ReflectionUtil
								.getConstructor(PACKET_PLAYER_INFO_CLASS).get());
				sendOLDPackets(getPlayer(), packet,
						datasOLD.get(id), false);
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
	 * Use this to add an existing offline player to a player's tablist. The
	 * name variable is so you can modify a player's name in the tablist. If you
	 * want the player-tab to be the same as the player's name, use the other
	 * method
	 * 
	 * @param id
	 * @param name
	 * @param player
	 */
	public void addExistingPlayer(int id, String name, OfflinePlayer player) {
		addValue(id, name, player.getUniqueId());
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
	 * @deprecated If all 80 slots have been taken, new values will not be shown
	 *             and may have the potential to go out of the registered
	 *             bounds. Use the "updateSlot" method to change a slot.
	 */
	@Deprecated
	public void addValue(int id, String name) {
		if (name.length() > 0
				&& Bukkit.getOfflinePlayer(name).hasPlayedBefore()) {
			this.addValue(id, name, Bukkit.getOfflinePlayer(name).getUniqueId());
		} else
			this.addValue(id, name, UUID.randomUUID());
	}

	/**
	 * Use this to add a new player to the list
	 * 
	 * @param id
	 * @param name
	 * @deprecated If all 80 slots have been taken, new values will not be shown
	 *             and may have the potential to go out of the registered
	 *             bounds. Use the "updateSlot" method to change a slot.
	 */
	@SuppressWarnings("unchecked")
	@Deprecated
	public void addValue(int id, String name, UUID uuid) {
		if (a()) {
			Object packet = ReflectionUtil
					.instantiate((Constructor<?>) ReflectionUtil
							.getConstructor(PACKET_PLAYER_INFO_CLASS).get());
			List<Object> players = (List<Object>) ReflectionUtil
					.getInstanceField(packet, "b");
			Object gameProfile = ReflectionUtil.instantiate(
					GAMEPROPHILECONSTRUCTOR, uuid, getNameFromID(id));
			Object[] array = (Object[]) ReflectionUtil.invokeMethod(
					CRAFT_CHAT_MESSAGE_CLASS, null, "fromString",
					new Class[] { String.class }, getNameFromID(id) + name);
			Object data = ReflectionUtil.instantiate(
					PACKET_PLAYER_INFO_DATA_CONSTRUCTOR, packet, gameProfile,
					1, WORLD_GAME_MODE_NOT_SET, array[0]);

			Object profile = GAMEPROFILECLASS.cast(ReflectionUtil.invokeMethod(
					data, "a", new Class[0]));
			String getname = (String) ReflectionUtil.invokeMethod(profile,
					"getName", null);
			tabs[getIDFromName(getname)] = getname;
			players.add(data);
			datas.add(data);
			sendNEWPackets(getPlayer(), packet, players,
					PACKET_PLAYER_INFO_ACTION_ADD_PLAYER);
		} else {
			try {
				Object packet = ReflectionUtil
						.instantiate((Constructor<?>) ReflectionUtil
								.getConstructor(PACKET_PLAYER_INFO_CLASS).get());
				sendOLDPackets(getPlayer(), packet,
						getNameFromID(id) + name, true);
				tabs[id] = name;
				datasOLD.put(id, getNameFromID(id) + name);
			} catch (Exception e) {
				error();
				e.printStackTrace();
			}
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
			addValue(i, "");
	}

	private static void sendNEWPackets(Player player, Object packet,
			List<?> players, Object action) {
		try {
			ReflectionUtil.setInstanceField(packet, "a", action);
			ReflectionUtil.setInstanceField(packet, "b", players);
			Object handle = ReflectionUtil.invokeMethod(
					CRAFTPLAYERCLASS.cast(player), "getHandle", null);
			Object playerConnection = ReflectionUtil.getInstanceField(handle,
					"playerConnection");
			ReflectionUtil.invokeMethod(playerConnection, "sendPacket",
					new Class[] { PACKET_CLASS }, packet);
		} catch (Exception e) {
			error();
			e.printStackTrace();
		}

	}

	private static void sendOLDPackets(Player player, Object packet,
			String name, boolean isOnline) {
		try {
			ReflectionUtil.setInstanceField(packet, "a", name);
			ReflectionUtil.setInstanceField(packet, "b", isOnline);
			ReflectionUtil.setInstanceField(packet, "c", ((short) 0));
			Object handle = ReflectionUtil.invokeMethod(
					CRAFTPLAYERCLASS.cast(player), "getHandle", new Class[0]);
			Object playerConnection = ReflectionUtil.getInstanceField(handle,
					"playerConnection");
			ReflectionUtil.invokeMethod(playerConnection, "sendPacket",
					new Class[] { PACKET_CLASS }, packet);
		} catch (Exception e) {
			error();
			e.printStackTrace();
		}
	}

	/**
	 * Returns the player.
	 * 
	 * @return the player (if they are online), or null (if they are offline)
	 */
	public Player getPlayer() {
		return Bukkit.getPlayer(this.uuid);
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
			return ChatColor.getByChar(firstletter) + ""
					+ ChatColor.getByChar(secondletter) + ChatColor.RESET;
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
			if (a[i].equalsIgnoreCase(id.charAt(1 + (indexAdder + indexAdder))
					+ "")) {
				total += i;
				break;
			}
		}
		return total;
	}

	private static void error() {
		Bukkit.broadcastMessage("PLEASE REPORT THIS ISSUE TO" + ChatColor.RED
				+ " ZOMBIE_STRIKER" + ChatColor.RESET + " ON THE BUKKIT FORUMS");
	}

	/**
	 * A small help with reflection
	 */
	public static class ReflectionUtil {
		private static final String SERVER_VERSION;
		static {
			String name = Bukkit.getServer().getClass().getName();
			name = name.substring(name.indexOf("craftbukkit.")
					+ "craftbukkit.".length());
			name = name.substring(0, name.indexOf("."));
			SERVER_VERSION = name;
		}

		private static boolean isVersionHigherThan(int mainVersion,
				int secondVersion) {
			String firstChar = SERVER_VERSION.substring(1, 2);
			int fInt = Integer.parseInt(firstChar);
			if (fInt < mainVersion)
				return false;
			StringBuilder secondChar = new StringBuilder();
			for (int i = 3; i < 10; i++) {
				if (SERVER_VERSION.charAt(i) == '_'
						|| SERVER_VERSION.charAt(i) == '.')
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
		private static Class<?> getNMSClass(String name) {
			try {
				return Class.forName("net.minecraft.server." + SERVER_VERSION
						+ "." + name);
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
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

		private static Class<?> getCraftbukkitClass(String name,
				String packageName) {
			try {
				return Class.forName("org.bukkit.craftbukkit." + SERVER_VERSION
						+ "." + packageName + "." + name);
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
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

		private static Class<?> getMojangAuthClass(String name) {
			try {
				return Class.forName("com.mojang.authlib." + name);
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
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
		 * @return The resulting object or null if an error occurred / the
		 *         method didn't return a thing
		 */
		@SuppressWarnings("rawtypes")
		private static Object invokeMethod(Object handle, String methodName,
				Class[] parameterClasses, Object... args) {
			return invokeMethod(handle.getClass(), handle, methodName,
					parameterClasses, args);
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
		 * @return The resulting object or null if an error occurred / the
		 *         method didn't return a thing
		 */
		@SuppressWarnings("rawtypes")
		private static Object invokeMethod(Class<?> clazz, Object handle,
				String methodName, Class[] parameterClasses, Object... args) {
			Optional<Method> methodOptional = getMethod(clazz, methodName,
					parameterClasses);
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
		private static void setInstanceField(Object handle, String name,
				Object value) {
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
		private static Object getInstanceField(Object handle, String name) {
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
		private static Object getEnumConstant(Class<?> enumClass, String name) {
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
		 * @return The Constructor or an empty Optional if there is none with
		 *         these parameters
		 */
		private static Optional<?> getConstructor(Class<?> clazz,
				Class<?>... params) {
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
		private static Object instantiate(Constructor<?> constructor,
				Object... arguments) {
			try {
				return constructor.newInstance(arguments);
			} catch (Exception e) {
				e.printStackTrace();
			}
			return null;
		}

		private static Optional<Method> getMethod(Class<?> clazz, String name,
				Class<?>... params) {
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

		private static Optional<Field> getField(Class<?> clazz, String name) {
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
}

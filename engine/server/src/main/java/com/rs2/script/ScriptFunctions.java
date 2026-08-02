package com.rs2.script;

import com.rs2.script.registries.AreaRegistry;
import com.rs2.script.registries.BossRegistry;
import com.rs2.script.registries.CommandHandlerRegistry;
import com.rs2.script.registries.ItemHandlerRegistry;
import com.rs2.script.registries.InteractionHandlerRegistry;
import com.rs2.script.registries.LifecycleRegistry;
import com.rs2.script.registries.NpcHandlerRegistry;
import com.rs2.script.registries.ObjectHandlerRegistry;
import com.rs2.script.registries.QuestRegistry;
import com.rs2.script.registries.RaidRegistry;
import com.rs2.script.registries.ScriptArea;
import com.rs2.script.quest.QuestDefinition;
import com.rs2.script.quest.QuestDefinitionParser;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.graalvm.polyglot.Value;
import org.apollo.cache.def.ItemDefinition;
import org.apollo.cache.def.ObjectDefinition;

/**
 * Singleton that owns the Java functional facades exposed to the GraalVM
 * script context. {@code ScriptBindings.install(Context)} retrieves each
 * accessor once at context build time and publishes the returned functional
 * interface as a JS global.
 *
 * Registration errors throw so the complete candidate is rejected.
 */
public final class ScriptFunctions {

	@FunctionalInterface
	public interface TriIntStrFunction {
		void apply(int id, String action, Value fn);
	}

	@FunctionalInterface
	public interface PairIntFunction {
		void apply(int firstId, int secondId, Value fn);
	}

	@FunctionalInterface
	public interface IntHandlerFunction {
		void apply(int id, Value fn);
	}

	@FunctionalInterface
	public interface AreaHandlerFunction {
		void apply(Value descriptor, Value fn);
	}

	@FunctionalInterface
	public interface ValueHandlerFunction {
		void apply(Value id, Value fn);
	}

	@FunctionalInterface
	public interface ValuePairHandlerFunction {
		void apply(Value firstId, Value secondId, Value fn);
	}

	public interface DevConsole {
		void log(String message);
	}

	private static final ScriptFunctions INSTANCE = new ScriptFunctions();

	private ScriptFunctions() {
	}

	public static ScriptFunctions getInstance() {
		return INSTANCE;
	}

	public Consumer<Value> getDefineBoss() {
		return def -> {
			requireObject("defineBoss", def);
			int id = readIntegralMember("defineBoss", def, "npcId", 0, 14999);
			if (BossRegistry.put(id, def) != null) {
				throw registrationError("defineBoss(" + id + ")",
						"duplicate registration");
			}
		};
	}

	public Consumer<Value> getDefineQuest() {
		return def -> {
			if (def == null || def.isNull()) {
				throw registrationError("defineQuest",
						"definition must not be null");
			}
			QuestDefinition definition = new QuestDefinitionParser().parse(def);
			QuestRegistry.put(definition.getId(), definition);
		};
	}

	public Consumer<Value> getDefineRaid() {
		return def -> {
			requireObject("defineRaid", def);
			String id = requireStringMember("defineRaid", def, "id");
			rejectDuplicate("defineRaid(" + id + ")", RaidRegistry.put(id, def));
		};
	}

	public Consumer<Value> getDefineArea() {
		return def -> {
			requireObject("defineArea", def);
			String id = requireStringMember("defineArea", def, "id");
			rejectDuplicate("defineArea(" + id + ")", AreaRegistry.put(id, def));
		};
	}

	public TriIntStrFunction getOnObject() {
		return (id, action, fn) -> {
			String registration = "onObject(" + id + ", " + action + ")";
			if (id < 0 || id > 65535) {
				throw registrationError(registration,
						"object id must be between 0 and 65535");
			}
			if (!isObjectAction(action)) {
				throw registrationError(registration, "unsupported action");
			}
			requireExecutable(registration, fn);
			rejectDuplicate(registration, ObjectHandlerRegistry.put(id, action, fn));
		};
	}

	public TriIntStrFunction getOnNpc() {
		return (id, action, fn) -> {
			String registration = "onNpc(" + id + ", " + action + ")";
			if (id < 0 || id > 14999) {
				throw registrationError(registration,
						"npc id must be between 0 and 14999");
			}
			if (!isNpcAction(action)) {
				throw registrationError(registration, "unsupported action");
			}
			requireExecutable(registration, fn);
			rejectDuplicate(registration, NpcHandlerRegistry.put(id, action, fn));
		};
	}

	public TriIntStrFunction getOnItem() {
		return (id, action, fn) -> {
			if (id < 0) {
				throw registrationError("onItem(" + id + ", " + action + ")",
						"item id must be non-negative");
			}
			if (!isItemAction(action)) {
				throw registrationError("onItem(" + id + ", " + action + ")",
						"unsupported action");
			}
			if (!isExecutable(fn)) {
				throw registrationError("onItem(" + id + ", " + action + ")",
						"handler is not executable");
			}
			rejectDuplicate("onItem(" + id + ", " + action + ")",
					ItemHandlerRegistry.putItem(id, action, fn));
		};
	}

	public PairIntFunction getOnItemOnItem() {
		return pairRegistrar("onItemOnItem", ItemHandlerRegistry::putItemOnItem);
	}

	public PairIntFunction getOnItemOnObject() {
		return pairRegistrar("onItemOnObject", ItemHandlerRegistry::putItemOnObject);
	}

	public PairIntFunction getOnItemOnNpc() {
		return pairRegistrar("onItemOnNpc", ItemHandlerRegistry::putItemOnNpc);
	}

	public BiConsumer<String, Value> getOnCommand() {
		return (name, fn) -> {
			if (name == null || name.trim().isEmpty()) {
				throw registrationError("onCommand", "name must be non-empty");
			}
			String normalized = name.toLowerCase(java.util.Locale.ROOT);
			requireExecutable("onCommand(" + normalized + ")", fn);
			rejectDuplicate("onCommand(" + normalized + ")",
					CommandHandlerRegistry.put(normalized, fn));
		};
	}

	public Consumer<Value> getOnLogin() {
		return singletonLifecycleRegistrar("login");
	}

	public Consumer<Value> getOnLogout() {
		return singletonLifecycleRegistrar("logout");
	}

	public IntHandlerFunction getOnNpcDeath() {
		return (npcId, fn) -> {
			String registration = "onNpcDeath(" + npcId + ")";
			if (npcId < 0) {
				throw registrationError(registration, "npc id must be non-negative");
			}
			if (!isExecutable(fn)) {
				throw registrationError(registration, "handler is not executable");
			}
			rejectDuplicate(registration, LifecycleRegistry.putNpcDeath(npcId, fn));
		};
	}

	public IntHandlerFunction getOnItemPickup() {
		return (itemId, fn) -> {
			String registration = "onItemPickup(" + itemId + ")";
			if (itemId < 0) {
				throw registrationError(registration, "item id must be non-negative");
			}
			if (!isExecutable(fn)) {
				throw registrationError(registration, "handler is not executable");
			}
			rejectDuplicate(registration, LifecycleRegistry.putItemPickup(itemId, fn));
		};
	}

	public AreaHandlerFunction getOnEnterArea() {
		return areaLifecycleRegistrar("onEnterArea", "enter");
	}

	public AreaHandlerFunction getOnLeaveArea() {
		return areaLifecycleRegistrar("onLeaveArea", "leave");
	}

	public ValueHandlerFunction getOnButton() {
		return (buttonIdValue, fn) -> {
			int buttonId = requireIntegralId("onButton", buttonIdValue, 0, 255255);
			if (buttonId / 1000 > 255 || buttonId % 1000 > 255) {
				throw registrationError("onButton(" + buttonId + ")",
						"button id is not decodable from two unsigned bytes");
			}
			requireExecutable("onButton(" + buttonId + ")", fn);
			rejectDuplicate("onButton(" + buttonId + ")",
					InteractionHandlerRegistry.putButton(buttonId, fn));
		};
	}

	public ValuePairHandlerFunction getOnItemOnGroundItem() {
		return (itemValue, groundItemValue, fn) -> {
			int itemId = requireIntegralId("onItemOnGroundItem", itemValue, 0, 14999);
			int groundItemId = requireIntegralId(
					"onItemOnGroundItem", groundItemValue, 0, 14999);
			requireLoadedItem("onItemOnGroundItem", itemId);
			requireLoadedItem("onItemOnGroundItem", groundItemId);
			requireExecutable("onItemOnGroundItem(" + itemId + ", "
					+ groundItemId + ")", fn);
			rejectDuplicate("onItemOnGroundItem(" + itemId + ", "
					+ groundItemId + ")", InteractionHandlerRegistry
					.putItemOnGroundItem(itemId, groundItemId, fn));
		};
	}

	public ValueHandlerFunction getOnItemOnPlayer() {
		return (itemValue, fn) -> {
			int itemId = requireIntegralId("onItemOnPlayer", itemValue, 0, 14999);
			requireLoadedItem("onItemOnPlayer", itemId);
			requireExecutable("onItemOnPlayer(" + itemId + ")", fn);
			rejectDuplicate("onItemOnPlayer(" + itemId + ")",
					InteractionHandlerRegistry.putItemOnPlayer(itemId, fn));
		};
	}

	public ValuePairHandlerFunction getOnMagicOnItem() {
		return (spellValue, itemValue, fn) -> {
			int spellId = requireIntegralId("onMagicOnItem", spellValue, 0, 65535);
			int itemId = requireIntegralId("onMagicOnItem", itemValue, 0, 14999);
			requireLoadedItem("onMagicOnItem", itemId);
			requireExecutable("onMagicOnItem(" + spellId + ", " + itemId + ")", fn);
			rejectDuplicate("onMagicOnItem(" + spellId + ", " + itemId + ")",
					InteractionHandlerRegistry.putMagicOnItem(spellId, itemId, fn));
		};
	}

	public ValuePairHandlerFunction getOnMagicOnObject() {
		return (spellValue, objectValue, fn) -> {
			int spellId = requireIntegralId("onMagicOnObject", spellValue, 0, 65535);
			int objectId = requireIntegralId("onMagicOnObject", objectValue, 0, 65535);
			requireLoadedObject("onMagicOnObject", objectId);
			requireExecutable("onMagicOnObject(" + spellId + ", " + objectId + ")", fn);
			rejectDuplicate("onMagicOnObject(" + spellId + ", " + objectId + ")",
					InteractionHandlerRegistry.putMagicOnObject(spellId, objectId, fn));
		};
	}

	public Consumer<Value> getOnPlayerDeath() {
		return fn -> {
			requireExecutable("onPlayerDeath()", fn);
			rejectDuplicate("onPlayerDeath()", LifecycleRegistry.putPlayerDeath(fn));
		};
	}

	public DevConsole getDev() {
		return DefaultDevConsole.INSTANCE;
	}

	public Consumer<String> getLog() {
		return DefaultDevConsole.INSTANCE::log;
	}

	public Map<String, Value> getCommandHandlers() {
		return CommandHandlerRegistry.all();
	}

	public void clearCommandHandlers() {
		CommandHandlerRegistry.clear();
	}

	private static boolean isObjectAction(String action) {
		return "first".equals(action) || "second".equals(action)
				|| "third".equals(action) || "fourth".equals(action);
	}

	private static boolean isItemAction(String action) {
		return "first".equals(action) || "second".equals(action) || "third".equals(action);
	}

	private static PairIntFunction pairRegistrar(String name, PairRegistry registry) {
		return (firstId, secondId, fn) -> {
			String registration = name + "(" + firstId + ", " + secondId + ")";
			if (firstId < 0 || secondId < 0) {
				throw registrationError(registration, "ids must be non-negative");
			}
			if (!isExecutable(fn)) {
				throw registrationError(registration, "handler is not executable");
			}
			rejectDuplicate(registration, registry.put(firstId, secondId, fn));
		};
	}

	private static Consumer<Value> singletonLifecycleRegistrar(final String event) {
		return fn -> {
			String registration = "on" + Character.toUpperCase(event.charAt(0))
					+ event.substring(1) + "()";
			if (!isExecutable(fn)) {
				throw registrationError(registration, "handler is not executable");
			}
			rejectDuplicate(registration, LifecycleRegistry.putSingleton(event, fn));
		};
	}

	private static AreaHandlerFunction areaLifecycleRegistrar(
			final String name, final String event) {
		return (descriptor, fn) -> {
			if (!isExecutable(fn)) {
				throw registrationError(name, "handler is not executable");
			}
			ScriptArea area = readArea(name, descriptor);
			try {
				rejectDuplicate(name + "(" + area.getId() + ")",
						LifecycleRegistry.putArea(event, area, fn));
			} catch (IllegalArgumentException e) {
				throw registrationError(name + "(" + area.getId() + ")", e.getMessage());
			}
		};
	}

	private static ScriptArea readArea(String registration, Value descriptor) {
		if (descriptor == null || descriptor.isNull() || !descriptor.hasMembers()) {
			throw registrationError(registration, "area descriptor must be an object");
		}
		String id = readStringMember(descriptor, "id");
		if (id == null || id.trim().isEmpty()) {
			throw registrationError(registration, "area id must be non-empty");
		}
		Integer minX = readRequiredInteger(descriptor, "minX");
		Integer minY = readRequiredInteger(descriptor, "minY");
		Integer maxX = readRequiredInteger(descriptor, "maxX");
		Integer maxY = readRequiredInteger(descriptor, "maxY");
		if (minX == null || minY == null || maxX == null || maxY == null) {
			throw registrationError(registration,
					"minX, minY, maxX, and maxY must be integers");
		}
		if (minX.intValue() > maxX.intValue() || minY.intValue() > maxY.intValue()) {
			throw registrationError(registration, "area bounds are inverted");
		}
		Value planeMember = descriptor.getMember("plane");
		Integer plane = readOptionalInteger(descriptor, "plane");
		if (planeMember != null && !planeMember.isNull() && plane == null) {
			throw registrationError(registration, "plane must be an integer");
		}
		if (plane != null && (plane.intValue() < 0 || plane.intValue() > 3)) {
			throw registrationError(registration, "plane must be between 0 and 3");
		}
		return new ScriptArea(id, minX.intValue(), minY.intValue(),
				maxX.intValue(), maxY.intValue(), plane);
	}

	private static Integer readRequiredInteger(Value descriptor, String member) {
		Value value = descriptor.getMember(member);
		if (value == null || value.isNull() || !value.isNumber()) {
			return null;
		}
		double number = value.asDouble();
		return Double.isFinite(number) && number == Math.rint(number)
				&& number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE
				? Integer.valueOf((int) number) : null;
	}

	private static Integer readOptionalInteger(Value descriptor, String member) {
		Value value = descriptor.getMember(member);
		if (value == null || value.isNull()) {
			return null;
		}
		return readRequiredInteger(descriptor, member);
	}

	private static void rejectDuplicate(String registration, Value previous) {
		if (previous != null) {
			throw registrationError(registration, "duplicate registration");
		}
	}

	private static int requireIntegralId(String registration, Value value,
			int minimum, int maximum) {
		if (value == null || value.isNull() || !value.isNumber()) {
			throw registrationError(registration, "id must be a number");
		}
		double number = value.asDouble();
		if (!Double.isFinite(number) || number != Math.rint(number)) {
			throw registrationError(registration, "id must be a finite integer");
		}
		if (number < minimum || number > maximum) {
			throw registrationError(registration,
					"id must be between " + minimum + " and " + maximum);
		}
		return (int) number;
	}

	private static void requireExecutable(String registration, Value fn) {
		if (!isExecutable(fn)) {
			throw registrationError(registration, "handler is not executable");
		}
	}

	private static void requireObject(String registration, Value value) {
		if (value == null || value.isNull() || !value.hasMembers()) {
			throw registrationError(registration, "definition must be an object");
		}
	}

	private static String requireStringMember(String registration, Value value,
			String member) {
		Value memberValue = value.getMember(member);
		if (memberValue == null || memberValue.isNull() || !memberValue.isString()
				|| memberValue.asString().trim().isEmpty()) {
			throw registrationError(registration,
					"member '" + member + "' must be a non-empty string");
		}
		return memberValue.asString();
	}

	private static int readIntegralMember(String registration, Value value,
			String member, int minimum, int maximum) {
		Value memberValue = value.getMember(member);
		if (memberValue == null || memberValue.isNull() || !memberValue.isNumber()) {
			throw registrationError(registration,
					"member '" + member + "' must be a number");
		}
		return requireIntegralId(registration + "." + member, memberValue,
				minimum, maximum);
	}

	private static void requireLoadedItem(String registration, int itemId) {
		ItemDefinition[] definitions = ItemDefinition.getDefinitions();
		if (definitions == null || itemId >= definitions.length
				|| definitions[itemId] == null
				|| definitions[itemId].getId() != itemId) {
			throw registrationError(registration,
					"item id " + itemId + " has no loaded definition");
		}
	}

	private static void requireLoadedObject(String registration, int objectId) {
		ObjectDefinition[] definitions = ObjectDefinition.getDefinitions();
		if (definitions == null || objectId >= definitions.length
				|| definitions[objectId] == null
				|| definitions[objectId].getId() != objectId) {
			throw registrationError(registration,
					"object id " + objectId + " has no loaded definition");
		}
	}

	private static IllegalArgumentException registrationError(String registration,
			String message) {
		return new IllegalArgumentException(
				"Script registration " + registration + ": " + message);
	}

	@FunctionalInterface
	private interface PairRegistry {
		Value put(int firstId, int secondId, Value handler);
	}

	private static boolean isNpcAction(String action) {
		return "first".equals(action) || "second".equals(action)
				|| "third".equals(action);
	}

	private static boolean isExecutable(Value v) {
		return v != null && !v.isNull() && v.canExecute();
	}

	private static int readIntMember(Value obj, String member) {
		Value v = obj.getMember(member);
		if (v == null || v.isNull() || !v.isNumber()) {
			return -1;
		}
		return v.asInt();
	}

	private static String readStringMember(Value obj, String member) {
		Value v = obj.getMember(member);
		if (v == null || v.isNull() || !v.isString()) {
			return null;
		}
		return v.asString();
	}

	private static void log(String message) {
		System.out.println("[script] " + message);
	}

	private static final class DefaultDevConsole implements DevConsole {

		private static final DefaultDevConsole INSTANCE = new DefaultDevConsole();

		@Override
		public void log(String message) {
			System.out.println("[script] " + message);
		}

	}

}

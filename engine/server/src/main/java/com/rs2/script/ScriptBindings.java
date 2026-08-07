package com.rs2.script;

import java.util.HashMap;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

/**
 * Installs the Java-backed global bindings that the TypeScript content layer
 * sees as JS globals.
 *
 * <p>Each binding is the result of calling a getter on
 * {@link ScriptFunctions#getInstance()}, wrapped in a {@link ProxyExecutable}
 * so JS callers can invoke it directly. The {@code dev} binding is exposed
 * as a host object exposing a single {@code log(message)} member.
 */
public final class ScriptBindings {

	private ScriptBindings() {
		throw new UnsupportedOperationException("static-utility classes may not be instantiated.");
	}

	/**
	 * Installs the global bindings on the given context. Must be called on a
	 * newly built context before any module is evaluated.
	 */
	public static void install(Context ctx) {
		ScriptFunctions functions = ScriptFunctions.getInstance();
		Value globals = ctx.getBindings("js");

		globals.putMember("defineBoss", consumeValue(functions.getDefineBoss()));
		globals.putMember("defineQuest", consumeValue(functions.getDefineQuest()));
		globals.putMember("defineRaid", consumeValue(functions.getDefineRaid()));
		globals.putMember("defineArea", consumeValue(functions.getDefineArea()));
		globals.putMember("defineDropTable",
				consumeValue(functions.getDefineDropTable()));
		globals.putMember("defineReward",
				consumeValue(functions.getDefineReward()));
		globals.putMember("defineShop",
				consumeValue(functions.getDefineShop()));
		globals.putMember("defineGatheringResource",
				consumeValue(functions.getDefineGatheringResource()));
		globals.putMember("defineProcessingSkill",
				consumeValue(functions.getDefineProcessingSkill()));
		globals.putMember("defineMob",
				consumeValue(functions.getDefineMob()));
		globals.putMember("registerContentModule",
				biValue(functions.getRegisterContentModule()));
		globals.putMember("onObject", triIntStr(functions.getOnObject()));
		globals.putMember("onNpc", triIntStr(functions.getOnNpc()));
		globals.putMember("onItem", triIntStr(functions.getOnItem()));
		globals.putMember("onItemOnItem", pairInt(functions.getOnItemOnItem()));
		globals.putMember("onItemOnObject", pairInt(functions.getOnItemOnObject()));
		globals.putMember("onItemOnNpc", pairInt(functions.getOnItemOnNpc()));
		globals.putMember("onCommand", biStrValue(functions.getOnCommand()));
		globals.putMember("onLogin", requireHandler("onLogin", functions.getOnLogin()));
		globals.putMember("onLogout", requireHandler("onLogout", functions.getOnLogout()));
		globals.putMember("onNpcDeath", intHandler("onNpcDeath", functions.getOnNpcDeath()));
		globals.putMember("onItemPickup", intHandler("onItemPickup", functions.getOnItemPickup()));
		globals.putMember("onEnterArea", areaHandler("onEnterArea", functions.getOnEnterArea()));
		globals.putMember("onLeaveArea", areaHandler("onLeaveArea", functions.getOnLeaveArea()));
		globals.putMember("onButton", valueHandler("onButton", functions.getOnButton()));
		globals.putMember("onItemOnGroundItem", valuePairHandler(
				"onItemOnGroundItem", functions.getOnItemOnGroundItem()));
		globals.putMember("onItemOnPlayer", valueHandler(
				"onItemOnPlayer", functions.getOnItemOnPlayer()));
		globals.putMember("onMagicOnItem", valuePairHandler(
				"onMagicOnItem", functions.getOnMagicOnItem()));
		globals.putMember("onMagicOnObject", valuePairHandler(
				"onMagicOnObject", functions.getOnMagicOnObject()));
		globals.putMember("onMagicOnNpc", valuePairHandler(
				"onMagicOnNpc", functions.getOnMagicOnNpc()));
		globals.putMember("onMagicOnPlayer", valueHandler(
				"onMagicOnPlayer", functions.getOnMagicOnPlayer()));
		globals.putMember("onPlayerDeath", requireHandler(
				"onPlayerDeath", functions.getOnPlayerDeath()));

		Map<String, Object> devMembers = new HashMap<>();
		devMembers.put("log", (ProxyExecutable) arguments -> {
			if (arguments != null && arguments.length > 0) {
				functions.getDev().log(asLoggable(arguments[0]));
			}
			return null;
		});
		globals.putMember("dev", ProxyObject.fromMap(devMembers));

		globals.putMember("log", (ProxyExecutable) arguments -> {
			if (arguments != null && arguments.length > 0) {
				functions.getLog().accept(asLoggable(arguments[0]));
			}
			return null;
		});
	}

	/**
	 * Converts one guest argument into its exact printable value. Plain
	 * {@code Value.toString()} can expose internal representation class
	 * names, so strings and primitives are narrowed explicitly.
	 */
	private static String asLoggable(Value value) {
		if (value == null || value.isNull()) {
			return "null";
		}
		if (value.isString()) {
			return value.asString();
		}
		if (value.isNumber()) {
			double number = value.asDouble();
			if (Double.isFinite(number) && number == Math.rint(number)
					&& Math.abs(number) < 9.0e15) {
				return String.valueOf(value.asLong());
			}
			return String.valueOf(number);
		}
		if (value.isBoolean()) {
			return String.valueOf(value.asBoolean());
		}
		return value.toString();
	}

	private static ProxyExecutable consumeValue(final java.util.function.Consumer<Value> consumer) {
		return arguments -> {
			if (arguments == null || arguments.length < 1) {
				throw new IllegalArgumentException(
						"Script definition requires one descriptor");
			}
			consumer.accept(arguments[0]);
			return null;
		};
	}

	private static ProxyExecutable triIntStr(final ScriptFunctions.TriIntStrFunction fn) {
		return arguments -> {
			if (arguments == null || arguments.length < 3) {
				throw new IllegalArgumentException(
						"Script registration requires id, action, and callback");
			}
			if (!arguments[1].isString()) {
				throw new IllegalArgumentException("action must be a string");
			}
			fn.apply(registrationInt(arguments[0], "id"),
					arguments[1].asString(), arguments[2]);
			return null;
		};
	}

	private static ProxyExecutable biStrValue(final java.util.function.BiConsumer<String, Value> fn) {
		return arguments -> {
			if (arguments == null || arguments.length < 2) {
				throw new IllegalArgumentException(
						"Script registration requires a name and callback");
			}
			if (!arguments[0].isString()) {
				throw new IllegalArgumentException("name must be a string");
			}
			fn.accept(arguments[0].asString(), arguments[1]);
			return null;
		};
	}

	private static ProxyExecutable biValue(
			final java.util.function.BiConsumer<Value, Value> fn) {
		return arguments -> {
			if (arguments == null || arguments.length < 2) {
				throw new IllegalArgumentException(
						"Script registration requires a descriptor and scope function");
			}
			fn.accept(arguments[0], arguments[1]);
			return null;
		};
	}

	private static ProxyExecutable pairInt(final ScriptFunctions.PairIntFunction fn) {
		return arguments -> {
			if (arguments == null || arguments.length < 3) {
				throw new IllegalArgumentException(
						"Script registration requires two ids and a callback");
			}
			fn.apply(registrationInt(arguments[0], "first id"),
					registrationInt(arguments[1], "second id"), arguments[2]);
			return null;
		};
	}

	private static ProxyExecutable requireHandler(final String name,
			final java.util.function.Consumer<Value> consumer) {
		return arguments -> {
			if (arguments == null || arguments.length < 1) {
				throw new IllegalArgumentException(name + " requires a callback");
			}
			consumer.accept(arguments[0]);
			return null;
		};
	}

	private static ProxyExecutable intHandler(final String name,
			final ScriptFunctions.IntHandlerFunction fn) {
		return arguments -> {
			if (arguments == null || arguments.length < 2) {
				throw new IllegalArgumentException(name + " requires an id and callback");
			}
			fn.apply(registrationInt(arguments[0], "id"), arguments[1]);
			return null;
		};
	}

	private static ProxyExecutable valueHandler(final String name,
			final ScriptFunctions.ValueHandlerFunction fn) {
		return arguments -> {
			if (arguments == null || arguments.length < 2) {
				throw new IllegalArgumentException(name + " requires an id and callback");
			}
			fn.apply(arguments[0], arguments[1]);
			return null;
		};
	}

	private static ProxyExecutable valuePairHandler(final String name,
			final ScriptFunctions.ValuePairHandlerFunction fn) {
		return arguments -> {
			if (arguments == null || arguments.length < 3) {
				throw new IllegalArgumentException(
						name + " requires two ids and a callback");
			}
			fn.apply(arguments[0], arguments[1], arguments[2]);
			return null;
		};
	}

	private static int registrationInt(Value value, String label) {
		if (value == null || value.isNull() || !value.isNumber()) {
			throw new IllegalArgumentException(label + " must be a number");
		}
		double number = value.asDouble();
		if (!Double.isFinite(number) || number != Math.rint(number)
				|| number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
			throw new IllegalArgumentException(label + " must be a finite integer");
		}
		return (int) number;
	}

	private static ProxyExecutable areaHandler(final String name,
			final ScriptFunctions.AreaHandlerFunction fn) {
		return arguments -> {
			if (arguments == null || arguments.length < 2) {
				throw new IllegalArgumentException(
						name + " requires an area descriptor and callback");
			}
			fn.apply(arguments[0], arguments[1]);
			return null;
		};
	}

}

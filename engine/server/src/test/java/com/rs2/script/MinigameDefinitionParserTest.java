package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.script.area.AreaDefinitionRegistry;
import com.rs2.script.minigame.MinigameDefinition;
import com.rs2.script.minigame.MinigameDefinitionParser;
import com.rs2.script.minigame.MinigameDefinitionRegistry;
import com.rs2.script.registries.RegistryStore;

public class MinigameDefinitionParserTest {

	private Context context;

	@Before
	public void setUp() {
		context = Context.newBuilder("js").build();
	}

	@After
	public void tearDown() {
		ScriptRuntimeTestFixture.reset();
		if (context != null) {
			context.close();
		}
	}

	@Test
	public void parsesCanonicalMinigameDefinition() {
		MinigameDefinition definition = parseWithAreas(context.eval("js", "({"
				+ "id:'wave-demo',"
				+ "name:'Wave Demo',"
				+ "command:'wave-demo',"
				+ "lobbyAreaId:'wave-demo-lobby',"
				+ "arenaAreaId:'wave-demo-arena',"
				+ "entrance:{x:3220,y:3220,plane:0},"
				+ "leave:{x:3218,y:3218,plane:0},"
				+ "minPlayers:1,maxPlayers:5,lobbyWaitTicks:0,"
				+ "timeLimitTicks:600,"
				+ "score:{namespace:'minigame',key:'wave_demo_score'},"
				+ "waves:["
				+ "{id:'wave-one',npcs:[{npcId:1,x:3220,y:3222}]},"
				+ "{id:'wave-two',npcs:[{npcId:1,x:3222,y:3222}]}"
				+ "]"
				+ "})"));
		assertEquals("wave-demo", definition.id());
		assertEquals(2, definition.waves().size());
		assertEquals("wave-one", definition.waves().get(0).id());
		assertNotNull(definition.score());
		assertEquals("wave_demo_score", definition.score().key());
		MinigameDefinition loaded = MinigameDefinitionRegistry.get("wave-demo");
		assertNotNull(loaded);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsUnknownAreaReference() {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			new MinigameDefinitionParser("wave-demo", 1).parse(context.eval("js",
					"({id:'bad',command:'bad-mini',lobbyAreaId:'missing-lobby',"
							+ "arenaAreaId:'missing-arena',"
							+ "entrance:{x:3220,y:3220,plane:0},"
							+ "leave:{x:3218,y:3218,plane:0},"
							+ "minPlayers:1,maxPlayers:1,lobbyWaitTicks:0,"
							+ "timeLimitTicks:100,"
							+ "waves:[{id:'one',npcs:[{npcId:1,x:3220,y:3222}]}]})"));
		} finally {
			RegistryStore.rollback(candidate);
		}
	}

	private MinigameDefinition parseWithAreas(Value definition) {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			ScriptFunctions.getInstance().getDefineArea().accept(context
					.eval("js", "({id:'wave-demo-lobby',"
							+ "name:'Lobby',bounds:{minX:3218,minY:3218,maxX:3224,"
							+ "maxY:3224,plane:0},npcs:[],objects:[]})"));
			ScriptFunctions.getInstance().getDefineArea().accept(context
					.eval("js", "({id:'wave-demo-arena',"
							+ "name:'Arena',bounds:{minX:3218,minY:3218,maxX:3230,"
							+ "maxY:3230,plane:0},npcs:[],objects:[]})"));
			assertNotNull(AreaDefinitionRegistry.get(candidate,
					"wave-demo-lobby"));
			MinigameDefinition parsed = new MinigameDefinitionParser(
					"wave-demo", 1).parse(definition);
			MinigameDefinitionRegistry.put(parsed);
			ScriptRuntimeTestFixture.publishCandidate(context, candidate);
			return parsed;
		} catch (RuntimeException error) {
			RegistryStore.rollback(candidate);
			throw error;
		}
	}
}

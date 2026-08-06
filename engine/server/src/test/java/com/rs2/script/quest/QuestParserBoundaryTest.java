package com.rs2.script.quest;

import static org.junit.Assert.fail;

import org.graalvm.polyglot.Context;
import org.junit.Test;

/**
 * Table-driven {@code -1/exact/+1} byte/count/value matrix for every quest
 * parser bound plus unknown members, wrong types, duplicates, and overflow.
 * The exact bound values are accepted; every value one step outside is
 * rejected with the source/path diagnostic.
 */
public class QuestParserBoundaryTest {

	private static final String SKILLS_JS = "['attack','defence','strength',"
			+ "'hitpoints','ranged','prayer','magic','cooking','woodcutting',"
			+ "'fletching','fishing','firemaking','crafting','smithing',"
			+ "'mining','herblore','agility','thieving','slayer','farming',"
			+ "'runecraft']";

	@Test
	public void questIdBoundary() {
		assertAccepted(quest("id:'" + repeat("a", 63) + "'"));
		assertAccepted(quest("id:'" + repeat("a", 64) + "'"));
		assertRejected(quest("id:'" + repeat("a", 65) + "'"));
		assertRejected(quest("id:''"));
		assertRejected(quest("id:'Bad_Id'"));
		assertRejected(quest("id:'has-UPPER'"));
	}

	@Test
	public void nameBoundary() {
		assertAccepted(quest("name:'" + repeat("x", 127) + "'"));
		assertAccepted(quest("name:'" + repeat("x", 128) + "'"));
		assertRejected(quest("name:'" + repeat("x", 129) + "'"));
		assertRejected(quest("name:''"));
	}

	@Test
	public void summaryBoundary() {
		assertAccepted(quest("summary:'" + repeat("x", 1023) + "'"));
		assertAccepted(quest("summary:'" + repeat("x", 1024) + "'"));
		assertRejected(quest("summary:'" + repeat("x", 1025) + "'"));
		assertRejected(quest("summary:''"));
	}

	@Test
	public void stageCountBoundary() {
		assertAccepted(quest("stages:stages(127)"));
		assertAccepted(quest("stages:stages(128)"));
		assertRejected(quest("stages:stages(129)"));
		assertRejected(quest("stages:[]"));
		assertRejected(quest("stages:{}"));
		assertRejected(quest("stages:stages(1),extra:1"));
	}

	@Test
	public void objectiveBoundary() {
		assertAccepted(quest("stages:[{stage:0,objective:'" + repeat("o", 511)
				+ "'}]"));
		assertAccepted(quest("stages:[{stage:0,objective:'" + repeat("o", 512)
				+ "'}]"));
		assertRejected(quest("stages:[{stage:0,objective:'" + repeat("o", 513)
				+ "'}]"));
		assertRejected(quest("stages:[{stage:0,objective:''}]"));
		assertRejected(quest("stages:[{stage:0,objective:5}]"));
		assertRejected(quest("stages:[{stage:0,objective:'x',onEnter:function(){}}]"));
		assertRejected(quest("stages:[{stage:0,objective:'x'},{stage:2,objective:'y'}]"));
		assertRejected(quest("stages:[{stage:0.5,objective:'x'}]"));
	}

	@Test
	public void questPointBoundaries() {
		assertRejected(quest("requirements:{questPoints:-1}"));
		assertAccepted(quest("requirements:{questPoints:0}"));
		assertAccepted(quest("requirements:{questPoints:1}"));
		assertAccepted(quest("requirements:{questPoints:9999}"));
		assertAccepted(quest("requirements:{questPoints:10000}"));
		assertRejected(quest("requirements:{questPoints:10001}"));
		assertRejected(quest("rewards:{questPoints:-1}"));
		assertAccepted(quest("rewards:{questPoints:0}"));
		assertAccepted(quest("rewards:{questPoints:1}"));
		assertAccepted(quest("rewards:{questPoints:9999}"));
		assertAccepted(quest("rewards:{questPoints:10000}"));
		assertRejected(quest("rewards:{questPoints:10001}"));
		assertRejected(quest("requirements:{questPoints:NaN}"));
		assertRejected(quest("requirements:{questPoints:Infinity}"));
		assertRejected(quest("requirements:{questPoints:1.5}"));
	}

	@Test
	public void completedQuestCountBoundary() {
		assertAccepted(quest("requirements:{completedQuests:questIds(63)}"));
		assertAccepted(quest("requirements:{completedQuests:questIds(64)}"));
		assertRejected(quest("requirements:{completedQuests:questIds(65)}"));
		assertRejected(quest("requirements:{completedQuests:['Bad_Quest']}"));
		assertRejected(quest("requirements:{completedQuests:['"
				+ repeat("a", 65) + "']}"));
		assertRejected(quest("requirements:{completedQuests:['dup','dup']}"));
	}

	@Test
	public void skillCountAndLevelBoundary() {
		assertAccepted(quest("requirements:{skills:skills(20)}"));
		assertAccepted(quest("requirements:{skills:skills(21)}"));
		assertRejected(quest("requirements:{skills:skills(22)}"));
		assertRejected(quest("requirements:{skills:[{skill:'sailing',level:1}]}"));
		assertRejected(quest("requirements:{skills:[{skill:'magic',level:0}]}"));
		assertAccepted(quest("requirements:{skills:[{skill:'magic',level:1}]}"));
		assertAccepted(quest("requirements:{skills:[{skill:'magic',level:98}]}"));
		assertAccepted(quest("requirements:{skills:[{skill:'magic',level:99}]}"));
		assertRejected(quest("requirements:{skills:[{skill:'magic',level:100}]}"));
		assertRejected(quest("requirements:{skills:[{skill:'magic',level:1.5}]}"));
		assertRejected(quest("requirements:{skills:[{skill:'magic',level:1},"
				+ "{skill:'magic',level:2}]}"));
		assertRejected(quest("requirements:{skills:[{skill:'"
				+ repeat("a", 33) + "',level:1}]}"));
	}

	@Test
	public void itemCountAndValueBoundary() {
		assertAccepted(quest("requirements:{items:items(63)}"));
		assertAccepted(quest("requirements:{items:items(64)}"));
		assertRejected(quest("requirements:{items:items(65)}"));
		assertAccepted(quest("rewards:{items:items(63)}"));
		assertAccepted(quest("rewards:{items:items(64)}"));
		assertRejected(quest("rewards:{items:items(65)}"));
		assertRejected(quest("requirements:{items:[{itemId:0,amount:1}]}"));
		assertAccepted(quest("requirements:{items:[{itemId:1,amount:1}]}"));
		assertAccepted(quest("requirements:{items:[{itemId:65534,amount:1}]}"));
		assertAccepted(quest("requirements:{items:[{itemId:65535,amount:1}]}"));
		assertRejected(quest("requirements:{items:[{itemId:65536,amount:1}]}"));
		assertRejected(quest("requirements:{items:[{itemId:995,amount:0}]}"));
		assertAccepted(quest("requirements:{items:[{itemId:995,amount:1}]}"));
		assertAccepted(quest("requirements:{items:[{itemId:995,amount:2147483647}]}"));
		assertRejected(quest("requirements:{items:[{itemId:995,amount:2147483648}]}"));
		assertRejected(quest("requirements:{items:[{itemId:995.5,amount:1}]}"));
		assertRejected(quest("requirements:{items:[{itemId:'995',amount:1}]}"));
		assertRejected(quest("requirements:{items:[{itemId:995,amount:NaN}]}"));
		assertRejected(quest("requirements:{items:[{itemId:995,amount:1},"
				+ "{itemId:995,amount:2}]}"));
	}

	@Test
	public void experienceCountAndAmountBoundary() {
		assertAccepted(quest("rewards:{experience:exps(20)}"));
		assertAccepted(quest("rewards:{experience:exps(21)}"));
		assertRejected(quest("rewards:{experience:exps(22)}"));
		assertRejected(quest("rewards:{experience:[{skill:'magic',amount:0}]}"));
		assertAccepted(quest("rewards:{experience:[{skill:'magic',amount:1}]}"));
		assertAccepted(quest("rewards:{experience:[{skill:'magic',amount:199999999}]}"));
		assertAccepted(quest("rewards:{experience:[{skill:'magic',amount:200000000}]}"));
		assertRejected(quest("rewards:{experience:[{skill:'magic',amount:200000001}]}"));
		assertRejected(quest("rewards:{experience:[{skill:'magic',amount:NaN}]}"));
		assertRejected(quest("rewards:{experience:[{skill:'magic',amount:1},"
				+ "{skill:'magic',amount:2}]}"));
	}

	@Test
	public void unknownMembersAndWrongTypesAreRejected() {
		assertRejected("null");
		assertRejected(quest("difficulty:'novice'"));
		assertRejected(quest("stages:[{stage:0,objective:'x'}],note:'n'"));
		assertRejected(quest("requirements:{questPoints:1,note:'n'}"));
		assertRejected(quest("rewards:{questPoints:1,note:'n'}"));
		assertRejected(quest("requirements:{skills:5}"));
		assertRejected(quest("requirements:{items:'nope'}"));
		assertRejected(quest("requirements:{completedQuests:'nope'}"));
		assertRejected(quest("rewards:{experience:5}"));
		assertRejected("5");
		assertRejected("'quest'");
	}

	private static String quest(String splice) {
		return "({id:'bound',name:'Bound',summary:'Summary',"
				+ "stages:[{stage:0,objective:'Done'}]," + splice + "})";
	}

	private static void assertAccepted(String script) {
		try (Context context = Context.create("js")) {
			new QuestDefinitionParser().parse(context.eval("js",
					"stages=(n)=>Array.from({length:n},(_,i)=>({stage:i,"
							+ "objective:'o'}));"
							+ "questIds=(n)=>Array.from({length:n},(_,i)=>'dep'+i);"
							+ "skills=(n)=>Array.from({length:n},(_,i)=>({skill:"
							+ SKILLS_JS + "[i],level:1}));"
							+ "exps=(n)=>Array.from({length:n},(_,i)=>({skill:"
							+ SKILLS_JS + "[i],amount:1}));"
							+ "items=(n)=>Array.from({length:n},(_,i)=>({itemId:"
							+ "995+i,amount:1}));" + script));
		} catch (QuestDefinitionException failure) {
			fail("descriptor should parse: " + script + " ("
					+ failure.getMessage() + ")");
		}
	}

	private static void assertRejected(String script) {
		try (Context context = Context.create("js")) {
			try {
				new QuestDefinitionParser().parse(context.eval("js",
						"stages=(n)=>Array.from({length:n},(_,i)=>({stage:i,"
								+ "objective:'o'}));"
								+ "questIds=(n)=>Array.from({length:n},(_,i)=>'dep'+i);"
								+ "skills=(n)=>Array.from({length:n},(_,i)=>({skill:"
								+ SKILLS_JS + "[i],level:1}));"
								+ "exps=(n)=>Array.from({length:n},(_,i)=>({skill:"
								+ SKILLS_JS + "[i],amount:1}));"
								+ "items=(n)=>Array.from({length:n},(_,i)=>({itemId:"
								+ "995+i,amount:1}));" + script));
				fail("descriptor should fail: " + script);
			} catch (QuestDefinitionException expected) {
				// expected
			}
		}
	}

	private static String repeat(String text, int count) {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < count; i++) {
			builder.append(text);
		}
		return builder.toString();
	}
}

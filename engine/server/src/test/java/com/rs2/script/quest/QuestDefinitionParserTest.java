package com.rs2.script.quest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.Test;

public class QuestDefinitionParserTest {

	@Test
	public void parsesAndOwnsStrictDescriptorAfterContextCloses() {
		QuestDefinition parsed;
		try (Context context = Context.create("js")) {
			Value value = context.eval("js", "({id:'owned-quest',name:'Owned',"
					+ "summary:'Copied into Java.',"
					+ "stages:[{stage:0,objective:'Begin.'},{stage:1,objective:'End.'}],"
					+ "requirements:{questPoints:2,skills:[{skill:'magic',level:5}]},"
					+ "rewards:{questPoints:1,items:[{itemId:995,amount:10}],"
					+ "experience:[{skill:'magic',amount:25}]}})");
			parsed = new QuestDefinitionParser().parse(value);
		}
		assertEquals("owned-quest", parsed.getId());
		assertEquals(2, parsed.getStages().size());
		assertEquals(5, parsed.getRequirements().getSkills().get(0).getLevel());
		assertEquals(25, parsed.getRewards().getExperience().get(0).getAmount());
	}

	@Test
	public void rejectsFractionalUnsafeUnknownAndCallbackMembers() {
		assertRejected("({id:'bad',name:'Bad',summary:'Bad',"
				+ "stages:[{stage:0.5,objective:'No.'}]})");
		assertRejected("({id:'bad',name:'Bad',summary:'Bad',"
				+ "stages:[{stage:0,objective:'No.'}],"
				+ "rewards:{questPoints:9007199254740992}})");
		assertRejected("({id:'bad',name:'Bad',summary:'Bad',"
				+ "stages:[{stage:0,objective:'No.'}],difficulty:'novice'})");
		assertRejected("({id:'bad',name:'Bad',summary:'Bad',"
				+ "stages:[{stage:0,objective:'No.',onEnter:()=>{}}]})");
	}

	@Test
	public void itemIdValidationDoesNotRequireDefinitionInitialization() {
		try (Context context = Context.create("js")) {
			QuestDefinition parsed = new QuestDefinitionParser().parse(
					context.eval("js", "({id:'numeric-items',name:'Items',"
							+ "summary:'Bounds only.',"
							+ "stages:[{stage:0,objective:'Done.'}],"
							+ "rewards:{items:[{itemId:14999,amount:1}]}})"));
			assertEquals(14999,
					parsed.getRewards().getItems().get(0).getItemId());
		}
	}

	@Test
	public void rejectsMalformedNestedShapesBoundsDuplicatesAndUnknownSkills() {
		assertRejected("null");
		assertRejected("({id:'missing',name:'Missing',summary:'No stages'})");
		assertRejected("({id:'bad',name:'Bad',summary:'Bad',stages:{}})");
		assertRejected("({id:'bad',name:'Bad',summary:'Bad',stages:[]})");
		assertRejected("({id:'bad',name:'Bad',summary:'Bad',"
				+ "stages:[{stage:1,objective:'Gap'}]})");
		assertRejected("({id:'bad',name:'Bad',summary:'Bad',"
				+ "stages:[{stage:0,objective:'One'},"
				+ "{stage:0,objective:'Duplicate'}]})");
		assertRejected("({id:'bad',name:'Bad',summary:'Bad',"
				+ "stages:[{stage:0,objective:'Done'}],"
				+ "requirements:{skills:[{skill:'sailing',level:1}]}})");
		assertRejected("({id:'bad',name:'Bad',summary:'Bad',"
				+ "stages:[{stage:0,objective:'Done'}],"
				+ "requirements:{items:[{itemId:0,amount:1}]}})");
		assertRejected("({id:'bad',name:'Bad',summary:'Bad',"
				+ "stages:[{stage:0,objective:'Done'}],"
				+ "rewards:{items:[{itemId:995,amount:1},"
				+ "{itemId:995,amount:2}]}})");
		assertRejected("({id:'bad',name:'Bad',summary:'Bad',"
				+ "stages:[{stage:0,objective:'Done'}],"
				+ "rewards:{experience:[{skill:'magic',amount:NaN}]}})");
	}

	private static void assertRejected(String script) {
		try (Context context = Context.create("js")) {
			try {
				new QuestDefinitionParser().parse(context.eval("js", script));
				fail("descriptor should fail: " + script);
			} catch (QuestDefinitionException expected) {
				// expected
			}
		}
	}
}

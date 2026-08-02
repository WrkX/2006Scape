package com.rs2.script.quest;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.graalvm.polyglot.Context;

import com.rs2.script.registries.QuestRegistry;
import com.rs2.script.registries.RegistryStore;
import com.rs2.script.ScriptRuntimeTestFixture;

public class QuestRegistryValidationTest {

	private Context context;

	@Before
	public void setUp() {
		context = Context.create("js");
	}

	@After
	public void clear() {
		ScriptRuntimeTestFixture.reset();
		context.close();
	}

	@Test
	public void candidateRejectsMissingSelfAndCycles() {
		assertInvalid(definition("one", "missing"));
		assertInvalid(definition("self", "self"));

		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			QuestRegistry.put("one", definition("one", "two"));
			QuestRegistry.put("two", definition("two", "one"));
			QuestRegistry.validateCandidate(candidate);
			fail("cycle should fail");
		} catch (QuestDefinitionException expected) {
			RegistryStore.rollback(candidate);
		}
	}

	@Test
	public void failedCandidateDoesNotReplaceActiveDescriptors() {
		RegistryStore.State stable = RegistryStore.beginStaging();
		QuestDefinition active = definition("stable");
		QuestRegistry.put("stable", active);
		QuestRegistry.validateCandidate(stable);
		ScriptRuntimeTestFixture.publishCandidate(context, stable);

		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			QuestRegistry.put("broken", definition("broken", "missing"));
			QuestRegistry.validateCandidate(candidate);
			fail("missing dependency should fail");
		} catch (QuestDefinitionException expected) {
			RegistryStore.rollback(candidate);
		}
		assertSame(active, QuestRegistry.get("stable"));
	}

	private static void assertInvalid(QuestDefinition definition) {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			QuestRegistry.put(definition.getId(), definition);
			QuestRegistry.validateCandidate(candidate);
			fail("candidate should fail");
		} catch (QuestDefinitionException expected) {
			RegistryStore.rollback(candidate);
		}
	}

	static QuestDefinition definition(String id, String... dependencies) {
		return new QuestDefinition(id, id, "Summary",
				Arrays.asList(new QuestDefinition.Stage(0, "Done")),
				new QuestDefinition.Requirements(0, Arrays.asList(dependencies),
						Collections.<QuestDefinition.SkillRequirement>emptyList(),
						Collections.<QuestDefinition.ItemAmount>emptyList()),
				new QuestDefinition.Rewards(0,
						Collections.<QuestDefinition.ItemAmount>emptyList(),
						Collections.<QuestDefinition.ExperienceReward>emptyList()));
	}
}

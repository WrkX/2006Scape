package com.rs2.script.quest;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.apollo.cache.def.ItemDefinition;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.game.players.Player;

public class QuestCompletionConcurrencyTest {

	private ItemDefinition[] previousDefinitions;

	@Before
	public void definitions() throws Exception {
		Field field = ItemDefinition.class.getDeclaredField("definitions");
		field.setAccessible(true);
		previousDefinitions = (ItemDefinition[]) field.get(null);
		ItemDefinition[] definitions = new ItemDefinition[1001];
		ItemDefinition coins = new ItemDefinition(995);
		coins.setName("Coins");
		coins.setDescription("Coins");
		coins.setStackable(true);
		definitions[995] = coins;
		field.set(null, definitions);
	}

	@After
	public void restore() throws Exception {
		Field field = ItemDefinition.class.getDeclaredField("definitions");
		field.setAccessible(true);
		field.set(null, previousDefinitions);
	}

	@Test
	public void concurrentExpectedStageCompletionGrantsRewardsOnce()
			throws Exception {
		Player player = new Player(-1) { };
		QuestDefinition definition = QuestServiceTest.definition(
				"concurrent-completion", 0,
				QuestServiceTest.emptyRequirements(),
				new QuestDefinition.Rewards(1,
						Arrays.asList(new QuestDefinition.ItemAmount(995, 100)),
						java.util.Collections
								.<QuestDefinition.ExperienceReward>emptyList()));
		QuestService service = new QuestService(new QuestRewardTransaction());
		service.start(player, definition);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch go = new CountDownLatch(1);
		AtomicInteger changed = new AtomicInteger();
		Runnable completion = () -> {
			ready.countDown();
			try {
				go.await();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			if (service.complete(player, definition, 0).changed()) {
				changed.incrementAndGet();
			}
		};
		Thread first = new Thread(completion);
		Thread second = new Thread(completion);
		first.start();
		second.start();
		ready.await();
		go.countDown();
		first.join();
		second.join();

		assertEquals(1, changed.get());
		assertEquals(1, player.questPoints);
		assertEquals(100, player.getItemAssistant().getItemAmount(995));
	}
}

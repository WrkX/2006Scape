package com.rs2.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

public class CycleEventHandlerTest {

	private final Object owner = new Object();

	@After
	public void tearDown() {
		CycleEventHandler.getSingleton().stopEvents(owner);
		CycleEventHandler.getSingleton().process();
	}

	@Test
	public void stopEventsRemovesStoppedContainers() {
		CycleEventHandler handler = CycleEventHandler.getSingleton();
		handler.addEvent(owner, noopEvent(), 10);
		assertEquals(1, handler.getEventsCount());

		handler.stopEvents(owner);

		assertEquals(0, handler.getEventsCount());
	}

	@Test
	public void processRemovesEventThatStopsDuringExecution() {
		CycleEventHandler handler = CycleEventHandler.getSingleton();
		final boolean[] stopped = { false };
		handler.addEvent(owner, new CycleEvent() {
			@Override
			public void execute(CycleEventContainer container) {
				container.stop();
			}

			@Override
			public void stop() {
				stopped[0] = true;
			}
		}, 1);
		assertEquals(1, handler.getEventsCount());

		handler.process();

		assertTrue(stopped[0]);
		assertEquals(0, handler.getEventsCount());
	}

	@Test
	public void stopEventsWithIdRemovesOnlyMatchingEvent() {
		CycleEventHandler handler = CycleEventHandler.getSingleton();
		int eventId = "miningEvent".hashCode();
		handler.addEvent(eventId, owner, noopEvent(), 10);
		handler.addEvent(owner, noopEvent(), 10);
		assertEquals(2, handler.getEventsCount());

		handler.stopEvents(owner, eventId);

		assertEquals(1, handler.getEventsCount());
	}

	private static CycleEvent noopEvent() {
		return new CycleEvent() {
			@Override
			public void execute(CycleEventContainer container) {
			}

			@Override
			public void stop() {
			}
		};
	}
}

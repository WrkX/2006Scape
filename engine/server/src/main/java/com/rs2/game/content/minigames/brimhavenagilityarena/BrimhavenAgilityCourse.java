package com.rs2.game.content.minigames.brimhavenagilityarena;

import com.rs2.Constants;
import com.rs2.event.CycleEvent;
import com.rs2.event.CycleEventContainer;
import com.rs2.event.CycleEventHandler;
import com.rs2.game.content.StaticObjectList;
import com.rs2.game.content.skills.agility.Agility;
import com.rs2.game.players.Player;

/**
 * Obstacle handlers for the Brimhaven Agility Arena course.
 */
public final class BrimhavenAgilityCourse {

	private static final int ROPE_SWING_ANIMATION = 751;
	private static final int ROPE_JUMP_ANIMATION = 3067;
	private static final int ROPE_SWING_DURATION_TICKS = 4;
	private static final int ROPE_JUMP_TICKS = 2;
	private static final int GRID1_SOUTH_ROPE_START_X = 2806;
	private static final int GRID1_SOUTH_ROPE_START_Y = 9587;
	private static final int LEDGE_WALK_ANIMATION = 756;
	private static final int LEDGE_WALK_REVERSE_ANIMATION = 754;
	private static final int MONKEY_BARS_WALK_ANIMATION = 744;
	private static final int HAND_HOLDS_LEFT_ANIMATION = 1121;
	private static final int HAND_HOLDS_LEFT_ALT_ANIMATION = 1122;
	private static final int HAND_HOLDS_RIGHT_ANIMATION = 1118;
	private static final int HAND_HOLDS_RIGHT_ALT_ANIMATION = 1119;
	private static final int HAND_HOLDS_STEP_TICKS = 2;
	private static final int PILLAR_JUMP_ANIMATION = 1603;
	private static final int LOW_WALL_CLIMB_ANIMATION = 839;
	private static final int PILLAR_JUMP_TICKS = 2;
	private static final int LOW_WALL_CLIMB_TICKS = 2;
	private static final int LOW_WALL_HOP_DISTANCE = 2;
	private static final int PLATFORM_RADIUS = 2;
	private static final int HAND_HOLDS_LEVEL = 20;

	private enum ObstacleType {
		PLANK(Agility.LOG_EMOTE, 12, MovementStyle.WALK),
		ROPE_SWING(ROPE_SWING_ANIMATION, 12, MovementStyle.ROPE_SWING),
		BALANCING_ROPE(Agility.LOG_EMOTE, 10, MovementStyle.WALK),
		LOG_BALANCE(Agility.LOG_EMOTE, 12, MovementStyle.WALK),
		BALANCING_LEDGE(LEDGE_WALK_ANIMATION, 16, MovementStyle.WALK),
		MONKEY_BARS(MONKEY_BARS_WALK_ANIMATION, 14, MovementStyle.GAP_WALK),
		PILLAR(PILLAR_JUMP_ANIMATION, 18, MovementStyle.PILLAR_HOP),
		LOW_WALL(LOW_WALL_CLIMB_ANIMATION, 8, MovementStyle.LOW_WALL),
		HAND_HOLDS(HAND_HOLDS_LEFT_ANIMATION, 22, MovementStyle.WALK);

		private final int animation;
		private final double xp;
		private final MovementStyle movementStyle;

		ObstacleType(int animation, double xp, MovementStyle movementStyle) {
			this.animation = animation;
			this.xp = xp;
			this.movementStyle = movementStyle;
		}
	}

	private enum MovementStyle {
		WALK, ROPE_SWING, GAP_WALK, PILLAR_HOP, LOW_WALL
	}

	private static final class Crossing {
		private final ObstacleType type;
		private final int ax;
		private final int ay;
		private final int bx;
		private final int by;
		/** Walk line on the obstacle axis; -1 uses platform center. */
		private final int walkLineX;
		private final int walkLineY;
		/** Object-line offset for gap obstacles; player walks on platform center when false. */
		private final boolean playerUsesWalkLine;
		private final int[] pillars;

		private Crossing(ObstacleType type, int ax, int ay, int bx, int by) {
			this(type, ax, ay, bx, by, -1, -1, false, null);
		}

		private Crossing(ObstacleType type, int ax, int ay, int bx, int by,
				int walkLineX, int walkLineY) {
			this(type, ax, ay, bx, by, walkLineX, walkLineY, true, null);
		}

		private Crossing(ObstacleType type, int ax, int ay, int bx, int by, int[] pillars) {
			this(type, ax, ay, bx, by, -1, -1, false, pillars);
		}

		private Crossing(ObstacleType type, int ax, int ay, int bx, int by,
				int walkLineX, int walkLineY, int[] pillars) {
			this(type, ax, ay, bx, by, walkLineX, walkLineY, true, pillars);
		}

		private Crossing(ObstacleType type, int ax, int ay, int bx, int by,
				int walkLineX, int walkLineY, boolean playerUsesWalkLine, int[] pillars) {
			this.type = type;
			this.ax = ax;
			this.ay = ay;
			this.bx = bx;
			this.by = by;
			this.walkLineX = walkLineX;
			this.walkLineY = walkLineY;
			this.playerUsesWalkLine = playerUsesWalkLine;
			this.pillars = pillars;
		}
	}

	private static final class Traversal {
		private final ObstacleType type;
		private final int destX;
		private final int destY;
		private final int walkAnimation;
		private final int travelDirX;
		private final int travelDirY;
		private final int alignX;
		private final int alignY;
		private final int gapStartX;
		private final int gapStartY;

		private Traversal(ObstacleType type, int destX, int destY, int walkAnimation) {
			this(type, destX, destY, walkAnimation, 0, 0, -1, -1, -1, -1);
		}

		private Traversal(ObstacleType type, int destX, int destY, int walkAnimation,
				int travelDirX, int travelDirY) {
			this(type, destX, destY, walkAnimation, travelDirX, travelDirY, -1, -1, -1, -1);
		}

		private Traversal(ObstacleType type, int destX, int destY, int walkAnimation,
				int travelDirX, int travelDirY, int alignX, int alignY) {
			this(type, destX, destY, walkAnimation, travelDirX, travelDirY, alignX, alignY, -1, -1);
		}

		private Traversal(ObstacleType type, int destX, int destY, int walkAnimation,
				int travelDirX, int travelDirY, int alignX, int alignY,
				int gapStartX, int gapStartY) {
			this.type = type;
			this.destX = destX;
			this.destY = destY;
			this.walkAnimation = walkAnimation;
			this.travelDirX = travelDirX;
			this.travelDirY = travelDirY;
			this.alignX = alignX;
			this.alignY = alignY;
			this.gapStartX = gapStartX;
			this.gapStartY = gapStartY;
		}
	}

	private static final Crossing[] CROSSINGS = {
			// Plank
			new Crossing(ObstacleType.PLANK, 2761, 9557, 2772, 9557),
			new Crossing(ObstacleType.PLANK, 2794, 9590, 2805, 9590),
			// Rope swing
			new Crossing(ObstacleType.ROPE_SWING, 2761, 9568, 2772, 9568),
			new Crossing(ObstacleType.ROPE_SWING, 2805, 9579, 2805, 9590),
			// Balancing rope (objects sit on platform center column/row)
			new Crossing(ObstacleType.BALANCING_ROPE, 2772, 9557, 2772, 9568, 2772, -1),
			new Crossing(ObstacleType.BALANCING_ROPE, 2783, 9579, 2783, 9590, 2783, -1),
			new Crossing(ObstacleType.BALANCING_ROPE, 2794, 9546, 2794, 9557, 2794, -1),
			// Log balance
			new Crossing(ObstacleType.LOG_BALANCE, 2761, 9579, 2772, 9579),
			new Crossing(ObstacleType.LOG_BALANCE, 2794, 9579, 2794, 9590),
			new Crossing(ObstacleType.LOG_BALANCE, 2805, 9546, 2805, 9557),
			// Balancing ledge
			new Crossing(ObstacleType.BALANCING_LEDGE, 2761, 9546, 2772, 9546),
			new Crossing(ObstacleType.BALANCING_LEDGE, 2761, 9590, 2772, 9590),
			new Crossing(ObstacleType.BALANCING_LEDGE, 2794, 9546, 2805, 9546),
			// Monkey bars — objects offset from center; player walks platform center row/column
			new Crossing(ObstacleType.MONKEY_BARS, 2772, 9546, 2783, 9546, -1, 9545, false, null),
			new Crossing(ObstacleType.MONKEY_BARS, 2772, 9568, 2772, 9579, 2771, -1, false, null),
			new Crossing(ObstacleType.MONKEY_BARS, 2794, 9557, 2794, 9568, 2793, -1, false, null),
			// Pillar (stepping stones in the gap — cache-verified coordinates)
			new Crossing(ObstacleType.PILLAR, 2761, 9546, 2761, 9557,
					new int[] { 9549, 9550, 9551, 9552, 9553, 9554 }),
			new Crossing(ObstacleType.PILLAR, 2783, 9568, 2794, 9568,
					new int[] { 2786, 2787, 2788, 2789, 2790, 2791 }),
			new Crossing(ObstacleType.PILLAR, 2805, 9568, 2805, 9579,
					new int[] { 9571, 9572, 9573, 9574, 9575, 9576 }),
			// Low wall (on walkable corridors between platforms)
			new Crossing(ObstacleType.LOW_WALL, 2772, 9590, 2783, 9590),
			new Crossing(ObstacleType.LOW_WALL, 2783, 9557, 2783, 9568),
			new Crossing(ObstacleType.LOW_WALL, 2805, 9557, 2805, 9568),
			// Hand holds (level 20 — cache-verified walk lines)
			new Crossing(ObstacleType.HAND_HOLDS, 2772, 9557, 2772, 9568, 2759, -1),
			new Crossing(ObstacleType.HAND_HOLDS, 2783, 9546, 2794, 9546, -1, 9544),
			new Crossing(ObstacleType.HAND_HOLDS, 2783, 9590, 2794, 9590, -1, 9592),
	};

	private BrimhavenAgilityCourse() {
	}

	/**
	 * Gap obstacles sit over void between platforms. Players cannot path onto the
	 * object tile, so object clicks must be accepted from the adjacent platform.
	 */
	public static boolean canInteractFromDistance(Player player, int objectId,
			int objectX, int objectY) {
		if (!BrimhavenAgilityArena.isInArena(player)) {
			return false;
		}
		ObstacleType type = objectIdToType(objectId);
		if (type == null) {
			return false;
		}
		if (type.movementStyle != MovementStyle.ROPE_SWING
				&& type.movementStyle != MovementStyle.GAP_WALK
				&& type != ObstacleType.HAND_HOLDS) {
			return false;
		}
		return resolveTraversal(player, objectId, objectX, objectY) != null;
	}

	public static boolean handleObject(Player player, int objectId, int objectX, int objectY) {
		if (!BrimhavenAgilityArena.isInArena(player)) {
			return false;
		}
		ObstacleType type = objectIdToType(objectId);
		if (type == null) {
			return false;
		}
		if (type == ObstacleType.HAND_HOLDS
				&& player.playerLevel[Constants.AGILITY] < HAND_HOLDS_LEVEL) {
			player.getPacketSender().sendMessage(
					"You need an Agility level of 20 to use these hand holds.");
			return true;
		}
		Traversal traversal = resolveTraversal(player, objectId, objectX, objectY);
		if (traversal == null) {
			player.getPacketSender().sendMessage("You cannot reach that from here.");
			return true;
		}
		switch (traversal.type.movementStyle) {
			case WALK:
				if (traversal.type == ObstacleType.HAND_HOLDS) {
					handleHandHolds(player, traversal);
				} else if (traversal.type == ObstacleType.BALANCING_ROPE) {
					handleBalancingRope(player, traversal);
				} else if (traversal.type == ObstacleType.BALANCING_LEDGE) {
					handleBalancingLedge(player, traversal);
				} else {
					handleWalk(player, traversal);
				}
				break;
			case GAP_WALK:
				handleMonkeyBars(player, traversal);
				break;
			case ROPE_SWING:
				handleRopeSwing(player, traversal, objectX, objectY);
				break;
			case PILLAR_HOP:
				handlePillarHop(player, traversal);
				break;
			case LOW_WALL:
				handleLowWall(player, traversal, objectX, objectY);
				break;
		}
		return true;
	}

	private static void handleWalk(Player player, Traversal traversal) {
		if (player.stopPlayerPacket) {
			return;
		}
		final int destX = traversal.destX;
		final int destY = traversal.destY;
		if (destX == player.absX && destY == player.absY) {
			return;
		}
		final int dx = destX - player.absX;
		final int dy = destY - player.absY;
		player.stopPlayerPacket = true;
		player.getPlayerAction().setAction(true);
		player.getPlayerAction().canWalk(false);
		player.isRunning2 = false;
		player.playerWalkIndex = traversal.walkAnimation;
		player.getPlayerAssistant().requestUpdates();
		player.getPlayerAssistant().walkTo2(dx, dy);
		final int travelTicks = walkDelayTicks(dx, dy);
		CycleEventHandler.getSingleton().addEvent(player, new CycleEvent() {
			@Override
			public void execute(CycleEventContainer container) {
				if (player.disconnected) {
					container.stop();
					return;
				}
				finishTraversal(player, destX, destY, traversal.type);
				container.stop();
			}

			@Override
			public void stop() {
			}
		}, travelTicks);
	}

	/** Matches {@link Agility#destinationReached} tick timing for walk emotes. */
	private static int walkDelayTicks(int dx, int dy) {
		if (dx >= 0 && dy >= 0 && dx != dy) {
			return dx + dy;
		}
		if (dx == dy) {
			return dx;
		}
		if (dx < 0) {
			return -dx + dy;
		}
		return dx - dy;
	}

	private static void handleMonkeyBars(Player player, Traversal traversal) {
		handleAlignedGapWalk(player, traversal, MONKEY_BARS_WALK_ANIMATION, PLATFORM_RADIUS);
	}

	private static void handleHandHolds(Player player, Traversal traversal) {
		if (player.stopPlayerPacket) {
			return;
		}
		final int destX = traversal.destX;
		final int destY = traversal.destY;
		final int gapStartX = traversal.gapStartX;
		final int gapStartY = traversal.gapStartY;
		final int distance = Math.abs(destX - gapStartX) + Math.abs(destY - gapStartY);
		final int steps = distance + 1;
		final int travelStepX = Integer.signum(destX - gapStartX);
		final int travelStepY = Integer.signum(destY - gapStartY);
		final boolean goingRight = destX > gapStartX || destY > gapStartY;

		player.stopPlayerPacket = true;
		player.getPlayerAction().setAction(true);
		player.getPlayerAction().canWalk(false);
		player.isRunning2 = false;

		if (player.absX != gapStartX || player.absY != gapStartY) {
			walkLineTicks(player, player.absX, player.absY, gapStartX, gapStartY,
					handHoldsAnimationForStep(0, goingRight), null,
					() -> walkGapSteps(player, gapStartX, gapStartY, travelStepX, travelStepY,
							steps, goingRight, traversal.type, true, destX, destY));
			return;
		}

		walkGapSteps(player, gapStartX, gapStartY, travelStepX, travelStepY,
				steps, goingRight, traversal.type, true, destX, destY);
	}

	private static void handleBalancingRope(Player player, Traversal traversal) {
		alignThenWalk(player, traversal);
	}

	private static void handleBalancingLedge(Player player, Traversal traversal) {
		handleAlignedGapWalk(player, traversal, traversal.walkAnimation, PLATFORM_RADIUS);
	}

	private static void alignThenWalk(Player player, Traversal traversal) {
		if (player.stopPlayerPacket) {
			return;
		}
		final int alignX = traversal.alignX >= 0 ? traversal.alignX : player.absX;
		final int alignY = traversal.alignY >= 0 ? traversal.alignY : player.absY;
		if (alignX != player.absX || alignY != player.absY) {
			player.getPlayerAssistant().movePlayer(alignX, alignY, BrimhavenAgilityArena.ARENA_PLANE);
			player.getPlayerAssistant().requestUpdates();
		}
		handleWalk(player, traversal);
	}

	/** Fixed-step gap traversal (hand holds). */
	private static void walkGapSteps(Player player, int startX, int startY, int stepX, int stepY,
			int steps, boolean goingRight, ObstacleType finishType, boolean faceNorth,
			int destX, int destY) {
		if (steps <= 0) {
			finishTraversal(player, destX, destY, finishType);
			return;
		}
		player.turnPlayerTo(startX, startY + 1);
		player.startAnimation(handHoldsAnimationForStep(0, goingRight));
		player.getPlayerAssistant().requestUpdates();

		CycleEventHandler.getSingleton().addEvent(player, new CycleEvent() {
			private int tick = 0;
			private int cx = startX;
			private int cy = startY;
			private boolean completed = false;

			@Override
			public void execute(CycleEventContainer container) {
				if (player.disconnected) {
					container.stop();
					return;
				}
				tick++;
				cx += stepX;
				cy += stepY;
				player.getPlayerAssistant().movePlayer(cx, cy, BrimhavenAgilityArena.ARENA_PLANE);
				if (faceNorth) {
					player.turnPlayerTo(cx, cy + 1);
				}
				player.startAnimation(handHoldsAnimationForStep(tick, goingRight));
				player.getPlayerAssistant().requestUpdates();
				if (tick >= steps) {
					completed = true;
					container.stop();
					CycleEventHandler.getSingleton().addEvent(player, new CycleEvent() {
						@Override
						public void execute(CycleEventContainer container) {
							if (player.disconnected) {
								container.stop();
								return;
							}
							finishTraversal(player, destX, destY, finishType);
							container.stop();
						}

						@Override
						public void stop() {
						}
					}, HAND_HOLDS_STEP_TICKS);
				}
			}

			@Override
			public void stop() {
				if (!completed && player.stopPlayerPacket) {
					unlockTraversal(player);
				}
			}
		}, HAND_HOLDS_STEP_TICKS);
	}

	/**
	 * Gap obstacles (monkey bars, hand holds) sit over void — pathfinding cannot
	 * cross them, so move one tile per tick along the obstacle line.
	 */
	private static void handleAlignedGapWalk(Player player, Traversal traversal,
			int walkAnimation, int maxPerpOffset) {
		if (player.stopPlayerPacket) {
			return;
		}
		final int destX = traversal.destX;
		final int destY = traversal.destY;
		final int lineX = traversal.alignX >= 0 ? traversal.alignX : player.absX;
		final int lineY = traversal.alignY >= 0 ? traversal.alignY : player.absY;
		final int perpDx = lineX - player.absX;
		final int perpDy = lineY - player.absY;
		if (Math.abs(perpDx) + Math.abs(perpDy) > maxPerpOffset) {
			return;
		}

		player.stopPlayerPacket = true;
		player.getPlayerAction().setAction(true);
		player.getPlayerAction().canWalk(false);
		player.isRunning2 = false;

		if (perpDx != 0 || perpDy != 0) {
			final int alignDestX = player.absX + perpDx;
			final int alignDestY = player.absY + perpDy;
			walkLineTicks(player, player.absX, player.absY, alignDestX, alignDestY, walkAnimation,
					null, () -> walkLineTicks(player, alignDestX, alignDestY, destX, destY,
							walkAnimation, traversal.type, null));
		} else {
			walkLineTicks(player, player.absX, player.absY, destX, destY, walkAnimation,
					traversal.type, null);
		}
	}

	private static void walkLineTicks(Player player, int startX, int startY, int destX, int destY,
			int walkAnimation, ObstacleType finishType, Runnable onComplete) {
		int steps = Math.abs(destX - startX) + Math.abs(destY - startY);
		if (steps == 0) {
			if (finishType != null) {
				finishTraversal(player, destX, destY, finishType);
			} else if (onComplete != null) {
				onComplete.run();
			}
			return;
		}
		final int stepX = Integer.signum(destX - startX);
		final int stepY = Integer.signum(destY - startY);
		player.startAnimation(walkAnimation);
		player.getPlayerAssistant().requestUpdates();

		CycleEventHandler.getSingleton().addEvent(player, new CycleEvent() {
			private int tick = 0;
			private int cx = startX;
			private int cy = startY;
			private boolean completed = false;

			@Override
			public void execute(CycleEventContainer container) {
				if (player.disconnected) {
					container.stop();
					return;
				}
				tick++;
				cx += stepX;
				cy += stepY;
				player.getPlayerAssistant().movePlayer(cx, cy, BrimhavenAgilityArena.ARENA_PLANE);
				player.startAnimation(walkAnimation);
				player.getPlayerAssistant().requestUpdates();
				if (tick >= steps) {
					completed = true;
					if (finishType != null) {
						finishTraversal(player, destX, destY, finishType);
					} else if (onComplete != null) {
						onComplete.run();
					}
					container.stop();
				}
			}

			@Override
			public void stop() {
				if (!completed && player.stopPlayerPacket) {
					unlockTraversal(player);
				}
			}
		}, 1);
	}

	private static void handleRopeSwing(Player player, Traversal traversal, int objectX, int objectY) {
		if (player.stopPlayerPacket) {
			return;
		}
		if (isGrid1SouthRopeStart(player) && traversal.destY < player.absY) {
			handleChainedRopeSwing(player, traversal, objectX, objectY);
			return;
		}
		final int destX = traversal.destX;
		final int destY = traversal.destY;
		final int startX = player.absX;
		final int startY = player.absY;
		final int deltaX = destX - startX;
		final int deltaY = destY - startY;

		player.stopPlayerPacket = true;
		player.getPlayerAction().setAction(true);
		player.getPlayerAction().canWalk(false);
		player.isRunning2 = false;
		player.turnPlayerTo(objectX, objectY);
		player.startAnimation(ROPE_SWING_ANIMATION);
		player.getPlayerAssistant().requestUpdates();

		CycleEventHandler.getSingleton().addEvent(player, new CycleEvent() {
			private int tick;

			@Override
			public void execute(CycleEventContainer container) {
				if (player.disconnected) {
					container.stop();
					return;
				}
				tick++;
				if (tick >= ROPE_SWING_DURATION_TICKS) {
					finishTraversal(player, destX, destY, traversal.type);
					container.stop();
					return;
				}
				int progressX = startX + (deltaX * tick) / ROPE_SWING_DURATION_TICKS;
				int progressY = startY + (deltaY * tick) / ROPE_SWING_DURATION_TICKS;
				player.getPlayerAssistant().movePlayer(progressX, progressY,
						BrimhavenAgilityArena.ARENA_PLANE);
				player.startAnimation(ROPE_SWING_ANIMATION);
				player.getPlayerAssistant().requestUpdates();
			}

			@Override
			public void stop() {
			}
		}, 1);
	}

	/**
	 * Grid 1 south rope: jump → swing (751) → jump, only from {@code 2806,9587}.
	 */
	private static void handleChainedRopeSwing(Player player, Traversal traversal,
			int objectX, int objectY) {
		final int destX = traversal.destX;
		final int destY = traversal.destY;
		final int startX = player.absX;
		final int startY = player.absY;
		final int deltaX = destX - startX;
		final int deltaY = destY - startY;

		player.stopPlayerPacket = true;
		player.getPlayerAction().setAction(true);
		player.getPlayerAction().canWalk(false);
		player.isRunning2 = false;
		player.turnPlayerTo(objectX, objectY);
		player.startAnimation(ROPE_JUMP_ANIMATION);
		player.getPlayerAssistant().requestUpdates();

		CycleEventHandler.getSingleton().addEvent(player, new CycleEvent() {
			@Override
			public void execute(CycleEventContainer container) {
				if (player.disconnected) {
					container.stop();
					return;
				}
				player.startAnimation(ROPE_SWING_ANIMATION);
				player.getPlayerAssistant().requestUpdates();
				CycleEventHandler.getSingleton().addEvent(player, new CycleEvent() {
					private int tick;

					@Override
					public void execute(CycleEventContainer container) {
						if (player.disconnected) {
							container.stop();
							return;
						}
						tick++;
						if (tick >= ROPE_SWING_DURATION_TICKS) {
							container.stop();
							return;
						}
						int progressX = startX + (deltaX * tick) / ROPE_SWING_DURATION_TICKS;
						int progressY = startY + (deltaY * tick) / ROPE_SWING_DURATION_TICKS;
						player.getPlayerAssistant().movePlayer(progressX, progressY,
								BrimhavenAgilityArena.ARENA_PLANE);
						player.startAnimation(ROPE_SWING_ANIMATION);
						player.getPlayerAssistant().requestUpdates();
					}

					@Override
					public void stop() {
					}
				}, 1);
				container.stop();
			}

			@Override
			public void stop() {
			}
		}, ROPE_JUMP_TICKS);

		int landTick = ROPE_JUMP_TICKS + ROPE_SWING_DURATION_TICKS;
		CycleEventHandler.getSingleton().addEvent(player, new CycleEvent() {
			@Override
			public void execute(CycleEventContainer container) {
				if (player.disconnected) {
					container.stop();
					return;
				}
				player.getPlayerAssistant().movePlayer(destX, destY, BrimhavenAgilityArena.ARENA_PLANE);
				player.startAnimation(ROPE_JUMP_ANIMATION);
				player.getPlayerAssistant().requestUpdates();
				container.stop();
			}

			@Override
			public void stop() {
			}
		}, landTick);

		CycleEventHandler.getSingleton().addEvent(player, new CycleEvent() {
			@Override
			public void execute(CycleEventContainer container) {
				if (player.disconnected) {
					container.stop();
					return;
				}
				finishTraversal(player, destX, destY, traversal.type);
				container.stop();
			}

			@Override
			public void stop() {
			}
		}, landTick + ROPE_JUMP_TICKS);
	}

	private static boolean isGrid1SouthRopeCrossing(Crossing crossing) {
		return crossing.ax == 2805 && crossing.ay == 9579
				&& crossing.bx == 2805 && crossing.by == 9590;
	}

	private static boolean isGrid1SouthRopeStart(Player player) {
		return player.absX == GRID1_SOUTH_ROPE_START_X
				&& player.absY == GRID1_SOUTH_ROPE_START_Y;
	}

	private static void handlePillarHop(Player player, Traversal traversal) {
		if (player.stopPlayerPacket) {
			return;
		}
		final int destX = traversal.destX;
		final int destY = traversal.destY;
		if (destX == player.absX && destY == player.absY) {
			player.getPlayerAction().setAction(false);
			player.getPlayerAction().canWalk(true);
			player.stopPlayerPacket = false;
			return;
		}

		player.stopPlayerPacket = true;
		player.getPlayerAction().setAction(true);
		player.getPlayerAction().canWalk(false);
		player.isRunning2 = false;
		player.startAnimation(PILLAR_JUMP_ANIMATION);
		player.getPlayerAssistant().requestUpdates();

		CycleEventHandler.getSingleton().addEvent(player, new CycleEvent() {
			@Override
			public void execute(CycleEventContainer container) {
				if (player.disconnected) {
					container.stop();
					return;
				}
				finishTraversal(player, destX, destY, traversal.type);
				container.stop();
			}

			@Override
			public void stop() {
			}
		}, PILLAR_JUMP_TICKS);
	}

	private static void handleLowWall(Player player, Traversal traversal, int objectX, int objectY) {
		if (player.stopPlayerPacket) {
			return;
		}
		int approachX = player.absX;
		int approachY = player.absY;
		if (traversal.alignX >= 0 && traversal.alignY >= 0) {
			approachX = traversal.alignX;
			approachY = traversal.alignY;
		} else if (Math.abs(player.absX - objectX) + Math.abs(player.absY - objectY) > 1) {
			approachX += Integer.signum(objectX - player.absX);
			approachY += Integer.signum(objectY - player.absY);
		}
		final boolean needsApproach = approachX != player.absX || approachY != player.absY;
		final int climbFromX = approachX;
		final int climbFromY = approachY;
		final int hopX = climbFromX + traversal.travelDirX * LOW_WALL_HOP_DISTANCE;
		final int hopY = climbFromY + traversal.travelDirY * LOW_WALL_HOP_DISTANCE;
		final int finalX = hopX + traversal.travelDirX;
		final int finalY = hopY + traversal.travelDirY;

		player.stopPlayerPacket = true;
		player.getPlayerAction().setAction(true);
		player.getPlayerAction().canWalk(false);
		player.isRunning2 = false;

		if (needsApproach) {
			final int approachDx = approachX - player.absX;
			final int approachDy = approachY - player.absY;
			player.getPlayerAssistant().walkTo2(approachDx, approachDy);
			player.getPlayerAssistant().requestUpdates();
			int approachTicks = walkDelayTicks(approachDx, approachDy);
			CycleEventHandler.getSingleton().addEvent(player, new CycleEvent() {
				@Override
				public void execute(CycleEventContainer container) {
					if (player.disconnected) {
						container.stop();
						return;
					}
					startLowWallClimb(player, traversal, objectX, objectY, hopX, hopY, finalX, finalY);
					container.stop();
				}

				@Override
				public void stop() {
				}
			}, Math.max(2, approachTicks));
		} else {
			startLowWallClimb(player, traversal, objectX, objectY, hopX, hopY, finalX, finalY);
		}
	}

	private static void startLowWallClimb(Player player, Traversal traversal, int objectX, int objectY,
			int hopX, int hopY, int finalX, int finalY) {
		player.turnPlayerTo(objectX, objectY);
		player.startAnimation(LOW_WALL_CLIMB_ANIMATION);
		player.getPlayerAssistant().requestUpdates();

		CycleEventHandler.getSingleton().addEvent(player, new CycleEvent() {
			@Override
			public void execute(CycleEventContainer container) {
				if (player.disconnected) {
					container.stop();
					return;
				}
				player.getPlayerAssistant().movePlayer(hopX, hopY, BrimhavenAgilityArena.ARENA_PLANE);
				player.getPlayerAssistant().requestUpdates();
				container.stop();
			}

			@Override
			public void stop() {
			}
		}, LOW_WALL_CLIMB_TICKS);

		CycleEventHandler.getSingleton().addEvent(player, new CycleEvent() {
			@Override
			public void execute(CycleEventContainer container) {
				if (player.disconnected) {
					container.stop();
					return;
				}
				finishTraversal(player, finalX, finalY, traversal.type);
				container.stop();
			}

			@Override
			public void stop() {
			}
		}, LOW_WALL_CLIMB_TICKS + 1);
	}

	private static void finishTraversal(Player player, int destX, int destY, ObstacleType type) {
		player.getPlayerAssistant().movePlayer(destX, destY, BrimhavenAgilityArena.ARENA_PLANE);
		unlockTraversal(player);
		player.getPlayerAssistant().addSkillXP(type.xp, Constants.AGILITY);
	}

	private static void unlockTraversal(Player player) {
		player.getPlayerAssistant().resetAnimation();
		player.getPlayerAction().setAction(false);
		player.getPlayerAction().canWalk(true);
		player.isRunning2 = true;
		player.stopPlayerPacket = false;
		player.getPlayerAssistant().requestUpdates();
	}

	private static Traversal resolveTraversal(Player player, int objectId, int objectX, int objectY) {
		ObstacleType type = objectIdToType(objectId);
		if (type == null) {
			return null;
		}
		Crossing crossing = findCrossing(type, objectX, objectY);
		if (crossing == null) {
			return null;
		}
		int dirX = Integer.signum(crossing.bx - crossing.ax);
		int dirY = Integer.signum(crossing.by - crossing.ay);
		boolean onA = isOnPlatform(player, crossing.ax, crossing.ay);
		boolean onB = isOnPlatform(player, crossing.bx, crossing.by);
		boolean nearA = canStartFromSideA(player, crossing, dirX, dirY);
		boolean nearB = canStartFromSideB(player, crossing, dirX, dirY);
		boolean onPillar = isOnPillar(player, crossing);
		if (type.movementStyle == MovementStyle.PILLAR_HOP) {
			if (!onA && !onB && !onPillar) {
				return null;
			}
		} else if (type.movementStyle == MovementStyle.LOW_WALL) {
			if (!canUseLowWall(player, objectX, objectY, crossing)) {
				return null;
			}
		} else if (type.movementStyle == MovementStyle.ROPE_SWING
				|| type.movementStyle == MovementStyle.GAP_WALK) {
			if (isGrid1SouthRopeCrossing(crossing)) {
				if (isGrid1SouthRopeStart(player)) {
					nearA = false;
					nearB = true;
				} else if (onB && !onA) {
					return null;
				}
			} else if (!nearA && !nearB) {
				return null;
			}
			if (type.movementStyle == MovementStyle.GAP_WALK
					&& !isAlignedToGapLine(player, crossing, type)) {
				return null;
			}
		} else if (type == ObstacleType.HAND_HOLDS) {
			if (!canUseHandHolds(player, crossing)) {
				return null;
			}
			int distA = Math.abs(player.absX - crossing.ax) + Math.abs(player.absY - crossing.ay);
			int distB = Math.abs(player.absX - crossing.bx) + Math.abs(player.absY - crossing.by);
			nearA = distA <= distB;
			nearB = distB < distA;
		} else if (!onA && !onB) {
			return null;
		}
		int destX;
		int destY;
		int walkAnimation = type.animation;

		if (type.movementStyle == MovementStyle.PILLAR_HOP) {
			int[] pillarDest = resolvePillarHop(player, crossing, objectX, objectY, onA, onB, dirX, dirY);
			if (pillarDest == null) {
				return null;
			}
			destX = pillarDest[0];
			destY = pillarDest[1];
		} else if (type.movementStyle == MovementStyle.LOW_WALL) {
			if (!canUseLowWall(player, objectX, objectY, crossing)) {
				return null;
			}
			int travelDirX;
			int travelDirY;
			if (onA && !onB) {
				travelDirX = dirX;
				travelDirY = dirY;
			} else if (onB && !onA) {
				travelDirX = -dirX;
				travelDirY = -dirY;
			} else {
				int distA = Math.abs(player.absX - crossing.ax) + Math.abs(player.absY - crossing.ay);
				int distB = Math.abs(player.absX - crossing.bx) + Math.abs(player.absY - crossing.by);
				if (distA <= distB) {
					travelDirX = dirX;
					travelDirY = dirY;
				} else {
					travelDirX = -dirX;
					travelDirY = -dirY;
				}
			}
			int approachX = -1;
			int approachY = -1;
			if (onA && !onB) {
				if (dirY == 0) {
					approachX = crossing.ax + dirX * (PLATFORM_RADIUS + 1);
					approachY = player.absY;
				} else {
					approachX = player.absX;
					approachY = crossing.ay + dirY * (PLATFORM_RADIUS + 1);
				}
			} else if (onB && !onA) {
				if (dirY == 0) {
					approachX = crossing.bx - dirX * (PLATFORM_RADIUS + 1);
					approachY = player.absY;
				} else {
					approachX = player.absX;
					approachY = crossing.by - dirY * (PLATFORM_RADIUS + 1);
				}
			}
			return new Traversal(type, 0, 0, walkAnimation, travelDirX, travelDirY, approachX, approachY);
		} else {
			if (nearA && !nearB) {
				destX = crossing.bx - dirX * PLATFORM_RADIUS;
				destY = crossing.by - dirY * PLATFORM_RADIUS;
			} else if (nearB && !nearA) {
				destX = crossing.ax + dirX * PLATFORM_RADIUS;
				destY = crossing.ay + dirY * PLATFORM_RADIUS;
			} else {
				int distA = Math.abs(player.absX - crossing.ax) + Math.abs(player.absY - crossing.ay);
				int distB = Math.abs(player.absX - crossing.bx) + Math.abs(player.absY - crossing.by);
				if (distA <= distB) {
					destX = crossing.bx - dirX * PLATFORM_RADIUS;
					destY = crossing.by - dirY * PLATFORM_RADIUS;
				} else {
					destX = crossing.ax + dirX * PLATFORM_RADIUS;
					destY = crossing.ay + dirY * PLATFORM_RADIUS;
				}
			}
			if (type == ObstacleType.ROPE_SWING) {
				if (dirY == 0) {
					destY = player.absY;
				} else if (dirX == 0) {
					destX = player.absX;
				}
			} else if (type == ObstacleType.PLANK || type == ObstacleType.LOG_BALANCE
					|| type == ObstacleType.BALANCING_LEDGE) {
				if (dirY == 0) {
					destY = player.absY;
				} else if (dirX == 0) {
					destX = player.absX;
				}
			} else if (type == ObstacleType.BALANCING_ROPE) {
				if (crossing.walkLineX >= 0) {
					destX = crossing.walkLineX;
				}
				if (crossing.walkLineY >= 0) {
					destY = crossing.walkLineY;
				}
			} else {
				if (crossing.playerUsesWalkLine && crossing.walkLineX >= 0) {
					destX = crossing.walkLineX;
				} else if (dirX == 0) {
					destX = crossing.ax;
				}
				if (crossing.playerUsesWalkLine && crossing.walkLineY >= 0) {
					destY = crossing.walkLineY;
				} else if (dirY == 0) {
					destY = crossing.ay;
				}
			}
			if (type == ObstacleType.BALANCING_LEDGE) {
				walkAnimation = ledgeWalkAnimation(player.absX, player.absY, destX, destY);
			}
			if (type == ObstacleType.HAND_HOLDS) {
				walkAnimation = handHoldsAnimation(player.absX, player.absY, destX, destY);
			}
			if (type == ObstacleType.BALANCING_ROPE) {
				int alignX = player.absX;
				int alignY = player.absY;
				if (crossing.walkLineX >= 0) {
					alignX = crossing.walkLineX;
				}
				if (crossing.walkLineY >= 0) {
					alignY = crossing.walkLineY;
				}
				return new Traversal(type, destX, destY, walkAnimation, 0, 0, alignX, alignY);
			}
			if (type == ObstacleType.BALANCING_LEDGE) {
				return new Traversal(type, destX, destY, walkAnimation, 0, 0,
						player.absX, player.absY);
			}
			if (type == ObstacleType.HAND_HOLDS) {
				boolean leavingFromA;
				if (nearA && !nearB) {
					leavingFromA = true;
				} else if (nearB && !nearA) {
					leavingFromA = false;
				} else {
					int distA = Math.abs(player.absX - crossing.ax) + Math.abs(player.absY - crossing.ay);
					int distB = Math.abs(player.absX - crossing.bx) + Math.abs(player.absY - crossing.by);
					leavingFromA = distA <= distB;
				}
				int gapStartX;
				int gapStartY;
				if (leavingFromA) {
					gapStartX = crossing.ax + dirX * PLATFORM_RADIUS;
					gapStartY = crossing.ay + dirY * PLATFORM_RADIUS;
				} else {
					gapStartX = crossing.bx - dirX * PLATFORM_RADIUS;
					gapStartY = crossing.by - dirY * PLATFORM_RADIUS;
				}
				if (crossing.walkLineY >= 0) {
					gapStartY = crossing.walkLineY;
				}
				if (crossing.walkLineX >= 0) {
					gapStartX = crossing.walkLineX;
				}
				return new Traversal(type, destX, destY, walkAnimation, 0, 0, -1, -1,
						gapStartX, gapStartY);
			}
			if (type.movementStyle == MovementStyle.GAP_WALK) {
				int alignX = player.absX;
				int alignY = player.absY;
				if (crossing.playerUsesWalkLine && crossing.walkLineY >= 0) {
					alignY = crossing.walkLineY;
				} else if (dirY == 0) {
					alignY = crossing.ay;
				}
				if (crossing.playerUsesWalkLine && crossing.walkLineX >= 0) {
					alignX = crossing.walkLineX;
				} else if (dirX == 0) {
					alignX = crossing.ax;
				}
				return new Traversal(type, destX, destY, walkAnimation, 0, 0, alignX, alignY);
			}
		}

		return new Traversal(type, destX, destY, walkAnimation);
	}

	private static boolean canUseLowWall(Player player, int objectX, int objectY, Crossing crossing) {
		if (Math.abs(player.absX - objectX) > 2 || Math.abs(player.absY - objectY) > 2) {
			return false;
		}
		return isInCorridor(player, crossing)
				|| isOnPlatform(player, crossing.ax, crossing.ay)
				|| isOnPlatform(player, crossing.bx, crossing.by);
	}

	private static boolean canStartFromSideA(Player player, Crossing crossing, int dirX, int dirY) {
		return isOnPlatform(player, crossing.ax, crossing.ay)
				|| isOnGapApproach(player, crossing.ax, crossing.ay, dirX, dirY);
	}

	private static boolean canStartFromSideB(Player player, Crossing crossing, int dirX, int dirY) {
		return isOnPlatform(player, crossing.bx, crossing.by)
				|| isOnGapApproach(player, crossing.bx, crossing.by, -dirX, -dirY);
	}

	/**
	 * One tile into the gap past the platform edge (e.g. 2764,9569 west of 2761,9568).
	 */
	private static boolean isOnGapApproach(Player player, int centerX, int centerY, int dirX, int dirY) {
		if (dirX == 0 && dirY == 0) {
			return false;
		}
		int approachX = centerX + dirX * (PLATFORM_RADIUS + 1);
		int approachY = centerY + dirY * (PLATFORM_RADIUS + 1);
		if (dirY == 0) {
			return player.absX == approachX
					&& Math.abs(player.absY - centerY) <= PLATFORM_RADIUS;
		}
		if (dirX == 0) {
			return player.absY == approachY
					&& Math.abs(player.absX - centerX) <= PLATFORM_RADIUS;
		}
		return false;
	}

	private static boolean canUseHandHolds(Player player, Crossing crossing) {
		if (isOnPlatform(player, crossing.ax, crossing.ay)
				|| isOnPlatform(player, crossing.bx, crossing.by)) {
			if (crossing.walkLineY >= 0) {
				return Math.abs(player.absY - crossing.walkLineY) <= 3;
			}
			if (crossing.walkLineX >= 0) {
				return Math.abs(player.absX - crossing.walkLineX) <= 3;
			}
			return true;
		}
		int minX = Math.min(crossing.ax, crossing.bx) - PLATFORM_RADIUS;
		int maxX = Math.max(crossing.ax, crossing.bx) + PLATFORM_RADIUS;
		int minY = Math.min(crossing.ay, crossing.by) - PLATFORM_RADIUS;
		int maxY = Math.max(crossing.ay, crossing.by) + PLATFORM_RADIUS;
		if (crossing.walkLineY >= 0) {
			return player.absX >= minX && player.absX <= maxX
					&& Math.abs(player.absY - crossing.walkLineY) <= 3;
		}
		if (crossing.walkLineX >= 0) {
			return player.absY >= minY && player.absY <= maxY
					&& Math.abs(player.absX - crossing.walkLineX) <= 3;
		}
		return false;
	}

	private static int ledgeWalkAnimation(int startX, int startY, int destX, int destY) {
		// 2770→2763 (west): 756. 2763→2770 (east): 754.
		if (destX > startX || destY > startY) {
			return LEDGE_WALK_REVERSE_ANIMATION;
		}
		return LEDGE_WALK_ANIMATION;
	}

	private static int handHoldsAnimation(int startX, int startY, int destX, int destY) {
		return handHoldsAnimationForStep(0, destX > startX || destY > startY);
	}

	private static int handHoldsAnimationForStep(int step, boolean goingRight) {
		if (goingRight) {
			return step % 2 == 0 ? HAND_HOLDS_RIGHT_ANIMATION : HAND_HOLDS_RIGHT_ALT_ANIMATION;
		}
		return step % 2 == 0 ? HAND_HOLDS_LEFT_ANIMATION : HAND_HOLDS_LEFT_ALT_ANIMATION;
	}

	private static int[] resolvePillarHop(Player player, Crossing crossing,
			int objectX, int objectY, boolean onA, boolean onB, int dirX, int dirY) {
		if (crossing.pillars == null || crossing.pillars.length == 0) {
			return null;
		}
		boolean horizontal = dirY == 0;
		int travelSign = horizontal ? dirX : dirY;
		int playerPos = horizontal ? player.absX : player.absY;
		int objectPos = horizontal ? objectX : objectY;
		int edgeA = horizontal
				? crossing.ax + dirX * PLATFORM_RADIUS
				: crossing.ay + dirY * PLATFORM_RADIUS;
		int edgeB = horizontal
				? crossing.bx - dirX * PLATFORM_RADIUS
				: crossing.by - dirY * PLATFORM_RADIUS;

		int destCoord;
		if (onA && !onB) {
			destCoord = nextPillarToward(crossing.pillars, playerPos, travelSign, edgeB);
		} else if (onB && !onA) {
			destCoord = nextPillarToward(crossing.pillars, playerPos, -travelSign, edgeA);
		} else if (isOnPillar(player, crossing)) {
			int hopSign = Integer.signum(objectPos - playerPos);
			if (hopSign == 0) {
				int distA = Math.abs(player.absX - crossing.ax) + Math.abs(player.absY - crossing.ay);
				int distB = Math.abs(player.absX - crossing.bx) + Math.abs(player.absY - crossing.by);
				hopSign = distB <= distA ? travelSign : -travelSign;
			}
			destCoord = nextPillarToward(crossing.pillars, playerPos, hopSign,
					hopSign > 0 ? edgeB : edgeA);
		} else {
			int distA = Math.abs(player.absX - crossing.ax) + Math.abs(player.absY - crossing.ay);
			int distB = Math.abs(player.absX - crossing.bx) + Math.abs(player.absY - crossing.by);
			if (distA <= distB) {
				destCoord = nextPillarToward(crossing.pillars, playerPos, travelSign, edgeB);
			} else {
				destCoord = nextPillarToward(crossing.pillars, playerPos, -travelSign, edgeA);
			}
		}
		if (destCoord == Integer.MIN_VALUE) {
			return null;
		}
		if (horizontal) {
			return new int[] { destCoord, crossing.ay };
		}
		return new int[] { crossing.ax, destCoord };
	}

	private static int nextPillarToward(int[] pillars, int playerPos, int sign, int platformEdge) {
		if (sign > 0) {
			for (int pillar : pillars) {
				if (pillar > playerPos) {
					return pillar;
				}
			}
			return platformEdge;
		}
		if (sign < 0) {
			for (int i = pillars.length - 1; i >= 0; i--) {
				if (pillars[i] < playerPos) {
					return pillars[i];
				}
			}
			return platformEdge;
		}
		return Integer.MIN_VALUE;
	}

	private static boolean isAlignedToGapLine(Player player, Crossing crossing, ObstacleType type) {
		if (type == ObstacleType.MONKEY_BARS) {
			if (crossing.ay == crossing.by) {
				return Math.abs(player.absY - crossing.ay) <= PLATFORM_RADIUS;
			}
			if (crossing.ax == crossing.bx) {
				return Math.abs(player.absX - crossing.ax) <= PLATFORM_RADIUS;
			}
			return true;
		}
		if (crossing.walkLineY >= 0) {
			return Math.abs(player.absY - crossing.walkLineY) <= 3;
		}
		if (crossing.walkLineX >= 0) {
			return Math.abs(player.absX - crossing.walkLineX) <= 3;
		}
		return true;
	}

	private static Crossing findCrossing(ObstacleType type, int objectX, int objectY) {
		for (Crossing crossing : CROSSINGS) {
			if (crossing.type != type) {
				continue;
			}
			if (isObjectInCrossingBounds(crossing, objectX, objectY)) {
				return crossing;
			}
		}
		return null;
	}

	private static boolean isObjectInCrossingBounds(Crossing crossing, int objectX, int objectY) {
		int minX = Math.min(crossing.ax, crossing.bx) - PLATFORM_RADIUS;
		int maxX = Math.max(crossing.ax, crossing.bx) + PLATFORM_RADIUS;
		int minY = Math.min(crossing.ay, crossing.by) - PLATFORM_RADIUS;
		int maxY = Math.max(crossing.ay, crossing.by) + PLATFORM_RADIUS;
		return objectX >= minX && objectX <= maxX
				&& objectY >= minY && objectY <= maxY;
	}

	private static boolean isOnPlatform(Player player, int centerX, int centerY) {
		return Math.abs(player.absX - centerX) <= PLATFORM_RADIUS
				&& Math.abs(player.absY - centerY) <= PLATFORM_RADIUS;
	}

	private static boolean isOnPillar(Player player, Crossing crossing) {
		if (crossing.pillars == null) {
			return false;
		}
		boolean horizontal = crossing.ay == crossing.by;
		for (int coord : crossing.pillars) {
			if (horizontal && player.absY == crossing.ay && player.absX == coord) {
				return true;
			}
			if (!horizontal && player.absX == crossing.ax && player.absY == coord) {
				return true;
			}
		}
		return false;
	}

	private static boolean isInCorridor(Player player, Crossing crossing) {
		int dirX = Integer.signum(crossing.bx - crossing.ax);
		int dirY = Integer.signum(crossing.by - crossing.ay);
		if (dirY == 0) {
			int minX = Math.min(crossing.ax, crossing.bx) + PLATFORM_RADIUS + 1;
			int maxX = Math.max(crossing.ax, crossing.bx) - PLATFORM_RADIUS - 1;
			int lineY = crossing.walkLineY >= 0 ? crossing.walkLineY : crossing.ay;
			return player.absY == lineY && player.absX >= minX && player.absX <= maxX;
		}
		if (dirX == 0) {
			int minY = Math.min(crossing.ay, crossing.by) + PLATFORM_RADIUS + 1;
			int maxY = Math.max(crossing.ay, crossing.by) - PLATFORM_RADIUS - 1;
			int lineX = crossing.walkLineX >= 0 ? crossing.walkLineX : crossing.ax;
			return player.absX == lineX && player.absY >= minY && player.absY <= maxY;
		}
		return false;
	}

	private static ObstacleType objectIdToType(int objectId) {
		switch (objectId) {
			case StaticObjectList.PLANK_3570:
			case StaticObjectList.PLANK_3571:
			case StaticObjectList.PLANK_3572:
			case StaticObjectList.PLANK_3573:
			case StaticObjectList.PLANK_3574:
			case StaticObjectList.PLANK_3575:
			case StaticObjectList.PLANK_3576:
			case StaticObjectList.PLANK_3577:
				return ObstacleType.PLANK;
			case StaticObjectList.ROPE_SWING_3566:
				return ObstacleType.ROPE_SWING;
			case StaticObjectList.BALANCING_ROPE_3551:
			case StaticObjectList.BALANCING_ROPE_3552:
				return ObstacleType.BALANCING_ROPE;
			case StaticObjectList.LOG_BALANCE_3553:
			case StaticObjectList.LOG_BALANCE_3554:
			case StaticObjectList.LOG_BALANCE_3555:
			case StaticObjectList.LOG_BALANCE_3556:
			case StaticObjectList.LOG_BALANCE_3557:
			case StaticObjectList.LOG_BALANCE_3558:
				return ObstacleType.LOG_BALANCE;
			case StaticObjectList.BALANCING_LEDGE_3559:
			case StaticObjectList.BALANCING_LEDGE_3560:
			case StaticObjectList.BALANCING_LEDGE_3561:
			case StaticObjectList.BALANCING_LEDGE_3562:
				return ObstacleType.BALANCING_LEDGE;
			case StaticObjectList.MONKEY_BARS:
			case StaticObjectList.MONKEY_BARS_3564:
				return ObstacleType.MONKEY_BARS;
			case StaticObjectList.LOW_WALL:
				return ObstacleType.LOW_WALL;
			case StaticObjectList.PILLAR_3578:
			case StaticObjectList.PILLAR_3579:
				return ObstacleType.PILLAR;
			case StaticObjectList.HAND_HOLDS_3583:
			case StaticObjectList.HAND_HOLDS_3584:
				return ObstacleType.HAND_HOLDS;
			default:
				return null;
		}
	}
}

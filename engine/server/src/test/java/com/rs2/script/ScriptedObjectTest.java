package com.rs2.script;

import static org.junit.Assert.assertEquals;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.junit.Test;

import com.rs2.game.objects.Objects;

public class ScriptedObjectTest {

	@Test
	public void exposesResolvedObjectMetadataThroughGraalBoundary() {
		Objects clicked = new Objects(2213, 3208, 3220, 2, 3, 10, 0);
		ScriptedObject target = new ScriptedObject(clicked);

		try (Context context = Context.newBuilder("js")
				.allowHostAccess(HostAccess.EXPLICIT)
				.build()) {
			context.getBindings("js").putMember("target", target);
			Value metadata = context.eval("js",
					"({ id: target.getId(), x: target.getX(), y: target.getY(),"
							+ " plane: target.getPlane(),"
							+ " positionPlane: target.getPosition().plane,"
							+ " type: target.getType(),"
							+ " rotation: target.getRotation() })");

			assertEquals(2213, metadata.getMember("id").asInt());
			assertEquals(3208, metadata.getMember("x").asInt());
			assertEquals(3220, metadata.getMember("y").asInt());
			assertEquals(2, metadata.getMember("plane").asInt());
			assertEquals(2, metadata.getMember("positionPlane").asInt());
			assertEquals(10, metadata.getMember("type").asInt());
			assertEquals(3, metadata.getMember("rotation").asInt());
		}
	}
}

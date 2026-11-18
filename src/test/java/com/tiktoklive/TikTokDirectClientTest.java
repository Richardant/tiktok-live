package com.tiktoklive;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class TikTokDirectClientTest
{
	private static byte[] resource(String name) throws IOException
	{
		try (InputStream in = TikTokDirectClientTest.class.getResourceAsStream(name))
		{
			assertNotNull(name, in);
			return in.readAllBytes();
		}
	}

	@Test
	public void decodesPushFrame() throws IOException
	{
		List<JsonObject> events = new ArrayList<>();
		TikTokDirectClient client = new TikTokDirectClient(null, null, null, events::add, "test", "");

		client.handleFrame(null, resource("push_frame.bin"));

		assertEquals(7, events.size());

		JsonObject chat = events.get(0);
		assertEquals("chat", chat.get("event").getAsString());
		EventData data = new EventData(chat.getAsJsonObject("data"));
		assertEquals("hola streamer", data.getString("comment"));
		assertEquals("Ana ✨", data.getString("nickname"));
		assertEquals("ana_01", data.getString("uniqueId"));

		JsonObject gift = events.get(1);
		assertEquals("gift", gift.get("event").getAsString());
		data = new EventData(gift.getAsJsonObject("data"));
		assertEquals("Rose", data.getString("giftName"));
		assertEquals(1, data.getLong("diamondCount"));
		assertEquals(5, data.getLong("repeatCount"));
		assertEquals(1, data.getLong("giftType"));
		assertTrue(data.getBoolean("repeatEnd", false));
		assertEquals("bob", data.getString("uniqueId"));

		data = new EventData(events.get(2).getAsJsonObject("data"));
		assertEquals("like", events.get(2).get("event").getAsString());
		assertEquals(15, data.getLong("likeCount"));
		assertEquals(4321, data.getLong("totalLikeCount"));

		assertEquals("follow", events.get(3).get("event").getAsString());
		assertEquals("Dee", new EventData(events.get(3).getAsJsonObject("data")).getString("nickname"));

		assertEquals("member", events.get(4).get("event").getAsString());

		assertEquals("roomUser", events.get(5).get("event").getAsString());
		assertEquals(87, new EventData(events.get(5).getAsJsonObject("data")).getLong("viewerCount"));

		assertEquals("streamEnd", events.get(6).get("event").getAsString());
		assertFalse(client.isConnected());
	}

	@Test
	public void giftWithoutRepeatEndIsStreakStart() throws IOException
	{
		byte[] gift = new ProtoWriter()
			.int64(2, 5655)
			.int64(5, 1)
			.bytes(15, new ProtoWriter().int64(11, 1).int64(12, 1).string(16, "Rose").toByteArray())
			.toByteArray();
		byte[] message = new ProtoWriter()
			.string(1, "WebcastGiftMessage")
			.bytes(2, gift)
			.int64(3, 1)
			.toByteArray();
		byte[] frame = new ProtoWriter()
			.string(7, "msg")
			.bytes(8, new ProtoWriter().bytes(1, message).toByteArray())
			.toByteArray();

		List<JsonObject> events = new ArrayList<>();
		TikTokDirectClient client = new TikTokDirectClient(null, null, null, events::add, "test", "");
		client.handleFrame(null, frame);
		// the same message id again is ignored
		client.handleFrame(null, frame);

		assertEquals(1, events.size());
		assertFalse(new EventData(events.get(0).getAsJsonObject("data")).getBoolean("repeatEnd", true));
	}

	@Test
	public void buildsWebSocketUrl() throws IOException
	{
		String url = TikTokDirectClient.buildWebSocketUrl(new ProtoReader(resource("fetch_result.bin")), 42);
		assertNotNull(url);
		assertTrue(url, url.startsWith("wss://webcast.example/ws?"));
		assertTrue(url, url.contains("cursor=abc&"));
		assertTrue(url, url.contains("internal_ext=x%20y&"));
		assertTrue(url, url.contains("room_id=42&"));
		assertTrue(url, url.endsWith("&version_code=270000"));
	}

	@Test
	public void protoRoundTrip()
	{
		byte[] bytes = new ProtoWriter()
			.int64(1, 7400000000000000001L)
			.string(5, "audience")
			.int64(300, 3)
			.toByteArray();

		ProtoReader r = new ProtoReader(bytes);
		assertTrue(r.next());
		assertEquals(1, r.field());
		assertEquals(7400000000000000001L, r.varint());
		assertTrue(r.next());
		assertEquals("audience", r.string());
		assertTrue(r.next());
		assertEquals(300, r.field());
		assertEquals(3, r.varint());
		assertFalse(r.next());
	}
}

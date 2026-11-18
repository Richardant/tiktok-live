package com.tiktoklive;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * Connects straight to the TikTok LIVE WebSocket. TikTok requires the WebSocket URL to be
 * signed, which is done by the Euler Stream sign server. Messages are protobuf encoded and
 * are translated to JSON events of the form {"event": "chat", "data": {...}}.
 */
@Slf4j
class TikTokDirectClient extends WebSocketListener
{
	private static final String BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
		+ "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
	private static final HttpUrl ROOM_URL = HttpUrl.get("https://www.tiktok.com/api-live/user/room/");
	private static final HttpUrl ROOM_INFO_URL = HttpUrl.get("https://webcast.tiktok.com/webcast/room/info/");
	private static final HttpUrl SIGN_URL = HttpUrl.get("https://api.eulerstream.com/webcast/fetch");

	private static final long RETRY_OFFLINE_MS = 30_000;
	private static final long RETRY_ERROR_MS = 60_000;
	private static final long RETRY_RATE_LIMIT_MS = 5 * 60_000;
	private static final long RETRY_NOT_FOUND_MS = 5 * 60_000;
	private static final long DEFAULT_HEARTBEAT_MS = 10_000;

	private static final int CONTROL_STREAM_ENDED = 3;
	private static final int CONTROL_STREAM_SUSPENDED = 4;
	private static final int MEMBER_JOINED = 1;
	private static final int SEEN_MESSAGE_IDS = 1000;

	private enum State
	{
		IDLE,
		CONNECTING,
		CONNECTED
	}

	private final OkHttpClient okHttpClient;
	private final Gson gson;
	private final ScheduledExecutorService executor;
	private final Consumer<JsonObject> eventHandler;
	private final String uniqueId;
	private final String apiKey;

	private final Map<String, String> cookies = new LinkedHashMap<>();
	private final Set<Long> seenMessageIds = Collections.newSetFromMap(new LinkedHashMap<Long, Boolean>()
	{
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, Boolean> eldest)
		{
			return size() > SEEN_MESSAGE_IDS;
		}
	});
	private int generation;
	private WebSocket webSocket;
	private ScheduledFuture<?> heartbeatFuture;
	private long roomId;
	private long heartbeatSeq;

	private volatile State state = State.IDLE;
	private volatile String status;
	private volatile long nextAttempt;

	TikTokDirectClient(OkHttpClient okHttpClient, Gson gson, ScheduledExecutorService executor,
		Consumer<JsonObject> eventHandler, String uniqueId, String apiKey)
	{
		this.okHttpClient = okHttpClient;
		this.gson = gson;
		this.executor = executor;
		this.eventHandler = eventHandler;
		this.uniqueId = uniqueId;
		this.apiKey = apiKey;
		this.status = "connecting...";
	}

	public void tick()
	{
		if (state == State.IDLE && !Strings.isNullOrEmpty(uniqueId) && System.currentTimeMillis() >= nextAttempt)
		{
			connect();
		}
	}

	public synchronized void close()
	{
		generation++;
		closeSocket();
		state = State.IDLE;
	}

	public boolean isConnected()
	{
		return state == State.CONNECTED;
	}

	public String getStatus()
	{
		return state == State.CONNECTED ? null : status;
	}

	private void closeSocket()
	{
		if (heartbeatFuture != null)
		{
			heartbeatFuture.cancel(false);
			heartbeatFuture = null;
		}
		if (webSocket != null)
		{
			webSocket.cancel();
			webSocket = null;
		}
	}

	private synchronized void connect()
	{
		close();
		int gen = generation;
		state = State.CONNECTING;
		status = "connecting to @" + uniqueId + "...";
		cookies.clear();
		cookies.put("tt-target-idc", "useast1a");

		HttpUrl url = ROOM_URL.newBuilder()
			.addQueryParameter("aid", "1988")
			.addQueryParameter("app_name", "tiktok_web")
			.addQueryParameter("device_platform", "web_pc")
			.addQueryParameter("app_language", "en")
			.addQueryParameter("browser_language", "en-US")
			.addQueryParameter("uniqueId", uniqueId)
			.addQueryParameter("sourceType", "54")
			.build();

		log.debug("Looking up TikTok room for @{}", uniqueId);
		okHttpClient.newCall(tiktokRequest(url).build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("TikTok room lookup failed", e);
				fail(gen, "can't reach TikTok, retrying...", RETRY_ERROR_MS);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					storeCookies(gen, response);
					onRoomResponse(gen, body.string());
				}
				catch (IOException | JsonParseException | IllegalStateException e)
				{
					log.debug("Unable to read TikTok room response", e);
					fail(gen, "unexpected response from TikTok, retrying...", RETRY_ERROR_MS);
				}
			}
		});
	}

	private void onRoomResponse(int gen, String json)
	{
		JsonObject root = gson.fromJson(json, JsonObject.class);
		if ("user_not_found".equals(getString(root, "message")))
		{
			fail(gen, "@" + uniqueId + " was not found", RETRY_NOT_FOUND_MS);
			return;
		}

		String room = getString(root, "data", "user", "roomId");
		String liveStatus = getString(root, "data", "liveRoom", "status");
		if (room.isEmpty() || "0".equals(room) || "4".equals(liveStatus))
		{
			fail(gen, "@" + uniqueId + " is not live", RETRY_OFFLINE_MS);
			return;
		}

		fetchRoomStats(gen, room);
		fetchSignedUrl(gen, room);
	}

	/**
	 * TikTok only sends the like total when someone likes, so read the current
	 * totals once when connecting.
	 */
	private void fetchRoomStats(int gen, String room)
	{
		HttpUrl url = ROOM_INFO_URL.newBuilder()
			.addQueryParameter("aid", "1988")
			.addQueryParameter("app_name", "tiktok_web")
			.addQueryParameter("device_platform", "web_pc")
			.addQueryParameter("room_id", room)
			.build();

		okHttpClient.newCall(tiktokRequest(url).build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("TikTok room info request failed", e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					JsonObject root = gson.fromJson(body.string(), JsonObject.class);
					JsonObject data = new JsonObject();
					data.addProperty("totalLikeCount", getString(root, "data", "stats", "like_count"));
					data.addProperty("viewerCount", getString(root, "data", "user_count"));

					synchronized (TikTokDirectClient.this)
					{
						if (gen != generation)
						{
							return;
						}
					}
					emit("roomStats", data);
				}
				catch (IOException | JsonParseException | IllegalStateException e)
				{
					log.debug("Unable to read TikTok room info", e);
				}
			}
		});
	}

	private void fetchSignedUrl(int gen, String room)
	{
		HttpUrl url = SIGN_URL.newBuilder()
			.addQueryParameter("client", "ttlive-java")
			.addQueryParameter("room_id", room)
			.addQueryParameter("user_agent", effectiveUserAgent())
			.addQueryParameter("client_enter", "true")
			.addQueryParameter("platform", "web")
			.build();

		Request.Builder request = new Request.Builder().url(url);
		if (!Strings.isNullOrEmpty(apiKey))
		{
			request.header("X-Api-Key", apiKey);
		}

		log.debug("Requesting signed WebSocket URL for room {}", room);
		okHttpClient.newCall(request.build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Sign server request failed", e);
				fail(gen, "can't reach Euler Stream, retrying...", RETRY_ERROR_MS);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					byte[] bytes = body.bytes();
					if (response.code() == 429)
					{
						fail(gen, "Euler Stream rate limit reached, retrying in 5 min", RETRY_RATE_LIMIT_MS);
					}
					else if (response.code() == 401 || response.code() == 403)
					{
						fail(gen, "Euler Stream rejected the API key", RETRY_NOT_FOUND_MS);
					}
					else if (response.code() != 200 || bytes.length == 0)
					{
						log.debug("Sign server returned {}: {}", response.code(), new String(bytes, StandardCharsets.UTF_8));
						fail(gen, "Euler Stream error " + response.code() + ", retrying...", RETRY_ERROR_MS);
					}
					else
					{
						parseCookieHeader(gen, response.header("X-Set-TT-Cookie"));
						openWebSocket(gen, Long.parseLong(room), new ProtoReader(bytes));
					}
				}
				catch (IOException | RuntimeException e)
				{
					log.debug("Unable to read sign server response", e);
					fail(gen, "unexpected response from Euler Stream, retrying...", RETRY_ERROR_MS);
				}
			}
		});
	}

	private void openWebSocket(int gen, long room, ProtoReader fetchResult)
	{
		String uri = buildWebSocketUrl(fetchResult, room);
		if (uri == null)
		{
			fail(gen, "TikTok did not return a WebSocket server, retrying...", RETRY_ERROR_MS);
			return;
		}

		synchronized (this)
		{
			if (gen != generation)
			{
				return;
			}

			StringBuilder cookieHeader = new StringBuilder();
			cookies.forEach((k, v) -> cookieHeader.append(k).append('=').append(v).append("; "));

			Request request = new Request.Builder()
				.url(uri)
				.header("User-Agent", BROWSER_USER_AGENT)
				.header("Cookie", cookieHeader.toString().trim())
				.header("Sec-WebSocket-Protocol", "echo-protocol")
				.build();

			roomId = room;
			log.debug("Opening TikTok WebSocket for room {}", room);
			webSocket = okHttpClient.newWebSocket(request, this);
		}
	}

	/**
	 * Builds the WebSocket URL from the ProtoMessageFetchResult returned by the sign server.
	 *
	 * @return the URL, or null if the response has no push server
	 */
	static String buildWebSocketUrl(ProtoReader fetchResult, long room)
	{
		String pushServer = "";
		Map<String, String> params = new LinkedHashMap<>();

		// ProtoMessageFetchResult
		while (fetchResult.next())
		{
			switch (fetchResult.field())
			{
				case 7: // route_params
					ProtoReader entry = fetchResult.message();
					String key = "";
					String value = "";
					while (entry.next())
					{
						if (entry.field() == 1)
						{
							key = entry.string();
						}
						else if (entry.field() == 2)
						{
							value = entry.string();
						}
						else
						{
							entry.skip();
						}
					}
					if (!key.isEmpty() && !value.isEmpty())
					{
						params.put(key, value);
					}
					break;
				case 10:
					pushServer = fetchResult.string();
					break;
				default:
					fetchResult.skip();
			}
		}

		if (pushServer.isEmpty())
		{
			return null;
		}

		params.put("aid", "1988");
		params.put("app_language", "en");
		params.put("app_name", "tiktok_web");
		params.put("browser_platform", "Win32");
		params.put("browser_language", "en-US");
		params.put("browser_name", "Mozilla");
		params.put("browser_version", BROWSER_USER_AGENT.substring("Mozilla/".length()));
		params.put("browser_online", "true");
		params.put("cookie_enabled", "true");
		params.put("tz_name", "Etc/UTC");
		params.put("device_platform", "web");
		params.put("identity", "audience");
		params.put("live_id", "12");
		params.put("sup_ws_ds_opt", "1");
		params.put("update_version_code", "2.0.0");
		params.put("version_code", "180800");
		params.put("client_enter", "1");
		params.put("ws_direct", "1");
		params.put("did_rule", "3");
		params.put("webcast_language", "en");
		params.put("screen_height", "1080");
		params.put("screen_width", "1920");
		params.put("heartbeat_duration", "10000");
		params.put("resp_content_type", "protobuf");
		params.put("history_comment_count", "6");
		params.put("last_rtt", "150");
		params.put("room_id", Long.toString(room));
		params.put("compress", "gzip");

		StringBuilder uri = new StringBuilder(pushServer).append('?');
		params.forEach((k, v) -> uri.append(k).append('=').append(urlEncode(v)).append('&'));
		// TikTok expects a second version_code
		uri.append("version_code=270000");
		return uri.toString();
	}

	@Override
	public void onOpen(WebSocket ws, Response response)
	{
		long heartbeatMs = DEFAULT_HEARTBEAT_MS;
		String options = response.header("Handshake-Options");
		if (options != null)
		{
			for (String option : options.split(";"))
			{
				String[] kv = option.trim().split("=", 2);
				if (kv.length == 2 && kv[0].equals("ping-interval"))
				{
					try
					{
						heartbeatMs = (long) (Double.parseDouble(kv[1]) * 1000);
					}
					catch (NumberFormatException ignored)
					{
					}
				}
			}
		}

		synchronized (this)
		{
			if (ws != webSocket)
			{
				return;
			}

			log.debug("Connected to TikTok LIVE room {}", roomId);
			state = State.CONNECTED;
			status = null;

			byte[] enterRoom = new ProtoWriter()
				.int64(1, roomId)
				.int64(4, 12)
				.string(5, "audience")
				.string(9, "0")
				.toByteArray();
			ws.send(ByteString.of(pushFrame("im_enter_room", enterRoom, 0)));

			heartbeatSeq = 1;
			long interval = Math.max(1000, heartbeatMs);
			heartbeatFuture = executor.scheduleAtFixedRate(() -> sendHeartbeat(ws), 0, interval, TimeUnit.MILLISECONDS);
		}
	}

	private synchronized void sendHeartbeat(WebSocket ws)
	{
		if (ws != webSocket)
		{
			return;
		}

		byte[] heartbeat = new ProtoWriter()
			.int64(1, roomId)
			.int64(2, heartbeatSeq++)
			.toByteArray();
		ws.send(ByteString.of(pushFrame("hb", heartbeat, 0)));
	}

	@Override
	public void onMessage(WebSocket ws, ByteString bytes)
	{
		synchronized (this)
		{
			if (ws != webSocket)
			{
				return;
			}
		}

		try
		{
			handleFrame(ws, bytes.toByteArray());
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("Unable to decode TikTok message", e);
		}
	}

	void handleFrame(WebSocket ws, byte[] frame) throws IOException
	{
		// WebcastPushFrame
		long logId = 0;
		String payloadType = "";
		String compression = "";
		byte[] payload = new byte[0];

		ProtoReader r = new ProtoReader(frame);
		while (r.next())
		{
			switch (r.field())
			{
				case 2:
					logId = r.varint();
					break;
				case 5: // headers
					ProtoReader header = r.message();
					String key = "";
					String value = "";
					while (header.next())
					{
						if (header.field() == 1)
						{
							key = header.string();
						}
						else if (header.field() == 2)
						{
							value = header.string();
						}
						else
						{
							header.skip();
						}
					}
					if (key.equals("compress_type"))
					{
						compression = value;
					}
					break;
				case 7:
					payloadType = r.string();
					break;
				case 8:
					payload = r.bytes();
					break;
				default:
					r.skip();
			}
		}

		if (!payloadType.equals("msg"))
		{
			return;
		}

		if (compression.equals("gzip"))
		{
			payload = gunzip(payload);
		}

		// ProtoMessageFetchResult
		boolean needAck = false;
		String internalExt = "";
		ProtoReader result = new ProtoReader(payload);
		while (result.next())
		{
			switch (result.field())
			{
				case 1:
					handleMessage(result.message());
					break;
				case 5:
					internalExt = result.string();
					break;
				case 9:
					needAck = result.bool();
					break;
				default:
					result.skip();
			}
		}

		if (needAck)
		{
			byte[] ack = (internalExt.isEmpty() ? "-" : internalExt).getBytes(StandardCharsets.UTF_8);
			ws.send(ByteString.of(pushFrame("ack", ack, logId)));
		}
	}

	private void handleMessage(ProtoReader message)
	{
		// BaseProtoMessage
		String method = "";
		byte[] payload = new byte[0];
		long msgId = 0;
		while (message.next())
		{
			switch (message.field())
			{
				case 1:
					method = message.string();
					break;
				case 2:
					payload = message.bytes();
					break;
				case 3:
					msgId = message.varint();
					break;
				default:
					message.skip();
			}
		}

		// TikTok can deliver the same message more than once
		if (msgId != 0 && !seenMessageIds.add(msgId))
		{
			log.debug("Skipping duplicate {} {}", method, msgId);
			return;
		}

		ProtoReader r = new ProtoReader(payload);
		switch (method)
		{
			case "WebcastChatMessage":
				onChat(r);
				break;
			case "WebcastGiftMessage":
				onGift(r);
				break;
			case "WebcastLikeMessage":
				onLike(r);
				break;
			case "WebcastMemberMessage":
				onMember(r);
				break;
			case "WebcastSocialMessage":
				onSocial(r);
				break;
			case "WebcastSubNotifyMessage":
				onSubscribe(r);
				break;
			case "WebcastRoomUserSeqMessage":
				onRoomUserSeq(r);
				break;
			case "WebcastControlMessage":
				onControl(r);
				break;
		}
	}

	private void onChat(ProtoReader r)
	{
		JsonObject data = new JsonObject();
		while (r.next())
		{
			switch (r.field())
			{
				case 2:
					readUser(r.message(), data);
					break;
				case 3:
					data.addProperty("comment", r.string());
					break;
				default:
					r.skip();
			}
		}
		emit("chat", data);
	}

	private void onGift(ProtoReader r)
	{
		JsonObject data = new JsonObject();
		// protobuf omits false values, and the start of a streak has no repeat_end
		data.addProperty("repeatEnd", false);
		while (r.next())
		{
			switch (r.field())
			{
				case 5:
					data.addProperty("repeatCount", r.varint());
					break;
				case 7:
					readUser(r.message(), data);
					break;
				case 9:
					data.addProperty("repeatEnd", r.varint() != 0);
					break;
				case 15: // Gift
					ProtoReader gift = r.message();
					while (gift.next())
					{
						switch (gift.field())
						{
							case 11:
								data.addProperty("giftType", gift.varint());
								break;
							case 12:
								data.addProperty("diamondCount", gift.varint());
								break;
							case 16:
								data.addProperty("giftName", gift.string());
								break;
							default:
								gift.skip();
						}
					}
					break;
				default:
					r.skip();
			}
		}
		emit("gift", data);
	}

	private void onLike(ProtoReader r)
	{
		JsonObject data = new JsonObject();
		while (r.next())
		{
			switch (r.field())
			{
				case 2:
					data.addProperty("likeCount", r.varint());
					break;
				case 3:
					data.addProperty("totalLikeCount", r.varint());
					break;
				case 5:
					readUser(r.message(), data);
					break;
				default:
					r.skip();
			}
		}
		emit("like", data);
	}

	private void onMember(ProtoReader r)
	{
		JsonObject data = new JsonObject();
		long action = 0;
		while (r.next())
		{
			switch (r.field())
			{
				case 2:
					readUser(r.message(), data);
					break;
				case 10:
					action = r.varint();
					break;
				default:
					r.skip();
			}
		}
		if (action == MEMBER_JOINED)
		{
			emit("member", data);
		}
	}

	private void onSocial(ProtoReader r)
	{
		JsonObject data = new JsonObject();
		String displayType = "";
		while (r.next())
		{
			switch (r.field())
			{
				case 1:
					displayType = readDisplayType(r.message());
					break;
				case 2:
					readUser(r.message(), data);
					break;
				default:
					r.skip();
			}
		}

		if (displayType.contains("follow"))
		{
			emit("follow", data);
		}
		else if (displayType.contains("share"))
		{
			emit("share", data);
		}
	}

	private void onSubscribe(ProtoReader r)
	{
		JsonObject data = new JsonObject();
		while (r.next())
		{
			if (r.field() == 2)
			{
				readUser(r.message(), data);
			}
			else
			{
				r.skip();
			}
		}
		emit("subscribe", data);
	}

	private void onRoomUserSeq(ProtoReader r)
	{
		while (r.next())
		{
			if (r.field() == 3)
			{
				JsonObject data = new JsonObject();
				data.addProperty("viewerCount", r.varint());
				emit("roomUser", data);
			}
			else
			{
				r.skip();
			}
		}
	}

	private void onControl(ProtoReader r)
	{
		long action = 0;
		while (r.next())
		{
			if (r.field() == 2)
			{
				action = r.varint();
			}
			else
			{
				r.skip();
			}
		}

		if (action == CONTROL_STREAM_ENDED || action == CONTROL_STREAM_SUSPENDED)
		{
			emit("streamEnd", new JsonObject());
			synchronized (this)
			{
				closeSocket();
				state = State.IDLE;
				status = "the LIVE has ended";
				nextAttempt = System.currentTimeMillis() + RETRY_OFFLINE_MS;
			}
		}
	}

	/**
	 * Reads CommonMessageData.display_text.key
	 */
	private static String readDisplayType(ProtoReader common)
	{
		while (common.next())
		{
			if (common.field() == 8)
			{
				ProtoReader text = common.message();
				while (text.next())
				{
					if (text.field() == 1)
					{
						return text.string();
					}
					text.skip();
				}
			}
			else
			{
				common.skip();
			}
		}
		return "";
	}

	private static void readUser(ProtoReader user, JsonObject data)
	{
		while (user.next())
		{
			switch (user.field())
			{
				case 1:
					data.addProperty("userId", Long.toString(user.varint()));
					break;
				case 3:
					data.addProperty("nickname", user.string());
					break;
				case 38:
					data.addProperty("uniqueId", user.string());
					break;
				default:
					user.skip();
			}
		}
	}

	private void emit(String event, JsonObject data)
	{
		JsonObject json = new JsonObject();
		json.addProperty("event", event);
		json.add("data", data);
		eventHandler.accept(json);
	}

	@Override
	public void onClosing(WebSocket ws, int code, String reason)
	{
		ws.close(1000, null);
	}

	@Override
	public void onClosed(WebSocket ws, int code, String reason)
	{
		synchronized (this)
		{
			if (ws != webSocket)
			{
				// closed by us
				return;
			}
		}

		log.debug("TikTok WebSocket closed: {} {}", code, reason);
		disconnected(ws, "disconnected, reconnecting...");
	}

	@Override
	public void onFailure(WebSocket ws, Throwable t, Response response)
	{
		synchronized (this)
		{
			if (ws != webSocket)
			{
				// cancelled by us, e.g. the LIVE ended or the settings changed
				return;
			}
		}

		String reason = response != null ? response.header("Handshake-Msg") : null;
		log.debug("TikTok WebSocket failed ({})", reason, t);
		disconnected(ws, reason != null ? "rejected by TikTok: " + reason : "disconnected, reconnecting...");
	}

	private synchronized void disconnected(WebSocket ws, String message)
	{
		if (ws != webSocket)
		{
			return;
		}

		closeSocket();
		state = State.IDLE;
		status = message;
		nextAttempt = System.currentTimeMillis() + RETRY_OFFLINE_MS;
	}

	private synchronized void fail(int gen, String message, long retryMs)
	{
		if (gen != generation)
		{
			return;
		}

		log.debug("TikTok connection failed: {}", message);
		closeSocket();
		state = State.IDLE;
		status = message;
		nextAttempt = System.currentTimeMillis() + retryMs;
	}

	private static Request.Builder tiktokRequest(HttpUrl url)
	{
		return new Request.Builder()
			.url(url)
			.header("User-Agent", BROWSER_USER_AGENT)
			.header("Referer", "https://www.tiktok.com/")
			.header("Accept", "application/json");
	}

	/**
	 * RuneLite prefixes its own name to the User-Agent of every request, so the
	 * sign server must be given the same string the WebSocket will send.
	 */
	private static String effectiveUserAgent()
	{
		String runelite = RuneLite.USER_AGENT;
		return Strings.isNullOrEmpty(runelite) ? BROWSER_USER_AGENT : runelite + " " + BROWSER_USER_AGENT;
	}

	private synchronized void storeCookies(int gen, Response response)
	{
		if (gen != generation)
		{
			return;
		}

		for (String setCookie : response.headers("Set-Cookie"))
		{
			putCookie(setCookie.split(";", 2)[0]);
		}
	}

	private synchronized void parseCookieHeader(int gen, String header)
	{
		if (gen != generation || header == null)
		{
			return;
		}

		for (String part : header.split("[;,]"))
		{
			String trimmed = part.trim();
			int eq = trimmed.indexOf('=');
			if (eq <= 0)
			{
				continue;
			}

			String name = trimmed.substring(0, eq).toLowerCase();
			if (name.equals("path") || name.equals("domain") || name.equals("expires") || name.equals("max-age")
				|| name.equals("samesite") || name.equals("priority"))
			{
				continue;
			}
			putCookie(trimmed);
		}
	}

	private void putCookie(String pair)
	{
		int eq = pair.indexOf('=');
		if (eq > 0)
		{
			cookies.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
		}
	}

	private static byte[] pushFrame(String type, byte[] payload, long logId)
	{
		ProtoWriter w = new ProtoWriter();
		if (logId != 0)
		{
			w.int64(2, logId);
		}
		return w.string(6, "pb")
			.string(7, type)
			.bytes(8, payload)
			.toByteArray();
	}

	private static byte[] gunzip(byte[] data) throws IOException
	{
		try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(data)))
		{
			return in.readAllBytes();
		}
	}

	private static String urlEncode(String s)
	{
		return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
	}

	private static String getString(JsonObject root, String... path)
	{
		JsonElement e = root;
		for (String key : path)
		{
			if (e == null || !e.isJsonObject())
			{
				return "";
			}
			e = e.getAsJsonObject().get(key);
		}
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}
}

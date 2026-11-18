package com.tiktoklive;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Lenient accessor for the "data" object of a TikTok LIVE event. Missing or malformed
 * fields read as empty values instead of throwing.
 */
class EventData
{
	private final JsonObject data;

	EventData(JsonObject data)
	{
		this.data = data != null ? data : new JsonObject();
	}

	String getString(String key)
	{
		JsonPrimitive p = find(key);
		return p != null ? p.getAsString() : "";
	}

	long getLong(String key)
	{
		JsonPrimitive p = find(key);
		if (p == null)
		{
			return 0;
		}

		try
		{
			return p.getAsLong();
		}
		catch (NumberFormatException ex)
		{
			return 0;
		}
	}

	boolean getBoolean(String key, boolean def)
	{
		JsonPrimitive p = find(key);
		if (p == null)
		{
			return def;
		}
		return p.isBoolean() ? p.getAsBoolean() : Boolean.parseBoolean(p.getAsString());
	}

	private JsonPrimitive find(String key)
	{
		JsonElement e = data.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsJsonPrimitive() : null;
	}
}

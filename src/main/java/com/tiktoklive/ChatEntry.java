package com.tiktoklive;

import lombok.Value;

@Value
class ChatEntry
{
	enum Kind
	{
		CHAT,
		GIFT,
		EVENT
	}

	Kind kind;
	String name;
	/**
	 * Stable id of the viewer (their @username), or null for system messages
	 */
	String viewerKey;
	String message;
	boolean highlighted;
	long timestamp;
}

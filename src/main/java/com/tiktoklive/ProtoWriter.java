package com.tiktoklive;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Minimal protobuf wire format writer.
 */
final class ProtoWriter
{
	private final ByteArrayOutputStream out = new ByteArrayOutputStream();

	ProtoWriter int64(int field, long value)
	{
		writeVarint((long) field << 3);
		writeVarint(value);
		return this;
	}

	ProtoWriter string(int field, String value)
	{
		return bytes(field, value.getBytes(StandardCharsets.UTF_8));
	}

	ProtoWriter bytes(int field, byte[] value)
	{
		writeVarint((long) field << 3 | 2);
		writeVarint(value.length);
		out.write(value, 0, value.length);
		return this;
	}

	byte[] toByteArray()
	{
		return out.toByteArray();
	}

	private void writeVarint(long value)
	{
		while ((value & ~0x7FL) != 0)
		{
			out.write((int) ((value & 0x7F) | 0x80));
			value >>>= 7;
		}
		out.write((int) value);
	}
}

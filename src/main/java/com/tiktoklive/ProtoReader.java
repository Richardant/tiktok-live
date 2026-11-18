package com.tiktoklive;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Minimal protobuf wire format reader. Usage:
 * <pre>
 * while (r.next()) {
 *     switch (r.field()) {
 *         case 1: value = r.string(); break;
 *         default: r.skip();
 *     }
 * }
 * </pre>
 */
final class ProtoReader
{
	static final class ProtoException extends RuntimeException
	{
		ProtoException(String message)
		{
			super(message);
		}
	}

	private static final int VARINT = 0;
	private static final int FIXED64 = 1;
	private static final int LENGTH_DELIMITED = 2;
	private static final int FIXED32 = 5;

	private final byte[] buf;
	private final int end;
	private int pos;
	private int tag;

	ProtoReader(byte[] buf)
	{
		this(buf, 0, buf.length);
	}

	private ProtoReader(byte[] buf, int start, int end)
	{
		this.buf = buf;
		this.pos = start;
		this.end = end;
	}

	boolean next()
	{
		if (pos >= end)
		{
			return false;
		}
		tag = (int) readVarint();
		return true;
	}

	int field()
	{
		return tag >>> 3;
	}

	long varint()
	{
		if ((tag & 7) != VARINT)
		{
			skip();
			return 0;
		}
		return readVarint();
	}

	boolean bool()
	{
		return varint() != 0;
	}

	String string()
	{
		return new String(bytes(), StandardCharsets.UTF_8);
	}

	byte[] bytes()
	{
		if ((tag & 7) != LENGTH_DELIMITED)
		{
			skip();
			return new byte[0];
		}
		int len = readLength();
		byte[] b = Arrays.copyOfRange(buf, pos, pos + len);
		pos += len;
		return b;
	}

	ProtoReader message()
	{
		if ((tag & 7) != LENGTH_DELIMITED)
		{
			skip();
			return new ProtoReader(buf, pos, pos);
		}
		int len = readLength();
		ProtoReader r = new ProtoReader(buf, pos, pos + len);
		pos += len;
		return r;
	}

	void skip()
	{
		switch (tag & 7)
		{
			case VARINT:
				readVarint();
				break;
			case FIXED64:
				advance(8);
				break;
			case LENGTH_DELIMITED:
				advance(readLength());
				break;
			case FIXED32:
				advance(4);
				break;
			default:
				throw new ProtoException("Unsupported wire type " + (tag & 7));
		}
	}

	private void advance(int n)
	{
		if (n < 0 || pos + n > end)
		{
			throw new ProtoException("Truncated message");
		}
		pos += n;
	}

	private int readLength()
	{
		long len = readVarint();
		if (len < 0 || pos + len > end)
		{
			throw new ProtoException("Invalid length " + len);
		}
		return (int) len;
	}

	private long readVarint()
	{
		long result = 0;
		for (int shift = 0; shift < 64; shift += 7)
		{
			if (pos >= end)
			{
				throw new ProtoException("Truncated varint");
			}
			byte b = buf[pos++];
			result |= (long) (b & 0x7F) << shift;
			if ((b & 0x80) == 0)
			{
				return result;
			}
		}
		throw new ProtoException("Malformed varint");
	}
}

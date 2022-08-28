package me.crespel.strava.model;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

public class EpochDateTimeDeserializer extends StdDeserializer<ZonedDateTime>
{
	private static final long serialVersionUID = 1L;

	public EpochDateTimeDeserializer()
	{
		this(null);
	}

	public EpochDateTimeDeserializer(Class<?> vc)
	{
		super(vc);
	}

	@Override
	public ZonedDateTime deserialize(JsonParser jsonparser, DeserializationContext context) throws IOException, JsonProcessingException
	{
		return Instant.ofEpochSecond(Long.parseLong(jsonparser.getText())).atZone(ZoneId.systemDefault());
	}
}
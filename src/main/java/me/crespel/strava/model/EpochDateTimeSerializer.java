package me.crespel.strava.model;

import java.io.IOException;
import java.time.ZonedDateTime;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

public class EpochDateTimeSerializer extends StdSerializer<ZonedDateTime>
{

	private static final long serialVersionUID = 1L;

	protected EpochDateTimeSerializer()
	{
		this(null);
	}

	protected EpochDateTimeSerializer(Class<ZonedDateTime> t)
	{
		super(t);
	}

	@Override
	public void serialize(ZonedDateTime date, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException
	{
		jsonGenerator.writeNumber(date.toEpochSecond());
	}

}
package me.crespel.strava.model;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AccessTokenResponse
{
	public String			token_type;
	@JsonDeserialize(using = EpochDateTimeDeserializer.class)
	@JsonSerialize(using = EpochDateTimeSerializer.class)
	public ZonedDateTime	expires_at;
	public int				expires_in;
	public String			refresh_token;
	public String			access_token;

	@JsonIgnore
	public boolean isExpired()
	{
		return ZonedDateTime.now().isAfter(expires_at);
	}
}

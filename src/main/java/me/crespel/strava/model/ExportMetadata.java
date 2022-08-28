package me.crespel.strava.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExportMetadata
{
	public String	name;
	public String	description;
	public boolean	commute;
	public boolean	trainer;
	public String	dataType;
	public String	sportType;
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public String	gearId;
	public String	externalId;

	public String	fileName;
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public String	shoe;
}

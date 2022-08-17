package me.crespel.runtastic.model;

import java.nio.file.Path;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.Data;

/**
 * Runtastic model for images meta data (\Photos\Images-meta-data).
 *
 * @author Christian IMFELD (imfeldc@gmail.com)
 */
@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ImageMetaData implements Comparable<ImageMetaData>
{

	@JsonFormat(shape = Shape.NUMBER)
	private Date			createdAt;
	private LocationData	location;
	public String			id;
	public String			sampleId;

	@JsonIgnore
	public Path imagePath;

	@Override
	public int compareTo(ImageMetaData o)
	{
		if (o == null)
		{
			return 1;
		}
		else if (this.id == null)
		{
			return -1;
		}
		return (this.id.compareTo(o.id));
	}

}

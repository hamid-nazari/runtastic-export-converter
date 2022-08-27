package me.crespel.runtastic.model;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.topografix.gpx._1._1.BoundsType;
import com.topografix.gpx._1._1.GpxType;

import lombok.Data;
import lombok.ToString;

@Data
@ToString(of = {"id", "sportTypeId", "startTime", "duration", "distance", "userEquipmentIds"})
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SportSession implements Comparable<SportSession>
{

	public Date				startTime;
	public Date				endTime;
	private Date			createdAt;
	private Date			updatedAt;
	private Integer			startTimeTimezoneOffset;
	private Integer			endTimeTimezoneOffset;
	public Integer			distance;
	private Integer			duration;
	private Integer			elevationGain;
	private Integer			elevationLoss;
	private BigDecimal		averageSpeed;
	private Integer			calories;
	private BigDecimal		longitude;
	private BigDecimal		latitude;
	private BigDecimal		maxSpeed;
	private Integer			pauseDuration;
	private Integer			durationPerKm;
	private BigDecimal		temperature;
	private String			notes;
	public Integer			pulseAvg;
	public Integer			pulseMax;
	private Boolean			manual;
	private Boolean			edited;
	private Boolean			completed;
	private Boolean			liveTrackingActive;
	private Boolean			liveTrackingEnabled;
	private Boolean			cheeringEnabled;
	public Boolean			indoor;
	private Boolean			altitudeRefined;
	public String			id;
	private String			weatherConditionId;
	private String			surfaceId;
	private String			subjectiveFeelingId;
	public String			sportTypeId;
	private List<String>	userEquipmentIds;

	@JsonIgnore
	private List<ElevationData> elevationData;

	@JsonIgnore
	public List<GpsData> gpsData;

	@JsonIgnore
	public GpxType gpx;

	@JsonIgnore
	public List<HeartRateData> heartRateData;

	@JsonIgnore
	private List<ImageMetaData> images;

	@JsonIgnore
	private List<SportSession> overlapSessions;

	@JsonIgnore
	private List<SportSession> compoundSessions;

	@JsonIgnore
	private BoundsType	innerBound;
	@JsonIgnore
	private BoundsType	outerBound;

	@JsonIgnore
	private User user;

	public Boolean contains(String filter)
	{
		Boolean ret = false;
		if (filter != null)
		{
			if (getId().equals(filter))
			{
				// id is equal to filter (keyword)
				ret = true;
			}
			else if ((getNotes() != null) && getNotes().contains(filter))
			{
				// notes are available and contains the filter (keyword)
				ret = true;
			}
			else if (getUserEquipmentIds() != null)
			{
				for (String equipmentid : getUserEquipmentIds())
				{
					if (equipmentid.equals(filter))
					{
						// equipment id is equal to filter (keyword)
						ret = true;
						break;
					}
				}
			}
			else if (getImages() != null)
			{
				for (ImageMetaData image : getImages())
				{
					if (image.getId().toString().equals(filter))
					{
						// photo id is equal to filter (keyword)
						ret = true;
						break;
					}
				}
			}
		}
		else
		{
			// no filter set; retun "true"
			ret = true;
		}
		return ret;
	}

	@Override
	public int compareTo(SportSession o)
	{
		if (o == null)
		{
			return 1;
		}
		else if (this.startTime == null)
		{
			return -1;
		}
		else
		{
			int ret = this.startTime.compareTo(o.startTime);
			if (ret == 0)
			{
				ret = this.id.compareTo(o.id);
			}
			return ret;
		}
	}

	public boolean hasTimeOverlap(SportSession other, int toleranceSeconds)
	{
		if (this.startTime == null
			|| this.endTime == null
			|| other == null
			|| other.startTime == null
			|| other.endTime == null)
			return false;
		if (this.startTime.toInstant().minusSeconds(toleranceSeconds).isAfter(other.endTime.toInstant()) ||
			this.endTime.toInstant().plusSeconds(toleranceSeconds).isBefore(other.startTime.toInstant()))
			return false;
		return true;
	}

}

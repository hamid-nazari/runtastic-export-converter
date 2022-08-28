package me.crespel.runtastic;

import java.awt.Desktop;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import javax.activity.InvalidActivityException;
import javax.security.sasl.AuthenticationException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.topografix.gpx._1._1.BoundsType;

import me.crespel.runtastic.converter.ExportConverter;
import me.crespel.runtastic.model.ImageMetaData;
import me.crespel.runtastic.model.SportSession;
import me.crespel.runtastic.model.User;
import me.crespel.strava.model.AccessTokenResponse;
import me.crespel.strava.model.ExportMetadata;
import me.crespel.strava.model.FailureResponse;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.MultipartBody.Builder;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Runtastic export converter main class.
 *
 * @author Fabien CRESPEL (fabien@crespel.net)
 * @author Christian IMFELD (imfeldc@gmail.com)
 */
public class RuntasticExportConverter
{

	protected final ExportConverter converter = new ExportConverter();

	public static void main(String[] args)
	{
		RuntasticExportConverter converter = new RuntasticExportConverter();
		try
		{
			converter.run(args);
		}
		catch (Throwable t)
		{
			t.printStackTrace();
			converter.printUsage();
			System.exit(1);
		}
	}

	public void run(String[] args) throws Exception
	{
		String action = args.length > 0 ? args[0] : "";
		switch (action)
		{
			case "check":
				if (args.length < 2)
				{
					throw new IllegalArgumentException("Missing argument for action 'check'");
				}
				doCheck(new File(args[1]));
				break;
			case "list":
				if (args.length < 2)
				{
					throw new IllegalArgumentException("Missing argument for action 'list'");
				}
				doListWithFilter(new File(args[1]), args.length > 2 ? args[2] : null);
				break;
			case "user":
				if (args.length < 2)
				{
					throw new IllegalArgumentException("Missing argument for action 'user'");
				}
				doUser(new File(args[1]));
				break;
			case "info":
				if (args.length < 3)
				{
					throw new IllegalArgumentException("Missing argument for action 'info'");
				}
				doInfo(new File(args[1]), args[2]);
				break;
			case "convert":
				if (args.length < 4)
				{
					throw new IllegalArgumentException("Missing arguments for action 'convert'");
				}
				doConvert(new File(args[1]), args[2], new File(args[3]), args.length > 4 ? args[4] : null, args.length > 5 ? "meta".equalsIgnoreCase(args[5]) : false);
				break;
			case "overlap":
				if (args.length < 3)
				{
					throw new IllegalArgumentException("Missing argument for action 'overlap'");
				}
				doOverlap(new File(args[2]), args[1], args.length > 3 ? new File(args[3]) : null, args.length > 4 ? args[4] : "gpx");
				break;
			case "compound":
				if (args.length < 3)
				{
					throw new IllegalArgumentException("Missing argument for action 'compound'");
				}
				doCompound(new File(args[2]), args[1], args.length > 3 ? new File(args[3]) : null, args.length > 4 ? args[4] : "gpx");
				break;
			case "upload-strava":
				if (args.length < 5)
				{
					throw new IllegalArgumentException("Missing argument for action 'upload-strava'");
				}
				doUploadStrava(Paths.get(args[1]), args[2], args[3], args[4]);
				break;
			case "help":
			default:
				printUsage();
				break;
		}
	}

	protected void printUsage()
	{
		System.out.println("Expected arguments:");
		System.out.println("  check    <export path>");
		System.out.println("  list     <export path> <filter>");
		System.out.println("  user     <export path>");
		System.out.println("  info     <export path> <activity id>");
		System.out.println("  convert  <export path> <activity id | 'all'> <destination path> ['gpx' | 'tcx' | 'auto'] ['meta']");
		System.out.println("  overlap  <export path> <activity id | 'all'> <destination path> ['gpx' | 'tcx']");
		System.out.println("  compound <export path> <activity id | 'all'> <destination path> ['gpx' | 'tcx']");
		System.out.println("  upload-strava <converted path> <client_id> <client_secret> <code>");
		System.out.println("  help");
	}

	private void doCheck(File path) throws FileNotFoundException, IOException
	{
		System.out.println("Check curent export and provide some statistics ...");
		List<SportSession> sessions = converter.listSportSessions(path, false);
		System.out.println("      " + sessions.size() + " Sport Sessions found.");

		System.out.println("Load full list of sport session (inclusive all sub-data), this requires some time ...");
		List<SportSession> fullsessions = converter.convertSportSessions(path, "gpx");

		// Calculate statistics ..
		Integer gpxSessionCount = 0;
		Integer heartRateDataCount = 0;
		Integer imageSessionCount = 0;
		Integer imageCount = 0;
		Integer minDistance = Integer.MAX_VALUE, maxDistance = 0, totDistance = 0;
		for (SportSession session : fullsessions)
		{
			if (session == null)
				continue;
			if (session.getGpx() != null)
				gpxSessionCount += 1;
			if (session.getHeartRateData() != null)
				heartRateDataCount += 1;
			if (session.getImages() != null)
			{
				imageSessionCount += 1;
				imageCount += session.getImages().size();
			}
			minDistance = Integer.min(minDistance, session.getDistance());
			maxDistance = Integer.max(maxDistance, session.getDistance());
			totDistance += session.getDistance();
		}

		// Calculate overlapping sessions
		converter.doOverlap(fullsessions);
		displaySummary(fullsessions, false);

		System.out.println("Session statistics ...");
		System.out.println("      " + fullsessions.size() + " Sport Sessions found.");
		System.out.println("      " + gpxSessionCount + " Sport Sessions found with GPX data assigned. ");
		System.out.println("      " + heartRateDataCount + " Sport Sessions found with heart rate data assigned.");
		System.out.println("      " + imageSessionCount + " Sport Sessions found with totally " + imageCount + " photo(s) assigned.");
		System.out.println("      Total Distance: " + totDistance / 1000.0 + " [km],  Minimum distance: " + minDistance / 1000.0 + " [km],  Maximum distance: " + maxDistance / 1000.0 + " [km]");

	}

	protected void doList(File path) throws FileNotFoundException, IOException
	{
		doListWithFilter(path, null);
	}

	protected void doListWithFilter(File path, String filter) throws FileNotFoundException, IOException
	{
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		List<SportSession> sessions = converter.listSportSessions(path, false);
		for (SportSession session : sessions)
		{
			if (filter == null || session.contains(filter))
			{
				System.out.println(sdf.format(session.getStartTime()) + " - ID: " + session.getId() + ", Sport Type: " + session.getSportTypeId() + ", duration: " + Duration.ofMillis(session.getDuration()).toString() + " (" + session.getDuration() / 60000 + " min), Notes: '" + session.getNotes() + "'");
			}
		}
	}

	protected void doUser(File path) throws FileNotFoundException, IOException
	{
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		User user = converter.getUser(path);
		System.out.println(sdf.format(user.getCreatedAt()) + " - ID: " + user.getLogin());
		System.out.println("      Name: " + user.getFirstName() + " " + user.getLastName() + ",  Birthday: " + user.getBirthday() + ",  City: " + user.getCityName());
		System.out.println("      Mail: " + user.getEmail() + " (" + user.getFbProxiedEMail() + ")");
		System.out.println("      Gender: " + user.getGender() + ", Height: " + user.getHeight() + ", Weight: " + user.getWeight() + ", Language: " + user.getLanguage());
		System.out.println("      Created At: " + sdf.format(user.getCreatedAt()) + ",  Confirmed At: " + sdf.format(user.getConfirmedAt()) + ",  Last Sign-in At: " + sdf.format(user.getLastSignInAt()) + ",  Updated At: " + sdf.format(user.getUpdatedAt()));
	}

	protected void doInfo(File path, String id) throws FileNotFoundException, IOException
	{
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		SportSession session = converter.getSportSession(path, id);
		if (session != null)
		{
			System.out.println(sdf.format(session.getStartTime()) + " - ID: " + session.getId());
			System.out.println("      Sport Type: " + session.getSportTypeId() + ", Surface Type: " + session.getSurfaceId() + ", Feeling Id: " + session.getSubjectiveFeelingId());
			System.out.println("      Duration: " + Duration.ofMillis(session.getDuration()).toString() + " (" + session.getDuration() / 60000 + " min)");
			System.out.println("      Distance: " + (session.getDistance() != null ? session.getDistance() / 1000.0 : "n/a") + " km, Calories: " + session.getCalories());
			System.out.println("      Avg Pace: " + (session.getDurationPerKm() != null ? session.getDurationPerKm() / 60000.0 : "n/a") + " min/km");
			System.out.println("      Avg Speed: " + session.getAverageSpeed() + " km/h, Max Speed: " + session.getMaxSpeed() + " km/h");
			System.out.println("      Start: " + sdf.format(session.getStartTime()) + ", End: " + sdf.format(session.getEndTime()) + ", Created: " + sdf.format(session.getCreatedAt()) + ", Updated: " + sdf.format(session.getUpdatedAt()));
			System.out.println("      Elevation: (+) " + session.getElevationGain() + " m , (-) " + session.getElevationLoss() + " m  /  " + (session.getLatitude() != null ? "Latitude: " + session.getLatitude() + ", Longitude: " + session.getLongitude() + "  ( http://maps.google.com/maps?q=" + session.getLatitude() + "," + session.getLongitude() + " )" : "No GPS information available."));
			System.out.println("      Notes: " + session.getNotes());
			System.out.println("      Waypoints: " + ((session.getGpsData() == null) ? "0" : session.getGpsData().size()) + " JSON points, " + ((session.getGpx() == null) ? "0" : session.getGpx().getTrk().get(0).getTrkseg().get(0).getTrkpt().size()) + " GPX points.");
			System.out.println("      Photos:" + (session.getImages() != null ? session.getImages().size() : "none"));
			if (session.getImages() != null)
			{
				for (ImageMetaData image : session.getImages())
				{
					System.out.println("             [" + image.getId() + ".jpg] " + sdf.format(image.getCreatedAt()) + (image.getLocation() != null ? " ( http://maps.google.com/maps?q=" + image.getLocation().getLatitude() + "," + image.getLocation().getLongitude() + " )" : ""));
				}
			}
			if (session.getUser() != null)
			{
				User user = session.getUser();
				System.out.println("      Name: " + user.getFirstName() + " " + user.getLastName() + ",  Birthday: " + user.getBirthday() + ",  City: " + user.getCityName());
				System.out.println("      Mail: " + user.getEmail() + " (" + user.getFbProxiedEMail() + ")");
				System.out.println("      Gender: " + user.getGender() + ", Height: " + user.getHeight() + ", Weight: " + user.getWeight() + ", Language: " + user.getLanguage());
				System.out.println("      Created At: " + sdf.format(user.getCreatedAt()) + ",  Confirmed At: " + sdf.format(user.getConfirmedAt()) + ",  Last Sign-in At: " + sdf.format(user.getLastSignInAt()) + ",  Updated At: " + sdf.format(user.getUpdatedAt()));
			}
		}
	}

	protected void doConvert(File path, String id, File dest, String format, boolean withMetadata) throws FileNotFoundException, IOException
	{
		if ("all".equalsIgnoreCase(id))
		{
			long startTime = System.currentTimeMillis();
			int count = converter.exportSportSessions(path, dest, format, withMetadata);
			System.out.println(count + " activities successfully written to '" + dest + "' in " + Duration.ofMillis(System.currentTimeMillis() - startTime));
		}
		else
		{
			converter.exportSportSession(path, id, dest, format);
			System.out.println("Activity successfully written to '" + dest + "'");
		}
	}

	private void doOverlap(File path, String id, File dest, String format) throws FileNotFoundException, IOException
	{
		long startTime = System.currentTimeMillis();
		System.out.println("Load full list of sport session (inclusive all sub-data), this requires some time ...");
		List<SportSession> sessions = converter.convertSportSessions(path, format);
		converter.doOverlap(sessions);
		displaySummary(sessions, false);

		if (dest != null)
		{
			System.out.println("Export '" + id + "' overlap sport session(s) ...");
			for (SportSession session : sessions)
			{
				List<SportSession> overlapSessions = session.getOverlapSessions();
				if ((overlapSessions != null) && (overlapSessions.size() > 0))
				{
					if ("all".equalsIgnoreCase(id) || (id.equalsIgnoreCase(session.getId())))
					{
						converter.exportSportSession(session, dest, format);
					}
				}
			}
		}

		long endTime = System.currentTimeMillis();
		System.out.println(sessions.size() + " activities successfully processed, in " + (endTime - startTime) / 1000 + " seconds");
	}

	private void doCompound(File path, String id, File dest, String format) throws FileNotFoundException, IOException
	{
		long startTime = System.currentTimeMillis();
		System.out.println("Load full list of sport session (inclusive all sub-data), this requires some time ...");
		List<SportSession> sessions = converter.convertSportSessions(path, format);
		converter.doCompound(sessions);
		displaySummary(sessions, false);

		if (dest != null)
		{
			System.out.println("Export '" + id + "' compound sport session(S) ...");
			for (SportSession session : sessions)
			{
				List<SportSession> compoundSessions = session.getCompoundSessions();
				if ((compoundSessions != null) && (compoundSessions.size() > 0))
				{
					if ("all".equalsIgnoreCase(id) || (id.equalsIgnoreCase(session.getId())))
					{
						converter.exportSportSession(session, dest, format);
					}
				}
			}
		}

		long endTime = System.currentTimeMillis();
		System.out.println(sessions.size() + " activities successfully processed, in " + (endTime - startTime) / 1000 + " seconds");
	}

	// display summary of sport sessions
	public void displaySummary(List<SportSession> sessions, boolean full)
	{
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Integer singleSessionCount = 0;
		Integer multiSessionCount = 0;
		Integer totMultiSessionCount = 0;
		Integer minNumOverlapSessions = 99999;
		Integer maxNumOverlapSessions = 0;
		Integer compoundSessionCount = 0;

		System.out.println("Sessions with 'empty' GPX track(s) ...");
		Integer emptyGPXTrackSessionCount = 0;
		for (SportSession session : sessions)
		{
			if (session.getGpx() == null)
			{
				System.out.println("      " + sdf.format(session.getStartTime()) + " - ID: " + session.getId() + ", Sport Type: " + session.getSportTypeId() + ", duration: " + Duration.ofMillis(session.getDuration()).toString() + " (" + session.getDuration() / 60000 + " min), Notes: '" + session.getNotes() + "'");
				emptyGPXTrackSessionCount += 1;
			}
		}
		if (emptyGPXTrackSessionCount == 0)
		{
			System.out.println("      none");
		}

		System.out.println("Sessions with 'zero' distance ...");
		Integer zeroDistanceSessionCount = 0;
		for (SportSession session : sessions)
		{
			if (session.getDistance() != null)
			{
				if (session.getDistance() == 0)
				{
					System.out.println("      " + sdf.format(session.getStartTime()) + " - ID: " + session.getId() + ", Sport Type: " + session.getSportTypeId() + ", duration: " + Duration.ofMillis(session.getDuration()).toString() + " (" + session.getDuration() / 60000 + " min), Notes: '" + session.getNotes() + "'");
					zeroDistanceSessionCount += 1;
				}
			}
		}
		if (zeroDistanceSessionCount == 0)
		{
			System.out.println("      none");
		}

		System.out.println("Single sport session found ...");
		for (SportSession session : sessions)
		{
			List<SportSession> overlapsessions = session.getOverlapSessions();
			if ((overlapsessions == null) || (overlapsessions.size() == 0))
			{
				singleSessionCount += 1;
				if (full)
					System.out.println("      " + sdf.format(session.getStartTime()) + "[" + singleSessionCount + "] - ID: " + session.getId() + ", Sport Type: " + session.getSportTypeId() + ", Notes: '" + session.getNotes() + "'");
			}
		}
		if (singleSessionCount == 0)
		{
			System.out.println("      none");
		}
		else
		{
			System.out.println("      " + singleSessionCount + " single sessions found.");
		}

		System.out.println("Multiple sport session found ...");
		for (SportSession session : sessions)
		{
			List<SportSession> overlapsessions = session.getOverlapSessions();
			if ((overlapsessions != null) && (overlapsessions.size() > 0))
			{
				multiSessionCount += 1;
				totMultiSessionCount += overlapsessions.size();
				minNumOverlapSessions = Integer.min(minNumOverlapSessions, overlapsessions.size());
				maxNumOverlapSessions = Integer.max(maxNumOverlapSessions, overlapsessions.size());
				if (full)
					System.out.println("      " + sdf.format(session.getStartTime()) + "[" + multiSessionCount + "] - ID: " + session.getId() + ", Sport Type: " + session.getSportTypeId() + ", Notes: '" + session.getNotes() + "'");
				for (SportSession overlapsession : overlapsessions)
				{
					if (full)
						System.out.println("            " + sdf.format(overlapsession.getStartTime()) + " - ID: " + overlapsession.getId() + ", Sport Type: " + overlapsession.getSportTypeId() + ", Notes: '" + overlapsession.getNotes() + "'");
					if (overlapsessions.size() != overlapsession.getOverlapSessions().size())
					{
						// this "error" occured before normalizing the overlapping sessions
						System.out.println("            ----> Diff. count of overlapping sessions: " + overlapsession.getOverlapSessions().size() + " vs. " + overlapsessions.size() + ", ID=" + overlapsession.getId() + " with " + overlapsession.getOverlapSessions().size() + " sessions vs. ID=" + session.getId() + " with " + overlapsessions.size() + " sessions.");
					}
				}

				// check bounds ..
				if ((session.getGpx() != null) && (session.getGpx().getMetadata() != null) && (session.getGpx().getMetadata().getBounds() != null))
				{
					BoundsType sessionBound = session.getGpx().getMetadata().getBounds();
					if ((session.getInnerBound() != null) && (session.getOuterBound() != null))
					{
						if (sessionBound.getMinlat().compareTo(session.getInnerBound().getMinlat()) == 1)
						{
							System.out.println("            ----> Inner bound mismatch, getMinlat: ID=" + session.getId() + "  with session bound: " + sessionBound.getMinlat() + " vs. overlap bound: " + session.getInnerBound().getMinlat() + ".");
						}
						if (sessionBound.getMaxlat().compareTo(session.getInnerBound().getMaxlat()) == -1)
						{
							System.out.println("            ----> Inner bound mismatch, getMaxlat: ID=" + session.getId() + "  with session bound: " + sessionBound.getMaxlat() + " vs. overlap bound: " + session.getInnerBound().getMaxlat() + ".");
						}
						if (sessionBound.getMinlon().compareTo(session.getInnerBound().getMinlon()) == 1)
						{
							System.out.println("            ----> Inner bound mismatch, getMinlon: ID=" + session.getId() + "  with session bound: " + sessionBound.getMinlon() + " vs. overlap bound: " + session.getInnerBound().getMinlon() + ".");
						}
						if (sessionBound.getMaxlon().compareTo(session.getInnerBound().getMaxlon()) == -1)
						{
							System.out.println("            ----> Inner bound mismatch, getMaxlon: ID=" + session.getId() + "  with session bound: " + sessionBound.getMaxlon() + " vs. overlap bound: " + session.getInnerBound().getMaxlon() + ".");
						}
						if (sessionBound.getMinlat().compareTo(session.getOuterBound().getMinlat()) == -1)
						{
							System.out.println("            ----> Outer bound mismatch, getMinlat: ID=" + session.getId() + "  with session bound: " + sessionBound.getMinlat() + " vs. overlap bound: " + session.getInnerBound().getMinlat() + ".");
						}
						if (sessionBound.getMaxlat().compareTo(session.getOuterBound().getMaxlat()) == 1)
						{
							System.out.println("            ----> Outer bound mismatch, getMaxlat: ID=" + session.getId() + "  with session bound: " + sessionBound.getMaxlat() + " vs. overlap bound: " + session.getInnerBound().getMaxlat() + ".");
						}
						if (sessionBound.getMinlon().compareTo(session.getOuterBound().getMinlon()) == -1)
						{
							System.out.println("            ----> Outer bound mismatch, getMinlon: ID=" + session.getId() + "  with session bound: " + sessionBound.getMinlon() + " vs. overlap bound: " + session.getInnerBound().getMinlon() + ".");
						}
						if (sessionBound.getMaxlon().compareTo(session.getOuterBound().getMaxlon()) == 1)
						{
							System.out.println("            ----> Outer bound mismatch, getMaxlon: ID=" + session.getId() + "  with session bound: " + sessionBound.getMaxlon() + " vs. overlap bound: " + session.getInnerBound().getMaxlon() + ".");
						}
					}
					else
					{
						System.out.println("            ----> Inner and/or outer bounds not available: ID=" + session.getId() + "  with " + overlapsessions.size() + " sessions.");
					}
				}
				else
				{
					System.out.println("            ----> Session bounds not available: ID=" + session.getId() + "  with " + overlapsessions.size() + " sessions.");
				}
			}
		}
		if (multiSessionCount == 0)
		{
			System.out.println("      none");
		}
		else
		{
			System.out.println("      " + multiSessionCount + " multi sesions found.  Minimum " + minNumOverlapSessions + " and maximum " + maxNumOverlapSessions + " number of overlapping sessions.");
		}

		System.out.println("Compound sport session found ...");
		for (SportSession session : sessions)
		{
			List<SportSession> compoundSessions = session.getCompoundSessions();
			if ((compoundSessions != null) && (compoundSessions.size() > 0))
			{
				compoundSessionCount += 1;
				if (full)
					System.out.println("      " + sdf.format(session.getStartTime()) + "[" + compoundSessionCount + "] - ID: " + session.getId() + ", Sport Type: " + session.getSportTypeId() + ", Notes: '" + session.getNotes()
						+ "', Bounds[MinLat=" + session.getGpx().getMetadata().getBounds().getMinlat()
						+ ", MaxLat=" + session.getGpx().getMetadata().getBounds().getMaxlat()
						+ ", MinLon=" + session.getGpx().getMetadata().getBounds().getMinlon()
						+ ", MaxLon=" + session.getGpx().getMetadata().getBounds().getMaxlon() + "]");
				if (full)
				{
					for (SportSession compoundSession : compoundSessions)
					{
						System.out.println("            ID: " + compoundSession.getId() + ", Sport Type: " + compoundSession.getSportTypeId()
							+ ", Notes: '" + compoundSession.getNotes()
							+ "', Bounds[MinLat=" + compoundSession.getGpx().getMetadata().getBounds().getMinlat()
							+ ", MaxLat=" + compoundSession.getGpx().getMetadata().getBounds().getMaxlat()
							+ ", MinLon=" + compoundSession.getGpx().getMetadata().getBounds().getMinlon()
							+ ", MaxLon=" + compoundSession.getGpx().getMetadata().getBounds().getMaxlon() + "]");
					}
				}
			}
		}
		if (compoundSessionCount == 0)
		{
			System.out.println("      none");
		}
		else
		{
			System.out.println("      " + compoundSessionCount + " compound sessions found.");
		}

		System.out.println(sessions.size() + " activities successfully processed with max. deviation of " + distance(0, 0, 0, converter.diff.doubleValue(), "K") + " km.");
	}

	// See https://www.geodatasource.com/developers/java
	private static double distance(double lat1, double lon1, double lat2, double lon2, String unit)
	{
		if ((lat1 == lat2) && (lon1 == lon2))
		{
			return 0;
		}
		else
		{
			double theta = lon1 - lon2;
			double dist = Math.sin(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2)) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(Math.toRadians(theta));
			dist = Math.acos(dist);
			dist = Math.toDegrees(dist);
			dist = dist * 60 * 1.1515;
			if (unit.equals("K"))
			{
				dist = dist * 1.609344;
			}
			else if (unit.equals("N"))
			{
				dist = dist * 0.8684;
			}
			return (dist);
		}
	}

	public static String mapSportType(String id)
	{
		switch (id)
		{
			case "1":
				return "Run";
			case "2":
				return "Nordic Walking";
			case "3":
				return "Cycling";
			case "4":
				return "Mountain Biking";
			case "5":
				return "Other";
			case "6":
				return "Inline Skating";
			case "7":
				return "Hiking";
			case "8":
				return "Cross-country skiing";
			case "9":
				return "Skiing";
			case "10":
				return "Snowboarding";
			case "11":
				return "Motorbike";
			case "13":
				return "Snowshoeing";
			case "14":
				return "Treadmill Run";
			case "15":
				return "Ergometer";
			case "16":
				return "Elliptical";
			case "17":
				return "Rowing";
			case "18":
				return "Swimming";
			case "19":
				return "Walk";
			case "20":
				return "Riding";
			case "21":
				return "Golfing";
			case "22":
				return "Race Cycling";
			case "23":
				return "Tennis";
			case "24":
				return "Badminton";
			case "25":
				return "Squash";
			case "26":
				return "Yoga";
			case "27":
				return "Aerobics";
			case "28":
				return "Martial Arts";
			case "29":
				return "Sailing";
			case "30":
				return "Windsurfing";
			case "31":
				return "Pilates";
			case "32":
				return "Rock Climbing";
			case "33":
				return "Frisbee";
			case "34":
				return "Strength Training";
			case "35":
				return "Volleyball";
			case "36":
				return "Handbike";
			case "37":
				return "Cross Skating";
			case "38":
				return "Soccer";
			case "42":
				return "Surfing";
			case "43":
				return "Kitesurfing";
			case "44":
				return "Kayaking";
			case "45":
				return "Basketball";
			case "46":
				return "Spinning";
			case "47":
				return "Paragliding";
			case "48":
				return "Wakeboarding";
			case "50":
				return "Diving";
			case "51":
				return "Table Tennis";
			case "52":
				return "Handball";
			case "53":
				return "Back-country skiing";
			case "54":
				return "Ice Skating";
			case "55":
				return "Sledding";
			case "58":
				return "Curling";
			case "60":
				return "Biathlon";
			case "61":
				return "Kite Skiing";
			case "62":
				return "Speed Skiing";
			case "63":
				return "PushUps";
			case "64":
				return "SitUps";
			case "65":
				return "PullUps";
			case "66":
				return "Squats";
			case "67":
				return "American Football";
			case "68":
				return "Baseball";
			case "69":
				return "Crossfit";
			case "70":
				return "Dancing";
			case "71":
				return "Ice Hockey";
			case "72":
				return "Skateboarding";
			case "73":
				return "Zumba";
			case "74":
				return "Gymnastics";
			case "75":
				return "Rugby";
			case "76":
				return "Standup Paddling";
			case "77":
				return "Sixpack";
			case "78":
				return "Butt Training";
			case "80":
				return "Leg Training";
			case "81":
				return "Results Workout";
			case "82":
				return "Trail Running";
			case "84":
				return "Plogging";
			case "85":
				return "Wheelchair";
			case "86":
				return "E Biking";
			case "87":
				return "Scootering";
			case "88":
				return "Rowing Machine";
			case "89":
				return "Stair Climbing";
			case "90":
				return "Jumping Rope";
			case "91":
				return "Trampoline";
			case "92":
				return "Bodyweight Training";
			case "93":
				return "Tabata";
			case "94":
				return "Callisthenics";
			case "95":
				return "Suspension Training";
			case "96":
				return "Powerlifting";
			case "97":
				return "Olympic Weightlifting";
			case "98":
				return "Stretching";
			case "99":
				return "Mediation";
			case "100":
				return "Bouldering";
			case "101":
				return "Via Ferrata";
			case "102":
				return "Pade";
			case "103":
				return "Pole Dancing";
			case "104":
				return "Boxing";
			case "105":
				return "Cricket";
			case "106":
				return "Field Hockey";
			case "107":
				return "Track Field";
			case "108":
				return "Fencing";
			case "109":
				return "Skydiving";
			case "111":
				return "Cheerleading/E-Sports";
			case "112":
				return "E-Sports";
			case "113":
				return "Lacrosse";
			case "114":
				return "Beach Volleyball";
			case "115":
				return "Virtual Running";
			case "116":
				return "Virtual Cycling";
		}
		return "Other";
	}

	public static String mapPartOfDay(Date date)
	{
		int hours = date.getHours();
		if (hours < 2)
			return "Midnight";
		if (hours < 6)
			return "Early Morning";
		if (hours < 9)
			return "Morning";
		if (hours < 12)
			return "Late Morning";
		if (hours < 17)
			return "Afternoon";
		if (hours < 19)
			return "Early Evening";
		if (hours < 21)
			return "Evening";
		return "Late Evening";
	}

	public static String mapToStravaSportType(String id)
	{
		switch (id)
		{
			case "1":
				return "Run";
			case "2":
				return "Walk";
			case "3":
			case "11":
			case "20":
				return "Ride";
			case "4":
			case "22":
				return "MountainBikeRide";
			case "5":
				return "Other";
			case "6":
				return "InlineSkate";
			case "7":
				return "Hike";
			case "8":
				return "AlpineSki";
			case "9":
				return "NordicSki";
			case "10":
				return "Snowboard";
			case "13":
				return "Snowshoe";
			case "14":
			case "15":
				return "Workout";
			case "16":
				return "Elliptical";
			case "17":
				return "Rowing";
			case "18":
				return "Swim";
			case "19":
				return "Walk";
			case "21":
				return "Golf";
			case "23":
				return "Workout";
			case "24":
				return "Workout";
			case "25":
				return "Workout";
			case "26":
				return "Yoga";
			case "27":
				return "Workout";
			case "28":
				return "Workout";
			case "29":
				return "Sail";
			case "30":
				return "Windsurf";
			case "31":
				return "Workout";
			case "32":
				return "RockClimbing";
			case "33":
				return "Workout";
			case "34":
				return "WeightTraining";
			case "35":
				return "Workout";
			case "36":
				return "Handcycle";
			case "37":
				return "IceSkate";
			case "38":
				return "Soccer";
			case "42":
				return "Surfing";
			case "43":
				return "Kitesurf";
			case "44":
				return "Sail";
			case "45":
				return "Workout";
			case "46":
				return "Workout";
			case "47":
				return "Workout";
			case "48":
				return "Workout";
			case "50":
				return "Swim";
			case "51":
				return "Workout";
			case "52":
				return "Workout";
			case "53":
				return "BackcountrySki";
			case "54":
				return "IceSkate";
			case "55":
				return "Workout";
			case "58":
				return "Workout";
			case "60":
				return "Workout";
			case "61":
				return "NordicSki";
			case "62":
				return "NordicSki";
			case "63":
				return "Workout";
			case "64":
				return "Workout";
			case "65":
				return "Workout";
			case "66":
				return "Workout";
			case "67":
				return "Workout";
			case "68":
				return "Workout";
			case "69":
				return "Crossfit";
			case "70":
				return "Workout";
			case "71":
				return "Workout";
			case "72":
				return "Skateboard";
			case "73":
				return "Workout";
			case "74":
				return "Workout";
			case "75":
				return "Workout";
			case "76":
				return "Workout";
			case "77":
				return "Workout";
			case "78":
				return "Workout";
			case "80":
				return "Workout";
			case "81":
				return "Workout";
			case "82":
				return "TrailRun";
			case "84":
				return "Workout";
			case "85":
				return "Workout";
			case "86":
				return "EBikeRide";
			case "87":
				return "Workout";
			case "88":
				return "Rowing";
			case "89":
				return "StairStepper";
			case "90":
				return "Workout";
			case "91":
				return "Workout";
			case "92":
				return "Workout";
			case "93":
				return "Workout";
			case "94":
				return "Workout";
			case "95":
				return "Workout";
			case "96":
				return "Workout";
			case "97":
				return "Workout";
			case "98":
				return "Workout";
			case "99":
				return "Workout";
			case "100":
				return "Workout";
			case "101":
				return "Workout";
			case "102":
				return "Workout";
			case "103":
				return "Workout";
			case "104":
				return "Workout";
			case "105":
				return "Workout";
			case "106":
				return "Workout";
			case "107":
				return "Workout";
			case "108":
				return "Workout";
			case "109":
				return "Workout";
			case "111":
				return "Workout";
			case "112":
				return "Workout";
			case "113":
				return "Workout";
			case "114":
				return "Workout";
			case "115":
				return "VirtualRun";
			case "116":
				return "VirtualRide";
		}
		return "Workout";
	}

	private static final int		STRAVA_15M_RATE_LIMIT		= 100;
	private static final int		UNIFORM_INTER_UPLOAD_WAIT	= 15 * 60 * 1000 / STRAVA_15M_RATE_LIMIT;
	private static final MediaType	MEDIA_TYPE_GZIP				= MediaType.parse("application/gzip");

	private void doUploadStrava(Path convertedFolder, String clientID, String clientSecret, String code) throws FileNotFoundException, IOException
	{
		if (!Files.exists(convertedFolder) || !Files.isDirectory(convertedFolder))
			throw new FileNotFoundException("No such directory '" + convertedFolder.toString() + "'");

		File uploadedFolder = convertedFolder.resolve("uploaded").toFile();

		long startTime = System.nanoTime();
		System.out.println("Uploading converted activities to Strava");

		int total = (int) Files.list(convertedFolder)
			.parallel()
			.filter(f -> "meta".equals(FilenameUtils.getExtension(f.getFileName().toString())))
			.count();

		System.out.println(" o Found " + total + " activities to be uploaded");
		OkHttpClient client = new OkHttpClient();
		ObjectMapper mapper = converter.parser.mapper;

		AccessTokenResponse accessToken;
		Path accessTokenFile = convertedFolder.resolve("access.token");
		if (Files.exists(accessTokenFile))
		{
			System.out.println(" o Found a previously saved Strava acceess token");
			accessToken = converter.parser.mapper.readValue(accessTokenFile.toFile(), AccessTokenResponse.class);
			accessToken = checkAndRefreshAccessToken(client, mapper, accessTokenFile, accessToken, clientID, clientSecret);
		}
		else
		{
			System.out.println(" o Obtaining access token from Strava ...");

			FormBody formBody = new FormBody.Builder()
				.add("client_id", clientID)
				.add("client_secret", clientSecret)
				.add("code", code)
				.add("grant_type", "authorization_code")
				.build();

			Request request = new Request.Builder()
				.url("https://www.strava.com/api/v3/oauth/token")
				.post(formBody)
				.build();

			try (Response response = client.newCall(request).execute())
			{
				accessToken = mapper.readValue(handleFailure(mapper, response, clientID), AccessTokenResponse.class);
			}
			mapper.writeValue(accessTokenFile.toFile(), accessToken);
			System.out.println("   - Obtained and saved access token");
		}

		AtomicBoolean stopNotified = new AtomicBoolean();
		AtomicInteger counter = new AtomicInteger();
		AtomicInteger throttleCounter = new AtomicInteger();
		AccessTokenResponse[] finalAccessToken = new AccessTokenResponse[1];
		finalAccessToken[0] = accessToken;
		int rtotal = (int) Files.list(convertedFolder)
			.parallel()
			.filter(f -> !stopNotified.get() && "meta".equals(FilenameUtils.getExtension(f.getFileName().toString())))
			.map(new Function<Path, Path>()
			{

				Path metaDataFile;

				private boolean throttle()
				{
					int failures = throttleCounter.incrementAndGet();
					int c = counter.get();
					if (failures > 3)
					{
						stopNotified.set(true);
						System.err.println("   - Upload terminated at " + c + " uploads (" + metaDataFile.getFileName() + ") since daily upload limit is reached");
						return false;
					}
					Duration duration = Duration.ofNanos(System.nanoTime() - startTime);
					double per15m = (duration.getSeconds() == 0 ? 0d : c / ((double) duration.getSeconds())) * 60 * 15;
					int pause = (15 - (int) (duration.toMinutes() % 15)) + 1;
					System.out.println("   - Waiting after " + c + " uploads for " + pause + "m to adjust current upload rate of " + (int) per15m + "/15m");
					try
					{
						Thread.sleep(pause * 60 * 1000);
					}
					catch (Exception ex)
					{}
					return true;
				}

				@Override
				public Path apply(Path metaDataFile)
				{
					if (stopNotified.get())
						return null;
					this.metaDataFile = metaDataFile;
					while (true)
					{
						try
						{
							finalAccessToken[0] = checkAndRefreshAccessToken(client, mapper, accessTokenFile, finalAccessToken[0], clientID, clientSecret);
						}
						catch (InvalidActivityException ex)
						{
							if (throttle())
								continue;
							stopNotified.set(true);
							ex.printStackTrace();
							return null;
						}
						catch (Exception ex)
						{
							if (throttle())
								continue;
							stopNotified.set(true);
							ex.printStackTrace();
							return null;
						}

						try
						{
							ExportMetadata metaData = mapper.readValue(metaDataFile.toFile(), ExportMetadata.class);

							File activityFile = metaDataFile.resolveSibling(metaData.fileName).toFile();
							if (!activityFile.exists())
							{
								System.out.println("   - Skipping " + metaDataFile);
								return null;
							}

							Builder postBodyBuilder = new MultipartBody.Builder()
								.addFormDataPart("name", metaData.name)
								.addFormDataPart("description", metaData.description)
								.addFormDataPart("external_id", metaData.externalId)
								.addFormDataPart("sport_type", metaData.sportType)
								.addFormDataPart("data_type", metaData.dataType)
								.addFormDataPart("commute", "false")
								.addFormDataPart("trainer", "false")
								.addFormDataPart("trainer", "false")
								.addFormDataPart("file", metaData.fileName, RequestBody.create(activityFile, MEDIA_TYPE_GZIP));
							if (metaData.gearId != null)
								postBodyBuilder.addFormDataPart("gear_id", metaData.gearId);
							Request request = new Request.Builder()
								.url("https://www.strava.com/api/v3/uploads")
								.addHeader("Authorization", finalAccessToken[0].token_type + " " + finalAccessToken[0].access_token)
								.post(postBodyBuilder.build())
								.build();

							try (Response response = client.newCall(request).execute())
							{
								handleFailure(mapper, response, clientID);
							}
							throttleCounter.set(0);

							FileUtils.moveFileToDirectory(metaDataFile.toFile(), uploadedFolder, true);
							FileUtils.moveFileToDirectory(activityFile, uploadedFolder, false);
							File rawActivityFile = metaDataFile.resolveSibling(FilenameUtils.getBaseName(activityFile.getName())).toFile();
							if (rawActivityFile.exists())
								FileUtils.moveFileToDirectory(rawActivityFile, uploadedFolder, false);

							int c = counter.incrementAndGet();
							if (c % 5 == 0)
							{
								Duration duration = Duration.ofNanos(System.nanoTime() - startTime);
								double per15m = (duration.getSeconds() == 0 ? 0d : c / ((double) duration.getSeconds())) * 60 * 15;
								System.out.println("   - " + ZonedDateTime.now() + ": Uploaded " + c + " / " + total + " (" + (c * 100 / total) + "%) activities (" + ((int) per15m) + "/15m)");
							}
						}
						catch (AuthenticationException ex)
						{
							stopNotified.set(true);
							ex.printStackTrace();
							return null;
						}
						catch (InvalidActivityException ex)
						{
							if (throttle())
								continue;
							stopNotified.set(true);
							ex.printStackTrace();
							return null;
						}
						catch (Exception ex)
						{
							if (throttle())
								continue;
							stopNotified.set(true);
							ex.printStackTrace();
							return null;
						}
						return metaDataFile;
					}
				}
			})
			.filter(f -> f != null)
			.count();

		System.out.println(rtotal + " activities successfully uploaded in " + Duration.ofNanos(System.nanoTime() - startTime));
	}

	private static String handleFailure(ObjectMapper mapper, Response response, String clientID) throws IOException
	{
		try (ResponseBody body = response.body())
		{
			String bodyString = body.string().trim();
			if (response.isSuccessful())
				return bodyString;
			FailureResponse errorResponse = null;
			try
			{
				errorResponse = mapper.readValue(bodyString, FailureResponse.class);
			}
			catch (Exception ex)
			{}
			if (errorResponse == null)
				throw new IOException("HTTP failure (" + response.code() + ")");
			if ("Rate Limit Exceeded".equalsIgnoreCase(errorResponse.message))
				throw new InvalidActivityException("Strava API rate limit reached");
			if ("Bad Request".equalsIgnoreCase(errorResponse.message) &&
				errorResponse.errors != null && errorResponse.errors.length != 0
				&& "AuthorizationCode".equalsIgnoreCase(errorResponse.errors[0].resource)
				&& "code".equalsIgnoreCase(errorResponse.errors[0].field))
			{
				URI oauthUrl = null;
				try
				{
					oauthUrl = new URI("https://www.strava.com/oauth/authorize?client_id=" + clientID + "&redirect_uri=https://localhost/response&response_type=code&scope=read,read_all,profile:read_all,profile:write,activity:read_all,activity:write");
					Desktop.getDesktop().browse(oauthUrl);
				}
				catch (URISyntaxException ex)
				{
					// TODO Auto-generated catch block
					ex.printStackTrace();
				}
				throw new AuthenticationException("Supplied Strava OAuth authentication 'code' is not valid. Please acquire a new one:\r\n" + oauthUrl);
			}
			throw new IOException("Strava API failure '" + errorResponse.message + "' (" + response.code() + ")");
		}
	}

	private static synchronized AccessTokenResponse checkAndRefreshAccessToken(OkHttpClient client, ObjectMapper mapper, Path accessTokenFile, AccessTokenResponse accessToken, String clientID, String clientSecret) throws IOException
	{
		if (!accessToken.isExpired())
			return accessToken;
		System.out.println("   - Access token is expired and needs a refresh");
		accessToken = refreshAccessToken(client, mapper, accessTokenFile, accessToken, clientID, clientSecret);
		System.out.println("   - Refreshed and saved access token");
		return accessToken;
	}

	private static AccessTokenResponse refreshAccessToken(OkHttpClient client, ObjectMapper mapper, Path accessTokenFile, AccessTokenResponse accessToken, String clientID, String clientSecret) throws IOException
	{
		FormBody formBody = new FormBody.Builder()
			.add("client_id", clientID)
			.add("client_secret", clientSecret)
			.add("refresh_token", accessToken.refresh_token)
			.add("grant_type", "refresh_token")
			.build();

		Request request = new Request.Builder()
			.url("https://www.strava.com/api/v3/oauth/token")
			.post(formBody)
			.build();

		try (Response response = client.newCall(request).execute())
		{
			accessToken = mapper.readValue(handleFailure(mapper, response, clientID), AccessTokenResponse.class);
		}
		catch (Exception ex)
		{
			try
			{
				Files.deleteIfExists(accessTokenFile);
			}
			catch (Exception ex1)
			{}
			throw ex;
		}

		mapper.writeValue(accessTokenFile.toFile(), accessToken);

		return accessToken;
	}
}

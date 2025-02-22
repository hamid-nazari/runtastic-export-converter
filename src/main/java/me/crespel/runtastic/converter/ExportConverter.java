package me.crespel.runtastic.converter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.io.filefilter.SuffixFileFilter;

import com.topografix.gpx._1._1.BoundsType;

import me.crespel.runtastic.RuntasticExportConverter;
import me.crespel.runtastic.mapper.DelegatingSportSessionMapper;
import me.crespel.runtastic.mapper.SportSessionMapper;
import me.crespel.runtastic.model.Shoe;
import me.crespel.runtastic.model.SportSession;
import me.crespel.runtastic.model.User;
import me.crespel.runtastic.parser.SportSessionParser;
import me.crespel.strava.model.ExportMetadata;

/**
 * Export directory converter.
 *
 * @author Fabien CRESPEL (fabien@crespel.net)
 * @author Christian IMFELD (imfeldc@gmail.com)
 */
public class ExportConverter
{

	public BigDecimal diff = new BigDecimal(0.0005); // max. allowed "deviation" between bounds of sessions

	public static final String	SPORT_SESSIONS_DIR				= "Sport-sessions";
	public static final String	PHOTOS_DIR						= "Photos";
	public static final String	PHOTOS_META_DATA_DIR			= "Photos" + File.separator + "Images-meta-data";
	public static final String	PHOTOS_SPORT_SESSION_ALBUMS_DIR	= "Photos" + File.separator + "Images-meta-data" + File.separator + "Sport-session-albums";
	public static final String	USER_DIR						= "User";
	public static final String	DEFAULT_FORMAT					= "tcx";
	public static final String	SHOES_DIR						= USER_DIR + File.separator + "Shoes";
	public static final String	GEAR_MAP						= SHOES_DIR + File.separator + "gear_map.properties";

	public final SportSessionParser			parser	= new SportSessionParser();
	protected final SportSessionMapper<?>	mapper	= new DelegatingSportSessionMapper();

	public List<SportSession> listSportSessions(File path, boolean full) throws FileNotFoundException, IOException
	{
		return Files.list(normalizeExportPath(path, SPORT_SESSIONS_DIR).toPath())
			.filter(file -> file.getFileName().toString().endsWith(".json"))
			.parallel()
			.map(file -> {
				try
				{
					return parser.parseSportSession(file.toFile(), full);
				}
				catch (IOException ex)
				{
					ex.printStackTrace();
					return null;
				}
			})
			.filter(s -> s != null)
			.sorted()
			.collect(Collectors.toList());
	}

	public User getUser(File path) throws FileNotFoundException, IOException
	{
		return parser.parseUser(getUserFile(path));
	}

	public SportSession getSportSession(File path, String id) throws FileNotFoundException, IOException
	{
		return parser.parseSportSession(new File(normalizeExportPath(path, SPORT_SESSIONS_DIR), id + ".json"), true);
	}

	public List<SportSession> convertSportSessions(File path, String format) throws FileNotFoundException, IOException
	{
		return Files.list(normalizeExportPath(path, SPORT_SESSIONS_DIR).toPath())
			.filter(p -> p.getFileName().toString().endsWith(".json"))
			.parallel()
			.map(file -> {
				try
				{
					return parser.parseSportSession(file.toFile(), true);
				}
				catch (IOException ex)
				{
					ex.printStackTrace();
					return null;
				}
			})
			.filter(s -> s != null)
			.map(session -> {
				if (session.getGpsData() != null || session.getHeartRateData() != null || session.getGpx() != null)
					mapper.mapSportSession(session, format);
				return session;
			}).collect(Collectors.toList());
	}

	public void exportSportSession(SportSession session, File dest, String format) throws FileNotFoundException, IOException
	{
		if (dest.isDirectory())
		{
			dest = new File(dest, buildFileName(session, format));
		}
		mapper.mapSportSession(session, format, dest);
	}

	public void exportSportSession(File path, String id, File dest, String format) throws FileNotFoundException, IOException
	{
		SportSession session = parser.parseSportSession(new File(normalizeExportPath(path, SPORT_SESSIONS_DIR), id + ".json"), true);
		exportSportSession(session, dest, format);
	}

	public int exportSportSessions(File path, File dest, String format, boolean withMetadata) throws FileNotFoundException, IOException
	{
		if (dest.exists() && !dest.isDirectory())
			throw new IllegalArgumentException("Destination '" + dest + "' is not a valid directory");
		boolean autoFormat = "auto".equalsIgnoreCase(format);
		dest.mkdirs();
		File gearMapFile = new File(path, GEAR_MAP);
		Properties gearMap = null;
		Map<String, Shoe> activityToShoeMap = null;
		if (gearMapFile.exists())
		{
			System.out.println(" + Found gear mapping file at '" + gearMapFile + "'");
			gearMap = new Properties();
			try (BufferedReader rdr = Files.newBufferedReader(gearMapFile.toPath()))
			{
				gearMap.load(rdr);
			}
			if (!gearMap.isEmpty())
				System.out.println(" + Loaded " + gearMap.size() + " gear mappings");
		}
		List<Shoe> shoes = Files.list(normalizeExportPath(path, SHOES_DIR).toPath())
			.filter(p -> p.getFileName().toString().endsWith(".json"))
			.map(p -> {
				try
				{
					return parser.parseShoe(p.toFile());
				}
				catch (IOException ex)
				{
					ex.printStackTrace();
					return null;
				}
			})
			.filter(shoe -> shoe != null)
			.collect(Collectors.toList());
		if (!shoes.isEmpty())
		{
			System.out.println(" + Found " + shoes.size() + " shoe(s)");
			activityToShoeMap = new HashMap<>();
			for (Shoe s : shoes)
			{
				for (String activityID : s.samplesIds)
					activityToShoeMap.put(activityID, s);
			}
			if (activityToShoeMap.isEmpty())
				activityToShoeMap = null;
			else
				System.out.println(" + Found " + activityToShoeMap.size() + " activities with shoe");
		}
		Map<String, Shoe> activityToShoeMapFinal = activityToShoeMap;
		Properties gearMapFinal = gearMap;
		int total = (int) Files.list(normalizeExportPath(path, SPORT_SESSIONS_DIR).toPath())
			.filter(p -> p.getFileName().toString().endsWith(".json"))
			.count();
		AtomicInteger counter = new AtomicInteger();
		return (int) Files.list(normalizeExportPath(path, SPORT_SESSIONS_DIR).toPath())
			.filter(p -> p.getFileName().toString().endsWith(".json"))
			.parallel()
			.map(file -> {
				try
				{
					return parser.parseSportSession(file.toFile(), true);
				}
				catch (IOException ex)
				{
					ex.printStackTrace();
					return null;
				}
			})
			.filter(s -> s != null)
			.map(session -> {
				ZonedDateTime now = ZonedDateTime.now();
				String effFormat = format;
				if (autoFormat)
				{
					if (session.distance > 0 && (session.gpx != null || session.gpsData != null))
						effFormat = "gpx";
					else
						effFormat = "tcx";
				}
				String fileName = buildFileName(session, effFormat);
				Path activityFile = new File(dest, fileName).toPath();
				mapper.mapSportSession(session, effFormat, activityFile.toFile());
				if (withMetadata)
				{
					ExportMetadata metaData = new ExportMetadata();
					metaData.name = RuntasticExportConverter.mapPartOfDay(session.startTime) + " " + RuntasticExportConverter.mapSportType(session.sportTypeId);
					metaData.externalId = session.id;
					metaData.description = "Imported from Adidas Running (Runtastic) at " + now + " through my automated script (original:" + session.id + ")";
					metaData.sportType = RuntasticExportConverter.mapToStravaSportType(session.sportTypeId);
					metaData.dataType = effFormat;
					try
					{
						activityFile = gzip(activityFile);
						metaData.dataType += ".gz";
					}
					catch (IOException ex)
					{
						ex.printStackTrace();
					}
					metaData.fileName = activityFile.getFileName().toString();
					if (activityToShoeMapFinal != null)
					{
						Shoe shoe = activityToShoeMapFinal.get(session.id);
						if (shoe != null)
						{
							metaData.shoe = shoe.id;
							if (gearMapFinal != null)
							{
								String gearID = gearMapFinal.getProperty(shoe.id);
								if (gearID != null)
									metaData.gearId = gearID;
							}
						}
					}
					try
					{
						this.parser.mapper.writeValue(dest.toPath().resolve(fileName + ".meta").toFile(), metaData);
					}
					catch (Exception ex)
					{
						ex.printStackTrace();
					}
				}
				int c = counter.incrementAndGet();
				if (c % 5 == 0)
					System.out.println(ZonedDateTime.now() + " - Converted " + c + " / " + total + " (" + (c * 100 / total) + "%) sessions");
				return session;
			})
			.filter(s -> s != null)
			.count();
	}

	// Loop through all sport session and add "overlapping" session to each sport session
	public void doOverlap(List<SportSession> sessions)
	{
		// (1) search per session for all overlapping sessions
		// NOTE: This can result in different results; e.g.
		// - Session A, overlaps with B and C, but
		// - Session D, overlaps only with B and C (this because B & C are in range of D, but not of A)
		// but expected is that all mention sessions above are calculated as "overlapping"
		// This circumstance will be "normalized" in a second step.
		BoundsType bounds;
		BoundsType bounds2;
		BigDecimal diffMaxlat;
		BigDecimal diffMaxlon;
		BigDecimal diffMinlat;
		BigDecimal diffMinlon;
		List<SportSession> overlapSessions = null;
		for (SportSession session : sessions)
		{
			if (session.getGpx() == null ||
				session.getGpx().getMetadata() == null ||
				session.getGpx().getMetadata().getBounds() == null)
				continue;
			overlapSessions = null;
			for (SportSession session2 : sessions)
			{
				if (session.getId().equals(session2.getId()) ||
					!session.hasTimeOverlap(session2, 5 * 60) ||
					session2.getGpx() == null ||
					session2.getGpx().getMetadata() == null ||
					session2.getGpx().getMetadata().getBounds() == null)
					continue;

				bounds = session.getGpx().getMetadata().getBounds();
				bounds2 = session2.getGpx().getMetadata().getBounds();
				if (bounds.getMaxlat() == null || bounds2.getMaxlat() == null)
					continue;

				diffMaxlat = bounds.getMaxlat().subtract(bounds2.getMaxlat()).abs();
				diffMaxlon = bounds.getMaxlon().subtract(bounds2.getMaxlon()).abs();
				diffMinlat = bounds.getMinlat().subtract(bounds2.getMinlat()).abs();
				diffMinlon = bounds.getMinlon().subtract(bounds2.getMinlon()).abs();
				if ((diffMaxlat.compareTo(diff) < 0) &&
					(diffMaxlon.compareTo(diff) < 0) &&
					(diffMinlat.compareTo(diff) < 0) &&
					(diffMinlon.compareTo(diff) < 0))
				{
					if (overlapSessions == null)
						overlapSessions = new ArrayList<>();
					// overlapping sport session found
					overlapSessions.add(session2);
				}
			}
			if (overlapSessions != null)
				session.setOverlapSessions(overlapSessions);
		}
		// (2) Normalize overlapping sport sessions
		for (SportSession session : sessions)
		{
			if (session.getOverlapSessions() == null)
				continue;
			List<SportSession> normalizedOverlapSessions = new ArrayList<>();
			for (SportSession overlapSession : session.getOverlapSessions())
				addOverlapSessions(normalizedOverlapSessions, overlapSession);
			session.setOverlapSessions(normalizedOverlapSessions);
			// (3) Calculate inner and outer bound (of normalized overlapping sessions)
			calculateInnerAndOuterBound(session);
		}
	}

	private void addOverlapSessions(List<SportSession> normalizedOverlapSessions, SportSession overlapSession)
	{
		if ((normalizedOverlapSessions != null) && (overlapSession.getOverlapSessions() != null))
		{
			for (SportSession innerOverlapSession : overlapSession.getOverlapSessions())
			{
				if ((innerOverlapSession != null) && (!normalizedOverlapSessions.contains(innerOverlapSession)))
				{
					normalizedOverlapSessions.add(innerOverlapSession);
					addOverlapSessions(normalizedOverlapSessions, innerOverlapSession);
				}
			}
		}
	}

	public void calculateInnerAndOuterBound(SportSession session)
	{
		if (session.getOverlapSessions() != null)
		{
			BoundsType innerBounds = null;
			BoundsType outerBounds = null;
			for (SportSession overlapSession : session.getOverlapSessions())
			{
				BoundsType sessionBounds = overlapSession.getGpx().getMetadata().getBounds();;
				if ((innerBounds == null) && (outerBounds == null))
				{
					// init bounds with "any" existing bounds from sessions
					innerBounds = new BoundsType();
					innerBounds.setMaxlat(sessionBounds.getMaxlat());
					innerBounds.setMinlat(sessionBounds.getMinlat());
					innerBounds.setMaxlon(sessionBounds.getMaxlon());
					innerBounds.setMinlon(sessionBounds.getMinlon());
					outerBounds = new BoundsType();
					outerBounds.setMaxlat(sessionBounds.getMaxlat());
					outerBounds.setMinlat(sessionBounds.getMinlat());
					outerBounds.setMaxlon(sessionBounds.getMaxlon());
					outerBounds.setMinlon(sessionBounds.getMinlon());
				}
				else
				{
					// calculate "left" side of inner bounds ...
					innerBounds.setMinlon(sessionBounds.getMinlon().max(innerBounds.getMinlon()));
					// calculate "right" side of inner bounds ...
					innerBounds.setMaxlon(sessionBounds.getMaxlon().min(innerBounds.getMaxlon()));
					// caluclate "top" side of inner bounds ...
					innerBounds.setMaxlat(sessionBounds.getMaxlat().min(innerBounds.getMaxlat()));
					// calculate "lower" side of inner bounds ...
					innerBounds.setMinlat(sessionBounds.getMinlat().max(innerBounds.getMinlat()));
					// calculate "left" side of outer bounds ...
					outerBounds.setMinlon(sessionBounds.getMinlon().min(outerBounds.getMinlon()));
					// caluclate "right" side of outer bounds ...
					outerBounds.setMaxlon(sessionBounds.getMaxlon().max(outerBounds.getMaxlon()));
					// calculate "top" side of outer bounds ...
					outerBounds.setMaxlat(sessionBounds.getMaxlat().max(outerBounds.getMaxlat()));
					// calculate "lower" side of outer bounds ...
					outerBounds.setMinlat(sessionBounds.getMinlat().min(outerBounds.getMinlat()));
				}
			}
			// Store inner and outer bounds in sport session
			session.setInnerBound(innerBounds);
			session.setOuterBound(outerBounds);
		}
	}

	// Loop through all sport session and search for "adjuncted" sessions
	public void doCompound(List<SportSession> sessions)
	{
		// calculate overlapping sessions, as those are not considered as "compound" session
		doOverlap(sessions);

		// (1) search per session for all "adjuncted sessions
		for (SportSession session : sessions)
		{
			if (session.getGpx() != null && session.getGpx().getMetadata() != null && session.getGpx().getMetadata().getBounds() != null)
			{
				List<SportSession> compoundSessions = new ArrayList<>();
				for (SportSession session2 : sessions)
				{
					if (!session.getId().equals(session2.getId()))
					{
						if ((session.getOverlapSessions() == null) ||
							((session.getOverlapSessions() != null) && (!session.getOverlapSessions().contains(session2))))
						{
							// process session only if it isn't an "overlapping" session
							if (isCompound(session, session2))
							{
								// compound sport session found
								compoundSessions.add(session2);
							}
						}
					}
				}
				if (compoundSessions.size() > 0)
				{
					session.setCompoundSessions(compoundSessions);
				}
			}
		}
		// (2) Normalize compound sport sessions (add all compound sessions to one "chain")
		for (SportSession session : sessions)
		{
			if (session.getCompoundSessions() != null)
			{
				List<SportSession> normalizedCompoundSessions = new ArrayList<>();
				for (SportSession compoundSession : session.getCompoundSessions())
				{
					addCompoundSessions(normalizedCompoundSessions, compoundSession);
				}
				session.setCompoundSessions(normalizedCompoundSessions);
			}
		}
	}

	public boolean isCompound(SportSession session, SportSession session2)
	{
		if ((session2.getGpx() != null && session2.getGpx().getMetadata() != null && session2.getGpx().getMetadata().getBounds() != null))
		{
			BoundsType bounds = session.getGpx().getMetadata().getBounds();
			BoundsType bounds2 = session2.getGpx().getMetadata().getBounds();
			BigDecimal diffTop = bounds.getMaxlat().subtract(bounds2.getMinlat()).abs();
			BigDecimal diffRight = bounds.getMaxlon().subtract(bounds2.getMinlon()).abs();
			BigDecimal diffDown = bounds.getMinlat().subtract(bounds2.getMaxlat()).abs();
			BigDecimal diffLeft = bounds.getMinlon().subtract(bounds2.getMaxlon()).abs();
			if (((diffTop.compareTo(diff) < 0) && (bounds.getMinlon().compareTo(bounds2.getMaxlon()) <= 0) && (bounds.getMaxlon().compareTo(bounds2.getMinlon()) >= 0))
				|| ((diffRight.compareTo(diff) < 0) && (bounds.getMinlat().compareTo(bounds2.getMaxlat()) <= 0) && (bounds.getMaxlat().compareTo(bounds2.getMinlat()) >= 0))
				|| ((diffDown.compareTo(diff) < 0) && (bounds.getMinlon().compareTo(bounds2.getMaxlon()) <= 0) && (bounds.getMaxlon().compareTo(bounds2.getMinlon()) >= 0))
				|| ((diffLeft.compareTo(diff) < 0) && (bounds.getMinlat().compareTo(bounds2.getMaxlat()) <= 0) && (bounds.getMaxlat().compareTo(bounds2.getMinlat()) >= 0)))
			{
				// compound sport session found
				return true;
			}
		}
		return false;
	}

	private void addCompoundSessions(List<SportSession> normalizedCompoundSessions, SportSession compoundSession)
	{
		if ((normalizedCompoundSessions != null) && (compoundSession.getCompoundSessions() != null))
		{
			for (SportSession innerCompoundSession : compoundSession.getCompoundSessions())
			{
				if ((innerCompoundSession != null) && (!normalizedCompoundSessions.contains(innerCompoundSession)))
				{
					normalizedCompoundSessions.add(innerCompoundSession);
					addCompoundSessions(normalizedCompoundSessions, innerCompoundSession);
				}
			}
		}
	}

	protected static File normalizeExportPath(File path, String subpath)
	{
		// check if "Sport Session" sub-directory is provided ...
		if (SPORT_SESSIONS_DIR.equals(path.getName()))
		{
			// if yes, remove them.
			path = path.getParentFile();
		}
		// check if already path including sub-path is provided ...
		if (!subpath.equals(path.getName()))
		{
			// if not, add sub-path to path.
			path = new File(path, subpath);
		}
		if (!path.isDirectory())
		{
			throw new IllegalArgumentException("Export path '" + path + "' is not a valid directory");
		}
		return path;
	}

	protected String buildFileName(SportSession session, String format)
	{
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
		return new StringBuilder("runtastic_")
			.append(sdf.format(session.getStartTime()))
			.append('_')
			.append(session.getId())
			.append('.')
			.append(format != null ? format : DEFAULT_FORMAT)
			.toString();
	}

	public static File getUserFile(File path) throws FileNotFoundException
	{
		path = normalizeExportPath(path, USER_DIR);
		File[] files = path.listFiles((FilenameFilter) new SuffixFileFilter("user.json"));
		if (files.length == 0)
			throw new FileNotFoundException("Can't find a user.json file");
		return files[0];
	}

	public static Path gzip(Path input) throws IOException
	{
		Path compressedFile = input.resolveSibling(input.getFileName().toString() + ".gz");
		try (InputStream is = Files.newInputStream(input);
			OutputStream os = Files.newOutputStream(compressedFile);
			GZIPOutputStream gzo = new GZIPOutputStream(os))
		{
			byte[] buffer = new byte[1024];
			int len;
			while ((len = is.read(buffer)) != -1)
				gzo.write(buffer, 0, len);
		}
		return compressedFile;
	}
}

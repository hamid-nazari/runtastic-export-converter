package me.crespel.runtastic.parser;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.apache.commons.io.FilenameUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.topografix.gpx._1._1.GpxType;

import me.crespel.runtastic.converter.ExportConverter;
import me.crespel.runtastic.model.ElevationData;
import me.crespel.runtastic.model.GpsData;
import me.crespel.runtastic.model.HeartRateData;
import me.crespel.runtastic.model.ImageMetaData;
import me.crespel.runtastic.model.Shoe;
import me.crespel.runtastic.model.SportSession;
import me.crespel.runtastic.model.User;

/**
 * Sport session parser.
 * This class reads sport sessions and related data exported as JSON.
 *
 * @author Fabien CRESPEL (fabien@crespel.net)
 * @author Christian IMFELD (imfeldc@gmail.com)
 */
public class SportSessionParser
{

	public static final String	ELEVATION_DATA_DIR		= "Elevation-data";
	public static final String	GPS_DATA_DIR			= "GPS-data";
	public static final String	HEARTRATE_DATA_DIR		= "Heart-rate-data";
	public static final String	PHOTOS_DIR				= "Photos";
	public static final String	PHOTOS_META_DATA_DIR	= PHOTOS_DIR + File.separator + "Images-meta-data";
	public static final String	USER_DIR				= "User";

	protected final ObjectMapper mapper = new ObjectMapper();

	private static Map<String, List<ImageMetaData>> imagesCache;

	private Map<String, List<ImageMetaData>> buildImagesCache(File exportRoot) throws IOException
	{
		Map<String, Path> photoIndex = Files.list(exportRoot.toPath().resolve(PHOTOS_DIR))
			.parallel()
			.map(p -> {
				String baseName = FilenameUtils.getBaseName(p.getFileName().toString());
				int i = baseName.lastIndexOf('_');
				if (i == -1)
					return null;
				String photoId = baseName.substring(i + 1);
				return new Object[] {photoId, p};
			})
			.filter(tuple -> tuple != null)
			.collect(Collectors.toMap(tuple -> (String) tuple[0], tuple -> (Path) tuple[1]));

		Map<String, List<ImageMetaData>> result = Files.list(exportRoot.toPath().resolve(PHOTOS_META_DATA_DIR))
			.filter(p -> p.getFileName().toString().endsWith(".json"))
			.parallel()
			.map(p -> {
				try
				{
					return parseImagesMetaData(p.toFile());
				}
				catch (IOException ex)
				{
					ex.printStackTrace();
					return null;
				}
			})
			.filter(mt -> {
				if (mt == null)
					return false;
				mt.imagePath = photoIndex.get(mt.id);
				return mt.imagePath != null;
			}).collect(HashMap::new, (map, mt) -> {
				List<ImageMetaData> images = map.get(mt.sampleId);
				if (images == null)
				{
					images = new ArrayList<>();
					map.put(mt.sampleId, images);
				}
				images.add(mt);
			}, (map1, map2) -> {
				map1.putAll(map2);
			});

		result.forEach((s, l) -> Collections.sort(l));
		return result;
	}

	public SportSession parseSportSession(File file) throws FileNotFoundException, IOException
	{
		return parseSportSession(file, false);
	}

	public SportSession parseSportSession(File file, boolean full) throws FileNotFoundException, IOException
	{
		try (InputStream is = new BufferedInputStream(new FileInputStream(file)))
		{
			SportSession sportSession = parseSportSession(is);
			if (full)
			{
				File elevationDataFile = new File(new File(file.getParentFile(), ELEVATION_DATA_DIR), file.getName());
				if (elevationDataFile.exists())
				{
					sportSession.setElevationData(parseElevationData(elevationDataFile));
				}
				// read GPS data from JSON file
				File gpsDataFileJSON = new File(new File(file.getParentFile(), GPS_DATA_DIR), file.getName());
				if (gpsDataFileJSON.exists())
				{
					sportSession.setGpsData(parseGpsData(gpsDataFileJSON));
				}
				// read GPS data from GPX file (the runtastic export contains GPS data as GPX files, starting from April-2020)
				File gpsDataFileGPX = new File(new File(file.getParentFile(), GPS_DATA_DIR), FilenameUtils.getBaseName(file.getName()) + ".gpx");
				if (gpsDataFileGPX.exists())
				{
					// Load GPX file
					try
					{
						JAXBContext ctx = JAXBContext.newInstance(GpxType.class);
						Unmarshaller um = ctx.createUnmarshaller();
						JAXBElement<GpxType> root = (JAXBElement<GpxType>) um.unmarshal(gpsDataFileGPX);
						GpxType gpx = root.getValue();
						sportSession.setGpx(gpx);
					}
					catch (JAXBException e)
					{
						throw new RuntimeException(e);
					}
				}
				File heartRateDataFile = new File(new File(file.getParentFile(), HEARTRATE_DATA_DIR), file.getName());
				if (heartRateDataFile.exists())
				{
					sportSession.setHeartRateData(parseHeartRateData(heartRateDataFile));
				}
			}

			Map<String, List<ImageMetaData>> imagesCache = SportSessionParser.imagesCache;
			if (imagesCache == null)
			{
				synchronized (SportSessionParser.class)
				{
					imagesCache = SportSessionParser.imagesCache;
					if (imagesCache == null)
					{
						imagesCache = buildImagesCache(file.getParentFile().getParentFile());
						SportSessionParser.imagesCache = imagesCache;
					}
				}
			}
			sportSession.setImages(imagesCache.get(sportSession.id));

			// read and add user

			sportSession.setUser(parseUser(ExportConverter.getUserFile(new File(file.getParentFile().getParentFile(), USER_DIR))));
			return sportSession;
		}
	}

	public SportSession parseSportSession(InputStream is) throws FileNotFoundException, IOException
	{
		return mapper.readValue(is, SportSession.class);
	}

	public List<ElevationData> parseElevationData(File file) throws FileNotFoundException, IOException
	{
		try (InputStream is = new BufferedInputStream(new FileInputStream(file)))
		{
			return parseElevationData(is);
		}
	}

	public List<ElevationData> parseElevationData(InputStream is) throws FileNotFoundException, IOException
	{
		return mapper.readValue(is, new TypeReference<List<ElevationData>>()
		{});
	}

	public List<GpsData> parseGpsData(File file) throws FileNotFoundException, IOException
	{
		try (InputStream is = new BufferedInputStream(new FileInputStream(file)))
		{
			return parseGpsData(is);
		}
	}

	public List<GpsData> parseGpsData(InputStream is) throws FileNotFoundException, IOException
	{
		return mapper.readValue(is, new TypeReference<List<GpsData>>()
		{});
	}

	public List<HeartRateData> parseHeartRateData(File file) throws FileNotFoundException, IOException
	{
		try (InputStream is = new BufferedInputStream(new FileInputStream(file)))
		{
			return parseHeartRateData(is);
		}
	}

	public List<HeartRateData> parseHeartRateData(InputStream is) throws FileNotFoundException, IOException
	{
		return mapper.readValue(is, new TypeReference<List<HeartRateData>>()
		{});
	}

	public ImageMetaData parseImagesMetaData(File file) throws FileNotFoundException, IOException
	{
		try (InputStream is = new BufferedInputStream(new FileInputStream(file)))
		{
			return parseImagesMetaData(is);
		}
	}

	public ImageMetaData parseImagesMetaData(InputStream is) throws FileNotFoundException, IOException
	{
		return mapper.readValue(is, new TypeReference<ImageMetaData>()
		{});
	}

	public Shoe parseShoe(File file) throws FileNotFoundException, IOException
	{
		try (InputStream is = new BufferedInputStream(new FileInputStream(file)))
		{
			return parseShoe(is);
		}
	}

	public Shoe parseShoe(InputStream is) throws FileNotFoundException, IOException
	{
		return mapper.readValue(is, new TypeReference<Shoe>()
		{});
	}

	public User parseUser(File file) throws FileNotFoundException, IOException
	{
		try (InputStream is = new BufferedInputStream(new FileInputStream(file)))
		{
			return parseUser(is);
		}
	}

	public User parseUser(InputStream is) throws FileNotFoundException, IOException
	{
		return mapper.readValue(is, new TypeReference<User>()
		{});
	}
}

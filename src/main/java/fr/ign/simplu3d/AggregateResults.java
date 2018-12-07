package fr.ign.simplu3d;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.geotools.data.DataStore;
import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.FeatureReader;
import org.geotools.data.FileDataStoreFactorySpi;
import org.geotools.data.Transaction;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

public class AggregateResults {

	public static String PARCEL_NAME = "parcelle.shp";
	
	


	/**
	 * Aggregate the parcel results using the input data of the simulation and the
	 * statistics of the results of the simulation.
	 * 
	 * @param inputDirectory
	 *            the main (top) directory containing the input data of the
	 *            simulation
	 * @param inputCSV
	 *            the CSV file containing statistics on the simulation results
	 * @param outputFile
	 *            the output directory for the aggregate task
	 * @throws Exception
	 */
	public static void aggregateParcels(File inputDirectory, File inputCSV, File outputFile) throws Exception {
		Stream<Path> stream = Files.find(Paths.get(inputDirectory.toURI()), 5,
				(filePath, fileAttr) -> filePath.endsWith(PARCEL_NAME));
		aggregateParcels(stream.map((path) -> path.getParent().toFile()).toArray(File[]::new), inputCSV, outputFile);
		stream.close();
	}

	/**
	 * Aggregate the parcel results using the input data of the simulation and the
	 * statistics of the results of the simulation.
	 * 
	 * @param inputDirectories
	 *            the directories containing the input data of the simulation
	 * @param inputCSV
	 *            the CSV file containing statistics on the simulation results
	 * @param outputFile
	 *            the output directory for the aggregate task
	 * @throws Exception
	 */
	public static void aggregateParcels(File[] inputDirectories, File inputCSV, File outputFile) throws Exception {
		// build a map with the parcel id and the corresponding results from the CSV
		// file
		Reader in = new FileReader(inputCSV);
		Iterable<CSVRecord> records = CSVFormat.DEFAULT.withFirstRecordAsHeader().withDelimiter(';')
				.withIgnoreEmptyLines().parse(in);
		Map<String, List<String>> map = new HashMap<>();
		for (CSVRecord record : records) {
			// a proper record with all the necessary values
			if (record.size() == 5) {
				String dir = record.get(0).trim();
				String par = record.get(1).trim();
				String nbobj = record.get(2).trim();
				String floorarea = record.get(3).trim();
				String iteration = record.get(4).trim();
				map.put(par, Arrays.asList(nbobj, floorarea, dir, iteration));
			} else {
				// a record with "#" around the parcel id: an exception was thrown
				if (record.size() == 1) {
					map.put(record.get(0).replaceAll("#", "").trim(), Arrays.asList());
				} else {
					// empty line ?
					System.out.println("record with size : " + record.size());
				}
			}
		}
		in.close();
		// build a new shaphefile with the parcels from the input CSV and the
		// simulation results
		String specs = "the_geom:Polygon,id:String,imu:String,status:String,nb_objects:Integer,floor_area:Double";
		try {
			FileDataStoreFactorySpi factory = new ShapefileDataStoreFactory();
			DataStore dataStore = factory.createDataStore(outputFile.toURI().toURL());
			String featureTypeName = "Object";
			SimpleFeatureType featureType = DataUtilities.createType(featureTypeName, specs);
			dataStore.createSchema(featureType);
			Transaction transaction = new DefaultTransaction("create");
			String typeName = dataStore.getTypeNames()[0];
			SimpleFeatureSource featureSource = dataStore.getFeatureSource(typeName);
			SimpleFeatureType type = featureSource.getSchema();
			SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
			ListFeatureCollection collection = new ListFeatureCollection(featureType);
			featureStore.setTransaction(transaction);
			int i = 1;
			for (File file : inputDirectories) {
				// if the name is not numeric, it's probably the parent directory.. 
				if (!StringUtils.isNumeric(file.getName())) {
					System.out.println(" what the heck " + file.getName());
					continue;
				}
				File parcelFile = new File(file, PARCEL_NAME);
				try {
					ShapefileDataStore store = new ShapefileDataStore(parcelFile.toURI().toURL());
					FeatureReader<SimpleFeatureType, SimpleFeature> featureReader = store.getFeatureReader();
					while (featureReader.hasNext()) {
						SimpleFeature feature = featureReader.next();
						Object geom = feature.getDefaultGeometry();
						String idPar = feature.getAttribute("IDPAR").toString();
						String imu = feature.getAttribute("IDBLOCK").toString();
						List<String> list = map.get(idPar);
						String status = "NOT PROCESSED";
						int nbobj = 0;
						double area = 0;
						if (list != null) {
							if (list.isEmpty()) {
								status = "EXCEPTION DURING PROCESSING";
							} else {
								nbobj = Integer.parseInt(list.get(0));
								switch (nbobj) {
								case -1:
									status = "NO RULE FOUND";
									break;
								case -2:
									status = "TRIANGULATION ERROR";
									break;
								case -42:
									status = "FILTERED";
									break;
								case -88:
									status = "MINIMUM PARCEL AREA TOO BIG";
									break;
								default:
									status = "PROCESSED";
									area = Double.parseDouble(list.get(1));
								}
							}
						}
						Object[] values = new Object[] { geom, idPar, imu, status, nbobj, area };
						SimpleFeature simpleFeature = SimpleFeatureBuilder.build(type, values, String.valueOf(i++));
						collection.add(simpleFeature);
					}
					featureReader.close();
					store.dispose();
				} catch (MalformedURLException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			try {
				featureStore.addFeatures(collection);
				transaction.commit();
			} catch (Exception problem) {
				problem.printStackTrace();
				transaction.rollback();
			} finally {
				transaction.close();
				dataStore.dispose();
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (SchemaException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Aggregate the simulation results (buildings) into a unique building file.
	 * 
	 * @param inputDirectory
	 *            the main (top) directory containing the output data of the
	 *            simulation
	 * @param outputFile
	 *            the output directory for the aggregate task
	 * @throws Exception
	 */
	public static void aggregateBuildings(File inputDirectory, File outputFile) throws Exception {
		Stream<Path> stream = Files.find(Paths.get(inputDirectory.toURI()), 100,
				(filePath, fileAttr) -> filePath.toString().endsWith("sampler.shp"));
		aggregateBuildings((File[]) stream.map((path) -> path.getParent().toFile()).toArray(File[]::new), outputFile);
		stream.close();
	}

	/**
	 * Aggregate the simulation results (buildings) into a unique building file.
	 * 
	 * @param inputDirectories
	 *            the directories containing the output data of the simulation
	 * @param outputFile
	 *            the output directory for the aggregate task
	 * @throws Exception
	 */
	public static void aggregateBuildings(File[] inputDirectories, File outputFile) throws Exception {
		String specsBuildings = "the_geom:Polygon,height:Double,area:Double,volume:Double,idpar:String,imu_dir:String";
		try {
			FileDataStoreFactorySpi factory = new ShapefileDataStoreFactory();
			DataStore dataStoreBuildings = factory.createDataStore(outputFile.toURI().toURL());
			String featureTypeNameB = "buildings";
			SimpleFeatureType featureTypeBuildings = DataUtilities.createType(featureTypeNameB, specsBuildings);
			dataStoreBuildings.createSchema(featureTypeBuildings);
			Transaction transactionB = new DefaultTransaction("create");
			String typeNameB = dataStoreBuildings.getTypeNames()[0];
			SimpleFeatureSource featureSourceB = dataStoreBuildings.getFeatureSource(typeNameB);
			SimpleFeatureType typeB = featureSourceB.getSchema();
			SimpleFeatureStore featureStoreB = (SimpleFeatureStore) featureSourceB;
			ListFeatureCollection collectionB = new ListFeatureCollection(featureTypeBuildings);
			featureStoreB.setTransaction(transactionB);
			int building = 1;
			for (File file : inputDirectories) {
				// go through the entire file hierarchy to find the shapefiles to aggregate
				Stream<Path> stream = Files.find(Paths.get(file.toURI()), 2,
						(filePath, fileAttr) -> filePath.toString().endsWith("sampler.shp"));
				Iterator<Path> it = stream.iterator();
				while (it.hasNext()) {
					Path buildingFile = it.next();
					try {
						ShapefileDataStore store = new ShapefileDataStore(buildingFile.toUri().toURL());
						FeatureReader<SimpleFeatureType, SimpleFeature> featureReader = store.getFeatureReader();
						while (featureReader.hasNext()) {
							SimpleFeature feature = featureReader.next();
							Object geom = feature.getDefaultGeometry();
							double height = Double.parseDouble(feature.getAttribute("Hauteur").toString());
							double area = Double.parseDouble(feature.getAttribute("Aire").toString());
							double volume = Double.parseDouble(feature.getAttribute("Volume").toString());
							String idpar = feature.getAttribute("idpar").toString();
							String imu_dir = feature.getAttribute("idblock").toString();
							Object[] values = new Object[] { geom, height, area, volume, idpar, imu_dir };
							SimpleFeature simpleFeature = SimpleFeatureBuilder.build(typeB, values,
									String.valueOf(building++));
							collectionB.add(simpleFeature);
						}
						featureReader.close();
						store.dispose();
					} catch (MalformedURLException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				stream.close();
			}
			try {
				featureStoreB.addFeatures(collectionB);
				transactionB.commit();
			} catch (Exception problem) {
				problem.printStackTrace();
				transactionB.rollback();
			} finally {
				transactionB.close();
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (SchemaException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("I'm finished with buildings");
	}

	/**
	 * Run the aggregate task.
	 * 
	 * @param inputDirectory
	 *            the directoriy containing the input data of the simulation
	 * @param inputCSV
	 *            the CSV file containing statistics on the simulation results
	 * @param inputResultDir
	 *            the directories containing the output data of the simulation
	 * @param outputParcelFile
	 *            the output file for parcels
	 * @param outputBuildingFile
	 *            the output file for buildings
	 * @throws Exception
	 */
	public static void aggregate(File inputDirectory, File inputCSV, File inputResultDir, File outputParcelFile,
			File outputBuildingFile) throws Exception {
		outputParcelFile.getParentFile().mkdirs();
		aggregateParcels(inputDirectory, inputCSV, outputParcelFile);
		outputBuildingFile.getParentFile().mkdirs();
		aggregateBuildings(inputResultDir, outputBuildingFile);
	}

	/**
	 * Run the aggregate task.
	 * 
	 * @param imu
	 *            the name of the directories corresponding to the IMU (urban block)
	 * @param dataDir
	 *            the directories containing the input data of the simulation
	 * @param resultDir
	 *            the directories containing the output data of the simulation
	 * @param inputCSV
	 *            the CSV file containing statistics on the simulation results
	 * @param aggregateOutputDir
	 *            the output directory for the aggregate task
	 * @throws Exception
	 */
	public static void run(String[] imu, File[] dataDir, File[] resultDir, File inputCSV, File aggregateOutputDir)
			throws Exception {
		AggregateResults.PARCEL_NAME = "parcelle.shp";
		aggregateOutputDir.mkdirs();
		aggregateParcels(dataDir, inputCSV, new File(aggregateOutputDir, "parcels.shp"));
		aggregateBuildings(resultDir, new File(aggregateOutputDir, "buildings.shp"));
	}

	public static void main(String[] args) throws Exception {
		AggregateResults.PARCEL_NAME = "parcelle.shp";
		File dataDir[] = (new File("/home/mbrasebin/.openmole/ZBOOK-SIGOPT-2016/webui/projects/pchoisy_openmole_tarball/results_pchoisy/")).listFiles();
		File inputCSV = (new File("/home/mbrasebin/.openmole/ZBOOK-SIGOPT-2016/webui/projects/pchoisy_openmole_tarball/results_pchoisy/output.csv"));
		File aggregateOutputDir = new File("/tmp/tmp/");
		//aggregateParcels(dataDir, inputCSV, new File(aggregateOutputDir, "parcels.shp"));
		aggregateBuildings(dataDir, new File(aggregateOutputDir, "buildings.shp"));
		
	}
}

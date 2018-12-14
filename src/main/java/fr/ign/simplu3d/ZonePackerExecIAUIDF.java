package fr.ign.simplu3d;

import fr.ign.cogit.geoxygene.api.feature.IFeature;
import fr.ign.cogit.geoxygene.api.feature.IFeatureCollection;
import fr.ign.cogit.geoxygene.util.conversion.ShapefileReader;
import fr.ign.cogit.simplu3d.util.distribution.Initialize;
import fr.ign.cogit.simplu3d.util.distribution.ZonePackager;
import fr.ign.simplu3d.iauidf.openmole.EPFIFTask;

public class ZonePackerExecIAUIDF {

	/**
	 * The aim of this method is to prepare a set of folder from a parcel shapefile
	 * for distribution purpose
	 * 
	 * @param args 4 arguments are required
	 * @throws Exception an exception
	 */
	public static void main(String[] args) throws Exception {

		if (args.length < 4) {
			System.out.println("Not enough attribute requires 4 attributes");
			System.out.println("A path to a shapefile that needs to be packages for distribution");
			System.out.println(
					"A patha to temporary folder where intermediate results (morphological unit) will be stored");
			System.out.println("A path to the output folder where the results will be stored");
			System.out.println(
					"A positive Integer that contains the number of simulable parcels that will be stored in each subpackages");
			return ;

		}
		
		Initialize.init();
		
		
		String parcelFileIn = args[0];// "/home/mbrasebin/Bureau/parcels_rulez/real/parcels_rulez.shp";
		String folderTemp = args[1]; // "/tmp/tmp/";
		String folderOut = args[2]; // "/tmp/out/";

		IFeatureCollection<IFeature> parcelles = ShapefileReader.read(parcelFileIn);

		int numberOfParcels = Integer.parseInt(args[3]); //20
		double areaMax = EPFIFTask.MAX_PARCEL_AREA;

		ZonePackager.ATTRIBUTE_SIMUL = EPFIFTask.ATT_SIMUL;
		ZonePackager.ATTRIBUTE_SIMUL_TYPE = "Integer";

		ZonePackager.createParcelGroupsAndExport(parcelles, numberOfParcels, areaMax, folderTemp, folderOut, false);
	}

}

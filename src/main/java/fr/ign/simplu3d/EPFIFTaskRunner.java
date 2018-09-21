package fr.ign.simplu3d;
import java.io.File;

import fr.ign.cogit.simplu3d.io.feature.AttribNames;
import fr.ign.simplu3d.iauidf.openmole.EPFIFTask;
import fr.ign.simplu3d.iauidf.tool.ParcelAttributeTransfert;

public class EPFIFTaskRunner {
  public static String run(File folder, String dirName, File folderOut, File parameterFile, long seed) {
    AttribNames.setATT_CODE_PARC("IDPAR");
    EPFIFTask.USE_DEMO_SAMPLER = false;
    EPFIFTask.INTERSECTION = true;
    EPFIFTask.FLOOR_HEIGHT = 3;
    EPFIFTask.MAX_PARCEL_AREA = 10000; // now obsolete
    EPFIFTask.PARCEL_NAME = "parcelle.shp";
    EPFIFTask.DEBUG_MODE = true;
    
	ParcelAttributeTransfert.att_libelle_zone = "LIBELLE_ZO";
	ParcelAttributeTransfert.att_art_10_m = "B1_HAUT_M";
	ParcelAttributeTransfert.att_art_10_m_2 = "B2_HAUT_M";

    // String[] folderSplit = folder.getAbsolutePath().split(File.separator);
    String imu = dirName;// folderSplit[folderSplit.length - 1];
  
    String result = "";
    	 
    try {
    	result = EPFIFTask.run(folder, dirName, folderOut, parameterFile, seed);
    } catch (Exception e) {
      result = "# " + imu + " #\n";
      result += "# "+ e.toString() + "\n";
//      for (StackTraceElement s : e.getStackTrace())
//        result += "# " + s.toString() + "\n";
      result += "# " + imu + " #\n";
      e.printStackTrace();
    }
    return result;
  }
  
  
  public static ClassLoader getClassLoader() {
	return EPFIFTaskRunner.class.getClassLoader();
}

  public static void main(String[] args) {
	ParcelAttributeTransfert.att_libelle_zone = "LIBELLE_ZO";
	ParcelAttributeTransfert.att_art_10_m = "B1_HAUT_M";
	ParcelAttributeTransfert.att_art_10_m_2 = "B2_HAUT_M";
																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																									

    String numrep = "8951";
    EPFIFTask.inclusion_list.add("94055000AN0043");
    String foldName ="/home/mbrasebin/Documents/Donnees/IAUIDF/Input/Input1/dep_94_connected_openmole/dataBasicSimu/dep94/";
    
    
    File folder = new File(foldName + numrep + "/");
    File folderOut = new File("/tmp/tmp/" + numrep + "/");
    File parameterFile = new File("/home/mbrasebin/Documents/Donnees/IAUIDF/Input/Input1/dep_94_connected_openmole/dataBasicSimu/scenario/parameters_iauidf_temp.xml");
    
    

    
    long seed = 42L;
    String res = "";
    res = run(folder, numrep, folderOut, parameterFile, seed);
    System.out.println("result : " + res);
  }
}

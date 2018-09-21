import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import fr.ign.simplu3d.EPFIFTaskRunner;

public class IterativeSim {

	public static void main(String[] args) throws FileNotFoundException, IOException {

		File in = new File("/home/mickael/data/mbrasebin/donnees/IAUIDF/data_grille/results_77_test/cat.txt");

		String numrep;
		try (InputStream fis = new FileInputStream(in);
				InputStreamReader isr = new InputStreamReader(fis, Charset.forName("UTF-8"));
				BufferedReader br = new BufferedReader(isr);) {
			while ((numrep = br.readLine()) != null) {
				System.out.println(numrep);

				String foldName = "/home/mickael/data/mbrasebin/donnees/IAUIDF/data_grille/dep77/";

				File folder = new File(foldName + numrep + "/");
				File folderOut = new File(
						"/home/mickael/data/mbrasebin/donnees/IAUIDF/data_grille/out/" + numrep + "/");
				File parameterFile = new File(
						"/home/mickael/data/mbrasebin/donnees/IAUIDF/data_grille/parameters_iauidf.xml");
				long seed = 42L;
				String res = "";
				res = EPFIFTaskRunner.run(folder, numrep, folderOut, parameterFile, seed);
				System.out.println("result : " + res);
			}
		}

	}

}

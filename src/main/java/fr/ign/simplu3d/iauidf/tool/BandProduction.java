package fr.ign.simplu3d.iauidf.tool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.PrecisionModel;
import com.vividsolutions.jts.operation.linemerge.LineMerger;
import com.vividsolutions.jts.precision.GeometryPrecisionReducer;
import com.vividsolutions.jts.simplify.DouglasPeuckerSimplifier;

import fr.ign.cogit.geoxygene.api.spatial.coordgeom.IDirectPosition;
import fr.ign.cogit.geoxygene.api.spatial.coordgeom.IPolygon;
import fr.ign.cogit.geoxygene.api.spatial.geomaggr.IMultiCurve;
import fr.ign.cogit.geoxygene.api.spatial.geomaggr.IMultiSurface;
import fr.ign.cogit.geoxygene.api.spatial.geomprim.IOrientableCurve;
import fr.ign.cogit.geoxygene.api.spatial.geomprim.IOrientableSurface;
import fr.ign.cogit.geoxygene.api.spatial.geomroot.IGeometry;
import fr.ign.cogit.geoxygene.contrib.geometrie.Vecteur;
import fr.ign.cogit.geoxygene.convert.FromGeomToLineString;
import fr.ign.cogit.geoxygene.convert.FromGeomToSurface;
import fr.ign.cogit.geoxygene.spatial.geomaggr.GM_MultiCurve;
import fr.ign.cogit.geoxygene.util.conversion.AdapterFactory;
import fr.ign.cogit.simplu3d.model.BasicPropertyUnit;
import fr.ign.cogit.simplu3d.model.ParcelBoundary;
import fr.ign.cogit.simplu3d.model.ParcelBoundaryType;
import fr.ign.simplu3d.iauidf.regulation.Regulation;

public class BandProduction {

  List<IMultiSurface<IOrientableSurface>> lOut = new ArrayList<>();
  IMultiCurve<IOrientableCurve> iMSRoad = new GM_MultiCurve<>();
  private IMultiCurve<IOrientableCurve> lineRoad = null;
  private final double DOUGLAS_PEUCKER_PRECISION = 0.1;

  private IGeometry simplifiyGeom(IGeometry iMSRoad, double precision) {
    IGeometry geom = null;
    try {
      Geometry ginit = AdapterFactory.toGeometry(new GeometryFactory(), iMSRoad);
      PrecisionModel pm = new PrecisionModel(10);
      Geometry g2 = GeometryPrecisionReducer.reduce(ginit, pm);
      LineMerger merger = new LineMerger();
      for (int i = 0; i < g2.getNumGeometries(); i++) {
        merger.add(g2.getGeometryN(i));
      }
      Collection<LineString> collection = merger.getMergedLineStrings();
      Geometry g3 = ginit.getFactory().createMultiLineString(collection.toArray(new LineString[collection.size()]));
      // Geometry g3 = g2.union();
      System.out.println(g3);
      Geometry g = DouglasPeuckerSimplifier.simplify(g3, precision);
      System.out.println(g.getNumPoints());
      geom = AdapterFactory.toGM_Object(g);
      // IGeometry geom = CommonAlgorithms.filtreDouglasPeucker(iMSRoad, 10.0);
      System.out.println(iMSRoad.numPoints() + " : " + iMSRoad);
      System.out.println(geom.numPoints() + " : " + geom);
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
    return geom;
  }
  
  
  public static double SHIFT_BAND = 0.0;

  @SuppressWarnings("unchecked")
  public BandProduction(BasicPropertyUnit bPU, Regulation r1, Regulation r2) {

    // On récupère le polygone surlequel on va faire la découpe
    IPolygon pol_BPU = bPU.getPol2D();

    // On créé la géométrie des limites donnant sur la voirie

    List<ParcelBoundary> lBordureVoirie = bPU.getCadastralParcels().get(0).getBoundariesByType(ParcelBoundaryType.ROAD);
    for (ParcelBoundary sc : lBordureVoirie) {
      iMSRoad.add((IOrientableCurve) sc.getGeom());
    }

    // System.out.println("size road" + iMSRoad.size());

    double profBande = r1.getBande();
    // BANDE Profondeur de la bande principale x > 0 profondeur de la bande
    // par rapport à la voirie
    IMultiSurface<IOrientableSurface> iMSBande1;
    if (profBande == 0) {
      iMSBande1 = FromGeomToSurface.convertMSGeom(pol_BPU);
    } else {
      // iMSBande1 = FromGeomToSurface.convertMSGeom(pol_BPU.intersection(iMSRoad.buffer(profBande)));
      IGeometry g = simplifiyGeom(iMSRoad, DOUGLAS_PEUCKER_PRECISION);
      iMSBande1 = FromGeomToSurface.convertMSGeom(pol_BPU.intersection(g.buffer(profBande + SHIFT_BAND)));
    }

    IMultiSurface<IOrientableSurface> iMSBande2 = null;

    // ART_6 Distance minimale des constructions par rapport à la voirie
    // imposée en mètre 88= non renseignable, 99= non réglementé

    // On enlève la bande de x m à partir de la voirie
    double r1_art6 = r1.getArt_6();
    if (r1_art6 != 88.0 && r1_art6 != 99.0 && r1_art6 != 0.0 && !iMSBande1.isEmpty()) {
      // IGeometry g = simplifiyGeom(iMSRoad, 10);
      iMSBande1 = FromGeomToSurface.convertMSGeom(iMSBande1.difference(iMSRoad.buffer(r1_art6)));
      // iMSBande1 = FromGeomToSurface.convertMSGeom(iMSBande1.difference(g.buffer(r1_art6)));
    }

    // ART_72 Distance minimale des constructions par rapport aux limites
    // séparatives imposée en mètre 88= non renseignable, 99= non réglementé
    // ART_73 Distance minimale des constructions par rapport à la limte
    // séparative de fond de parcelle 88= non renseignable, 99= non
    // réglementé

    // il me semble qu'on avait dit qu'il n'y avait pas de discrimination

    // On créé la géométrie des autres limites séparatives
    IMultiCurve<IOrientableCurve> iMSLim = new GM_MultiCurve<>();
    List<ParcelBoundary> lBordureLat = bPU.getCadastralParcels().get(0).getBoundaries();

    for (ParcelBoundary sc : lBordureLat) {

      if (sc.getType() == ParcelBoundaryType.LAT) {
        iMSLim.add((IOrientableCurve) sc.getGeom());
      }
    }
    List<ParcelBoundary> lBordureFond = bPU.getCadastralParcels().get(0).getBoundaries();
    for (ParcelBoundary sc : lBordureFond) {
      if (sc.getType() == ParcelBoundaryType.BOT) {
        iMSLim.add((IOrientableCurve) sc.getGeom());
      }
    }

    // On enlève la bande de x m à partir des limites séparatives
    double r1_art72 = r1.getArt_72();
    if (r1_art72 != 88.0 && r1_art72 != 99.0 && r1_art72 != 0.0 && !iMSBande1.isEmpty() && (r1.getArt_71() != 2)) {
      iMSBande1 = FromGeomToSurface.convertMSGeom(iMSBande1.difference(iMSLim.buffer(r1_art72)));
    }

    // Idem s'il y a un règlement de deuxième bande
    if (r2 != null && profBande !=0  ) {
       
    	
    	if( !iMSRoad.isEmpty()) {
    	      IGeometry g = simplifiyGeom(iMSRoad, DOUGLAS_PEUCKER_PRECISION);
    	      // iMSBande2 = FromGeomToSurface.convertMSGeom(pol_BPU.difference(iMSRoad.buffer(profBande)));
    	      iMSBande2 = FromGeomToSurface.convertMSGeom(pol_BPU.difference(g.buffer(profBande-SHIFT_BAND)));
    		
    	}else {
    		 iMSBande2 = FromGeomToSurface.convertMSGeom(pol_BPU);
    	}

      // idem pour r2
      double r2_art6 = r2.getArt_6();
      if (r2_art6 != 88.0 && r2_art6 != 99.0 && r2_art6 != 0.0 && iMSBande2 != null && !iMSBande2.isEmpty()) {

        iMSBande2 = FromGeomToSurface.convertMSGeom(iMSBande2.difference(iMSRoad.buffer(r2_art6)));
      }

      // idem pour r2
      double r2_art72 = r2.getArt_72();
      if (r2_art72 != 88.0 && r2_art72 != 99.0 && r2_art72 != 0.0 && iMSBande2 != null && !iMSBande2.isEmpty() && (r2.getArt_71() != 2)) {

        iMSBande2 = FromGeomToSurface.convertMSGeom(iMSBande2.difference(iMSLim.buffer(r2_art72)));
      }

      r2.setGeomBande(iMSBande2);

    }

    // 2 bandes
    lOut.add(iMSBande1);
    lOut.add(iMSBande2);

    r1.setGeomBande(iMSBande1);

    // Si l'article 6 demande qu'un alignementsoit respecté, on l'active
    double rArt6 = r1.getArt_6();
    if (rArt6 != 99.0 && rArt6 != 88.0) {

      if (rArt6 == 0) {
        // Soit le long de la limite donnant sur la voirie
        lineRoad = (IMultiCurve<IOrientableCurve>) (iMSRoad.clone());
      } else {
        // Soit en appliquant un petit décalage
        lineRoad = shiftRoad(bPU, rArt6);
      }
    } else {

    }
  }

  /**
   * Méthode permettant de produire une multicurve en reculant vers l'intérieur de l'unité foncière (bPU) les limites donnant sur la voirie d'une
   * distance (valShiftB)
   * 
   * @param bPU
   * @param valShiftB
   * @return
   */
  private IMultiCurve<IOrientableCurve> shiftRoad(BasicPropertyUnit bPU, double valShiftB) {

    IMultiCurve<IOrientableCurve> iMS = new GM_MultiCurve<>();

    IDirectPosition centroidParcel = bPU.getPol2D().centroid();

    for (IOrientableCurve oC : iMSRoad) {

      if (oC.isEmpty()) {
        continue;
      }

      IDirectPosition centroidGeom = oC.coord().get(0);
      Vecteur v = new Vecteur(centroidParcel, centroidGeom);

      Vecteur v2 = new Vecteur(oC.coord().get(0), oC.coord().get(oC.coord().size() - 1));
      v2.setZ(0);
      v2.normalise();

      Vecteur vOut = v2.prodVectoriel(new Vecteur(0, 0, 1));

      IGeometry geom = ((IGeometry) oC.clone());

      if (v.prodScalaire(vOut) < 0) {
        vOut = vOut.multConstante(-1);
      }

      IGeometry geom2 = geom.translate(valShiftB * vOut.getX(), valShiftB * vOut.getY(), 0);

      if (!geom2.intersects(bPU.getGeom())) {
        geom2 = geom.translate(-valShiftB * vOut.getX(), -valShiftB * vOut.getY(), 0);
      }

      iMS.addAll(FromGeomToLineString.convert(geom2));

    }

    return iMS;

  }

  public IMultiCurve<IOrientableCurve> getLineRoad() {
    return this.lineRoad;
  }

}

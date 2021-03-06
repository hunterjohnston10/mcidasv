/*
 * This file is part of McIDAS-V
 *
 * Copyright 2007-2017
 * Space Science and Engineering Center (SSEC)
 * University of Wisconsin - Madison
 * 1225 W. Dayton Street, Madison, WI 53706, USA
 * http://www.ssec.wisc.edu/mcidas
 * 
 * All Rights Reserved
 * 
 * McIDAS-V is built on Unidata's IDV and SSEC's VisAD libraries, and
 * some McIDAS-V source code is based on IDV and VisAD source code.  
 * 
 * McIDAS-V is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 * 
 * McIDAS-V is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 */
package edu.wisc.ssec.mcidasv.data.hydra;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import javax.swing.event.ChangeEvent;
import java.awt.event.ActionListener;
import javax.swing.event.ChangeListener;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.Map;
import java.util.HashMap;
import java.util.Hashtable;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JSlider;
import javax.swing.DefaultBoundedRangeModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ucar.unidata.data.DataChoice;
import ucar.unidata.data.DataSelection;
import ucar.unidata.data.DataSelectionComponent;
import ucar.unidata.data.GeoLocationInfo;
import ucar.unidata.data.GeoSelection;
import ucar.unidata.geoloc.projection.LatLonProjection;
import ucar.unidata.view.geoloc.MapProjectionDisplay;
import ucar.unidata.view.geoloc.MapProjectionDisplayJ3D;
import ucar.unidata.view.geoloc.MapProjectionDisplayJ2D;
import ucar.unidata.geoloc.ProjectionRect;
import ucar.visad.ProjectionCoordinateSystem;
import ucar.visad.display.DisplayMaster;
import ucar.visad.display.LineDrawing;
import ucar.visad.display.MapLines;
import ucar.visad.display.RubberBandBox;
import visad.CellImpl;
import visad.FlatField;
import visad.Gridded2DSet;
import visad.RealTupleType;
import visad.RealType;
import visad.SampledSet;
import visad.UnionSet;
import visad.VisADException;
import visad.data.mcidas.BaseMapAdapter;
import visad.georef.MapProjection;
import visad.GriddedSet;

public class TrackSelection extends DataSelectionComponent {

	private static final Logger logger = LoggerFactory.getLogger(TrackSelection.class);
	
	public static final int DEFAULT_TRACK_STRIDE = 5;
	public static final int DEFAULT_VERTICAL_STRIDE = 2;
	
	DataChoice dataChoice;
	FlatField track;

	double[] x_coords = new double[2];
	double[] y_coords = new double[2];
	MapProjectionDisplayJ2D mapProjDsp;
	DisplayMaster dspMaster;

	int trackStride;
	int verticalStride;

	JTextField trkStr;
	JTextField vrtStr;
        JTextField widthFld;

        LineDrawing trackSelectDsp;
        float[][] trackLocs;
        int trackLen;
        int trackPos;
        int trackStart;
        int trackStop;
        double trackWidthPercent = 5;
        int selectWidth;
        Map defaultSubset;

	TrackSelection(DataChoice dataChoice, FlatField track, Map defaultSubset) throws VisADException, RemoteException {
		super("track");
		this.dataChoice = dataChoice;
		this.track = track;
                this.defaultSubset = defaultSubset;

                GriddedSet gset = (GriddedSet)track.getDomainSet();
                float[] lo = gset.getLow();
                float[] hi = gset.getHi();
                float[][] values = gset.getSamples();
                
                trackLen = values[0].length;
                selectWidth = (int) (trackLen*(trackWidthPercent/100));
                selectWidth /= 2;
                trackPos = trackLen/2;
                trackStart = trackPos - selectWidth;
                trackStop = trackPos + selectWidth;
                
                trackLocs = values;
                Gridded2DSet track2D = new Gridded2DSet(RealTupleType.SpatialEarth2DTuple, new float[][] {values[0], values[1]}, values[0].length);
		//mapProjDsp = new MapProjectionDisplayJ3D(MapProjectionDisplay.MODE_2Din3D);
		mapProjDsp = new MapProjectionDisplayJ2D();
		//mapProjDsp.enableRubberBanding(false);
		dspMaster = mapProjDsp;
		mapProjDsp.setMapProjection(getDataProjection(new ProjectionRect(lo[0],lo[1],hi[0],hi[1])));
		LineDrawing trackDsp = new LineDrawing("track");
		trackDsp.setLineWidth(0.5f);
                trackDsp.setData(track2D);
                
                trackSelectDsp = new LineDrawing("trackSelect");
                trackSelectDsp.setLineWidth(3f);
                trackSelectDsp.setColor(java.awt.Color.green);
                
                updateTrackSelect();
                
		mapProjDsp.addDisplayable(trackDsp);
                mapProjDsp.addDisplayable(trackSelectDsp);

		MapLines mapLines = new MapLines("maplines");
		URL mapSource = mapProjDsp.getClass().getResource(
				"/auxdata/maps/OUTLSUPU");
		try {
			BaseMapAdapter mapAdapter = new BaseMapAdapter(mapSource);
			mapLines.setMapLines(mapAdapter.getData());
			mapLines.setColor(java.awt.Color.cyan);
			//mapProjDsp.addDisplayable(mapLines);
		} catch (Exception excp) {
			logger.error("cannot open map file: " + mapSource, excp);
		}

		mapLines = new MapLines("maplines");
		mapSource = mapProjDsp.getClass().getResource("/auxdata/maps/OUTLSUPW");
		try {
			BaseMapAdapter mapAdapter = new BaseMapAdapter(mapSource);
			mapLines.setMapLines(mapAdapter.getData());
			mapLines.setColor(java.awt.Color.cyan);
			mapProjDsp.addDisplayable(mapLines);
		} catch (Exception excp) {
			logger.error("cannot open map file: " + mapSource, excp);
		}

		mapLines = new MapLines("maplines");
		mapSource = mapProjDsp.getClass().getResource("/auxdata/maps/OUTLHPOL");
		try {
			BaseMapAdapter mapAdapter = new BaseMapAdapter(mapSource);
			mapLines.setMapLines(mapAdapter.getData());
			mapLines.setColor(java.awt.Color.cyan);
			//mapProjDsp.addDisplayable(mapLines);
		} catch (Exception excp) {
			logger.error("cannot open map file: " + mapSource, excp);
		}

		dspMaster.draw();
	}

	public MapProjection getDataProjection(ProjectionRect rect) {
		MapProjection mp = null;
		try {
			mp = new ProjectionCoordinateSystem(new LatLonProjection("blah", rect));
		} catch (Exception e) {
			logger.error("error getting data projection", e);
		}
		return mp;
	}

	protected JComponent doMakeContents() {
		try {
			JPanel panel = new JPanel(new BorderLayout());
			panel.add("Center", dspMaster.getDisplayComponent());

			JPanel stridePanel = new JPanel(new FlowLayout());
			trkStr = new JTextField(Integer.toString(TrackSelection.DEFAULT_TRACK_STRIDE), 3);
			vrtStr = new JTextField(Integer.toString(TrackSelection.DEFAULT_VERTICAL_STRIDE), 3);
                        widthFld = new JTextField(Double.toString(trackWidthPercent), 3);
                        
			trkStr.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent ae) {
					setTrackStride(Integer.valueOf(trkStr.getText().trim()));
				}
			});
			vrtStr.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent ae) {
					setVerticalStride(Integer.valueOf(vrtStr.getText().trim()));
				}
			});
                        widthFld.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent ae) {
                                        setWidthPercent(Double.valueOf(widthFld.getText().trim()));
                                }
                        });

			stridePanel.add(new JLabel("Track Stride: "));
			stridePanel.add(trkStr);
			stridePanel.add(new JLabel("Vertical Stride: "));
			stridePanel.add(vrtStr);
                        stridePanel.add(new JLabel("Width%: "));
                        stridePanel.add(widthFld);

                        JPanel selectPanel = new JPanel(new GridLayout(2,0));
                        DefaultBoundedRangeModel brm = new DefaultBoundedRangeModel(trackStart, 0, 0, trackLen); 
                        JSlider trackSelect = new JSlider(brm);
                        trackSelect.addChangeListener( new ChangeListener() {
                           public void stateChanged(ChangeEvent e) {
                              trackPos = (int) ((JSlider)e.getSource()).getValue();
                              updateTrackSelect();
                           }
                        }
                        );
                        selectPanel.add(trackSelect);
                        selectPanel.add(stridePanel);
			panel.add("South", selectPanel);

			return panel;
		} catch (Exception e) {
			logger.error("error creating contents", e);
		}
		return null;
	}

	public void setTrackStride(int stride) {
		trackStride = stride;
	}

	public void setVerticalStride(int stride) {
		verticalStride = stride;
	}
        
        public void setWidthPercent(double percent) {
                trackWidthPercent = percent;
                selectWidth = (int) (trackLen*(trackWidthPercent/100));
                selectWidth /= 2;
                updateTrackSelect();
        }

	/**
	 * Update Track Stride if input text box holds a positive integer.
	 * 
	 * @return true if trackStride was updated
	 */
	
	public boolean setTrackStride() {
		boolean setOk = false;
		try {
			int newStride = Integer.valueOf(trkStr.getText().trim());
                        if (newStride >= 1) {
			   trackStride = newStride;
			   setOk = true;
                        }
                        else {
                           setOk = false;
                        }
		} catch (NumberFormatException nfe) {
			// do nothing, will return correct result code
		}
		return setOk;
	}

	/**
	 * Update Vertical Stride if input text box holds a positive integer.
	 * 
	 * @return true if verticalStride was updated
	 */
	
	public boolean setVerticalStride() {
		boolean setOk = false;
		try {
			int newStride = Integer.valueOf(vrtStr.getText().trim());
                        if (newStride >= 1) {
   		  	   verticalStride = newStride;
			   setOk = true;
                        }
                        else {
                           setOk = false;
                        }
		} catch (NumberFormatException nfe) {
			// do nothing, will return correct result code
		}
		return setOk;
	}

        /**
	 * Update Vertical Stride if input text box holds a positive integer.
	 * 
	 * @return true if verticalStride was updated
	 */
	
	public boolean setWidthPercent() {
		boolean setOk = false;
		try {
			double newWidth = Double.valueOf(widthFld.getText().trim());
			trackWidthPercent = newWidth;
			setOk = true;
		} catch (NumberFormatException nfe) {
			// do nothing, will return correct result code
		}
		return setOk;
	}
        
        void updateTrackSelect() {
               trackStart = trackPos - selectWidth;
               if (trackStart < 0) trackStart = 0;
               
               trackStop = trackPos + selectWidth;
               if (trackStop >= trackLen) trackStop = trackLen - 1;
               
               try {
                  Gridded2DSet trck = new Gridded2DSet(RealTupleType.SpatialEarth2DTuple,
                         new float[][] {java.util.Arrays.copyOfRange(trackLocs[0], trackStart, trackStop), 
                                        java.util.Arrays.copyOfRange(trackLocs[1], trackStart, trackStop)}, 
                               (trackStop - trackStart));
                  trackSelectDsp.setData(trck);
               }
               catch (Exception exc) {
                  exc.printStackTrace();
               }           
        }
        
	public void applyToDataSelection(DataSelection dataSelection) {
		setTrackStride();
		setVerticalStride();
                setWidthPercent();

                HashMap subset = (HashMap) ((HashMap)defaultSubset).clone();
                double[] coords = (double[]) subset.get(ProfileAlongTrack.vertDim_name);

                subset.put(ProfileAlongTrack.trackDim_name, new double[] {trackStart, trackStop, trackStride});
                subset.put(ProfileAlongTrack.vertDim_name, new double[] {coords[0], coords[1], verticalStride});
                  
 
                MultiDimensionSubset select = new MultiDimensionSubset(subset);

                Hashtable table = dataChoice.getProperties();
                table.put(MultiDimensionSubset.key, select);

                table = dataSelection.getProperties();
                table.put(MultiDimensionSubset.key, select);

                dataChoice.setDataSelection(dataSelection);

	}
}

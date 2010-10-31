/*
 * $RCSfile$
 *
 * Copyright (c) 2007 Sun Microsystems, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * - Redistribution of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistribution in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in
 *   the documentation and/or other materials provided with the
 *   distribution.
 *
 * Neither the name of Sun Microsystems, Inc. or the names of
 * contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any
 * kind. ALL EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND
 * WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY
 * EXCLUDED. SUN MICROSYSTEMS, INC. ("SUN") AND ITS LICENSORS SHALL
 * NOT BE LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF
 * USING, MODIFYING OR DISTRIBUTING THIS SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE FOR
 * ANY LOST REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL,
 * CONSEQUENTIAL, INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND
 * REGARDLESS OF THE THEORY OF LIABILITY, ARISING OUT OF THE USE OF OR
 * INABILITY TO USE THIS SOFTWARE, EVEN IF SUN HAS BEEN ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGES.
 *
 * You acknowledge that this software is not designed, licensed or
 * intended for use in the design, construction, operation or
 * maintenance of any nuclear facility.
 *
 * $Revision: 150 $
 * $Date: 2007-02-10 02:20:46 +0900 (土, 10 2 2007) $
 * $State$
 */

package com.sun.j3d.loaders.lw3d;



import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Vector;

import com.sun.j3d.loaders.IncorrectFormatException;
import com.sun.j3d.loaders.ParsingErrorException;


/**
 * This class is responsible for parsing a binary Object file and storing
 * the data. This class is not called directly, but is the parent class of
 * J3dLwoObject. The subclass calls this class to parse the file, then it
 * turns the resulting data into Java3D objects. LwoObject instantiates
 * an LWOBFileReader object to parse the data. Then the class creates a
 * list of ShapeHolder objects to hold all of the vertex/facet data and
 * surface references and creates a list of LwoSurface objects to hold
 * the data for each surface.<BR>
 * Rather than describe in detail how the file is parsed for each method,
 * I advise the user of this code to understand the lw3d file format 
 * specs, which are pretty clear.
 */

class LwoParser extends ParserObject {

	static final int FORMAT_LWOB = 0;
	static final int FORMAT_LWO2 = 1;

	int format = FORMAT_LWO2;

    LWOBFileReader theReader;
    int currLength;
    float coordsArray[];
    Vector surfNameList = null;
    Vector surfaceList = new Vector(200);	
    Vector shapeList = new Vector(200);

	/**
	* Constructor: Creates file reader and calls parseFile() to actually
	* read the file and grab the data
	*/
    LwoParser(String fileName, int debugVals)
	throws FileNotFoundException {

	super(debugVals);
	debugOutputLn(TRACE, "parser()");
	long start = System.currentTimeMillis();
	theReader = new LWOBFileReader(fileName);
	debugOutputLn(TIME, " file opened in " +
		      (System.currentTimeMillis() - start));
	parseFile();
    }

  LwoParser(URL url, int debugVals)
    throws FileNotFoundException {
      super(debugVals);
      debugOutputLn(TRACE, "parser()");
      try {
	long start = System.currentTimeMillis();
	theReader = new LWOBFileReader(url);
	debugOutputLn(TIME, " file opened in " + 
		      (System.currentTimeMillis() - start));
      }
      catch (IOException ex) {
	throw new FileNotFoundException(url.toString());
      }
      parseFile();
  }
		
	/**
	* Detail polygons are currently not implemented by this loader.  Their
	* structure in geometry files is a bit complex, so there's this separate
	* method for simply parsing through and ignoring the data for detail
	* polygons
	*/
    int skipDetailPolygons(int numPolys) throws ParsingErrorException {
	debugOutputLn(TRACE, "skipDetailPolygons(), numPolys = " + numPolys);
	int lengthRead = 0;
	int vert;
	
	try {
	    for (int polyNum = 0; polyNum < numPolys; ++polyNum) {
		debugOutputLn(VALUES, "polyNum = " + polyNum);
		int numVerts = theReader.getShortInt();
		theReader.skip(numVerts * 2 + 2);  // skip indices plus surf
		lengthRead += (numVerts * 2) + 4;  // increment counter
	    }
	}
	catch (IOException e) {
	    debugOutputLn(EXCEPTION, "Exception in reading detail polys: " + e);
	    throw new ParsingErrorException(e.getMessage());
	}
	return lengthRead;
    }

	/**
	* Returns already-existing ShapeHolder if one exists with the same
	* surface and the same geometry type (point, line, or poly)
	*/
    ShapeHolder getAppropriateShape(int numSurf, int numVerts) {
	for (Enumeration e = shapeList.elements();
	     e.hasMoreElements() ;) {
	    ShapeHolder shape = (ShapeHolder)e.nextElement();
	    if (shape.numSurf == numSurf)
		if (shape.numVerts == numVerts ||
		    (shape.numVerts > 3 &&
		     numVerts > 3))
		    return shape;
	}
	return null;
    }

   
	/**
	* Parse the file for all the data for a POLS object (polygon 
	* description)
	*/ 
    void getPols(int length) {
	debugOutputLn(TRACE, "getPols(len), len = " + length);
	int vert;
	int lengthRead = 0;
	int prevNumVerts = -1;
	int prevNumSurf = 0;
	Vector facetSizesList;
	int facetIndicesArray[];
	facetSizesList =
	    new Vector(length/6);  // worst case size (every poly one vert)
		// Note that our array sizes are hardcoded because we don't
		// know until we're done how large they will be
	facetIndicesArray = new int[length/2];
	ShapeHolder shape = new ShapeHolder(debugPrinter.getValidOutput());
	debugOutputLn(VALUES, "new shape = " + shape);
	shape.coordsArray = coordsArray;
	shape.facetSizesList = facetSizesList;
	//shape.facetIndicesList = facetIndicesList;
	shape.facetIndicesArray = facetIndicesArray;
	shapeList.addElement(shape);
		
    //long startTime = (new Date()).getTime();
	boolean firstTime = true;
	while (lengthRead < length) {
	    int numVerts = theReader.getShortInt();
	    lengthRead += 2;
	    int intArray[] = new int[numVerts];
	    for (int i = 0; i < numVerts; ++i) {
		intArray[i] = theReader.getShortInt();
		lengthRead += 2;
	    }

	    int numSurf = theReader.getShortInt();
	    lengthRead += 2;
	    long startTimeBuff = 0, startTimeList = 0;
	    if (!firstTime &&
		(numSurf != prevNumSurf ||
		 ((numVerts != prevNumVerts) &&
		  ((prevNumVerts < 3) ||
		   (numVerts < 3))))) {
		// If above true, then start new shape
		shape = getAppropriateShape(numSurf, numVerts);
		if (shape == null) {
		    //debugOutputLn(LINE_TRACE, "Starting new shape");
		    facetSizesList = new Vector(length/6);
		    facetIndicesArray = new int[length/2];
		    shape = new ShapeHolder(debugPrinter.getValidOutput());
		    shape.coordsArray = coordsArray;
		    shape.facetSizesList = facetSizesList;
		    //shape.facetIndicesList = facetIndicesList;
		    shape.facetIndicesArray = facetIndicesArray;
		    shape.numSurf = numSurf;
		    shape.numVerts = numVerts;
		    shapeList.addElement(shape);
		    }
		else {
		    facetSizesList = shape.facetSizesList;
		    facetIndicesArray = shape.facetIndicesArray;
		}
	    }
	    else {
		shape.numSurf = numSurf;
		shape.numVerts = numVerts;
	    }
	    prevNumVerts = numVerts;
	    prevNumSurf = numSurf;
	    facetSizesList.addElement(new Integer(numVerts));

	    int currPtr = 0;
	    System.arraycopy(intArray, 0,
			     facetIndicesArray, shape.currentNumIndices,
			     numVerts);
	    shape.currentNumIndices += numVerts;
	    if (numSurf < 0) {   // neg number means detail poly
		int numPolys = theReader.getShortInt();
		lengthRead += skipDetailPolygons(numPolys);
		shape.numSurf = ~shape.numSurf & 0xffff;
		if (shape.numSurf == 0)
		    shape.numSurf = 1;  // Can't have surface = 0
	    }
	    firstTime = false;
	}
    }

	/**
	* Parses file to get the names of all surfaces.  Each polygon will
	* be associated with a particular surface number, which is the index
	* number of these names
	*/
    void getSrfs(int length) {
	String surfName = new String();
    surfNameList = new Vector(length/2);  // worst case size (each name 2 chars long)
	int lengthRead = 0;
	int stopMarker = theReader.getMarker() + length;

        int surfIndex = 0;
	while (theReader.getMarker() < stopMarker) {
	    debugOutputLn(VALUES, "marker, stop = " +
			  theReader.getMarker() + ", " + stopMarker);
	    debugOutputLn(LINE_TRACE, "About to call getString");
	    surfName = theReader.getString();
	    debugOutputLn(VALUES, "Surfname = " + surfName);
	    surfNameList.addElement(surfName);
	}
    }
		
	/**
	* Parses file to get all vertices
	*/
    void getPnts(int length) throws ParsingErrorException {
	int numVerts = length / 12;
	float x, y, z;

	coordsArray = new float[numVerts*3];
	theReader.getVerts(coordsArray, numVerts);
    }

	/**
	* Creates new LwoSurface object that parses file and gets all
	* surface parameters for a particular surface
	*/
    void getSurf(int length) throws FileNotFoundException {
	debugOutputLn(TRACE, "getSurf()");
	
	// Create LwoSurface object to read and hold each surface, then
	// store that surface in a vector of all surfaces.

	LwoSurface surf = new LwoSurface(theReader, length,
		debugPrinter.getValidOutput());
	surfaceList.addElement(surf);
    }

    /**
	 * parses LWOB containts.
	 */
	void parseLwob(int dataLength) throws FileNotFoundException, IncorrectFormatException {
	debugOutputLn(TRACE, "parseLwob()");
	int length = 0;
	int lengthRead = 0;
	
	// Every parsing unit begins with a four character string
	String tokenString = theReader.getToken();
	lengthRead += 4;
		    
	while (!(tokenString == null) &&
		  lengthRead < dataLength) {
	    long startTime = System.currentTimeMillis();
	    // Based on value of tokenString, go to correct parsing method
	    length = theReader.getInt();
	
	    lengthRead += 4;
	    //debugOutputLn(VALUES, "length, lengthRead, fileLength = " +
	    //	      length + ", " + lengthRead + ", " + fileLength);
	    //debugOutputLn(VALUES, "LWOB marker is at: " + theReader.getMarker());
	
	    if (tokenString.equals("PNTS")) {
		//debugOutputLn(LINE_TRACE, "PNTS");
		getPnts(length);
		debugOutputLn(TIME, "done with " + tokenString + " in " +
			      (System.currentTimeMillis() - startTime));
	    }
	    else if (tokenString.equals("POLS")) {
		//debugOutputLn(LINE_TRACE, "POLS");
		getPols(length);
		debugOutputLn(TIME, "done with " + tokenString + " in " +
			      (System.currentTimeMillis() - startTime));
	    }
	    else if (tokenString.equals("SRFS")) {
		//debugOutputLn(LINE_TRACE, "SRFS");
		getSrfs(length);
		    debugOutputLn(TIME, "done with " + tokenString + " in " +
		    (System.currentTimeMillis() - startTime));
	    }
	    else if (tokenString.equals("CRVS")) {
		//debugOutputLn(LINE_TRACE, "CRVS");
		theReader.skipLength(length);
		    //debugOutputLn(TIME, "done with " + tokenString + " in " +
		    //	(System.currentTimeMillis() - startTime));
	    }
	    else if (tokenString.equals("PCHS")) {
		//debugOutputLn(LINE_TRACE, "PCHS");
		theReader.skipLength(length);
		    //debugOutputLn(TIME, "done with " + tokenString + " in " +
		    //	(System.currentTimeMillis() - startTime));
	    }
	    else if (tokenString.equals("SURF")) {
		//debugOutputLn(LINE_TRACE, "SURF");
		getSurf(length);
		//debugOutputLn(VALUES, "Done with SURF, marker = " + theReader.getMarker());
		debugOutputLn(TIME, "done with " + tokenString + " in " +
			      (System.currentTimeMillis() - startTime));
	    }
	    else if (tokenString.equals("LWOB")) {
		//debugOutputLn(LINE_TRACE, "LWOB");
	    }
	    else {
		//debugOutputLn(LINE_TRACE, "Unknown object = " + tokenString);
		theReader.skipLength(length);
		    //debugOutputLn(TIME, "done with " + tokenString + " in " +
		    //	(System.currentTimeMillis() - startTime));
	    }
	    lengthRead += length;
	    if (lengthRead < dataLength) {
		//debugOutputLn(VALUES, "end of parseLwob, length, lengthRead = " +
		    //	  length + ", " + lengthRead);
		tokenString = theReader.getToken();
		lengthRead += 4;
		//debugOutputLn(VALUES, "just got tokenString = " + tokenString);
	    }
	}
	debugOutputLn(TIME, "done with parseLwob");
	}

	// Lwo2 specific fields
    Vector<String> tagList = new Vector<String>(50);

	class Lwo2Polygon extends ShapeHolder {
		public static final int TYPE_UNKNOWN = 0;
		public static final int TYPE_SURF = 1;
		public static final int TYPE_PTCH = 2;

		int type = TYPE_UNKNOWN;
		int lengthRead = -1;
		String surfName = null;
		Lwo2Surface surface = null;
		// FIXME: ignored properties of polygons, from PTAG specification
		/* Object part = null;
		 * Object smoothGrp = null; */

		Lwo2Polygon(String typeString, int debugVals) {
			super(debugVals);
			setTypeFromString(typeString);
			this.coordsArray = LwoParser.this.coordsArray;
			lengthRead = 0;
			// Currently ignores any flags
			numVerts = theReader.getShortInt() & 0x03FF;
			lengthRead += 2;
			facetSizesList = new Vector(1);
			facetSizesList.add(new Integer(numVerts));
			facetIndicesArray = new int[numVerts];
			for (int i = 0; i < numVerts; ++i) {
				facetIndicesArray[i] = (theReader.getVX());
				lengthRead += theReader.getLastLength();
			}
			currentNumIndices = numVerts;
		}

		public int getLengthRead() {
			return lengthRead;
		}

		public int getType() {
			return type;
		}

		void setTypeFromString(String typeString) {
			if (typeString.equals("SURF")) {
				type = TYPE_SURF;
			}
			else if (typeString.equals("PTCH")) {
				type = TYPE_PTCH;
			}
			else {
				debugOutputLn(WARNING, "Unknown POLS type " + typeString);
				type = TYPE_UNKNOWN;
			}
		}

		public String getSurfName() {
			return surfName;
		}

		public void setSurfName(String surfName) {
			this.surfName = surfName;
			
		}

		public Lwo2Surface getSurface() {
			if (surface == null) {
				surface = Lwo2Surface.getSurfByName(surfName);
			}
			return surface;
		}
	}

	/**
	* Parse the file for all the data for a POLS object (polygon 
	* description)
	*/ 
	void getPolsLwo2(int length) {
	debugOutputLn(TRACE, "getPolsLwo2(len), len = " + length);
	int lengthRead = 0;
	//long startTime = (new Date()).getTime();
	String type = theReader.getToken();
	lengthRead += 4;
	while (lengthRead < length) {
		Lwo2Polygon pol = new Lwo2Polygon(type, debugPrinter.getValidOutput());
		lengthRead += pol.getLengthRead();
		shapeList.add(pol);
	}
	}

	/**
	* Parses file to get all tag, which can be used to name
	* surfaces, parts, smoothing groups
	*/
	void getTagsLwo2(int length) {
	String tagName;
	tagList = new Vector<String>(length/2);  // worst case size (each name 2 chars long)
	int stopMarker = theReader.getMarker() + length;
	while (theReader.getMarker() < stopMarker) {
	    debugOutputLn(VALUES, "marker, stop = " +
			  theReader.getMarker() + ", " + stopMarker);
	    debugOutputLn(LINE_TRACE, "About to call getString");
	    tagName = theReader.getString();
	    debugOutputLn(VALUES, "Surfname = " + tagName);
	    tagList.addElement(tagName);
	}
	}

	/* TODO   void parseLwo2Vmap(int dataLength) {
		debugOutputLn(TRACE, "parseVmap()");
		int length = 0;
		int lengthRead = 0;
		
		// Every parsing unit begins with a four character string
		String tokenString = theReader.getToken();
		lengthRead += 4;
			    
		while (!(tokenString == null) &&
			  lengthRead < dataLength) {
		    long startTime = System.currentTimeMillis();
		    // Based on value of tokenString, go to correct parsing method
		    length = theReader.getShortInt();
	
		    lengthRead += 2;
		    //debugOutputLn(VALUES, "length, lengthRead, fileLength = " +
		    //	      length + ", " + lengthRead + ", " + fileLength);
		    //debugOutputLn(VALUES, "LWOB marker is at: " + theReader.getMarker());
	
	
		    lengthRead += length;
		    if (lengthRead < dataLength) {
			//debugOutputLn(VALUES, "end of parseLWOB, length, lengthRead = " +
			    //	  length + ", " + lengthRead);
			tokenString = theReader.getToken();
			lengthRead += 4;
			//debugOutputLn(VALUES, "just got tokenString = " + tokenString);
		    }
	}
	debugOutputLn(TIME, "done with parseVmap");
	}*/
	
	/**
	* Parses each ptag, which can be used to associate
	* tag of given type to polygons in most recent POLS chunk
	*/
	void getPtagLwo2(int length) {
	debugOutputLn(TRACE, "getPtagLwo2(len), len = " + length);
	int lengthRead = 0;
	//long startTime = (new Date()).getTime();
	String type = theReader.getToken();
	lengthRead += 4;
	while (lengthRead < length) {
		Lwo2Polygon pol = (Lwo2Polygon) shapeList.get(theReader.getVX());
		lengthRead += theReader.getLastLength();
		String tag = tagList.get(theReader.getShortInt());
		lengthRead += 2;
		if (type.equals("SURF")) {
			pol.setSurfName(tag);
		}
		else {
			debugOutputLn(LINE_TRACE, "Unknown type of PTAG: " + type + " / " + tag);
		}
	}
	}

	/**
	* Creates new Lwo2Surface object that parses file and gets all
	* surface parameters for a particular surface
	*/
	void getSurfLwo2(int length) throws FileNotFoundException {
	debugOutputLn(TRACE, "getSurfLwo2()");
	
	// Create Lwo2Surface object to read and hold each surface
	// all surfaces could be retrieved from Lwo2Surface.getSurfByName() static method
	
	Lwo2Surface surf = new Lwo2Surface(theReader, length,
		debugPrinter.getValidOutput());
	}

	/**
     * parses LWO2 containts.
     */
    int parseLwo2(int dataLength) throws FileNotFoundException, IncorrectFormatException {
	debugOutputLn(TRACE, "parseLwo2()");
	int length = 0;
	int lengthRead = 0;
	
	// Every parsing unit begins with a four character string
	String tokenString = theReader.getToken();
	lengthRead += 4;
		    
	while (!(tokenString == null) &&
		  lengthRead < dataLength) {
	    long startTime = System.currentTimeMillis();
	    // Based on value of tokenString, go to correct parsing method
	    length = theReader.getInt();

	    lengthRead += 4;
	    //debugOutputLn(VALUES, "length, lengthRead, fileLength = " +
	    //	      length + ", " + lengthRead + ", " + fileLength);
	    //debugOutputLn(VALUES, "LWO2 marker is at: " + theReader.getMarker());

	    // TODO: Currently LAYRs are skipped
	    if (tokenString.equals("PNTS")) {
		//debugOutputLn(LINE_TRACE, "PNTS");
		getPnts(length);
		debugOutputLn(TIME, "done with " + tokenString + " in " +
			      (System.currentTimeMillis() - startTime));
	    }
	    // TODO: Currently VMAPs are skipped
	    else if (tokenString.equals("POLS")) {
		//debugOutputLn(LINE_TRACE, "POLS");
		getPolsLwo2(length);
		debugOutputLn(TIME, "done with " + tokenString + " in " +
			      (System.currentTimeMillis() - startTime));
	    }
	    else if (tokenString.equals("TAGS")) {
			//debugOutputLn(LINE_TRACE, "TAGS");
			getTagsLwo2(length);
		    debugOutputLn(TIME, "done with " + tokenString + " in " +
		    (System.currentTimeMillis() - startTime));
	    }
		else if (tokenString.equals("PTAG")) {
			getPtagLwo2(length);
			debugOutputLn(TIME, "done with " + tokenString + " in " +
					(System.currentTimeMillis() - startTime));
		}
	    // TODO: Currently VMADs are skipped
	    // TODO: Currently VMPAs are skipped
	    // TODO: Currently ENVLs are skipped
	    // TODO: Currently CLIPs are skipped
	    else if (tokenString.equals("SURF")) {
		//debugOutputLn(LINE_TRACE, "SURF");
		getSurfLwo2(length);
		//debugOutputLn(VALUES, "Done with SURF, marker = " + theReader.getMarker());
		debugOutputLn(TIME, "done with " + tokenString + " in " +
			      (System.currentTimeMillis() - startTime));
	    }
	    else {
		debugOutputLn(LINE_TRACE, "Unknown object = " + tokenString);
		theReader.skipLength(length);
		    //debugOutputLn(TIME, "done with " + tokenString + " in " +
		    //	(System.currentTimeMillis() - startTime));
	    }
	    lengthRead += length;
	    if (lengthRead < dataLength) {
		//debugOutputLn(VALUES, "end of parseFile, length, lengthRead = " +
		    //	  length + ", " + lengthRead);
		tokenString = theReader.getToken();
		lengthRead += 4;
		//debugOutputLn(VALUES, "just got tokenString = " + tokenString);
	    }
	}
	debugOutputLn(TIME, "done with parseLwo2");
	return 0;
    }

	/**
     * parses entire file.
     * return -1 on error or 0 on completion
     */
    int parseFile() throws FileNotFoundException, IncorrectFormatException {
	debugOutputLn(TRACE, "parseFile()");
	
	long parseStartTime = System.currentTimeMillis();
	// Every parsing unit begins with a four character string
	String tokenString = theReader.getToken();
	int dataLength = theReader.getInt() - 4;

    if (!tokenString.equals("FORM")) {
    	throw new IncorrectFormatException(
    	"File is not started with FORM tag");
    }
	//debugOutputLn(LINE_TRACE, "got a form");
	tokenString = theReader.getToken();
	if (tokenString.equals("LWOB")) {
		format = FORMAT_LWOB;
		parseLwob(dataLength);
	}
	else if (tokenString.equals("LWO2")) {
		format = FORMAT_LWO2;
		parseLwo2(dataLength);
	}
	else {
    	throw new IncorrectFormatException(
    	"File is not started with of FORM-length-LWO{2,B} format");
	}
	debugOutputLn(TIME, "done with parseFile in " +
		      (System.currentTimeMillis() - parseStartTime));
	return 0;
    }

	/**
	* This method is used only for testing
	*/
    static void main(String[] args) {
	String fileName;
	if (args.length == 0)
	    fileName = "cube.obj";
	else
	    fileName = args[0];

        try {
	  LwoParser theParser = new LwoParser(fileName, 0);
	}
	catch (FileNotFoundException e) {
	    System.err.println(e.getMessage());
	    e.printStackTrace();
	    System.exit(1);
	}
    }
}

 



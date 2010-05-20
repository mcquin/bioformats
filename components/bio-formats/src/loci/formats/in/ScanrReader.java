//
// ScanrReader.java
//

/*
OME Bio-Formats package for reading and converting biological file formats.
Copyright (C) 2005-@year@ UW-Madison LOCI and Glencoe Software, Inc.

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package loci.formats.in;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;

import loci.common.DataTools;
import loci.common.Location;
import loci.common.RandomAccessInputStream;
import loci.common.xml.XMLTools;
import loci.formats.CoreMetadata;
import loci.formats.FormatException;
import loci.formats.FormatReader;
import loci.formats.FormatTools;
import loci.formats.MetadataTools;
import loci.formats.meta.MetadataStore;
import loci.formats.tiff.IFD;
import loci.formats.tiff.TiffParser;

import ome.xml.r201004.enums.Correction;
import ome.xml.r201004.enums.Immersion;
import ome.xml.r201004.enums.NamingConvention;
import ome.xml.r201004.primitives.NonNegativeInteger;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * ScanrReader is the file format reader for Olympus ScanR datasets.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/components/bio-formats/src/loci/formats/in/ScanrReader.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/components/bio-formats/src/loci/formats/in/ScanrReader.java">SVN</a></dd></dl>
 *
 * @author Melissa Linkert melissa at glencoesoftware.com
 */
public class ScanrReader extends FormatReader {

  // -- Constants --

  private static final String XML_FILE = "experiment_descriptor.xml";
  private static final String EXPERIMENT_FILE = "experiment_descriptor.dat";
  private static final String ACQUISITION_FILE = "AcquisitionLog.dat";

  // -- Fields --

  private Vector<String> metadataFiles = new Vector<String>();
  private int wellRows, wellColumns;
  private int fieldRows, fieldColumns;
  private int wellCount = 0;
  private Vector<String> channelNames = new Vector<String>();
  private Hashtable<String, Integer> wellLabels =
    new Hashtable<String, Integer>();
  private String plateName;

  private String[] tiffs;
  private MinimalTiffReader reader;

  // -- Constructor --

  /** Constructs a new ScanR reader. */
  public ScanrReader() {
    super("Olympus ScanR", new String[] {"dat", "xml", "tif"});
    domains = new String[] {FormatTools.HCS_DOMAIN};
    suffixSufficient = false;
  }

  // -- IFormatReader API methods --

  /* @see loci.formats.IFormatReader#fileGroupOption(String) */
  public int fileGroupOption(String id) throws FormatException, IOException {
    return FormatTools.MUST_GROUP;
  }

  /* @see loci.formats.IFormatReader#isThisType(String, boolean) */
  public boolean isThisType(String name, boolean open) {
    String localName = new Location(name).getName();
    if (localName.equals(XML_FILE) || localName.equals(EXPERIMENT_FILE) ||
      localName.equals(ACQUISITION_FILE))
    {
      return true;
    }

    return super.isThisType(name, open);
  }

  /* @see loci.formats.IFormatReader#isThisType(RandomAccessInputStream) */
  public boolean isThisType(RandomAccessInputStream stream) throws IOException {
    TiffParser p = new TiffParser(stream);
    IFD ifd = p.getFirstIFD();
    if (ifd == null) return false;

    Object s = ifd.getIFDValue(IFD.SOFTWARE);
    if (s == null) return false;
    String software = s instanceof String[] ? ((String[]) s)[0] : s.toString();
    return software.trim().equals("National Instruments IMAQ");
  }

  /* @see loci.formats.IFormatReader#getSeriesUsedFiles(boolean) */
  public String[] getSeriesUsedFiles(boolean noPixels) {
    FormatTools.assertId(currentId, true, 1);

    Vector<String> files = new Vector<String>();
    for (String file : metadataFiles) {
      if (file != null) files.add(file);
    }

    if (!noPixels && tiffs != null) {
      int offset = getSeries() * getImageCount();
      for (int i=0; i<getImageCount(); i++) {
        if (tiffs[offset + i] != null) {
          files.add(tiffs[offset + i]);
        }
      }
    }

    return files.toArray(new String[files.size()]);
  }

  /* @see loci.formats.IFormatReader#close(boolean) */
  public void close(boolean fileOnly) throws IOException {
    super.close(fileOnly);
    if (!fileOnly) {
      if (reader != null) {
        reader.close();
      }
      reader = null;
      tiffs = null;
      plateName = null;
      channelNames.clear();
      fieldRows = fieldColumns = 0;
      wellRows = wellColumns = 0;
      metadataFiles.clear();
      wellLabels.clear();
      wellCount = 0;
    }
  }

  /**
   * @see loci.formats.IFormatReader#openBytes(int, byte[], int, int, int, int)
   */
  public byte[] openBytes(int no, byte[] buf, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    FormatTools.checkPlaneParameters(this, no, buf.length, x, y, w, h);

    int index = getSeries() * getImageCount() + no;
    if (tiffs[index] != null) {
      reader.setId(tiffs[index]);
      reader.openBytes(0, buf, x, y, w, h);
      reader.close();
    }

    return buf;
  }

  // -- Internal FormatReader API methods --

  /* @see loci.formats.FormatReader#initFile(String) */
  protected void initFile(String id) throws FormatException, IOException {
    super.initFile(id);
    if (metadataFiles.size() > 0) {
      // this dataset has already been initialized
      return;
    }

    // make sure we have the .xml file
    if (!checkSuffix(id, "xml") && isGroupFiles()) {
      Location parent = new Location(id).getAbsoluteFile().getParentFile();
      if (checkSuffix(id, "tif")) {
        parent = parent.getParentFile();
      }
      String[] list = parent.list();
      for (String file : list) {
        if (file.equals(XML_FILE)) {
          id = new Location(parent, file).getAbsolutePath();
          super.initFile(id);
          break;
        }
      }
      if (!checkSuffix(id, "xml")) {
        throw new FormatException("Could not find " + XML_FILE + " in " +
          parent.getAbsolutePath());
      }
    }
    else if (!isGroupFiles() && checkSuffix(id, "tif")) {
      TiffReader r = new TiffReader();
      r.setMetadataStore(getMetadataStore());
      r.setId(id);
      core = r.getCoreMetadata();
      metadataStore = r.getMetadataStore();

      Hashtable globalMetadata = r.getGlobalMetadata();
      for (Object key : globalMetadata.keySet()) {
        addGlobalMeta(key.toString(), globalMetadata.get(key));
      }

      r.close();
      tiffs = new String[] {id};
      reader = new MinimalTiffReader();

      return;
    }

    Location dir = new Location(id).getAbsoluteFile().getParentFile();
    String[] list = dir.list(true);

    for (String file : list) {
      Location f = new Location(dir, file);
      if (!f.isDirectory()) {
        metadataFiles.add(f.getAbsolutePath());
      }
    }

    // parse XML metadata

    String xml = DataTools.readFile(id).trim();

    // add the appropriate encoding, as some ScanR XML files use non-UTF8
    // characters without specifying an encoding

    if (xml.startsWith("<?")) {
      xml = xml.substring(xml.indexOf("?>") + 2);
    }
    xml = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" + xml;

    XMLTools.parseXML(xml, new ScanrHandler());

    Vector<String> uniqueRows = new Vector<String>();
    Vector<String> uniqueColumns = new Vector<String>();

    for (String well : wellLabels.keySet()) {
      if (!Character.isLetter(well.charAt(0))) continue;
      String row = well.substring(0, 1).trim();
      String column = well.substring(1).trim();
      if (!uniqueRows.contains(row) && row.length() > 0) uniqueRows.add(row);
      if (!uniqueColumns.contains(column) && column.length() > 0) {
        uniqueColumns.add(column);
      }
    }

    wellRows = uniqueRows.size();
    wellColumns = uniqueColumns.size();

    if (wellRows * wellColumns == 0) {
      if (wellCount <= 96) {
        wellColumns = 12;
      }
      else if (wellCount <= 384) {
        wellColumns = 24;
      }
      wellRows = wellCount / wellColumns;
      if (wellRows * wellColumns < wellCount) wellRows++;
    }

    int nChannels = getSizeC() == 0 ? channelNames.size() : getSizeC();
    if (nChannels == 0) nChannels = 1;
    int nSlices = getSizeZ() == 0 ? 1 : getSizeZ();
    int nTimepoints = getSizeT();
    int nWells = wellRows * wellColumns;
    int nPos = fieldRows * fieldColumns;
    if (nPos == 0) nPos = 1;

    // get list of TIFF files

    Location dataDir = new Location(dir, "data");
    list = dataDir.list(true);
    if (list == null) {
      // try to find the TIFFs in the current directory
      list = dir.list(true);
    }
    else dir = dataDir;
    if (nTimepoints == 0) {
      nTimepoints = list.length / (nChannels * nWells * nPos * nSlices);
      if (nTimepoints == 0) nTimepoints = 1;
    }

    tiffs = new String[nChannels * nWells * nPos * nTimepoints * nSlices];

    int next = 0;
    String[] keys = wellLabels.keySet().toArray(new String[wellLabels.size()]);
    int realPosCount = 0;
    for (int well=0; well<nWells; well++) {
      Integer w = keys.length > 0 ? wellLabels.get(keys[well]) : null;
      int wellIndex = w == null ? well + 1 : w.intValue();

      String wellPos = getBlock(wellIndex, "W");
      int originalIndex = next;

      for (int pos=0; pos<nPos; pos++) {
        String posPos = getBlock(pos + 1, "P");
        int posIndex = next;

        for (int z=0; z<nSlices; z++) {
          String zPos = getBlock(z, "Z");

          for (int t=0; t<nTimepoints; t++) {
            String tPos = getBlock(t, "T");

            for (int c=0; c<nChannels; c++) {
              for (String file : list) {
                if (file.indexOf(wellPos) != -1 && file.indexOf(zPos) != -1 &&
                  file.indexOf(posPos) != -1 && file.indexOf(tPos) != -1 &&
                  file.indexOf(channelNames.get(c)) != -1)
                {
                  tiffs[next++] = new Location(dir, file).getAbsolutePath();
                  break;
                }
              }
            }
          }
        }
        if (posIndex != next) realPosCount++;
      }
      if (next == originalIndex) {
        wellLabels.remove(keys[well]);
      }
    }

    if (wellLabels.size() > 0 && wellLabels.size() != nWells) {
      uniqueRows.clear();
      uniqueColumns.clear();
      for (String well : wellLabels.keySet()) {
        if (!Character.isLetter(well.charAt(0))) continue;
        String row = well.substring(0, 1).trim();
        String column = well.substring(1).trim();
        if (!uniqueRows.contains(row) && row.length() > 0) uniqueRows.add(row);
        if (!uniqueColumns.contains(column) && column.length() > 0) {
          uniqueColumns.add(column);
        }
      }

      wellRows = uniqueRows.size();
      wellColumns = uniqueColumns.size();
      nWells = wellRows * wellColumns;
    }
    if (realPosCount < nPos) {
      nPos = realPosCount;
    }

    reader = new MinimalTiffReader();
    reader.setId(tiffs[0]);
    int sizeX = reader.getSizeX();
    int sizeY = reader.getSizeY();
    int pixelType = reader.getPixelType();

    // we strongly suspect that ScanR incorrectly records the
    // signedness of the pixels

    switch (pixelType) {
      case FormatTools.INT8:
        pixelType = FormatTools.UINT8;
        break;
      case FormatTools.UINT8:
        pixelType = FormatTools.INT8;
        break;
      case FormatTools.INT16:
        pixelType = FormatTools.UINT16;
        break;
      case FormatTools.UINT16:
        pixelType = FormatTools.INT16;
        break;
    }

    boolean rgb = reader.isRGB();
    boolean interleaved = reader.isInterleaved();
    boolean indexed = reader.isIndexed();
    boolean littleEndian = reader.isLittleEndian();

    reader.close();

    core = new CoreMetadata[nWells * nPos];
    for (int i=0; i<getSeriesCount(); i++) {
      core[i] = new CoreMetadata();
      core[i].sizeC = nChannels;
      core[i].sizeZ = nSlices;
      core[i].sizeT = nTimepoints;
      core[i].sizeX = sizeX;
      core[i].sizeY = sizeY;
      core[i].pixelType = pixelType;
      core[i].rgb = rgb;
      core[i].interleaved = interleaved;
      core[i].indexed = indexed;
      core[i].littleEndian = littleEndian;
      core[i].dimensionOrder = "XYCTZ";
      core[i].imageCount = nSlices * nTimepoints * nChannels;
    }

    MetadataStore store = makeFilterMetadata();
    MetadataTools.populatePixels(store, this);

    int nFields = fieldRows * fieldColumns;

    for (int i=0; i<getSeriesCount(); i++) {
      MetadataTools.setDefaultCreationDate(store, id, i);

      int field = i % nFields;
      int well = i / nFields;

      int wellRow = well / wellColumns;
      int wellCol = well % wellColumns;

      store.setWellColumn(new NonNegativeInteger(wellCol), 0, well);
      store.setWellRow(new NonNegativeInteger(wellRow), 0, well);

      store.setWellSampleIndex(new NonNegativeInteger(i), 0, well, field);
      String imageID = MetadataTools.createLSID("Image", i);
      store.setWellSampleImageRef(imageID, 0, well, field);
      store.setImageID(imageID, i);

      String name = "Well " + (well + 1) + ", Field " + (field +1) + " (Spot " +
        (i + 1) + ")";
      store.setImageName(name, i);
    }

    if (getMetadataOptions().getMetadataLevel() == MetadataLevel.ALL) {
      // populate LogicalChannel data

      for (int i=0; i<getSeriesCount(); i++) {
        for (int c=0; c<getSizeC(); c++) {
          store.setChannelName(channelNames.get(c), i, c);
        }
      }

      if (wellRows > 26) {
        store.setPlateRowNamingConvention(NamingConvention.NUMBER, 0);
        store.setPlateColumnNamingConvention(NamingConvention.LETTER, 0);
      }
      else {
        store.setPlateRowNamingConvention(NamingConvention.LETTER, 0);
        store.setPlateColumnNamingConvention(NamingConvention.NUMBER, 0);
      }
      store.setPlateName(plateName, 0);
    }
  }

  // -- Helper class --

  class ScanrHandler extends DefaultHandler {
    private String key, value;
    private String qName;

    private String wellIndex;

    // -- DefaultHandler API methods --

    public void characters(char[] ch, int start, int length) {
      String v = new String(ch, start, length);
      if (v.trim().length() == 0) return;
      if (qName.equals("Name")) {
        key = v;
      }
      else if (qName.equals("Val")) {
        value = v.trim();
        addGlobalMeta(key, value);

        if (key.equals("columns/well")) {
          fieldColumns = Integer.parseInt(value);
        }
        else if (key.equals("rows/well")) {
          fieldRows = Integer.parseInt(value);
        }
        else if (key.equals("# slices")) {
          core[0].sizeZ = Integer.parseInt(value);
        }
        else if (key.equals("timeloop real")) {
          core[0].sizeT = Integer.parseInt(value);
        }
        else if (key.equals("name")) {
          channelNames.add(value);
        }
        else if (key.equals("plate name")) {
          plateName = value;
        }
        else if (key.equals("idle")) {
          int lastIndex = channelNames.size() - 1;
          if (value.equals("0") &&
            !channelNames.get(lastIndex).equals("Autofocus"))
          {
            core[0].sizeC++;
          }
          else channelNames.remove(lastIndex);
        }
        else if (key.equals("well selection table + cDNA")) {
          if (Character.isDigit(value.charAt(0))) {
            wellIndex = value;
            wellCount++;
          }
          else {
            wellLabels.put(value, new Integer(wellIndex));
          }
        }
      }
    }

    public void startElement(String uri, String localName, String qName,
      Attributes attributes)
    {
      this.qName = qName;
    }

  }

  // -- Helper methods --

  private String getBlock(int index, String axis) {
    String b = String.valueOf(index);
    while (b.length() < 5) b = "0" + b;
    return axis + b;
  }

}

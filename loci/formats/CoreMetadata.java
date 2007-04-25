//
// CoreMetadata.java
//

/*
LOCI Bio-Formats package for reading and converting biological file formats.
Copyright (C) 2005-@year@ Melissa Linkert, Curtis Rueden, Chris Allan,
Eric Kjellman and Brian Loranger.

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU Library General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Library General Public License for more details.

You should have received a copy of the GNU Library General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package loci.formats;

import java.util.*;

/** Encompasses core metadata values. */
public class CoreMetadata {
  public int[] sizeX, sizeY, sizeZ, sizeC, sizeT, pixelType, imageCount;
  public int[][] cLengths;
  public String[][] cTypes;
  public String[] currentOrder;
  public boolean[] orderCertain, rgb, littleEndian, interleaved;
  public Hashtable[] seriesMetadata;

  public CoreMetadata(int series) {
    sizeX = new int[series];
    sizeY = new int[series];
    sizeZ = new int[series];
    sizeC = new int[series];
    sizeT = new int[series];
    pixelType = new int[series];
    imageCount = new int[series];
    cLengths = new int[series][];
    cTypes = new String[series][];
    currentOrder = new String[series];
    orderCertain = new boolean[series];
    rgb = new boolean[series];
    littleEndian = new boolean[series];
    interleaved = new boolean[series];
    seriesMetadata = new Hashtable[series];
    for (int i=0; i<series; i++) seriesMetadata[i] = new Hashtable();
  }

}

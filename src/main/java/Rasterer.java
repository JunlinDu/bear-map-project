import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.lang.Math;

/**
 * This class provides all code necessary to take a query box and produce
 * a query result.
 * @author Junlin Du
 */
public class Rasterer {

    private double lonCoverage;
    private double latCoverage;

    /*
     * For reference and testing purposes. LonDPP of 8 levels of tile:
     * [3.4332275390625E-4, 1.71661376953125E-4, 8.58306884765625E-5, 4.291534423828125E-5,
     * 2.1457672119140625E-5, 1.0728836059570312E-5, 5.364418029785156E-6, 2.682209014892578E-6]
     */
    private ArrayList<Double>  zoomLevelLonDPPs;

    public Rasterer() {
        lonCoverage = MapServer.ROOT_LRLON - MapServer.ROOT_ULLON;
        latCoverage = MapServer.ROOT_ULLAT - MapServer.ROOT_LRLAT;
        zoomLevelLonDPPs = new ArrayList<Double>();

        for (int i = 0, l = 1; i < 8; i++, l*=2) {
            // calculates and initiates an array list for the LonDPP of 8 levels of zoom
            zoomLevelLonDPPs.add(lonCoverage / (l * MapServer.TILE_SIZE));
        }
    }


    /**
     * Takes a user query and finds the grid of images that best matches the query. These
     * images will be combined into one big image (rastered) by the front end. <br>
     *
     * @param params Map of the HTTP GET request's query parameters - the query box and
     *               the user viewport width and height.
     *
     * @return A map of results for the front end as specified: <br>
     * "render_grid"   : String[][], the files to display. <br>
     * "raster_ul_lon" : Number, the bounding upper left longitude of the rastered image. <br>
     * "raster_ul_lat" : Number, the bounding upper left latitude of the rastered image. <br>
     * "raster_lr_lon" : Number, the bounding lower right longitude of the rastered image. <br>
     * "raster_lr_lat" : Number, the bounding lower right latitude of the rastered image. <br>
     * "depth"         : Number, the depth of the nodes of the rastered image <br>
     * "query_success" : Boolean, whether the query was able to successfully complete <br>
     */
    public Map<String, Object> getMapRaster(Map<String, Double> params) {
        // prints out the HTTP GET request's query parameters
        System.out.println(params);

        return constructResult(params);
    }

    private Map<String, Object> constructResult(Map<String, Double> params) {
        Map<String, Object> results = new HashMap<>();
        int depth = calcDepth((params.get("lrlon") - params.get("ullon"))/ params.get("w"));

        double[] uLTileULCoor = calcTileULCoor(depth, params.get("ullon"), params.get("ullat"));
        double[] lRTileULCoor = calcTileULCoor(depth, params.get("lrlon"), params.get("lrlat"));

//        System.out.println(Arrays.deepToString(constructTile(depth, uLTileULCoor, lRTileULCoor)));
//        System.out.println(constructTile(depth, uLTileULCoor, lRTileULCoor).length);

        return results;
    }


//    private String[][] constructTile(int depth, double[] uLTileULCoor, double[] lRTileULCoor) {
//        int[] uLTile = identifyFileNum(depth, uLTileULCoor[0], uLTileULCoor[1]);
//        int[] lRTile = identifyFileNum(depth, lRTileULCoor[0], lRTileULCoor[1]);
//        System.out.println(lRTile[0]);
//        System.out.println(lRTile[1]);
//
//        int numOfHorizontalTile = lRTile[0] - uLTile[0] + 1;
//        int numOfVerticalTile = lRTile[1] - uLTile[1] + 1;
//
//        String [][] result = new String[numOfHorizontalTile][numOfVerticalTile];
//        for (int y = 0; y < numOfVerticalTile - 1; y++) {
//            for (int x = 0; x < numOfHorizontalTile - 1; x++) {
//                result[y][x] = "d" + depth + "_x" + String.valueOf(uLTile[0] + x) + "_y" + String.valueOf(uLTile[1] + y) + ".png";
//            }
//        }
//
//        return result;
//    }


    /** Takes the query LonDPP and returns the level of depth corresponding to the query
     *
     * @param queryLonDPP The LonDPP of the query box
     * @return the corresponding level of depth
     */
    private int calcDepth(double queryLonDPP) {
        int i = 0;
        for (double ldpp : zoomLevelLonDPPs) {
            if (ldpp < queryLonDPP || i == zoomLevelLonDPPs.size() - 1) break;
            i++;
        }
        return i;
    }


    /**
     * This function search for the longitude or latitude of the appropriate
     * tile based on the current zoom level and a given coordinate.
     * This is a recursive function utilizes the principles of binary
     * search. The upperBound and lowerBound can be thought of as two ends
     * of a sorted array, where the depth determines the number of elements
     * in the array.
     *
     * @param depth the current level of zoom (depth)
     * @param coordinate the coordinate of the query box
     * @param lowerBound the coordinate of the lower bound of the bounding box for a single dimension
     * @param upperBound the coordinate of the upper bound of the bounding box for a single dimension
     *
     * @return the coordinate of a rastered tile
     *
    * */
    private double searchSingleDimCoord(int depth, double coordinate, double lowerBound, double upperBound) {

        if (depth != 0) {
            double mid = lowerBound + (upperBound - lowerBound) / 2;

            // If the coordinate happens to coincide with the mid point
            // This may happen very rarely.
            if (coordinate == mid) return mid;

            // If the coordinate is smaller than the mid point, i.e.
            // is on the left side of the imaginary array.
            if (coordinate < mid)
                return searchSingleDimCoord(depth - 1, coordinate, lowerBound, mid);

            // The only possibility left is that the coordinate is
            // greater that the mid point.
            return searchSingleDimCoord(depth - 1, coordinate, mid, upperBound);
        }

        // This will be the coordinate of the rastered tile.
        // The coordinate is the greatest of an array (imaginary)
        // of tiles that is smaller than the given coordinate,
        // thus have the requested region covered.
        return lowerBound;
    }


    /**
    * This function returns the upper left longitudinal and latitudinal
     * coordinates of a tile that covers the requested coordinate in the
     * current zoom level.
     *
     * @param depth current level of zoom
     * @param longitude requested longitude
     * @param latitude requested latitude
     *
     * @return a double array: [tileUlLon, tileUlLat]
    * */
    private double[] calcTileULCoor(int depth, double longitude, double latitude) {
        // The latitudinal distance covered by a tile
        double tileLatCoverage = this.latCoverage / Math.pow(2, depth);
        return new double[]{
                searchSingleDimCoord
                        (depth, longitude, MapServer.ROOT_ULLON, MapServer.ROOT_LRLON),
                searchSingleDimCoord
                        (depth, latitude, MapServer.ROOT_LRLAT, MapServer.ROOT_ULLAT)
                        + tileLatCoverage};
    }


    /**
     * This function identifies the x and y axis of the tile image based on
     * a given longitude and latitude.
     *
     * @param depth current zoom level
     * @param tileUlLon the longitude of a tile's upper left
     * @param tileUlLat the latitude of a tile's upper left
     *
     * @return [x, y]
     * */
    private int[] identifyFileNum(int depth, double tileUlLon, double tileUlLat) {
        int x = 0;
        int y = 0;
        double numOfTiles = Math.pow(2, depth);
        double longDisFromBound = tileUlLon - MapServer.ROOT_ULLON;
        double latDisFromBound = MapServer.ROOT_ULLAT - tileUlLat;

        if (longDisFromBound != 0){
            x = (int) Math.round((longDisFromBound / this.lonCoverage) * numOfTiles);
        }

        if (latDisFromBound != 0) {

            y = (int) Math.round((latDisFromBound / this.latCoverage) * numOfTiles);
        }

        return new int[] {x, y};
    }


    /** The Main function is used for testing purposes.*/
    public static void main (String[] args) {
        Rasterer rasterer = new Rasterer();

        int[] a = rasterer.identifyFileNum(7, -122.24212646484375, 37.87701580361881);
//        System.out.println(String.valueOf(a[0]) + " " + String.valueOf(a[1]));
//        System.out.println("\n\n");
//
//        a = rasterer.identifyFileNum(2, -122.2998046875, 37.87484726881516);
//        System.out.println(String.valueOf(a[0]) + " " + String.valueOf(a[1]));
//        System.out.println("\n\n");
//
//        a = rasterer.identifyFileNum(1, -122.2998046875, 37.892195547244356);
//        System.out.println(String.valueOf(a[0]) + " " + String.valueOf(a[1]));
//        System.out.println("\n\n");
//
//        a = rasterer.identifyFileNum(1, -122.2998046875, 37.892195547244356);
//        System.out.println(String.valueOf(a[0]) + " " + String.valueOf(a[1]));
//        System.out.println("\n\n");

        double[] d = rasterer.calcTileULCoor(7, -122.24053369025242, 37.87548268822065);
        a = rasterer.identifyFileNum(7, d[0], d[1]);
        System.out.println(String.valueOf(a[0]) + " " + String.valueOf(a[1]));
        System.out.println("########");
        d = rasterer.calcTileULCoor(2, -122.2104604264636, 37.8318576119893);
        a = rasterer.identifyFileNum(2, d[0], d[1]);
        System.out.println(String.valueOf(a[0]) + " " + String.valueOf(a[1]));
        System.out.println("########");
        d = rasterer.calcTileULCoor(1, -122.20908713544797, 37.848731523430196);
        a = rasterer.identifyFileNum(1, d[0], d[1]);
        System.out.println(String.valueOf(a[0]) + " " + String.valueOf(a[1]));
    }

}

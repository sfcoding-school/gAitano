package ulisse.test3.MarkerDet;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;



import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvException;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Point3;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;



/**
 * Marker detected in an image, it must be a four-squared contour with black border and
 * a valid code inside it.
 *
 */
public class Marker extends Vector<Point> implements Comparable<Marker>{



    private static final long serialVersionUID = 1L;
    protected int id;
    protected float ssize;
    private int rotations;

    public Code code; // a matrix of integer representing the code (see the class to further explanation)

    private Mat mat; // the cvMat of the CANONICAL marker (not the one taken from the capture)
    private Mat Rvec;
    private Mat Tvec;

    public Marker(float size){
        id = -1;
        ssize = size;
        // TODO revise how the mats are initialized, better to create them with the proper type
        // code more legible
        code = new Code();
        Rvec = new Mat(3,1,CvType.CV_64FC1);
        Tvec = new Mat(3,1,CvType.CV_64FC1);
        mat = new Mat();
    }

    public void draw(Mat in, Scalar color, int lineWidth, boolean writeId){
        if (size()!=4)
            return;

        // TODO loop¿?
        for(int i=0;i<4;i++)
            Core.line(in, this.get(i), this.get((i+1)%4), color, lineWidth);
        if(writeId){
            String cad = new String();
            cad = "id="+id;
            // determine the centroid
            Point cent = new Point(0,0);
            for(int i=0;i<4;i++){
                cent.x += this.get(i).x;
                cent.y += this.get(i).y;
            }

            cent.x/=4.;
            cent.y/=4.;
            Log.e("Marker Centro"," " + cent.x + " " + cent.y);
            Core.putText(in, cad, cent, Core.FONT_HERSHEY_SIMPLEX, 0.5, color,2);

        }
    }

    public Point findCenter(){
        Point cent = new Point(0,0);
        for(int i=0;i<4;i++){
            cent.x += this.get(i).x;
            cent.y += this.get(i).y;
        }
        cent.x/=4;
        cent.y/=4;
        return cent;
    }

    /**
     * returns the perimeter of the marker, the addition of the distances between
     * consecutive points.
     * @return the perimeter.
     */
    public double perimeter(){
        double sum=0;
        for(int i=0;i<size();i++){
            Point current = get(i);
            Point next = get((i+1)%4);
            sum+=Math.sqrt( (current.x-next.x)*(current.x-next.x) +
                    (current.y-next.y)*(current.y-next.y));
        }
        return sum;
    }

    /**
     * method to access the id, this only returns the id. Doesn't calculate it.
     * @return the marker id.
     */
    public int getMarkerId(){
        return id;
    }

    public static Mat createMarkerImage(int id,int size) throws CvException {
        if (id>=1024)
            throw new CvException("id out of range");
        Mat marker = new Mat(size,size, CvType.CV_8UC1, new Scalar(0));
        //for each line, create
        int swidth=size/7;
        int ids[]={0x10,0x17,0x09,0x0e};
        for (int y=0;y<5;y++) {
            int index=(id>>2*(4-y)) & 0x0003;
            int val=ids[index];
            for (int x=0;x<5;x++) {
                Mat roi=marker.submat((x+1)*swidth, (x+2)*swidth,(y+1)*swidth,(y+2)*swidth);// TODO check
                if ( (( val>>(4-x) ) & 0x0001) != 0 )
                    roi.setTo(new Scalar(255));
                else
                    roi.setTo(new Scalar(0));
            }
        }
        return marker;
    }



    protected void setMat(Mat in){
        in.copyTo(mat);
    }

    /**
     * construct the matrix of integers from the mat stored.
     */
    protected void extractCode(){
        int rows = mat.rows();
        int cols = mat.cols();
        assert(rows == cols);
        Mat grey = new Mat();
        // change the color space if necessary
        if(mat.type() == CvType.CV_8UC1)
            grey = mat;
        else
            Imgproc.cvtColor(mat, grey, Imgproc.COLOR_RGBA2GRAY);
        // apply a threshold
        Imgproc.threshold(grey, grey, 125, 255, Imgproc.THRESH_BINARY|Imgproc.THRESH_OTSU);
        // the swidth is the width of each row
        int swidth = rows/7;
        // we go through all the rows
        for(int y=0;y<7;y++){
            for(int x=0;x<7;x++){
                int Xstart = x*swidth;
                int Ystart = y*swidth;
                Mat square = grey.submat(Xstart, Xstart+swidth, Ystart, Ystart+swidth);
                int nZ = Core.countNonZero(square);
                if(nZ > (swidth*swidth)/2)
                    code.set(x, y, 1);
                else
                    code.set(x,y,0);
            }
        }
    }

    /**
     * Return the id read in the code inside a marker. Each marker is divided into 7x7 regions
     * of which the inner 5x5 contain info, the border should always be black. This function
     * assumes that the code has been extracted previously.
     * @param //in a marker
     * @return the id of the marker
     */
    protected int calculateMarkerId(){
        // check all the rotations of code
        Code[] rotations = new Code[4];
        rotations[0] = code;
        int[] dists = new int[4];
        dists[0] = hammDist(rotations[0]);
        int[] minDist = {dists[0],0};
        for(int i=1;i<4;i++){
            // rotate
            rotations[i] = Code.rotate(rotations[i-1]);
            dists[i] = hammDist(rotations[i]);
            if(dists[i] < minDist[0]){
                minDist[0] = dists[i];
                minDist[1] = i;
            }
        }
        this.rotations = minDist[1]; //Log.e("Marker","" +minDist[0]);
        if(minDist[0] != 0){
            return -1; // matching id not found
        }
        else{
            this.id = 0;//mat2id(rotations[minDist[1]]);
        }

        return id;
    }

    /**
     * this functions checks if the whole border of the marker is black
     * @return true if the border is black, false otherwise
     */
    protected boolean checkBorder(){
        for(int i=0;i<7;i++){
            // normally we'll only check first and last square
            int inc = 6;
            if(i==0 || i==6)// in first and last row the whole row must be checked
                inc = 1;
            for(int j=0;j<7;j+=inc)
                if(code.get(i, j)==1)
                    return false;
        }
        return true;
    }

    /**
     * Calculate 3D position of the marker based on its translation and rotation matrix.
     * This method fills in these matrix properly.
     * @param camMatrix
     * @param //distCoeff
     */
    protected void calculateExtrinsics(Mat camMatrix, Mat distCoeffs, float sizeMeters){// TODO size neccesary?
        // TODO check params

        // set the obj 3D points
        double halfSize = sizeMeters/2.0;
        List<Point3> objPoints = new ArrayList<Point3>();
        objPoints.add(new Point3(-halfSize, -halfSize,0));
        objPoints.add(new Point3(-halfSize,  halfSize,0));
        objPoints.add(new Point3( halfSize,  halfSize,0));
        objPoints.add(new Point3( halfSize, -halfSize,0));


//              rotateXAxis(Rvec);// TODO necesario??
    }

    private int hammDist(Code code){
        int ids[][] = {
                //{1,1,1,1,1},
                //{1,0,1,1,1}
//                {1,1,0,0,1},
//                {1,1,0,1,1},
//                {1,1,1,1,1}
              // {1,0,0,0,1},{1,0,0,0,0},{1,0,0,0,0},{1,0,0,0,0},{1,0,0,0,1}
                {1,1,1,1,1},{1,0,0,0,1},{1,0,0,0,1},{1,0,0,0,1},{1,1,1,1,1}
        };
        int dist = 0;

//        for(int y=0;y<5;y++){
//            int minSum = Integer.MAX_VALUE;
//            // hamming distance to each possible word
//            for(int p=0;p<4;p++){
//                int sum=0;
//                String teest = "";
//                String teest2 = "";
//                for(int x=0;x<5;x++) {
//                    sum += code.get(y + 1, x + 1) == ids[p][x] ? 0 : 1;
//                    teest += ids[p][x];
//                    teest2 += code.get(y + 1, x + 1);
//                }
//                Log.e("Marker", teest  + " " + teest2);
//                minSum = sum<minSum? sum:minSum;
//
//            }
//            dist+=minSum;
//        }

        for(int y=0;y<5;y++){

                for(int x=0;x<5;x++)
                    if (code.get(y+1,x+1)!=ids[y][x]) {
                        //Log.e("Marker", "Diverso ");
                        return -1;
                    }
        }
        //Log.e("Marker", "Uguale");
        return 0;
//        for(int y=0;y<5;y++){
//            int minSum = 0;
//            for(int p=0;p<4;p++){
//                int sum=0;
//                for(int x=0;x<5;x++) {
//                    minSum += code.get(y + 1, x + 1) == ids[p][x] ? 0 : 1;
//                }
//                //Log.e("Marker", teest  + " " + teest2);
//                //minSum = sum<minSum? sum:minSum;
//
//            }
//            dist+=minSum;
//        }
        //return dist;
    }

    private int mat2id(Code code){
        int val=0;
        for(int y=1;y<6;y++){
            val<<=1;
            if(code.get(y,2) == 1)
                val |= 1;
            val<<=1;
            if(code.get(y,4) == 1)
                val |= 1;
        }
        return val;
    }

    public int getRotations(){
        return this.rotations;
    }

    @Override
    public int compareTo(Marker other) {
        if(id < other.id)
            return -1;
        else if(id > other.id)
            return 1;
        return 0;
    }


}

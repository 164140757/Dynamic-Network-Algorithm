package Algorithms.Graph;

import Algorithms.Graph.Network.AdjList;
import org.jblas.DoubleMatrix;
import org.jgrapht.alg.util.Pair;

import java.io.IOException;
import java.util.Arrays;

/**
 * For the high computation performance, the matrix (DoubleMatrix) is based on
 * jblas-1.2.4 jar
 * please visit http://jblas.org/javadoc/ for further information
 *
 * <p>
 * Hungarian algorithm is the one used to address the problems in allocation within
 * minimum/maximum resources.
 * </p>
 * <br>
 * <p>
 * The result will be a vector used to identify the optimized allocation strategy.
 * </p>
 * <br>
 * <p>
 * Please refer to http://csclab.murraystate.edu/~bob.pilgrim/445/munkres.html for the clear description of
 * the algorithm.
 * </p>
 **/
public class Hungarian {
    protected DoubleMatrix mat;
    protected int matCol;
    protected int matRow;


    // record the mapping result
    private int[] result;
    private ZeroMasks[][] maskMat;
    private boolean[] R_cover;
    private boolean[] C_cover;
    // path for augmenting path algorithm
    private int[][] path;
    private int pathRow;
    private int pathCol;

    private enum ZeroMasks {starred, primed}
    public enum ProblemType {maxLoc, minLoc}

    protected Hungarian(DoubleMatrix mat, ProblemType type) throws IOException {
        init(mat, type);
        hungarian();
    }

    public Hungarian(AdjList list, ProblemType type) throws IOException {
        mat = list.getMatrix();
        init(mat, type);
        hungarian();
    }

    private void hungarian() throws IOException {
        boolean done = false;
        int step = 1;
        while(!done){
            switch(step){
                case 1:
                    step = subtractRowMinimal();
                    break;
                case 2:
                    step = starZeros();
                    break;
                case 3:
                    step = coverStarredZeros();
                    break;
                case 4:
                    step = primeZeros();
                    break;
                case 5:
                    step = augmentingPath();
                    break;
                case 6:
                    step = adjustMat();
                    break;
                case 7:
                    result = finish();
                    done = true;
                    break;
            }
        }

    }


    private void init(DoubleMatrix mat, ProblemType type) {
        if (type == ProblemType.maxLoc) {
            this.mat = mat.neg();
        } else {
            this.mat = mat;
        }
        matCol = mat.columns;
        matRow = mat.rows;
        maskMat = new ZeroMasks[matRow][matCol];
        R_cover = new boolean[matRow];
        C_cover = new boolean[matCol];
        path = new int[matCol][2];
        result = new int[matRow];
        Arrays.fill(result, -1);
    }


    /**
     * STEP 1:
     * subtract from every element the minimum value of its row
     */
    protected int subtractRowMinimal() {
        int rows = mat.getRows();
        for (int r = 0; r < rows; r++) {
            DoubleMatrix curRow = mat.getRow(r);
            double minRowVal = curRow.min();
            mat.putRow(r, curRow.sub(minRowVal));
        }
        return 2;
    }

    /**
     * STEP 2:
     * Find zeros in the resulting matrix that is uncovered.
     */
    protected int starZeros() {
        for (int r = 0; r < matRow; r++) {
            for (int c = 0; c < matCol; c++) {
                if (mat.get(r, c) == 0 && !R_cover[r] && !C_cover[c]) {
                    maskMat[r][c] = ZeroMasks.starred;
                    R_cover[r] = true;
                    C_cover[c] = true;
                }
            }
        }
        initCoverVets();
        return 3;
    }

    private void initCoverVets() {
        Arrays.fill(R_cover, false);
        Arrays.fill(C_cover, false);
    }

    /**
     * STEP 3:
     * Cover each column containing a starred zero.
     * If all columns are covered, the starred zeros describe a complete set of unique assignments.
     */
    protected int coverStarredZeros() {
        for (int r = 0; r < matRow; r++) {
            for (int c = 0; c < matCol; c++) {
                if (maskMat[r][c] == ZeroMasks.starred) {
                    C_cover[c] = true;
                }
            }
        }
        int colCoveredCount = detectC_cover();
        if(colCoveredCount >= matCol || colCoveredCount >= matRow){
            return 7;
        }
        else{
            return 4;
        }
    }

    private int detectC_cover() {
        int res = 0;
        for (boolean b :
                C_cover) {
            if (b) {
                res++;
            }
        }
        return res;
    }

    /**
     * STEP 4:
     * Find a noncovered zero and prime it.
     * If there is no starred zero in the row containing this primed zero,
     * Go to Step 5.  Otherwise, cover this row and uncover the column containing the starred zero. Continue in this manner until there are no uncovered zeros left.
     * Save the smallest uncovered value and Go to Step 6
     *
     */
    protected int primeZeros(){
        int row = 0;
        int col = 0;
        while(true){
            Pair<Integer,Integer> res =findAUncoveredZero(row,col);
            if(res == null){
                return 6;
                // step 6
            }
            else{
                int primedRow = res.getFirst();
                int primedCol = res.getSecond();
                // record current location
                row = primedRow;
                col = primedCol;
                maskMat[primedRow][primedCol] = ZeroMasks.primed;
                int starredCol = findStarredInRow(primedRow);
                if(starredCol != -1){
                    R_cover[row] = true;
                    C_cover[col] = false;
                }
                else{
                    //step 5
                    pathRow = row;
                    pathCol = col;
                    return 5;
                }
            }
        }
    }

    private Pair<Integer,Integer> findAUncoveredZero(int row,int col) {
        for(int r = row; r < matRow; r++){
            for (int c = col; c < matCol; c++) {
                if(mat.get(r,c) == 0 && !C_cover[c] && !R_cover[r]){
                    return new Pair<>(r, c);
                }
            }
        }
        return null;
    }


    private int findStarredInRow(int row){
        for (int c = 0; c < matCol; c++) {
            if(maskMat[row][c] == ZeroMasks.starred){
                return c;
            }
        }
        return -1;
    }
    /**
     * STEP 5:
     * Construct a series of alternating primed and starred zeros as follows.
     * Let Z0 represent the uncovered primed zero found in Step 4.
     * Let Z1 denote the starred zero in the column of Z0 (if any).
     * Let Z2 denote the primed zero in the row of Z1 (there will always be one).
     * Continue until the series terminates at a primed zero that has no starred zero in
     * its column.  Unstar each starred zero of the series, star each primed zero of the series,
     * erase all primes and uncover every line in the matrix.  Return to Step 3
     *<br>
     * <p>augmenting path algorithm (for solving the maximal matching problem) </p>
     */
    protected int augmentingPath(){
        int pathCount = 1;
        path[0][0] = pathRow;
        path[0][1] = pathCol;
        boolean done = false;
        while(!done){
            int rowIndex = findStarredInCol(path[pathCount-1][1]);
            if(rowIndex > -1){
                pathCount ++;
                path[pathCount-1][0] = rowIndex;
                path[pathCount-1][1] = path[pathCount-2][1];
            }
            else{
                done = true;
            }
            if(!done){
                int colIndex = findPrimeInRow(path[pathCount-1][0]);
                pathCount ++;
                path[pathCount-1][0] = path[pathCount-2][0];
                path[pathCount-1][1] = colIndex;
            }
        }
        augmentPath(pathCount);
        initCoverVets();
        erasePrimes();
        return 3;
    }

    private void erasePrimes() {
        for (int r = 0; r < matRow; r++) {
            for (int c = 0; c < matCol; c++) {
                if(maskMat[r][c] == ZeroMasks.primed){
                    maskMat[r][c] = null;
                }
            }
        }
        // step 3
    }


    private void augmentPath(int pathCount) {
        for (int p = 0; p < pathCount; p++) {
                if(maskMat[path[p][0]][path[p][1]] == ZeroMasks.starred){
                    maskMat[path[p][0]][path[p][1]] = null;
                }
                else{
                    maskMat[path[p][0]][path[p][1]] = ZeroMasks.starred;
                }
        }
    }

    private int findPrimeInRow(int row) {
        for (int c = 0; c < matCol; c++) {
            if(maskMat[row][c] == ZeroMasks.primed){
                return c;
            }
        }
        return -1;
    }

    private int findStarredInCol(int col) {
        for (int r = 0; r < matRow; r++) {
            if(maskMat[r][col] == ZeroMasks.starred){
                return r;
            }
        }
        return -1;
    }
    /**
     * STEP 6:
     *
     * Add the value found in Step 4 to every element of each covered row, and subtract it from
     * every element of each uncovered column.  Return to Step 4 without altering any stars,
     * primes, or covered lines. Notice that this step uses the smallest uncovered value
     * in the cost matrix to modify the matrix.  Even though this step refers to the value
     * being found in Step 4 it is more convenient to wait until you reach Step 6 before
     * searching for this value.  It may seem that since the values in the cost matrix are
     * being altered, we would lose sight of the original problem.  However, we are only
     * changing certain values that have already been tested and found not to be elements
     * of the minimal assignment.  Also we are only changing the values by an amount equal to
     * the smallest value in the cost matrix,
     * so we will not jump over the optimal (i.e. minimal assignment) with this change
     */
    protected int adjustMat(){
        double minVal = findSmallestOfUncovered();
        for (int r = 0; r < matRow; r++) {
            for (int c = 0; c < matCol; c++) {
                double val = mat.get(r,c);
                if(R_cover[r]){
                    mat.put(r,c,val+minVal);
                }
                if(!C_cover[c]){
                    mat.put(r,c,val-minVal);
                }
            }
        }
        return 4;
    }

    private double findSmallestOfUncovered() {
        double tpVal = Double.MAX_VALUE;
        for (int r = 0; r < matRow; r++) {
            for(int c = 0; c< matCol;c++){
                if(!R_cover[r] && !C_cover[c]){
                    double val = mat.get(r,c);
                    if( val < tpVal){
                        tpVal = val;
                    }
                }
            }
        }
        return tpVal;
    }
    /**
     * STEP 7:
     * star zero is the assignment, output the int[] result for rows
     */
    protected int[] finish(){
        int[] result = new int[matRow];
        for (int r = 0; r < matRow; r++) {
            int indexCol = findStarredInRow(r);
            result[r] = indexCol;
        }
        return result;
    }

    public int[] getResult() {
        assert (result != null);
        return result;
    }
}
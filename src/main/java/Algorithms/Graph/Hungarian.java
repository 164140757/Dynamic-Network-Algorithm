package Algorithms.Graph;


import DS.Matrix.StatisticsMatrix;
import org.jgrapht.alg.util.Pair;

import java.util.Arrays;
import java.util.Vector;
import java.util.logging.Logger;

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
public class Hungarian<T> {
    protected StatisticsMatrix mat;
    protected int cols;
    protected int rows;


    // record the mapping result
    private int[] result;
    private ZeroMasks[][] maskMat;
    private boolean[] R_cover;
    private boolean[] C_cover;
    // path for augmenting path algorithm
    private int[][] path;
    private int pathRow;
    private int pathCol;
    private Vector<Integer> rowIndexes;
    private Vector<Integer> colIndexes;

    private enum ZeroMasks {starred, primed}

    public enum ProblemType {maxLoc, minLoc}

    // debug
    public Logger logger;

    protected Hungarian(StatisticsMatrix mat, ProblemType type) {
        init(mat, type);
    }

    public Hungarian(SimMat<T> mat, ProblemType type) {
        this.mat = mat.getMat();
        init(this.mat, type);
    }

    public void run() {
        hungarian();
    }

    private void hungarian() {
        boolean done = false;
        int step = 1;
        while (!done) {
            switch (step) {
                case 1 : {
                    step = subtractRowMinimal();
                    break;
                }
                case 2 :{
                    step = starZeros();
                    break;
                }
                case 3 : {
                    step = coverStarredZeros();
                    break;
                }
                case 4 :{
                    step = primeZeros();
                    break;
                }
                case 5 :{
                    step = augmentingPath();
                    break;
                }
                case 6 :{
                    step = adjustMat();
                    break;
                }
                case 7 :{
                    result = finish();
                    done = true;
                    break;
                }
            }
        }

    }


    private void init(StatisticsMatrix mat, ProblemType type) {
        if (type == ProblemType.maxLoc) {
            this.mat = mat.negative();
        } else {
            this.mat = mat;
        }
        cols = mat.numCols();
        rows = mat.numRows();
        maskMat = new ZeroMasks[rows][cols];
        R_cover = new boolean[rows];
        C_cover = new boolean[cols];
        path = new int[cols][2];
        result = new int[rows];
        rowIndexes = new Vector<>(rows);
        colIndexes = new Vector<>(cols);
        for (int r = 0; r < rows; r++) {
            rowIndexes.add(r);
        }
        for (int c = 0; c < cols; c++) {
            colIndexes.add(c);
        }
        Arrays.fill(result, -1);
    }


    /**
     * STEP 1:
     * subtract from every element the minimum value of its row
     */
    protected int subtractRowMinimal() {
        // parallel here there is no interference and no stateful lambda
        //https://docs.oracle.com/javase/tutorial/collections/streams/parallelism.html
        rowIndexes.parallelStream().forEach(r -> {
            StatisticsMatrix curRow = mat.getRow(r);
            double minRowVal = curRow.getMinDouble();
            mat.rRow(r,curRow.minus(minRowVal));
        });
        return 2;
    }

    /**
     * STEP 2:
     * Find zeros in the resulting matrix that is uncovered.
     */
    protected int starZeros() {
        // no parallel here stateful R_cover and C_cover
        rowIndexes.forEach(r -> colIndexes.parallelStream().forEach(c -> {
            if (mat.get(r, c) == 0 && !R_cover[r] && !C_cover[c]) {
                maskMat[r][c] = ZeroMasks.starred;
                R_cover[r] = true;
                C_cover[c] = true;
            }
        }));
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
        // parallel here there is no interference and no stateful lambda
        //https://docs.oracle.com/javase/tutorial/collections/streams/parallelism.html
//        logInfo("Hungarian step 3-Cover each column containing a starred zero...");
        rowIndexes.parallelStream().forEach(r -> colIndexes.parallelStream().forEach(c -> {
            if (maskMat[r][c] == ZeroMasks.starred) {
                C_cover[c] = true;
            }
        }));
        int colCoveredCount = detectC_cover();
        if (colCoveredCount >= cols || colCoveredCount >= rows) {
            return 7;
        } else {
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
     * Find a uncovered zero and prime it.
     * If there is no starred zero in the row containing this primed zero,
     * Go to Step 5.  Otherwise, cover this row and uncover the column containing the starred zero. Continue in this manner until there are no uncovered zeros left.
     * Save the smallest uncovered value and Go to Step 6
     */
    protected int primeZeros() {
//        logInfo("Hungarian step 4 - Find a uncovered zero and prime it...");
        int row = 0;
        int col = 0;
        while (true) {
            Pair<Integer, Integer> res = findAUncoveredZero(row, col);
            if (res == null) {
                return 6;
                // step 6
            } else {
                int primedRow = res.getFirst();
                int primedCol = res.getSecond();
                // record current location
                row = primedRow;
                col = primedCol;
                maskMat[primedRow][primedCol] = ZeroMasks.primed;
                int starredCol = findStarredInRow(primedRow);
                if (starredCol != -1) {
                    R_cover[row] = true;
                    C_cover[col] = false;
                } else {
                    //step 5
                    pathRow = row;
                    pathCol = col;
                    return 5;
                }
            }
        }
    }

    private Pair<Integer, Integer> findAUncoveredZero(int row, int col) {
        for (int r = row; r < rows; r++) {
            for (int c = col; c < cols; c++) {
                if (mat.get(r, c) == 0 && !C_cover[c] && !R_cover[r]) {
                    return new Pair<>(r, c);
                }
            }
        }
        return null;
    }


    private int findStarredInRow(int row) {
        for (int c = 0; c < cols; c++) {
            if (maskMat[row][c] == ZeroMasks.starred) {
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
     * <br>
     * <p>augmenting path algorithm (for solving the maximal matching problem) </p>
     */
    protected int augmentingPath() {
//        logInfo("Hungarian step 5 - Construct a series of alternating primed and starred zeros...");
        int pathCount = 1;
        path[0][0] = pathRow;
        path[0][1] = pathCol;
        boolean done = false;
        while (!done) {
            int rowIndex = findStarredInCol(path[pathCount - 1][1]);
            if (rowIndex > -1) {
                pathCount++;
                path[pathCount - 1][0] = rowIndex;
                path[pathCount - 1][1] = path[pathCount - 2][1];
            } else {
                done = true;
            }
            if (!done) {
                int colIndex = findPrimeInRow(path[pathCount - 1][0]);
                pathCount++;
                path[pathCount - 1][0] = path[pathCount - 2][0];
                path[pathCount - 1][1] = colIndex;
            }
        }
        augmentPath(pathCount);
        initCoverVets();
        erasePrimes();
        return 3;
    }

    private void erasePrimes() {
        // parallel here there is no interference and no stateful lambda
        //https://docs.oracle.com/javase/tutorial/collections/streams/parallelism.html
        rowIndexes.parallelStream().forEach(r -> colIndexes.parallelStream().forEach(c -> {
            if (maskMat[r][c] == ZeroMasks.primed) {
                maskMat[r][c] = null;
            }
        }));
        // step 3
    }


    private void augmentPath(int pathCount) {
        for (int p = 0; p < pathCount; p++) {
            if (maskMat[path[p][0]][path[p][1]] == ZeroMasks.starred) {
                maskMat[path[p][0]][path[p][1]] = null;
            } else {
                maskMat[path[p][0]][path[p][1]] = ZeroMasks.starred;
            }
        }
    }

    private int findPrimeInRow(int row) {
        for (int c = 0; c < cols; c++) {
            if (maskMat[row][c] == ZeroMasks.primed) {
                return c;
            }
        }
        return -1;
    }

    private int findStarredInCol(int col) {
        for (int r = 0; r < rows; r++) {
            if (maskMat[r][col] == ZeroMasks.starred) {
                return r;
            }
        }
        return -1;
    }

    /**
     * STEP 6:
     * <p>
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
    protected int adjustMat() {
//        logInfo("Hungarian step 6-Modify the matrix...");
        double minVal = findSmallestOfUncovered();
        // no parallel here stateful R_cover and C_cover
        rowIndexes.forEach(r -> colIndexes.forEach(c -> {
            double val = mat.get(r, c);
            if (R_cover[r]) {
                mat.set(r, c, val + minVal);
            }
            if (!C_cover[c]) {
                mat.set(r, c, val - minVal);
            }
        }));
        return 4;
    }

    private double findSmallestOfUncovered() {
        double tpVal = Double.MAX_VALUE;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (!R_cover[r] && !C_cover[c]) {
                    double val = mat.get(r, c);
                    if (val < tpVal) {
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
    protected int[] finish() {
//        logInfo("Hungarian step 7-Finish");
        int[] result = new int[rows];
        for (int r = 0; r < rows; r++) {
            int indexCol = findStarredInRow(r);
            result[r] = indexCol;
        }
        return result;
    }

    public int[] getResult() {
        assert (result != null);
        return result;
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

}